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
        ok(BenchmarkStats.strictMedian(Arrays.asList(0.0,0.0,100.0),3,true)==0,"true zero latency");
        ok(BenchmarkStats.strictMedian(Arrays.asList(30.0,10.0,20.0),3,false)==20,"three complete passes");
        ok(Double.isNaN(BenchmarkStats.strictMedian(Arrays.asList(10.0,20.0),3,false)),"missing sample");
        ok(Double.isNaN(BenchmarkStats.strictMedian(Arrays.asList(10.0,0.0,20.0),3,false)),"zero throughput rejected");
        ok(Double.isNaN(BenchmarkStats.strictMedian(Arrays.asList(10.0,null,20.0),3,false)),"null sample");
        ok(Double.isNaN(BenchmarkStats.strictMedian(Arrays.asList(10.0,Double.NaN,20.0),3,false)),"NaN sample");
        ok(Double.isNaN(BenchmarkStats.strictMedian(Arrays.asList(10.0,-1.0,20.0),3,true)),"negative latency");
        ok(BenchmarkStats.worstDrop(Arrays.asList(20.0,10.0,19.0),3)==50,"middle-pass decline retained");
        ok(BenchmarkStats.worstDrop(Arrays.asList(20.0,21.0,23.0),3)==0,"no decline");
        ok(Double.isNaN(BenchmarkStats.worstDrop(Arrays.asList(20.0,0.0,19.0),3)),"invalid decline");
        ok(BenchmarkStats.reportRepeats(1,3)==2,"legacy denominator");
        ok(BenchmarkStats.reportRepeats(2,3)==3,"new denominator");
        try{BenchmarkStats.reportRepeats(2,2);throw new AssertionError("invalid protocol");}catch(IllegalArgumentException expected){}
        ok(BenchmarkStats.median(Arrays.asList(Double.MAX_VALUE,Double.MAX_VALUE))==Double.MAX_VALUE,"finite median");
        ok(Math.abs(BenchmarkStats.dropPercent(Double.MAX_VALUE,Double.MAX_VALUE/2)-50)<1e-8,"finite decline");
        ok("gpu".equals(BenchmarkStats.winner("cpu",Double.NaN,"gpu",25)),"invalid baseline");
        System.out.println("PASS: BenchmarkStats (26 assertions)");
    }
}
