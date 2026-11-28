package com.ollamapocket.app;

/** Width-based, window-aware pane policy; independent of Android and model/engine state. */
final class WorkspaceLayoutPolicy {
    static final String AUTO="auto", PHONE="phone", TABLET="tablet";
    static final int GAP_DP=12;
    static final int AUTO_MIN_DP=720;
    static final int TABLET_MIN_DP=600;
    static final int MIN_RAIL_DP=180;
    static final int FILTER_GAP_DP=4;
    // Compact, independently clickable title/model lines; icon controls retain 48dp targets.
    static final int HEADER_TITLE_MIN_DP=36, HEADER_TITLE_TOP_DP=12;
    static final int HEADER_MODEL_MIN_DP=32, HEADER_MODEL_GAP_DP=2;
    static boolean headerCloudLabelFits(int width,int title,int label,int target,int margins,int chrome){
        long icon=Math.max(0,target);
        long needed=2*icon+Math.max(0,margins)+(long)Math.max(0,title)+Math.max(icon,(long)Math.max(0,label)+Math.max(0,chrome));
        return width>=needed;
    }
    private WorkspaceLayoutPolicy(){}
    static String normalize(String value){
        if(PHONE.equals(value)||TABLET.equals(value))return value;
        return AUTO;
    }
    static int minContentDp(float textScale){
        return Float.isFinite(textScale)&&textScale>1.2f?480:400;
    }
    /** Actual chat content width after rail, insets and centered-column padding. */
    static boolean stackedComposer(int contentDp,float textScale){
        return contentDp<(Float.isFinite(textScale)&&textScale>1.2f?520:420);
    }
    static boolean directCloud(int contentDp,float textScale){
        return contentDp>=(Float.isFinite(textScale)&&textScale>1.2f?460:320);
    }
    static boolean compactHeader(int widthDp,float textScale){
        return widthDp<(Float.isFinite(textScale)&&textScale>1.2f?520:380);
    }
    /** Keep two translated model filters wide enough for text and the trailing spinner glyph. */
    static boolean stackedModelFilters(int contentDp,float textScale){
        double scale=Float.isFinite(textScale)?Math.max(1f,textScale):1d;
        return contentDp<2d*(40d+Math.ceil(132d*scale))+GAP_DP;
    }
    /** Model cards reserve fixed padding plus a scalable text budget. */
    static int modelCardMinDp(float scale){
        double safe=Float.isFinite(scale)?Math.max(1d,Math.min(3d,scale)):1d;
        return 20+(int)Math.ceil(140d*safe);
    }
    static int modelCardHeightPx(int sidePx,int minimumPx){return Math.max(sidePx,minimumPx);}
    static int readingColumnDp(int usableWidthDp){return Math.max(0,Math.min(880,usableWidthDp));}
    /** Dock the existing sidebar only if the window can retain a comfortable main pane. */
    static int railWidthDp(String mode,int usableWidthDp,float textScale){
        if(!tabletPresentation(mode,usableWidthDp))return 0;
        int content=Float.isFinite(textScale)&&textScale>1.2f?640:560;
        int minimum=Float.isFinite(textScale)&&textScale>1.2f?280:248;
        int available=usableWidthDp-content-GAP_DP;
        if(available<minimum)return 0;
        return Math.min(320,Math.min(available,Math.max(minimum,usableWidthDp/4)));
    }
    static boolean tabletPresentation(String mode,int usableWidthDp){
        String chosen=normalize(mode);
        return !PHONE.equals(chosen)&&usableWidthDp>=(TABLET.equals(chosen)?TABLET_MIN_DP:AUTO_MIN_DP);
    }
    static int readingColumnDp(String mode,int usableWidthDp){
        return readingColumnDp(mode,usableWidthDp,usableWidthDp);
    }
    static int readingColumnDp(String mode,int windowWidthDp,int contentWidthDp){
        return Math.max(0,Math.min(tabletPresentation(mode,windowWidthDp)?880:640,contentWidthDp));
    }
    /** Actual text width plus padding/arrow, with bounded integer arithmetic. */
    static int filterChipWidthPx(float textWidthPx,int minimumPx,int chromePx){
        double text=Float.isFinite(textWidthPx)?Math.max(0d,textWidthPx):0d;
        return (int)Math.min(Integer.MAX_VALUE,Math.max(Math.max(0,minimumPx),Math.ceil(text)+Math.max(0,chromePx)));
    }
    /** Single-line filter strip: each control fits the available viewport and exposes full text in its menu. */
    static int filterChipWidthPx(float textWidthPx,int minimumPx,int chromePx,int maxPx){
        int minimum=Math.max(0,minimumPx);
        return Math.max(minimum,Math.min(Math.max(minimum,maxPx),filterChipWidthPx(textWidthPx,minimum,chromePx)));
    }
    /** Clamp the drawer to the actual window, including narrow multi-window. */
    static int drawerWidthDp(String mode,int usableWidthDp){
        if(usableWidthDp<=0)return 0;
        int cap=tabletPresentation(mode,usableWidthDp)?360:300;
        return Math.min(cap,Math.max(Math.min(usableWidthDp,228),usableWidthDp-56));
    }
}
