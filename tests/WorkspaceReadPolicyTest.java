package com.ollamapocket.app;

public final class WorkspaceReadPolicyTest {
    private static int n;
    private static void check(boolean ok){n++;if(!ok)throw new AssertionError("workspace reading "+n);}
    private static void reject(String text,String lang,int rate){
        try{WorkspaceReadPolicy.review(text,lang,rate);}catch(IllegalArgumentException expected){check(true);return;}
        throw new AssertionError("Invalid reading accepted");
    }
    public static void main(String[] args){
        String text="  보고서\n[K1] Evidence 😀\n";
        WorkspaceReadPolicy.Review r=WorkspaceReadPolicy.review(text,"ko",1);
        check(r.text.equals(text)&&r.language.equals("ko")&&r.rate==1);
        check(r.matches(text)&&!r.matches(text.trim())&&!r.matches(null));
        for(String lang:VoiceInteractionPolicy.LANGUAGES)for(int i=0;i<VoiceInteractionPolicy.RATES.length;i++){
            r=WorkspaceReadPolicy.review("한국어 English 简体 繁體 日本語",lang,i);
            check(r.language.equals(lang)&&r.rate==i);
        }
        check(WorkspaceReadPolicy.review("x".repeat(160000),"app",0).text.length()==160000);
        check(WorkspaceReadPolicy.review("a\tb\r\nc","en",1).text.equals("a\tb\r\nc"));
        check(WorkspaceReadPolicy.review("👩‍💻","ja",2).text.equals("👩‍💻"));
        reject("x".repeat(160001),"app",1);reject("","app",1);reject("  ","app",1);
        reject("\u3000","app",1);reject("\u200D","app",1);reject(null,"app",1);
        reject("a\u0000b","app",1);reject("a\u202Eb","app",1);reject("\uD800","app",1);reject("\uDC00","app",1);
        reject("text",null,1);reject("text","zh",1);reject("text","de",1);reject("text","en",-1);reject("text","en",4);
        VoiceInteractionPolicy.Playback p=new VoiceInteractionPolicy.Playback("a😀b。日本語","workspace-",3);
        VoiceInteractionPolicy.Playback.Chunk first=p.next();
        check(first.text.equals("a😀")&&!p.finished());
        check(!p.acknowledge("stale")&&p.completed()==0);
        check(p.pause()&&!p.current(first.id)&&!p.acknowledge(first.id)&&p.completed()==0);
        check(p.resume());
        VoiceInteractionPolicy.Playback.Chunk again=p.next();
        check(again.text.equals(first.text)&&!again.id.equals(first.id));
        check(p.acknowledge(again.id)&&!p.finished()&&p.completed()==3);
        StringBuilder reconstructed=new StringBuilder(again.text);
        while(!p.finished()){
            VoiceInteractionPolicy.Playback.Chunk chunk=p.next();reconstructed.append(chunk.text);check(p.acknowledge(chunk.id));
        }
        check(reconstructed.toString().equals(p.text)&&p.percent()==100);
        check(!p.acknowledge(again.id));
        System.out.println("PASS: "+n+" reviewed workspace read-aloud assertions");
    }
}
