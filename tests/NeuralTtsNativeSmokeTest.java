package com.ollamapocket.app;

import java.io.File;

/** Actual model/JNI smoke evidence; no device timing or listening-quality claim. */
public final class NeuralTtsNativeSmokeTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("model directory required");
        try (NativeNeuralTts engine = new NativeNeuralTts(new File(args[0]).getCanonicalFile())) {
            String[] languages = {"ko", "en", "ja"};
            String[] texts = {"안녕하세요. 음성 안내를 시작합니다.", "Hello. Offline speech is ready.", "こんにちは。音声案内を開始します。"};
            for (int i = 0; i < languages.length; i++) {
                float[] audio = engine.generate(texts[i], languages[i], 1f, () -> false);
                if (!NeuralTtsPolicy.validPcm(audio, 44_100)) throw new AssertionError("Invalid waveform");
                double energy = 0;
                for (float sample : audio) energy += sample * sample;
                if (energy < .001 || audio.length < 4410) throw new AssertionError("Silent/short waveform");
                System.out.printf("PASS: neural TTS JNI %s frames=%d rate=44100 finite/nonzero\n", languages[i], audio.length);
            }
            if (engine.generate(texts[0], "ko", 1f, () -> true) != null) throw new AssertionError("Pre-cancel failed");
            // The supplier turns true after native admission. The pinned runtime
            // may finish this bounded synthesis; the result must never be returned.
            java.util.concurrent.atomic.AtomicInteger queries = new java.util.concurrent.atomic.AtomicInteger();
            if (engine.generate(texts[0], "ko", 1f, () -> queries.incrementAndGet() >= 3) != null)
                throw new AssertionError("Cancelled native result escaped");
            boolean rejected = false;
            try { engine.generate("a".repeat(121),"en",1f,()->false); }
            catch (IllegalArgumentException expected) { rejected = true; }
            if (!rejected) throw new AssertionError("Text admission failed");
            rejected = false;
            try { engine.generate("你好","zh",1f,()->false); }
            catch (IllegalArgumentException expected) { rejected = true; }
            if (!rejected) throw new AssertionError("Unsupported locale admitted");
            System.out.println("PASS: neural TTS JNI pre-cancel, in-flight result suppression, bounds and language rejection");
        }
    }
}
