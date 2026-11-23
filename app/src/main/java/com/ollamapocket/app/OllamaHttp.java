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

/** API requests never leave the device. */
public final class OllamaHttp {
    private static final String BASE = "http://127.0.0.1:11434";
    public interface ChunkListener { void onChunk(JSONObject item) throws Exception; }
    private OllamaHttp() { }

    private static HttpURLConnection open(String path, String method, int readTimeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE + path).openConnection();
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
        if (code >= 400) {
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
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        JSONObject message = new JSONObject(line);
                        if (message.has("error")) throw new IOException(message.optString("error"));
                        onChunk.onChunk(message);
                    }
                }
            }
        } finally { conn.disconnect(); }
    }

    public static HttpURLConnection newChatConnection() throws Exception { return open("/api/chat", "POST", 0); }
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
