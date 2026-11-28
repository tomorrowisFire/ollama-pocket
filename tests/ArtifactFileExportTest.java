package com.ollamapocket.app;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public final class ArtifactFileExportTest {
    private static int n;
    private static void ok(boolean pass){n++;if(!pass)throw new AssertionError("check "+n);}
    private static void rejects(Runnable r){try{r.run();throw new AssertionError("Accepted invalid input");}catch(IllegalArgumentException expected){n++;}}
    private static void exact(ArtifactFileExport.Format f,String input)throws IOException{
        ArtifactFileExport export=ArtifactFileExport.prepare(f,input);
        ByteArrayOutputStream out=new ByteArrayOutputStream();export.writeTo(out);
        ok(out.toString(StandardCharsets.UTF_8).equals(input));
        ok(export.size()==input.getBytes(StandardCharsets.UTF_8).length);
        ok(export.filename(123).equals("OnHand-AI-"+f.stem+"-123."+f.extension));
        export.writeTo(new OutputStream(){public void write(int b){}public void write(byte[] b){java.util.Arrays.fill(b,(byte)0);}});
        out.reset();export.writeTo(out);ok(out.toString(StandardCharsets.UTF_8).equals(input));
    }
    public static void main(String[] args)throws IOException{
        for(ArtifactFileExport.Format format:ArtifactFileExport.Format.values()){
            for(String input:new String[]{"","a\r\nb\n","한국어 日本語 中文 😀","<tag>\" & 'x'","  \t\n"})exact(format,input);
        }
        ok(ArtifactFileExport.Format.REPORT_JSON.mime.equals("application/json"));
        ok(ArtifactFileExport.Format.REPORT_JSON.extension.equals("json"));
        ok(ArtifactFileExport.Format.REPORT_TEXT.mime.equals("text/plain"));
        ok(ArtifactFileExport.Format.PROJECT_TEXT.stem.equals("project-version"));
        exact(ArtifactFileExport.Format.REPORT_JSON,"{\"status\":\"STOPPED\",\"synthesis\":null}");
        exact(ArtifactFileExport.Format.PROJECT_TEXT,"historical version\n  한글 😀\n");
        exact(ArtifactFileExport.Format.REPORT_JSON,"한".repeat(120000));
        String safe="Name\tValue\tEmpty\n'=SUM(A1:A9)\t\"a,b\"\t\n한글😀\t-12.5\t001";
        ok(ArtifactFileExport.csvFromSafeTsv(safe).equals("\"Name\",\"Value\",\"Empty\"\r\n\"'=SUM(A1:A9)\",\"\"\"a,b\"\"\",\"\"\r\n\"한글😀\",\"-12.5\",\"001\"\r\n"));
        rejects(()->ArtifactFileExport.csvFromSafeTsv("a\tb\nc"));
        rejects(()->ArtifactFileExport.csvFromSafeTsv("a\tb\r"));
        rejects(()->ArtifactFileExport.csvFromSafeTsv("a\t\0"));
        rejects(()->ArtifactFileExport.csvFromSafeTsv("a\t".repeat(10)+"b"));
        rejects(()->ArtifactFileExport.csvFromSafeTsv("a\tb\n".repeat(49)+"a\tb"));
        rejects(()->ArtifactFileExport.csvFromSafeTsv("x".repeat(160001)));
        rejects(()->ArtifactFileExport.prepare(null,"x"));
        rejects(()->ArtifactFileExport.prepare(ArtifactFileExport.Format.CODE,null));
        rejects(()->ArtifactFileExport.prepare(ArtifactFileExport.Format.CODE,"a\0b"));
        rejects(()->ArtifactFileExport.prepare(ArtifactFileExport.Format.CODE,"\ud800"));
        rejects(()->ArtifactFileExport.prepare(ArtifactFileExport.Format.CODE,"\udc00"));
        rejects(()->ArtifactFileExport.prepare(ArtifactFileExport.Format.CODE,"x".repeat(320001)));
        rejects(()->ArtifactFileExport.prepare(ArtifactFileExport.Format.CODE,"한".repeat(213334)));
        exact(ArtifactFileExport.Format.CODE,"한".repeat(213333));
        exact(ArtifactFileExport.Format.CODE,"😀".repeat(160000));
        Random random=new Random(1128);
        String[] fragments={"a","한","😀","\"",",","\r\n","\t","\\","\u200d"};
        for(int i=0;i<1000;i++){
            StringBuilder text=new StringBuilder();for(int j=random.nextInt(100);j>0;j--)text.append(fragments[random.nextInt(fragments.length)]);
            exact(ArtifactFileExport.Format.values()[i%ArtifactFileExport.Format.values().length],text.toString());
        }
        ArtifactFileExport sample=ArtifactFileExport.prepare(ArtifactFileExport.Format.CODE,"x");
        rejects(()->sample.filename(-1));
        try{sample.writeTo(null);throw new AssertionError("Null output accepted");}catch(IOException expected){n++;}
        try{sample.writeTo(new OutputStream(){public void write(int b)throws IOException{throw new IOException("provider");}});throw new AssertionError("Write failure lost");}catch(IOException expected){n++;}
        System.out.println("PASS: "+n+" exact UTF-8 artifact export assertions");
    }
}
