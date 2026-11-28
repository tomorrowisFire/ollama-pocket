package com.ollamapocket.app;

import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONObject;

/** Strict, bounded text-only accumulator for reviewed structured CPU work.
 * Old engines may omit done_reason or eval_count; malformed supplied metadata is rejected. */
final class StructuredTextStream {
    private final StringBuilder text=new StringBuilder();
    private final int maxChars,maxTokens;
    private boolean done,truncated,invalid;
    private long reportedTokens=-1;
    StructuredTextStream(int maxChars,int maxTokens){
        if(maxChars<=0||maxTokens<=0)throw new IllegalArgumentException("Structured output limits");
        this.maxChars=maxChars;this.maxTokens=maxTokens;
    }
    private static IOException bad(){return new IOException("CHAT_STREAM_INCOMPLETE");}
    void accept(JSONObject item)throws IOException{
        if(invalid)throw bad();
        try{append(item);}catch(IOException|RuntimeException failure){
            invalid=true;
            if(failure instanceof IOException)throw (IOException)failure;
            throw new IOException("CHAT_STREAM_INCOMPLETE",failure);
        }
    }
    private void append(JSONObject item)throws IOException{
        if(done||item==null||item.has("error"))throw bad();
        Object flag=item.opt("done");
        if(item.has("done")&&!(flag instanceof Boolean))throw bad();
        Object raw=item.opt("message");
        if(raw!=null&&raw!=JSONObject.NULL){
            if(!(raw instanceof JSONObject))throw bad();
            JSONObject message=(JSONObject)raw;
            Object value=message.opt("content");
            if(value!=null&&value!=JSONObject.NULL){
                if(!(value instanceof String))throw bad();
                String part=(String)value;
                if((long)text.length()+part.length()>maxChars)throw new IOException("CHAT_STREAM_LIMIT");
                text.append(part);
            }
            Object calls=message.opt("tool_calls");
            if(calls!=null&&calls!=JSONObject.NULL&&(!(calls instanceof JSONArray)||((JSONArray)calls).length()!=0))throw bad();
        }
        if(Boolean.TRUE.equals(flag)){
            Object count=item.opt("eval_count");
            if(count!=null&&count!=JSONObject.NULL){
                if(!(count instanceof Number))throw new IOException("CHAT_STREAM_INVALID_TOKENS");
                double n=((Number)count).doubleValue();
                if(!Double.isFinite(n)||n<0||n>maxTokens||n!=Math.floor(n))throw new IOException("CHAT_STREAM_INVALID_TOKENS");
                reportedTokens=(long)n;
            }
            truncated=StructuredInferenceTerminal.truncated(item.isNull("done_reason")?null:item.opt("done_reason"));
            done=true;
        }
    }
    long reportedTokens()throws IOException{
        if(invalid||!done)throw bad();
        return reportedTokens;
    }
    String text()throws IOException{
        if(invalid||!done)throw bad();
        return text.toString();
    }
    boolean truncated()throws IOException{
        if(invalid||!done)throw bad();
        return truncated;
    }
}
