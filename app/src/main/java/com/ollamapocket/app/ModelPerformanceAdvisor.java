package com.ollamapocket.app;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Heuristic resource admission and benchmark-derived, explicitly labelled throughput. */
public final class ModelPerformanceAdvisor {
    private ModelPerformanceAdvisor() {}

    public static final class Result {
        public final String suitability;
        public final String throughput;
        public final String explanation;
        public final boolean measured;
        Result(String suitability,String throughput,String explanation,boolean measured) {
            this.suitability=suitability;this.throughput=throughput;
            this.explanation=explanation;this.measured=measured;
        }
        public String summary() {
            return suitability+" · "+throughput+"\n"+explanation;
        }
    }

    private static final Pattern CPU_TPS=Pattern.compile(
            "CPU\\s*[·:]\\s*Ollama\\s*:\\s*생성\\s*([0-9]+(?:\\.[0-9]+)?)\\s*tok/s",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GPU_TPS=Pattern.compile(
            "GPU\\s*[·:]\\s*Vulkan\\s*:\\s*생성\\s*([0-9]+(?:\\.[0-9]+)?)\\s*tok/s",
            Pattern.CASE_INSENSITIVE);

    public static Double measuredCpuTps(String savedReport) {
        if(savedReport==null)return null;
        Matcher m=CPU_TPS.matcher(savedReport);
        if(!m.find())return null;
        try {double x=Double.parseDouble(m.group(1));return Double.isFinite(x)&&x>0?x:null;}
        catch(NumberFormatException ignored){return null;}
    }

    public static Double measuredGpuTps(String savedReport) {
        if(savedReport==null)return null;
        Matcher m=GPU_TPS.matcher(savedReport);
        if(!m.find())return null;
        try {double x=Double.parseDouble(m.group(1));return Double.isFinite(x)&&x>0?x:null;}
        catch(NumberFormatException ignored){return null;}
    }

    /**
     * Estimated *range* from actual on-device CPU benchmark calibration.
     * Heuristic only: sizes alone cannot model architecture, memory bandwidth,
     * CPU/GPU scheduling, KV cache, context or thermal throttling.
     */
    public static Result evaluate(boolean arm64,boolean lowMemory,
            double totalRamGiB,double availableRamGiB,double weightGiB,
            Double sameModelMeasuredTps, Double baselineMeasuredTps,
            double baselineWeightGiB) {
        if(!arm64)return new Result("미지원","속도 산출 불가",
            "이 앱의 추론 엔진은 ARM64 기기에서만 실행됩니다.",false);

        double total=Math.max(0,totalRamGiB);
        double free=Math.max(0,availableRamGiB);
        double weight=Math.max(0,weightGiB);
        // Rough memory reserve includes KV cache and per-engine execution buffers.
        double required=weight>0 ? weight*1.15+0.65 : 0;
        boolean constrained=lowMemory || (required>0 && total>0 && total<required+0.9);
        boolean pressure=required>0 && free>0 && free<required*0.80;
        String memory;
        if(weight<=0)memory="모델 용량 미확인";
        else memory=String.format(Locale.KOREA,
           "가중치 %.1fGiB · KV/작업 메모리 포함 최소 약 %.1fGiB 필요(추정)",weight,required);

        if(sameModelMeasuredTps!=null && Double.isFinite(sameModelMeasuredTps)
                && sameModelMeasuredTps>0) {
            double tps=sameModelMeasuredTps;
            String grade=tps>=15?"원활":tps>=7?"보통":"느림";
            if(constrained)grade="메모리 주의";
            return new Result(grade,
                String.format(Locale.KOREA,"실측 %.1f tok/s (지난 벤치마크)",tps),
                memory+"\n기기 온도·백그라운드 작업·추론 장치가 바뀌면 속도가 달라집니다.",true);
        }
        if(constrained)return new Result("권장하지 않음","예상 tok/s: 측정 전",
                memory+"\n현재 RAM 용량 또는 저메모리 상태에서는 종료 위험이 큽니다.",false);

        String grade=pressure?"주의":"실행 여유 예상";
        if(baselineMeasuredTps!=null && baselineMeasuredTps>0 &&
                Double.isFinite(baselineMeasuredTps) &&
                baselineWeightGiB>0 && weight>0 &&
                weight / baselineWeightGiB >= 0.2 &&
                weight / baselineWeightGiB <= 5.0) {
            // Cross-model interpolation is deliberately wide and labelled approximate.
            double midpoint=baselineMeasuredTps * baselineWeightGiB/weight;
            double lo=Math.max(0.1,midpoint*0.5);
            double hi=midpoint*1.5;
            return new Result(grade,
                String.format(Locale.KOREA,"참고 예상 %.1f~%.1f tok/s",lo,hi),
                memory+"\n같은 기기의 다른 모델 실측값과 가중치 크기로 추정한 넓은 범위이며, " +
                "실제 속도는 다를 수 있습니다.",false);
        }
        return new Result(grade,"예상 tok/s: 측정 전",
                memory+"\n기기 스펙만으로 속도를 신뢰성 있게 예측할 수 없습니다. " +
                "모델 벤치마크 실행 후 실측값을 표시합니다.",false);
    }
}
