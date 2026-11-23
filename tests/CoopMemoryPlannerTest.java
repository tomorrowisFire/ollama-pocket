import com.ollamapocket.app.CoopMemoryPlanner;
import java.util.Arrays;
import java.util.Collections;

public final class CoopMemoryPlannerTest {
    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
    public static void main(String[] args) {
        long small = CoopMemoryPlanner.safeMiB(4096, 2048, 384, false, false);
        check(small >= 384 && small <= 1024, "4GB realistic memory budget");
        check(CoopMemoryPlanner.safeMiB(4096, 512, 400, false, false) == 0,
                "low available memory stops node admission");
        check(CoopMemoryPlanner.safeMiB(4096, 2048, 384, true, false) == 0,
                "Android lowMemory stops node admission");
        check(CoopMemoryPlanner.safeMiB(16384, 13000, 700, false, true) < 12000,
                "16GB host does not expose all RAM");
        CoopMemoryPlanner.Plan smallModel = CoopMemoryPlanner.evaluate(
                4800, 6400, Arrays.asList(550L, 800L));
        check(smallModel.allowed, "8B-class model fits conservative 2-node example");
        check(smallModel.selectedIndices.size() == 2, "both nodes selected");
        check("358,608".equals(smallModel.tensorSplit), "proportional remote split");
        CoopMemoryPlanner.Plan large = CoopMemoryPlanner.evaluate(
                9200, 6900, Arrays.asList(600L, 700L));
        check(!large.allowed, "14B is denied without sufficient safe RAM");
        check(large.reason.contains("추가 확보"), "denial explains shortage");
        CoopMemoryPlanner.Plan excluded = CoopMemoryPlanner.evaluate(
                3800, 6000, Arrays.asList(200L, 600L));
        check(excluded.allowed && excluded.selectedIndices.equals(Collections.singletonList(1))
                && "408".equals(excluded.tensorSplit), "exclude unsafe node consistently");
        check(!CoopMemoryPlanner.evaluate(3800, 7000,
                Collections.emptyList()).allowed, "RPC needs a remote node");
        check(!CoopMemoryPlanner.evaluate(3800, 300,
                Arrays.asList(6000L)).allowed, "host must reserve KV/work memory");
        System.out.println("PASS: CoopMemoryPlanner (9 assertions)");
    }
}
