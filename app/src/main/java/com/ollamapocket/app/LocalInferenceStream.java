package com.ollamapocket.app;

import java.io.BufferedReader;
import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded protocol parsing shared by the actual local inference paths and offline fixtures. */
final class LocalInferenceStream {
    static final int MAX_LINE=1_000_000,MAX_WIRE=8_000_000,MAX_LINES=65_536;
    interface Listener { void accept(JSONObject item)throws Exception; }
    private LocalInferenceStream(){}
    static String line(BufferedReader reader,int limit)throws IOException{
        StringBuilder out=new StringBuilder();
        for(int ch;(ch=reader.read())!=-1;){
            if(ch=='\n')return out.toString();
            if(ch=='\r'){reader.mark(1);if(reader.read()!='\n')reader.reset();return out.toString();}
            if(out.length()>=limit)throw new IOException("CHAT_STREAM_LIMIT");
            out.append((char)ch);
        }
        return out.length()==0?null:out.toString();
    }
    private static final class Budget {
        long chars;int lines;
        void add(String row)throws IOException{
            chars+=(long)row.length()+1;
            if(chars>MAX_WIRE||++lines>MAX_LINES)throw new IOException("CHAT_STREAM_LIMIT");
        }
    }
    static void ndjson(BufferedReader reader,Listener listener,boolean requireDone)throws Exception{
        Budget budget=new Budget();
        for(String row;(row=line(reader,MAX_LINE))!=null;){
            if(requireDone)budget.add(row);
            if(row.trim().isEmpty())continue;
            JSONObject message=new JSONObject(row);
            if(message.has("error"))throw new IOException("LOCAL_ENGINE_ERROR");
            listener.accept(message);
            if(requireDone&&Boolean.TRUE.equals(message.opt("done")))return;
        }
        if(requireDone)throw new IOException("CHAT_STREAM_INCOMPLETE");
    }
    static void sse(BufferedReader reader,Listener listener,boolean requireTerminal)throws Exception{
        Budget budget=new Budget();StringBuilder data=new StringBuilder();
        boolean terminal=false;
        for(String row;(row=line(reader,MAX_LINE))!=null;){
            budget.add(row);
            if(row.startsWith("data:")){
                String part=row.substring(5);if(part.startsWith(" "))part=part.substring(1);
                if((long)data.length()+part.length()+1>MAX_LINE)throw new IOException("CHAT_STREAM_LIMIT");
                if(data.length()>0)data.append('\n');data.append(part);continue;
            }
            if(!row.isEmpty()||data.length()==0)continue;
            String event=data.toString().trim();data.setLength(0);
            if("[DONE]".equals(event)){
                if(requireTerminal&&!terminal)throw new IOException("CHAT_STREAM_INCOMPLETE");
                return;
            }
            JSONObject message=new JSONObject(event);
            if(message.has("error"))throw new IOException("LOCAL_ENGINE_ERROR");
            JSONArray choices=message.optJSONArray("choices");
            if(choices==null||choices.length()==0)continue;
            JSONObject first=choices.getJSONObject(0);
            String reason=first.isNull("finish_reason")?"":first.optString("finish_reason","");
            if(requireTerminal&&terminal)throw new IOException("CHAT_STREAM_INCOMPLETE");
            JSONObject delta=first.optJSONObject("delta");
            if(delta!=null)listener.accept(new JSONObject().put("message",new JSONObject().put("content",delta.optString("content",""))));
            if(LocalChatTurns.gpuTerminal(reason)){
                terminal=true;
                // An explicit model finish reason is authoritative for local chat.
                if(requireTerminal)return;
            }else if(requireTerminal&&!reason.isEmpty())throw new IOException("CHAT_STREAM_INCOMPLETE");
        }
        if(requireTerminal&&!terminal)throw new IOException("CHAT_STREAM_INCOMPLETE");
    }
}
