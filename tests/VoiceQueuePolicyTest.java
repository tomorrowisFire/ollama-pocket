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
        System.out.println("PASS: 13 voice queue policy checks");
    }
}
