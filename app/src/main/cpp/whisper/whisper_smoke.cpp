#include "whisper_core.h"
#include <algorithm>
#include <cctype>
#include <cstring>
#include <fstream>
#include <iostream>
#include <thread>
#include <vector>

namespace {
uint32_t u32(const unsigned char *p) {
    return p[0] | uint32_t(p[1]) << 8 | uint32_t(p[2]) << 16 | uint32_t(p[3]) << 24;
}
uint16_t u16(const unsigned char *p) { return p[0] | uint16_t(p[1]) << 8; }
std::vector<int16_t> wav(const char *path) {
    std::ifstream file(path, std::ios::binary | std::ios::ate);
    auto bytes = file.tellg();
    if (bytes < 44 || bytes > 2 * 1024 * 1024) throw "fixture size";
    std::vector<unsigned char> data(static_cast<size_t>(bytes));
    file.seekg(0); file.read(reinterpret_cast<char *>(data.data()), bytes);
    if (!file || std::memcmp(data.data(), "RIFF", 4) || std::memcmp(data.data() + 8, "WAVE", 4))
        throw "fixture header";
    bool format = false;
    for (size_t p = 12; p + 8 <= data.size();) {
        uint32_t length = u32(data.data() + p + 4);
        if (length > data.size() - p - 8) throw "fixture chunk";
        const unsigned char *chunk = data.data() + p + 8;
        if (!std::memcmp(data.data() + p, "fmt ", 4)) {
            if (length < 16 || u16(chunk) != 1 || u16(chunk + 2) != 1 ||
                    u32(chunk + 4) != 16000 || u16(chunk + 14) != 16) throw "fixture format";
            format = true;
        }
        if (!std::memcmp(data.data() + p, "data", 4)) {
            if (!format || length % 2 || length / 2 > onhand_whisper::kMaxSamples) throw "fixture samples";
            std::vector<int16_t> pcm(length / 2);
            for (size_t i = 0; i < pcm.size(); ++i) pcm[i] = static_cast<int16_t>(u16(chunk + 2 * i));
            return pcm;
        }
        p += 8 + length + (length & 1);
    }
    throw "fixture data";
}
void require(bool value, const char *message) { if (!value) throw message; }
}

int main(int argc, char **argv) {
    if (argc != 3) return 2;
    try {
        auto pcm = wav(argv[2]);
        std::string output;
        onhand_whisper::Job cancelled;
        cancelled.cancelled = true;
        require(!onhand_whisper::transcribe(cancelled, argv[1], pcm.data(), pcm.size(), "en", 2, output), "pre-cancel");
        onhand_whisper::Job expired(-1);
        require(!onhand_whisper::transcribe(expired, argv[1], pcm.data(), pcm.size(), "en", 2, output), "deadline");
        onhand_whisper::Job short_input;
        require(!onhand_whisper::transcribe(short_input, argv[1], pcm.data(), 1, "en", 2, output), "short input");
        onhand_whisper::Job too_long;
        require(!onhand_whisper::transcribe(too_long, argv[1], pcm.data(), 480001, "en", 2, output), "long input");
        onhand_whisper::Job language;
        require(!onhand_whisper::transcribe(language, argv[1], pcm.data(), pcm.size(), "unsupported", 2, output), "language");
        onhand_whisper::Job missing;
        require(!onhand_whisper::transcribe(missing, "/missing-model", pcm.data(), pcm.size(), "en", 2, output), "missing model");

        onhand_whisper::Job real;
        require(onhand_whisper::transcribe(real, argv[1], pcm.data(), pcm.size(), "en", 4, output), "real inference");
        std::transform(output.begin(), output.end(), output.begin(), [](unsigned char c) { return std::tolower(c); });
        require(output.find("ask not") != std::string::npos && output.find("country") != std::string::npos, "fixture transcript");
        require(!onhand_whisper::transcribe(real, argv[1], pcm.data(), pcm.size(), "en", 4, output), "reused job");
        require(output.empty(), "stale output");

        onhand_whisper::Job active;
        std::atomic<bool> done{false};
        bool completed = false;
        std::thread worker([&] {
            completed = onhand_whisper::transcribe(active, argv[1], pcm.data(), pcm.size(), "en", 2, output);
            done.store(true);
        });
        while (!active.inference_started.load(std::memory_order_acquire) && !done.load())
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        active.cancelled.store(true);
        worker.join();
        require(active.inference_started && !completed && output.empty(), "active cancellation");
        std::cout << "PASS: Whisper real-model fixture, bounds, one-shot and cancellation (11 checks)\n";
        return 0;
    } catch (const char *error) {
        std::cerr << "FAIL: Whisper smoke " << error << '\n';
    } catch (...) { std::cerr << "FAIL: Whisper smoke exception\n"; }
    return 1;
}
