package com.ollamapocket.app;

import java.util.Locale;

/** Bounds for the pinned Supertonic3 CPU model, independent of Android. */
final class NeuralTtsPolicy {
    static final int MAX_CODEPOINTS = 120;
    static final int MAX_TEXT_UTF16 = MAX_CODEPOINTS * 2;
    static final int SAMPLE_RATE = 44_100;
    static final int MAX_PCM_FRAMES = SAMPLE_RATE * 60;
    private NeuralTtsPolicy() { }

    static String language(Locale locale) {
        if (locale == null) return "";
        String language = locale.getLanguage();
        return language.equals("en") || language.equals("ko") || language.equals("ja") ? language : "";
    }

    static boolean validRate(float rate) {
        return Float.isFinite(rate) && rate >= .8f && rate <= 1.4f;
    }

    static boolean validChunk(String text) {
        if (text == null || text.isEmpty() || text.length() > MAX_TEXT_UTF16) return false;
        int codepoints = 0;
        boolean visible = false;
        for (int i = 0; i < text.length();) {
            char ch = text.charAt(i);
            if (Character.isLowSurrogate(ch)) return false;
            if (Character.isHighSurrogate(ch) &&
                    (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1)))) return false;
            int cp = text.codePointAt(i);
            if (++codepoints > MAX_CODEPOINTS || cp == 0 ||
                    (Character.isISOControl(cp) && cp != '\n' && cp != '\r' && cp != '\t')) return false;
            if (!Character.isWhitespace(cp)) visible = true;
            i += Character.charCount(cp);
        }
        return visible;
    }

    static boolean validPcm(float[] samples, int sampleRate) {
        if (sampleRate != SAMPLE_RATE || samples == null || samples.length == 0 ||
                samples.length > MAX_PCM_FRAMES) return false;
        for (float sample : samples) if (!Float.isFinite(sample) || sample < -1f || sample > 1f) return false;
        return true;
    }

    static long drainBudgetMillis(int frames) {
        if (frames <= 0 || frames > MAX_PCM_FRAMES) throw new IllegalArgumentException("PCM bounds");
        return 10_000L + (frames * 1_000L + SAMPLE_RATE - 1) / SAMPLE_RATE;
    }
}
