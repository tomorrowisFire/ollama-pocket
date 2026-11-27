package com.ollamapocket.app;

public final class WorkspaceLayoutPolicyTest {
    private static int n;
    private static void ok(boolean b,String label){if(!b)throw new AssertionError(label);n++;}
    private static void eq(String mode,int width,float font,int expected){
        int actual=WorkspaceLayoutPolicy.railWidthDp(mode,width,font);
        ok(actual==expected,mode+" width="+width+" font="+font+" expected="+expected+" actual="+actual);
    }
    public static void main(String[] args){
        eq("auto",360,1,0);eq("auto",599,1,0);eq("auto",719,1,0);
        eq("auto",720,1,216);eq("auto",840,1,216);eq("auto",959,1,216);eq("auto",960,1,256);
        eq("tablet",599,1,0);eq("tablet",600,1,180);eq("tablet",719,1,180);eq("tablet",720,1,216);
        eq("phone",1200,1,0);eq("phone",600,1,0);
        eq("tablet",600,1.26f,0);eq("tablet",671,1.26f,0);eq("tablet",672,1.26f,180);
        eq("auto",720,1.26f,216);eq("auto",0,1,0);eq("tablet",-1,1,0);
        eq("unknown",840,1,216);eq(null,840,1,216);eq("auto",720,Float.NaN,216);
        for(String mode:new String[]{"auto","phone","tablet"}){
            for(float scale:new float[]{1f,1.13f,1.26f}){
                for(int width=240;width<=1600;width+=8){
                    int rail=WorkspaceLayoutPolicy.railWidthDp(mode,width,scale);
                    ok(rail==0||(rail>=180&&width-rail-WorkspaceLayoutPolicy.GAP_DP>=WorkspaceLayoutPolicy.minContentDp(scale)),"safe remaining width");
                    if("phone".equals(mode))ok(rail==0,"phone mode remains single-pane");
                }
            }
        }
        ok(WorkspaceLayoutPolicy.stackedComposer(320,1f),"compact full width composer");
        ok(WorkspaceLayoutPolicy.stackedComposer(419,1f),"stack below boundary");
        ok(!WorkspaceLayoutPolicy.stackedComposer(420,1f),"wide inline composer");
        ok(WorkspaceLayoutPolicy.stackedComposer(480,1.26f),"large text safe composer");
        ok(!WorkspaceLayoutPolicy.stackedComposer(520,1.26f),"large text wide composer");
        ok(!WorkspaceLayoutPolicy.directCloud(319,1f),"compact cloud menu fallback");
        ok(WorkspaceLayoutPolicy.directCloud(320,1f),"phone direct cloud");
        ok(!WorkspaceLayoutPolicy.directCloud(450,1.26f),"large text cloud menu fallback");
        ok(WorkspaceLayoutPolicy.directCloud(460,1.26f),"wide large text direct cloud");
        ok(WorkspaceLayoutPolicy.compactHeader(360,1f),"compact header");
        ok(!WorkspaceLayoutPolicy.compactHeader(380,1f),"wide header");
        ok(WorkspaceLayoutPolicy.compactHeader(480,1.26f),"large text header");
        ok(WorkspaceLayoutPolicy.readingColumnDp(1024)==880,"wide readable column");
        ok(WorkspaceLayoutPolicy.readingColumnDp(360)==360,"phone fills column");
        modelCardLayout();
        System.out.println("WorkspaceLayoutPolicyTest PASS: "+n+" assertions");
    }
    private static void modelCardLayout(){
        ok(WorkspaceLayoutPolicy.modelCardMinDp(1f)==160,"default card budget");
        ok(WorkspaceLayoutPolicy.modelCardMinDp(2f)==300,"large card budget");
        ok(WorkspaceLayoutPolicy.modelCardMinDp(Float.NaN)==160,"invalid scale");
        ok(WorkspaceLayoutPolicy.modelCardMinDp(Float.POSITIVE_INFINITY)==160,"infinite scale");
        ok(WorkspaceLayoutPolicy.modelCardMinDp(-1f)==160,"negative scale");
        ok(WorkspaceLayoutPolicy.modelCardMinDp(10f)==440,"bounded scale");
        for(int width=120;width<=1600;width+=4){
            int last=0;
            for(float scale:new float[]{1f,1.13f,1.26f,1.5f,2f,2.52f,3f}){
                int minimum=WorkspaceLayoutPolicy.modelCardMinDp(scale);
                ok(minimum>=last,"monotonic text budget");last=minimum;
                for(int gap:new int[]{8,10}){
                    int count=Math.max(1,(width+gap)/(minimum+gap));
                    int side=Math.max(1,(width-gap*(count-1))/count);
                    int height=WorkspaceLayoutPolicy.modelCardHeightPx(side,minimum);
                    ok(side*count+gap*(count-1)<=width,"no width overflow");
                    ok(height>=minimum&&height>=side,"safe height budget");
                    ok(count==1||side>=minimum,"multiple columns fit");
                    ok(side<minimum||height==side,"square when possible");
                }
            }
        }
    }
}
