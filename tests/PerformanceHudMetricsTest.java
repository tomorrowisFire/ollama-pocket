import com.ollamapocket.app.PerformanceHudMetrics;

public final class PerformanceHudMetricsTest {
    private static void ok(boolean test,String label){if(!test)throw new AssertionError(label);}
    public static void main(String[] args){
        long[] a=PerformanceHudMetrics.cpuTicks("cpu  100 0 40 860 0 0 0 0");
        long[] b=PerformanceHudMetrics.cpuTicks("cpu  140 0 60 900 0 0 0 0");
        ok(a!=null&&a[0]==1000&&a[1]==860,"tick baseline");
        ok(Math.abs(PerformanceHudMetrics.cpuPercent(a,b)-60)<1e-7,"actual delta load");
        ok(PerformanceHudMetrics.cpuPercent(null,b)==null,"no fake initial CPU");
        ok(PerformanceHudMetrics.cpuTicks("cpu0 1 2 3")==null,"ignore per-core row");
        ok(PerformanceHudMetrics.gpuPercent("57 %") == 57,"GPU percent driver");
        ok(PerformanceHudMetrics.gpuPercent("126") == null,"invalid GPU metric rejected");
        ok(PerformanceHudMetrics.gpuPercent(null)==null,"unknown GPU");
        ok("조회 불가".equals(PerformanceHudMetrics.percent(null)),"missing values explicit");
        ok(Math.abs(PerformanceHudMetrics.processCpuPercent(500,1000,4)-12.5)<1e-7,
                "app CPU utilization normalized by cores");
        ok(PerformanceHudMetrics.processCpuPercent(10,0,4)==null,"invalid wall time");
        ok(PerformanceHudMetrics.processCpuPercent(10,100,0)==null,"invalid core count");
        System.out.println("PASS: PerformanceHudMetrics (11 assertions)");
    }
}
