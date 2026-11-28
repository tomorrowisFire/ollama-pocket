package com.ollamapocket.app;

import java.util.Locale;

/** Bounded page-labelled text excerpt; independent of Android's PDF parser. */
final class PdfExcerpt {
    static final int MAX_BYTES=64*1024*1024, MAX_PAGES=12;
    private PdfExcerpt(){}
    static boolean supports(String name){
        return name!=null&&name.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }
    static final class Builder {
        private final int pages,first;
        private final StringBuilder out=new StringBuilder();
        private int examined,withText;
        private boolean partial,started,hasText;
        Builder(int pages){this(pages,1);}
        Builder(int pages,int first){
            if(pages<=0||!OcrPolicy.validPage(first)||first>pages)throw new IllegalArgumentException("Invalid page range");
            this.pages=pages;this.first=first;partial=first>1||pages>MAX_PAGES;
        }
        boolean canReadPage(){
            return examined<Math.min(pages-first+1,MAX_PAGES)&&out.length()<AttachmentText.MAX_EXCERPT;
        }
        void startPage(int index){
            if(started||!canReadPage()||index!=first+examined)throw new IllegalStateException("Unexpected page");
            started=true;hasText=false;examined++;
        }
        void append(String text){
            if(!started)throw new IllegalStateException("No page");
            if(text==null||text.isEmpty())return;
            // Treat control-only/whitespace-only blocks as no extractable text.
            boolean useful=false;
            for(int i=0;i<text.length();i++){
                char c=text.charAt(i);
                if(!Character.isWhitespace(c)&&!Character.isSpaceChar(c)&&!Character.isISOControl(c)&&c!='\uFFFD'){useful=true;break;}
            }
            if(!useful)return;
            if(!hasText){hasText=true;withText++;add("\n[Page "+(first+examined-1)+"]\n");}
            add(text);add("\n");
        }
        private void add(String text){
            int room=AttachmentText.MAX_EXCERPT-out.length();
            if(text.length()>room)partial=true;
            int end=Math.max(0,Math.min(room,text.length()));
            if(end>0&&Character.isHighSurrogate(text.charAt(end-1)))end--;
            for(int i=0;i<end;i++){
                char c=text.charAt(i);
                out.append(Character.isISOControl(c)&&c!='\t'&&c!='\n'&&c!='\r'?' ':c);
            }
        }
        void endPage(){
            if(!started)throw new IllegalStateException("No page");
            if(!hasText)partial=true;
            started=false;
        }
        boolean full(){return out.length()>=AttachmentText.MAX_EXCERPT;}
        void markPartial(){partial=true;}
        int total(){return pages;}
        int examined(){return examined;}
        int withText(){return withText;}
        boolean partial(){return partial||examined<pages;}
        String text(){return out.toString().trim();}
        AttachmentText.Result result(String name)throws AttachmentText.Failure{
            if(started)throw new IllegalStateException("Page still open");
            if(withText==0||text().isEmpty())throw new AttachmentText.Failure(AttachmentText.Problem.PDF_NO_TEXT);
            return new AttachmentText.Result(AttachmentText.safeName(name),text(),partial(),false,true,pages,examined,withText,false,0,0,0,0,0,first);
        }
    }
}
