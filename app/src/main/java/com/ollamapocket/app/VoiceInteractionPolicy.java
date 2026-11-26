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
        for(int i=0;i<text.length()&&result.length()<240;){
            int cp=text.codePointAt(i);i+=Character.charCount(cp);
            if(Character.isISOControl(cp)||Character.getType(cp)==Character.FORMAT||
                cp>=0xD800&&cp<=0xDFFF)continue;
            if(result.length()+Character.charCount(cp)>240)break;
            result.appendCodePoint(cp);
        }
        return result.toString().trim();
    }
    static final class Playback {
        final String text,prefix;
        final int limit;
        private int completed,start,end;
        private long serial;
        private String current;
        private boolean paused;
        Playback(String text,String prefix,int limit){
            if(text==null||text.trim().isEmpty()||text.length()>160000||prefix==null||prefix.isEmpty()||limit<2||limit>2000)
                throw new IllegalArgumentException("Playback scope");
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
