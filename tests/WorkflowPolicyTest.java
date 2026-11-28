package com.ollamapocket.app;
public final class WorkflowPolicyTest {
    private static int n;
    private static void ok(boolean pass){if(!pass)throw new AssertionError("check "+n);n++;}
    private static void rejects(String value){try{WorkflowPolicy.parse(value);throw new AssertionError("invalid accepted");}catch(IllegalArgumentException expected){n++;}}
    public static void main(String[] args){
        java.util.List<WorkflowPolicy.Step> p=WorkflowPolicy.parse("Draft | READY\nReview | OK");
        ok(p.size()==2);ok(p.get(0).prompt.equals("Draft"));ok(WorkflowPolicy.verified(p.get(0),"READY now"));
        ok(!WorkflowPolicy.verified(p.get(0),"ready"));ok(!WorkflowPolicy.verified(p.get(1),""));
        ok(!WorkflowPolicy.verified(p.get(0),"READY"+"x".repeat(4000)));
        rejects("");rejects("no gate");rejects("prompt | ");rejects(" | gate");rejects("a|b\n".repeat(11));
        ok(WorkflowPolicy.parse("a|b\n".repeat(10)).size()==10);
        ok(WorkflowPolicy.canAttempt(0,100,100));ok(WorkflowPolicy.canAttempt(2,100,100));
        ok(!WorkflowPolicy.canAttempt(3,100,100));ok(!WorkflowPolicy.canAttempt(0,99,100));
        ok(!WorkflowPolicy.canAttempt(0,100+WorkflowPolicy.MAX_MILLIS,100));
        ok(WorkflowPolicy.prompt(p.get(1),"READY now",true).contains("READY now"));
        ok(WorkflowPolicy.prompt(p.get(1),"",true).contains("did not complete this verified step"));
        WorkflowPolicy.Step exact=WorkflowPolicy.parse("p | @exact:OK").get(0);
        ok(WorkflowPolicy.verified(exact," OK "));ok(!WorkflowPolicy.verified(exact,"OK not exact"));
        WorkflowPolicy.Step chars=WorkflowPolicy.parse("p | @minchars:3").get(0);
        ok(WorkflowPolicy.verified(chars,"가나다"));ok(!WorkflowPolicy.verified(chars,"ab"));
        WorkflowPolicy.Step json=WorkflowPolicy.parse("p | @jsonkeys:a,b").get(0);
        ok(WorkflowPolicy.verified(json,"{\"a\":1,\"b\":false}"));
        ok(!WorkflowPolicy.verified(json,"{\"a\":1}"));ok(!WorkflowPolicy.verified(json,"{\"a\":1,\"b\":null}"));
        ok(!WorkflowPolicy.verified(json,"{\"a\":1,\"b\":2} trailing"));
        rejects("p | @minchars:0");rejects("p | @minchars:9999");rejects("p | @jsonkeys:a,a");rejects("p | @unknown:x");
        ok(WorkflowPolicy.verified(WorkflowPolicy.parse("p | @exact:OK",1).get(0),"@exact:OK"));
        WorkflowPolicy.Step values=WorkflowPolicy.parse("p | @jsonvalues:{\"status\":\"OK\",\"count\":3,\"ready\":true}").get(0);
        ok(WorkflowPolicy.verified(values," {\"ready\":true,\"count\":3,\"status\":\"OK\"} "));
        for(String bad:new String[]{
            "{\"status\":\"NO\",\"count\":3,\"ready\":true}",
            "{\"status\":\"OK\",\"count\":3,\"ready\":false}",
            "{\"status\":\"OK\",\"count\":\"3\",\"ready\":true}",
            "{\"status\":\"OK\",\"count\":3.0,\"ready\":true}",
            "{\"status\":\"OK\",\"count\":3}",
            "{\"status\":\"OK\",\"count\":3,\"ready\":true,\"extra\":0}",
            "{\"status\":\"OK\",\"status\":\"OK\",\"count\":3,\"ready\":true}",
            "{\"status\":\"OK\",\"count\":03,\"ready\":true}",
            "{\"status\":\"OK\",\"count\":3,\"ready\":true,}",
            "{\"status\":\"OK\",\"count\":3,\"ready\":true} trailing",
            "{\"status\":\"OK\",\"count\":3,\"ready\":null}",
            "{\"status\":\"OK\",\"count\":3,\"ready\":[]}"
        })ok(!WorkflowPolicy.verified(values,bad));
        for(String bad:new String[]{"{}","{\"x\":null}","{\"x\":[]}","{\"x\":{}}","{\"x\":1,\"x\":2}","{\"x\":1e999}","{\"x\":1e-999999}","{\"x\":01}","{\"x\":\"\\uD800\"}","{\"x\":1} x"})rejects("p | @jsonvalues:"+bad);
        ok(WorkflowJsonFields.matchesValues("{\"x\":\"한글 😀\"}","{\"\\u0078\":\"한글 \\uD83D\\uDE00\"}"));
        ok(WorkflowJsonFields.matchesValues("{\"x\":3.25e-2}","{\"x\":0.0325}"));
        ok(!WorkflowJsonFields.matchesValues("{\"x\":9007199254740993}","{\"x\":9007199254740992}"));
        ok(!WorkflowJsonFields.matchesValues("{\"x\":1e-100}","{\"x\":2e-100}"));
        ok(!WorkflowJsonFields.matchesValues("{\"x\":1}","{\"x\":1e0}"));
        ok(WorkflowPolicy.verified(WorkflowPolicy.parse("p | @jsonvalues:{\"x\":1}",1).get(0),"literal @jsonvalues:{\"x\":1}"));
        ok(WorkflowPolicy.decide(values,"{\"status\":\"OK\",\"count\":3,\"ready\":true}",true,1)==WorkflowPolicy.Decision.RETRY);
        ok(WorkflowPolicy.decide(values,"{}",false,3)==WorkflowPolicy.Decision.BLOCK);
        ok(WorkflowPolicy.decide(values,"{}",false,2)==WorkflowPolicy.Decision.RETRY);
        ok(WorkflowPolicy.decide(values,"{\"status\":\"OK\",\"count\":3,\"ready\":true}",false,0)==WorkflowPolicy.Decision.BLOCK);
        for(WorkflowPolicy.Step invalid:new WorkflowPolicy.Step[]{
            null,new WorkflowPolicy.Step("p","",WorkflowPolicy.Gate.CONTAINS),
            new WorkflowPolicy.Step("p","0",WorkflowPolicy.Gate.MIN_CHARS),
            new WorkflowPolicy.Step("p","not-number",WorkflowPolicy.Gate.MIN_CHARS),
            new WorkflowPolicy.Step("p","x,x",WorkflowPolicy.Gate.JSON_KEYS),
            new WorkflowPolicy.Step("p","x:unknown",WorkflowPolicy.Gate.JSON_FIELDS),
            new WorkflowPolicy.Step("p","{}",WorkflowPolicy.Gate.JSON_VALUES)
        }){
            ok(WorkflowPolicy.sample(invalid,"OK",false)==WorkflowPolicy.Sample.INVALID_CONDITION);
            ok(WorkflowPolicy.sample(invalid,"",true)==WorkflowPolicy.Sample.INVALID_CONDITION);
            ok(WorkflowPolicy.decide(invalid,"OK",false,1)==WorkflowPolicy.Decision.BLOCK);
        }
        ok(WorkflowPolicy.firstPrompt("first | @exact:OK\nsecond | @contains:NEXT").equals(
            WorkflowPolicy.prompt(WorkflowPolicy.parse("first | @exact:OK").get(0),"",false)));
        ok(!WorkflowPolicy.firstPrompt("first | @exact:OK\nsecond | @contains:NEXT").contains("NEXT"));
        StringBuilder ten=new StringBuilder();
        for(int i=1;i<=10;i++)ten.append("Step ").append(i).append(" | @jsonvalues:{\"step\":").append(i).append("}\n");
        java.util.List<WorkflowPolicy.Step> chain=WorkflowPolicy.parse(ten.toString());
        int completed=0,requests=0;
        while(completed<chain.size()){
            int i=completed+1;WorkflowPolicy.Step step=chain.get(completed);
            requests++;ok(WorkflowPolicy.decide(step,"{\"step\":0}",false,1)==WorkflowPolicy.Decision.RETRY);
            requests++;ok(WorkflowPolicy.decide(step,"{\"step\":"+i+"}",true,2)==WorkflowPolicy.Decision.RETRY);
            requests++;
            if(WorkflowPolicy.decide(step,"{\"step\":"+i+"}",false,3)==WorkflowPolicy.Decision.ADVANCE)completed++;
            else throw new AssertionError("Expected advance");
        }
        ok(completed==10&&requests==30);
        System.out.println("PASS: "+n+" workflow policy assertions");
    }
}
