import com.ollamapocket.app.ModelTileCatalog;
import java.util.*;
public final class ModelTileCatalogTest {
    static void ok(boolean condition,String msg){if(!condition)throw new AssertionError(msg);}
    public static void main(String[] args){
        List<ModelTileCatalog.Tile> first=ModelTileCatalog.combine(
            Arrays.asList("qwen3-coder:30b","gemma3:1b"),
            Collections.singletonMap("qwen3-coder:30b",19_000_000_000L),Collections.emptyMap());
        ok(first.get(0).id.equals("qwen3-coder:30b"),"installed first");
        ok(first.get(0).installed,"installed verified");
        ok(first.get(0).bytes>0,"installed bytes from engine");
        ok(first.stream().noneMatch(t->!t.installed&&"qwen3-coder:30b".equals(t.id)),
            "no duplicate installed card");
        ok(first.stream().anyMatch(t->t.id.equals("gemma4:e2b")&&!t.installed),
            "suggest not installed card");
        ok("다운로드 중".equals(ModelTileCatalog.status(first.get(0),"qwen3-coder:30b")),
            "download status driven by real active tag");
        ok("설치됨".equals(ModelTileCatalog.status(first.get(0),"")),
            "existing model status");
        List<ModelTileCatalog.Tile> empty=ModelTileCatalog.combine(
            Collections.emptyList(),Collections.emptyMap(),Collections.emptyMap());
        ok(empty.size()==4,"four nonblank featured cards");
        ok(empty.stream().noneMatch(t->t.id.isEmpty()),"no placeholder");
        ok("Qwen3 Coder".equals(ModelTileCatalog.familyName("qwen3-coder:30b")),
            "friendly brand label");
        ok("미설치".equals(ModelTileCatalog.status(empty.get(0),"")),
            "accurate absent state");
        ok(empty.stream().noneMatch(t->t.installed),"no assumed installations");
        System.out.println("PASS: ModelTileCatalog (12 assertions)");
    }
}
