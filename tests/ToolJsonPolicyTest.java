package com.ollamapocket.app;

import java.util.StringJoiner;

public final class ToolJsonPolicyTest {
    private static int checks;
    private static void accept(String text) {
        ToolJsonPolicy.requireObject(text); checks++;
    }
    private static void reject(String text) {
        try { ToolJsonPolicy.requireObject(text); }
        catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Unexpected acceptance: " + (text == null ? "null" : text.substring(0, Math.min(100,text.length()))));
    }
    private static String object(String value) { return "{\"x\":"+value+"}"; }
    private static String keys(int count) {
        StringJoiner j = new StringJoiner(",", "{", "}");
        for (int i=0;i<count;i++) j.add("\"k"+i+"\":0");
        return j.toString();
    }
    private static String array(int count) { return "["+"0,".repeat(count-1)+"0]"; }
    public static void main(String[] args) {
        for (String text : new String[]{"{}", " \t\r\n{}\n", "{\"x\":true,\"y\":false,\"z\":null}",
                "{\"a\":[1,-2,0,3.14,-0.5,1e2,-1E-2,{\"b\":\"한글😀\"}]}",
                "{\"a\":{},\"b\":{\"a\":1}}", "{\"\":0}", "{\"x\":\"\\uD83D\\uDE00\"}",
                "{\"x\":\"\\\"\\\\\\/\\b\\f\\n\\r\\t\"}", object("1e256"), object("1e-256"),
                object("-0"), object("0.0"), object("1E+1"), object("[".repeat(7)+"0"+"]".repeat(7)),
                keys(128), object(array(1022)), "{\""+"k".repeat(128)+"\":0}",
                object("\""+"a".repeat(15992)+"\"")}) accept(text);
        for (String text : new String[]{null,"","[]","null","{}{}","{}x","\uFEFF{}","\u00A0{}","{x:1}","{'x':1}",
                "{\"x\":1,}","{\"x\":[1,]}","{\"x\":[,1]}","{\"x\" 1}","{\"x\":}","{\"x\":1 \"y\":2}",
                "{/*c*/\"x\":1}","{\"x\"://c\n1}","{\"x\":1,\"x\":2}","{\"x\":1,\"\\u0078\":2}",
                "{\"😀\":1,\"\\uD83D\\uDE00\":2}","{\"x\":{\"y\":1,\"y\":2}}", "{\"x\":\"\\q\"}",
                "{\"x\":\"\\u12\"}","{\"x\":\"\\u00ＦＦ\"}","{\"x\":\"\\uD800\"}","{\"x\":\"\\uDC00\"}",
                "{\"x\":\"\\uD800a\"}","{\"x\":\""+(char)0xD800+"\"}","{\"x\":\"line\nbreak\"}",
                "{\"x\":\""+(char)0+"\"}","{\"x\":\"unterminated}", "{\"x\":truex}", "{\"x\":True}",
                keys(129),object(array(1023)),object("[".repeat(8)+"0"+"]".repeat(8)),
                "{\""+"k".repeat(129)+"\":0}",object("\""+"a".repeat(15993)+"\"")}) reject(text);
        for (String number : new String[]{"+1","01","-01","00",".1","1.","--1","-","1e","1e+","1e+-2","1e257","1e-257","1e999999999","0x10","NaN","Infinity","1_000","1".repeat(129),"１"}) reject(object(number));
        for (int i = 0; i <= 255; i++) {
            String escaped = String.format("\\u%04x",i);
            accept("{\"x\":\""+escaped+"\"}");
            reject("{\""+escaped+"\":0,\""+escaped+"\":1}");
        }
        System.out.println("PASS: "+checks+" strict tool JSON admission checks");
    }
}
