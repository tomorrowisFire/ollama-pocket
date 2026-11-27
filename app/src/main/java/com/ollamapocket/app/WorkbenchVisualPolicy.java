package com.ollamapocket.app;

/** Shared workspace design tokens. Opaque text/surface pairs, independent of Android. */
final class WorkbenchVisualPolicy {
    static final int MIN_TARGET_DP=48, RADIUS_DP=16, GAP_DP=10;
    enum Role { SECONDARY, PRIMARY, DESTRUCTIVE }
    enum ModelStatus { AVAILABLE, INSTALLED, ACTIVE, DOWNLOADING }
    static ModelStatus modelStatus(boolean installed,boolean selected,boolean downloading){
        return downloading?ModelStatus.DOWNLOADING:installed?(selected?ModelStatus.ACTIVE:ModelStatus.INSTALLED):ModelStatus.AVAILABLE;
    }
    static final class Palette {
        final int canvas,surface,field,bubble,accentSoft,text,muted,border,focus,primary,primaryText,danger,dangerText,disabled,disabledText;
        Palette(int canvas,int surface,int field,int bubble,int accentSoft,int text,int muted,int border,int focus,int primary,int primaryText,
                int danger,int dangerText,int disabled,int disabledText){
            this.canvas=canvas;this.surface=surface;this.field=field;this.bubble=bubble;this.accentSoft=accentSoft;
            this.text=text;this.muted=muted;this.border=border;this.focus=focus;
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
        int modelStatusFill(ModelStatus status){
            return status==ModelStatus.ACTIVE?primary:status==ModelStatus.DOWNLOADING?accentSoft:status==ModelStatus.INSTALLED?bubble:field;
        }
        int modelStatusInk(ModelStatus status){
            return status==ModelStatus.ACTIVE?primaryText:status==ModelStatus.AVAILABLE?muted:text;
        }
        // Dialog action slots describe platform placement, not semantic risk.
        int dialogFill(boolean enabled){return enabled?surface:disabled;}
        int dialogInk(boolean positive,boolean enabled){return !enabled?disabledText:positive?primary:muted;}
        int dialogRipple(){return (primary&0x00ffffff)|0x24000000;}
        int focusInk(Role role){return role==Role.PRIMARY?primaryText:role==Role.DESTRUCTIVE?dangerText:focus;}
    }
    // OnHand: warm ivory, restrained burnt orange, muted lavender and plum-charcoal.
    // Accent is deliberately darker than the artwork in light mode for text contrast.
    private static final Palette LIGHT=new Palette(0xfffaf6f1,0xfffffcfa,0xfff2edf6,0xfffce7dc,0xfff9e4d7,
        0xff272330,0xff655d6a,0xffded5de,0xff7a4a95,0xffb83b12,0xffffffff,
        0xfffceef0,0xff9f2437,0xffefe8e8,0xff70616b);
    private static final Palette DARK=new Palette(0xff221c24,0xff2d2730,0xff352d3e,0xff49362d,0xff4b3028,
        0xfff7f0f5,0xffc6bacb,0xff584a5d,0xffc7a7e2,0xffffad7b,0xff241a1b,
        0xff4b2933,0xffffb2be,0xff3f3742,0xffc4b7c8);
    private WorkbenchVisualPolicy(){}
    static Palette palette(boolean dark){return dark?DARK:LIGHT;}
    static float textScale(String preference){return "large".equals(preference)?1.13f:"extra".equals(preference)?1.26f:1f;}
    private static double linear(int c){double x=c/255d;return x<=0.04045?x/12.92:Math.pow((x+0.055)/1.055,2.4);}
    static double luminance(int color){return .2126*linear((color>>>16)&255)+.7152*linear((color>>>8)&255)+.0722*linear(color&255);}
    /** Framework primary text is light for a dark Material theme. Ignore the theme's alpha. */
    static boolean darkFromPrimaryText(int color){return luminance(color)>.5;}
    static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
}
