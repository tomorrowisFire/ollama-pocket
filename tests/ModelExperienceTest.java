import com.ollamapocket.app.*;
import java.util.*;

public final class ModelExperienceTest{
    static void ok(boolean v,String what){if(!v)throw new AssertionError(what);}
    public static void main(String[] args){
        ModelVariantCatalog.Variant q4=new ModelVariantCatalog.Variant("Ollama","qwen3:1.7b-q4_K_M","",1150000000L);
        ModelVariantCatalog.Variant q8=new ModelVariantCatalog.Variant("Ollama","qwen3:1.7b-q8_0","",2200000000L);
        ModelVariantCatalog.Variant hf=new ModelVariantCatalog.Variant("Hugging Face","org/qwen3","qwen3-1.7b-Q4_K_M.gguf",1150000000L);
        ok(ModelGroupings.key(q4).equals(ModelGroupings.key(q8)),"same-size Ollama quant variants grouped");
        ok(!ModelGroupings.key(q4).equals(ModelGroupings.key(hf)),"distinct providers grouped separately");
        ok(ModelGroupings.localKey(q4.model).equals(ModelGroupings.key(q8)),"local tag merges into Ollama group");
        ModelSearchCache cache=new ModelSearchCache(2,10000);
        String key=ModelSearchCache.key("Ollama","  Qwen3 ");
        cache.put(key,Arrays.asList(q4),1000);
        ok(cache.get(ModelSearchCache.key("ollama","qwen3"),1001).size()==1,"cached results");
        ok(cache.get(key,11002)==null,"TTL expires");
        cache.put("first",Arrays.asList(q4),2000);cache.put("second",Arrays.asList(q8),2001);
        cache.put("third",Arrays.asList(hf),2002);
        ok(cache.get("first",2002)==null,"LRU bounded");
        ok(cache.get("third",2002).get(0)==hf,"cache preserves immutable variants");
        ok(ModelDownloadBudget.requiredBytes(2L*1024*1024*1024)>2L*1024*1024*1024,"extra storage reserve");
        ok(!ModelDownloadBudget.enough(100_000_000,2_000_000_000L),"reject low storage");
        ok(ModelDownloadBudget.enough(100_000_000,0),"unknown length not falsely rejected");
        ok(ModelDownloadBudget.layerPercent(500,1000)==50,"layer progress");
        ok(ModelDownloadBudget.layerPercent(0,0)<0,"unknown layer progress");
        ok(ModelDownloadBudget.layerLabel("pulling",50,100).contains("전체 다운로드 아님"),"no fake total percentage");
        ok(BenchmarkRecommendation.adjustment(30.0,null)<BenchmarkRecommendation.adjustment(2.0,null),"measured speed prioritization");
        ok(BenchmarkRecommendation.adjustment(null,null)==0,"no fabricated benchmarks");
        ok(BenchmarkRecommendation.bestMeasured(10.0,22.0)==22.0,"best measured CPU/GPU");
        long reserve=512L*1024*1024;
        ok(ModelDownloadBudget.requiredBytes(1)==reserve+2,"round up exact reserve");
        ok(ModelDownloadBudget.requiredBytes(5)==reserve+6,"20 percent exact");
        ok(ModelDownloadBudget.requiredBytes(Long.MAX_VALUE)==Long.MAX_VALUE,"saturating reserve");
        ok(ModelDownloadBudget.requiredBytes(0)==0,"unknown size");
        ok(!ModelDownloadBudget.enough(reserve+1,1)&&ModelDownloadBudget.enough(reserve+2,1),"byte boundary");
        ok(ModelDownloadBudget.requiresReconfirmation(0,10),"new estimate needs review");
        ok(ModelDownloadBudget.requiresReconfirmation(10,11),"higher estimate needs review");
        ok(!ModelDownloadBudget.requiresReconfirmation(10,10)&&!ModelDownloadBudget.requiresReconfirmation(10,0),"unchanged and unknown estimate");
        ok(ModelDownloadBudget.importRequiredBytes(100)==reserve+200,"two copy import reserve");
        ok(ModelDownloadBudget.registrationRequiredBytes(100)==reserve+100,"registration headroom");
        ok(ModelDownloadBudget.importRequiredBytes(Long.MAX_VALUE)==Long.MAX_VALUE,"import overflow saturates");
        ok(ModelDownloadBudget.registrationRequiredBytes(Long.MAX_VALUE)==Long.MAX_VALUE,"registration overflow saturates");
        ok(ModelDownloadBudget.importRequiredBytes(-1)==0,"unknown import size");
        ok(ModelDownloadBudget.layerPercent(101,100)==-1,"invalid progress is not falsely 100 percent");
        System.out.println("PASS: ModelExperience (30 checks)");
    }
}
