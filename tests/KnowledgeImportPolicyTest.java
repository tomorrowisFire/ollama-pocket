package com.ollamapocket.app;
import java.util.*;
import java.io.*;
public final class KnowledgeImportPolicyTest {
    private static int n;
    private static void ok(boolean b){if(!b)throw new AssertionError("import "+n);n++;}
    interface Action{void run()throws Exception;}
    private static void reject(Action a)throws Exception{
        try{a.run();}catch(IllegalArgumentException|IOException e){n++;return;}throw new AssertionError("invalid accepted");
    }
    public static void main(String[] args)throws Exception{
        KnowledgeLibrary.State old=KnowledgeLibrary.add(KnowledgeLibrary.empty(),"old","Manual","한글");
        byte[] v1=KnowledgeLibrary.encode(old);
        ok(java.nio.ByteBuffer.wrap(v1).getInt(4)==1);
        ok(KnowledgeLibrary.decode(v1).source("old").origin==null);
        AttachmentText.Result txt=AttachmentText.Result.window("test.txt",DocumentWindow.select("😀한글",1,false),false);
        KnowledgeImportPolicy.Review reviewed=KnowledgeImportPolicy.review(old,"file",txt,txt.text);
        ok(reviewed.origin.first==1&&reviewed.origin.last==4&&!reviewed.origin.partial);
        reject(()->KnowledgeImportPolicy.review(old,"file",txt,"edited"));
        reject(()->KnowledgeImportPolicy.review(old,"file",null,"text"));
        KnowledgeLibrary.State mixed=reviewed.apply(old);
        byte[] v2=KnowledgeLibrary.encode(mixed);
        ok(java.nio.ByteBuffer.wrap(v2).getInt(4)==2);
        KnowledgeLibrary.State saved=KnowledgeLibrary.decode(v2);
        ok(Arrays.equals(v2,KnowledgeLibrary.encode(saved)));
        ok(saved.source("old").origin==null&&saved.source("file").origin.kind==KnowledgeLibrary.Kind.TEXT);
        ok(saved.source("file").text.equals(txt.text)&&saved.source("file").origin.name.equals("test.txt"));
        ok(KnowledgeLibrary.search(saved,"😀").get(0).end==2);
        ok(java.nio.ByteBuffer.wrap(KnowledgeLibrary.encode(KnowledgeLibrary.remove(saved,"file"))).getInt(4)==1);
        AttachmentText.Result office=AttachmentText.Result.window("a.xlsx",DocumentWindow.select("x".repeat(6000),1,false),true);
        ok(KnowledgeImportPolicy.review(saved,"office",office,office.text).origin.kind==KnowledgeLibrary.Kind.OFFICE);
        AttachmentText.Result pdf=new AttachmentText.Result("a.pdf","text",true,false,true,20,2,1,false,0,0,0,0,0,3);
        KnowledgeImportPolicy.Review pr=KnowledgeImportPolicy.review(saved,"pdf",pdf,pdf.text);
        ok(pr.origin.first==3&&pr.origin.last==4&&pr.origin.total==20&&pr.origin.readable==1);
        AttachmentText.Result img=OcrPolicy.result("a.png","recognized",0,0);
        ok(KnowledgeImportPolicy.review(saved,"image",img,img.text).origin.kind==KnowledgeLibrary.Kind.IMAGE_OCR);
        AttachmentText.Result scan=OcrPolicy.result("a.pdf","recognized",2,4);
        KnowledgeImportPolicy.Review sr=KnowledgeImportPolicy.review(saved,"scan",scan,scan.text);
        ok(sr.origin.kind==KnowledgeLibrary.Kind.PDF_OCR&&sr.origin.first==2&&sr.origin.partial);
        KnowledgeLibrary.State all=KnowledgeLibrary.decode(KnowledgeLibrary.encode(sr.apply(pr.apply(saved))));
        ok(all.source("pdf").origin.last==4&&all.source("scan").text.equals(scan.text));
        reject(()->new KnowledgeLibrary.Origin(KnowledgeLibrary.Kind.PDF_TEXT,"a.pdf",false,0,3,4,20,1));
        reject(()->new KnowledgeLibrary.Origin(KnowledgeLibrary.Kind.PDF_OCR,"a.pdf",true,0,3,3,2,0));
        reject(()->new KnowledgeLibrary.Origin(KnowledgeLibrary.Kind.IMAGE_OCR,"a.png",false,0,1,1,1,0));
        reject(()->new KnowledgeLibrary.Origin(KnowledgeLibrary.Kind.TEXT,"bad\nname",false,1,1,1,0,0));
        reject(()->KnowledgeLibrary.add(old,"bad","File","bad",reviewed.origin));
        reject(()->reviewed.apply(mixed));
        byte[] corrupt=v2.clone();corrupt[30]^=1;reject(()->KnowledgeLibrary.decode(corrupt));
        System.out.println("PASS: "+n+" knowledge extraction provenance, v1/v2 compatibility and exact review assertions");
    }
}
