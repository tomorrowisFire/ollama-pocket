package com.ollamapocket.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
    McpLanClient(String address){endpoint=CloudApi.remoteBase(address)+"/mcp";}
    String endpoint(){return endpoint;}
    static final class Tool {
        final String name,description,schema,identity;
        final boolean eligible;
        final JSONObject inputSchema;
        Tool(JSONObject t)throws Exception{
            name=t.getString("name");if(!name.matches("[A-Za-z0-9_.-]{1,128}"))throw new IOException("Invalid tool name");
            description=t.optString("description","");if(description.length()>4000)throw new IOException("Tool description limit");
            JSONObject s=t.optJSONObject("inputSchema");if(s==null)throw new IOException("Missing schema");
            inputSchema=s;schema=s.toString(2);if(schema.length()>16000)throw new IOException("Schema limit");
            JSONObject a=t.optJSONObject("annotations"),execution=t.optJSONObject("execution");
            eligible=a!=null&&Boolean.TRUE.equals(a.opt("readOnlyHint"))&&!Boolean.TRUE.equals(a.opt("destructiveHint"))&&
                (execution==null||!"required".equals(execution.optString("taskSupport")))&&McpInputPolicy.supportsObject(inputSchema);
            identity=t.toString();
        }
    }
    boolean permitted(Tool tool){return !closed&&tool!=null&&tool.identity.equals(permissions.get(tool.name));}
    void authorize(Tool tool){if(closed||tool==null||!tool.eligible)throw new IllegalArgumentException("Tool not allowed");permissions.put(tool.name,tool.identity);}
    void revoke(Tool tool){if(tool!=null)permissions.remove(tool.name);}
    boolean validArguments(Tool tool,String raw){try{return tool!=null&&tool.eligible&&McpInputPolicy.valid(tool.inputSchema,McpInputPolicy.arguments(raw));}catch(Exception e){return false;}}
    void cancel(){closed=true;permissions.clear();HttpURLConnection c=active;if(c!=null)c.disconnect();}
    List<Tool> connect()throws Exception{
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
    JSONObject invoke(Tool reviewed,String rawArgs)throws Exception{
        if(reviewed==null||!reviewed.eligible||rawArgs==null||rawArgs.length()>16000)throw new IOException("Tool not allowed");
        if(!permitted(reviewed)||!validArguments(reviewed,rawArgs))throw new IOException("Missing permission or invalid arguments");
        JSONObject args=McpInputPolicy.arguments(rawArgs);
        refresh();Tool now=catalog.get(reviewed.name);
        if(now==null||!now.eligible||!now.identity.equals(reviewed.identity)||!permitted(now)||!McpInputPolicy.valid(now.inputSchema,args)){revoke(reviewed);throw new IOException("Tool changed; review again");}
        // All arguments are entered and approved by the user. No chat/model access.
        return call("tools/call",new JSONObject().put("name",now.name).put("arguments",args));
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
        HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection(Proxy.NO_PROXY);
        active=c;
        try{
            if(closed)throw new java.io.InterruptedIOException();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(5000);c.setReadTimeout(20_000);c.setUseCaches(false);
            c.setRequestMethod("POST");c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json, text/event-stream");
            if(!"initialize".equals(request.getString("method")))c.setRequestProperty("MCP-Protocol-Version",VERSION);
            if(!session.isEmpty())c.setRequestProperty("Mcp-Session-Id",session);
            byte[] payload=request.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(payload.length);
            try(java.io.OutputStream out=c.getOutputStream()){out.write(payload);}
            int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("MCP HTTP "+code);
            String token=c.getHeaderField("Mcp-Session-Id");
            if(token!=null){if(!token.matches("[!-~]{1,512}")||(!session.isEmpty()&&!session.equals(token)))throw new IOException("Invalid MCP session");session=token;}
            if(id<0){if(code!=202&&code!=204)throw new IOException("Invalid notification response");return new JSONObject();}
            String mime=c.getContentType();
            if(mime==null)throw new IOException("Missing content type");
            mime=mime.toLowerCase(java.util.Locale.ROOT).split(";",2)[0].trim();
            try(InputStream input=c.getInputStream()){
                JSONObject response;
                if("application/json".equals(mime))response=new JSONObject(readBounded(input));
                else if("text/event-stream".equals(mime))response=readSse(input,id);
                else throw new IOException("Unsupported MCP response");
                if(!"2.0".equals(response.optString("jsonrpc"))||response.optInt("id",-1)!=id||
                    !response.isNull("error")||response.optJSONObject("result")==null)throw new IOException("MCP request rejected");
                return response.getJSONObject("result");
            }
        }finally{active=null;c.disconnect();}
    }
    private String readBounded(InputStream input)throws Exception{
        InputStreamReader reader=new InputStreamReader(input,StandardCharsets.UTF_8);
        StringBuilder s=new StringBuilder();char[] buf=new char[2048];long deadline=System.nanoTime()+30_000_000_000L;int n;
        while((n=reader.read(buf))!=-1){if(closed||System.nanoTime()-deadline>=0||s.length()+n>524288)throw new IOException("MCP response limit");s.append(buf,0,n);}
        return s.toString();
    }
    private JSONObject readSse(InputStream input,int id)throws Exception{
        InputStreamReader reader=new InputStreamReader(input,StandardCharsets.UTF_8);
        StringBuilder line=new StringBuilder(),data=new StringBuilder();int ch,count=0;long deadline=System.nanoTime()+30_000_000_000L;
        while((ch=reader.read())!=-1){
            if(closed||++count>524288||System.nanoTime()-deadline>=0)throw new IOException("MCP SSE limit");
            if(ch=='\r')continue;
            if(ch!='\n'){line.append((char)ch);continue;}
            String row=line.toString();line.setLength(0);
            if(row.startsWith("data:")){if(data.length()>0)data.append('\n');String part=row.substring(5);data.append(part.startsWith(" ")?part.substring(1):part);}
            else if(row.isEmpty()&&data.length()>0){
                JSONObject message=new JSONObject(data.toString());data.setLength(0);
                if(message.has("method")&&message.has("id"))throw new IOException("Server requests unsupported");
                if(message.optInt("id",-1)==id)return message;
            }
        }
        throw new IOException("Incomplete MCP response; no automatic replay");
    }
}
