#ifndef ONHAND_WHISPER_CORE_H
#define ONHAND_WHISPER_CORE_H

#include <atomic>
#include <chrono>
#include <cstdint>
#include <string>

namespace onhand_whisper {
constexpr int kSampleRate = 16000;
constexpr int kMaxSamples = kSampleRate * 30;
constexpr size_t kMaxOutputBytes = 16384;
constexpr int64_t kModelBytes = 77691713;

struct Job {
    std::atomic<bool> cancelled{false};
    std::atomic<bool> used{false};
    std::atomic<bool> inference_started{false};
    // Initialized once before publication; never written concurrently with callbacks.
    const std::chrono::steady_clock::time_point deadline;
    explicit Job(int timeout_ms = 120000)
        : deadline(std::chrono::steady_clock::now() + std::chrono::milliseconds(timeout_ms)) {}
    bool stopped() const {
        return cancelled.load(std::memory_order_relaxed) ||
               std::chrono::steady_clock::now() >= deadline;
    }
};

// The caller verifies the pinned model SHA-256 and holds its model-store lease.
// Context construction, inference and destruction all stay on this worker call.
bool transcribe(Job &job, const std::string &model_path, const int16_t *samples,
                int count, const std::string &language, int threads, std::string &output);
}
#endif
