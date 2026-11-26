package com.ollamapocket.app;

public final class VoiceQueuePolicyTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        String p="oh-2-1-",last=p+"2000";
        check(VoiceQueuePolicy.event(p,last,"oh-1-1-2000",true)==VoiceQueuePolicy.Event.IGNORE);
        check(VoiceQueuePolicy.event(null,last,last,true)==VoiceQueuePolicy.Event.IGNORE);
        check(VoiceQueuePolicy.event(p,last,null,false)==VoiceQueuePolicy.Event.IGNORE);
        check(VoiceQueuePolicy.event(p,last,p+"0",true)==VoiceQueuePolicy.Event.WAIT);
        check(VoiceQueuePolicy.event(p,last,p+"0",false)==VoiceQueuePolicy.Event.ERROR);
        check(VoiceQueuePolicy.event(p,last,last,true)==VoiceQueuePolicy.Event.COMPLETE);
        check(VoiceQueuePolicy.event(p,last,last,false)==VoiceQueuePolicy.Event.ERROR);
        String s="a\uD83D\uDE00bc";
        check(VoiceQueuePolicy.chunkEnd(s,0,2)==1);
        check(VoiceQueuePolicy.chunkEnd(s,1,2)==3);
        check(VoiceQueuePolicy.chunkEnd(s,3,2000)==5);
        check(VoiceQueuePolicy.timeout(0)==30000L);
        check(VoiceQueuePolicy.timeout(2000)==1030000L);
        check(VoiceQueuePolicy.timeout(Integer.MAX_VALUE)==1800000L);
        check(VoiceQueuePolicy.event("oh-2-2-", "oh-2-2-0", "oh-2-1-0", false)==VoiceQueuePolicy.Event.IGNORE);
        check(VoiceQueuePolicy.event(p,last,"oh-20-1-2000",true)==VoiceQueuePolicy.Event.IGNORE);
        check(VoiceQueuePolicy.event(p,null,p+"0",true)==VoiceQueuePolicy.Event.WAIT);
        check(VoiceQueuePolicy.timeout(-1)==30000L);
        String mixed="한글中文日本語"+s.repeat(1000);
        StringBuilder rebuilt=new StringBuilder();
        for(int start=0;start<mixed.length();){
            int end=VoiceQueuePolicy.chunkEnd(mixed,start,2000);
            check(end>start&&end-start<=2000);
            check(end==mixed.length()||!Character.isLowSurrogate(mixed.charAt(end)));
            rebuilt.append(mixed,start,end);start=end;
        }
        check(mixed.contentEquals(rebuilt));
        boolean rejected=false;
        try{VoiceQueuePolicy.chunkEnd(s,0,1);}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected);
        short[] quiet=new short[320], loud=new short[320];
        java.util.Arrays.fill(loud,(short)1500);
        int hits=0;
        for(int i=0;i<7;i++){hits=VoiceQueuePolicy.bargeFrames(loud,320,hits);check(hits==i+1);}
        check(VoiceQueuePolicy.bargeFrames(quiet,320,hits)==0);
        check(VoiceQueuePolicy.bargeFrames(loud,320,hits)==8);
        check(VoiceQueuePolicy.bargeFrames(loud,320,8)==8);
        check(VoiceQueuePolicy.bargeFrames(loud,100,7)==0);
        check(VoiceQueuePolicy.bargeFrames(null,320,7)==0);
        check(VoiceQueuePolicy.bargeFrames(loud,321,7)==0);
        check(VoiceQueuePolicy.currentEvent("current","previous",false)==VoiceQueuePolicy.Event.IGNORE);
        check(VoiceQueuePolicy.currentEvent("current","current",true)==VoiceQueuePolicy.Event.COMPLETE);
        check(VoiceQueuePolicy.currentEvent("current","current",false)==VoiceQueuePolicy.Event.ERROR);
        check(VoiceQueuePolicy.currentEvent(null,"old",true)==VoiceQueuePolicy.Event.IGNORE);
        String sentences="Hello world. Next sentence.";
        check(VoiceQueuePolicy.naturalChunkEnd(sentences,0,15)==12);
        String multilingual="한국어 문장입니다. 中文句子。 日本語です！ 😀 and more ".repeat(180);
        StringBuilder exact=new StringBuilder();
        for(int pos=0;pos<multilingual.length();){
            int end=VoiceQueuePolicy.naturalChunkEnd(multilingual,pos,128);
            check(end>pos&&end-pos<=128);
            check(end==multilingual.length()||!Character.isLowSurrogate(multilingual.charAt(end)));
            exact.append(multilingual,pos,end);pos=end;
        }
        check(exact.toString().equals(multilingual));
        check(VoiceQueuePolicy.mergeDictation("draft"," spoken ",30).equals("draft\nspoken"));
        check(VoiceQueuePolicy.mergeDictation("","spoken",30).equals("spoken"));
        try{VoiceQueuePolicy.mergeDictation("old","new",6);throw new AssertionError();}catch(IllegalArgumentException expected){}
        System.out.println("PASS: voice queue, current callback, natural chunks, draft preservation and multilingual round-trip checks");
    }
}
