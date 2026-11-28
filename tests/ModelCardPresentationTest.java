package com.ollamapocket.app;

public final class ModelCardPresentationTest {
    private static int n;
    private static void ok(boolean b){n++;if(!b)throw new AssertionError("check "+n);}
    private static void rejects(Runnable r){try{r.run();throw new AssertionError("invalid labels accepted");}catch(IllegalArgumentException expected){n++;}}
    public static void main(String[] args){
        for(int mask=0;mask<8;mask++){
            boolean downloading=(mask&1)!=0,catalog=(mask&2)!=0,installed=(mask&4)!=0;
            ModelCardPresentation.Action expected=downloading?ModelCardPresentation.Action.WAIT:
                catalog||!installed?ModelCardPresentation.Action.CHOOSE_VARIANT:ModelCardPresentation.Action.OPEN_CHAT;
            ok(ModelCardPresentation.action(downloading,catalog,installed)==expected);
        }
        String[] device={"This device","내 기기","この端末","本机","本機"};
        String[] unknown={"Size unknown","용량 미확인","容量不明","大小未知","容量未知"};
        for(int i=0;i<device.length;i++){
            ok(ModelCardPresentation.provider(true,"Hugging Face",device[i],"Ollama").equals(device[i]));
            ok(ModelCardPresentation.provider(true,null,device[i],"Ollama").equals(device[i]));
            ok(ModelCardPresentation.provider(false,"Hugging Face",device[i],"Ollama").equals("Hugging Face"));
            ok(ModelCardPresentation.provider(false,null,device[i],"Ollama").equals("Ollama"));
            ok(ModelCardPresentation.provider(false,"",device[i],"Ollama").equals("Ollama"));
            ok(ModelCardPresentation.summary("Q4_K_M",unknown[i],device[i],"").equals("Q4_K_M · "+unknown[i]+" · "+device[i]));
            ok(ModelCardPresentation.summary("Q8_0","2.20 GB",device[i]," · 12 tok/s").equals("Q8_0 · 2.20 GB · "+device[i]+" · 12 tok/s"));
        }
        rejects(()->ModelCardPresentation.provider(true,"Ollama",null,"Ollama"));
        rejects(()->ModelCardPresentation.provider(false,null,"Local",""));
        rejects(()->ModelCardPresentation.summary(null,"2 GB","Ollama",""));
        rejects(()->ModelCardPresentation.summary("Q4_K_M","","Ollama",""));
        rejects(()->ModelCardPresentation.summary("Q4_K_M","2 GB",null,""));
        rejects(()->ModelCardPresentation.summary("Q4_K_M","2 GB","Ollama",null));
        System.out.println("PASS: "+n+" model card presentation assertions");
    }
}
