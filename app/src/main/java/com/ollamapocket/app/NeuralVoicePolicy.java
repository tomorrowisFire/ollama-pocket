package com.ollamapocket.app;

import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Bounded, offline neural voice requests. Engine availability is not inferred from this policy. */
final class NeuralVoicePolicy {
    static final int SAMPLE_RATE = 16000;
    static final int MIN_CAPTURE = SAMPLE_RATE / 4;
    static final int MAX_CAPTURE = SAMPLE_RATE * 30;
    static final int MAX_TEXT = 240;
    static final int MAX_OUTPUT_SECONDS = 60;
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private NeuralVoicePolicy() {}

    static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled == null) throw new IllegalArgumentException("Cancellation signal required");
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }
    static String whisperLanguage(Locale locale) {
        String lang = locale == null ? "" : locale.getLanguage();
        if ("ko".equals(lang) || "en".equals(lang) || "zh".equals(lang) || "ja".equals(lang)) return lang;
        throw new IllegalArgumentException("Unsupported Whisper locale");
    }
    static String supertonicLanguage(Locale locale) {
        String lang = whisperLanguage(locale);
        if ("zh".equals(lang)) throw new IllegalArgumentException("Chinese requires another neural TTS model");
        return lang;
    }
    static int threads(int available) { return Math.max(1, Math.min(4, available)); }
    static float[] capture(short[] pcm, int count, BooleanSupplier cancelled) {
        checkCancelled(cancelled);
        if (pcm == null || count < MIN_CAPTURE || count > MAX_CAPTURE || count > pcm.length)
            throw new IllegalArgumentException("16 kHz capture length");
        float[] result = new float[count];
        for (int i = 0; i < count; i++) {
            if ((i & 4095) == 0) checkCancelled(cancelled);
            result[i] = pcm[i] / 32768.0f;
        }
        checkCancelled(cancelled);
        return result;
    }
    static String speechText(String text) {
        if (text == null || text.length() > MAX_TEXT || text.trim().isEmpty())
            throw new IllegalArgumentException("Synthesis text length");
        boolean content = false;
        for (int i=0;i<text.length();) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 0xD800 && cp <= 0xDFFF || Character.isISOControl(cp) && cp != '\n' && cp != '\r' && cp != '\t')
                throw new IllegalArgumentException("Invalid synthesis text");
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp) && Character.getType(cp) != Character.FORMAT) content=true;
        }
        if (!content) throw new IllegalArgumentException("Empty synthesis text");
        return text;
    }
    static float speed(float speed) {
        if (!Float.isFinite(speed) || speed < 0.8f || speed > 1.4f) throw new IllegalArgumentException("Speech speed");
        return speed;
    }
    static int outputLimit(int sampleRate) {
        if (sampleRate < 8000 || sampleRate > 48000) throw new IllegalArgumentException("Audio rate");
        return sampleRate * MAX_OUTPUT_SECONDS;
    }
    static short[] output(float[] samples, int sampleRate, BooleanSupplier cancelled) {
        checkCancelled(cancelled);
        int limit = outputLimit(sampleRate);
        if (samples == null || samples.length == 0 || samples.length > limit)
            throw new IllegalArgumentException("Synthesized audio length");
        short[] pcm = new short[samples.length];
        for (int i=0;i<samples.length;i++) {
            if ((i & 4095) == 0) checkCancelled(cancelled);
            float v = samples[i];
            if (!Float.isFinite(v)) throw new IllegalArgumentException("Nonfinite audio");
            pcm[i] = (short)Math.round(Math.max(-1f, Math.min(1f, v)) * (v < 0 ? 32768f : 32767f));
        }
        checkCancelled(cancelled);
        return pcm;
    }
    static Lease acquire(BooleanSupplier cancelled) {
        checkCancelled(cancelled);
        if (!BUSY.compareAndSet(false, true)) throw new IllegalStateException("Neural voice busy");
        return new Lease();
    }
    static final class Lease implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();
        private Lease() {}
        public void close() { if (closed.compareAndSet(false, true)) BUSY.set(false); }
    }
}
