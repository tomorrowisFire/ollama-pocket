package com.ollamapocket.app;
public final class OcrPolicyTest {
    private static int n;
    private static void ok(boolean v,String why){if(!v)throw new AssertionError(why);n++;}
    public static void main(String[] args)throws Exception{
        ok(OcrPolicy.image("a.JPG")&&OcrPolicy.image("a.webp")&&!OcrPolicy.image("a.svg"),"formats");
        ok(OcrPolicy.sample(1920,1080)==1,"normal bitmap");
        ok(OcrPolicy.sample(8000,4000)==4,"downsampling");
        try{OcrPolicy.sample(100000,100000);throw new AssertionError("bomb");}
        catch(IllegalArgumentException e){n++;}
        ok(!OcrPolicy.validBitmap(2049,10)&&OcrPolicy.validBitmap(2048,2048),"pixel cap");
        ok(OcrPolicy.validPage(1)&&!OcrPolicy.validPage(0)&&!OcrPolicy.validPage(10001),"page cap");
        AttachmentText.Result r=OcrPolicy.result("a.png","한국어 中文 日本語",0,0);
        ok(r.ocr&&!r.pdf&&!r.partial&&r.text.contains("한국어 中文 日本語"),"image source");
        r=OcrPolicy.result("a.pdf","hello",3,8);
        ok(r.ocr&&r.ocrPage==3&&r.ocrPages==8&&r.partial&&r.text.startsWith("[Page 3"),"PDF source");
        r=OcrPolicy.result("a.png","x".repeat(7000),0,0);
        ok(r.text.length()<=AttachmentText.MAX_EXCERPT&&r.partial,"excerpt cap");
        try{OcrPolicy.result("a.png"," \n\u0001",0,0);throw new AssertionError("empty accepted");}
        catch(AttachmentText.Failure e){ok(e.problem==AttachmentText.Problem.OCR_NO_TEXT,"empty");}
        ok(AttachmentText.maxBytes("a.png")==OcrPolicy.MAX_BYTES,"file size");
        System.out.println("OcrPolicyTest PASS: "+n+" assertions");
    }
}
