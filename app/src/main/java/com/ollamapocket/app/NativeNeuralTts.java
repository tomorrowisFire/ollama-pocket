package com.ollamapocket.app;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

/** Worker-only, CPU-only bridge. One owner must serialize open/generate/close. */
final class NativeNeuralTts implements AutoCloseable {
    private long handle;
    private final boolean meloCandidate;

    NativeNeuralTts(File directory) throws IOException {
        this(directory, false);
    }

    /** Host qualification of pinned MeloTTS assets only; not an installed-model selector. */
    static NativeNeuralTts openMeloCandidate(File directory) throws IOException {
        return new NativeNeuralTts(directory, true);
    }

    private NativeNeuralTts(File directory, boolean meloCandidate) throws IOException {
        this.meloCandidate = meloCandidate;
        if (directory == null || !directory.isDirectory() ||
                !directory.getAbsoluteFile().equals(directory.getCanonicalFile())) {
            throw new IOException("Invalid TTS model directory");
        }
        System.loadLibrary("onhand_tts");
        handle = nativeOpen(directory.getCanonicalPath(), meloCandidate);
        if (handle == 0) throw new IOException("TTS model could not be loaded");
    }

    float[] generate(String text, String language, float rate, BooleanSupplier cancelled) {
        boolean supported = meloCandidate ? "zh".equals(language) :
                ("en".equals(language) || "ko".equals(language) || "ja".equals(language));
        if (handle == 0 || !NeuralTtsPolicy.validChunk(text) || !NeuralTtsPolicy.validRate(rate) ||
                !supported || cancelled == null) {
            throw new IllegalArgumentException("Invalid TTS request");
        }
        if (cancelled.getAsBoolean()) return null;
        // Standard UTF-8, not JNI modified UTF-8: supplementary Unicode stays intact.
        float[] audio = nativeGenerate(handle, text.getBytes(StandardCharsets.UTF_8), language, rate, cancelled);
        if (cancelled.getAsBoolean()) return null;
        if (!NeuralTtsPolicy.validPcm(audio, NeuralTtsPolicy.SAMPLE_RATE)) {
            throw new IllegalStateException("Invalid TTS waveform");
        }
        return audio;
    }

    @Override public void close() {
        if (handle != 0) {
            nativeClose(handle);
            handle = 0;
        }
    }

    private static native long nativeOpen(String directory, boolean meloCandidate);
    private static native float[] nativeGenerate(long handle, byte[] utf8, String language, float rate,
                                                BooleanSupplier cancelled);
    private static native void nativeClose(long handle);
}
