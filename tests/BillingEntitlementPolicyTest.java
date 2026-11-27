package com.ollamapocket.app;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import static com.ollamapocket.app.BillingEntitlementPolicy.State;

public final class BillingEntitlementPolicyTest {
    private static int checks;
    private static void check(boolean value){if(!value)throw new AssertionError("billing "+checks);checks++;}
    public static void main(String[] args)throws Exception{
        String pkg=BillingEntitlementPolicy.PACKAGE,sku=BillingEntitlementPolicy.PRODUCT;
        List<String> items=List.of(sku);
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
        KeyPair first=generator.generateKeyPair(),other=generator.generateKeyPair();
        String encoded=Base64.getEncoder().encodeToString(first.getPublic().getEncoded());
        PublicKey key=BillingEntitlementPolicy.key(encoded);
        check(key!=null);check(BillingEntitlementPolicy.key("not-a-key")==null);
        check(BillingEntitlementPolicy.key(null)==null);check(BillingEntitlementPolicy.key("A".repeat(9000))==null);
        check(!BillingEntitlementPolicy.enabled(false,true,key));check(!BillingEntitlementPolicy.enabled(true,false,key));
        check(!BillingEntitlementPolicy.enabled(true,true,null));check(BillingEntitlementPolicy.enabled(true,true,key));
        String json="{\"packageName\":\""+pkg+"\",\"productId\":\""+sku+"\",\"purchaseToken\":\"fixture\"}";
        Signature signer=Signature.getInstance("SHA1withRSA");signer.initSign(first.getPrivate());signer.update(json.getBytes(StandardCharsets.UTF_8));
        String proof=Base64.getEncoder().encodeToString(signer.sign());
        check(BillingEntitlementPolicy.verify(key,json,proof));
        check(!BillingEntitlementPolicy.verify(key,json+" ",proof));
        check(!BillingEntitlementPolicy.verify(other.getPublic(),json,proof));
        check(!BillingEntitlementPolicy.verify(key,json,"invalid"));
        check(!BillingEntitlementPolicy.verify(null,json,proof));
        check(!BillingEntitlementPolicy.verify(key,"",proof));
        check(!BillingEntitlementPolicy.verify(key,"x".repeat(65537),proof));
        check(!BillingEntitlementPolicy.verify(key,json,"x".repeat(8193)));
        generator.initialize(1024);check(BillingEntitlementPolicy.key(Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded()))==null);
        for(int mask=0;mask<16;mask++){
            boolean purchased=(mask&1)!=0,pending=(mask&2)!=0,acked=(mask&4)!=0,verified=(mask&8)!=0;
            State expected=!verified||purchased==pending?State.INVALID:pending?State.PENDING:acked?State.OWNED:State.NEED_ACK;
            check(BillingEntitlementPolicy.inspect(pkg,items,"token",1,purchased,pending,acked,verified)==expected);
            check(BillingEntitlementPolicy.inspect("wrong",items,"token",1,purchased,pending,acked,verified)==State.INVALID);
            check(BillingEntitlementPolicy.inspect(pkg,items,"token",2,purchased,pending,acked,verified)==State.INVALID);
            check(BillingEntitlementPolicy.inspect(pkg,List.of("other"),"token",1,purchased,pending,acked,verified)==State.ABSENT);
        }
        check(BillingEntitlementPolicy.inspect(pkg,List.of(sku,sku),"token",1,true,false,true,true)==State.INVALID);
        check(BillingEntitlementPolicy.inspect(pkg,items," ",1,true,false,true,true)==State.INVALID);
        check(BillingEntitlementPolicy.inspect(pkg,items,"x".repeat(4097),1,true,false,true,true)==State.INVALID);
        check(BillingEntitlementPolicy.inspect(pkg,null,"token",1,true,false,true,true)==State.INVALID);
        for(State a:State.values())for(State b:State.values()){
            State expected=b==State.ABSENT?a:a==State.ABSENT?b:State.INVALID;
            check(BillingEntitlementPolicy.merge(a,b)==expected);
        }
        for(State state:new State[]{null,State.ABSENT,State.INVALID,State.PENDING,State.NEED_ACK,State.OWNED})for(int mask=0;mask<128;mask++){
            boolean actual=BillingEntitlementPolicy.mayBuy((mask&1)!=0,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0,(mask&16)!=0,(mask&32)!=0,state,(mask&64)!=0);
            check(actual==(state==State.ABSENT&&mask==79));
        }
        System.out.println("PASS: "+checks+" billing policy, signature and launch-guard checks");
    }
}
