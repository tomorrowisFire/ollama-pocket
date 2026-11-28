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
        String pp="http://schemas.openxmlformats.org/presentationml/2006/main",draw="http://schemas.openxmlformats.org/drawingml/2006/main";
        String pres="<p:presentation xmlns:p=\""+pp+"\" xmlns:r=\""+R+"\"><p:sldIdLst><p:sldId r:id=\"s2\"/><p:sldId r:id=\"s1\"/></p:sldIdLst></p:presentation>";
        String pr=rels("<Relationship Id=\"s1\" Type=\""+R+"/slide\" Target=\"slides/slide1.xml\"/><Relationship Id=\"s2\" Type=\""+R+"/slide\" Target=\"slides/slide2.xml\"/>");
        String slide="<p:sld xmlns:p=\""+pp+"\" xmlns:a=\""+draw+"\"><a:p><a:r><a:t>VALUE</a:t></a:r></a:p></p:sld>";
        byte[] ppt=zip("ppt/presentation.xml",pres,"ppt/_rels/presentation.xml.rels",pr,
                "ppt/slides/slide1.xml",slide.replace("VALUE","Second"),"ppt/slides/slide2.xml",slide.replace("VALUE","한국어 😀"));
        for(String name:new String[]{"test.pptx","test.pptm","test.ppsx","test.potx"}){
            var result=read(name,ppt);
            ok(result.partial&&result.text.contains("[Slide 1] 한국어 😀")&&result.text.indexOf("한국어")<result.text.indexOf("Second"),"Slide order "+name);
        }
        bad("bad.pptx",zip("ppt/presentation.xml",pres),AttachmentText.Problem.INVALID);
        bad("bad.pptx",zip("ppt/presentation.xml",pres,"ppt/_rels/presentation.xml.rels",pr,
                "ppt/slides/slide2.xml","<!DOCTYPE x>"+slide),AttachmentText.Problem.INVALID);
        String hw="<hs:sec xmlns:hs=\"http://www.hancom.co.kr/hwpml/2011/section\" xmlns:hp=\"http://www.hancom.co.kr/hwpml/2011/paragraph\"><hp:p><hp:run><hp:t>VALUE</hp:t></hp:run></hp:p></hs:sec>";
        var hwp=read("한글.HWPX",zip("Contents/section1.xml",hw.replace("VALUE","둘째"),"Contents/section0.xml",hw.replace("VALUE","첫째 😀")));
        ok(hwp.office&&hwp.partial&&hwp.text.contains("[Section 1] 첫째 😀")&&hwp.text.indexOf("첫째")<hwp.text.indexOf("둘째"),"HWPX section order");
        bad("bad.hwpx",zip("Contents/section0.xml","<x/>"),AttachmentText.Problem.INVALID);
        String odf="<office:document-content xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\" xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\" xmlns:draw=\"urn:oasis:names:tc:opendocument:xmlns:drawing:1.0\"><office:body>VALUE</office:body></office:document-content>";
        String odt=odf.replace("VALUE","<office:text><text:p>안녕<text:s text:c=\"2\"/>世界</text:p></office:text>");
        ok(read("test.odt",zip("mimetype","application/vnd.oasis.opendocument.text","content.xml",odt)).text.contains("안녕  世界"),"ODT spaces");
        String ods=odf.replace("VALUE","<office:spreadsheet><table:table table:name=\"Sales\"><table:table-row><table:table-cell office:value=\"42\" table:formula=\"of:=SUM(A1:A2)\"/><table:table-cell><text:p>中文</text:p></table:table-cell></table:table-row></table:table></office:spreadsheet>");
        String odsc=read("test.ods",zip("mimetype","application/vnd.oasis.opendocument.spreadsheet","content.xml",ods)).text;
        ok(odsc.contains("[formula: of:=SUM(A1:A2)] [stored text/value: 42]")&&odsc.contains("中文"),"ODS stored values");
        String odp=odf.replace("VALUE","<office:presentation><draw:page draw:name=\"One\"><text:p>日本語</text:p></draw:page></office:presentation>");
        ok(read("test.odp",zip("mimetype","application/vnd.oasis.opendocument.presentation","content.xml",odp)).text.contains("[Slide One] 日本語"),"ODP slide text");
        bad("test.odt",zip("mimetype","application/vnd.oasis.opendocument.spreadsheet","content.xml",odt),AttachmentText.Problem.INVALID);
        ok(read("test.docm",zip("word/document.xml",doc(para("macro text")),"word/vbaProject.bin","NOT EXECUTED")).text.contains("macro text"),"Macro package text only");
        ok(read("test.xlsm",excel).text.equals(data),"XLSM stored values only");
        ok(AttachmentText.maxBytes("a.xlsx")==OfficeText.MAX_BYTES&&AttachmentText.maxBytes("a.txt")==AttachmentText.MAX_BYTES,"separate size limits");
        ok(OfficeText.MAX_BYTES==500*1024*1024&&OfficeText.MAX_EXPANDED==768*1024*1024,"500 MiB compressed cap and bounded expansion");
        byte[] heavy=zip("word/document.xml",doc(para("large package")),"word/media/large.bin","x".repeat(10*1024*1024));
        ok(read("big.docx",heavy).text.contains("large package"),"Stream unretained large media");
        // Actual compressed-byte cap includes bytes after the last local ZIP entry.
        InputStream oversized=new SequenceInputStream(new ByteArrayInputStream(heavy),new InputStream(){
            long left=(long)OfficeText.MAX_BYTES-heavy.length+1;
            @Override public int read(){if(left==0)return -1;left--;return 0;}
            @Override public int read(byte[] b,int off,int len){
                if(len==0)return 0;if(left==0)return -1;
                int n=(int)Math.min(left,len);java.util.Arrays.fill(b,off,off+n,(byte)0);left-=n;return n;
            }
        });
        try{AttachmentText.read("big.docx",oversized);throw new AssertionError("Oversized archive");}
        catch(AttachmentText.Failure e){ok(e.problem==AttachmentText.Problem.TOO_LARGE,"Physical byte limit without large array");}
        try{AttachmentText.read("a.docx",new ByteArrayInputStream(heavy),DocumentWindow.MAX_OFFICE_SEGMENTS+1);throw new AssertionError("Office range");}
        catch(AttachmentText.Failure e){ok(e.problem==AttachmentText.Problem.RANGE,"Office range cap");}
        ok(OfficeText.MAX_RETAINED==24*1024*1024&&OfficeText.MAX_PART==8*1024*1024,"Retained XML memory budgets unchanged");
        System.out.println("OfficeTextTest PASS: "+n+" assertions");
    }
}
