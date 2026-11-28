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
    /** Each callback must identify the one queued utterance, not merely its playback prefix. */
    static Event currentEvent(String expected,String actual,boolean success){
        if(expected==null||!expected.equals(actual))return Event.IGNORE;
        return success?Event.COMPLETE:Event.ERROR;
    }
    /** Prefer sentence and word boundaries without deleting any code units. */
    static int naturalChunkEnd(String text,int start,int limit){
        int end=chunkEnd(text,start,limit);
        if(end==text.length())return end;
        int floor=start+Math.max(1,(end-start)*2/3),space=-1;
        for(int i=end;i>floor;i--){
            char c=text.charAt(i-1);
            if(".!?。！？；;\n".indexOf(c)>=0)return i;
            if(space<0&&(Character.isWhitespace(c)||Character.isSpaceChar(c)))space=i;
        }
        return space>start?space:end;
    }
    static String mergeDictation(String draft,String spoken,int max){
        if(draft==null||max<1||draft.length()>max)throw new IllegalArgumentException("Draft limit");
        String reviewed=VoiceInteractionPolicy.finalTranscript(spoken,max);
        String separator=draft.isEmpty()?"":"\n";
        if((long)draft.length()+separator.length()+reviewed.length()>max)
            throw new IllegalArgumentException("Draft limit");
        return draft+separator+reviewed;
    }
    // 20ms frames at 16kHz; conservative energy gate, not semantic speech recognition.
    static int bargeFrames(short[] frame,int count,int previous){
        if(frame==null||count<240||count>frame.length)return 0;
        long sum=0; for(int i=0;i<count;i++){long v=frame[i];sum+=v*v;}
        return sum>=count*1200L*1200L?Math.min(7,Math.max(0,previous))+1:0;
    }
    static long timeout(int length){
        return Math.min(1800000L,30000L+Math.max(0,length)*500L);
    }
    static boolean silent(String text){
        if(text==null)return true;
        for(int i=0;i<text.length();){
            int codepoint=text.codePointAt(i);
            if(!Character.isWhitespace(codepoint)&&!Character.isSpaceChar(codepoint))return false;
            i+=Character.charCount(codepoint);
        }
        return true;
    }
}
