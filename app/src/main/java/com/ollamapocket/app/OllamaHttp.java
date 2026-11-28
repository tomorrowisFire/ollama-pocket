package com.ollamapocket.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

/** API requests never leave the device. */
public final class OllamaHttp {
    private static final String BASE = "http://127.0.0.1:11434";
    public interface ChunkListener { void onChunk(JSONObject item) throws Exception; }
    private OllamaHttp() { }

    private static HttpURLConnection open(String path, String method, int readTimeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE + path).openConnection(java.net.Proxy.NO_PROXY);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod(method);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(readTimeoutMs);
        conn.setUseCaches(false);
        conn.setRequestProperty("Accept", "application/json");
        if (!"GET".equals(method)) {
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
        }
        return conn;
    }

    public static JSONObject get(String path) throws Exception {
        HttpURLConnection conn = open(path, "GET", 6000);
        try { return readSingle(conn); } finally { conn.disconnect(); }
    }

    /** A short, aggregate handoff budget avoids an entire cold-start timeout per resident model.
     * Network timeouts bound individual blocking operations; check elapsed time again after each response. */
    private static int releaseTimeout(long started,BooleanSupplier cancelled)throws IOException{
        if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("CHAT_CANCELLED");
        int timeout=EngineTransitionPolicy.releaseTimeoutMs(started,android.os.SystemClock.elapsedRealtime());
        if(timeout==0)throw new java.net.SocketTimeoutException("Resident model release deadline");
        return timeout;
    }
    private static JSONObject releaseRequest(String path,JSONObject body,long started,BooleanSupplier cancelled)throws Exception{
        int timeout=releaseTimeout(started,cancelled);
        HttpURLConnection conn=open(path,body==null?"GET":"POST",timeout);
        conn.setConnectTimeout(Math.min(timeout,1000));
        try{
            if(body!=null)write(conn,body);
            JSONObject result=readSingle(conn);
            releaseTimeout(started,cancelled);
            return result;
        }finally{conn.disconnect();}
    }
    /** Release only model allocations in this app's loopback Ollama daemon. */
    public static void releaseResidentModels(BooleanSupplier cancelled)throws Exception{
        long started=android.os.SystemClock.elapsedRealtime();
        releaseTimeout(started,cancelled);
        if(!OllamaForegroundService.isRunning())return;
        JSONArray models=releaseRequest("/api/ps",null,started,cancelled).optJSONArray("models");
        if(models==null||models.length()>128)throw new IOException("Invalid resident model list");
        // Validate the complete snapshot before performing any unload, and deduplicate names.
        java.util.LinkedHashSet<String> names=new java.util.LinkedHashSet<>();
        for(int i=0;i<models.length();i++){
            JSONObject row=models.optJSONObject(i);
            String name=row==null?"":row.optString("name",row.optString("model",""));
            if(name.isEmpty()||name.length()>256)throw new IOException("Invalid resident model name");
            names.add(name);
        }
        for(String name:names)
            releaseRequest("/api/generate",new JSONObject().put("model",name).put("keep_alive",0).put("stream",false),started,cancelled);
        for(int i=0;i<8;i++){
            JSONArray left=releaseRequest("/api/ps",null,started,cancelled).optJSONArray("models");
            if(left!=null&&left.length()==0)return;
            Thread.sleep(Math.min(250,releaseTimeout(started,cancelled)));
        }
        throw new IOException("Resident models did not unload");
    }

    public static JSONObject post(String path, JSONObject body, String method) throws Exception {
        HttpURLConnection conn = open(path, method, 20000);
        try {
            write(conn, body);
            return readSingle(conn);
        } finally { conn.disconnect(); }
    }

    private static void write(HttpURLConnection conn, JSONObject json) throws Exception {
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = conn.getOutputStream()) { out.write(bytes); }
    }

    private static InputStream checkedStream(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            InputStream err = conn.getErrorStream();
            String message = err == null ? "" : readText(err, 8192);
            throw new IOException("Ollama HTTP " + code + ": " + message);
        }
        return conn.getInputStream();
    }

    private static JSONObject readSingle(HttpURLConnection conn) throws Exception {
        InputStream input = checkedStream(conn);
        String payload = readText(input, 4_000_000).trim();
        return payload.isEmpty() ? new JSONObject() : new JSONObject(payload);
    }

    private static String readText(InputStream input, int maxChars) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (sb.length() + count > maxChars) throw new IOException("Response too large");
                sb.append(buffer, 0, count);
            }
            return sb.toString();
        }
    }

    /** Caller may cancel the request via connection.disconnect() on another thread. */
    public static void stream(HttpURLConnection conn, JSONObject request, ChunkListener onChunk) throws Exception {
        try {
            write(conn, request);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    checkedStream(conn), StandardCharsets.UTF_8))) {
                LocalInferenceStream.ndjson(reader,onChunk::onChunk,false);
            }
        } finally { conn.disconnect(); }
    }

    /** Only interactive chat requires a normal terminal marker. Pulls and diagnostics keep their existing API. */
    public static void streamChat(HttpURLConnection connection,JSONObject request,ChunkListener listener)throws Exception{
        streamChat(connection,request,listener,()->false);
    }
    public static void streamChat(HttpURLConnection connection,JSONObject request,ChunkListener listener,BooleanSupplier cancelled)throws Exception{
        try{
            LocalStreamCancellation.check(cancelled);
            write(connection,request);
            LocalStreamCancellation.check(cancelled);
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(checkedStream(connection),StandardCharsets.UTF_8))){
                LocalInferenceStream.ndjson(reader,listener::onChunk,true,cancelled);
            }
            LocalStreamCancellation.check(cancelled);
        }finally{connection.disconnect();}
    }
    public static HttpURLConnection newGpuChatConnection() throws Exception {
        HttpURLConnection conn = (HttpURLConnection)new URL(GpuRunner.url() + "/v1/chat/completions").openConnection(java.net.Proxy.NO_PROXY);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(300_000);
        conn.setUseCaches(false);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "text/event-stream");
        return conn;
    }

    public static HttpURLConnection newCooperativeChatConnection() throws Exception {
        HttpURLConnection conn = (HttpURLConnection)new URL(DistributedRunner.url() + "/v1/chat/completions").openConnection(java.net.Proxy.NO_PROXY);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(300_000);
        conn.setUseCaches(false);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "text/event-stream");
        return conn;
    }

    public static JSONObject gpuChatBody(String model, JSONArray history) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", new JSONArray(history.toString()));
        body.put("stream", true);
        body.put("max_tokens", 1024);
        return body;
    }

    /** OpenAI compatible SSE returned by the local Vulkan llama.cpp server. */
    public static void streamGpu(HttpURLConnection connection,JSONObject request,ChunkListener onChunk)throws Exception{
        streamGpuInternal(connection,request,onChunk,false,null);
    }
    public static void streamGpuChat(HttpURLConnection connection,JSONObject request,ChunkListener onChunk)throws Exception{
        streamGpuChat(connection,request,onChunk,()->false);
    }
    public static void streamGpuChat(HttpURLConnection connection,JSONObject request,ChunkListener onChunk,BooleanSupplier cancelled)throws Exception{
        streamGpuInternal(connection,request,onChunk,true,cancelled);
    }
    private static void streamGpuInternal(HttpURLConnection connection,JSONObject request,ChunkListener onChunk,boolean requireTerminal,BooleanSupplier cancelled)throws Exception{
        try{
            LocalStreamCancellation.check(cancelled);
            write(connection,request);
            LocalStreamCancellation.check(cancelled);
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(checkedStream(connection),StandardCharsets.UTF_8))){
                LocalInferenceStream.sse(reader,onChunk::onChunk,requireTerminal,cancelled);
            }
            LocalStreamCancellation.check(cancelled);
        }finally{connection.disconnect();}
    }

    public static HttpURLConnection newChatConnection() throws Exception { return open("/api/chat", "POST", 300_000); }
    public static HttpURLConnection newPullConnection() throws Exception { return open("/api/pull", "POST", 0); }

    public static JSONObject chatBody(String model, JSONArray history) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", new JSONArray(history.toString()));
        body.put("stream", true);
        body.put("keep_alive", "5m");
        JSONObject options = new JSONObject();
        options.put("num_ctx", 2048);
        options.put("num_gpu", 0); // Current packaged runner is CPU-only; never silently report GPU use.
        options.put("num_thread", Math.max(1, Math.min(6, Runtime.getRuntime().availableProcessors())));
        body.put("options", options);
        return body;
    }

    public static JSONObject pullBody(String model) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("stream", true);
        return body;
    }
}
