package com.ollamapocket.app;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Closed, deliberately narrow JSON Schema subset; unsupported constraints fail closed. */
final class McpInputPolicy {
    private static final Set<String> COMMON=new HashSet<>(Arrays.asList("type","title","description","default","examples","$schema","enum","const"));
    private static final Set<String> TYPES=new HashSet<>(Arrays.asList("object","array","string","integer","number","boolean","null"));
    static JSONObject arguments(String raw)throws Exception{
        if(raw==null||raw.length()>16000)throw new IllegalArgumentException("Argument limit");
        JSONTokener t=new JSONTokener(raw);Object x=t.nextValue();
        if(!(x instanceof JSONObject)||t.nextClean()!=0)throw new IllegalArgumentException("Expected JSON object");
        return (JSONObject)x;
    }
    static boolean supportsObject(JSONObject schema){
        try{return schema!=null&&"object".equals(schema.opt("type"))&&schemaNode(schema,0,new int[]{0});}catch(Exception e){return false;}
    }
    private static boolean bound(JSONObject s,String k){
        if(!s.has(k))return true;
        Object v=s.opt(k);
        return v instanceof Number&&number(v).signum()>=0&&number(v).compareTo(BigDecimal.valueOf(16000))<=0&&number(v).stripTrailingZeros().scale()<=0;
    }
    private static BigDecimal number(Object v){return new BigDecimal(v.toString());}
    private static boolean primitive(Object v){return v==JSONObject.NULL||v instanceof String||v instanceof Number||v instanceof Boolean;}
    private static boolean same(Object a,Object b){
        if(a instanceof Number&&b instanceof Number)return number(a).compareTo(number(b))==0;
        if(a==JSONObject.NULL||b==JSONObject.NULL)return a==b;
        return a!=null&&a.equals(b);
    }
    private static boolean schemaNode(JSONObject s,int depth,int[] n)throws Exception{
        if(depth>8||++n[0]>256||!(s.opt("type") instanceof String))return false;
        String type=s.getString("type");if(!TYPES.contains(type))return false;
        Set<String> keys=new HashSet<>(COMMON);
        switch(type){
            case "object":keys.addAll(Arrays.asList("properties","required","additionalProperties"));break;
            case "array":keys.addAll(Arrays.asList("items","minItems","maxItems"));break;
            case "string":keys.addAll(Arrays.asList("minLength","maxLength"));break;
            case "integer":case "number":keys.addAll(Arrays.asList("minimum","maximum"));break;
        }
        for(Iterator<String> i=s.keys();i.hasNext();)if(!keys.contains(i.next()))return false;
        if(s.has("enum")){
            JSONArray a=s.optJSONArray("enum");if(a==null||a.length()==0||a.length()>64)return false;
            for(int i=0;i<a.length();i++)if(!primitive(a.get(i)))return false;
        }
        if(s.has("const")&&!primitive(s.get("const")))return false;
        if("object".equals(type)){
            JSONObject props=s.optJSONObject("properties");if(props==null&&s.has("properties"))return false;
            if(s.has("additionalProperties")&&!(s.opt("additionalProperties") instanceof Boolean))return false;
            if(props!=null)for(Iterator<String> i=props.keys();i.hasNext();){
                String k=i.next();if(k.length()>128||props.optJSONObject(k)==null||!schemaNode(props.getJSONObject(k),depth+1,n))return false;
            }
            if(s.has("required")){
                JSONArray a=s.optJSONArray("required");if(a==null||a.length()>64)return false;
                Set<String> seen=new HashSet<>();
                for(int i=0;i<a.length();i++)if(!(a.get(i) instanceof String)||props==null||!props.has(a.getString(i))||!seen.add(a.getString(i)))return false;
            }
        }else if("array".equals(type)){
            if(s.optJSONObject("items")==null||!schemaNode(s.getJSONObject("items"),depth+1,n)||!bound(s,"minItems")||!bound(s,"maxItems")||s.optInt("minItems",0)>s.optInt("maxItems",16000))return false;
        }else if("string".equals(type)){
            if(!bound(s,"minLength")||!bound(s,"maxLength")||s.optInt("minLength",0)>s.optInt("maxLength",16000))return false;
        }else if("integer".equals(type)||"number".equals(type)){
            for(String k:new String[]{"minimum","maximum"})if(s.has(k)&&!(s.get(k) instanceof Number))return false;
            if(s.has("minimum")&&s.has("maximum")&&number(s.get("minimum")).compareTo(number(s.get("maximum")))>0)return false;
        }
        return true;
    }
    static boolean valid(JSONObject schema,JSONObject args){
        try{return supportsObject(schema)&&args!=null&&value(schema,args,0,new int[]{0});}catch(Exception e){return false;}
    }
    private static boolean value(JSONObject s,Object v,int depth,int[] n)throws Exception{
        if(depth>8||++n[0]>1024)return false;
        if(s.has("const")&&!same(s.get("const"),v))return false;
        JSONArray choices=s.optJSONArray("enum");
        if(choices!=null){boolean found=false;for(int i=0;i<choices.length();i++)if(same(choices.get(i),v))found=true;if(!found)return false;}
        switch(s.getString("type")){
            case "object":
                if(!(v instanceof JSONObject))return false;
                JSONObject o=(JSONObject)v,props=s.optJSONObject("properties");
                JSONArray required=s.optJSONArray("required");
                if(required!=null)for(int i=0;i<required.length();i++)if(!o.has(required.getString(i)))return false;
                // Local policy always denies undeclared fields, even if the remote schema allows extras.
                for(Iterator<String> i=o.keys();i.hasNext();){
                    String k=i.next();if(props==null||!props.has(k)||!value(props.getJSONObject(k),o.get(k),depth+1,n))return false;
                }
                return true;
            case "array":
                if(!(v instanceof JSONArray))return false;
                JSONArray a=(JSONArray)v;if(a.length()<s.optInt("minItems",0)||a.length()>s.optInt("maxItems",16000))return false;
                for(int i=0;i<a.length();i++)if(!value(s.getJSONObject("items"),a.get(i),depth+1,n))return false;
                return true;
            case "string":
                if(!(v instanceof String))return false;
                String text=(String)v;int len=text.codePointCount(0,text.length());
                return len>=s.optInt("minLength",0)&&len<=s.optInt("maxLength",16000);
            case "integer":case "number":
                if(!(v instanceof Number))return false;
                BigDecimal num=number(v);
                return (!"integer".equals(s.getString("type"))||num.stripTrailingZeros().scale()<=0)&&
                    (!s.has("minimum")||num.compareTo(number(s.get("minimum")))>=0)&&
                    (!s.has("maximum")||num.compareTo(number(s.get("maximum")))<=0);
            case "boolean":return v instanceof Boolean;
            case "null":return v==JSONObject.NULL;
            default:return false;
        }
    }
}
