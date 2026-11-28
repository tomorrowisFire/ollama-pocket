package com.ollamapocket.app;

import java.util.Locale;

/** Independent speech preferences and a bounded, callback-safe speech cursor. */
final class VoiceInteractionPolicy {
    static final String[] LANGUAGES={"app","ko","en","zh-Hans","zh-Hant","ja"};
    static final float[] RATES={0.8f,1.0f,1.2f,1.4f};
    static String language(String value){
        for(String tag:LANGUAGES)if(tag.equals(value))return tag;return "app";
    }
    static int rateIndex(int value){return value>=0&&value<RATES.length?value:1;}
    static Locale locale(String value,Locale fallback){
        switch(language(value)){
            case "ko":return Locale.KOREA;
            case "en":return Locale.US;
            case "zh-Hans":return Locale.SIMPLIFIED_CHINESE;
            case "zh-Hant":return Locale.TRADITIONAL_CHINESE;
            case "ja":return Locale.JAPAN;
            default:return fallback==null?Locale.US:fallback;
        }
    }
    static String partial(String text){
        if(text==null)return "";
        StringBuilder result=new StringBuilder();
        int scanLimit=Math.min(text.length(),4096);
        for(int i=0;i<scanLimit&&result.length()<240;){
            int cp=text.codePointAt(i);
            int width=Character.charCount(cp);
            if(i+width>scanLimit)break;
            i+=width;
            if(Character.isISOControl(cp)||
                Character.getType(cp)==Character.FORMAT&&cp!=0x200C&&cp!=0x200D||
                cp>=0xD800&&cp<=0xDFFF)continue;
            if(result.length()+Character.charCount(cp)>240)break;
            result.appendCodePoint(cp);
        }
        return result.toString().trim();
    }
    /** One recognition request, on the main thread. Terminal callbacks can be consumed only once. */
    static final long PLATFORM_CAPTURE_MS=45_000L, PLATFORM_FINALIZE_MS=12_000L;
    static long platformFinalizeBudget(long remaining){
        return Math.max(0L,Math.min(PLATFORM_FINALIZE_MS,remaining));
    }
    static final class Capture {
        enum State { PREPARING, LISTENING, FINALIZING, TERMINAL }
        private State state=State.PREPARING;
        private boolean heardSpeech;
        State state(){return state;}
        void ready(){if(state==State.PREPARING)state=State.LISTENING;}
        void speechStarted(){
            ready();
            if(state==State.LISTENING)heardSpeech=true;
        }
        boolean waitingForSpeech(){return state==State.LISTENING&&!heardSpeech;}
        void end(){if(state==State.PREPARING||state==State.LISTENING)state=State.FINALIZING;}
        boolean canFinish(){return state==State.LISTENING;}
        boolean finish(){if(!canFinish())return false;state=State.FINALIZING;return true;}
        boolean consumeTerminal(){if(state==State.TERMINAL)return false;state=State.TERMINAL;return true;}
        void cancel(){state=State.TERMINAL;}
    }
    /** Reject invalid final text; do not truncate or silently strip characters from a submitted draft. */
    static String finalTranscript(String raw,int max){
        if(raw==null||max<1||raw.length()>max)throw new IllegalArgumentException("Recognition text limit");
        boolean content=false;
        for(int i=0;i<raw.length();){
            int cp=raw.codePointAt(i);i+=Character.charCount(cp);
            if((cp>=0xD800&&cp<=0xDFFF)||
                (Character.isISOControl(cp)&&cp!='\n'&&cp!='\r'&&cp!='\t')||
                (Character.getType(cp)==Character.FORMAT&&cp!=0x200C&&cp!=0x200D))
                throw new IllegalArgumentException("Invalid recognition text");
            if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp)&&cp!=0x200C&&cp!=0x200D)content=true;
        }
        if(!content)throw new IllegalArgumentException("Empty recognition text");
        return raw.trim();
    }
    private static String chineseScript(Locale locale){
        if("Hans".equals(locale.getScript())||"Hant".equals(locale.getScript()))return locale.getScript();
        if(!locale.getScript().isEmpty())return "";
        String region=locale.getCountry();
        if("CN".equals(region)||"SG".equals(region))return "Hans";
        if("TW".equals(region)||"HK".equals(region)||"MO".equals(region))return "Hant";
        return "";
    }
    /** No cross-script or unknown-script Chinese substitution. Negative rank means unavailable. */
    static int localeRank(Locale target,Locale candidate){
        if(target==null||candidate==null||target.getLanguage().isEmpty()||
            !target.getLanguage().equals(candidate.getLanguage()))return -1;
        if("zh".equals(target.getLanguage())){
            String script=chineseScript(target);
            if(script.isEmpty()||!script.equals(chineseScript(candidate)))return -1;
        }else if(!target.getScript().isEmpty()&&!candidate.getScript().isEmpty()&&
            !target.getScript().equals(candidate.getScript()))return -1;
        if(target.equals(candidate))return 4;
        if(!target.getCountry().isEmpty()&&target.getCountry().equals(candidate.getCountry()))return 3;
        if(!target.getScript().isEmpty()&&target.getScript().equals(candidate.getScript()))return 2;
        return 1;
    }
    static boolean betterVoice(int rank,int quality,int latency,String name,int oldRank,int oldQuality,int oldLatency,String oldName){
        if(rank<0||name==null)return false;
        if(oldName==null)return true;
        if(rank!=oldRank)return rank>oldRank;
        if(quality!=oldQuality)return quality>oldQuality;
        if(latency!=oldLatency)return latency<oldLatency;
        return name.compareTo(oldName)<0;
    }
    static final class Playback {
        final String text,prefix;
        final int limit;
        private int completed,start,end;
        private long serial;
        private String current;
        private boolean paused;
        Playback(String text,String prefix,int limit){
            if(prefix==null||prefix.isEmpty()||limit<2||limit>2000)
                throw new IllegalArgumentException("Playback scope");
            finalTranscript(text,160000); // validate only; preserve exact reviewed source and offsets
            this.text=text;this.prefix=prefix;this.limit=limit;
        }
        static final class Chunk {
            final String id,text;
            Chunk(String id,String text){this.id=id;this.text=text;}
        }
        Chunk next(){
            if(paused||current!=null||finished())throw new IllegalStateException("Playback state");
            start=completed;end=VoiceQueuePolicy.naturalChunkEnd(text,start,limit);
            current=prefix+(++serial)+"-"+start;
            return new Chunk(current,text.substring(start,end));
        }
        /** Both speech backends skip silent chunks while preserving exact source progress. */
        Chunk nextAudible(){
            while(!finished()){
                Chunk chunk=next();
                if(!VoiceQueuePolicy.silent(chunk.text))return chunk;
                acknowledge(chunk.id);
            }
            return null;
        }
        boolean current(String id){return current!=null&&current.equals(id);}
        boolean acknowledge(String id){
            if(!current(id))return false;
            completed=end;current=null;return true;
        }
        boolean pause(){
            if(paused||finished())return false;
            current=null;paused=true;return true;
        }
        boolean resume(){if(!paused||finished())return false;paused=false;return true;}
        boolean paused(){return paused;}
        boolean finished(){return completed>=text.length();}
        int completed(){return completed;}
        int percent(){return (int)((long)completed*100/text.length());}
    }
}
