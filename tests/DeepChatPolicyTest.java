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
        ok(access.finish(skipped)&&!access.busy());
        ok(!access.earned(skipped,start+3)&&!access.authorized(start+3,false));
        var first=access.begin(start+10);
        ok(access.show(first,start+11)&&access.earned(first,start+20));
        ok(!access.earned(first,start+50));
        ok(access.remaining(start+20,false)==DeepChatPolicy.PASS_MILLIS);
        ok(access.remaining(start+50,false)==DeepChatPolicy.PASS_MILLIS-30);
        ok(access.finish(first));
        ok(access.remaining(start+19,false)==0);
        ok(access.authorized(start+20+DeepChatPolicy.PASS_MILLIS-1,false));
        ok(!access.authorized(start+20+DeepChatPolicy.PASS_MILLIS,false));
        var renewal=access.begin(start+100);
        ok(access.show(renewal,start+101)&&access.earned(renewal,start+102));
        ok(!access.finish(first)&&access.busy());
        ok(access.remaining(start+102,false)==DeepChatPolicy.PASS_MILLIS);
        ok(access.finish(renewal));
        var expired=access.begin(2000);
        ok(!access.show(expired,2000+DeepChatPolicy.REWARD_FLOW_MILLIS));
        ok(!access.earned(expired,2000+DeepChatPolicy.REWARD_FLOW_MILLIS));
        ok(access.finish(expired)&&!access.busy());
        ok(access.begin(-1)==null);
        System.out.println("PASS: "+n+" deep-chat reward ownership and mode-limit assertions");
    }
}
