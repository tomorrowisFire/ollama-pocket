package com.ollamapocket.app;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

public final class DocumentWindowTest {
    private static int n;
    private static void ok(boolean v,String why){if(!v)throw new AssertionError(why);n++;}
    private static AttachmentText.Result read(String name,String data,int seg)throws Exception{
        return AttachmentText.read(name,new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)),seg);
    }
    public static void main(String[] args)throws Exception{
        String source="a".repeat(5998)+"😀"+"b".repeat(6000)+"tail";
        DocumentWindow.Slice a=DocumentWindow.select(source,1,false),b=DocumentWindow.select(source,2,false),c=DocumentWindow.select(source,3,false);
        ok(a.end==b.start&&b.end==c.start,"contiguous surrogate-safe range");
        ok((a.text+b.text+c.text).equals(source),"no dropped Unicode");
        ok(a.text.length()<=6000&&b.text.length()<=6000&&c.text.length()<=6000,"excerpt cap");
        AttachmentText.Result r=read("report.txt",source,2);
        ok(r.segment==2&&r.rangeStart==b.start&&r.rangeEnd==b.end&&r.text.equals(b.text),"selected text");
        ok(r.sourceName().contains("segment 2")&&r.partial,"source label");
        try{read("report.txt","short",2);throw new AssertionError("range");}
        catch(AttachmentText.Failure e){ok(e.problem==AttachmentText.Problem.RANGE,"out of range");}
        try{read("report.txt","short",257);throw new AssertionError("segment bound");}
        catch(AttachmentText.Failure e){ok(e.problem==AttachmentText.Problem.RANGE,"segment cap");}
        String word="<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>"+
            "a".repeat(7000)+"</w:t></w:r></w:p><w:p><w:r><w:t>Later paragraph</w:t></w:r></w:p></w:body></w:document>";
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(out)){z.putNextEntry(new ZipEntry("word/document.xml"));z.write(word.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
        AttachmentText.Result doc=AttachmentText.read("report.docx",new ByteArrayInputStream(out.toByteArray()),2);
        ok(doc.office&&doc.segment==2&&doc.text.contains("[Paragraph 2] Later paragraph"),"later Word content");
        ok(DocumentWindow.prefixLimit(256)<=1536000,"bounded extraction prefix");
        System.out.println("DocumentWindowTest PASS: "+n+" assertions");
    }
}
