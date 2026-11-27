package com.ollamapocket.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

public final class McpReplyPolicyTest {
    private static int checks;
    private static final String OK="{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"한국어😀\"}]}}";
    private interface Action{void run()throws Exception;}
    private static void ok(boolean pass){checks++;if(!pass)throw new AssertionError("check "+checks);}
    private static void reject(Action a)throws Exception{
        checks++;try{a.run();}catch(IOException|IllegalArgumentException|org.json.JSONException expected){return;}
        throw new AssertionError("Invalid reply accepted");
    }
    private static McpReplyPolicy.Reply read(String text,String mime)throws Exception{
        return McpReplyPolicy.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),mime,7,true,()->false);
    }
    public static void main(String[] args)throws Exception{
        ok(read(OK,"Application/JSON; charset=UTF-8").kind==McpReplyPolicy.Kind.RESULT);
        ok(read(OK.replace("\"content\"","\"isError\":true,\"content\""),"application/json").kind==McpReplyPolicy.Kind.TOOL_ERROR);
        ok(read("{\"jsonrpc\":\"2.0\",\"id\":7,\"error\":{\"code\":-32602,\"message\":\"error\"}}","application/json").kind==McpReplyPolicy.Kind.RPC_ERROR);
        for(String eol:new String[]{"\n","\r\n","\r"}){
            String sse=": heartbeat"+eol+"event: message"+eol+"data: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\",\"params\":{}}"+eol+eol+
                "id: 1"+eol+"data: "+OK+eol+eol;
            ok(read(sse,"text/event-stream").kind==McpReplyPolicy.Kind.RESULT);
        }
        ok(read("data: {\"jsonrpc\":\"2.0\",\"id\":7,"+"\n"+"data: \"result\":{}}\n\n","text/event-stream").kind==McpReplyPolicy.Kind.RESULT);
        for(String bad:new String[]{OK.replace("\"id\":7","\"id\":8"),OK.replace("\"id\":7","\"id\":\"7\""),
                OK.replace("\"id\":7","\"id\":7.0"),OK.replace("\"id\":7","\"id\":7,\"\\u0069d\":7"),
                OK.replace("\"2.0\"","2.0"),OK.replace("\"result\"","\"extra\":0,\"result\""),
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":null}","{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{},\"error\":{}}",
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{}}",
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"isError\":\"true\"}}",
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"error\":{\"code\":-1.0,\"message\":\"error\"}}",
                OK+"{}",OK.replace("\"text\":\"한국어😀\"","\"text\":\"\\uD800\"")})
            reject(()->read(bad,"application/json"));
        reject(()->read(OK,"text/html"));
        reject(()->read("data: "+OK+"\n","text/event-stream"));
        reject(()->read("data: "+OK,"text/event-stream"));
        reject(()->read("data: {\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"sampling/createMessage\"}\n\n"+"data: "+OK+"\n\n","text/event-stream"));
        reject(()->read(("data: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}\n\n").repeat(64)+"data: "+OK+"\n\n","text/event-stream"));
        reject(()->read(":comment\n".repeat(4096)+"data: "+OK+"\n\n","text/event-stream"));
        byte[] malformed={123,34,120,34,58,34,(byte)0xc3,40,34,125};
        reject(()->McpReplyPolicy.read(new ByteArrayInputStream(malformed),"application/json",7,true,()->false));
        String large=OK.replace("한국어😀","x".repeat(17000));
        ok(read(large,"application/json").kind==McpReplyPolicy.Kind.RESULT);
        reject(()->ToolJsonPolicy.requireObject(large)); // old argument cap is unchanged
        reject(()->read(OK.replace("한국어😀","x".repeat(McpReplyPolicy.MAX_BYTES)),"application/json"));
        AtomicLong clock=new AtomicLong();
        reject(()->McpReplyPolicy.read(new ByteArrayInputStream(OK.getBytes(StandardCharsets.UTF_8)),"application/json",7,true,()->false,
                ()->clock.getAndAdd(McpReplyPolicy.MAX_NANOS)));
        AtomicInteger polls=new AtomicInteger();
        reject(()->McpReplyPolicy.read(new ByteArrayInputStream(OK.getBytes(StandardCharsets.UTF_8)),"text/event-stream",7,true,()->polls.incrementAndGet()>4));
        for(int split=1;split<=OK.length();split++){
            final int chunk=split;
            byte[] bytes=OK.getBytes(StandardCharsets.UTF_8);
            InputStream in=new ByteArrayInputStream(bytes){
                @Override public synchronized int read(byte[] b,int off,int len){return super.read(b,off,Math.min(len,chunk));}
            };
            ok(McpReplyPolicy.read(in,"application/json",7,true,()->false).kind==McpReplyPolicy.Kind.RESULT);
        }
        System.out.println("PASS: "+checks+" bounded inert MCP JSON-RPC/SSE response checks");
    }
}
