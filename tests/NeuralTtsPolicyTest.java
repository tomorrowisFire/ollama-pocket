package com.ollamapocket.app;

import java.util.Locale;

public final class NeuralTtsPolicyTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    public static void main(String[] args) {
        check(NeuralTtsPolicy.language(Locale.KOREAN).equals("ko"));
        check(NeuralTtsPolicy.language(Locale.US).equals("en"));
        check(NeuralTtsPolicy.language(Locale.JAPANESE).equals("ja"));
        check(NeuralTtsPolicy.language(Locale.SIMPLIFIED_CHINESE).isEmpty());
        check(NeuralTtsPolicy.language(Locale.TRADITIONAL_CHINESE).isEmpty());
        check(NeuralTtsPolicy.language(Locale.ROOT).isEmpty());
        check(NeuralTtsPolicy.language(null).isEmpty());
        check(!NeuralTtsPolicy.validChunk(null));
        check(!NeuralTtsPolicy.validChunk(""));
        check(!NeuralTtsPolicy.validChunk(" \t\r\n"));
        check(NeuralTtsPolicy.validChunk("첫 줄\nSecond line\tこんにちは。"));
        check(NeuralTtsPolicy.validChunk("가".repeat(120)));
        check(!NeuralTtsPolicy.validChunk("가".repeat(121)));
        check(NeuralTtsPolicy.validChunk("😀".repeat(120)));
        check(!NeuralTtsPolicy.validChunk("😀".repeat(121)));
        check(!NeuralTtsPolicy.validChunk("broken\ud83d"));
        check(!NeuralTtsPolicy.validChunk("\udc00broken"));
        for (int ch = 0; ch < 32; ch++) {
            boolean allowed = ch == '\n' || ch == '\r' || ch == '\t';
            check(NeuralTtsPolicy.validChunk("a" + (char) ch) == allowed);
        }
        for (float rate : new float[]{.8f,1f,1.2f,1.4f}) check(NeuralTtsPolicy.validRate(rate));
        for (float rate : new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,.799f,1.401f,0f}) check(!NeuralTtsPolicy.validRate(rate));
        check(NeuralTtsPolicy.validPcm(new float[]{0,.2f,-.2f,1,-1},44_100));
        for (float bad : new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,1.001f,-1.001f}) check(!NeuralTtsPolicy.validPcm(new float[]{bad},44_100));
        check(!NeuralTtsPolicy.validPcm(null,44_100));
        check(!NeuralTtsPolicy.validPcm(new float[0],44_100));
        check(!NeuralTtsPolicy.validPcm(new float[1],24_000));
        check(NeuralTtsPolicy.validPcm(new float[NeuralTtsPolicy.MAX_PCM_FRAMES],44_100));
        check(!NeuralTtsPolicy.validPcm(new float[NeuralTtsPolicy.MAX_PCM_FRAMES+1],44_100));
        check(NeuralTtsPolicy.drainBudgetMillis(44_100)==11_000);
        check(NeuralTtsPolicy.drainBudgetMillis(NeuralTtsPolicy.MAX_PCM_FRAMES)==70_000);
        System.out.println("PASS: " + checks + " neural TTS boundary checks");
    }
}
