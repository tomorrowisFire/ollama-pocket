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
    static boolean homeActionsVisible(boolean localChat,boolean readable,int historyCount,boolean pending,boolean busy,boolean compact){
        return localChat&&readable&&historyCount==0&&!pending&&!busy&&!compact;
    }
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
    /** Reject Unicode whitespace-only drafts while preserving mixed text and its exact budget. */
    static boolean hasDraftText(String draft){
        if(draft==null)return false;
        for(int i=0;i<draft.length();i++){
            char c=draft.charAt(i);
            if(c>0x20&&c!=0x85&&!Character.isWhitespace(c)&&!Character.isSpaceChar(c))return true;
        }
        return false;
    }
    static boolean canSend(String draft,int max){
        return draft!=null&&max>0&&draft.length()<=max&&hasDraftText(draft);
    }
    /** Preserve the entire existing draft including its attachment suffix. */
    static String prependStarter(String draft,String starter,int max){
        if(draft==null||!hasDraftText(starter)||max<1)throw new IllegalArgumentException("Invalid starter");
        String prefix=starter.trim();
        String next=prefix+(draft.isEmpty()?"":"\n\n"+draft);
        if(next.length()>max)throw new IllegalArgumentException("Draft limit");
        return next;
    }
}
