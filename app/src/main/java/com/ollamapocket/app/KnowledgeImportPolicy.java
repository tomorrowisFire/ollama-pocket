package com.ollamapocket.app;

/** Only exact unedited extracted snapshots, never URI or original bytes. */
final class KnowledgeImportPolicy {
    private KnowledgeImportPolicy(){}
    static final class Review {
        final String id,title,text;final KnowledgeLibrary.Origin origin;
        private Review(KnowledgeLibrary.Source s){id=s.id;title=s.title;text=s.text;origin=s.origin;}
        KnowledgeLibrary.State apply(KnowledgeLibrary.State state){return KnowledgeLibrary.add(state,id,title,text,origin);}
    }
    static Review review(KnowledgeLibrary.State state,String id,AttachmentText.Result result,String reviewed){
        if(result==null||result.text==null||!result.text.equals(reviewed)||!AttachmentText.safeName(result.name).equals(result.name))
            throw new IllegalArgumentException("Not exact extraction");
        KnowledgeLibrary.Kind kind;int segment=0,first=0,last=0,total=0,readable=0;
        if(result.ocr){
            if(result.pdf||result.office||result.segment!=0)throw new IllegalArgumentException("Ambiguous extraction");
            if(result.ocrPage>0){kind=KnowledgeLibrary.Kind.PDF_OCR;first=last=result.ocrPage;total=result.ocrPages;}
            else {if(result.ocrPage!=0||result.ocrPages!=0)throw new IllegalArgumentException("OCR range");kind=KnowledgeLibrary.Kind.IMAGE_OCR;}
        }else if(result.pdf){
            if(result.office||result.segment!=0||result.pdfExamined<1)throw new IllegalArgumentException("Ambiguous extraction");
            kind=KnowledgeLibrary.Kind.PDF_TEXT;first=result.pdfFirst;last=Math.addExact(first,result.pdfExamined-1);
            total=result.pdfPages;readable=result.pdfTextPages;
        }else{
            if(result.rangeStart<0)throw new IllegalArgumentException("Range");
            kind=result.office?KnowledgeLibrary.Kind.OFFICE:KnowledgeLibrary.Kind.TEXT;
            segment=result.segment;first=Math.addExact(result.rangeStart,1);last=result.rangeEnd;
        }
        KnowledgeLibrary.Origin origin=new KnowledgeLibrary.Origin(kind,result.name,result.partial,segment,first,last,total,readable);
        int end=Math.min(80,result.name.length());if(end<result.name.length()&&Character.isHighSurrogate(result.name.charAt(end-1)))end--;
        return new Review(KnowledgeLibrary.add(state,id,result.name.substring(0,end),reviewed,origin).source(id));
    }
}
