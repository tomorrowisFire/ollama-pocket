#include <jni.h>
#include <c-api.h>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <memory>
#include <string>

namespace {
constexpr int kSampleRate = 44100;
constexpr int kMaxFrames = kSampleRate * 60;
constexpr int kMaxUtf8Bytes = 120 * 4;

void error(JNIEnv *env, const char *message) {
    if (env->ExceptionCheck()) return;
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type) { env->ThrowNew(type, message); env->DeleteLocalRef(type); }
}

struct StringChars {
    JNIEnv *env;
    jstring value;
    const char *chars;
    StringChars(JNIEnv *e, jstring v) : env(e), value(v), chars(e->GetStringUTFChars(v, nullptr)) {}
    ~StringChars() { if (chars) env->ReleaseStringUTFChars(value, chars); }
};

struct AudioDeleter {
    void operator()(const SherpaOnnxGeneratedAudio *audio) const {
        if (audio) SherpaOnnxDestroyOfflineTtsGeneratedAudio(audio);
    }
};

struct Cancellation {
    JNIEnv *env;
    jobject supplier;
    jmethodID method;
    bool invalid = false;
    bool cancelled() {
        if (env->ExceptionCheck()) return true;
        jboolean answer = env->CallBooleanMethod(supplier, method);
        return env->ExceptionCheck() || answer == JNI_TRUE;
    }
};

int32_t progress(const float *samples, int32_t count, float, void *opaque) {
    auto &state = *static_cast<Cancellation *>(opaque);
    if (count <= 0 || count > kMaxFrames || samples == nullptr) state.invalid = true;
    // Supertonic v2.0.3 ignores callback abort returns. This callback is only an
    // admission/cancellation signal. The caller discards a cancelled result.
    return state.invalid || state.cancelled() ? 0 : 1;
}

