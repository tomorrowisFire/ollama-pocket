package com.ollamapocket.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicitly approved, trusted private-LAN MCP. No autonomous tool use, OAuth or remote hosts. */
final class McpLanClient {
    static final String VERSION="2025-11-25";
    private final String endpoint;
    private volatile HttpURLConnection active;
    private volatile boolean closed;
    private String session="";
    private int next=1;
    private final Map<String,Tool> catalog=new LinkedHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String,String> permissions=new java.util.concurrent.ConcurrentHashMap<>();
    interface ConnectionFactory {HttpURLConnection open(String endpoint)throws IOException;}
    private final ConnectionFactory connections;
    private final ToolCallApproval approvals;
    McpLanClient(String address){this(address,target->(HttpURLConnection)new URL(target).openConnection(Proxy.NO_PROXY));}
    // Host fixture only; production always uses the fixed validated private-LAN endpoint.
    McpLanClient(String address,ConnectionFactory connections){
        endpoint=CloudApi.remoteBase(address)+"/mcp";
        this.connections=java.util.Objects.requireNonNull(connections);
        approvals=new ToolCallApproval(endpoint,ToolCallApproval.MAX_ATTEMPTS);
    }
    String endpoint(){return endpoint;}
    static final class Tool {
        final String name,description,schema,identity;
        final boolean eligible;
        final JSONObject inputSchema;
        Tool(JSONObject t)throws Exception{
            if(!(t.opt("name") instanceof String))throw new IOException("Invalid tool name");
            name=t.getString("name");if(!name.matches("[A-Za-z0-9_.-]{1,128}"))throw new IOException("Invalid tool name");
            description=t.optString("description","");if(description.length()>4000)throw new IOException("Tool description limit");
            JSONObject s=t.optJSONObject("inputSchema");if(s==null)throw new IOException("Missing schema");
            inputSchema=s;schema=s.toString(2);if(schema.length()>16000)throw new IOException("Schema limit");
            JSONObject a=t.optJSONObject("annotations"),execution=t.optJSONObject("execution");
            eligible=a!=null&&Boolean.TRUE.equals(a.opt("readOnlyHint"))&&!Boolean.TRUE.equals(a.opt("destructiveHint"))&&
                (execution==null||!"required".equals(execution.optString("taskSupport")))&&McpInputPolicy.supportsObject(inputSchema);
            identity=t.toString();if(identity.length()>ToolCallApproval.MAX_IDENTITY_CHARS)throw new IOException("Tool identity limit");
        }
    }
    boolean permitted(Tool tool){return !closed&&tool!=null&&tool.identity.equals(permissions.get(tool.name));}
    int remaining(){return approvals.remaining();}
    ToolCallApproval.Review prepareReview(Tool tool,String raw){
        if(closed)throw new IllegalStateException("Closed MCP session");
        return approvals.prepare(tool==null?null:tool.name,tool==null?null:tool.identity,raw,validArguments(tool,raw));
    }
    boolean approve(ToolCallApproval.Review review,Tool tool,String raw){
        if(closed||!validArguments(tool,raw)||!approvals.approveExact(review,endpoint,tool.name,tool.identity,raw))return false;
        permissions.put(tool.name,tool.identity);return true;
    }
    void decline(ToolCallApproval.Review review){approvals.decline(review);}
    void revoke(Tool tool){
        if(tool!=null)permissions.remove(tool.name);
        approvals.revoke();
        if(approvals.inFlight())cancel();
    }
    boolean validArguments(Tool tool,String raw){try{return !closed&&tool!=null&&tool.eligible&&McpInputPolicy.valid(tool.inputSchema,McpInputPolicy.arguments(raw));}catch(Exception e){return false;}}
    void cancel(){closed=true;approvals.close();permissions.clear();HttpURLConnection c=active;if(c!=null)c.disconnect();}
    synchronized List<Tool> connect()throws Exception{
        JSONObject init=call("initialize",new JSONObject().put("protocolVersion",VERSION).put("capabilities",new JSONObject())
            .put("clientInfo",new JSONObject().put("name","OnHand AI").put("version","0.12")));
        if(!VERSION.equals(init.optString("protocolVersion"))||init.optJSONObject("capabilities")==null||
            init.getJSONObject("capabilities").optJSONObject("tools")==null)throw new IOException("Unsupported MCP protocol or tools");
        notifyInitialized();
        return refresh();
    }
    private List<Tool> refresh()throws Exception{
        Map<String,Tool> found=new LinkedHashMap<>();String cursor="";
        for(int page=0;page<4;page++){
            JSONObject params=new JSONObject();if(!cursor.isEmpty())params.put("cursor",cursor);
            JSONObject result=call("tools/list",params);JSONArray list=result.optJSONArray("tools");
            if(list==null)throw new IOException("Invalid catalog");
            for(int i=0;i<list.length();i++){
                Tool t=new Tool(list.getJSONObject(i));
                if(found.size()>=64||found.putIfAbsent(t.name,t)!=null)throw new IOException("Catalog limit or duplicate");
            }
            cursor=result.optString("nextCursor","");
            if(cursor.isEmpty()){catalog.clear();catalog.putAll(found);return new ArrayList<>(found.values());}
            if(cursor.length()>2000)throw new IOException("Cursor limit");
        }
        throw new IOException("Catalog pagination limit");
    }
    synchronized JSONObject invoke(ToolCallApproval.Review review,Tool reviewed,String rawArgs)throws Exception{
        if(closed||!permitted(reviewed)||!validArguments(reviewed,rawArgs)||!approvals.approvedForDispatch(review,endpoint,reviewed.name,reviewed.identity,rawArgs)){
            approvals.decline(review);throw new IOException("Missing reviewed tool permission");
        }
        try{
            JSONObject args=McpInputPolicy.arguments(rawArgs);
            refresh();Tool now=catalog.get(reviewed.name);
            if(now==null||!now.eligible||!now.identity.equals(reviewed.identity)||!permitted(now)||!McpInputPolicy.valid(now.inputSchema,args)){
                revoke(reviewed);throw new IOException("Tool changed; review again");
            }
            ToolCallApproval.Lease lease=approvals.begin(review,endpoint,now.name,now.identity,rawArgs,permitted(now)&&!closed);
            if(lease==null)throw new IOException("Tool approval expired or changed");
            try(ToolCallApproval.Lease owned=lease){
                if(closed||!permitted(now))throw new IOException("Tool permission revoked");
                // One explicitly reviewed request. Never refund or replay an uncertain call.
                return call("tools/call",new JSONObject().put("name",now.name).put("arguments",args));
            }
        }finally{approvals.decline(review);}
    }
    private JSONObject call(String method,JSONObject params)throws Exception{
        int id=next++;
        return exchange(new JSONObject().put("jsonrpc","2.0").put("id",id).put("method",method).put("params",params),id);
    }
    private void notifyInitialized()throws Exception{
        exchange(new JSONObject().put("jsonrpc","2.0").put("method","notifications/initialized"),-1);
    }
    private JSONObject exchange(JSONObject request,int id)throws Exception{
        if(closed||Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();
        HttpURLConnection c=connections.open(endpoint);
        active=c;
        try{
            if(closed)throw new java.io.InterruptedIOException();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(5000);c.setReadTimeout(20_000);c.setUseCaches(false);
            c.setRequestMethod("POST");c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json, text/event-stream");
            if(!"initialize".equals(request.getString("method")))c.setRequestProperty("MCP-Protocol-Version",VERSION);
            if(!session.isEmpty())c.setRequestProperty("Mcp-Session-Id",session);
            byte[] payload=request.toString().getBytes(StandardCharsets.UTF_8);
            if(payload.length>128*1024)throw new IOException("MCP request limit");
            c.setFixedLengthStreamingMode(payload.length);
            try(java.io.OutputStream out=c.getOutputStream()){out.write(payload);}
            int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("MCP HTTP "+code);
            String token=c.getHeaderField("Mcp-Session-Id");
            if(token!=null){if(!token.matches("[!-~]{1,512}")||(!session.isEmpty()&&!session.equals(token)))throw new IOException("Invalid MCP session");session=token;}
            if(id<0){if(code!=202&&code!=204)throw new IOException("Invalid notification response");return new JSONObject();}
            try(InputStream input=c.getInputStream()){
                McpReplyPolicy.Reply reply=McpReplyPolicy.read(input,c.getContentType(),id,
                    "tools/call".equals(request.getString("method")),()->closed);
                if(reply.kind==McpReplyPolicy.Kind.RPC_ERROR)throw new IOException("MCP RPC rejected");
                return reply.payload;
            }
        }finally{active=null;c.disconnect();}
    }
}
