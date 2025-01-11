#include <android/log.h>
#include <jni.h>
#include <iomanip>
#include <math.h>
#include <string>
#include <unistd.h>
#include <llama.h>
#include <common.h>
#include <sampling.h>



#define TAG "llama-android.cpp"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// 全局参数
static float g_temperature = 0.7f;
static float g_top_p = 0.9f;
static int g_max_tokens = 2048;

std::string cached_token_chars;
const int g_ga_n = 2;
const int g_ga_w = 512;

// 添加KV Cache管理相关变量
static int n_past = 0;
static const int n_keep = 64;  // 保留的上下文token数

bool is_valid_utf8(const char * string) {
    if (!string) {
        return true;
    }

    const unsigned char * bytes = (const unsigned char *)string;
    int num;

    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) {
            // U+0000 to U+007F
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            // U+0080 to U+07FF
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            // U+0800 to U+FFFF
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            // U+10000 to U+10FFFF
            num = 4;
        } else {
            return false;
        }

        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }

    return true;
}

static void log_callback(ggml_log_level level, const char * fmt, void * data) {
    if (level == GGML_LOG_LEVEL_ERROR)     __android_log_print(ANDROID_LOG_ERROR, TAG, fmt, data);
    else if (level == GGML_LOG_LEVEL_INFO) __android_log_print(ANDROID_LOG_INFO, TAG, fmt, data);
    else if (level == GGML_LOG_LEVEL_WARN) __android_log_print(ANDROID_LOG_WARN, TAG, fmt, data);
    else __android_log_print(ANDROID_LOG_DEFAULT, TAG, fmt, data);
}


static std::string chat_add_and_format(struct llama_model * model,
     std::vector<common_chat_msg> & chat_msgs,
     const std::string & role, const std::string & content) {

}

