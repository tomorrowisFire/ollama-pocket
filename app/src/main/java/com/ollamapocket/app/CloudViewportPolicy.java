package com.ollamapocket.app;

/** Pure UI-only cloud conversation scroll intent. Tickets invalidate obsolete posted moves. */
final class CloudViewportPolicy {
    private boolean following=true;
    private long revision;
    boolean following(){return following;}
    long ticket(){return revision;}
    void reset(){following=true;revision++;}
    void invalidate(){revision++;}
    void observed(int content,int viewport,int y,int threshold){
        if(viewport<=0)return;
        boolean latest=ChatWorkspacePolicy.nearLatest(content,viewport,y,threshold);
        // A genuine scroll, even while already reading older messages, invalidates stale restores.
        following=latest;revision++;
    }
    boolean accepts(long ticket){return ticket==revision;}
    boolean shouldFollow(long ticket){return accepts(ticket)&&following;}
    static String shortTitle(String text,int maxUnits){
        if(text==null||maxUnits<1)return "";
        String clean=text.replace('\n',' ').replace('\r',' ').replace('\t',' ').trim();
        if(clean.length()<=maxUnits)return clean;
        int end=maxUnits;
        if(Character.isHighSurrogate(clean.charAt(end-1))&&Character.isLowSurrogate(clean.charAt(end)))end--;
        return clean.substring(0,end)+"…";
    }
}
