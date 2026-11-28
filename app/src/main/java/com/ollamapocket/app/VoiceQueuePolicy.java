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
    /** Prefer a boundary outside common joined Unicode sequences. A single sequence
     * larger than the backend limit must still be split at a valid code-point boundary. */
    private static boolean speechBoundary(String text,int start,int end){
        if(end<=start)return false;
        if(end>=text.length())return true;
        if(Character.isHighSurrogate(text.charAt(end-1))&&Character.isLowSurrogate(text.charAt(end)))return false;
        int left=text.codePointBefore(end),right=text.codePointAt(end),kind=Character.getType(right);
        if(left=='\r'&&right=='\n'||left==0x200d||right==0x200d||right==0x200c||
                kind==Character.NON_SPACING_MARK||kind==Character.COMBINING_SPACING_MARK||kind==Character.ENCLOSING_MARK||
                right>=0x1f3fb&&right<=0x1f3ff)return false;
        // Preserve pairs of regional-indicator characters when the pair fits.
        if(left>=0x1f1e6&&left<=0x1f1ff&&right>=0x1f1e6&&right<=0x1f1ff){
            int count=0,pos=end;
            while(pos>start){
                int cp=text.codePointBefore(pos);
                if(cp<0x1f1e6||cp>0x1f1ff)break;
                count++;pos-=Character.charCount(cp);
            }
            return (count&1)==0;
        }
        return true;
    }
    /** Prefer sentence and word boundaries without deleting any code units. */
    static int naturalChunkEnd(String text,int start,int limit){
        int hardEnd=chunkEnd(text,start,limit);
        if(hardEnd==text.length())return hardEnd;
        int end=hardEnd;
        while(end>start&&!speechBoundary(text,start,end))end--;
        if(end==start)end=hardEnd;
        int floor=start+Math.max(1,(end-start)*2/3),space=-1;
        for(int i=end;i>floor;i--){
            if(!speechBoundary(text,start,i))continue;
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
