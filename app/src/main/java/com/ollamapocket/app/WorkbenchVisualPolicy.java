package com.ollamapocket.app;

/** Shared workspace design tokens. Opaque text/surface pairs, independent of Android. */
final class WorkbenchVisualPolicy {
    static final int MIN_TARGET_DP=48, RADIUS_DP=12, GAP_DP=8;
    enum Role { SECONDARY, PRIMARY, DESTRUCTIVE }
    static final class Palette {
        final int surface,field,text,muted,border,focus,primary,primaryText,danger,dangerText,disabled,disabledText;
        Palette(int surface,int field,int text,int muted,int border,int focus,int primary,int primaryText,
                int danger,int dangerText,int disabled,int disabledText){
            this.surface=surface;this.field=field;this.text=text;this.muted=muted;this.border=border;this.focus=focus;
            this.primary=primary;this.primaryText=primaryText;this.danger=danger;this.dangerText=dangerText;
            this.disabled=disabled;this.disabledText=disabledText;
        }
        int fill(Role role,boolean enabled){
            if(!enabled)return disabled;
            return role==Role.PRIMARY?primary:role==Role.DESTRUCTIVE?danger:field;
        }
        int ink(Role role,boolean enabled){
            if(!enabled)return disabledText;
            return role==Role.PRIMARY?primaryText:role==Role.DESTRUCTIVE?dangerText:text;
        }
        int focusInk(Role role){return role==Role.PRIMARY?primaryText:role==Role.DESTRUCTIVE?dangerText:focus;}
    }
    private static final Palette LIGHT=new Palette(0xffffffff,0xfff3f4f6,0xff25272c,0xff656970,0xffd7d9de,0xff62666f,
        0xff222429,0xffffffff,0xfffceef0,0xff9f2437,0xffe9eaed,0xff646870);
    private static final Palette DARK=new Palette(0xff1b1c20,0xff282a2f,0xffeeeff2,0xffb2b5bd,0xff45474e,0xffadb0ba,
        0xffedeef0,0xff16171a,0xff42282e,0xffffb2be,0xff34363b,0xffacafb7);
    private WorkbenchVisualPolicy(){}
    static Palette palette(boolean dark){return dark?DARK:LIGHT;}
    static float textScale(String preference){return "large".equals(preference)?1.13f:"extra".equals(preference)?1.26f:1f;}
    private static double linear(int c){double x=c/255d;return x<=0.04045?x/12.92:Math.pow((x+0.055)/1.055,2.4);}
    static double luminance(int color){return .2126*linear((color>>>16)&255)+.7152*linear((color>>>8)&255)+.0722*linear(color&255);}
    /** Framework primary text is light for a dark Material theme. Ignore the theme's alpha. */
    static boolean darkFromPrimaryText(int color){return luminance(color)>.5;}
    static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
}
