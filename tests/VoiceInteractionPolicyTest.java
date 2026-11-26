package com.ollamapocket.app;

import java.util.Locale;

public final class VoiceInteractionPolicyTest {
    private static int n;
    private static void ok(boolean pass){if(!pass)throw new AssertionError("check "+n);n++;}
    private static void rejects(Runnable r){boolean denied=false;try{r.run();}catch(IllegalArgumentException|IllegalStateException e){denied=true;}ok(denied);}
    public static void main(String[] args){
        ok(VoiceInteractionPolicy.language(null).equals("app"));
        ok(VoiceInteractionPolicy.language("fr").equals("app"));
        ok(VoiceInteractionPolicy.language("zh-Hant").equals("zh-Hant"));
        ok(VoiceInteractionPolicy.locale("app",Locale.KOREA).equals(Locale.KOREA));
        ok(VoiceInteractionPolicy.locale("app",null).equals(Locale.US));
        ok(VoiceInteractionPolicy.locale("ja",Locale.US).equals(Locale.JAPAN));
        ok(VoiceInteractionPolicy.locale("zh-Hans",Locale.US).equals(Locale.SIMPLIFIED_CHINESE));
        ok(VoiceInteractionPolicy.locale("zh-Hant",Locale.US).equals(Locale.TRADITIONAL_CHINESE));
        ok(VoiceInteractionPolicy.rateIndex(-1)==1);
        ok(VoiceInteractionPolicy.rateIndex(4)==1);
        ok(VoiceInteractionPolicy.rateIndex(3)==3);
        ok(VoiceInteractionPolicy.partial(null).isEmpty());
        ok(VoiceInteractionPolicy.partial("Hi\u202e\u0000 there").equals("Hi there"));
        ok(VoiceInteractionPolicy.partial("x".repeat(239)+"😀").length()==239);
        ok(VoiceInteractionPolicy.partial("x".repeat(300)).length()==240);
        ok(VoiceInteractionPolicy.partial("😀".repeat(120)).length()==240);
        ok(VoiceInteractionPolicy.partial("\uD800x").equals("x"));
        String sample="First sentence. Second sentence. 한국어😀日本語。";
        VoiceInteractionPolicy.Playback p=new VoiceInteractionPolicy.Playback(sample,"test-",16);
        ok(p.completed()==0&&p.percent()==0&&!p.finished());
        VoiceInteractionPolicy.Playback.Chunk first=p.next();
        ok(p.current(first.id)&&!p.current("old"));
        rejects(p::next);
        ok(p.pause()&&p.paused()&&p.completed()==0);
        ok(!p.acknowledge(first.id));
        rejects(p::next);
        ok(p.resume()&&!p.paused());
        VoiceInteractionPolicy.Playback.Chunk replay=p.next();
        ok(replay.text.equals(first.text)&&!replay.id.equals(first.id));
        ok(!p.acknowledge(first.id));
        ok(p.acknowledge(replay.id)&&p.completed()==replay.text.length());
        ok(!p.acknowledge(replay.id));
        StringBuilder reconstructed=new StringBuilder(replay.text);
        while(!p.finished()){
            VoiceInteractionPolicy.Playback.Chunk next=p.next();
            ok(next.text.length()<=16);
            ok(!Character.isLowSurrogate(next.text.charAt(0)));
            ok(!Character.isHighSurrogate(next.text.charAt(next.text.length()-1)));
            reconstructed.append(next.text);
            ok(p.acknowledge(next.id));
        }
        ok(reconstructed.toString().equals(sample));
        ok(p.percent()==100&&p.completed()==sample.length());
        ok(!p.pause()&&!p.resume());
        rejects(p::next);
        rejects(()->new VoiceInteractionPolicy.Playback("","x",16));
        rejects(()->new VoiceInteractionPolicy.Playback("text","",16));
        rejects(()->new VoiceInteractionPolicy.Playback("text","x",1));
        rejects(()->new VoiceInteractionPolicy.Playback("text","x",2001));
        System.out.println("PASS: "+n+" voice interaction and playback cursor checks");
    }
}
