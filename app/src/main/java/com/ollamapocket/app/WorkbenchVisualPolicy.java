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
        final int canvas,surface,field,bubble,accentSoft,text,muted,border,focus,primary,primaryFill,primaryText,danger,dangerText,disabled,disabledText;
        Palette(int canvas,int surface,int field,int bubble,int accentSoft,int text,int muted,int border,int focus,int primary,int primaryFill,int primaryText,
                int danger,int dangerText,int disabled,int disabledText){
            this.canvas=canvas;this.surface=surface;this.field=field;this.bubble=bubble;this.accentSoft=accentSoft;
            this.text=text;this.muted=muted;this.border=border;this.focus=focus;
            this.primary=primary;this.primaryFill=primaryFill;this.primaryText=primaryText;this.danger=danger;this.dangerText=dangerText;
            this.disabled=disabled;this.disabledText=disabledText;
        }
        int fill(Role role,boolean enabled){
            if(!enabled)return disabled;
            return role==Role.PRIMARY?primaryFill:role==Role.DESTRUCTIVE?danger:field;
        }
        int ink(Role role,boolean enabled){
            if(!enabled)return disabledText;
            return role==Role.PRIMARY?primaryText:role==Role.DESTRUCTIVE?dangerText:text;
        }
        int modelStatusFill(ModelStatus status){
            return status==ModelStatus.ACTIVE?primaryFill:status==ModelStatus.DOWNLOADING?accentSoft:status==ModelStatus.INSTALLED?bubble:field;
        }
        int modelStatusInk(ModelStatus status){
            return status==ModelStatus.ACTIVE?primaryText:status==ModelStatus.AVAILABLE?muted:text;
        }
        // Use the readable accent as a boundary for the softer filled control.
        int primaryOutline(){return primary;}
        // Secondary composer actions blend into the single input surface.
        int composerActionFill(boolean primaryAction,boolean enabled){
            return primaryAction?(enabled?primaryFill:disabled):surface;
        }
        int composerActionInk(boolean primaryAction,boolean enabled){
            return !enabled?disabledText:primaryAction?primaryText:text;
        }
        int composerActionFocus(boolean primaryAction){return primaryAction?primaryText:focus;}
        // Dialog action slots describe platform placement, not semantic risk.
        int dialogFill(boolean enabled){return enabled?surface:disabled;}
        int dialogInk(boolean positive,boolean enabled){return !enabled?disabledText:positive?primary:muted;}
        int dialogRipple(){return (primary&0x00ffffff)|0x24000000;}
        int focusInk(Role role){return role==Role.PRIMARY?primaryText:role==Role.DESTRUCTIVE?dangerText:focus;}
    }
    // OnHand Soft Peach: quiet warm neutrals, peach fills, and readable companion ink.
    // Primary foreground and primary fill are distinct roles; never use peach as light-mode text.
    private static final Palette LIGHT=new Palette(0xfff9f8f5,0xfffffefc,0xfff0eeea,0xfff8ebe4,0xfffbede4,
        0xff252522,0xff625e57,0xffd9d5cf,0xff79503e,0xff79503e,0xffe5bba6,0xff30221d,
        0xfffceef0,0xff9f2437,0xffebe8e3,0xff655e58);
    private static final Palette DARK=new Palette(0xff191a19,0xff242523,0xff30312e,0xff3f3029,0xff48352c,
        0xfff3f2ed,0xffc1beb6,0xff4c4d48,0xffddbcaa,0xffe7c1ad,0xffd4a58e,0xff30221d,
        0xff4b2933,0xffffb2be,0xff373835,0xffc4bfb5);
    private WorkbenchVisualPolicy(){}
    static Palette palette(boolean dark){return dark?DARK:LIGHT;}
    static float textScale(String preference){return "large".equals(preference)?1.13f:"extra".equals(preference)?1.26f:1f;}
    private static double linear(int c){double x=c/255d;return x<=0.04045?x/12.92:Math.pow((x+0.055)/1.055,2.4);}
    static double luminance(int color){return .2126*linear((color>>>16)&255)+.7152*linear((color>>>8)&255)+.0722*linear(color&255);}
    /** Framework primary text is light for a dark Material theme. Ignore the theme's alpha. */
    static boolean darkFromPrimaryText(int color){return luminance(color)>.5;}
    static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
}
