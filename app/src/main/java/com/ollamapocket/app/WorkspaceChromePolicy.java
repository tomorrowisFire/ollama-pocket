package com.ollamapocket.app;

/** Shared display-only navigation and viewport rules. No persistence or route mutation. */
final class WorkspaceChromePolicy {
    private WorkspaceChromePolicy(){}
    static boolean selectedDestination(int activeTab,int destination){
        return destination>=1&&destination<=3&&activeTab==destination;
    }
    static String selectionDescription(String label,boolean selected,String selectedLabel){
        if(label==null||selectedLabel==null)throw new IllegalArgumentException("Missing navigation label");
        return selected?label+" · "+selectedLabel:label;
    }
    /** Center the reading column, keeping symmetric gutters within the actual viewport. */
    static int centeredInsetPx(int viewport,int readingWidth,int gutter){
        int width=Math.max(0,viewport),reading=Math.min(width,Math.max(0,readingWidth));
        long desired=(width-reading)/2L+Math.max(0,gutter);
        return (int)Math.min(width/2L,desired);
    }
    /** Preserve the preferred 86% reading measure, but never force the bubble wider than its pane. */
    static int bubbleWidthPx(int viewport,int padding,int maximum,int preferredMinimum){
        long usable=Math.max(0L,(long)Math.max(0,viewport)-Math.max(0,padding));
        long limit=Math.min(usable,Math.max(0,maximum));
        long preferred=Math.max(Math.max(0,preferredMinimum),usable*86/100);
        return (int)Math.min(limit,preferred);
    }
}
