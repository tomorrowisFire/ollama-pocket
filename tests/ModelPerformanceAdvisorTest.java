import com.ollamapocket.app.ModelPerformanceAdvisor;
public final class ModelPerformanceAdvisorTest {
    static void ok(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    public static void main(String[] args){
        String old="CPU · Ollama: 생성 17.2 tok/s · 프롬프트 123.0 tok/s";
        ok(Math.abs(ModelPerformanceAdvisor.measuredCpuTps(old)-17.2)<1e-7,"read measured CPU");
        ok(ModelPerformanceAdvisor.measuredCpuTps("미측정")==null,"no invented tps");
        ok(ModelPerformanceAdvisor.measuredGpuTps("GPU · Vulkan: 생성 9.0 tok/s")==9,"GPU measured");
        ok("미지원".equals(ModelPerformanceAdvisor.evaluate(false,false,8,4,1,null,null,0).suitability),
                "non arm64 denied");
        ok("권장하지 않음".equals(ModelPerformanceAdvisor.evaluate(true,true,8,4,1,null,null,0).suitability),
                "low memory");
        ok("원활".equals(ModelPerformanceAdvisor.evaluate(true,false,8,4,1,22.0,null,0).suitability),
                "measured 22 tps");
        ok("보통".equals(ModelPerformanceAdvisor.evaluate(true,false,8,4,1,11.0,null,0).suitability),
                "measured 11 tps");
        ok("느림".equals(ModelPerformanceAdvisor.evaluate(true,false,8,4,1,3.0,null,0).suitability),
                "measured 3 tps");
        ok(ModelPerformanceAdvisor.evaluate(true,false,8,4,1,null,24.0,0.5).throughput.contains("참고 예상"),
                "calibrated range clearly labelled");
        ok(ModelPerformanceAdvisor.evaluate(true,false,8,4,1,null,null,0).throughput.contains("측정 전"),
                "no guessed number");
        ok("권장하지 않음".equals(ModelPerformanceAdvisor.evaluate(true,false,4,1,6,null,null,0).suitability),
                "too big for RAM");
        ok("주의".equals(ModelPerformanceAdvisor.evaluate(true,false,8,Double.NaN,1,null,null,0).suitability),"unknown free RAM");
        ok("주의".equals(ModelPerformanceAdvisor.evaluate(true,false,8,9,1,null,null,0).suitability),"inconsistent free RAM");
        ok("주의".equals(ModelPerformanceAdvisor.evaluate(true,false,Double.NaN,4,1,null,null,0).suitability),"unknown total RAM");
        ok("주의".equals(ModelPerformanceAdvisor.evaluate(true,false,8,4,Double.POSITIVE_INFINITY,null,null,0).suitability),"unknown size");
        ok("메모리 주의".equals(ModelPerformanceAdvisor.evaluate(true,false,8,1,1,22.0,null,0).suitability),"historical speed cannot override memory pressure");
        ok(!ModelPerformanceAdvisor.evaluate(true,false,8,4,1,Double.POSITIVE_INFINITY,null,0).measured,"invalid measurement");
        ok(!ModelPerformanceAdvisor.evaluate(true,false,8,4,1,100001.0,null,0).measured,"implausible measurement");
        System.out.println("PASS: ModelPerformanceAdvisor (18 assertions)");
    }
}
