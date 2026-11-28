package com.ollamapocket.app;

/** No Android, network, native execution or persisted data. */
public final class F10A97DraftAdmissionTest {
    private static int n;
    private static void ok(boolean pass,String label){n++;if(!pass)throw new AssertionError(label);}
    private static void rejects(Runnable action){
        try{action.run();throw new AssertionError("Invalid draft/starter accepted");}
        catch(IllegalArgumentException expected){n++;}
    }
    public static void main(String[] args){
        for(String invalid:new String[]{"", " \n\t", "\u3000\u00a0", "\u200d", "\ufeff", "x\u0000", "\u0000x", "x\u007f", "x\u0085", "x\ud800", "x\udc00"}){
            ok(!ChatWorkspacePolicy.canSend(invalid,16000),"invalid send admission");
            rejects(()->ChatWorkspacePolicy.prependStarter("old",invalid,16000));
        }
        for(String valid:new String[]{"한국어 中文 日本語", "👩‍💻", "a\u200cb", " \t\u00a0x\r\n", "x".repeat(16000)}){
            ok(ChatWorkspacePolicy.canSend(valid,16000),"valid prompt");
            ok(ChatWorkspacePolicy.canSend(valid,16000)==LocalChatTurns.validPrompt(valid),"UI and engine agreement");
        }
        for(String invalidDraft:new String[]{"old\u0000", "old\u0085", "old\ud800"})
            rejects(()->ChatWorkspacePolicy.prependStarter(invalidDraft,"task",16000));
        String draft="\u3000old\n[attached file]👩‍💻  ";
        String joined=ChatWorkspacePolicy.prependStarter(draft,"  task  ",16000);
        ok(joined.equals("task\n\n"+draft),"preserve reviewed draft verbatim");
        ok(ChatWorkspacePolicy.canSend(joined,joined.length()),"exact merged budget");
        rejects(()->ChatWorkspacePolicy.prependStarter(draft,"task",joined.length()-1));
        rejects(()->ChatWorkspacePolicy.prependStarter("x".repeat(16000),"task",20000));
        ok(!ChatWorkspacePolicy.canSend("x".repeat(16001),20000),"engine limit cannot be bypassed");
        ok(!ChatWorkspacePolicy.canSend("x",0),"invalid limit");
        ok(!ChatWorkspacePolicy.canSend(null,16000),"missing input");
        for(int cp=0;cp<=0x10ffff;cp++){
            if(cp>=0xd800&&cp<=0xdfff)continue;
            String sample="x"+new String(Character.toChars(cp));
            ok(ChatWorkspacePolicy.canSend(sample,16000)==LocalChatTurns.validPrompt(sample),"scalar admission mismatch");
        }
        System.out.println("PASS: "+n+" draft admission assertions");
    }
}
