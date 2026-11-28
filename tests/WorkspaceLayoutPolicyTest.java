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
                    int rail=WorkspaceLayoutPolicy.railWidthDp(mode,width,scale);
                    ok(rail>=0&&rail<=320,"bounded sidebar");
                    if(rail>0){
                        ok(!"phone".equals(mode)&&WorkspaceLayoutPolicy.tabletPresentation(mode,width),"only tablet docks");
                        int minimum=Float.isFinite(scale)&&scale>1.2f?640:560;
                        ok(width-rail-WorkspaceLayoutPolicy.GAP_DP>=minimum,"main pane never squeezed");
                    }
                    int main=width-(rail==0?0:rail+WorkspaceLayoutPolicy.GAP_DP);
                    int column=WorkspaceLayoutPolicy.readingColumnDp(mode,width,main);
                    ok(column>=0&&column<=main,"reading column fits remaining pane");
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
        eq("phone",1600,1f,0);
        eq("auto",819,1f,0);
        eq("auto",820,1f,248);
        eq("tablet",800,1f,0);
        eq("tablet",931,1.26f,0);
        eq("tablet",932,1.26f,280);
        eq("tablet",1600,1f,320);
        ok(WorkspaceLayoutPolicy.readingColumnDp("auto",900,650)==650,"tablet reading uses full window classification");
        ok(WorkspaceLayoutPolicy.readingColumnDp("phone",1200,1000)==640,"forced phone measure");
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
        ok(WorkspaceLayoutPolicy.HEADER_TITLE_MIN_DP+WorkspaceLayoutPolicy.HEADER_MODEL_MIN_DP<96,"compact two-line branding");
        ok(WorkspaceLayoutPolicy.HEADER_MODEL_GAP_DP==2,"tight title/model separation");
        ok(WorkspaceLayoutPolicy.headerCloudLabelFits(336,110,30,48,18,34),"ordinary phone keeps Cloud text");
        ok(!WorkspaceLayoutPolicy.headerCloudLabelFits(270,140,30,48,18,34),"narrow/large-font header uses icon only");
        ok(WorkspaceLayoutPolicy.headerCloudLabelFits(284,110,26,48,18,34),"exact width fits");
        ok(!WorkspaceLayoutPolicy.headerCloudLabelFits(283,110,26,48,18,34),"preserve title width");
        ok(!WorkspaceLayoutPolicy.headerCloudLabelFits(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,48,18,34),"overflow safe");
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
