package com.ollamapocket.app;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Authenticated encryption; provider scope is authenticated as additional data. */
final class CloudKeyCipher {
    private CloudKeyCipher(){}
    static byte[] encrypt(SecretKey key,String provider,String value)throws Exception{
        if(value==null||value.isEmpty()||value.length()>4096||value.indexOf('\r')>=0||value.indexOf('\n')>=0)
            throw new IllegalArgumentException("Invalid key");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key);
        cipher.updateAAD(provider.getBytes(StandardCharsets.UTF_8));
        byte[] plain=value.getBytes(StandardCharsets.UTF_8);
        try{
            byte[] iv=cipher.getIV();if(iv==null||iv.length!=12)throw new SecurityException("Invalid IV");
            byte[] encrypted=cipher.doFinal(plain);
            byte[] result=new byte[iv.length+encrypted.length];
            System.arraycopy(iv,0,result,0,iv.length);
            System.arraycopy(encrypted,0,result,iv.length,encrypted.length);
            return result;
        }finally{Arrays.fill(plain,(byte)0);}
    }
    static String decrypt(SecretKey key,String provider,byte[] data)throws Exception{
        if(data==null||data.length<29||data.length>16500)throw new SecurityException("Invalid ciphertext");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,data,0,12));
        cipher.updateAAD(provider.getBytes(StandardCharsets.UTF_8));
        byte[] plain=cipher.doFinal(data,12,data.length-12);
        try{return new String(plain,StandardCharsets.UTF_8);}
        finally{Arrays.fill(plain,(byte)0);}
    }
}
