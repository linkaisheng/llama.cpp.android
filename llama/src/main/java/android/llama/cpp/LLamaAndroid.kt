package android.llama.cpp

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class LLamaAndroid {
    private val tag: String? = this::class.simpleName
    private var context: Context? = null

    private val threadLocalState: ThreadLocal<State> = ThreadLocal.withInitial { State.Idle }

    // 模型参数
    private var temperature: Float = 0.7f
    private var topP: Float = 0.9f
    private var nlen: Int = 2048  // 最大可能的生成长度

    private val runLoop: CoroutineDispatcher = Executors.newSingleThreadExecutor {
        thread(start = false, name = "Llm-RunLoop") {
            Log.d(tag, "Dedicated thread for native code: ${Thread.currentThread().name}")

            System.loadLibrary("llama-android")
            log_to_android()
            backend_init(false)
            Log.d(tag, system_info())

            it.run()
        }.apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, exception: Throwable ->
                Log.e(tag, "Unhandled exception", exception)
            }
        }
    }.asCoroutineDispatcher()

    private external fun log_to_android()
    private external fun load_model(filename: String): Long
    private external fun free_model(model: Long)
    private external fun new_context(model: Long): Long
    private external fun free_context(context: Long)
    private external fun backend_init(numa: Boolean)
    private external fun backend_free()
    private external fun new_sampler(model: Long): Long
    private external fun get_chat_format_single(
        model: Long,
        role: String,
        content: String
    ): String
    private external fun free_sampler(sampler: Long)
    private external fun getContextSize(contextPointer: Long): Int
    private external fun system_info(): String
    private external fun completion_init(
        context: Long,
        sampler: Long,
        text: String,
        nLen: Int
    ): Long
    private external fun completion_loop(
        context: Long,
        sampler: Long,
        nLen: Int,
        ncur: IntVar
    ): String?
    private external fun kv_cache_clear(context: Long)
    
    // 修改外部函数名称
    private external fun nativeSetTemperature(temp: Float)
    private external fun nativeSetTopP(topP: Float)
    private external fun nativeSetMaxGenerateTokens(maxTokens: Int)

    fun setTemperature(value: Float) {
        temperature = value.coerceIn(0f, 1f)
        nativeSetTemperature(temperature)
        Log.i(tag, "Temperature set to $temperature")
    }

    fun setTopP(value: Float) {
        topP = value.coerceIn(0f, 1f)
        nativeSetTopP(topP)
        Log.i(tag, "Top P set to $topP")
    }

    fun setMaxGenerateTokens(value: Int) {
        nlen = value.coerceIn(128, 4096)
        nativeSetMaxGenerateTokens(nlen)
        Log.i(tag, "Max generate tokens set to $nlen")
    }

    fun initialize(context: Context) {
        this.context = context
    }

    suspend fun load(pathToModel: String) {
        withContext(runLoop) {
            when (threadLocalState.get()) {
                is State.Idle -> {
                    val model = load_model(pathToModel)
                    if (model == 0L)  throw IllegalStateException("load_model() failed")

                    val context = new_context(model)
                    if (context == 0L) throw IllegalStateException("new_context() failed")

                    val sampler = new_sampler(model)
                    if (sampler == 0L) throw IllegalStateException("new_sampler() failed")

                    Log.i(tag, "Loaded model $pathToModel")
                    threadLocalState.set(State.Loaded(model, context, sampler))
                }
                else -> throw IllegalStateException("Model already loaded")
            }
        }
    }

    suspend fun get_chat_format_msg(role: String, content: String) : String {
        var res = ""
        withContext(runLoop) {
            when (val state = threadLocalState.get()) {
                is State.Loaded -> {
                    res = get_chat_format_single(state.model, role, content)
                }
                else -> {}
            }
        }
        return res
    }

    fun send(message: String): Flow<String> = flow {
        when (val state = threadLocalState.get()) {
            is State.Loaded -> {
                try {
                    Log.d(tag, "Starting completion with message: $message")
                    val ncur = IntVar(completion_init(state.context, state.sampler, message, nlen).toInt())
                    Log.d(tag, "Completion initialized with ncur: ${ncur.value}")

                    // 获取模型的上下文窗口大小
                    val contextSize = getContextSize(state.context)
                    Log.d(tag, "Model context size: $contextSize")

                    // 计算剩余可用的token数量
                    val remainingTokens = contextSize - ncur.value
                    Log.d(tag, "Remaining available tokens: $remainingTokens")

                    var tokenCount = 0
                    while (tokenCount < remainingTokens) {
                        val str = completion_loop(state.context,  state.sampler, nlen, ncur)
                        if (str == null) {
                            Log.d(tag, "Completion loop returned null (end of generation), breaking")
                            break
                        }
                        if (str.isNotEmpty()) {
                            Log.d(tag, "Emitting token: $str")
                            emit(str)
                        }
                        tokenCount++

                        // 每生成100个token记录一次进度
                        if (tokenCount % 100 == 0) {
                            Log.d(tag, "Generated $tokenCount tokens, remaining: ${remainingTokens - tokenCount}")
                        }
                    }

                    if (tokenCount >= remainingTokens) {
                        Log.w(tag, "Generation stopped due to context window limit")
                    }

                } catch (e: Exception) {
                    Log.e(tag, "Error in completion", e)
                    // 只在发生错误时清理KV Cache
                    kv_cache_clear(state.context)
                    throw e
                }
            }
            else -> throw IllegalStateException("No model loaded")
        }
    }.flowOn(runLoop)

    /**
     * Unloads the model and frees resources.
     *
     * This is a no-op if there's no model loaded.
     */
    suspend fun unload() {
        withContext(runLoop) {
            when (val state = threadLocalState.get()) {
                is State.Loaded -> {
                    free_context(state.context)
                    free_model(state.model)
                    free_sampler(state.sampler);
                    threadLocalState.set(State.Idle)
                }
                else -> {}
            }
        }
    }

    suspend fun getContextSize(): Int {
        return withContext(runLoop) {
            when (val state = threadLocalState.get()) {
                is State.Loaded -> {
                    getContextSize(state.context)
                }
                else -> throw IllegalStateException("No model loaded")
            }
        }
    }

    fun getContextSizeSync(): Int {
        when (val state = threadLocalState.get()) {
            is State.Loaded -> {
                return getContextSize(state.context)
            }
            else -> throw IllegalStateException("No model loaded")
        }
    }

    companion object {
        private class IntVar(value: Int) {
            @Volatile
            var value: Int = value
                private set

            fun inc() {
                synchronized(this) {
                    value += 1
                }
            }
        }

        private sealed interface State {
            data object Idle: State
            data class Loaded(val model: Long, val context: Long, val sampler: Long): State
        }

        private val _instance: LLamaAndroid = LLamaAndroid()

        fun instance(): LLamaAndroid = _instance
    }
}
