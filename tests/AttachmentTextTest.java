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
        for(String blank:new String[]{"\u00a0","\u3000","\u2003","\u202f","\u0085","\u00a0 \u3000\t"}){
            bad("a.txt",bytes(blank),AttachmentText.Problem.EMPTY);
            ok(!AttachmentText.hasContent(blank),"Unicode blank");
            ok(AttachmentText.hasContent(blank+"한글😀"),"mixed content");
            try{AttachmentText.include("q","a.txt",blank);throw new AssertionError("blank excerpt");}
            catch(IllegalArgumentException expected){n++;}
        }
        String mixed="\u00a0한글\u3000";
        ok(read("a.txt",bytes(mixed)).text.equals(mixed),"preserve Unicode source offsets");
        String included=AttachmentText.include("Summarize","report.txt","raw text");
        ok(included.startsWith("Summarize")&&included.contains("source data")&&included.contains("raw text"),"explicit context");
        try{AttachmentText.include("q".repeat(16000),"a.txt","x");throw new AssertionError("limit");}catch(IllegalArgumentException e){n++;}
        for(AttachmentText.Encoding encoding:new AttachmentText.Encoding[]{AttachmentText.Encoding.CP949,AttachmentText.Encoding.JAPANESE,AttachmentText.Encoding.GB18030,AttachmentText.Encoding.BIG5}){
            String value=encoding==AttachmentText.Encoding.CP949?"한글":encoding==AttachmentText.Encoding.JAPANESE?"日本語":"中文";
            byte[] raw=value.getBytes(java.nio.charset.Charset.forName(encoding.charset));
            ok(AttachmentText.read("legacy.csv",new ByteArrayInputStream(raw),1,encoding).text.equals(value),"explicit legacy encoding");
        }
        try{AttachmentText.read("bom.txt",new ByteArrayInputStream("한글".getBytes(StandardCharsets.UTF_16)),1,AttachmentText.Encoding.CP949);throw new AssertionError("BOM");}
        catch(AttachmentText.Failure e){ok(e.problem==AttachmentText.Problem.INVALID,"no legacy reinterpretation of BOM");}
        AttachmentText.Result partial=read("report.txt",bytes("x".repeat(7000)));
        String reviewed=AttachmentText.includeReviewed("Analyze",partial,"edited");
        ok(reviewed.contains("PARTIAL SOURCE")&&reviewed.contains("USER-EDITED PREVIEW"),"scope and edit provenance");
        ok(reviewed.contains("positions 1–5999")&&reviewed.contains("edited"),"original range retained");
        ok(!AttachmentText.includeReviewed("",partial,partial.text).contains("USER-EDITED PREVIEW"),"unedited evidence");
        AttachmentText.Result pdf=new AttachmentText.Result("a.pdf","data",true,false,true,20,3,2,false,0,0,0,0,0,4);
        ok(AttachmentText.includeReviewed("",pdf,"data").contains("pages 4–6 of 20"),"PDF source coverage");
        AttachmentText.Result ocr=new AttachmentText.Result("scan.pdf","data",true,false,false,0,0,0,true,2,4);
        ok(AttachmentText.includeReviewed("",ocr,"data").contains("OCR page 2 of 4"),"OCR provenance");
        try{AttachmentText.includeReviewed("q".repeat(16000),partial,"edited");throw new AssertionError("limit");}catch(IllegalArgumentException e){n++;}
        System.out.println("AttachmentTextTest PASS: "+n+" assertions");
    }
}
