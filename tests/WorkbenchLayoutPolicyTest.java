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
        check(WorkbenchLayoutPolicy.cloudComposerStacked(419,1)&&!WorkbenchLayoutPolicy.cloudComposerStacked(420,1));
        check(WorkbenchLayoutPolicy.cloudComposerStacked(519,1.3f)&&!WorkbenchLayoutPolicy.cloudComposerStacked(520,1.3f));
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
        System.out.println("PASS: "+n+" responsive workbench assertions");
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
                check(width<=144||width==600||actual>=last);last=actual;
            }
        }
    }
}
