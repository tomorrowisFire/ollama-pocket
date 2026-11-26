package com.ollamapocket.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Explicit opt-in persistent storage: ciphertext in private preferences, key in Android Keystore. */
final class CloudKeyVault {
    private final SharedPreferences prefs;
    CloudKeyVault(Context context){prefs=context.getApplicationContext().getSharedPreferences("onhands_cloud_keys_v1",Context.MODE_PRIVATE);}
    private static String alias(CloudApi.Provider provider){return "onhands.cloud.v1."+provider.name();}
    private static KeyStore store()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);return ks;
    }
    private static SecretKey secret(CloudApi.Provider p,boolean create)throws Exception{
        KeyStore ks=store();String alias=alias(p);
        java.security.Key existing=ks.getKey(alias,null);
        if(existing instanceof SecretKey)return (SecretKey)existing;
        if(!create)throw new SecurityException("Keystore key missing");
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
    synchronized void save(CloudApi.Provider p,String value)throws Exception{
        if(p==null||p.remote()||!PrivacyPolicy.validApiKey(value))throw new IllegalArgumentException("Invalid hosted credential");
        byte[] ciphertext=CloudKeyCipher.encrypt(secret(p,true),p.name(),value);
        if(!prefs.edit().putString(p.name(),Base64.encodeToString(ciphertext,Base64.NO_WRAP)).commit())
            throw new java.io.IOException("Key storage failed");
    }
    synchronized String load(CloudApi.Provider p)throws Exception{
        if(p==null||p.remote())throw new IllegalArgumentException("Hosted credential required");
        String encoded=prefs.getString(p.name(),null);
        if(encoded==null)return "";
        if(encoded.length()>22000)throw new SecurityException("Invalid stored credential");
        return CloudKeyCipher.decrypt(secret(p,false),p.name(),Base64.decode(encoded,Base64.NO_WRAP));
    }
    synchronized void delete(CloudApi.Provider p)throws Exception{
        if(p==null||p.remote())throw new IllegalArgumentException("Hosted credential required");
        if(!prefs.edit().remove(p.name()).commit())throw new java.io.IOException("Key removal failed");
        KeyStore ks=store();if(ks.containsAlias(alias(p)))ks.deleteEntry(alias(p));
    }
}
