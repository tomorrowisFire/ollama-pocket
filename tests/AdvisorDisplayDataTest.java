package com.ollamapocket.app;

public final class AdvisorDisplayDataTest {
    private static int n;
    private static void ok(boolean v,String label){if(!v)throw new AssertionError(label);n++;}
    public static void main(String[] args){
        ModelPerformanceAdvisor.Result u=ModelPerformanceAdvisor.evaluate(false,false,16,12,1,null,null,0);
        ok(u.reason==ModelPerformanceAdvisor.Result.Reason.UNSUPPORTED,"unsupported");
        ModelPerformanceAdvisor.Result m=ModelPerformanceAdvisor.evaluate(true,false,16,12,2,15.25,null,0);
        ok(m.reason==ModelPerformanceAdvisor.Result.Reason.MEASURED&&m.measured&&m.measuredTps==15.25,"measured value");
        ok(Math.abs(m.requiredGiB-(2*1.15+.65))<1e-9,"original memory estimate");
        ModelPerformanceAdvisor.Result r=ModelPerformanceAdvisor.evaluate(true,false,16,12,2,null,10.0,2);
        ok(r.reason==ModelPerformanceAdvisor.Result.Reason.RANGE&&r.lowTps==5&&r.highTps==15,"range");
        ModelPerformanceAdvisor.Result constrained=ModelPerformanceAdvisor.evaluate(true,true,4,1,3,null,null,0);
        ok(constrained.reason==ModelPerformanceAdvisor.Result.Reason.MEMORY&&"권장하지 않음".equals(constrained.suitability),"admission unchanged");
        ModelPerformanceAdvisor.Result unmeasured=ModelPerformanceAdvisor.evaluate(true,false,16,12,0,null,null,0);
        ok(unmeasured.reason==ModelPerformanceAdvisor.Result.Reason.UNMEASURED&&!unmeasured.measured&&unmeasured.weightGiB==0,"unknown size");
        ok(ModelPerformanceAdvisor.measuredCpuTps("CPU · Ollama: 생성 17.5 tok/s · 프롬프트 9.0 tok/s")==17.5,"legacy parser");
        System.out.println("AdvisorDisplayDataTest PASS: "+n+" assertions");
    }
}
