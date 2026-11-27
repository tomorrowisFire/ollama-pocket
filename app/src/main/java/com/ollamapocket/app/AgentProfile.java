package com.ollamapocket.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import org.json.JSONObject;

/** Portable declarative local-only configuration, not an installer, permission grant or executable workflow. */
final class AgentProfile {
    private static final Set<String> KEYS=new HashSet<>(Arrays.asList("version","name","model","modelSha256","instruction","temperatureMilli","maxTokens"));
    private static final String SEG="[A-Za-z0-9][A-Za-z0-9._-]{0,63}";
    final String name,model,modelSha256,instruction;
    final int temperatureMilli,maxTokens;
    AgentProfile(String name,String model,String modelSha256,String instruction,int temperatureMilli,int maxTokens){
        this.name=text(name,80,false);
        this.instruction=text(instruction,4096,true);
        if(model==null||!model.matches("(?:"+SEG+"/)?"+SEG+":"+SEG)||model.contains("..")||
                modelSha256==null||!modelSha256.matches("[a-f0-9]{64}")||
                temperatureMilli<0||temperatureMilli>2000||maxTokens<1||maxTokens>512)
            throw new IllegalArgumentException("Agent profile model or parameters");
        this.model=model;this.modelSha256=modelSha256;this.temperatureMilli=temperatureMilli;this.maxTokens=maxTokens;
    }
    private static String text(String s,int limit,boolean multiline){
        if(s==null||s.length()>limit||s.trim().isEmpty())throw new IllegalArgumentException("Agent profile text");
        boolean visible=false;
        for(int i=0;i<s.length();){
            int cp=s.codePointAt(i);i+=Character.charCount(cp);
            if(cp>=0xd800&&cp<=0xdfff||cp==0x2028||cp==0x2029||
                    Character.isISOControl(cp)&&!(multiline&&(cp=='\n'||cp=='\r'||cp=='\t'))||
                    Character.getType(cp)==Character.FORMAT&&cp!=0x200c&&cp!=0x200d)
                throw new IllegalArgumentException("Agent profile text control");
            if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp)&&cp!=0x200c&&cp!=0x200d)visible=true;
        }
        if(!visible)throw new IllegalArgumentException("Empty agent profile text");
        return s;
    }
    private static String string(JSONObject j,String key)throws Exception{
        Object v=j.get(key);if(!(v instanceof String))throw new IllegalArgumentException("Agent profile field type");
        return (String)v;
    }
    private static int integer(JSONObject j,String key)throws Exception{
        Object v=j.get(key);
        if(!(v instanceof Integer)&&!(v instanceof Long))throw new IllegalArgumentException("Agent profile integer type");
        long n=((Number)v).longValue();
        if(n<Integer.MIN_VALUE||n>Integer.MAX_VALUE)throw new IllegalArgumentException("Agent profile integer range");
        return (int)n;
    }
    static AgentProfile decode(String raw)throws Exception{
        ToolJsonPolicy.requireObject(raw);
        JSONObject j=new JSONObject(raw);
        if(j.length()!=KEYS.size())throw new IllegalArgumentException("Agent profile fields");
        for(Iterator<String> i=j.keys();i.hasNext();)if(!KEYS.contains(i.next()))throw new IllegalArgumentException("Unknown agent profile field");
        if(integer(j,"version")!=1)throw new IllegalArgumentException("Agent profile version");
        return new AgentProfile(string(j,"name"),string(j,"model"),string(j,"modelSha256"),
                string(j,"instruction"),integer(j,"temperatureMilli"),integer(j,"maxTokens"));
    }
    private static String q(String s){
        return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r")
                .replace("\n","\\n").replace("\t","\\t")+"\"";
    }
    String encode(){
        return "{\"version\":1,\"name\":"+q(name)+",\"model\":"+q(model)+",\"modelSha256\":"+q(modelSha256)
                +",\"instruction\":"+q(instruction)+",\"temperatureMilli\":"+temperatureMilli+",\"maxTokens\":"+maxTokens+"}";
    }
    /** Identity of the reviewed canonical content, not proof of trusted provenance or user consent. */
    String reviewDigest(){
        try{
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(encode().getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder(64);
            for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b&255));
            return out.toString();
        }catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    boolean matchesInstalled(String localModel,String localDigest){
        return model.equals(localModel)&&modelSha256.equals(localDigest);
    }
}
