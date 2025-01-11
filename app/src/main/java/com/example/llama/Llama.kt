package com.example.llama
import android.llama.cpp.LLamaAndroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import android.util.Log
import android.content.Context

class Llama {
    private val llamaAndroid: LLamaAndroid = LLamaAndroid.instance()
    private var isFirstMessage = true

    // 性能监控数据
    data class PerformanceMetrics(
        val inputTokens: Int,
        val outputTokens: Int,
        val generationTimeMs: Long,
        val memoryUsageMb: Long
    )

    // 模型参数配置
    data class ModelConfig(
        val temperature: Float = 0.7f,
        val topP: Float = 0.9f,
        val maxGenerateTokens: Int = 2048
    )

    private var currentMetrics: PerformanceMetrics? = null
    private var currentConfig = ModelConfig()

    fun initialize(context: Context) {
        llamaAndroid.initialize(context)
    }

    fun updateConfig(config: ModelConfig) {
        currentConfig = config
        llamaAndroid.setTemperature(config.temperature)
        llamaAndroid.setTopP(config.topP)
        llamaAndroid.setMaxGenerateTokens(config.maxGenerateTokens)
    }

    private val systemPrompt = """You are a helpful assistant. Follow these rules:
1. Match user's language (Chinese for Chinese, English for English)
2. Be concise and clear
3. Admit when you don't know something
4. Focus on accuracy and helpfulness"""

    suspend fun load(modelPath: String) {
        withContext(Dispatchers.IO) {
            llamaAndroid.load(modelPath)
            isFirstMessage = true
            // 加载时应用当前配置
            updateConfig(currentConfig)
        }
    }

    suspend fun generate(prompt: String, customSystemPrompt: String? = null): Flow<String> = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            var outputTokenCount = 0
            
            val contextPrompt = buildString {
                if (isFirstMessage) {
                    append(llamaAndroid.get_chat_format_msg("system", customSystemPrompt ?: systemPrompt))
                    isFirstMessage = false
                }
                append(llamaAndroid.get_chat_format_msg("user", prompt.trim()))
            }
            
            llamaAndroid.send(contextPrompt)
                .onEach { token -> 
                    outputTokenCount++
                }
                .onCompletion {
                    val endTime = System.currentTimeMillis()
                    val duration = endTime - startTime
                    currentMetrics = PerformanceMetrics(
                        inputTokens = prompt.length / 4, // 粗略估算，仅用于参考
                        outputTokens = outputTokenCount,
                        generationTimeMs = duration,
                        memoryUsageMb = getCurrentMemoryUsage()
                    )
                    // 输出性能指标
                    Log.i("LlamaPerformance", """
                        Performance Metrics:
                        - Generation Time: ${duration}ms
                        - Output Tokens: $outputTokenCount
                        - Speed: ${(outputTokenCount * 1000.0) / duration} tokens/s
                        - Temperature: ${currentConfig.temperature}
                        - Top P: ${currentConfig.topP}
                    """.trimIndent())
                }
        } catch (e: Exception) {
            Log.e("Llama", "Error generating response", e)
            throw e
        }
    }

    private fun getCurrentMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) // Convert to MB
    }

    suspend fun unload() {
        withContext(Dispatchers.IO) {
            try {
                llamaAndroid.unload()
            } catch (e: Exception) {
                Log.e("Llama", "Error unloading model", e)
            }
        }
    }

    // 获取最近一次生成的性能指标
    fun getLastPerformanceMetrics(): PerformanceMetrics? = currentMetrics

    // 获取当前配置
    fun getCurrentConfig(): ModelConfig = currentConfig
}
