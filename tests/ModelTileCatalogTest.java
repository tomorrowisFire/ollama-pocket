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
        ok(ModelTileCatalog.displayBytes(true,1000,700)==700,"local observed size");
        ok(ModelTileCatalog.displayBytes(true,1000,0)==0,"unknown local size is not advertised size");
        ok(ModelTileCatalog.displayBytes(false,1000,700)==1000,"registry size for absent model");
        ok(ModelTileCatalog.displayBytes(true,1000,-1)==0,"invalid size");
        ok("Q8_0".equals(ModelTileCatalog.displayQuant(true,"Q4_K_M","q8_0")),"local observed quant");
        ok("미확인".equals(ModelTileCatalog.displayQuant(true,"Q4_K_M",null)),"unknown local quant");
        ok("Q4_K_M".equals(ModelTileCatalog.displayQuant(false,"q4_k_m","Q8_0")),"advertised quant for absent model");
        System.out.println("PASS: ModelTileCatalog (19 assertions)");
    }
}
