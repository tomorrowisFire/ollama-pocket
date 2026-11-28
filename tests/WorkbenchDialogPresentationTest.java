package com.ollamapocket.app;

public final class WorkbenchDialogPresentationTest {
    private static int checks;
    private static void ok(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
    public static void main(String[] args){
        ok(WorkbenchDialogPresentation.combinedScale(1f,1.26f)==1.26f);
        ok(WorkbenchDialogPresentation.combinedScale(1.13f,1.13f)>1.2f);
        ok(WorkbenchDialogPresentation.combinedScale(Float.NaN,1.26f)==1.26f);
        ok(WorkbenchDialogPresentation.combinedScale(2f,Float.POSITIVE_INFINITY)==2f);
        ok(WorkbenchDialogPresentation.combinedScale(-1f,0f)==1f);
        ok(WorkbenchDialogPresentation.combinedScale(Float.MAX_VALUE,Float.MAX_VALUE)==6f);
        float[] scales={0f,.75f,1f,1.13f,1.26f,2f,3f,6f,Float.MAX_VALUE,Float.NaN,Float.POSITIVE_INFINITY};
        for(float system:scales)for(float app:scales){
            float combined=WorkbenchDialogPresentation.combinedScale(system,app);
            ok(Float.isFinite(combined)&&combined>=1&&combined<=6);
            ok(combined==WorkbenchDialogPresentation.combinedScale(app,system));
        }
        ok(WorkbenchDialogPresentation.usableWidth(1000,1100,20,40)==1000);
        ok(WorkbenchDialogPresentation.usableWidth(1100,1000,20,40)==940);
        ok(WorkbenchDialogPresentation.usableWidth(0,1000,20,40)==940);
        ok(WorkbenchDialogPresentation.usableWidth(1000,0,20,40)==1000);
        ok(WorkbenchDialogPresentation.usableWidth(1000,1000,Integer.MAX_VALUE,Integer.MAX_VALUE)==0);
        ok(WorkbenchDialogPresentation.usableWidth(-1,Integer.MAX_VALUE,-1,-1)==Integer.MAX_VALUE);
        for(int w=0;w<=1800;w+=6)for(int inset:new int[]{0,12,48,1000,Integer.MAX_VALUE}){
            int usable=WorkbenchDialogPresentation.usableWidth(w,w+30,inset,inset);
            ok(usable>=0&&usable<=w+30);
            if(w>0)ok(usable<=w);
        }
        for(String title:new String[]{"Model","한국어","日本語","简体中文","繁體中文","👩‍💻"}){
            for(String detail:new String[]{"Q4_K_M · 2 GB","설치됨","下載中","明細\n2 GB"}){
                String label=title+"\n"+detail;
                int boundary=WorkbenchDialogPresentation.detailStart(label);
                ok(boundary==title.length()+1);
                ok(label.substring(0,boundary-1).equals(title)&&label.substring(boundary).equals(detail));
            }
        }
        for(String label:new String[]{null,"","Model","\nDetail","Title\n","\u3000\nDetail","Title\n\u200d\u00a0","\u200d\nDetail"})
            ok(WorkbenchDialogPresentation.detailStart(label)==-1);
        System.out.println("PASS: "+checks+" dialog presentation assertions");
    }
}
