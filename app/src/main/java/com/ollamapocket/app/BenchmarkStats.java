package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure Java benchmark calculations; tested separately from Android. */
public final class BenchmarkStats {
    private BenchmarkStats() {}

    public static double tokensPerSecond(long tokens, double millis) {
        return tokens > 0 && Double.isFinite(millis) && millis > 0
                ? tokens * 1000.0 / millis : 0.0;
    }

    public static double median(List<Double> samples) {
        List<Double> valid = new ArrayList<>();
        if (samples != null) for (Double value : samples)
            if (value != null && Double.isFinite(value) && value > 0) valid.add(value);
        if (valid.isEmpty()) return 0;
        Collections.sort(valid);
        int n = valid.size();
        return n % 2 == 1 ? valid.get(n/2) :
                (valid.get(n/2-1) + valid.get(n/2))/2.0;
    }

    /** Decay from first measured pass to last measured pass, not a thermal proof. */
    public static double dropPercent(double first, double last) {
        if (!(first > 0) || !(last > 0) || !Double.isFinite(first)
                || !Double.isFinite(last)) return 0;
        return Math.max(0, (first-last) * 100.0 / first);
    }

    /** Use a 5% margin to avoid switching modes for noise-level improvements. */
    public static String winner(String previous, double previousRate,
                                String candidate, double candidateRate) {
        if (!Double.isFinite(candidateRate) || candidateRate <= 0) return previous;
        if (previous == null || previousRate <= 0 || candidateRate > previousRate * 1.05)
            return candidate;
        return previous;
    }
}
