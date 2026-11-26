package com.ollamapocket.app;

public final class PdfExcerptTest {
    private static int n;
    private static void ok(boolean v,String why){if(!v)throw new AssertionError(why);n++;}
    public static void main(String[] args)throws Exception{
        PdfExcerpt.Builder b=new PdfExcerpt.Builder(3);
        b.startPage(1);b.append("한국어 中文 日本語");b.endPage();
        b.startPage(2);b.append(" \n\u0001");b.endPage();
        b.startPage(3);b.append("Third");b.endPage();
        AttachmentText.Result r=b.result("Sample.PDF");
        ok(r.pdf&&!r.office&&r.pdfPages==3&&r.pdfExamined==3&&r.pdfTextPages==2,"metadata");
        ok(r.text.contains("[Page 1]\n한국어 中文 日本語")&&r.text.contains("[Page 3]\nThird"),"page references");
        ok(r.partial&&!r.text.contains("[Page 2]"),"no invented text");
        PdfExcerpt.Builder longDoc=new PdfExcerpt.Builder(100);
        for(int i=1;i<=12;i++){ok(longDoc.canReadPage(),"page "+i);longDoc.startPage(i);longDoc.append("Test");longDoc.endPage();}
        ok(!longDoc.canReadPage()&&longDoc.result("long.pdf").partial,"page cap");
        PdfExcerpt.Builder large=new PdfExcerpt.Builder(1);
        large.startPage(1);large.append("x".repeat(7000));large.endPage();
        ok(large.result("x.pdf").text.length()<=AttachmentText.MAX_EXCERPT&&large.partial(),"excerpt cap");
        PdfExcerpt.Builder empty=new PdfExcerpt.Builder(1);
        empty.startPage(1);empty.append("\uFFFD \t");empty.endPage();
        try{empty.result("scan.pdf");throw new AssertionError("invented text");}
        catch(AttachmentText.Failure e){ok(e.problem==AttachmentText.Problem.PDF_NO_TEXT,"no text distinct from corruption");}
        ok(PdfExcerpt.supports("a.PDF")&&!PdfExcerpt.supports("a.png"),"extension");
        ok(AttachmentText.maxBytes("a.pdf")==PdfExcerpt.MAX_BYTES,"file cap");
        PdfExcerpt.Builder control=new PdfExcerpt.Builder(1);
        control.startPage(1);control.append("a\u0001b");control.endPage();
        ok(control.result("a.pdf").text.endsWith("a b"),"control sanitization");
        System.out.println("PdfExcerptTest PASS: "+n+" assertions");
    }
}
