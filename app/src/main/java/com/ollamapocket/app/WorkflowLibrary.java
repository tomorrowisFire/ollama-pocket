package com.ollamapocket.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Bounded private workflow library. No execution, network access or automatic restore. */
final class WorkflowLibrary {
    static final int MAX_TEMPLATES=12,MAX_RUNS=8,MAX_TEMPLATE_CHARS=420000,MAX_HISTORY_CHARS=1500000,MAX_SNAPSHOT=180000;
    static final class Template {
        final String id,name,plan; final long saved;
        Template(String id,String name,String plan,long saved){this.id=id;this.name=name;this.plan=plan;this.saved=saved;}
        JSONObject json()throws Exception{return new JSONObject().put("id",id).put("name",name).put("plan",plan).put("saved",saved);}
    }
    static final class Archive {
        final String id,snapshot,digest;final long archived;
        Archive(String id,String snapshot,String digest,long archived){this.id=id;this.snapshot=snapshot;this.digest=digest;this.archived=archived;}
        JSONObject json()throws Exception{return new JSONObject().put("id",id).put("snapshot",snapshot).put("digest",digest).put("archived",archived);}
    }
    static String digest(String raw)throws Exception{
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder result=new StringBuilder(64);
        for(byte b:bytes)result.append(Character.forDigit((b&255)>>>4,16)).append(Character.forDigit(b&15,16));
        return result.toString();
    }
    private static JSONArray array(String raw,int max)throws Exception{
        if(raw==null)return new JSONArray();
        if(raw.length()>max)throw new IllegalArgumentException("Library limit");
        org.json.JSONTokener tok=new org.json.JSONTokener(raw);Object value=tok.nextValue();
        if(!(value instanceof JSONArray)||tok.nextClean()!=0)throw new IllegalArgumentException("Invalid library");
        return (JSONArray)value;
    }
    private static String name(String raw){
        if(raw==null)throw new IllegalArgumentException("Template name");
        String text=raw.trim();
        if(text.isEmpty()||text.length()>60||text.matches("(?s).*\\p{Cntrl}.*"))throw new IllegalArgumentException("Template name");
        return text;
    }
    private static boolean id(String id){return id!=null&&id.matches("[A-Za-z0-9_-]{1,64}");}
    static List<Template> templates(String raw)throws Exception{
        JSONArray a=array(raw,MAX_TEMPLATE_CHARS);if(a.length()>MAX_TEMPLATES)throw new IllegalArgumentException("Template limit");
        List<Template> out=new ArrayList<>();HashSet<String> ids=new HashSet<>(),names=new HashSet<>();
        for(int i=0;i<a.length();i++){
            JSONObject o=a.getJSONObject(i);
            String id=o.getString("id"),name=name(o.getString("name")),plan=o.getString("plan");
            long saved=o.getLong("saved");
            if(!id(id)||!ids.add(id)||!names.add(name.toLowerCase(Locale.ROOT))||saved<=0)throw new IllegalArgumentException("Invalid template");
            WorkflowPolicy.parse(plan);
            out.add(new Template(id,name,plan,saved));
        }
        return out;
    }
    private static String encodeTemplates(List<Template> list)throws Exception{
        JSONArray a=new JSONArray();for(Template t:list)a.put(t.json());
        String raw=a.toString();if(raw.length()>MAX_TEMPLATE_CHARS)throw new IllegalArgumentException("Template storage limit");
        return raw;
    }
    static String saveTemplate(String raw,String id,String title,String plan,long now)throws Exception{
        List<Template> list=templates(raw);String name=name(title);WorkflowPolicy.parse(plan);
        if(!id(id)||now<=0||list.size()>=MAX_TEMPLATES)throw new IllegalArgumentException("Template limit");
        for(Template t:list)if(t.id.equals(id)||t.name.equalsIgnoreCase(name))throw new IllegalArgumentException("Duplicate template");
        list.add(0,new Template(id,name,plan,now));
        return encodeTemplates(list);
    }
    static String deleteTemplate(String raw,String id)throws Exception{
        List<Template> list=templates(raw);if(!list.removeIf(t->t.id.equals(id)))throw new IllegalArgumentException("Unknown template");
        return encodeTemplates(list);
    }
    static List<Archive> history(String raw)throws Exception{
        JSONArray a=array(raw,MAX_HISTORY_CHARS);if(a.length()>MAX_RUNS)throw new IllegalArgumentException("History limit");
        List<Archive> out=new ArrayList<>();HashSet<String> ids=new HashSet<>();
        for(int i=0;i<a.length();i++){
            JSONObject o=a.getJSONObject(i);
            String id=o.getString("id"),snapshot=o.getString("snapshot"),digest=o.getString("digest");long time=o.getLong("archived");
            if(!id(id)||!ids.add(id)||time<=0||snapshot.length()>MAX_SNAPSHOT||!digest(snapshot).equals(digest)||
                !id.equals(new JSONObject(snapshot).getString("id")))throw new IllegalArgumentException("Invalid history snapshot");
            out.add(new Archive(id,snapshot,digest,time));
        }
        return out;
    }
    private static String encodeHistory(List<Archive> list)throws Exception{
        JSONArray a=new JSONArray();for(Archive r:list)a.put(r.json());return a.toString();
    }
    static String archive(String raw,String snapshot,long now)throws Exception{
        if(snapshot==null||snapshot.length()>MAX_SNAPSHOT||now<=0)throw new IllegalArgumentException("Snapshot limit");
        String id=new JSONObject(snapshot).getString("id");if(!id(id))throw new IllegalArgumentException("Snapshot ID");
        List<Archive> list=history(raw);String digest=digest(snapshot);
        for(Archive r:list)if(r.id.equals(id)){
            if(r.digest.equals(digest))return encodeHistory(list);
            throw new IllegalArgumentException("Duplicate run ID");
        }
        list.add(0,new Archive(id,snapshot,digest,now));
        while(list.size()>MAX_RUNS)list.remove(list.size()-1);
        String encoded=encodeHistory(list);
        while(encoded.length()>MAX_HISTORY_CHARS&&list.size()>1){list.remove(list.size()-1);encoded=encodeHistory(list);}
        if(encoded.length()>MAX_HISTORY_CHARS)throw new IllegalArgumentException("History storage limit");
        return encoded;
    }
    static String deleteHistory(String raw,String id)throws Exception{
        List<Archive> list=history(raw);if(!list.removeIf(a->a.id.equals(id)))throw new IllegalArgumentException("Unknown run");
        return encodeHistory(list);
    }
}
