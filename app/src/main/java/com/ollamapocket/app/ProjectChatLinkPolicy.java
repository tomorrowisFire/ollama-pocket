package com.ollamapocket.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Local reference resolution. No writes, model launch, context transfer or network. */
final class ProjectChatLinkPolicy {
    enum Status { AVAILABLE, MISSING, UNREADABLE, MODEL_UNAVAILABLE }
    private static final int MAX_CHARS=512000,MAX_INDEX_ROWS=4096,MAX_HISTORY_ROWS=256;
    private ProjectChatLinkPolicy(){}
    private static JSONArray array(String raw)throws Exception{
        if(raw.length()>MAX_CHARS||!raw.trim().startsWith("["))throw new IllegalArgumentException("Invalid array");
        int depth=0;boolean quoted=false,escaped=false;
        for(int i=0;i<raw.length();i++){
            char c=raw.charAt(i);
            if(quoted){
                if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;
            }else if(c=='"')quoted=true;
            else if(c=='{'||c=='['){if(++depth>8)throw new IllegalArgumentException("Depth");}
            else if(c=='}'||c==']'){if(--depth<0)throw new IllegalArgumentException("Depth");}
            else if(c=='\''||c=='/')throw new IllegalArgumentException("Nonstandard JSON");
        }
        if(quoted||escaped||depth!=0)throw new IllegalArgumentException("Structure");
        JSONTokener tok=new JSONTokener(raw);Object o=tok.nextValue();
        if(!(o instanceof JSONArray)||tok.nextClean()!=0)throw new IllegalArgumentException("Invalid array");
        return (JSONArray)o;
    }
    static Status inspect(ProjectLibrary.Link link,String index,String history,boolean modelInstalled){
        if(link==null)return Status.UNREADABLE;
        if(index==null||history==null)return Status.MISSING;
        try{
            JSONArray records=array(index);
            if(records.length()>MAX_INDEX_ROWS)return Status.UNREADABLE;
            int matches=0;
            for(int i=0;i<records.length();i++){
                JSONObject row=records.getJSONObject(i);
                if(!(row.opt("id") instanceof String))return Status.UNREADABLE;
                if(link.session.equals(row.opt("id")))matches++;
            }
            if(matches==0)return Status.MISSING;
            if(matches!=1)return Status.UNREADABLE;
            JSONArray messages=array(history);
            if(messages.length()>MAX_HISTORY_ROWS)return Status.UNREADABLE;
            for(int i=0;i<messages.length();i++){
                JSONObject message=messages.getJSONObject(i);
                if(!(message.opt("role") instanceof String)||!(message.opt("content") instanceof String))return Status.UNREADABLE;
            }
            return modelInstalled?Status.AVAILABLE:Status.MODEL_UNAVAILABLE;
        }catch(Exception e){return Status.UNREADABLE;}
    }
}
