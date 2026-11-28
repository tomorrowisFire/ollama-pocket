package com.ollamapocket.app;
public final class DeepChatPolicyTest {
    private static int n;
    private static void ok(boolean value){if(!value)throw new AssertionError("check "+n);n++;}
    public static void main(String[] args){
        var access=new DeepChatPolicy.Access();
        long start=1000;
        ok(!access.authorized(start,false)&&access.authorized(start,true));
        ok(access.remaining(start,true)==Long.MAX_VALUE);
        ok(DeepChatPolicy.normalLimit(false)==1048576&&DeepChatPolicy.normalLimit(true)==8388608);
        ok(DeepChatPolicy.normalSegment(256)&&!DeepChatPolicy.normalSegment(257));
        ok(!DeepChatPolicy.mayStart(true,false)&&DeepChatPolicy.mayStart(false,false));
        var skipped=access.begin(start);
        ok(skipped!=null&&access.busy()&&access.begin(start)==null);
        ok(!access.earned(skipped,start)); // load alone never rewards
        ok(access.show(skipped,start+1));
        ok(!access.show(skipped,start+2));
        ok(access.finish(skipped,start+2)&&!access.busy());
        ok(access.suppressAppOpen(start+2)&&!access.suppressAppOpen(start+120002));
        ok(!access.earned(skipped,start+3)&&!access.authorized(start+3,false));
        var first=access.begin(start+10);
        ok(access.show(first,start+11)&&access.earned(first,start+20));
        ok(!access.earned(first,start+50));
        ok(access.remaining(start+20,false)==DeepChatPolicy.PASS_MILLIS);
        ok(access.remaining(start+50,false)==DeepChatPolicy.PASS_MILLIS-30);
        ok(!access.earned(first,start+9));
        ok(access.finish(first,start+50));
        ok(access.suppressAppOpen(start+50)&&access.suppressAppOpen(start+120049)&&!access.suppressAppOpen(start+120050));
        ok(access.remaining(start+19,false)==0);
        ok(access.authorized(start+20+DeepChatPolicy.PASS_MILLIS-1,false));
        ok(!access.authorized(start+20+DeepChatPolicy.PASS_MILLIS,false));
        var renewal=access.begin(start+100);
        ok(access.show(renewal,start+101)&&!access.earned(renewal,start+100));
        ok(access.earned(renewal,start+102));
        ok(!access.finish(first,start+103)&&access.busy());
        ok(access.remaining(start+102,false)==DeepChatPolicy.PASS_MILLIS);
        ok(access.finish(renewal,start+104));
        ok(access.suppressAppOpen(start+104));
        var expired=access.begin(2000);
        ok(!access.show(expired,2000+DeepChatPolicy.REWARD_FLOW_MILLIS));
        ok(!access.earned(expired,2000+DeepChatPolicy.REWARD_FLOW_MILLIS));
        ok(access.expired(expired,2000+DeepChatPolicy.REWARD_FLOW_MILLIS));
        ok(access.finish(expired,2000+DeepChatPolicy.REWARD_FLOW_MILLIS)&&!access.busy());
        var shown=access.begin(3_000_000L);
        ok(access.show(shown,3_000_001L));
        ok(!access.expired(shown,3_000_001L));
        ok(access.suppressAppOpen(3_000_001L));
        ok(access.expired(shown,3_000_000L+DeepChatPolicy.REWARD_FLOW_MILLIS));
        ok(access.finish(shown,3_000_000L+DeepChatPolicy.REWARD_FLOW_MILLIS));
        ok(!access.expired(shown,Long.MAX_VALUE));
        ok(access.begin(-1)==null);
        var workspace=new DeepChatPolicy.WorkspaceReward();
        ok(!workspace.preservePause(true,0));
        ok(workspace.begin(100)&&workspace.blocked()&&!workspace.begin(101));
        ok(!workspace.preservePause(false,101));
        ok(workspace.preservePause(true,102)&&workspace.blocked());
        ok(!workspace.preservePause(true,103));
        workspace.finish();ok(workspace.blocked());
        ok(workspace.resume(104)&&!workspace.blocked());
        ok(workspace.begin(200)&&workspace.preservePause(true,201));
        ok(workspace.resume(202)&&workspace.blocked()); // resume may precede SDK completion
        ok(!workspace.preservePause(true,203));
        workspace.finish();ok(!workspace.blocked());
        ok(workspace.begin(500)&&!workspace.preservePause(true,499));
        ok(workspace.preservePause(true,501));
        ok(workspace.expired(500+DeepChatPolicy.REWARD_FLOW_MILLIS));
        ok(!workspace.resume(500+DeepChatPolicy.REWARD_FLOW_MILLIS));
        var unavailable=new DeepChatPolicy.WorkspaceReward();
        ok(unavailable.begin(0));unavailable.finish();ok(!unavailable.blocked());
        System.out.println("PASS: "+n+" deep-chat reward ownership, workspace lifecycle and mode-limit assertions");
    }
}
