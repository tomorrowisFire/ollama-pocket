package com.ollamapocket.app;

import java.util.Locale;

public final class WorkspaceDictationPolicyTest {
    private static int n;
    private static void check(boolean b){n++;if(!b)throw new AssertionError("workspace dictation "+n);}
    private static void reject(String original,String spoken,int limit,boolean multi){
        try{WorkspaceDictationPolicy.review(original,spoken,limit,multi);}
        catch(IllegalArgumentException expected){check(true);return;}
        throw new AssertionError("Invalid dictation accepted");
    }
    public static void main(String[] args){
        WorkspaceDictationPolicy.Review r=WorkspaceDictationPolicy.review("old"," new ",10,false);
        check(r.original.equals("old"));check(r.spoken.equals("new"));check(r.suffix.equals(" new"));check(r.merged.equals("old new"));
        check(r.matches("old")&&!r.matches("OLD")&&!r.matches(null));check(r.apply("old").equals("old new"));
        try{r.apply("changed");throw new AssertionError("Stale draft accepted");}catch(IllegalStateException expected){check(true);}
        check(WorkspaceDictationPolicy.review("old","new",7,true).merged.equals("old\nnew"));
        check(WorkspaceDictationPolicy.review("","new",3,true).merged.equals("new"));
        check(WorkspaceDictationPolicy.review("","new",3,false).merged.equals("new"));
        check(WorkspaceDictationPolicy.review(" a ","b",5,false).merged.equals(" a  b"));
        check(WorkspaceDictationPolicy.review("a","b\nc",5,true).merged.equals("a\nb\nc"));
        check(WorkspaceDictationPolicy.review("a","b\tc",5,true).merged.equals("a\nb\tc"));
        check(WorkspaceDictationPolicy.review("한글","日本語",6,false).merged.equals("한글 日本語"));
        check(WorkspaceDictationPolicy.review("简体","繁體",5,true).merged.equals("简体\n繁體"));
        check(WorkspaceDictationPolicy.review("🙂","漢",4,false).merged.equals("🙂 漢"));
        check(WorkspaceDictationPolicy.review("","👩‍💻",5,true).merged.equals("👩‍💻"));
        reject("🙂","漢",3,false);reject("old","new",6,false);reject("old","new",6,true);
        reject("","",3,true);reject("","   ",3,true);reject("",null,10,true);reject(null,"a",10,true);
        reject("","a",0,true);reject("1234","a",3,true);reject("","long",3,true);
        reject("a","b\nc",20,false);reject("a","b\tc",20,false);reject("a","b\rc",20,false);
        reject("a","b\u2028c",20,false);reject("a","b\u2029c",20,false);
        reject("a\nb","c",20,false);reject("a\tb","c",20,false);
        reject("a","b\u0000c",20,true);reject("a","b\u202Ec",20,true);
        reject("a","\uD800",20,true);reject("\uDC00","a",20,true);
        reject("","\u200D",20,true);reject("","\u3000",20,true);
        String max="x".repeat(32000);
        check(WorkspaceDictationPolicy.review(max.substring(0,31998),"y",32000,true).merged.length()==32000);
        reject(max,"a",32000,true);
        String[] tags={"ko-KR","en-US","zh-CN","zh-TW","ja-JP"};
        for(int i=1;i<VoiceInteractionPolicy.LANGUAGES.length;i++)
            check(VoiceInteractionPolicy.locale(VoiceInteractionPolicy.LANGUAGES[i],Locale.US).toLanguageTag().equals(tags[i-1]));
        check(VoiceInteractionPolicy.locale("app",Locale.JAPAN).equals(Locale.JAPAN));
        System.out.println("PASS: "+n+" reviewed workspace dictation assertions");
    }
}
