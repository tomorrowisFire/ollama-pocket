package com.ollamapocket.app;

import java.util.Locale;

public final class AgentProfileTest {
    private static final String SHA="a".repeat(64);
    private static int checks;
    private interface Action{void run()throws Exception;}
    private static void ok(boolean pass,String label){checks++;if(!pass)throw new AssertionError(label);}
    private static void reject(Action a)throws Exception{checks++;try{a.run();}catch(Exception expected){return;}throw new AssertionError("Invalid agent profile accepted");}
    private static AgentProfile make(String name,String model,String digest,String instruction,int temperature,int max){
        return new AgentProfile(name,model,digest,instruction,temperature,max);
    }
    public static void main(String[] args)throws Exception{
        AgentProfile p=make("업무 помощник","local/qwen2.5:7b",SHA,"안녕 \"quote\"\\\n日本語 中文 😀",750,512);
        String raw=p.encode(),digest=p.reviewDigest();
        AgentProfile copy=AgentProfile.decode(raw);
        ok(raw.equals(copy.encode())&&digest.equals(copy.reviewDigest()),"Canonical round trip");
        ok(digest.matches("[a-f0-9]{64}"),"Digest format");
        ok(copy.matchesInstalled(p.model,SHA)&&!copy.matchesInstalled("local/qwen2.5:latest",SHA)&&
                !copy.matchesInstalled(p.model,"b".repeat(64)),"Exact installed identity");
        ok(!raw.contains("apiKey")&&!raw.contains("endpoint")&&!raw.contains("permissions"),"No credential/route fields");
        ok(!digest.equals(make("other",p.model,SHA,p.instruction,750,512).reviewDigest()),"Name identity");
        ok(!digest.equals(make(p.name,p.model,SHA,p.instruction+" ",750,512).reviewDigest()),"Instruction identity");
        ok(!digest.equals(make(p.name,p.model,SHA,p.instruction,751,512).reviewDigest()),"Parameter identity");
        ok(!digest.equals(make(p.name,p.model,"b".repeat(64),p.instruction,750,512).reviewDigest()),"Weight identity");
        ok(AgentProfile.decode(" \n"+raw+" ").reviewDigest().equals(digest),"Formatting-independent review");
        for(String key:new String[]{"endpoint","apiKey","history","tools","permission","workflow","autoRun","url"})
            reject(()->AgentProfile.decode(raw.substring(0,raw.length()-1)+",\""+key+"\":true}"));
        for(String bad:new String[]{"",null,"{}",raw+"{}",raw.replace("\"version\":1","\"version\":2"),
                raw.replace("\"version\":1","\"version\":1.0"),raw.replace("\"maxTokens\":512","\"maxTokens\":\"512\""),
                raw.replace("\"maxTokens\":512","\"maxTokens\":513"),raw.replace("\"maxTokens\":512","\"maxTokens\":0"),
                raw.replace("\"temperatureMilli\":750","\"temperatureMilli\":2001"),
                raw.replace("\"temperatureMilli\":750","\"temperatureMilli\":750e0"),
                raw.replace("\"version\":1","\"version\":1,\"\\u0076ersion\":1")})
            reject(()->AgentProfile.decode(bad));
        for(String model:new String[]{null,"","http://server:80","../qwen:7b","qwen","a/b/c:x","a:b/c","a:..","a:b c","/a:b","a:","a".repeat(65)+":b"})
            reject(()->make("name",model,SHA,"instruction",0,1));
        for(String bad:new String[]{null,""," ","\u00a0","\u200d","x\n","x\u202e","x\u0000","x\ud800","x".repeat(81)})
            reject(()->make(bad,"qwen:7b",SHA,"instruction",0,1));
        for(String bad:new String[]{null,""," ","\u200d","x\u2028","x\u0000","x\udc00","x".repeat(4097)})
            reject(()->make("name","qwen:7b",SHA,bad,0,1));
        reject(()->make("name","qwen:7b","A".repeat(64),"instruction",0,1));
        reject(()->make("name","qwen:7b",SHA,"instruction",-1,1));
        reject(()->make("name","qwen:7b",SHA,"instruction",0,513));
        for(String name:new String[]{"한국어","English","日本語","简体中文","繁體中文"}){
            for(int temperature:new int[]{0,1,1000,2000}){
                AgentProfile a=make(name,"qwen:7b",SHA,"\"\\\n\t".repeat(1000),temperature,1);
                ok(AgentProfile.decode(a.encode()).reviewDigest().equals(a.reviewDigest()),"Locale and escaped text round trip");
            }
        }
        Locale old=Locale.getDefault();
        try{Locale.setDefault(Locale.GERMANY);ok(p.reviewDigest().equals(digest),"Locale-invariant digest");}
        finally{Locale.setDefault(old);}
        System.out.println("PASS: "+checks+" declarative local agent profile import/export checks");
    }
}
