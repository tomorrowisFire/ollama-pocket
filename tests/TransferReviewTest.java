package com.ollamapocket.app;
import java.util.Arrays;
public final class TransferReviewTest {
    private static int n;
    private static void ok(boolean v,String why){if(!v)throw new AssertionError(why);n++;}
    public static void main(String[] args){
        String a=TransferReview.fingerprint("chat1","model","cooperative","payload");
        ok(a.equals(TransferReview.fingerprint("chat1","model","cooperative","payload")),"stable");
        ok(!a.equals(TransferReview.fingerprint("chat2","model","cooperative","payload")),"scope");
        ok(!a.equals(TransferReview.fingerprint("chat1","other","cooperative","payload")),"model");
        ok(!a.equals(TransferReview.fingerprint("chat1","model","cpu","payload")),"route");
        ok(!a.equals(TransferReview.fingerprint("chat1","model","cooperative","payload2")),"payload");
        ok(!TransferReview.fingerprint("ab","c","","").equals(TransferReview.fingerprint("a","bc","","")),"length framing");
        String first="한국어 [Attached file excerpt: report.pdf]\n[Page 13]\n中文 日本語";
        String text=TransferReview.text(Arrays.asList(new LocalChatTurns.Message("user",first),new LocalChatTurns.Message("assistant","Answer")),"U","A","Other");
        ok(text.contains(first)&&text.contains("2. A\nAnswer"),"complete context");
        try{TransferReview.text(Arrays.asList(new LocalChatTurns.Message("user","x".repeat(100001))),"U","A","O");throw new AssertionError("limit");}
        catch(IllegalArgumentException e){n++;}
        System.out.println("TransferReviewTest PASS: "+n+" assertions");
    }
}
