package com.ollamapocket.app;

/** Pure responsive workbench sizing; no persisted layout or app state. */
final class WorkbenchLayoutPolicy {
    static final int GRID_GAP_DP=10;
    private WorkbenchLayoutPolicy(){}
    static boolean largeText(float scale){return Float.isFinite(scale)&&scale>1.2f;}
    /** Safe owner window width after horizontal system/cutout insets, in dp. */
    static int dialogWidthDp(int usable,float scale){
        if(usable<=0)return 0;
        // Interpolate the 12dp to 24dp side gutters without a width cliff.
        int width=usable<=144?(int)((5L*usable+5)/6):usable<=552?usable-24:
            usable<600?528+(usable-552)/2:usable-48;
        return Math.min(largeText(scale)?960:880,width);
    }
    /** Short selections/confirmations keep a narrower reading measure than full workbenches. */
    static int compactDialogWidthDp(int usable,float scale){
        return Math.min(largeText(scale)?640:560,dialogWidthDp(usable,scale));
    }
    /** Horizontal table scroll absorbs growth; budget for app and system text scales. */
    static int tableColumnWidthDp(int length,float scale){
        int base=Math.min(240,Math.max(112,72+Math.min(21,Math.max(0,length))*8));
        double safe=Float.isFinite(scale)?Math.max(1d,Math.min(3d,scale)):1d;
        return 18+(int)Math.ceil((base-18)*safe);
    }
    static int columns(int innerWidth,float scale){
        return innerWidth>=(largeText(scale)?740:560)?2:1;
    }
    static int rows(int count,int columns){
        if(count<=0)return 0;
        int c=Math.max(1,Math.min(2,columns));
        return (int)(((long)count+c-1)/c);
    }
    static int cellWidth(int total,int gap,int columns,int index){
        int c=Math.max(1,Math.min(2,columns));
        if(index<0||index>=c)throw new IllegalArgumentException("Column index");
        int available=(int)Math.max(0L,(long)Math.max(0,total)-(long)Math.max(0,gap)*(c-1));
        return available/c+(index<available%c?1:0);
    }
    static int cloudContentDp(int width){return Math.max(0,Math.min(880,width));}
    static boolean cloudHeaderStacked(int width,float scale){return width<(largeText(scale)?360:260);}
    /** Equal-width header controls must fit their actual localized glyph and text measurements. */
    static boolean cloudHeaderStacked(int width,float scale,int backNatural,int settingsNatural){
        long needed=2L*Math.max(Math.max(0,backNatural),Math.max(0,settingsNatural))+GRID_GAP_DP;
        return cloudHeaderStacked(width,scale)||needed>width;
    }
    /** Cloud send/stop are mutually exclusive, so budget only one 48dp action. */
    static int cloudComposerMinimumDp(float scale){
        double safe=Float.isFinite(scale)?Math.max(1d,Math.min(6d,scale)):1d;
        return 16+8+48+(int)Math.ceil(160d*safe);
    }
    static boolean cloudComposerStacked(int width,float scale){return width<cloudComposerMinimumDp(scale);}
    /** Balanced integer insets avoid one-pixel icon clipping at fractional densities. */
    static int iconInsetPx(int targetPx,int iconPx,boolean trailing){
        int remaining=Math.max(0,Math.max(0,targetPx)-Math.max(0,iconPx));
        return remaining/2+(trailing?remaining%2:0);
    }
    static boolean compactCloudHeight(int height,float scale){return height>0&&height<(largeText(scale)?440:360);}
}
