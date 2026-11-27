package com.ollamapocket.app;

public final class AutomationDraftFormsTest {
    private static final String MODEL="qwen2.5:0.5b",SHA="a".repeat(64);
    private static int checks;
    interface Action{void run()throws Exception;}
    static void ok(boolean value){checks++;if(!value)throw new AssertionError("Draft check "+checks);}
    static void reject(Action a)throws Exception{
        checks++;try{a.run();}catch(IllegalArgumentException|org.json.JSONException expected){return;}
        throw new AssertionError("Invalid draft accepted");
    }
    public static void main(String[] args)throws Exception{
        String encoded=AutomationDraftForms.agent("Agent",MODEL,SHA,"한글\n日本語", "700","256");
        AgentProfile profile=AgentProfile.decode(encoded);
        ok(profile.instruction.equals("한글\n日本語")&&profile.temperatureMilli==700&&profile.maxTokens==256);
        reject(()->AutomationDraftForms.agent("Agent",MODEL,SHA,"task","2001","256"));
        reject(()->AutomationDraftForms.agent("Agent",MODEL,SHA,"task","0.5","256"));
        reject(()->AutomationDraftForms.agent("Agent",MODEL,"bad","task","700","256"));
        String old=BranchWorkflowRun.chainedExample(MODEL,SHA);
        var draft=new AutomationDraftForms.BranchDraft(old,MODEL);
        ok(draft.encode().equals(old));
        ok(draft.firstInvalidRow()==-1);
        ok(draft.targets(0).equals(java.util.List.of("!complete","!blocked","yes","no")));
        ok(draft.targets(2).equals(java.util.List.of("!complete","!blocked")));
        draft.rows.get(1).id="no";ok(!draft.targets(0).contains("no")&&draft.firstInvalidRow()==2);
        draft.rows.get(1).id="yes";
        draft.rows.get(1).prompt="Return verified with an explanation.";
        String changed=draft.encode();
        ok(!changed.equals(old));
        var parsed=BranchWorkflowRun.parse(changed);
        ok(parsed.retainOutputs&&parsed.tasks.get("yes").contains&&parsed.tasks.get("yes").usePrevious);
        draft.retain=false;reject(draft::encode);draft.retain=true;
        draft.rows.get(0).previous=true;reject(draft::encode);draft.rows.get(0).previous=false;
        draft.rows.get(0).next="missing";ok(draft.firstInvalidRow()==0);reject(draft::encode);draft.rows.get(0).next="yes";
        draft.rows.get(0).other="yes";reject(draft::encode);draft.rows.get(0).other="no";
        draft.remove(2);reject(draft::encode);
        var blank=new AutomationDraftForms.BranchDraft("",MODEL);
        reject(blank::encode);
        blank.digest=SHA;blank.rows.get(0).prompt="Return ok.";blank.rows.get(0).expect="ok";
        ok(!BranchWorkflowRun.parse(blank.encode()).retainOutputs);
        blank.insertAfter(0);ok(blank.firstInvalidRow()==1);reject(blank::encode);
        blank.rows.get(0).next=blank.rows.get(1).id;
        blank.rows.get(1).prompt="Return done.";blank.rows.get(1).expect="done";
        ok(BranchWorkflowRun.parse(blank.encode()).graph.nodes().size()==2);
        ok(blank.firstInvalidRow()==-1);
        blank.rows.get(1).next=blank.rows.get(0).id;reject(blank::encode);
        for(int i=2;i<10;i++)blank.insertAfter(i-1);
        reject(()->blank.insertAfter(9));
        reject(()->new AutomationDraftForms.BranchDraft("{bad",MODEL));
        System.out.println("PASS: "+checks+" visual draft validation, retention and exact identity checks");
    }
}
