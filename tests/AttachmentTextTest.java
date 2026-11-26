package com.ollamapocket.app;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class AttachmentTextTest {
    private static int n;
    private static void ok(boolean v,String label){if(!v)throw new AssertionError(label);n++;}
    private static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    private static AttachmentText.Result read(String name,byte[] b)throws Exception{return AttachmentText.read(name,new ByteArrayInputStream(b));}
    private static void bad(String name,byte[] data,AttachmentText.Problem reason)throws Exception{
        try{read(name,data);}catch(AttachmentText.Failure e){ok(e.problem==reason,"expected "+reason+" got "+e.problem);return;}
        throw new AssertionError("unexpected acceptance "+name);
    }
    public static void main(String[] args)throws Exception{
        ok(read("a.TXT",bytes("한국어 中文 日本語")).text.equals("한국어 中文 日本語"),"Unicode");
        ok(read("a.csv",bytes("A,B\n1,2")).text.equals("A,B\n1,2"),"CSV source");
        ok(read("a.txt","한글".getBytes(StandardCharsets.UTF_16)).text.equals("한글"),"UTF16 BOM");
        ok(read("a.md",bytes("x".repeat(7000))).partial,"excerpt limit");
        ok(read("a.txt",bytes("x".repeat(5999)+"😀")).text.length()==5999,"surrogate boundary");
        bad("a.pdf",bytes("text"),AttachmentText.Problem.UNSUPPORTED);
        bad("a.txt",new byte[]{0,1,2},AttachmentText.Problem.INVALID);
        bad("a.txt",new byte[]{(byte)0xff},AttachmentText.Problem.INVALID);
        bad("a.txt",new byte[AttachmentText.MAX_BYTES+1],AttachmentText.Problem.TOO_LARGE);
        bad("a.txt",bytes(" \n"),AttachmentText.Problem.EMPTY);
        String included=AttachmentText.include("Summarize","report.txt","raw text");
        ok(included.startsWith("Summarize")&&included.contains("source data")&&included.contains("raw text"),"explicit context");
        try{AttachmentText.include("q".repeat(16000),"a.txt","x");throw new AssertionError("limit");}catch(IllegalArgumentException e){n++;}
        System.out.println("AttachmentTextTest PASS: "+n+" assertions");
    }
}
