package com.ollamapocket.app;

/** Only verified device benchmarks adjust the existing RAM/size suitability tier. */
public final class BenchmarkRecommendation {
    private BenchmarkRecommendation(){}
    public static double adjustment(Double measuredCpuTps,Double measuredGpuTps){
        Double rate=null;
        if(measuredCpuTps!=null&&Double.isFinite(measuredCpuTps)&&measuredCpuTps>0)
            rate=measuredCpuTps;
        if(measuredGpuTps!=null&&Double.isFinite(measuredGpuTps)&&measuredGpuTps>0)
            rate=rate==null?measuredGpuTps:Math.max(rate,measuredGpuTps);
        if(rate==null)return 0;
        if(rate<3)return 32;
        if(rate<7)return 17;
        if(rate<12)return 2;
        if(rate<20)return -8;
        if(rate<30)return -16;
        return -24;
    }
    public static double bestMeasured(Double cpu,Double gpu){
        double c=cpu!=null&&Double.isFinite(cpu)&&cpu>0?cpu:0;
        double g=gpu!=null&&Double.isFinite(gpu)&&gpu>0?gpu:0;
        return Math.max(c,g);
    }
}
