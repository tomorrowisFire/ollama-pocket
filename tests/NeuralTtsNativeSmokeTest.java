package com.ollamapocket.app;

import java.io.File;

/** Actual model/JNI smoke evidence; no device timing or listening-quality claim. */
public final class NeuralTtsNativeSmokeTest {
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "--melo".equals(args[0])) {
            testMelo(new File(args[1]).getCanonicalFile());
            return;
        }
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
    private static void testMelo(File directory) throws Exception {
        String[] texts = {"你好，今天是11月27日。", "我正在学习 machine learning。"};
        try (NativeNeuralTts engine = NativeNeuralTts.openMelo(directory)) {
            for (int i = 0; i < texts.length; i++) {
                float[] audio = engine.generate(texts[i], "zh", 1f, () -> false);
                if (!NeuralTtsPolicy.validPcm(audio, 44_100)) throw new AssertionError("Invalid Chinese waveform");
                double energy = 0;
                for (float sample : audio) energy += sample * sample;
                if (energy < .001 || audio.length < 4410) throw new AssertionError("Silent/short Chinese waveform");
                System.out.printf("PASS: MeloTTS Chinese fixture %d frames=%d rate=44100 finite/nonzero%n", i + 1, audio.length);
            }
            if (engine.generate(texts[0], "zh", 1f, () -> true) != null) throw new AssertionError("Melo pre-cancel failed");
            java.util.concurrent.atomic.AtomicInteger queries = new java.util.concurrent.atomic.AtomicInteger();
            if (engine.generate(texts[0], "zh", 1f, () -> queries.incrementAndGet() >= 3) != null)
                throw new AssertionError("Cancelled Melo result escaped");
            for (String wrong : new String[]{"en", "ko", "ja"}) {
                boolean rejected = false;
                try { engine.generate(texts[0], wrong, 1f, () -> false); }
                catch (IllegalArgumentException expected) { rejected = true; }
                if (!rejected) throw new AssertionError("Wrong Melo language admitted: " + wrong);
            }
            boolean rejected = false;
            try { engine.generate("你".repeat(121), "zh", 1f, () -> false); }
            catch (IllegalArgumentException expected) { rejected = true; }
            if (!rejected) throw new AssertionError("Melo text bound failed");
            System.out.println("PASS: MeloTTS JNI cancellation, wrong-family languages and bounds");
        }
    }

}
