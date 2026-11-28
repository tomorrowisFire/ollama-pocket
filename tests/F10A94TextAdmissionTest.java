package com.ollamapocket.app;

import java.util.List;

/** Independent metadata and cloud-history regressions; no network or persisted data. */
public final class F10A94TextAdmissionTest {
    private static int checks;
    private static void ok(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void reject(Runnable action,String label){
        try{action.run();throw new AssertionError(label);}
        catch(IllegalArgumentException expected){checks++;}
    }
    private static void titles(){
        for(String invalid:new String[]{"\u00a0\u3000","\u200d","x\u0000","\u0001x","x\ud800","x\udc00","x\u202e","x\u2028y"})
            ok(!ChatOrganization.validTitle(invalid),"invalid title");
        for(String valid:new String[]{"한국어 中文 日本語","👩‍💻","a\u200cb"," title\r\nline\tend ","x".repeat(80)})
            ok(ChatOrganization.validTitle(valid),"valid title");
        ok(ChatOrganization.title(" title\r\nline\tend ").equals("title  line end"),"legacy layout preserved");
        ok(!ChatOrganization.validTitle("x".repeat(81)),"title limit");
    }
    private static void cloud(){
        for(String invalid:new String[]{"x\u0000","\u0001x","x\u007f","x\u0085","x\ud800","x\udc00","\u00a0\u3000","\u200d"}){
            ok(!CloudHistory.validMessage(invalid,100),"invalid cloud message");
            ok(CloudHistory.requestChars(List.of(new CloudHistory.Message("user",invalid)))==-1,"invalid outbound context");
            CloudHistory.Session session=new CloudHistory().session("OPENAI","model");
            session.draft("draft");
            reject(()->session.complete("question",invalid),"invalid answer committed");
            ok(session.turns().isEmpty()&&session.draft().equals("draft")&&session.revision()==0,"failed commit preserves state");
        }
        for(String valid:new String[]{"한국어 中文 日本語","👩‍💻","a\u200cb","a\r\nb\t", "\u00a0x"}){
            ok(CloudHistory.validMessage(valid,100),"valid multilingual content");
            CloudHistory.Session session=new CloudHistory().session("OPENAI","model");
            session.complete("question",valid);
            ok(session.turns().get(0).answer.equals(valid),"exact text retained");
        }
    }
    public static void main(String[] args){
        if(args.length==0||args[0].equals("title"))titles();
        if(args.length==0||args[0].equals("cloud"))cloud();
        System.out.println("PASS: "+checks+" text admission assertions");
    }
}
