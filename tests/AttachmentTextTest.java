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
        for(String name:new String[]{"main.PY","app.js","index.tsx","page.html","style.css","data.xml","config.yaml","query.sql","Main.java","Program.cs","main.cpp","game.gd","script.ps1","Dockerfile","Makefile",".gitignore","notebook.ipynb","notes.tex","chart.svg","data.jsonl","project.csproj","README"}){
            ok(read(name,bytes("한국어 😀 source")).text.equals("한국어 😀 source"),"Plain source "+name);
        }
        for(String name:new String[]{"a.exe","a.apk","a.dll","a.zip","a.rar","a.hwp","a.doc","a.xls","a.mp3","../code.py"})
            bad(name,bytes("not executable"),AttachmentText.Problem.UNSUPPORTED);
        byte[] large=bytes("a".repeat(2*1024*1024)+"TAIL");
        AttachmentText.Result tail=AttachmentText.read("big.log",new ByteArrayInputStream(large),350);
        ok(tail.text.endsWith("TAIL")&&tail.rangeEnd==large.length,"Late window beyond prior 1 MB limit");
        ok(AttachmentText.readPrefix("big.log",new ByteArrayInputStream(large),AttachmentText.Encoding.UNICODE,50).partial,"Bounded batch prefix");
        byte[] lateBad=large.clone();lateBad[lateBad.length-1]=(byte)0xff;
        bad("bad.py",lateBad,AttachmentText.Problem.INVALID);
        byte[] edge=bytes("a".repeat(AttachmentText.MAX_BYTES));
        ok(read("large.txt",edge).partial,"Exact larger byte cap");
        bad("large.txt",java.util.Arrays.copyOf(edge,edge.length+1),AttachmentText.Problem.TOO_LARGE);
        ok(AttachmentText.MAX_BYTES==16*1024*1024,"16 MiB text limit");
        String lateUnicode="a".repeat(5999*300-1)+"😀"+"last";
        byte[] unicodeBytes=bytes(lateUnicode);
        var left=AttachmentText.read("late.py",new ByteArrayInputStream(unicodeBytes),300);
        var right=AttachmentText.read("late.py",new ByteArrayInputStream(unicodeBytes),301);
        ok(left.rangeEnd==right.rangeStart&&right.text.equals("😀last"),"Large Unicode window boundary");
        InputStream stalled=new InputStream(){
            @Override public int read(){return 0;}
            @Override public int read(byte[] b,int off,int len){return 0;}
        };
        try{AttachmentText.read("stall.txt",stalled);throw new AssertionError("Stalled provider");}
        catch(AttachmentText.Failure e){ok(e.problem==AttachmentText.Problem.INVALID,"No zero-progress loop");}
        Thread.currentThread().interrupt();
        try{
            try{read("cancel.py",large);throw new AssertionError("Cancelled provider");}
            catch(InterruptedIOException expected){n++;}
        }finally{Thread.interrupted();}
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
