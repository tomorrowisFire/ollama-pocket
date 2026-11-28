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
        String sparse=" ".repeat(32)+"hello"+" ".repeat(64);
        VoiceInteractionPolicy.Playback sparsePlayback=new VoiceInteractionPolicy.Playback(sparse,"sparse-",16);
        VoiceInteractionPolicy.Playback.Chunk audible=sparsePlayback.nextAudible();
        ok(audible!=null&&audible.text.contains("hello")&&sparsePlayback.completed()==32);
        ok(sparsePlayback.acknowledge(audible.id));
        ok(sparsePlayback.nextAudible()==null&&sparsePlayback.finished()&&sparsePlayback.percent()==100);
        VoiceInteractionPolicy.Capture capture=new VoiceInteractionPolicy.Capture();
        ok(capture.state()==VoiceInteractionPolicy.Capture.State.PREPARING&&!capture.canFinish()&&!capture.finish());
        capture.ready();ok(capture.canFinish()&&capture.state()==VoiceInteractionPolicy.Capture.State.LISTENING);
        ok(capture.finish()&&!capture.finish()&&!capture.canFinish());
        capture.ready();capture.end();ok(capture.state()==VoiceInteractionPolicy.Capture.State.FINALIZING);
        ok(capture.consumeTerminal()&&!capture.consumeTerminal());
        capture.ready();capture.end();ok(!capture.finish()&&capture.state()==VoiceInteractionPolicy.Capture.State.TERMINAL);
        VoiceInteractionPolicy.Capture autoEnd=new VoiceInteractionPolicy.Capture();
        autoEnd.end();autoEnd.ready();ok(autoEnd.state()==VoiceInteractionPolicy.Capture.State.FINALIZING&&!autoEnd.finish());
        autoEnd.cancel();ok(!autoEnd.consumeTerminal()&&!autoEnd.canFinish());
        VoiceInteractionPolicy.Capture early=new VoiceInteractionPolicy.Capture();
        ok(early.consumeTerminal());early.ready();ok(!early.canFinish());
        ok(VoiceInteractionPolicy.finalTranscript("  한글 日本語 中文 😀  ",40).equals("한글 日本語 中文 😀"));
        ok(VoiceInteractionPolicy.finalTranscript("A\nB\tC",5).equals("A\nB\tC"));
        ok(VoiceInteractionPolicy.finalTranscript("👩‍💻",5).equals("👩‍💻"));
        ok(VoiceInteractionPolicy.finalTranscript("a".repeat(16000),16000).length()==16000);
        rejects(()->VoiceInteractionPolicy.finalTranscript("a".repeat(16001),16000));
        rejects(()->VoiceInteractionPolicy.finalTranscript(null,16000));
        rejects(()->VoiceInteractionPolicy.finalTranscript(" \u3000",16000));
        rejects(()->VoiceInteractionPolicy.finalTranscript("x\u0000",16000));
        rejects(()->VoiceInteractionPolicy.finalTranscript("\u202eabc",16000));
        rejects(()->VoiceInteractionPolicy.finalTranscript("x\uD800",16000));
        rejects(()->VoiceInteractionPolicy.finalTranscript("x\uDC00",16000));
        ok(VoiceInteractionPolicy.localeRank(Locale.KOREA,Locale.KOREA)==4);
        ok(VoiceInteractionPolicy.localeRank(Locale.US,Locale.UK)==1);
        ok(VoiceInteractionPolicy.localeRank(Locale.JAPAN,Locale.KOREA)==-1);
        ok(VoiceInteractionPolicy.localeRank(Locale.JAPAN,Locale.JAPAN)==4);
        ok(VoiceInteractionPolicy.localeRank(Locale.SIMPLIFIED_CHINESE,Locale.forLanguageTag("zh-Hans-SG"))==1);
        ok(VoiceInteractionPolicy.localeRank(Locale.SIMPLIFIED_CHINESE,Locale.TRADITIONAL_CHINESE)==-1);
        ok(VoiceInteractionPolicy.localeRank(Locale.TRADITIONAL_CHINESE,Locale.forLanguageTag("zh-Hant-HK"))==1);
        ok(VoiceInteractionPolicy.localeRank(Locale.TRADITIONAL_CHINESE,Locale.forLanguageTag("zh-Hans-TW"))==-1);
        ok(VoiceInteractionPolicy.localeRank(Locale.TRADITIONAL_CHINESE,Locale.CHINESE)==-1);
        ok(VoiceInteractionPolicy.localeRank(null,Locale.US)==-1);
        ok(VoiceInteractionPolicy.betterVoice(4,100,100,"a",3,400,10,"b"));
        ok(!VoiceInteractionPolicy.betterVoice(3,400,10,"a",4,100,100,"b"));
        ok(VoiceInteractionPolicy.betterVoice(4,400,100,"b",4,300,10,"a"));
        ok(VoiceInteractionPolicy.betterVoice(4,400,10,"b",4,400,100,"a"));
        ok(VoiceInteractionPolicy.betterVoice(4,400,10,"a",4,400,10,"b"));
        ok(!VoiceInteractionPolicy.betterVoice(-1,400,10,"a",-1,0,0,null));
        System.out.println("PASS: "+n+" voice interaction and playback cursor checks");
    }
}
