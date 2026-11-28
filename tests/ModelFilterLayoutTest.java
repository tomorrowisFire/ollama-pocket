package com.ollamapocket.app;
public final class ModelFilterLayoutTest {
    static int n;
    static void ok(boolean b){if(!b)throw new AssertionError("model filters "+n);n++;}
    public static void main(String[] args){
        ok(WorkspaceLayoutPolicy.FILTER_GAP_DP==4);
        ok(WorkspaceLayoutPolicy.filterChipWidthPx(0,48,24)==48);
        ok(WorkspaceLayoutPolicy.filterChipWidthPx(66.2f,48,24)==91);
        ok(WorkspaceLayoutPolicy.filterChipWidthPx(Float.NaN,48,24)==48);
        ok(WorkspaceLayoutPolicy.filterChipWidthPx(Float.POSITIVE_INFINITY,48,24)==48);
        ok(WorkspaceLayoutPolicy.filterChipWidthPx(-1,-1,-1)==0);
        ok(WorkspaceLayoutPolicy.filterChipWidthPx(Float.MAX_VALUE,48,Integer.MAX_VALUE)==Integer.MAX_VALUE);
        ok(WorkspaceLayoutPolicy.filterChipWidthPx(1000,48,24,240)==240);
        ok(WorkspaceLayoutPolicy.filterChipWidthPx(20,48,24,240)==48);
        ok(WorkspaceLayoutPolicy.filterChipWidthPx(1000,48,24,180)==180);
        ok(WorkspaceLayoutPolicy.filterChipWidthPx(1000,48,24,0)==48);
        ok(WorkspaceLayoutPolicy.filterChipWidthPx(Float.MAX_VALUE,48,Integer.MAX_VALUE,240)==240);
        for(int density=75;density<=500;density++){
            int minimum=Math.round(48*density/100f),chrome=Math.round(24*density/100f);
            int previous=0;
            for(int text=0;text<=200;text++){
                float measured=text*density/100f;
                int actual=WorkspaceLayoutPolicy.filterChipWidthPx(measured,minimum,chrome);
                ok(actual>=minimum&&actual>=Math.ceil(measured)+chrome&&actual>=previous);
                previous=actual;
            }
        }
        for(int[] measured:new int[][]{{60,34,44,55},{70,74,54,100},{120,130,98,180},{240,200,190,320}}){
            int total=3*WorkspaceLayoutPolicy.FILTER_GAP_DP;
            int[] widths=new int[4];
            for(int i=0;i<4;i++){widths[i]=WorkspaceLayoutPolicy.filterChipWidthPx(measured[i],48,24);total+=widths[i];}
            for(int viewport=180;viewport<=1600;viewport++){
                int extent=Math.max(total,viewport),end=0;
                for(int i=0;i<4;i++){
                    end+=widths[i];ok(end<=extent&&Math.max(0,end-viewport)<=extent-viewport);
                    if(i<3)end+=WorkspaceLayoutPolicy.FILTER_GAP_DP;
                }
                ok(end==total);
            }
        }
        System.out.println("PASS: "+n+" single-line model filter sizing assertions");
    }
}