extern "C"
JNIEXPORT jstring JNICALL
Java_android_llama_cpp_LLamaAndroid_get_1chat_1format_1single(JNIEnv *env, jobject, jlong jmodel, jstring jrole, jstring jcontent) {
    auto model = reinterpret_cast<llama_model *>(jmodel);
    std::vector<common_chat_msg> chat_msgs;
    auto role = env->GetStringUTFChars(jrole, 0);
    auto content = env->GetStringUTFChars(jcontent, 0);

    common_chat_msg new_msg{role, content};
    auto formatted = common_chat_format_single(model, "", chat_msgs, new_msg, true);
    jstring jformatted = nullptr;
    jformatted = env->NewStringUTF(formatted.c_str());
    return jformatted;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_load_1model(JNIEnv *env, jobject, jstring filename) {
    llama_model_params model_params = llama_model_default_params();

    auto path_to_model = env->GetStringUTFChars(filename, 0);

    auto model = llama_load_model_from_file(path_to_model, model_params);
    env->ReleaseStringUTFChars(filename, path_to_model);


    if (!model) {
        LOGe("load_model() failed");
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "load_model() failed");
        return 0;
    }

    return reinterpret_cast<jlong>(model);
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_free_1model(JNIEnv *, jobject, jlong model) {
    llama_free_model(reinterpret_cast<llama_model *>(model));
}

extern "C"
JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_new_1context(JNIEnv *env, jobject, jlong jmodel) {
    auto model = reinterpret_cast<llama_model *>(jmodel);

    if (!model) {
        LOGe("new_context(): model cannot be null");
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Model cannot be null");
        return 0;
    }

    int n_threads = std::max(1, std::min(8, (int) sysconf(_SC_NPROCESSORS_ONLN) - 2));
    LOGi("Using %d threads", n_threads);

    llama_context_params ctx_params = llama_context_default_params();

    ctx_params.n_ctx           = 2048;
    ctx_params.n_threads       = n_threads;
    ctx_params.n_threads_batch = n_threads;

    llama_context * context = llama_new_context_with_model(model, ctx_params);

    if (!context) {
        LOGe("llama_new_context_with_model() returned null)");
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),
                      "llama_new_context_with_model() returned null)");
        return 0;
    }

    return reinterpret_cast<jlong>(context);
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_free_1context(JNIEnv *, jobject, jlong context) {
    llama_free(reinterpret_cast<llama_context *>(context));
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_backend_1free(JNIEnv *, jobject) {
    llama_backend_free();
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_log_1to_1android(JNIEnv *, jobject) {
    llama_log_set(log_callback, NULL);
}


extern "C"
JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_new_1sampler(JNIEnv *, jobject, jlong model_pointer) {
    const auto model = reinterpret_cast<llama_model *>(model_pointer);
    if (!model) {
        LOGe("new_sampler(): model cannot be null");
        return 0;
    }

    common_params_sampling params;
    params.temp = g_temperature;
    params.top_p = g_top_p;
    params.penalty_repeat = 1.1f;
    params.n_prev = 64;
    params.penalty_last_n = 64;
    params.mirostat = 0;  // 禁用mirostat
    params.samplers = {
        COMMON_SAMPLER_TYPE_DRY,
        COMMON_SAMPLER_TYPE_TOP_K,
        COMMON_SAMPLER_TYPE_TYPICAL_P,
        COMMON_SAMPLER_TYPE_TOP_P,
        COMMON_SAMPLER_TYPE_MIN_P,
        COMMON_SAMPLER_TYPE_XTC,
        COMMON_SAMPLER_TYPE_TEMPERATURE
    };
    params.seed = time(NULL);

    auto * sampler = common_sampler_init(model, params);

    if (!sampler) {
        LOGe("Failed to initialize sampler");
        return 0;
    }
    LOGi("Sampler initialized with temp=%.2f, top_p=%.2f", g_temperature, g_top_p);
    LOGi("sampler chain: %s", common_sampler_print(sampler).c_str());
    return reinterpret_cast<jlong>(sampler);
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_free_1sampler(JNIEnv *, jobject, jlong sampler_pointer) {
    auto * sampler = reinterpret_cast<common_sampler *> (sampler_pointer);
    if (sampler) {
        common_sampler_free(sampler);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_backend_1init(JNIEnv *, jobject) {
    llama_backend_init();
}

extern "C"
JNIEXPORT jstring JNICALL
Java_android_llama_cpp_LLamaAndroid_system_1info(JNIEnv *env, jobject) {
    return env->NewStringUTF(llama_print_system_info());
}

extern "C"
JNIEXPORT jlong JNICALL
Java_android_llama_cpp_LLamaAndroid_completion_1init(
        JNIEnv *env,
        jobject,
        jlong context_pointer,
        jlong sampler_pointer,
        jstring jtext,
        jint n_len
    ) {
    cached_token_chars.clear();

    const auto text = env->GetStringUTFChars(jtext, 0);
    const auto context = reinterpret_cast<llama_context *>(context_pointer);
    const auto sampler = reinterpret_cast<common_sampler *>(sampler_pointer);

    auto tokens_list = common_tokenize(context, text, true, true);


    auto n_ctx_used = llama_get_kv_cache_used_cells(context);

    LOGi("Input tokens: %zu,  KV used: %zu",
         tokens_list.size(), n_ctx_used);

    // 使用批处理进行初始化
    int batch_size = 1024;
    for (int i = 0; i < tokens_list.size(); i += batch_size) {
        int n_eval = tokens_list.size() - i;
        if (n_eval > batch_size) {
            n_eval = batch_size;
        }

        auto batch = llama_batch_get_one(&tokens_list[i], n_eval);
        if (llama_decode(context, batch)) {
            env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),
                         "Failed to decode batch");
            return -1;
        }

        n_past += n_eval;
    }

    env->ReleaseStringUTFChars(jtext, text);
    return n_past;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_android_llama_cpp_LLamaAndroid_completion_1loop(
        JNIEnv * env,
        jobject,
        jlong context_pointer,
        jlong sampler_pointer,
        jint n_len,
        jobject intvar_ncur
) {
    const auto context = reinterpret_cast<llama_context *>(context_pointer);
    //const auto batch   = reinterpret_cast<llama_batch   *>(batch_pointer);
    const auto sampler = reinterpret_cast<common_sampler *>(sampler_pointer);
    const auto model = llama_get_model(context);

    if (!context || !sampler || !model) {
        LOGe("One or more required pointers are null");
        return nullptr;
    }

    LOGi("Starting token sampling...");

    try {
        // 获取上下文窗口大小
        const int n_ctx = llama_n_ctx(context);

        // 检查KV Cache使用情况
        const size_t n_ctx_used = llama_get_kv_cache_used_cells(context);
        LOGi("KV Cache status - Used: %zu, Total: %d, n_past: %d", n_ctx_used, n_ctx, n_past);

        // sample the most likely token
        auto new_token_id = common_sampler_sample(sampler, context, -1);
        if (new_token_id < 0) {
            LOGe("Failed to sample token");
            return nullptr;
        }
        LOGi("Sampled token ID: %d", new_token_id);

        common_sampler_accept(sampler, new_token_id, -1);
        if (llama_token_is_eog(model, new_token_id)) {
            auto end_token_char = common_token_to_piece(context, new_token_id);
            LOGi("EOG token detected, stopping generation, %d => %s", new_token_id, end_token_char.c_str());
            return nullptr;
        }

        auto new_token_chars = common_token_to_piece(context, new_token_id);
        LOGi("Token text: '%s'", new_token_chars.c_str());
        
        // 直接使用新token，不进行缓存拼接
        jstring new_token = nullptr;
        if (is_valid_utf8(new_token_chars.c_str())) {
            // 如果有缓存的 token，先尝试与新 token 组合
            if (!cached_token_chars.empty()) {
                std::string combined = cached_token_chars + new_token_chars;
                if (is_valid_utf8(combined.c_str())) {
                    new_token = env->NewStringUTF(combined.c_str());
                    cached_token_chars.clear();  // 清空缓存
                    LOGi("Combined cached and new token: '%s'", combined.c_str());
                } else {
                    // 如果组合后仍然无效，只返回新的有效 token
                    new_token = env->NewStringUTF(new_token_chars.c_str());
                    LOGi("Using only new valid token: '%s'", new_token_chars.c_str());
                }
            } else {
                new_token = env->NewStringUTF(new_token_chars.c_str());
                LOGi("Valid UTF8 token: '%s'", new_token_chars.c_str());
            }
        } else {
            // 对于无效的UTF8，我们缓存它直到形成有效的UTF8序列
            cached_token_chars += new_token_chars;
            LOGi("Invalid UTF8, caching token piece: '%s'", cached_token_chars.c_str());
            // 尝试看缓存的序列是否已经有效
            if (is_valid_utf8(cached_token_chars.c_str())) {
                new_token = env->NewStringUTF(cached_token_chars.c_str());
                cached_token_chars.clear();  // 清空缓存
                LOGi("Cached sequence became valid UTF8: '%s'", cached_token_chars.c_str());
            } else {
                new_token = env->NewStringUTF("");
            }
        }

        if (!new_token) {
            LOGe("Failed to create new string");
            return nullptr;
        }

        LOGi("Preparing next batch...");
        auto batch = llama_batch_get_one(&new_token_id, 1);

        int ga_i = 0;
        int ga_n = g_ga_n;
        int ga_w = g_ga_w;
        // Self-Extend
        while (n_past >= ga_i + ga_w) {
            const int ib = (ga_n*ga_i)/ga_w;
            const int bd = (ga_w/ga_n)*(ga_n-1);
            const int dd = (ga_w/ga_n)-ib*bd-ga_w;

            LOGi("\n");
            LOGi("shift: [%6d, %6d] + %6d -> [%6d, %6d]\n", ga_i, n_past, ib*bd, ga_i + ib*bd, n_past + ib*bd);
            LOGi("div:   [%6d, %6d] / %6d -> [%6d, %6d]\n", ga_i + ib*bd, ga_i + ib*bd + ga_w, ga_n, (ga_i + ib*bd)/ga_n, (ga_i + ib*bd + ga_w)/ga_n);
            LOGi("shift: [%6d, %6d] + %6d -> [%6d, %6d]\n", ga_i + ib*bd + ga_w, n_past + ib*bd, dd, ga_i + ib*bd + ga_w + dd, n_past + ib*bd + dd);

            llama_kv_cache_seq_add(context, 0, ga_i, n_past, ib*bd);
            llama_kv_cache_seq_div(context, 0, ga_i+ib*bd, ga_i+ib*bd+ga_w, ga_n);
            llama_kv_cache_seq_add(context, 0, ga_i + ib*bd+ga_w, n_past + ib*bd, dd);

            n_past -= bd;
            ga_i += ga_w/ga_n;
            LOGi("\nn_past_old = %d, n_past = %d, ga_i = %d\n\n", n_past + bd, n_past, ga_i);
        }

        LOGi("Decoding next token...");
        if (llama_decode(context, batch) != 0) {
            LOGe("llama_decode() failed");
            return new_token;
        }
        n_past++;
        LOGi("Decode successful, new n_past: %d", n_past);

        return new_token;
    } catch (const std::exception& e) {
        LOGe("Exception in completion_loop: %s", e.what());
        return nullptr;
    } catch (...) {
        LOGe("Unknown exception in completion_loop");
        return nullptr;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_kv_1cache_1clear(JNIEnv *, jobject, jlong context) {
    llama_kv_cache_clear(reinterpret_cast<llama_context *>(context));
    n_past = 0;  // 重置n_past
}

extern "C"
JNIEXPORT jint JNICALL
Java_android_llama_cpp_LLamaAndroid_getContextSize(
    JNIEnv *env,
    jobject /* this */,
    jlong context_pointer
) {
    const auto context = reinterpret_cast<llama_context *>(context_pointer);
    return llama_n_ctx(context);
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_nativeSetTemperature(JNIEnv *, jobject, jfloat temp) {
    g_temperature = temp;
    LOGi("Temperature set to %.2f", g_temperature);
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_nativeSetTopP(JNIEnv *, jobject, jfloat top_p) {
    g_top_p = top_p;
    LOGi("Top P set to %.2f", g_top_p);
}

extern "C"
JNIEXPORT void JNICALL
Java_android_llama_cpp_LLamaAndroid_nativeSetMaxGenerateTokens(JNIEnv *, jobject, jint max_tokens) {
    g_max_tokens = max_tokens;
    LOGi("Max tokens set to %d", g_max_tokens);
}
