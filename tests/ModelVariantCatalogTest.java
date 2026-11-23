import com.ollamapocket.app.ModelVariantCatalog;
import java.util.*;
public class ModelVariantCatalogTest{
    static void ok(boolean v,String m){if(!v)throw new AssertionError(m);}
    public static void main(String[] args){
        String page="<a href='/library/qwen3%3A0.6b-q4_K_M'><span>0.6b</span><span>523 MB</span></a>"+
            "<a href='/library/qwen3:0.6b-q8_0'>832MB</a>"+
            "<a href='/library/qwen3:0.6b-q4_K_M'>name only</a>"+
            "<a href='/library/qwen3:235b-cloud'>cloud</a>"+
            "<a href='/library/qwen3:1b-mlx'>3 GB</a>"+
            "<a href='/library/not-qwen:1b'>1 GB</a>";
        List<ModelVariantCatalog.Variant> v=ModelVariantCatalog.parseTags("qwen3",page);
        ok(v.size()==2,"dedupe, exclude cloud/MLX");
        ok(v.get(0).model.equals("qwen3:0.6b-q4_K_M"),"encoded colon");
        ok(v.get(0).bytes==523000000L,"exact advertised bytes");
        ok(v.get(0).quant.equals("Q4_K_M"),"exact quantization");
        ok(v.get(1).bytes==832000000L,"compact unit");
        List<String> f=ModelVariantCatalog.parseFamilies("<a href='/library/qwen3'>Qwen3</a>"+
            "<a href='/alice/llm'>alice/llm</a><a href='/blog/foo'>article</a>"+
            "<a href='https://example.com/library/qwen3'>external</a>");
        ok(f.equals(Arrays.asList("qwen3","alice/llm")),"verified library/community links");
        ok(!ModelVariantCatalog.validFamily("../secret"),"deny traversal");
        ok(ModelVariantCatalog.compareSize(0,8,true)>0,"unknown size last descending");
        ok(ModelVariantCatalog.compareSize(0,8,false)>0,"unknown size last ascending");
        ok(ModelVariantCatalog.columns(340,148,10)==2,"phone");
        ok(ModelVariantCatalog.columns(760,148,10)==4,"tablet");
        ok(ModelVariantCatalog.columns(1100,148,10)==7,"wide screen");
        ok(ModelVariantCatalog.tier(2*ModelVariantCatalog.GIB,true,false,true,false,8,5,3)==4,
            "HF temporary plus registered storage");
        double a=ModelVariantCatalog.score(2*ModelVariantCatalog.GIB,"Q4_K_M",false,false,true,false,8,5,32,8);
        double b=ModelVariantCatalog.score(8*ModelVariantCatalog.GIB,"Q8_0",false,false,true,false,8,5,32,8);
        ok(a<b,"device-fit ranking before quant preference");
        ok(ModelVariantCatalog.quantization("model-Q4_K_M.gguf").equals("Q4_K_M"),"GGUF quant");
        System.out.println("PASS: ModelVariantCatalog (15 assertions)");
    }
}
