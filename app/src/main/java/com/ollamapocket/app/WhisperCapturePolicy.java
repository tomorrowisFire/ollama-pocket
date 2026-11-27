package com.ollamapocket.app;

import java.util.Locale;

/** Bounds and conservative energy endpoint for one foreground Whisper recording. */
final class WhisperCapturePolicy {
    static final int SAMPLE_RATE = 16000;
    static final int MAX_SAMPLES = SAMPLE_RATE * 30;
    static final int FRAME_SAMPLES = 320;
    static final int MIN_SAMPLES = SAMPLE_RATE;
    static final int MAX_OUTPUT_BYTES = 16384;
    static final int MAX_OUTPUT_CHARS = 4096;
    private static final int REQUIRED_VOICE_SAMPLES = SAMPLE_RATE / 5;
    private static final int SILENCE_SAMPLES = SAMPLE_RATE * 6 / 5;

    private WhisperCapturePolicy() {}

    static String language(Locale locale) {
        String language = locale == null ? "" : locale.getLanguage();
        switch (language) {
            case "en": case "ko": case "ja": case "zh": return language;
            default: return null;
        }
    }

    static int threads(int available) { return Math.max(1, Math.min(4, available)); }

    static boolean validSamples(int length, int count) {
        return length >= count && length <= MAX_SAMPLES && count >= MIN_SAMPLES && count <= MAX_SAMPLES;
    }

    static boolean voiceFrame(short[] samples, int offset, int count) {
        if (samples == null || offset < 0 || count < 1 || count > FRAME_SAMPLES ||
                offset > samples.length - count) return false;
        long energy = 0;
        for (int i = offset; i < offset + count; i++) {
            long sample = samples[i];
            energy += sample * sample;
        }
        // An energy gate, not a semantic VAD or a claim of recognition quality.
        return energy >= 300L * 300L * count;
    }

    static final class Endpoint {
        private int samples, voiced, trailingSilence;
        void accept(short[] data, int offset, int count) {
            if (count < 1 || count > FRAME_SAMPLES || samples > MAX_SAMPLES - count ||
                    data == null || offset < 0 || offset > data.length - count)
                throw new IllegalArgumentException("Recording bounds");
            samples += count;
            if (voiceFrame(data, offset, count)) {
                voiced += count;
                trailingSilence = 0;
            } else {
                trailingSilence += count;
            }
        }
        boolean hasSpeech() { return voiced >= REQUIRED_VOICE_SAMPLES; }
        boolean ended() {
            return samples == MAX_SAMPLES ||
                    (samples >= MIN_SAMPLES && hasSpeech() && trailingSilence >= SILENCE_SAMPLES);
        }
    }
}
