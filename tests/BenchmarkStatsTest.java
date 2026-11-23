import com.ollamapocket.app.BenchmarkStats;
import java.util.Arrays;
import java.util.Collections;

public final class BenchmarkStatsTest {
    private static void ok(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        ok(Math.abs(BenchmarkStats.tokensPerSecond(96,3000)-32) < 1e-8, "tokens per second");
        ok(BenchmarkStats.tokensPerSecond(0,10) == 0, "zero count");
        ok(BenchmarkStats.tokensPerSecond(100,0) == 0, "zero duration");
        ok(Math.abs(BenchmarkStats.median(Arrays.asList(12.0,21.0,15.0))-15)<1e-8, "odd median");
        ok(Math.abs(BenchmarkStats.median(Arrays.asList(10.0,20.0))-15)<1e-8, "even median");
        ok(BenchmarkStats.median(Collections.emptyList()) == 0, "missing data");
        ok(Math.abs(BenchmarkStats.dropPercent(20,15)-25)<1e-8, "decay");
        ok(BenchmarkStats.dropPercent(20,25) == 0, "improvement not decay");
        ok("cpu".equals(BenchmarkStats.winner("cpu",20,"gpu",20.8)), "no noisy switch");
        ok("gpu".equals(BenchmarkStats.winner("cpu",20,"gpu",25)), "real speed win");
        System.out.println("PASS: BenchmarkStats (10 assertions)");
    }
}
