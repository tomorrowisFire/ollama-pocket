package com.ollamapocket.app;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.IOException;
import org.json.JSONObject;

public final class LocalInferenceStreamTest {
    private static int n;
    private interface Action {void run()throws Exception;}
    private static BufferedReader reader(String data){return new BufferedReader(new StringReader(data));}
    private static void ok(boolean pass){if(!pass)throw new AssertionError("check "+n);n++;}
    private static void rejects(Action a)throws Exception{boolean rejected=false;try{a.run();}catch(IOException expected){rejected=true;}ok(rejected);}
    private static String nd(String data)throws Exception{
        StringBuilder out=new StringBuilder();
        LocalInferenceStream.ndjson(reader(data),item->{JSONObject m=item.optJSONObject("message");if(m!=null)out.append(m.optString("content"));},true);
        return out.toString();
    }
    private static String sse(String data)throws Exception{
        StringBuilder out=new StringBuilder();
        LocalInferenceStream.sse(reader(data),item->out.append(item.getJSONObject("message").optString("content")),true);
        return out.toString();
    }
    private static String event(String content,String reason)throws Exception{
        JSONObject choice=new JSONObject().put("delta",new JSONObject().put("content",content)).put("finish_reason",reason==null?JSONObject.NULL:reason);
        return "data:"+new JSONObject().put("choices",new org.json.JSONArray().put(choice))+"\r\n\r\n";
    }
    public static void main(String[] args)throws Exception{
        ok("a".equals(LocalInferenceStream.line(reader("a\r\n"),1)));
        ok("a".equals(LocalInferenceStream.line(reader("a"),1)));
        rejects(()->LocalInferenceStream.line(reader("ab"),1));
        ok(nd("{\"message\":{\"content\":\"한국어\"}}\r\n{\"done\":true}\r\n").equals("한국어"));
        rejects(()->nd("{\"message\":{\"content\":\"partial\"}}\n"));
        rejects(()->nd("{\"done\":\"true\"}\n"));
        rejects(()->nd("{\"error\":\"private message\"}\n"));
        ok(sse(": comment\r\n\r\n"+event("a",null)+event("b","stop")).equals("ab"));
        ok(sse(event("limited","length")).equals("limited"));
        rejects(()->sse(event("partial",null)+"data: [DONE]\n\n"));
        rejects(()->sse(event("partial",null)));
        rejects(()->sse(event("partial","tool_calls")));
        rejects(()->sse("data: {\"error\":\"private message\"}\n\n"));
        ok(sse("data: {\"choices\":[\n"+"data: {\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}\n\n").equals("ok"));
        rejects(()->sse("data:"+("x".repeat(LocalInferenceStream.MAX_LINE+1))+"\n\n"));
        System.out.println("PASS: "+n+" local inference protocol assertions");
    }
}
