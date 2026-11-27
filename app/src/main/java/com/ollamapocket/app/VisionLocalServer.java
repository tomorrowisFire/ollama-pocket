package com.ollamapocket.app;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Authenticated, owned, single-purpose loopback llama-server. No cloud or arbitrary media URLs. */
final class VisionLocalServer implements AutoCloseable {
    private static final int MAX_RESPONSE=256*1024;
    private static final java.util.concurrent.atomic.AtomicBoolean OWNER=new java.util.concurrent.atomic.AtomicBoolean();
    private final Process process;
    private final int port;
    private final String secret,generation;
    private final File weights;
    private final BooleanSupplier stopped;
    private volatile HttpURLConnection active;
    private volatile boolean cancelled,verified;
    private final java.util.concurrent.atomic.AtomicBoolean released=new java.util.concurrent.atomic.AtomicBoolean();
    private VisionLocalServer(Process process,int port,String secret,File weights,BooleanSupplier stopped){
        this.process=process;this.port=port;this.secret=secret;this.weights=weights;this.stopped=stopped;
        generation=UUID.randomUUID().toString();
    }
    static boolean busy(){return OWNER.get();}
    static VisionLocalServer start(File executable,File weights,File projector,File directory,
                                   Map<String,String> environment,BooleanSupplier stopped)throws Exception{
        if(executable==null||!executable.isFile()||weights==null||projector==null||directory==null||
                !directory.isDirectory()||stopped==null)
            throw new IOException("Missing visual runtime");
        if(!OWNER.compareAndSet(false,true))throw new IOException("Visual runtime busy");
        VisionLocalServer server=null;
        Process launched=null;
        try{
            VisionImagePolicy.check(stopped);
            if(!VisionModelProfile.WEIGHTS.verify(weights.getParentFile(),stopped).equals(weights)||
                    !VisionModelProfile.PROJECTOR.verify(projector.getParentFile(),stopped).equals(projector))
                throw new IOException("Wrong visual model pair");
            byte[] entropy=new byte[32];new SecureRandom().nextBytes(entropy);
            String key=Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);Arrays.fill(entropy,(byte)0);
            int port;
            try(ServerSocket socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=socket.getLocalPort();}
            ProcessBuilder builder=new ProcessBuilder(executable.getAbsolutePath(),"-m",weights.getAbsolutePath(),
                    "--mmproj",projector.getAbsolutePath(),"--host","127.0.0.1","--port",Integer.toString(port),
                    "-c","2048","-np","1","-t","2","-ngl","0","--no-mmproj-offload",
                    "--alias",VisionModelProfile.ALIAS,"--no-webui","--log-disable");
            builder.directory(directory);builder.redirectErrorStream(true);
            Map<String,String> env=builder.environment();
            if(environment!=null)env.putAll(environment);
            for(String name:new ArrayList<>(env.keySet()))
                if(name.startsWith("LLAMA_ARG_")||name.startsWith("MTMD_")||name.startsWith("GGML_")||
                        name.startsWith("HF_")||name.equals("LLAMA_API_KEY")||
                        Arrays.asList("http_proxy","https_proxy","all_proxy","no_proxy").contains(name.toLowerCase(Locale.ROOT)))env.remove(name);
            env.put("LLAMA_API_KEY",key);
            launched=builder.start();
            Process owned=launched;
            Thread drain=new Thread(()->{
                try(InputStream in=owned.getInputStream()){
                    byte[] buffer=new byte[4096];while(in.read(buffer)>0){}
                }catch(IOException ignored){}
            },"onhand-vision-drain");
            drain.setDaemon(true);drain.start();
            server=new VisionLocalServer(launched,port,key,weights,stopped);
            server.waitReady();return server;
        }catch(Exception|Error failure){
            if(server!=null){
                try{server.close();}catch(Exception cleanup){failure.addSuppressed(cleanup);}
            }else if(launched!=null){
                launched.destroyForcibly();
                if(awaitDeath(launched))OWNER.set(false);
            }else OWNER.set(false);
            throw failure;
        }
    }
    private void check()throws IOException{
        if(cancelled||stopped.getAsBoolean()||Thread.currentThread().isInterrupted()||!process.isAlive())
            throw new InterruptedIOException("Visual runtime unavailable");
    }
    private static final class Response{
        final int status;final byte[] bytes;
        Response(int status,byte[] bytes){this.status=status;this.bytes=bytes;}
        JSONObject json()throws Exception{
            String raw=ReviewedLocalPolicy.utf8(bytes);ToolJsonPolicy.requireReplyObject(raw);return new JSONObject(raw);
        }
    }
    private Response exchange(String path,String auth,byte[] payload,int timeout)throws Exception{
        check();
        if(!Arrays.asList("/health","/props","/v1/chat/completions").contains(path))
            throw new IllegalArgumentException("Visual endpoint");
        HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+port+path).openConnection(Proxy.NO_PROXY);
        active=c;
        try{
            check();c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setConnectTimeout(2000);c.setReadTimeout(timeout);
            c.setRequestMethod(payload==null?"GET":"POST");c.setRequestProperty("Accept","application/json");
            if(auth!=null)c.setRequestProperty("Authorization","Bearer "+auth);
            if(payload!=null){
                if(payload.length<1||payload.length>VisionChatRequest.MAX_REQUEST_BYTES)throw new IOException("Visual request size");
                c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setFixedLengthStreamingMode(payload.length);
                try(OutputStream out=c.getOutputStream()){check();out.write(payload);}
            }
            int status=c.getResponseCode();check();
            if(status!=200)return new Response(status,new byte[0]);
            try(InputStream in=c.getInputStream()){
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];
                for(;;){
                    check();int n=in.read(buffer,0,Math.min(buffer.length,MAX_RESPONSE-out.size()+1));check();
                    if(n<0)break;
                    if(n==0||n>MAX_RESPONSE-out.size())throw new IOException("Visual reply size");
                    out.write(buffer,0,n);
                }
                return new Response(status,out.toByteArray());
            }
        }finally{active=null;c.disconnect();}
    }
    private void waitReady()throws Exception{
        long deadline=System.nanoTime()+180_000_000_000L;
        while(System.nanoTime()-deadline<0){
            check();boolean ready=false;
            try{
                Response health=exchange("/health",null,null,2000);
                ready=health.status==200&&"ok".equals(health.json().opt("status"));
            }catch(InterruptedIOException timeout){check();}
            catch(IOException|org.json.JSONException unavailable){check();}
            if(ready){
                // A random key plus negative-auth checks prevent accepting an unrelated loopback server.
                if(exchange("/props",null,null,2000).status!=401||
                        exchange("/props","invalid-onhand-fixture-credential",null,2000).status!=401)
                    throw new IOException("Visual authentication unavailable");
                Response response=exchange("/props",secret,null,4000);
                if(response.status!=200)throw new IOException("Visual capability request rejected");
                verifyProps(response.json(),weights);
                verified=true;return;
            }
            Thread.sleep(300);
        }
        throw new IOException("Visual runtime startup deadline");
    }
    static void verifyProps(JSONObject props,File weights)throws IOException{
        JSONObject modalities=props.optJSONObject("modalities");
        Object build=props.opt("build_info");
        if(modalities==null||!Boolean.TRUE.equals(modalities.opt("vision"))||
                !weights.getAbsolutePath().equals(props.opt("model_path"))||
                !(build instanceof String)||!((String)build).contains(VisionModelProfile.BACKEND_COMMIT.substring(0,7)))
            throw new IOException("Unverified visual runtime identity");
    }
    VisionTurnReview.Backend backend()throws IOException{
        check();if(!verified)throw new IOException("Visual runtime not verified");
        return new VisionTurnReview.Backend(generation,VisionModelProfile.BACKEND_COMMIT,VisionModelProfile.REVISION,
                VisionModelProfile.WEIGHTS.sha256,VisionModelProfile.WEIGHTS.bytes,
                VisionModelProfile.PROJECTOR.sha256,VisionModelProfile.PROJECTOR.bytes,true);
    }
    /** The caller supplies an explicitly approved, move-once lease. No arbitrary payload API. */
    ReviewedLocalPolicy.Result infer(VisionTurnReview.Lease request,int tokens,BooleanSupplier stop)throws Exception{
        backend();if(request==null||tokens<1||tokens>VisionChatRequest.MAX_TOKENS)
            throw new IllegalArgumentException("Visual request lease");
        byte[] payload=request.takePayload(generation,tokens,stop);
        long started=System.nanoTime();
        java.util.concurrent.ScheduledExecutorService watcher=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{
            Thread t=new Thread(r,"vision-request-watch");t.setDaemon(true);return t;
        });
        try{
            watcher.scheduleAtFixedRate(()->{
                if(stopped.getAsBoolean()||(stop!=null&&stop.getAsBoolean())||System.nanoTime()-started>=180_000_000_000L)cancel();
            },0,1,TimeUnit.SECONDS);
            Response reply=exchange("/v1/chat/completions",secret,payload,120000);
            if(reply.status!=200)throw new IOException("Visual inference rejected");
            VisionImagePolicy.check(stop);check();return parseReply(reply.json(),tokens);
        }finally{watcher.shutdownNow();Arrays.fill(payload,(byte)0);}
    }
    static ReviewedLocalPolicy.Result parseReply(JSONObject reply,int tokens)throws Exception{
        JSONArray choices=reply.optJSONArray("choices");
        if(choices==null||choices.length()!=1||!VisionModelProfile.ALIAS.equals(reply.opt("model")))
            throw new IOException("Visual reply identity");
        JSONObject choice=choices.getJSONObject(0),message=choice.optJSONObject("message"),usage=reply.optJSONObject("usage");
        Object finish=choice.opt("finish_reason"),count=usage==null?null:usage.opt("completion_tokens");
        if(message==null||!(message.opt("content") instanceof String)||message.has("tool_calls")&&!message.isNull("tool_calls")||
                !(count instanceof Integer||count instanceof Long)||((Number)count).longValue()<1||
                ((Number)count).longValue()>tokens||!("stop".equals(finish)||"length".equals(finish)))
            throw new IOException("Visual reply incomplete");
        String answer=ReviewedLocalPolicy.text((String)message.opt("content"),ReviewedLocalPolicy.MAX_OUTPUT);
        return new ReviewedLocalPolicy.Result(answer,"length".equals(finish));
    }
    void cancel(){
        cancelled=true;HttpURLConnection c=active;if(c!=null)c.disconnect();process.destroy();
    }
    boolean alive(){return process.isAlive();}
    private static boolean awaitDeath(Process p){
        boolean interrupted=Thread.interrupted();
        try{
            try{if(p.waitFor(4,TimeUnit.SECONDS))return true;}
            catch(InterruptedException e){interrupted=true;}
            p.destroyForcibly();
            try{return p.waitFor(4,TimeUnit.SECONDS);}
            catch(InterruptedException e){interrupted=true;return !p.isAlive();}
        }finally{if(interrupted)Thread.currentThread().interrupt();}
    }
    @Override public synchronized void close()throws IOException{
        if(released.get())return;
        cancel();
        if(!awaitDeath(process))throw new IOException("Visual process termination unverified");
        if(released.compareAndSet(false,true))OWNER.set(false);
    }
}
