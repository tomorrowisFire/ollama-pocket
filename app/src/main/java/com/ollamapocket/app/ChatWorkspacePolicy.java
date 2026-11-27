package com.ollamapocket.app;

/** UI-only bounds; no inference or history mutation. */
final class ChatWorkspacePolicy {
    private ChatWorkspacePolicy(){}
    // Four compact quick actions: preserve reading space and grow for larger type.
    static final int STARTER_COUNT=4;
    static final int STARTER_GAP_DP=8;
    static int starterMinWidthDp(float scale){
        double safe=Float.isFinite(scale)?Math.max(1d,Math.min(3d,scale)):1d;
        return 48+(int)Math.ceil(110d*safe);
    }
    static int starterColumns(int widthDp,float scale){
        long usable=Math.max(0L,(long)widthDp-8); // message viewport padding
        int minimum=starterMinWidthDp(scale);
        return usable>=4L*minimum+3*STARTER_GAP_DP?4:usable>=2L*minimum+STARTER_GAP_DP?2:1;
    }
    static int starterMinHeightDp(){return 48;}
    static boolean compactHeight(int heightDp,boolean keyboard,float scale){
        return keyboard||(heightDp>0&&heightDp<(Float.isFinite(scale)&&scale>1.2f?680:560));
    }
    static boolean nearLatest(int contentHeight,int viewportHeight,int scrollY,int threshold){
        long remaining=Math.max(0L,(long)Math.max(0,contentHeight)-Math.max(0,viewportHeight)-Math.max(0,scrollY));
        return remaining<=Math.max(0,threshold);
    }
    /** Natural child widths in pixels, after font scaling. Never squeeze labels below their natural width in a row. */
    static boolean stackAnswerActions(int available,int gap,int... naturalWidths){
        if(naturalWidths==null||naturalWidths.length==0)return false;
        long needed=(long)Math.max(0,gap)*(naturalWidths.length-1);
        for(int width:naturalWidths)needed+=Math.max(0,width);
        return needed>Math.max(0,available);
    }
    static boolean showDraftBudget(int chars,int max){
        return max>0&&chars>=Math.max(1,max*3L/4);
    }
    static boolean canSend(String draft,int max){
        return draft!=null&&!draft.trim().isEmpty()&&max>0&&draft.length()<=max;
    }
    /** Preserve the entire existing draft including its attachment suffix. */
    static String prependStarter(String draft,String starter,int max){
        if(draft==null||starter==null||starter.trim().isEmpty()||max<1)throw new IllegalArgumentException("Invalid starter");
        String prefix=starter.trim();
        String next=prefix+(draft.isEmpty()?"":"\n\n"+draft);
        if(next.length()>max)throw new IllegalArgumentException("Draft limit");
        return next;
    }
}
