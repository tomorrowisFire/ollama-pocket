package com.ollamapocket.app;

public final class ChatWorkspacePolicyTest {
    private static int n;
    private static void ok(boolean pass){if(!pass)throw new AssertionError("check "+n);n++;}
    private static void rejects(Runnable r){boolean failed=false;try{r.run();}catch(IllegalArgumentException e){failed=true;}ok(failed);}
    public static void main(String[] args){
        ok(ChatWorkspacePolicy.starterColumns(360,1f)==1);
        ok(ChatWorkspacePolicy.starterColumns(439,1f)==1);
        ok(ChatWorkspacePolicy.starterColumns(440,1f)==2);
        ok(ChatWorkspacePolicy.starterColumns(579,1.26f)==1);
        ok(ChatWorkspacePolicy.starterColumns(580,1.26f)==2);
        ok(ChatWorkspacePolicy.starterColumns(440,Float.NaN)==2);
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