bool valid_utf8(const std::string &text) {
    size_t i = 0;
    int codepoints = 0;
    while (i < text.size()) {
        unsigned char lead = static_cast<unsigned char>(text[i]);
        uint32_t cp;
        size_t count;
        if (lead < 0x80) { cp = lead; count = 1; }
        else if (lead >= 0xc2 && lead <= 0xdf) { cp = lead & 0x1f; count = 2; }
        else if (lead >= 0xe0 && lead <= 0xef) { cp = lead & 0x0f; count = 3; }
        else if (lead >= 0xf0 && lead <= 0xf4) { cp = lead & 0x07; count = 4; }
        else return false;
        if (i + count > text.size()) return false;
        for (size_t j = 1; j < count; ++j) {
            unsigned char byte = static_cast<unsigned char>(text[i+j]);
            if ((byte & 0xc0) != 0x80) return false;
            cp = (cp << 6) | (byte & 0x3f);
        }
        if ((count == 2 && cp < 0x80) || (count == 3 && cp < 0x800) ||
                (count == 4 && cp < 0x10000) || cp > 0x10ffff ||
                (cp >= 0xd800 && cp <= 0xdfff) || cp == 0 ||
                (cp < 32 && cp != 9 && cp != 10 && cp != 13) || cp == 127 ||
                ++codepoints > 120) return false;
        i += count;
    }
    return codepoints > 0;
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_ollamapocket_app_NativeNeuralTts_nativeOpen(JNIEnv *env, jclass, jstring directory) {
    if (!directory || env->GetStringLength(directory) > 4096) {
        error(env, "Invalid TTS directory"); return 0;
    }
    try {
        StringChars raw(env, directory);
        if (!raw.chars) return 0;
        std::string base(raw.chars);
        const char *names[] = {"duration_predictor.int8.onnx", "text_encoder.int8.onnx",
            "vector_estimator.int8.onnx", "vocoder.int8.onnx", "tts.json",
            "unicode_indexer.bin", "voice.bin"};
        std::string paths[7];
        for (int i = 0; i < 7; ++i) paths[i] = base + "/" + names[i];
        SherpaOnnxOfflineTtsConfig config{};
        config.model.supertonic.duration_predictor = paths[0].c_str();
        config.model.supertonic.text_encoder = paths[1].c_str();
        config.model.supertonic.vector_estimator = paths[2].c_str();
        config.model.supertonic.vocoder = paths[3].c_str();
        config.model.supertonic.tts_json = paths[4].c_str();
        config.model.supertonic.unicode_indexer = paths[5].c_str();
        config.model.supertonic.voice_style = paths[6].c_str();
        config.model.num_threads = 2;
        config.model.provider = "cpu";
        config.model.debug = 0;
        config.max_num_sentences = 1;
        const SherpaOnnxOfflineTts *tts = SherpaOnnxCreateOfflineTts(&config);
        if (!tts) { error(env, "TTS model load failed"); return 0; }
        if (SherpaOnnxOfflineTtsSampleRate(tts) != kSampleRate ||
                SherpaOnnxOfflineTtsNumSpeakers(tts) != 10) {
            SherpaOnnxDestroyOfflineTts(tts);
            error(env, "Unexpected TTS model metadata"); return 0;
        }
        return static_cast<jlong>(reinterpret_cast<intptr_t>(tts));
    } catch (...) { error(env, "TTS model load failed"); return 0; }
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_ollamapocket_app_NativeNeuralTts_nativeGenerate(
        JNIEnv *env, jclass, jlong handle, jbyteArray utf8, jstring language,
        jfloat rate, jobject cancelled) {
    if (!handle || !utf8 || !language || !cancelled || !std::isfinite(rate) || rate < .8f || rate > 1.4f) {
        error(env, "Invalid TTS request"); return nullptr;
    }
    jsize length = env->GetArrayLength(utf8);
    if (length <= 0 || length > kMaxUtf8Bytes || env->GetStringLength(language) != 2) {
        error(env, "TTS input bounds"); return nullptr;
    }
    jclass cls = env->GetObjectClass(cancelled);
    if (!cls) return nullptr;
    jmethodID method = env->GetMethodID(cls, "getAsBoolean", "()Z");
    env->DeleteLocalRef(cls);
    if (!method) return nullptr;
    Cancellation state{env, cancelled, method};
    if (state.cancelled()) return nullptr;
    try {
        std::string text(static_cast<size_t>(length), '\0');
        env->GetByteArrayRegion(utf8, 0, length, reinterpret_cast<jbyte *>(text.data()));
        if (env->ExceptionCheck()) return nullptr;
        if (!valid_utf8(text)) { error(env, "Invalid UTF-8 input"); return nullptr; }
        StringChars lang(env, language);
        if (!lang.chars) return nullptr;
        bool supported = std::strcmp(lang.chars, "en") == 0 || std::strcmp(lang.chars, "ko") == 0 || std::strcmp(lang.chars, "ja") == 0;
        std::string extra = "{\"lang\":\"" + std::string(lang.chars) + "\",\"max_len\":120,\"silence_duration\":0}";
        if (!supported) { error(env, "Unsupported TTS language"); return nullptr; }
        SherpaOnnxGenerationConfig config{};
        config.sid = 0;
        config.speed = rate;
        config.num_steps = 5;
        config.extra = extra.c_str();
        auto *tts = reinterpret_cast<const SherpaOnnxOfflineTts *>(static_cast<intptr_t>(handle));
        std::unique_ptr<const SherpaOnnxGeneratedAudio, AudioDeleter> audio(
            SherpaOnnxOfflineTtsGenerateWithConfig(tts, text.c_str(), &config, progress, &state));
        if (state.cancelled()) return nullptr;
        if (state.invalid || !audio || !audio->samples || audio->sample_rate != kSampleRate ||
                audio->n <= 0 || audio->n > kMaxFrames) {
            error(env, "TTS waveform bounds"); return nullptr;
        }
        for (int i = 0; i < audio->n; ++i) {
            if (!std::isfinite(audio->samples[i]) || audio->samples[i] < -1.f || audio->samples[i] > 1.f) {
                error(env, "Invalid TTS waveform"); return nullptr;
            }
        }
        jfloatArray result = env->NewFloatArray(audio->n);
        if (result) env->SetFloatArrayRegion(result, 0, audio->n, audio->samples);
        return result;
    } catch (...) { error(env, "TTS generation failed"); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_ollamapocket_app_NativeNeuralTts_nativeClose(JNIEnv *, jclass, jlong handle) {
    if (handle) SherpaOnnxDestroyOfflineTts(
        reinterpret_cast<const SherpaOnnxOfflineTts *>(static_cast<intptr_t>(handle)));
}
