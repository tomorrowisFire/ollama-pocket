package com.ollamapocket.app;

public final class ChatWorkspacePolicyTest {
    private static int n;
    private static void ok(boolean pass){if(!pass)throw new AssertionError("check "+n);n++;}
    private static void rejects(Runnable r){boolean failed=false;try{r.run();}catch(IllegalArgumentException e){failed=true;}ok(failed);}
    public static void main(String[] args){
        ok(ChatWorkspacePolicy.STARTER_COUNT==4);
        ok(ChatWorkspacePolicy.STARTER_GAP_DP==8);
        ok(ChatWorkspacePolicy.starterMinHeightDp()==48);
        ok(ChatWorkspacePolicy.starterColumns(331,1)==1);
        ok(ChatWorkspacePolicy.starterColumns(332,1)==2);
        ok(ChatWorkspacePolicy.starterColumns(360,1)==2);
        ok(ChatWorkspacePolicy.starterColumns(663,1)==2);
        ok(ChatWorkspacePolicy.starterColumns(664,1)==4);
        ok(ChatWorkspacePolicy.starterColumns(360,1.26f)==1);
        ok(ChatWorkspacePolicy.starterColumns(390,1.26f)==2);
        ok(ChatWorkspacePolicy.starterColumns(Integer.MIN_VALUE,1)==1);
        ok(ChatWorkspacePolicy.starterColumns(Integer.MAX_VALUE,3)==4);
        for(float scale:new float[]{.5f,1,1.13f,1.26f,1.5f,2,3,4,Float.NaN,Float.POSITIVE_INFINITY}){
            int previous=1;
            for(int width=0;width<=1800;width++){
                int cols=ChatWorkspacePolicy.starterColumns(width,scale);
                int min=ChatWorkspacePolicy.starterMinWidthDp(scale);
                ok(cols==1||cols==2||cols==4);
                ok(ChatWorkspacePolicy.STARTER_COUNT%cols==0);
                ok(cols>=previous);
                ok(cols==1||(long)cols*min+(cols-1)*ChatWorkspacePolicy.STARTER_GAP_DP<=width-8L);
                previous=cols;
            }
        }
        for(int mask=0;mask<32;mask++)for(int turns:new int[]{-1,0,1,100,Integer.MAX_VALUE}){
            boolean local=(mask&1)!=0,readable=(mask&2)!=0,pending=(mask&4)!=0,busy=(mask&8)!=0,compact=(mask&16)!=0;
            ok(ChatWorkspacePolicy.homeActionsVisible(local,readable,turns,pending,busy,compact)==(mask==3&&turns==0));
        }
        ok(!ChatWorkspacePolicy.compactHeight(0,false,1f));
        ok(ChatWorkspacePolicy.compactHeight(900,true,1f));
        ok(ChatWorkspacePolicy.compactHeight(559,false,1f));
        ok(!ChatWorkspacePolicy.compactHeight(560,false,1f));
        ok(ChatWorkspacePolicy.compactHeight(679,false,1.26f));
        ok(!ChatWorkspacePolicy.compactHeight(680,false,1.26f));
        ok(ChatWorkspacePolicy.nearLatest(100,150,0,0));
        ok(ChatWorkspacePolicy.nearLatest(1000,400,504,96));
        ok(!ChatWorkspacePolicy.nearLatest(1000,400,503,96));
        ok(ChatWorkspacePolicy.nearLatest(1000,400,600,0));
        ok(ChatWorkspacePolicy.nearLatest(0,0,0,0));
        ok(!ChatWorkspacePolicy.nearLatest(Integer.MAX_VALUE,0,0,96));
        ok(!ChatWorkspacePolicy.stackAnswerActions(320,6,96,110,48));
        ok(!ChatWorkspacePolicy.stackAnswerActions(266,6,96,110,48));
        ok(ChatWorkspacePolicy.stackAnswerActions(265,6,96,110,48));
        ok(ChatWorkspacePolicy.stackAnswerActions(220,6,140,130,48));
        ok(ChatWorkspacePolicy.stackAnswerActions(300,6,160,160));
        ok(ChatWorkspacePolicy.stackAnswerActions(100,6,140));
        ok(!ChatWorkspacePolicy.stackAnswerActions(140,6,140));
        ok(ChatWorkspacePolicy.stackAnswerActions(139,6,140));
        ok(ChatWorkspacePolicy.stackAnswerActions(0,6,1));
        ok(!ChatWorkspacePolicy.stackAnswerActions(0,6,0));
        ok(!ChatWorkspacePolicy.stackAnswerActions(0,6));
        ok(ChatWorkspacePolicy.stackAnswerActions(Integer.MAX_VALUE,6,Integer.MAX_VALUE,48));
        ok(!ChatWorkspacePolicy.showDraftBudget(11999,16000));
        ok(ChatWorkspacePolicy.showDraftBudget(12000,16000));
        ok(ChatWorkspacePolicy.showDraftBudget(16001,16000));
        ok(!ChatWorkspacePolicy.showDraftBudget(10,0));
        ok(!ChatWorkspacePolicy.canSend(" \n",16000));
        ok(!ChatWorkspacePolicy.canSend(null,16000));
        ok(ChatWorkspacePolicy.canSend("한글😀",4));
        ok(!ChatWorkspacePolicy.canSend("a".repeat(16001),16000));
        ok(!ChatWorkspacePolicy.canSend("    a",4));
        ok(ChatWorkspacePolicy.prependStarter("","  task  ",20).equals("task"));
        String draft="my old draft\n[attached file]";
        String edited=ChatWorkspacePolicy.prependStarter(draft,"task",100);
        ok(edited.equals("task\n\n"+draft));
        ok(edited.endsWith(draft));
        ok(ChatWorkspacePolicy.prependStarter("  ","task",8).equals("task\n\n  "));
        rejects(()->ChatWorkspacePolicy.prependStarter("old","task",8));
        rejects(()->ChatWorkspacePolicy.prependStarter("old"," ",100));
        rejects(()->ChatWorkspacePolicy.prependStarter(null,"task",100));
        System.out.println("PASS: "+n+" chat workspace and safe draft assertions");
    }
}
