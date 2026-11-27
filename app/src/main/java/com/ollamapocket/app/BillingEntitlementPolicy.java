package com.ollamapocket.app;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/** Test-only non-consumable entitlement policy. A production backend must verify and revoke ownership. */
final class BillingEntitlementPolicy {
    static final String PACKAGE="io.github.tomorrowisfire.aiinpocket";
    static final String PRODUCT="onhand_remove_ads_lifetime";
    enum State { ABSENT, INVALID, PENDING, NEED_ACK, OWNED }
    private BillingEntitlementPolicy(){}
    static PublicKey key(String encoded){
        if(encoded==null||encoded.length()<128||encoded.length()>8192)return null;
        try{
            PublicKey key=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
            return key instanceof RSAPublicKey&&((RSAPublicKey)key).getModulus().bitLength()>=2048?key:null;
        }catch(Exception invalid){return null;}
    }
    static boolean enabled(boolean debug,boolean optIn,PublicKey key){
        return debug&&optIn&&key instanceof RSAPublicKey&&((RSAPublicKey)key).getModulus().bitLength()>=2048;
    }
    /** Verify the exact Play-provided JSON, not reconstructed or UI-supplied fields. */
    static boolean verify(PublicKey key,String json,String signature){
        if(key==null||json==null||json.isEmpty()||json.length()>65536||signature==null||signature.isEmpty()||signature.length()>8192)return false;
        try{
            Signature verifier=Signature.getInstance("SHA1withRSA");
            verifier.initVerify(key);verifier.update(json.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        }catch(Exception invalid){return false;}
    }
    static State inspect(String pkg,List<String> products,String token,int quantity,boolean purchased,boolean pending,boolean acknowledged,boolean verified){
        if(products==null)return State.INVALID;
        if(!products.contains(PRODUCT))return State.ABSENT;
        if(!PACKAGE.equals(pkg)||products.size()!=1||quantity!=1||token==null||token.trim().isEmpty()||token.length()>4096||!verified||purchased==pending)return State.INVALID;
        if(pending)return State.PENDING;
        return acknowledged?State.OWNED:State.NEED_ACK;
    }
    /** Fail closed on an ambiguous snapshot containing multiple matching records. */
    static State merge(State earlier,State next){
        if(earlier==null||next==null)return State.INVALID;
        if(next==State.ABSENT)return earlier;
        if(earlier==State.ABSENT)return next;
        return State.INVALID;
    }
    static boolean mayBuy(boolean configured,boolean foreground,boolean eligible,boolean ready,boolean working,boolean flowing,State ownership,boolean freshOffer){
        return configured&&foreground&&eligible&&ready&&!working&&!flowing&&ownership==State.ABSENT&&freshOffer;
    }
}
