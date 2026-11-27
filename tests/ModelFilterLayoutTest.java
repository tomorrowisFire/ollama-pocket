package com.ollamapocket.app;
public final class ModelFilterLayoutTest {
    static int n;
    static void ok(boolean b){if(!b)throw new AssertionError("model filters "+n);n++;}
    public static void main(String[] args){
        ok(WorkspaceLayoutPolicy.stackedModelFilters(355,1f));
        ok(!WorkspaceLayoutPolicy.stackedModelFilters(356,1f));
        ok(WorkspaceLayoutPolicy.stackedModelFilters(425,1.26f));
        ok(!WorkspaceLayoutPolicy.stackedModelFilters(426,1.26f));
        ok(WorkspaceLayoutPolicy.stackedModelFilters(0,1f));
        ok(WorkspaceLayoutPolicy.stackedModelFilters(355,Float.NaN));
        ok(!WorkspaceLayoutPolicy.stackedModelFilters(356,0.5f));
        for(float scale:new float[]{1f,1.13f,1.26f,1.5f,2f,2.52f}){
            int minimum=(int)(2*(40+Math.ceil(132d*scale))+WorkspaceLayoutPolicy.GAP_DP);
            for(int w=240;w<=1200;w++){
                boolean stacked=WorkspaceLayoutPolicy.stackedModelFilters(w,scale);
                ok(stacked==(w<minimum));
                if(!stacked)ok((w-WorkspaceLayoutPolicy.GAP_DP)/2>=40+Math.ceil(132d*scale));
            }
        }
        System.out.println("PASS: "+n+" model filter width/scale assertions");
    }
}
