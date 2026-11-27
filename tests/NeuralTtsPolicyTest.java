package com.ollamapocket.app;

import java.util.Locale;

public final class NeuralTtsPolicyTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    public static void main(String[] args) {
        check(NeuralTtsPolicy.language(Locale.KOREAN).equals("ko"));
        check(NeuralTtsPolicy.language(Locale.US).equals("en"));
        check(NeuralTtsPolicy.language(Locale.JAPANESE).equals("ja"));
        check(NeuralTtsPolicy.language(Locale.SIMPLIFIED_CHINESE).equals("zh"));
        check(NeuralTtsPolicy.language(Locale.TRADITIONAL_CHINESE).isEmpty());
        check(NeuralTtsPolicy.language(Locale.ROOT).isEmpty());
        check(NeuralTtsPolicy.language(null).isEmpty());
        routeTests();
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
    private static void routeTests() {
        Locale[] locales = {null, Locale.ROOT, Locale.KOREA, Locale.US, Locale.JAPAN,
            Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE, Locale.forLanguageTag("zh-Hans"),
            Locale.forLanguageTag("zh-Hant"), Locale.forLanguageTag("zh-SG"), Locale.forLanguageTag("zh-HK"),
            Locale.forLanguageTag("zh-MO"), Locale.CHINESE, Locale.forLanguageTag("zh-Latn-CN"),
            Locale.forLanguageTag("zh-Hant-CN"), Locale.FRENCH};
        String[] expected = {"", "", "ko", "en", "ja", "zh", "", "zh", "", "zh", "", "", "", "", "", ""};
        for (int i = 0; i < locales.length; i++) {
            String language = expected[i];
            check(NeuralTtsPolicy.language(locales[i]).equals(language));
            for (int sdk : new int[]{26, 27, 36}) {
                for (int mask = 0; mask < 16; mask++) {
                    boolean superEnabled = (mask & 1) != 0, meloEnabled = (mask & 2) != 0;
                    boolean superReady = (mask & 4) != 0, meloReady = (mask & 8) != 0;
                    int[] reads = {0, 0};
                    String chosen = NeuralTtsPolicy.selectLanguage(locales[i], sdk, superEnabled, meloEnabled,
                        () -> { reads[0]++; return superReady; }, () -> { reads[1]++; return meloReady; });
                    boolean superProbe = sdk >= 27 && !language.isEmpty() && !"zh".equals(language) && superEnabled;
                    boolean meloProbe = sdk >= 27 && "zh".equals(language) && meloEnabled;
                    check(chosen.equals((superProbe && superReady || meloProbe && meloReady) ? language : ""));
                    check(reads[0] == (superProbe ? 1 : 0) && reads[1] == (meloProbe ? 1 : 0));
                }
            }
        }
        check(NeuralTtsPolicy.selectLanguage(Locale.US, 27, true, true, null, () -> true).isEmpty());
        check(NeuralTtsPolicy.selectLanguage(Locale.CHINA, 27, true, true, () -> true, null).isEmpty());
    }

}
