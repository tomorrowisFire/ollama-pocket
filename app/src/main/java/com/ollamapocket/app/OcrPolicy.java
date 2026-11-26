package com.ollamapocket.app;
import java.util.Locale;

/** Pure input and excerpt limits; never interprets recognized text as commands. */
final class OcrPolicy {
    static final int MAX_BYTES=8*1024*1024,MAX_SIDE=2048,MAX_PAGE=10000;
    static final long MAX_SOURCE_PIXELS=100_000_000L,MAX_PIXELS=4_194_304L;
    enum Script { LATIN,KOREAN,CHINESE,JAPANESE }
    private OcrPolicy(){}
    static boolean image(String name){
        return name!=null&&name.toLowerCase(Locale.ROOT).matches("(?s).+\\.(png|jpe?g|webp)");
    }
    static int sample(int width,int height){
        if(width<=0||height<=0||(long)width*height>MAX_SOURCE_PIXELS)
            throw new IllegalArgumentException("Invalid image dimensions");
        int sample=1;
        while((width+sample-1L)/sample>MAX_SIDE||(height+sample-1L)/sample>MAX_SIDE||
            ((width+sample-1L)/sample)*((height+sample-1L)/sample)>MAX_PIXELS)sample*=2;
        return sample;
    }
    static boolean validBitmap(int w,int h){return w>0&&h>0&&w<=MAX_SIDE&&h<=MAX_SIDE&&(long)w*h<=MAX_PIXELS;}
    static boolean validPage(int p){return p>=1&&p<=MAX_PAGE;}
    static AttachmentText.Result result(String name,String raw,int page,int total)throws AttachmentText.Failure{
        if(raw==null)raw="";
        StringBuilder clean=new StringBuilder();boolean partial=false;
        String prefix=page>0?"[Page "+page+" — OCR]\n":"[Image — OCR]\n";
        clean.append(prefix);int before=clean.length();
        for(int i=0;i<raw.length();){
            int c=raw.codePointAt(i);i+=Character.charCount(c);
            if(Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t')c=' ';
            if(clean.length()+Character.charCount(c)>AttachmentText.MAX_EXCERPT){partial=true;break;}
            clean.appendCodePoint(c);
        }
        if(clean.substring(before).trim().isEmpty())throw new AttachmentText.Failure(AttachmentText.Problem.OCR_NO_TEXT);
        return new AttachmentText.Result(AttachmentText.safeName(name),clean.toString(),partial||page>0&&total>1,
            false,false,0,0,0,true,page,total);
    }
}
