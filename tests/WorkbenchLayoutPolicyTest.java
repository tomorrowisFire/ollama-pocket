package com.ollamapocket.app;

public final class WorkbenchLayoutPolicyTest {
    private static int n;
    private static void check(boolean ok){n++;if(!ok)throw new AssertionError("workbench "+n);}
    public static void main(String[] args){
        check(WorkbenchLayoutPolicy.dialogWidthDp(0,1)==0);
        check(WorkbenchLayoutPolicy.dialogWidthDp(-10,1)==0);
        check(WorkbenchLayoutPolicy.dialogWidthDp(240,1)==216);
        check(WorkbenchLayoutPolicy.dialogWidthDp(360,1)==336);
        check(WorkbenchLayoutPolicy.dialogWidthDp(600,1)==552);
        check(WorkbenchLayoutPolicy.dialogWidthDp(880,1)==832);
        check(WorkbenchLayoutPolicy.dialogWidthDp(1400,1)==880);
        check(WorkbenchLayoutPolicy.dialogWidthDp(1400,1.3f)==960);
        check(WorkbenchLayoutPolicy.dialogWidthDp(1400,Float.NaN)==880);
        check(WorkbenchLayoutPolicy.columns(559,1)==1&&WorkbenchLayoutPolicy.columns(560,1)==2);
        check(WorkbenchLayoutPolicy.columns(739,1.3f)==1&&WorkbenchLayoutPolicy.columns(740,1.3f)==2);
        check(WorkbenchLayoutPolicy.columns(559,Float.POSITIVE_INFINITY)==1);
        check(WorkbenchLayoutPolicy.rows(0,2)==0&&WorkbenchLayoutPolicy.rows(3,1)==3&&WorkbenchLayoutPolicy.rows(3,2)==2);
        check(WorkbenchLayoutPolicy.rows(Integer.MAX_VALUE,2)==1073741824);
        check(WorkbenchLayoutPolicy.cellWidth(561,10,2,0)==276&&WorkbenchLayoutPolicy.cellWidth(561,10,2,1)==275);
        check(WorkbenchLayoutPolicy.cellWidth(100,10,1,0)==100);
        check(WorkbenchLayoutPolicy.cellWidth(5,10,2,0)==0);
        try{WorkbenchLayoutPolicy.cellWidth(560,10,2,2);throw new AssertionError("Bad column accepted");}catch(IllegalArgumentException expected){check(true);}
        check(WorkbenchLayoutPolicy.cloudContentDp(360)==360&&WorkbenchLayoutPolicy.cloudContentDp(1440)==880);
        check(WorkbenchLayoutPolicy.cloudHeaderStacked(259,1)&&!WorkbenchLayoutPolicy.cloudHeaderStacked(260,1));
        check(WorkbenchLayoutPolicy.cloudHeaderStacked(359,1.3f)&&!WorkbenchLayoutPolicy.cloudHeaderStacked(360,1.3f));
        check(WorkbenchLayoutPolicy.cloudComposerStacked(231,1)&&!WorkbenchLayoutPolicy.cloudComposerStacked(232,1));
        check(WorkbenchLayoutPolicy.cloudComposerStacked(273,1.26f)&&!WorkbenchLayoutPolicy.cloudComposerStacked(274,1.26f));
        check(WorkbenchLayoutPolicy.compactCloudHeight(359,1)&&!WorkbenchLayoutPolicy.compactCloudHeight(360,1));
        check(WorkbenchLayoutPolicy.compactCloudHeight(439,1.3f)&&!WorkbenchLayoutPolicy.compactCloudHeight(440,1.3f));
        check(!WorkbenchLayoutPolicy.compactCloudHeight(0,1));
        for(float scale:new float[]{1f,1.2f,1.3f,2f,Float.NaN}){
            for(int w=240;w<=1600;w++){
                int dialog=WorkbenchLayoutPolicy.dialogWidthDp(w,scale);
                check(dialog>0&&dialog<=w&&dialog<=(WorkbenchLayoutPolicy.largeText(scale)?960:880));
                int c=WorkbenchLayoutPolicy.columns(dialog,scale);
                int cells=0;
                for(int i=0;i<c;i++)cells+=WorkbenchLayoutPolicy.cellWidth(dialog,10,c,i);
                check(cells+(c-1)*10==dialog);
            }
        }
        compactDialogChecks();
        continuousDialogAndTableChecks();
        focusedBrandLayoutChecks();
        System.out.println("PASS: "+n+" responsive workbench assertions");
    }
    private static void focusedBrandLayoutChecks(){
        check(WorkbenchLayoutPolicy.cloudComposerMinimumDp(1)==232);
        check(WorkbenchLayoutPolicy.cloudComposerMinimumDp(1.26f)==274);
        check(WorkbenchLayoutPolicy.cloudComposerStacked(231,1));
        check(!WorkbenchLayoutPolicy.cloudComposerStacked(232,1));
        check(WorkbenchLayoutPolicy.cloudComposerStacked(273,1.26f));
        check(!WorkbenchLayoutPolicy.cloudComposerStacked(274,1.26f));
        check(WorkbenchLayoutPolicy.cloudHeaderStacked(309,1,150,140));
        check(!WorkbenchLayoutPolicy.cloudHeaderStacked(310,1,150,140));
        check(WorkbenchLayoutPolicy.cloudHeaderStacked(1000,1,Integer.MAX_VALUE,100));
        for(float scale:new float[]{.5f,1f,1.13f,1.26f,2f,3f,4f,6f,10f,Float.NaN,Float.POSITIVE_INFINITY}){
            int min=WorkbenchLayoutPolicy.cloudComposerMinimumDp(scale);check(min>=232&&min<=1032);
            for(int w=0;w<=1800;w++){
                check(WorkbenchLayoutPolicy.cloudComposerStacked(w,scale)==(w<min));
                check(WorkbenchLayoutPolicy.cloudHeaderStacked(w,scale,150,140)==(WorkbenchLayoutPolicy.cloudHeaderStacked(w,scale)||w<310));
                if(!WorkbenchLayoutPolicy.cloudHeaderStacked(w,scale,150,140))check((w-10)/2>=150);
            }
        }
        for(int d=750;d<=5000;d++){
            float density=d/1000f;int target=Math.round(48*density),glyph=Math.round(22*density);
            int a=WorkbenchLayoutPolicy.iconInsetPx(target,glyph,false),z=WorkbenchLayoutPolicy.iconInsetPx(target,glyph,true);
            check(a>=0&&z>=0&&a+glyph+z==target);check(Math.abs(a-z)<=1);
        }
        check(WorkbenchLayoutPolicy.iconInsetPx(0,1,true)==0);
        check(WorkbenchLayoutPolicy.iconInsetPx(Integer.MAX_VALUE,Integer.MIN_VALUE,true)==1073741824);
    }
    private static void continuousDialogAndTableChecks(){
        for(float scale:new float[]{1f,1.13f,1.2f,1.26f,2f,3f,Float.NaN,Float.POSITIVE_INFINITY,-1f}){
            int prev=0,prevCompact=0;
            for(int w=0;w<=2000;w++){
                int full=WorkbenchLayoutPolicy.dialogWidthDp(w,scale),compact=WorkbenchLayoutPolicy.compactDialogWidthDp(w,scale);
                check(full>=prev&&full<=w);check(compact>=prevCompact&&compact<=full);
                check(full<=(WorkbenchLayoutPolicy.largeText(scale)?960:880));check(compact<=(WorkbenchLayoutPolicy.largeText(scale)?640:560));
                if(w>0)check(full>0&&compact>0);
                prev=full;prevCompact=compact;
            }
        }
        check(WorkbenchLayoutPolicy.dialogWidthDp(599,1)==551);check(WorkbenchLayoutPolicy.dialogWidthDp(600,1)==552);
        for(int w:new int[]{240,360,880})check(WorkbenchLayoutPolicy.dialogWidthDp(w,1)==w-(w>=600?48:24));
        for(int len=-2;len<=200;len++){
            int base=WorkbenchLayoutPolicy.tableColumnWidthDp(len,1),last=base;
            check(base>=112&&base<=240);check(base==WorkbenchLayoutPolicy.tableColumnWidthDp(len,Float.NaN));
            check(base==WorkbenchLayoutPolicy.tableColumnWidthDp(len,Float.POSITIVE_INFINITY));
            for(float scale:new float[]{1,1.13f,1.26f,1.5f,2,2.52f,3,4}){
                int width=WorkbenchLayoutPolicy.tableColumnWidthDp(len,scale);check(width>=last&&width<=684);last=width;
            }
            check(last==WorkbenchLayoutPolicy.tableColumnWidthDp(len,3));
        }
        check(WorkbenchLayoutPolicy.tableColumnWidthDp(0,2)==206);
        check(WorkbenchLayoutPolicy.tableColumnWidthDp(Integer.MAX_VALUE,3)==684);
        check(WorkbenchLayoutPolicy.tableColumnWidthDp(Integer.MIN_VALUE,1)==112);
    }
    private static void compactDialogChecks(){
        check(WorkbenchLayoutPolicy.compactDialogWidthDp(0,1)==0);
        check(WorkbenchLayoutPolicy.compactDialogWidthDp(-1,1)==0);
        check(WorkbenchLayoutPolicy.compactDialogWidthDp(360,1)==336);
        check(WorkbenchLayoutPolicy.compactDialogWidthDp(600,1)==552);
        check(WorkbenchLayoutPolicy.compactDialogWidthDp(1400,1)==560);
        check(WorkbenchLayoutPolicy.compactDialogWidthDp(1400,1.3f)==640);
        for(float scale:new float[]{1,1.13f,1.2f,1.26f,2,Float.NaN,Float.POSITIVE_INFINITY}){
            int last=0;
            for(int width=1;width<=1600;width++){
                int actual=WorkbenchLayoutPolicy.compactDialogWidthDp(width,scale);
                check(actual>0&&actual<=width&&actual<=WorkbenchLayoutPolicy.dialogWidthDp(width,scale));
                check(actual<=(WorkbenchLayoutPolicy.largeText(scale)?640:560));
                check(actual>=last);last=actual;
            }
        }
    }
}
