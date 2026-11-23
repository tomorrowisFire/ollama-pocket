import com.ollamapocket.app.ModelTileCatalog;
import java.util.*;
public final class DeviceRecommendationGridTest {
    static void ok(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    public static void main(String[] args){
        List<String> ids=Arrays.asList("gemma3:270m","qwen2.5:0.5b","gemma3:1b","qwen2.5:1.5b","llama3.2:1b","qwen2.5:3b","llama3.2:3b");
        List<ModelTileCatalog.Tile> tiles=ModelTileCatalog.forDevice(
            ids,Collections.emptyMap(),Collections.emptyMap(),
            Arrays.asList("qwen2.5:3b","unrecommended:14b"),
            Collections.singletonMap("qwen2.5:3b",2000000000L),
            Collections.emptyMap(),6);
        ok(tiles.size()==6,"six max");
        ok(tiles.get(0).id.equals("qwen2.5:3b")&&tiles.get(0).installed,"installed priority");
        ok(tiles.stream().noneMatch(t->t.id.equals("unrecommended:14b")),"unsupported excluded");
        ok(tiles.stream().map(t->t.id).distinct().count()==6,"no duplicates");
        ok(tiles.stream().noneMatch(t->t.id.isEmpty()),"no empty filler");
        ok(tiles.stream().allMatch(t->t.featured),"all device recommendations");
        ok(ModelTileCatalog.forDevice(ids,null,null,null,null,null,3).size()==3,"cap 3");
        ok(ModelTileCatalog.forDevice(Arrays.asList("gemma3:270m"),null,null,null,null,null,6).size()==1,
            "low RAM device shows only safe recommendations");
        ok(ModelTileCatalog.forDevice(null,null,null,null,null,null,6).isEmpty(),"null recommendations");
        ok("다운로드 중".equals(ModelTileCatalog.status(tiles.get(1),tiles.get(1).id)),"real progress tag");
        List<ModelTileCatalog.Tile> installedVariant=
            ModelTileCatalog.forDevice(Arrays.asList("qwen3:1.7b"),
                Collections.emptyMap(),Collections.emptyMap(),
                Arrays.asList("qwen3:1.7b-q8_0"),
                Collections.singletonMap("qwen3:1.7b-q8_0",2200000000L),
                Collections.singletonMap("qwen3:1.7b-q8_0","Q8_0"),6);
        ok(installedVariant.size()==1 && installedVariant.get(0).installed,
            "matching quantized variant counts as installed");
        ok("Q8_0".equals(installedVariant.get(0).quantization),
            "installed quantization metadata used for base model tile");
        System.out.println("PASS: DeviceRecommendationGrid (12 assertions)");
    }
}
