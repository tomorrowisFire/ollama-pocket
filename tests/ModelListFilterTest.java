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
        ok("Gemma".equals(ModelListFilter.family("gemma4:e2b","Ollama · Qwen example")),"model id wins over metadata");
        ok("DeepSeek".equals(ModelListFilter.family("hf:org/DeepSeek-R1-Distill-Qwen-7B#file.gguf","Ollama")),"primary identifier before distillation base");
        ok("기타".equals(ModelListFilter.family("unknown:latest","Ollama")),"provider is not a model family");
        ok("Llama".equals(ModelListFilter.family("meta-llama/Llama-3.2","")),"Llama identifier");
        ok("Phi".equals(ModelListFilter.family("org/Phi-4-mini","")),"Phi identifier");
        ok("Gemma".equals(ModelListFilter.family("unknown","Ollama · Gemma 4")),"metadata fallback");
        ok("IQ2".equals(ModelListFilter.band("iq2_xxs")),"IQ band");
        ok("MXFP4".equals(ModelListFilter.band("mxfp4")),"MXFP4 band");
        ok("기타".equals(ModelListFilter.band("Q40")),"invalid prefix");
        ok(ModelListFilter.matchesQuantization("IQ4 전체","IQ4_XS"),"IQ filter");
        ok(ModelListFilter.matchesQuantization("q4_k_m","Q4_K_M"),"normalized exact choice");
        ok(ModelListFilter.matches("qwen3:4b","local model","Q4_K_M","qwen 4b q4_k_m","전체 모델","전체 양자화"),"multiple terms including quant");
        ok(ModelListFilter.matches("gemma4:e2b","로컬 모델","Q4_K_M","ＧＥＭＭＡ　Ｅ２Ｂ","전체 모델","전체 양자화"),"width normalization");
        ok(!ModelListFilter.matches("gemma4:e2b","local model","Q4_K_M","gemma q8","전체 모델","전체 양자화"),"all terms required");
        ok(ModelListFilter.matches("hf:org/model#file.gguf","model metadata","IQ2_XS","org\tmetadata IQ2","전체 모델","전체 양자화"),"whitespace terms");
        ok(!ModelListFilter.matches("qwen:4b","local model","Q4_K_M","qwen 4b","Llama","전체 양자화"),"family constraint preserved");
        ok(!ModelListFilter.matches("x","x","x","x".repeat(241),"전체 모델","전체 양자화"),"bounded query");
        System.out.println("PASS: ModelListFilter (35 assertions)");
    }
}
