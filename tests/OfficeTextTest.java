package com.ollamapocket.app;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

public final class OfficeTextTest {
    private static final String W="http://schemas.openxmlformats.org/wordprocessingml/2006/main",
        S="http://schemas.openxmlformats.org/spreadsheetml/2006/main",
        R="http://schemas.openxmlformats.org/officeDocument/2006/relationships",
        P="http://schemas.openxmlformats.org/package/2006/relationships";
    private static int n;
    private static void ok(boolean v,String m){if(!v)throw new AssertionError(m);n++;}
    private static byte[] zip(String... pairs)throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(out)){
            for(int i=0;i<pairs.length;i+=2){
                z.putNextEntry(new ZipEntry(pairs[i]));z.write(pairs[i+1].getBytes(StandardCharsets.UTF_8));z.closeEntry();
            }
        }return out.toByteArray();
    }
    private static AttachmentText.Result read(String name,byte[] data)throws Exception{return AttachmentText.read(name,new ByteArrayInputStream(data));}
    private static void bad(String name,byte[] data,AttachmentText.Problem why)throws Exception{
        try{read(name,data);}catch(AttachmentText.Failure e){ok(e.problem==why,"expected "+why+" got "+e.problem);return;}
        throw new AssertionError("Unexpected acceptance");
    }
    private static String doc(String content){return "<w:document xmlns:w=\""+W+"\"><w:body>"+content+"</w:body></w:document>";}
    private static String para(String text){return "<w:p><w:r><w:t>"+text+"</w:t></w:r></w:p>";}
    private static String book(String sheets){return "<workbook xmlns=\""+S+"\" xmlns:r=\""+R+"\"><sheets>"+sheets+"</sheets></workbook>";}
    private static String rel(String id,String target){return "<Relationship Id=\""+id+"\" Type=\""+R+"/worksheet\" Target=\""+target+"\"/>";}
    private static String rels(String entries){return "<Relationships xmlns=\""+P+"\">"+entries+"</Relationships>";}
    private static String sheet(String cells){return "<worksheet xmlns=\""+S+"\"><sheetData><row r=\"1\">"+cells+"</row></sheetData></worksheet>";}
    public static void main(String[] args)throws Exception{
        AttachmentText.Result word=read("report.DOCX",zip("word/document.xml",doc(para("한국어 中文 日本語")+para("Second"))));
        ok(word.office&&!word.partial&&word.text.contains("[Paragraph 1] 한국어 中文 日本語"),"Word unicode and reference");
        ok(word.text.contains("[Paragraph 2] Second"),"paragraph order");
        ok(read("a.docx",zip("word/document.xml",doc(para("x".repeat(7000))))).partial,"excerpt truncation");
        bad("a.docx",zip("word/document.xml",doc("<w:p/>")),AttachmentText.Problem.EMPTY);
        bad("a.docx",zip("word/document.xml","<x/>"),AttachmentText.Problem.INVALID);
        bad("a.docx",zip("word/document.xml","<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"+doc(para("&e;"))),AttachmentText.Problem.INVALID);
        bad("a.docx",zip("../escape","x","word/document.xml",doc(para("ok"))),AttachmentText.Problem.INVALID);
        bad("a.docx",zip("word/document.xml","x".repeat(OfficeText.MAX_PART+1)),AttachmentText.Problem.TOO_LARGE);
        bad("a.docx",new byte[]{0,1,2},AttachmentText.Problem.INVALID);
        String wb=book("<sheet name=\"실적\" sheetId=\"2\" r:id=\"r2\"/><sheet name=\"Summary\" sheetId=\"1\" r:id=\"r1\"/>");
        String rel=rels(rel("r1","worksheets/sheet1.xml")+rel("r2","/xl/worksheets/sheet2.xml"));
        byte[] excel=zip("xl/workbook.xml",wb,"xl/_rels/workbook.xml.rels",rel,
            "xl/sharedStrings.xml","<sst xmlns=\""+S+"\"><si><t>中文</t></si></sst>",
            "xl/worksheets/sheet1.xml",sheet("<c r=\"A1\" t=\"inlineStr\"><is><t>日本語</t></is></c>"),
            "xl/worksheets/sheet2.xml",sheet("<c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\"><f>1+2</f><v>3</v></c>"));
        String data=read("model.xlsx",excel).text;
        ok(data.indexOf("[Sheet: 실적]")>=0&&data.indexOf("[Sheet: 실적]")<data.indexOf("[Sheet: Summary]"),"workbook display names and order");
        ok(data.contains("A1: 中文")&&data.contains("A1: 日本語"),"shared and inline strings");
        ok(data.contains("B1: [formula: 1+2] [cached value: 3]"),"formula not executed");
        byte[] external=zip("xl/workbook.xml",book("<sheet name=\"X\" sheetId=\"1\" r:id=\"r1\"/>"),
            "xl/_rels/workbook.xml.rels",rels("<Relationship Id=\"r1\" Type=\""+R+"/worksheet\" TargetMode=\"External\" Target=\"https://invalid.example/sheet.xml\"/>"));
        bad("a.xlsx",external,AttachmentText.Problem.EMPTY);
        bad("a.xlsx",zip("xl/workbook.xml",wb),AttachmentText.Problem.INVALID);
        ok(AttachmentText.maxBytes("a.xlsx")==OfficeText.MAX_BYTES&&AttachmentText.maxBytes("a.txt")==AttachmentText.MAX_BYTES,"separate size limits");
        bad("a.docx",new byte[OfficeText.MAX_BYTES+1],AttachmentText.Problem.TOO_LARGE);
        System.out.println("OfficeTextTest PASS: "+n+" assertions");
    }
}
