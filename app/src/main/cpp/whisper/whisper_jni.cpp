#include <jni.h>
#include "whisper_core.h"

#include <algorithm>
#include <atomic>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <vector>

namespace {
std::mutex registry_mutex;
std::unordered_map<jlong, std::shared_ptr<onhand_whisper::Job>> registry;
jlong next_id = 1;

std::shared_ptr<onhand_whisper::Job> lookup(jlong id) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    auto item = registry.find(id);
    return item == registry.end() ? nullptr : item->second;
}
bool text(JNIEnv *env, jstring input, size_t limit, std::string &out) {
    if (!input || static_cast<size_t>(env->GetStringUTFLength(input)) > limit) return false;
    const char *chars = env->GetStringUTFChars(input, nullptr);
    if (!chars) return false;
    try { out.assign(chars); }
    catch (...) { env->ReleaseStringUTFChars(input, chars); return false; }
    env->ReleaseStringUTFChars(input, chars);
    return !env->ExceptionCheck();
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_ollamapocket_app_NativeWhisper_create(JNIEnv *, jclass) {
    try {
        std::lock_guard<std::mutex> lock(registry_mutex);
        if (registry.size() >= 4 || next_id <= 0 || next_id == INT64_MAX) return 0;
        jlong id = next_id++;
        registry.emplace(id, std::make_shared<onhand_whisper::Job>());
        return id;
    } catch (...) { return 0; }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_ollamapocket_app_NativeWhisper_transcribe(JNIEnv *env, jclass, jlong id,
        jstring model_path, jshortArray input, jint count, jstring language, jint threads) {
    try {
        auto job = lookup(id);
        if (!job || job->stopped() || !input || count < onhand_whisper::kSampleRate ||
                count > onhand_whisper::kMaxSamples) return nullptr;
        jsize length = env->GetArrayLength(input);
        if (length < count || length > onhand_whisper::kMaxSamples) return nullptr;
        std::string path, lang;
        if (!text(env, model_path, 4096, path) || !text(env, language, 8, lang)) return nullptr;
        std::vector<int16_t> samples(static_cast<size_t>(count));
        env->GetShortArrayRegion(input, 0, count, reinterpret_cast<jshort *>(samples.data()));
        if (env->ExceptionCheck()) return nullptr;
        std::string output;
        bool ok = onhand_whisper::transcribe(*job, path, samples.data(), count, lang, threads, output);
        std::fill(samples.begin(), samples.end(), static_cast<int16_t>(0));
        if (!ok || job->stopped() || output.size() > onhand_whisper::kMaxOutputBytes) return nullptr;
        // Whisper emits ordinary UTF-8, not JNI's modified UTF-8. Decode explicitly in Java.
        jbyteArray result = env->NewByteArray(static_cast<jsize>(output.size()));
        if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(output.size()),
                                         reinterpret_cast<const jbyte *>(output.data()));
        std::fill(output.begin(), output.end(), '\0');
        return env->ExceptionCheck() || job->stopped() ? nullptr : result;
    } catch (...) { return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_ollamapocket_app_NativeWhisper_cancel(JNIEnv *, jclass, jlong id) {
    try {
        auto job = lookup(id);
        if (job) job->cancelled.store(true, std::memory_order_relaxed);
    } catch (...) { }
}

extern "C" JNIEXPORT void JNICALL
Java_com_ollamapocket_app_NativeWhisper_destroy(JNIEnv *, jclass, jlong id) {
    try {
        std::lock_guard<std::mutex> lock(registry_mutex);
        auto item = registry.find(id);
        if (item != registry.end()) {
            item->second->cancelled.store(true, std::memory_order_relaxed);
            registry.erase(item);
        }
    } catch (...) { }
    // A running transcribe holds shared ownership, so erase cannot free its context.
}
