package com.ollamapocket.app;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;

public final class LocalInferenceStreamTest {
    private static int n;
    private interface Action {void run()throws Exception;}
    private static BufferedReader reader(String data){return new BufferedReader(new StringReader(data));}
    private static void ok(boolean pass){if(!pass)throw new AssertionError("check "+n);n++;}
    private static void rejects(Action a)throws Exception{boolean rejected=false;try{a.run();}catch(IOException expected){rejected=true;}ok(rejected);}
    private static void cancelled(Action a)throws Exception{
        boolean rejected=false;try{a.run();}catch(InterruptedIOException expected){rejected="CHAT_CANCELLED".equals(expected.getMessage());}ok(rejected);
    }
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
        ok(nd("{\"message\":{\"content\":null},\"done\":true}\n").isEmpty());
        ok(sse("data: {\"choices\":[{\"delta\":{\"content\":null},\"finish_reason\":\"stop\"}]}\n\n").isEmpty());
        for(String bad:new String[]{
            "{\"done\":\"true\"}", "{\"done\":null}", "{\"message\":42,\"done\":true}",
            "{\"message\":{\"content\":42},\"done\":true}", "{\"message\":{\"content\":{}},\"done\":true}"
        })rejects(()->LocalInferenceStream.ndjson(reader(bad+"\n"),m->{throw new AssertionError("Malformed callback");},true));
        for(String bad:new String[]{
            "{\"choices\":{}}", "{\"choices\":[1]}", "{\"choices\":[{\"delta\":1,\"finish_reason\":\"stop\"}]}",
            "{\"choices\":[{\"delta\":{\"content\":[]},\"finish_reason\":\"stop\"}]}",
            "{\"choices\":[{\"delta\":{\"content\":\"x\"},\"finish_reason\":1}]}"
        })rejects(()->LocalInferenceStream.sse(reader("data: "+bad+"\n\n"),m->{throw new AssertionError("Malformed callback");},true));
        AtomicBoolean stop=new AtomicBoolean();
        AtomicInteger callbacks=new AtomicInteger();
        cancelled(()->LocalInferenceStream.ndjson(reader("{\"done\":true}\n"),m->callbacks.incrementAndGet(),true,()->true));
        ok(callbacks.get()==0);
        cancelled(()->LocalInferenceStream.ndjson(reader("{\"message\":{\"content\":\"partial\"},\"done\":true}\n"),m->{callbacks.incrementAndGet();stop.set(true);},true,stop::get));
        ok(callbacks.get()==1);
        stop.set(false);
        cancelled(()->LocalInferenceStream.sse(reader(event("partial","stop")),m->{callbacks.incrementAndGet();stop.set(true);},true,stop::get));
        ok(callbacks.get()==2);
        AtomicInteger polls=new AtomicInteger();
        cancelled(()->LocalInferenceStream.ndjson(reader("\n".repeat(100)),m->{throw new AssertionError("blank callback");},true,()->polls.incrementAndGet()>=8));
        polls.set(0);
        cancelled(()->LocalInferenceStream.sse(reader(": keepalive\n\n".repeat(100)),m->{throw new AssertionError("comment callback");},true,()->polls.incrementAndGet()>=8));
        stop.set(false);
        LocalInferenceStream.ndjson(reader("{\"done\":true}\n"),m->callbacks.incrementAndGet(),true,stop::get);
        LocalInferenceStream.sse(reader(event("ok","stop")),m->callbacks.incrementAndGet(),true,stop::get);
        LocalInferenceStream.ndjson(reader("{\"status\":\"pulling\"}\n"),m->callbacks.incrementAndGet(),false);
        ok(callbacks.get()==5);
        AtomicInteger pullEvents=new AtomicInteger();
        LocalInferenceStream.pull(reader("{\"status\":\"pulling manifest\"}\n{\"status\":\"pulling layer\",\"total\":100,\"completed\":100}\n{\"status\":\"success\"}\n"),m->pullEvents.incrementAndGet(),()->false);
        ok(pullEvents.get()==3);
        for(String bad:new String[]{"","{\"status\":\"pulling\"}\n","{\"status\":\"Success\"}\n",
            "{\"status\":\"writing manifest\"}\n","{\"status\":true}\n","{}\n","{\"status\":\"\",\"total\":0}\n",
            "{\"status\":\"success\",\"error\":\"bad\"}\n","{\"status\":\"pulling\",\"total\":\"100\"}\n",
            "{\"status\":\"pulling\",\"completed\":-1}\n","{\"status\":\"pulling\",\"total\":1.5}\n",
            "{\"status\":\"pulling\",\"total\":9223372036854775808}\n","not JSON\n"})
            rejects(()->LocalInferenceStream.pull(reader(bad),m->{},()->false));
        cancelled(()->LocalInferenceStream.pull(reader("{\"status\":\"success\"}\n"),m->{throw new AssertionError("Cancelled pull callback");},()->true));
        AtomicBoolean pullStopped=new AtomicBoolean();
        cancelled(()->LocalInferenceStream.pull(reader("{\"status\":\"success\"}\n"),m->pullStopped.set(true),pullStopped::get));
        for(String bad:new String[]{
            "{\"message\":{\"content\":\"ok\",\"tool_calls\":[{}]},\"done\":true}",
            "{\"message\":{\"content\":\"ok\",\"function_call\":{}},\"done\":true}",
            "{\"message\":{\"content\":\"ok\",\"refusal\":\"no\"},\"done\":true}",
            "{\"done\":true,\"done_reason\":\"load\"}",
            "{\"done\":true,\"done_reason\":1}",
            "{\"message\":{\"content\":\"\\uD800\"},\"done\":true}",
            "{\"message\":{\"content\":\"\\u0000\"},\"done\":true}",
            "not JSON"
        })rejects(()->LocalInferenceStream.ndjson(reader(bad+"\n"),m->{throw new AssertionError("Bad callback");},true));
        ok(nd("{\"message\":{\"content\":\"ok\",\"tool_calls\":[]},\"done\":true,\"done_reason\":\"length\"}\n").equals("ok"));
        for(String bad:new String[]{
            "{\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"},{\"delta\":{}}]}",
            "{\"choices\":[{\"index\":1,\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}",
            "{\"choices\":[{\"index\":\"0\",\"finish_reason\":\"stop\"}]}",
            "{\"choices\":[{\"delta\":{\"tool_calls\":[{}]},\"finish_reason\":\"stop\"}]}",
            "{\"choices\":[{\"delta\":{\"content\":\"ok\",\"refusal\":\"no\"},\"finish_reason\":\"stop\"}]}",
            "{\"choices\":[{\"delta\":{\"content\":\"ok\"},\"finish_reason\":\"tool_calls\"}]}",
            "not JSON"
        })rejects(()->LocalInferenceStream.sse(reader("data: "+bad+"\n\n"),m->{throw new AssertionError("Bad callback");},true));
        byte[] malformed={(byte)0xc3,0x28};
        rejects(()->LocalInferenceStream.line(LocalInferenceStream.strictUtf8(new java.io.ByteArrayInputStream(malformed)),16));
        ok(LocalInferenceStream.line(LocalInferenceStream.strictUtf8(new java.io.ByteArrayInputStream("한글😀\n".getBytes(java.nio.charset.StandardCharsets.UTF_8))),16).equals("한글😀"));
        System.out.println("PASS: "+n+" local inference and model pull protocol assertions");
    }
}
