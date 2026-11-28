import com.ollamapocket.app.ModelListFilter;
public final class ModelListFilterTest {
    private static void ok(boolean yes,String label){if(!yes)throw new AssertionError(label);}
    public static void main(String[] args){
        ok("Qwen".equals(ModelListFilter.family("qwen2.5:7b","")), "Qwen");
        ok("Llama".equals(ModelListFilter.family("llama3.2:3b","")), "Llama");
        ok("DeepSeek".equals(ModelListFilter.family("deepseek-r1:7b","")), "DeepSeek");
        ok("Gemma".equals(ModelListFilter.family("gemma3:4b","")), "Gemma");
        ok("기타".equals(ModelListFilter.family("something:7b","")), "other");
        ok("Q4".equals(ModelListFilter.band("q4_k_m")), "quant group");
        ok("F16/BF16".equals(ModelListFilter.band("BF16")), "float quant");
        ok(ModelListFilter.matchesQuantization("Q4 전체","Q4_K_M"), "Q4 group");
        ok(ModelListFilter.matchesQuantization("Q4_K_M","q4_k_m".toUpperCase()), "exact version");
        ok(!ModelListFilter.matchesQuantization("Q4_K_M","Q4_0"), "exclude other Q4 variant");
        ok(ModelListFilter.matches("qwen2.5:7b","4.7 GB · Q4_K_M","Q4_K_M",
             "qwen","Qwen","Q4 전체"), "all combined filters");
        ok(!ModelListFilter.matches("gemma3:4b","3.4 GB · Q4_K_M","Q4_K_M",
             "qwen","Qwen","Q4 전체"), "text and family exclude");
        ok(ModelListFilter.matches("unclear","unknown","", "", "전체 모델","전체 양자화"), "unknown accepted");
        java.util.List<String> options=new java.util.ArrayList<>(java.util.Arrays.asList("전체 모델","Qwen"));
        ok(ModelListFilter.retainChoice(options,"Qwen")==1,"available choice");
        ok(ModelListFilter.retainChoice(options,"Gemma")==2&&options.get(2).equals("Gemma"),"preserve selection across partial provider results");
        ok(ModelListFilter.retainChoice(options,"Gemma")==2&&options.size()==3,"no duplicate preserved option");
        ok(ModelListFilter.retainChoice(options,null)==0,"default for missing choice");
        ok(!ModelListFilter.matches("qwen:4b","","Q4_K_M","","Gemma","전체 양자화"),"absent selection shows zero results, not another family");
        System.out.println("PASS: ModelListFilter (18 assertions)");
    }
}
