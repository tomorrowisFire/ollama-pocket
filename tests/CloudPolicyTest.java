package com.ollamapocket.app;

import java.util.Arrays;
import java.util.List;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public final class CloudPolicyTest {
    private static int n;
    private static void ok(boolean b,String label){if(!b)throw new AssertionError(label);n++;}
    private interface Checked{void run()throws Exception;}
    private static void fails(Checked f,String label){try{f.run();}catch(Exception e){n++;return;}throw new AssertionError(label);}
    public static void main(String[] args)throws Exception{
        CloudHistory h=new CloudHistory();
        CloudHistory.Session a=h.session("OPENAI","model-a"),b=h.session("CLAUDE","model-a"),c=h.session("OPENAI","model-b");
        a.complete("one","two");
        ok(a.request("three").size()==3,"multi-turn");
        ok("assistant".equals(a.request("three").get(1).role),"alternation");
        ok("three".equals(a.request("three").get(2).content),"latest question");
        ok(b.request("other").size()==1&&c.request("other").size()==1,"scope isolated");
        ok(a==h.session("OPENAI","model-a"),"session retained");
        a.request("failed question");ok(a.turns().size()==1,"uncommitted request");
        a.clear();ok(a.turns().isEmpty(),"clear selected");b.complete("b","answer");ok(b.turns().size()==1,"clear preserves other");
        for(int i=0;i<20;i++)a.complete("q"+i,"a"+i);
        ok(a.turns().size()==12,"turn bound");ok("q8".equals(a.turns().get(0).question),"oldest removed");
        a.complete("x".repeat(16000),"y".repeat(48000));List<CloudHistory.Message> req=a.request("last");
        ok(req.size()==1,"whole-pair context bound");
        fails(()->a.request("z".repeat(16001)),"prompt bound");
        fails(()->a.complete("x","y".repeat(48001)),"reply bound");
        fails(()->a.complete("x","\ud800"),"invalid Unicode reply");
        fails(()->a.complete("\u00a0\u200d","ok"),"invisible prompt");
        ok(CloudHistory.validMessage("😀 한글",100),"valid Unicode message");
        fails(()->h.session("OPENAI",""),"invalid model");
        fails(()->h.session("OPENAI\0OTHER","x"),"invalid scope");
        KeyGenerator generator=KeyGenerator.getInstance("AES");generator.init(256);SecretKey key=generator.generateKey();
        String original="sample-key-123";byte[] encrypted=CloudKeyCipher.encrypt(key,"OPENAI",original);
        ok(original.equals(CloudKeyCipher.decrypt(key,"OPENAI",encrypted)),"round trip");
        ok(!Arrays.equals(encrypted,CloudKeyCipher.encrypt(key,"OPENAI",original)),"unique IV");
        fails(()->CloudKeyCipher.decrypt(key,"CLAUDE",encrypted),"wrong scope");
        byte[] changed=encrypted.clone();changed[changed.length-1]^=1;
        fails(()->CloudKeyCipher.decrypt(key,"OPENAI",changed),"tamper rejected");
        fails(()->CloudKeyCipher.decrypt(generator.generateKey(),"OPENAI",encrypted),"wrong key");
        fails(()->CloudKeyCipher.encrypt(key,"OPENAI","a\nb"),"invalid header");
        System.out.println("CloudPolicyTest PASS: "+n+" assertions");
    }
}
