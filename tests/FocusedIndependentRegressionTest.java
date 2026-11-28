package com.ollamapocket.app;

import java.math.BigInteger;
import java.util.Random;

/** F10A86 isolated regression; no Android, network, native engine or user data. */
public final class FocusedIndependentRegressionTest {
    private static int checks;
    private static void check(boolean pass,String label){checks++;if(!pass)throw new AssertionError(label);}
    private static void testFamily(){
        check("DeepSeek".equals(ModelListFilter.family("hf:QwenTeam/DeepSeek-R1-Distill-Qwen-7B#model.gguf","")),"repository name before owner");
        check("Gemma".equals(ModelListFilter.family("llama-community/gemma3:4b","")),"community model before owner");
        check("Phi".equals(ModelListFilter.family("hf:QwenTeam/collection#folder/Phi-4-mini-Q4_K_M.gguf","")),"GGUF leaf fallback");
        check("기타".equals(ModelListFilter.family("hf:QwenTeam/collection#generic.gguf","Ollama")),"unknown model remains unknown");
        check("Qwen".equals(ModelListFilter.family("qwen2.5:7b","")),"ordinary Ollama tag");
        check("Llama".equals(ModelListFilter.family("meta-llama/Llama-3.2","")),"ordinary repository");
        check("Gemma".equals(ModelListFilter.family("unknown","Gemma 4")),"metadata fallback");
        check(ModelListFilter.matches("hf:QwenTeam/DeepSeek-R1#model.gguf","","Q4_K_M","DeepSeek","DeepSeek","Q4 전체"),"combined family filter");
        check(!ModelListFilter.matches("hf:QwenTeam/DeepSeek-R1#model.gguf","","Q4_K_M","","Qwen","전체 양자화"),"owner does not select wrong family");
    }
    private static void testDraft(){
        String[] spaces={"", " \n\t", "\u00a0", "\u3000", "\u2003", "\u202f", "\u0085", "\u00a0 \u3000\t"};
        for(String s:spaces){
            check(!ChatWorkspacePolicy.canSend(s,100),"blank Unicode draft: "+s.codePoints().boxed().toList());
            check(ChatWorkspacePolicy.canSend(s+"한글😀",100),"mixed meaningful text remains sendable");
            try{ChatWorkspacePolicy.prependStarter("old",s,100);throw new AssertionError("blank starter accepted");}
            catch(IllegalArgumentException expected){checks++;}
        }
        check(!ChatWorkspacePolicy.canSend(null,100),"null draft");
        check(!ChatWorkspacePolicy.canSend("한글😀",3),"UTF-16 budget");
        check(ChatWorkspacePolicy.canSend("한글😀",4),"exact UTF-16 budget");
        String draft="\u00a0old draft\n[attached file]";
        check(ChatWorkspacePolicy.prependStarter(draft," task ",100).equals("task\n\n"+draft),"existing draft preserved byte-for-byte");
    }
    private static void testProgress(){
        check(ModelDownloadBudget.layerPercent(-1,100)==-1,"negative completed");
        check(ModelDownloadBudget.layerPercent(0,0)==-1,"unknown total");
        check(ModelDownloadBudget.layerPercent(101,100)==-1,"over total");
        check(ModelDownloadBudget.layerPercent(29,100)==29,"29 percent exact");
        check(ModelDownloadBudget.layerPercent(57,100)==57,"57 percent exact");
        check(ModelDownloadBudget.layerPercent(Long.MAX_VALUE-1,Long.MAX_VALUE)==99,"never report completion early");
        check(ModelDownloadBudget.layerPercent(Long.MAX_VALUE,Long.MAX_VALUE)==100,"complete exact");
        long[] totals={1,3,100,1234,1_000_000_000L,Long.MAX_VALUE/100,Long.MAX_VALUE/100+1,Long.MAX_VALUE};
        for(long t:totals){
            check(ModelDownloadBudget.layerPercent(0,t)==0,"zero");
            check(ModelDownloadBudget.layerPercent(t,t)==100,"complete");
            check(ModelDownloadBudget.layerPercent(t-1,t)<100,"incomplete");
        }
        Random r=new Random(86);
        for(int i=0;i<10000;i++){
            long total=1+(r.nextLong()&Long.MAX_VALUE)%Long.MAX_VALUE;
            long completed=(r.nextLong()&Long.MAX_VALUE)%total;
            int expected=BigInteger.valueOf(completed).multiply(BigInteger.valueOf(100)).divide(BigInteger.valueOf(total)).intValueExact();
            check(ModelDownloadBudget.layerPercent(completed,total)==expected,"integer reference agreement");
        }
    }
    public static void main(String[] args){
        switch(args.length==0?"all":args[0]){
            case "family":testFamily();break;
            case "draft":testDraft();break;
            case "progress":testProgress();break;
            case "all":testFamily();testDraft();testProgress();break;
            default:throw new IllegalArgumentException("Unknown scope");
        }
        System.out.println("PASS: "+checks+" focused assertions");
    }
}
