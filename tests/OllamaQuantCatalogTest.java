import com.ollamapocket.app.OllamaQuantCatalog;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
public final class OllamaQuantCatalogTest {
    private static void ok(boolean state,String msg){if(!state)throw new AssertionError(msg);}
    public static void main(String[] args){
        String html="<a href='/library/qwen3:1.7b-q4_K_M'>qwen3:1.7b-q4_K_M</a>"+
            "<p>qwen3:1.7b-q8_0</p> qwen3:1.7b-fp16 qwen3:14b-q8_0 "+
            "qwen3:1.7b-does-not-exist"+" qwen2.5:1.7b-q5_K_M";
        List<OllamaQuantCatalog.Variant> v=OllamaQuantCatalog.parse("qwen3:1.7b",html,
            Arrays.asList("qwen3:1.7b-q8_0"));
        ok(v.stream().anyMatch(x->x.tag.equals("qwen3:1.7b-q4_K_M")),"real q4");
        ok(v.stream().anyMatch(x->x.tag.equals("qwen3:1.7b-q8_0")),"real q8");
        ok(v.stream().anyMatch(x->x.tag.equals("qwen3:1.7b-fp16")),"real fp16");
        ok(!v.stream().anyMatch(x->x.tag.equals("qwen3:14b-q8_0")),"no other size");
        ok(!v.stream().anyMatch(x->x.tag.contains("does-not-exist")),"no invented suffix");
        ok(!v.stream().anyMatch(x->x.tag.startsWith("qwen2.5:")),"no other repo");
        ok(v.get(0).installed,"installed version first");
        ok(v.stream().anyMatch(x->x.defaultTag),"default fallback");
        ok("qwen3".equals(OllamaQuantCatalog.repoName("qwen3:1.7b")),"repo");
        ok(!OllamaQuantCatalog.validBaseTag("qwen3:foo/../test"),"path injection denied");
        ok(OllamaQuantCatalog.belongsToModel("qwen3:1.7b","qwen3:1.7b-q4_K_M"),"belongs");
        ok(!OllamaQuantCatalog.belongsToModel("qwen3:1.7b","qwen3:14b"),"does not belong");
        ok(OllamaQuantCatalog.parse("gemma3:1b","",Collections.emptyList()).size()==1,
            "offline fallback only base");
        String familyPage="<a href='/library/qwen3:0.6b'>0.6b</a>"+
            "<a href='/library/qwen3:0.6b-q4_K_M'>q4</a>"+
            "<a href='/library/qwen3:1.7b-q8_0'>q8</a>"+
            "<a href='/library/qwen3:4b'>4b</a>"+
            "<a href='/library/qwen3:235b-cloud'>cloud</a>"+
            "<a href='/library/llama3:8b'>not qwen</a>";
        List<OllamaQuantCatalog.Variant> family=OllamaQuantCatalog.parseFamily(
            "qwen3",familyPage,Collections.emptyList());
        ok(family.size()==4,"family registry versions");
        ok("qwen3:0.6b".equals(family.get(0).tag),"sort small models first");
        ok(family.stream().anyMatch(x->x.tag.equals("qwen3:0.6b-q4_K_M")),"family q4");
        ok(family.stream().anyMatch(x->x.tag.equals("qwen3:1.7b-q8_0")),"family q8");
        ok(family.stream().noneMatch(x->x.tag.contains("llama")),"no other family");
        ok(family.stream().noneMatch(x->x.tag.contains("-cloud")),"no cloud-only on Android");
        ok(OllamaQuantCatalog.parseFamily("qwen3","",Collections.emptyList()).isEmpty(),
           "offline no invented versions");
        List<OllamaQuantCatalog.Variant> localOnly=OllamaQuantCatalog.parse("qwen3:1.7b",
            "qwen3:1.7b-cloud-q4_K_M qwen3:1.7b-q8_0/path qwen3:1.7b-q6_K:extra",
            Arrays.asList("qwen3:1.7b-cloud-q8_0", "qwen3:1.7b-q5_K_M"));
        ok(localOnly.size()==2,"local-only exact version tokens");
        ok(localOnly.get(0).installed&&localOnly.get(0).tag.equals("qwen3:1.7b-q5_K_M"),"installed local variant preserved");
        ok(OllamaQuantCatalog.parseFamily("qwen3","<a href='/library/qwen3:cloud'>cloud</a>",
            Arrays.asList("qwen3:235b-CLOUD", "qwen3:cloud", "qwen3:4b")).size()==1,"cloud tags excluded from installed and registry sources");
        boolean cloudRejected=false;
        try{OllamaQuantCatalog.parse("qwen3:235b-cloud","",Collections.emptyList());}
        catch(IllegalArgumentException expected){cloudRejected=true;}
        ok(cloudRejected,"cloud base cannot become a local pull candidate");
        System.out.println("PASS: OllamaQuantCatalog (24 assertions)");
    }
}
