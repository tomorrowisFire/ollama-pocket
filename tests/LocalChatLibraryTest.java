package com.ollamapocket.app;
import java.util.Arrays;

public final class LocalChatLibraryTest {
    private static int n;
    private static void ok(boolean v,String name){if(!v)throw new AssertionError(name);n++;}
    private static boolean intact(String s){
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(Character.isHighSurrogate(c)){
                if(++i>=s.length()||!Character.isLowSurrogate(s.charAt(i)))return false;
            }else if(Character.isLowSurrogate(c))return false;
        }
        return true;
    }
    private static void preview(String source,String query,String original){
        String q=LocalChatLibrary.query(query);
        ok(LocalChatLibrary.matches(source,q),"source matches");
        String result=LocalChatLibrary.excerpt(source,q);
        ok(result.contains(original),"literal original excerpt");
        ok(LocalChatLibrary.matches(result,q),"verified normalized excerpt");
        ok(result.length()<=LocalChatLibrary.MAX_EXCERPT+2&&intact(result),"bounded intact excerpt");
        ok(source.replace('\n',' ').replace('\r',' ').replace('\t',' ').contains(result.replaceFirst("^…","").replaceFirst("…$","")),"source substring");
    }
    public static void main(String[] args){
        ok(LocalChatLibrary.matches("한국어 질문",LocalChatLibrary.query("질문")),"Korean");
        ok(LocalChatLibrary.matches("Ｑｗｅｎ 中文",LocalChatLibrary.query("qwen")),"NFKC and case");
        ok(LocalChatLibrary.matches("日本語の回答",LocalChatLibrary.query("回答")),"Japanese");
        ok(!LocalChatLibrary.matches("anything",""),"no empty match");
        ok(LocalChatLibrary.query("x".repeat(300)).length()==200,"query bound");
        String text="prefix ".repeat(30)+"needle\nsecond line";
        String excerpt=LocalChatLibrary.excerpt(text,"needle");
        ok(excerpt.contains("needle")&&excerpt.startsWith("…")&&!excerpt.contains("\n"),"excerpt");
        ok(LocalChatLibrary.digest("abc").equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),"snapshot digest");
        String original="**answer**\n\u0060\u0060\u0060java\nx();\n\u0060\u0060\u0060";
        String md=LocalChatLibrary.markdown("qwen:1b",Arrays.asList(new LocalChatTurns.Message("user","question"),new LocalChatTurns.Message("assistant",original)),"User","Assistant","Other");
        ok(md.contains("## User\n\nquestion")&&md.contains("## Assistant\n\n"+original),"raw source export");
        try{LocalChatLibrary.markdown("model",Arrays.asList(new LocalChatTurns.Message("user","x".repeat(LocalChatLibrary.MAX_EXPORT))),"U","A","O");throw new AssertionError("export limit");}
        catch(IllegalArgumentException e){n++;}
        String emoji="\uD83D\uDE80";
        ok(LocalChatLibrary.shortTitle("x".repeat(37)+emoji+"z").equals("x".repeat(37)+"…"),"title pair boundary");
        ok(LocalChatLibrary.shortTitle("x".repeat(36)+emoji+"z").equals("x".repeat(36)+emoji+"…"),"title intact pair");
        ok(LocalChatLibrary.shortTitle("x".repeat(36)+emoji).equals("x".repeat(36)+emoji),"exact title limit");
        ok(LocalChatLibrary.shortTitle("  A\nB\rC\tD  ").equals("A B C D"),"title whitespace");
        ok(LocalChatLibrary.shortTitle(null).isEmpty(),"missing title");
        ok(LocalChatLibrary.query("x".repeat(199)+emoji+"z").equals("x".repeat(199)),"query pair boundary");
        ok(LocalChatLibrary.query("x".repeat(198)+emoji+"z").equals("x".repeat(198)+emoji),"query intact pair");
        ok(LocalChatLibrary.query("x".repeat(198)+emoji).length()==200,"exact query limit");
        ok(LocalChatLibrary.query(null).isEmpty()&&!LocalChatLibrary.matches("source",null),"missing query");
        preview("x".repeat(600)+"Ｑｗｅｎ 中文"+"y".repeat(500),"qwen","Ｑｗｅｎ");
        preview("ﬃ".repeat(4000)+" 目标 中文 "+"y".repeat(500),"目标","目标");
        preview("e\u0301".repeat(5000)+"  日本語の回答  "+"y".repeat(500),"回答","回答");
        preview("İ".repeat(1500)+"  한국어 질문  "+"z".repeat(500),"질문","질문");
        preview("x".repeat(4094)+"Ｑｗｅｎ"+"y".repeat(500),"qwen","Ｑｗｅｎ");
        preview("x".repeat(700)+"Cafe\u0301"+"y".repeat(500),"café","Cafe\u0301");
        preview("x".repeat(800)+"Z".repeat(200)+"y".repeat(700),"z".repeat(200),"Z".repeat(200));
        preview(emoji.repeat(500)+" needle "+emoji.repeat(500),"needle","needle");
        preview("繁體中文  搜尋", "搜尋", "搜尋");
        ok(LocalChatLibrary.excerpt("unrelated prefix","missing").isEmpty(),"no misleading prefix");
        ok(LocalChatLibrary.excerpt("hello"," ".repeat(201)).isEmpty(),"oversized query");
        ok(LocalChatLibrary.excerpt("a".repeat(LocalChatLibrary.MAX_SEARCH_CONVERSATION+1),"a").isEmpty(),"source cap");
        ok(LocalChatLibrary.excerpt("a\nb","a\nb").isEmpty(),"flattened mismatch omitted");
        Thread.currentThread().interrupt();
        try{ok(LocalChatLibrary.excerpt("test test","test").isEmpty(),"interrupted search");ok(Thread.currentThread().isInterrupted(),"interrupt preserved");}
        finally{Thread.interrupted();}
        System.out.println("LocalChatLibraryTest PASS: "+n+" assertions");
    }
}
