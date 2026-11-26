package com.ollamapocket.app;

final class VoiceQueuePolicy {
    enum Event { IGNORE, WAIT, COMPLETE, ERROR }
    static Event event(String prefix,String last,String id,boolean success){
        if(prefix==null||id==null||!id.startsWith(prefix))return Event.IGNORE;
        if(!success)return Event.ERROR;
        return id.equals(last)?Event.COMPLETE:Event.WAIT;
    }
    static int chunkEnd(String text,int start,int limit){
        if(limit<2||start<0||start>=text.length())throw new IllegalArgumentException();
        int end=start+Math.min(limit,text.length()-start);
        if(end<text.length()&&Character.isHighSurrogate(text.charAt(end-1))&&Character.isLowSurrogate(text.charAt(end)))end--;
        return end;
    }
    // 20ms frames at 16kHz; conservative energy gate, not semantic speech recognition.
    static int bargeFrames(short[] frame,int count,int previous){
        if(frame==null||count<240||count>frame.length)return 0;
        long sum=0; for(int i=0;i<count;i++){long v=frame[i];sum+=v*v;}
        return sum>=count*1200L*1200L?Math.min(8,Math.max(0,previous)+1):0;
    }
    static long timeout(int length){
        return Math.min(1800000L,30000L+Math.max(0,length)*500L);
    }
}
