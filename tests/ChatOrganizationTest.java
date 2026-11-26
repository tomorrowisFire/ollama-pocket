package com.ollamapocket.app;
public final class ChatOrganizationTest {
    private static int n;
    private static void ok(boolean b,String name){if(!b)throw new AssertionError(name);n++;}
    public static void main(String[] args){
        ok(ChatOrganization.validTitle("한국어 中文 日本語"),"multilingual title");
        ok(ChatOrganization.title("  title\nline\tend  ").equals("title line end"),"one line");
        ok(!ChatOrganization.validTitle(null)&&!ChatOrganization.validTitle(" \n\t"),"empty rejected");
        ok(ChatOrganization.validTitle("x".repeat(80))&&!ChatOrganization.validTitle("x".repeat(81)),"title limit");
        ok(ChatOrganization.compare(true,1,"a",false,99,"b")<0,"pinned first");
        ok(ChatOrganization.compare(false,1,"a",false,99,"b")>0,"recent first");
        ok(ChatOrganization.compare(true,99,"a",true,1,"b")<0,"recent pinned first");
        ok(ChatOrganization.compare(false,1,"a",false,1,"b")<0,"stable tie");
        System.out.println("ChatOrganizationTest PASS: "+n+" assertions");
    }
}
