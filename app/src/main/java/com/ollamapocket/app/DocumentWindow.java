package com.ollamapocket.app;

/** Bounded windows over extracted UTF-16 text, with contiguous surrogate-safe boundaries. */
final class DocumentWindow {
    static final int STRIDE=5999,MAX_SEGMENTS=90000,MAX_OFFICE_SEGMENTS=256;
    private DocumentWindow(){}
    static boolean validSegment(int segment){return segment>=1&&segment<=MAX_SEGMENTS;}
    static int prefixLimit(int segment){
        if(!validSegment(segment))throw new IllegalArgumentException("Segment range");
        return segment*STRIDE+1; // one extra code unit to inspect the next boundary
    }
    static final class Slice {
        final String text;final int segment,start,end;final boolean partial;
        Slice(String text,int segment,int start,int end,boolean partial){
            this.text=text;this.segment=segment;this.start=start;this.end=end;this.partial=partial;
        }
    }
    static Slice select(String source,int segment,boolean omitted)throws AttachmentText.Failure{
        if(!validSegment(segment))throw new AttachmentText.Failure(AttachmentText.Problem.RANGE);
        if(source==null||source.isEmpty())throw new AttachmentText.Failure(AttachmentText.Problem.EMPTY);
        int nominal=(segment-1)*STRIDE;
        if(nominal>=source.length())throw new AttachmentText.Failure(AttachmentText.Problem.RANGE);
        int start=nominal,end=Math.min(source.length(),segment*STRIDE);
        if(start>0&&Character.isLowSurrogate(source.charAt(start))&&Character.isHighSurrogate(source.charAt(start-1)))start--;
        if(end<source.length()&&end>0&&Character.isLowSurrogate(source.charAt(end))&&Character.isHighSurrogate(source.charAt(end-1)))end--;
        if(end<=start)throw new AttachmentText.Failure(AttachmentText.Problem.EMPTY);
        String text=source.substring(start,end);
        if(!AttachmentText.hasContent(text))throw new AttachmentText.Failure(AttachmentText.Problem.EMPTY);
        return new Slice(text,segment,start,end,omitted||start>0||end<source.length());
    }
}
