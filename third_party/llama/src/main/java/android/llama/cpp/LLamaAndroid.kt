class LLamaAndroid {
    fun send(message: String): Flow<String> = flow {
        when (val state = threadLocalState.get()) {
            is State.Loaded -> {
                // 1. 初始化补全过程
                val ncur = IntVar(completion_init(state.context, state.batch, message, nlen))
                
                // 2. 循环生成回复
                while (ncur.value <= nlen) {
                    // 3. 生成下一个 token 并转换为文本
                    val str = completion_loop(state.context, state.batch, state.sampler, nlen, ncur)
                    if (str == null) {
                        break
                    }
                    // 4. 发送生成的文本片段
                    emit(str)
                }
                // 5. 清理 KV 缓存
                kv_cache_clear(state.context)
            }
            else -> {}  // 模型未加载时不做处理
        }
    }.flowOn(runLoop)  // 在专用线程上执行
} 