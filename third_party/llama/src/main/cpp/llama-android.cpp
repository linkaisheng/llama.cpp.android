// 初始化补全
JNIEXPORT jint JNICALL Java_android_llama_cpp_LLamaAndroid_completion_1init(
        JNIEnv* env, jobject, jlong context_pointer, jlong batch_pointer,
        jstring jtext, jint n_len) {
    // 处理输入文本
    const auto tokens_list = common_tokenize(context, text, 1);
    // 初始化批处理
    common_batch_clear(*batch);
    // 评估初始提示
    for (auto i = 0; i < tokens_list.size(); i++) {
        common_batch_add(*batch, tokens_list[i], i, { 0 }, false);
    }
    return batch->n_tokens;
}

// 生成回复
JNIEXPORT jstring JNICALL Java_android_llama_cpp_LLamaAndroid_completion_1loop(
        JNIEnv * env, jobject, jlong context_pointer, jlong batch_pointer,
        jlong sampler_pointer, jint n_len, jobject intvar_ncur) {
    // 1. 转换指针类型
    const auto context = reinterpret_cast<llama_context *>(context_pointer);
    const auto batch   = reinterpret_cast<llama_batch   *>(batch_pointer);
    const auto sampler = reinterpret_cast<llama_sampler *>(sampler_pointer);
    const auto model = llama_get_model(context);

    // 2. 获取 Java 对象的方法 ID
    if (!la_int_var) la_int_var = env->GetObjectClass(intvar_ncur);
    if (!la_int_var_value) la_int_var_value = env->GetMethodID(la_int_var, "getValue", "()I");
    if (!la_int_var_inc) la_int_var_inc = env->GetMethodID(la_int_var, "inc", "()V");

    // 3. 采样生成新 token
    const auto new_token_id = llama_sampler_sample(sampler, context, -1);

    // 4. 检查是否需要结束生成
    const auto n_cur = env->CallIntMethod(intvar_ncur, la_int_var_value);
    if (llama_token_is_eog(model, new_token_id) || n_cur == n_len) {
        return nullptr;  // 遇到结束符或达到长度限制
    }

    // 5. token 转文本处理
    auto new_token_chars = common_token_to_piece(context, new_token_id);
    cached_token_chars += new_token_chars;  // 累积 token 字符

    // 6. UTF-8 有效性检查和转换
    jstring new_token = nullptr;
    if (is_valid_utf8(cached_token_chars.c_str())) {
        new_token = env->NewStringUTF(cached_token_chars.c_str());
        LOGi("cached: %s, new_token_chars: `%s`, id: %d", 
             cached_token_chars.c_str(), new_token_chars.c_str(), new_token_id);
        cached_token_chars.clear();  // 清空缓存
    } else {
        new_token = env->NewStringUTF("");  // 无效 UTF-8 时返回空字符串
    }

    // 7. 准备下一轮生成
    common_batch_clear(*batch);  // 清空批处理
    common_batch_add(*batch, new_token_id, n_cur, { 0 }, true);  // 添加新 token

    // 8. 更新计数器
    env->CallVoidMethod(intvar_ncur, la_int_var_inc);

    // 9. 执行下一轮推理
    if (llama_decode(context, *batch) != 0) {
        LOGe("llama_decode() returned null");
    }

    return new_token;  // 返回生成的文本片段
} 