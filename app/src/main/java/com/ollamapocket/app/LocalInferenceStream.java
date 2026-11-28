package com.ollamapocket.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded protocol parsing shared by the actual local inference paths and offline fixtures. */
final class LocalInferenceStream {
    static final int MAX_LINE=1_000_000,MAX_WIRE=8_000_000,MAX_LINES=65_536;
    interface Listener { void accept(JSONObject item)throws Exception; }
    private LocalInferenceStream(){}
    static String line(BufferedReader reader,int limit)throws IOException{
        return LocalStreamCancellation.line(reader,limit,null);
    }
    private static String textOrEmpty(JSONObject object,String key)throws IOException{
        Object value=object.opt(key);
        if(value==null||value==JSONObject.NULL)return "";
        if(!(value instanceof String))throw new IOException("LOCAL_ENGINE_PROTOCOL");
        return (String)value;
    }
    private static JSONObject optionalObject(JSONObject object,String key)throws IOException{
        Object value=object.opt(key);
        if(value==null||value==JSONObject.NULL)return null;
        if(!(value instanceof JSONObject))throw new IOException("LOCAL_ENGINE_PROTOCOL");
        return (JSONObject)value;
    }
    private static final class Budget {
        long chars;int lines;
        void add(String row)throws IOException{
            chars+=(long)row.length()+1;
            if(chars>MAX_WIRE||++lines>MAX_LINES)throw new IOException("CHAT_STREAM_LIMIT");
        }
    }
    static void ndjson(BufferedReader reader,Listener listener,boolean requireDone)throws Exception{
        ndjson(reader,listener,requireDone,requireDone?()->false:null);
    }
    static void ndjson(BufferedReader reader,Listener listener,boolean requireDone,BooleanSupplier cancelled)throws Exception{
        Budget budget=new Budget();
        for(String row;(row=LocalStreamCancellation.line(reader,MAX_LINE,cancelled))!=null;){
            if(requireDone)budget.add(row);
            if(row.trim().isEmpty())continue;
            JSONObject message=new JSONObject(row);
            if(message.has("error"))throw new IOException("LOCAL_ENGINE_ERROR");
            if(requireDone){
                if(message.has("done")&&!(message.opt("done") instanceof Boolean))throw new IOException("LOCAL_ENGINE_PROTOCOL");
                JSONObject content=optionalObject(message,"message");
                if(content!=null&&content.has("content"))content.put("content",textOrEmpty(content,"content"));
            }
            LocalStreamCancellation.check(cancelled);
            listener.accept(message);
            LocalStreamCancellation.check(cancelled);
            if(requireDone&&Boolean.TRUE.equals(message.opt("done")))return;
        }
        LocalStreamCancellation.check(cancelled);
        if(requireDone)throw new IOException("CHAT_STREAM_INCOMPLETE");
    }
    /** Pulls may last for hours; bound each record, not the total progress event count. */
    static void pull(BufferedReader reader,Listener listener,BooleanSupplier cancelled)throws Exception{
        for(String row;(row=LocalStreamCancellation.line(reader,MAX_LINE,cancelled))!=null;){
            if(row.trim().isEmpty())continue;
            JSONObject item;
            try{item=new JSONObject(row);}catch(Exception invalid){throw new IOException("PULL_PROTOCOL",invalid);}
            if(item.has("error"))throw new IOException("LOCAL_ENGINE_ERROR");
            Object raw=item.opt("status");
            if(!(raw instanceof String)||((String)raw).trim().isEmpty()||((String)raw).length()>1024)
                throw new IOException("PULL_PROTOCOL");
            for(String key:new String[]{"total","completed"}){
                Object value=item.opt(key);
                if(value==null||value==JSONObject.NULL)continue;
                if(!(value instanceof Number))throw new IOException("PULL_PROTOCOL");
                try{
                    if(new java.math.BigDecimal(value.toString()).longValueExact()<0)throw new IOException("PULL_PROTOCOL");
                }catch(ArithmeticException|NumberFormatException invalid){throw new IOException("PULL_PROTOCOL",invalid);}
            }
            LocalStreamCancellation.check(cancelled);
            listener.accept(item);
            LocalStreamCancellation.check(cancelled);
            if("success".equals(raw))return;
        }
        LocalStreamCancellation.check(cancelled);
        throw new IOException("PULL_INCOMPLETE");
    }
    static void sse(BufferedReader reader,Listener listener,boolean requireTerminal)throws Exception{
        sse(reader,listener,requireTerminal,requireTerminal?()->false:null);
    }
    static void sse(BufferedReader reader,Listener listener,boolean requireTerminal,BooleanSupplier cancelled)throws Exception{
        Budget budget=new Budget();StringBuilder data=new StringBuilder();
        boolean terminal=false;
        for(String row;(row=LocalStreamCancellation.line(reader,MAX_LINE,cancelled))!=null;){
            budget.add(row);
            if(row.startsWith("data:")){
                String part=row.substring(5);if(part.startsWith(" "))part=part.substring(1);
                if((long)data.length()+part.length()+1>MAX_LINE)throw new IOException("CHAT_STREAM_LIMIT");
                if(data.length()>0)data.append('\n');data.append(part);continue;
            }
            if(!row.isEmpty()||data.length()==0)continue;
            String event=data.toString().trim();data.setLength(0);
            if("[DONE]".equals(event)){
                LocalStreamCancellation.check(cancelled);
                if(requireTerminal&&!terminal)throw new IOException("CHAT_STREAM_INCOMPLETE");
                return;
            }
            JSONObject message=new JSONObject(event);
            if(message.has("error"))throw new IOException("LOCAL_ENGINE_ERROR");
            JSONArray choices=message.optJSONArray("choices");
            if(choices==null){
                if(requireTerminal&&!message.isNull("choices"))throw new IOException("LOCAL_ENGINE_PROTOCOL");
                continue;
            }
            if(choices.length()==0)continue;
            JSONObject first=choices.optJSONObject(0);
            if(first==null)throw new IOException("LOCAL_ENGINE_PROTOCOL");
            String reason=requireTerminal?textOrEmpty(first,"finish_reason"):first.isNull("finish_reason")?"":first.optString("finish_reason","");
            if(requireTerminal&&terminal)throw new IOException("CHAT_STREAM_INCOMPLETE");
            JSONObject delta=requireTerminal?optionalObject(first,"delta"):first.optJSONObject("delta");
            String content=delta==null?"":requireTerminal?textOrEmpty(delta,"content"):delta.optString("content","");
            LocalStreamCancellation.check(cancelled);
            if(delta!=null)listener.accept(new JSONObject().put("message",new JSONObject().put("content",content)));
            LocalStreamCancellation.check(cancelled);
            if(LocalChatTurns.gpuTerminal(reason)){
                terminal=true;
                // An explicit model finish reason is authoritative for local chat.
                if(requireTerminal)return;
            }else if(requireTerminal&&!reason.isEmpty())throw new IOException("CHAT_STREAM_INCOMPLETE");
        }
        LocalStreamCancellation.check(cancelled);
        if(requireTerminal&&!terminal)throw new IOException("CHAT_STREAM_INCOMPLETE");
    }
}
