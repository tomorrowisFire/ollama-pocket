package com.ollamapocket.app;
import java.util.Arrays;

public final class LocalChatLibraryTest {
    private static int n;
    private static void ok(boolean v,String name){if(!v)throw new AssertionError(name);n++;}
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
        System.out.println("LocalChatLibraryTest PASS: "+n+" assertions");
    }
}
