#include "whisper_core.h"
#include "whisper.h"

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <memory>
#include <mutex>
#include <sys/stat.h>
#include <unistd.h>
#include <vector>

namespace onhand_whisper {
namespace {
std::atomic<bool> busy{false};
std::once_flag logging_once;

void silent_log(enum ggml_log_level, const char *, void *) {}
bool abort_job(void *data) { return static_cast<Job *>(data)->stopped(); }
bool begin_encoder(whisper_context *, whisper_state *, void *data) { return !abort_job(data); }

struct BusyGuard {
    ~BusyGuard() { busy.store(false, std::memory_order_release); }
};
struct FileGuard {
    FILE *file;
    ~FileGuard() { if (file) std::fclose(file); }
};
size_t model_read(void *data, void *output, size_t size) {
    return std::fread(output, 1, size, static_cast<FILE *>(data));
}
bool model_eof(void *data) { return std::feof(static_cast<FILE *>(data)) != 0; }
void model_close(void *) {} // FileGuard owns it, including exceptional unwinds.

bool allowed_language(const std::string &language) {
    return language == "ko" || language == "en" || language == "ja" || language == "zh";
}
}

bool transcribe(Job &job, const std::string &model_path, const int16_t *samples,
                int count, const std::string &language, int threads, std::string &output) {
    output.clear();
    if (!samples || count < kSampleRate || count > kMaxSamples ||
            model_path.empty() || model_path.size() > 4096 || !allowed_language(language) ||
            job.stopped() || job.used.exchange(true)) return false;
    bool expected = false;
    if (!busy.compare_exchange_strong(expected, true, std::memory_order_acq_rel)) return false;
    BusyGuard active;
    try {
        std::call_once(logging_once, [] { whisper_log_set(silent_log, nullptr); });
        int fd = open(model_path.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
        if (fd < 0) return false;
        struct stat info{};
        if (fstat(fd, &info) != 0 || !S_ISREG(info.st_mode) || info.st_size != kModelBytes) {
            close(fd); return false;
        }
        FILE *file = fdopen(fd, "rb");
        if (!file) { close(fd); return false; }
        FileGuard source{file};
        whisper_model_loader loader{file, model_read, model_eof, model_close};
        auto context_params = whisper_context_default_params();
        context_params.use_gpu = false;
        context_params.flash_attn = false;
        if (job.stopped()) return false;
        // Do not abort the model loader with short reads: upstream read_safe does not
        // validate every read length. Cancellation discards the completed load instead.
        std::unique_ptr<whisper_context, decltype(&whisper_free)> context(
                whisper_init_with_params(&loader, context_params), whisper_free);
        if (!context || job.stopped() || !whisper_is_multilingual(context.get())) return false;
        std::vector<float> pcm(static_cast<size_t>(count));
        for (int i = 0; i < count; ++i) pcm[static_cast<size_t>(i)] = samples[i] / 32768.0f;
        auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        params.n_threads = std::max(1, std::min(4, threads));
        params.translate = false;
        params.no_context = true;
        params.no_timestamps = true;
        params.print_special = false;
        params.print_progress = false;
        params.print_realtime = false;
        params.print_timestamps = false;
        params.language = language.c_str();
        params.detect_language = false;
        params.greedy.best_of = 1;
        params.temperature = 0.0f;
        params.temperature_inc = 0.0f;
        // Do not silently truncate a rapid 30-second utterance at an arbitrary token cap.
        // The model context, PCM cap, output admission and deadline bound the work.
        params.max_tokens = 0;
        params.suppress_blank = true;
        params.suppress_nst = true;
        params.abort_callback = abort_job;
        params.abort_callback_user_data = &job;
        params.encoder_begin_callback = begin_encoder;
        params.encoder_begin_callback_user_data = &job;
        job.inference_started.store(true, std::memory_order_release);
        int status = whisper_full(context.get(), params, pcm.data(), count);
        std::fill(pcm.begin(), pcm.end(), 0.0f);
        if (status != 0 || job.stopped()) return false;
        int segments = whisper_full_n_segments(context.get());
        if (segments < 1 || segments > 1024) return false;
        for (int i = 0; i < segments; ++i) {
            const char *text = whisper_full_get_segment_text(context.get(), i);
            if (!text) { output.clear(); return false; }
            size_t bytes = strnlen(text, kMaxOutputBytes + 1);
            if (bytes > kMaxOutputBytes - output.size()) { output.clear(); return false; }
            output.append(text, bytes);
        }
        if (job.stopped()) { output.clear(); return false; }
        return !output.empty();
    } catch (...) {
        output.clear();
        return false;
    }
}
}
