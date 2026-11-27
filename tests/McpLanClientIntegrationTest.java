package com.ollamapocket.app;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONObject;

/** Actual manual MCP client path, entirely in memory; no socket or external tool. */
public final class McpLanClientIntegrationTest {
    private static int checks;
    private interface Action{void run()throws Exception;}
    private static void ok(boolean pass){checks++;if(!pass)throw new AssertionError("check "+checks);}
    private static void reject(Action a)throws Exception{
        checks++;try{a.run();}catch(IOException|IllegalArgumentException|IllegalStateException expected){return;}
        throw new AssertionError("Unapproved MCP operation");
    }
    private static final String ADDRESS="192.168.1.10:3001";
    private static final String ARGS="{\"q\":\"한글😀\"}";
    private static final String TOOL="{\"name\":\"lookup\",\"description\":\"fixture\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}},\"required\":[\"q\"],\"additionalProperties\":false},\"annotations\":{\"readOnlyHint\":true,\"destructiveHint\":false}}";
    private static final class Server implements McpLanClient.ConnectionFactory{
        int calls,lists;
        boolean changed,rpcError,toolError,sse,wrongId;
        JSONObject last;
        @Override public HttpURLConnection open(String endpoint)throws IOException{
            if(!("http://"+ADDRESS+"/mcp").equals(endpoint))throw new IOException("Wrong endpoint");
            return new HttpURLConnection(new URL(endpoint)){
                final ByteArrayOutputStream sent=new ByteArrayOutputStream();
                String response;
                int status;
                @Override public void connect(){}
                @Override public void disconnect(){}
                @Override public boolean usingProxy(){return false;}
                @Override public OutputStream getOutputStream(){return sent;}
                @Override public int getResponseCode()throws IOException{
                    try{
                        JSONObject req=new JSONObject(sent.toString("UTF-8"));
                        String method=req.getString("method");
                        if("notifications/initialized".equals(method)){status=202;return status;}
                        int id=req.getInt("id");
                        JSONObject body=new JSONObject().put("jsonrpc","2.0").put("id",wrongId?id+1:id);
                        if("initialize".equals(method))body.put("result",new JSONObject().put("protocolVersion",McpLanClient.VERSION).put("capabilities",new JSONObject().put("tools",new JSONObject())));
                        else if("tools/list".equals(method)){
                            lists++;
                            body.put("result",new JSONObject().put("tools",new org.json.JSONArray().put(new JSONObject(changed?TOOL.replace("fixture","changed"):TOOL))));
                        }else if("tools/call".equals(method)){
                            calls++;last=req.getJSONObject("params");
                            if(rpcError)body.put("error",new JSONObject().put("code",-32602).put("message","fixture error"));
                            else body.put("result",new JSONObject().put("isError",toolError).put("content",new org.json.JSONArray()));
                        }else throw new IOException("Unexpected method");
                        response=sse?"data: "+body+"\n\n":body.toString();status=200;return status;
                    }catch(Exception e){throw new IOException(e);}
                }
                @Override public String getContentType(){return sse?"text/event-stream":"application/json";}
                @Override public InputStream getInputStream(){return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));}
            };
        }
    }
    private static ToolCallApproval.Review approve(McpLanClient client,McpLanClient.Tool tool,String args){
        ToolCallApproval.Review review=client.prepareReview(tool,args);
        ok(client.approve(review,tool,args));return review;
    }
    public static void main(String[] args)throws Exception{
        reject(()->new McpLanClient("8.8.8.8:80",new Server()));
        Server s=new Server();McpLanClient c=new McpLanClient(ADDRESS,s);
        List<McpLanClient.Tool> tools=c.connect();McpLanClient.Tool t=tools.get(0);
        ok(tools.size()==1&&t.eligible&&c.remaining()==16);
        ok(!c.validArguments(t,"{\"q\":\"x\",\"\\u0071\":\"y\"}")&&!c.validArguments(t,"{q:\"x\"}")&&!c.validArguments(t,"{}"));
        reject(()->c.invoke(null,t,ARGS));ok(s.calls==0);
        ToolCallApproval.Review unapproved=c.prepareReview(t,ARGS);
        reject(()->c.invoke(unapproved,t,ARGS));ok(s.calls==0&&!c.approve(unapproved,t,ARGS));
        ToolCallApproval.Review altered=c.prepareReview(t,ARGS);
        ok(!c.approve(altered,t,ARGS+" "));
        ToolCallApproval.Review changedArgs=approve(c,t,ARGS);
        reject(()->c.invoke(changedArgs,t,ARGS+" "));ok(s.calls==0&&c.remaining()==16);
        ToolCallApproval.Review once=approve(c,t,ARGS);
        ok(!c.invoke(once,t,ARGS).getBoolean("isError"));
        ok(s.calls==1&&s.last.getJSONObject("arguments").getString("q").equals("한글😀")&&c.remaining()==15);
        reject(()->c.invoke(once,t,ARGS));ok(s.calls==1);
        ToolCallApproval.Review revoked=approve(c,t,ARGS);c.revoke(t);
        reject(()->c.invoke(revoked,t,ARGS));ok(s.calls==1);
        ToolCallApproval.Review changedCatalog=approve(c,t,ARGS);s.changed=true;
        reject(()->c.invoke(changedCatalog,t,ARGS));ok(s.calls==1);
        s.changed=false;s.sse=true;s.toolError=true;
        ok(c.invoke(approve(c,t,ARGS),t,ARGS).getBoolean("isError"));ok(c.remaining()==14);
        s.rpcError=true;
        ToolCallApproval.Review error=approve(c,t,ARGS);
        reject(()->c.invoke(error,t,ARGS));ok(c.remaining()==13&&s.calls==3);
        s.rpcError=false;s.toolError=false;s.sse=false;s.wrongId=true;
        ToolCallApproval.Review badReply=approve(c,t,ARGS);
        reject(()->c.invoke(badReply,t,ARGS));ok(s.calls==3&&c.remaining()==13);
        s.wrongId=false;
        for(int i=0;i<13;i++)c.invoke(approve(c,t,ARGS),t,ARGS);
        ok(c.remaining()==0&&s.calls==16);
        reject(()->c.prepareReview(t,ARGS));c.cancel();
        ok(c.remaining()==0&&!c.validArguments(t,ARGS));
        System.out.println("PASS: "+checks+" integrated manual MCP approval, reply and budget checks (mock transport)");
    }
}
