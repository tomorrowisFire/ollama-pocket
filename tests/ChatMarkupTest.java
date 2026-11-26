package com.ollamapocket.app;

public final class ChatMarkupTest {
    private static int n;
    private static void ok(boolean value,String label){if(!value)throw new AssertionError(label);n++;}
    private static void eq(String source,String expected){
        ChatMarkup.Result result=ChatMarkup.parse(source);ok(expected.equals(result.text),"display: "+result.text);
        for(ChatMarkup.Span s:result.spans)ok(s.start>=0&&s.start<s.end&&s.end<=result.text.length(),"valid span");
    }
    public static void main(String[] args){
        eq("plain 한국어 中文 日本語","plain 한국어 中文 日本語");
        eq("**bold** and *italic*","bold and italic");
        eq("# Heading\n- item\n+ another","Heading\n• item\n• another");
        eq("Use `a_b` now","Use a_b now");
        eq("```java\nint n=1;\n**literal**\n```\nDone","int n=1;\n**literal**\nDone");
        eq("```\n**unfinished**","```\n**unfinished**");
        eq("| A | B |\n| --- | --- |\n| 1 | **2** |","| A | B |\n| --- | --- |\n| 1 | **2** |");
        eq("<script>alert(1)</script> ![image](https://example.org/x)","<script>alert(1)</script> ![image](https://example.org/x)");
        eq("escaped \\*not emphasis*","escaped \\*not emphasis*");
        eq("unclosed **bold","unclosed **bold");
        eq("a\r\nb\n","a\nb\n");
        eq(null,"");
        String big="**".repeat(90000);eq(big,big);
        ok(ChatMarkup.parse("**x**").spans.get(0).kind==ChatMarkup.Kind.BOLD,"bold kind");
        ok(ChatMarkup.parse("`x`").spans.get(0).kind==ChatMarkup.Kind.CODE,"code kind");
        ok(ChatMarkup.parse("# H").spans.get(0).kind==ChatMarkup.Kind.HEADING,"heading kind");
        ok(ChatMarkup.parse("| A | B |\n| --- | --- |\n| 1 | 2 |").spans.get(0).kind==ChatMarkup.Kind.TABLE,"table kind");
        System.out.println("ChatMarkupTest PASS: "+n+" assertions");
    }
}
