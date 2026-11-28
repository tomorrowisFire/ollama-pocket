package com.ollamapocket.app;

public final class WorkspaceLayoutPolicyTest {
    private static int n;
    private static void ok(boolean b,String label){if(!b)throw new AssertionError(label);n++;}
    private static void eq(String mode,int width,float font,int expected){
        int actual=WorkspaceLayoutPolicy.railWidthDp(mode,width,font);
        ok(actual==expected,mode+" width="+width+" font="+font+" expected="+expected+" actual="+actual);
    }
    public static void main(String[] args){
        for(String mode:new String[]{"auto","phone","tablet","unknown",null}){
            for(float scale:new float[]{1f,1.13f,1.26f,2f,Float.NaN}){
                for(int width=0;width<=1600;width+=8){
                    eq(mode,width,scale,0);
                    boolean tablet=WorkspaceLayoutPolicy.tabletPresentation(mode,width);
                    int drawer=WorkspaceLayoutPolicy.drawerWidthDp(mode,width);
                    int reading=WorkspaceLayoutPolicy.readingColumnDp(mode,width);
                    ok(drawer>=0&&drawer<=width&&drawer<=(tablet?360:300),"drawer safe width");
                    ok(reading>=0&&reading<=width&&reading<=(tablet?880:640),"reading safe width");
                    if(width>0)ok(drawer>0&&reading>0,"reachable content");
                    if("phone".equals(mode))ok(!tablet,"explicit phone");
                }
            }
        }
        ok(!WorkspaceLayoutPolicy.tabletPresentation("auto",719),"auto narrow");
        ok(WorkspaceLayoutPolicy.tabletPresentation("auto",720),"auto tablet");
        ok(!WorkspaceLayoutPolicy.tabletPresentation("tablet",599),"narrow tablet fallback");
        ok(WorkspaceLayoutPolicy.tabletPresentation("tablet",600),"explicit tablet");
        ok(WorkspaceLayoutPolicy.drawerWidthDp("phone",360)==300,"phone drawer");
        ok(WorkspaceLayoutPolicy.drawerWidthDp("tablet",800)==360,"tablet drawer");
        ok(WorkspaceLayoutPolicy.drawerWidthDp("tablet",180)==180,"small window");
        ok(WorkspaceLayoutPolicy.drawerWidthDp("tablet",-1)==0,"invalid window");
        ok(WorkspaceLayoutPolicy.readingColumnDp("phone",1200)==640,"phone reading width");
        ok(WorkspaceLayoutPolicy.readingColumnDp("tablet",1200)==880,"tablet reading width");
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
