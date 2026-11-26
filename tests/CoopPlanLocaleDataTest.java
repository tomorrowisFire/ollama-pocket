import com.ollamapocket.app.CoopMemoryPlanner;
import java.util.Arrays;
import java.util.Collections;
public final class CoopPlanLocaleDataTest {
    private static int n;
    private static void ok(boolean b,String name){if(!b)throw new AssertionError(name);n++;}
    public static void main(String[] args){
        CoopMemoryPlanner.Plan p=CoopMemoryPlanner.evaluate(0,7000,Arrays.asList(800L));
        ok(p.reasonCode==CoopMemoryPlanner.Plan.ReasonCode.UNKNOWN_MODEL&&!p.allowed,"unknown");
        p=CoopMemoryPlanner.evaluate(3800,7000,Collections.emptyList());
        ok(p.reasonCode==CoopMemoryPlanner.Plan.ReasonCode.NO_REMOTE&&!p.allowed,"no guest");
        p=CoopMemoryPlanner.evaluate(3800,300,Arrays.asList(6000L));
        ok(p.reasonCode==CoopMemoryPlanner.Plan.ReasonCode.HOST_LOW&&!p.allowed,"host reserve");
        p=CoopMemoryPlanner.evaluate(9200,6900,Arrays.asList(600L,700L));
        ok(p.reasonCode==CoopMemoryPlanner.Plan.ReasonCode.SHORTFALL&&!p.allowed,"shortfall");
        ok(p.requiredMiB-p.totalSafeMiB>0&&p.reason.contains("추가 확보"),"legacy gap");
        p=CoopMemoryPlanner.evaluate(4800,6400,Arrays.asList(550L,800L));
        ok(p.reasonCode==CoopMemoryPlanner.Plan.ReasonCode.PASS&&p.allowed,"pass");
        ok("358,608".equals(p.tensorSplit),"same split");
        ok(p.selectedIndices.equals(Arrays.asList(0,1)),"same order");
        System.out.println("CoopPlanLocaleDataTest PASS: "+n+" assertions");
    }
}
