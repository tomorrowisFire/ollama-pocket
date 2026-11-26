package com.ollamapocket.app;

/** Width-based, window-aware pane policy; independent of Android and model/engine state. */
final class WorkspaceLayoutPolicy {
    static final String AUTO="auto", PHONE="phone", TABLET="tablet";
    static final int GAP_DP=12;
    static final int AUTO_MIN_DP=720;
    static final int TABLET_MIN_DP=600;
    static final int MIN_RAIL_DP=180;
    private WorkspaceLayoutPolicy(){}
    static String normalize(String value){
        if(PHONE.equals(value)||TABLET.equals(value))return value;
        return AUTO;
    }
    static int minContentDp(float textScale){
        return Float.isFinite(textScale)&&textScale>1.2f?480:400;
    }
    /** Return zero for compact layout. Input is actual usable content width, after root insets/padding. */
    static int railWidthDp(String mode,int usableWidthDp,float textScale){
        String chosen=normalize(mode);
        if(PHONE.equals(chosen)||usableWidthDp<(TABLET.equals(chosen)?TABLET_MIN_DP:AUTO_MIN_DP))return 0;
        int desired=usableWidthDp>=960?256:usableWidthDp>=720?216:MIN_RAIL_DP;
        int maximum=usableWidthDp-minContentDp(textScale)-GAP_DP;
        int width=Math.min(desired,maximum);
        return width>=MIN_RAIL_DP?width:0;
    }
}
