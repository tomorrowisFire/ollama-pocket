package com.ollamapocket.app;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/** Fixed HTTPS endpoints; no redirects, automatic retries, local history or key persistence. */
final class CloudApi {
    enum Provider {
        OPENAI("OpenAI","https://api.openai.com/v1/responses",0),
        GEMINI("Google Gemini","https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",1),
        CLAUDE("Anthropic Claude","https://api.anthropic.com/v1/messages",2),
        XAI("xAI Grok","https://api.x.ai/v1/responses",0),
        DEEPSEEK("DeepSeek","https://api.deepseek.com/chat/completions",1),
        OPENROUTER("OpenRouter","https://openrouter.ai/api/v1/chat/completions",1);
        final String title,url;final int protocol;
        Provider(String title,String url,int protocol){this.title=title;this.url=url;this.protocol=protocol;}
    }
    static JSONObject request(Provider provider,String model,String text) throws Exception {
        JSONObject body=new JSONObject().put("model",model);
        if(provider.protocol==0){
            body.put("input",new JSONArray().put(new JSONObject().put("role","user").put("content",text)));
            body.put("max_output_tokens",2048).put("store",false);
        }else{
            body.put("messages",new JSONArray().put(new JSONObject().put("role","user").put("content",text)));
            body.put("max_tokens",2048).put("stream",false);
        }
        return body;
    }
    static String parse(Provider provider,JSONObject body) throws Exception {
        StringBuilder out=new StringBuilder();
        if(provider.protocol==0){
            JSONArray rows=body.optJSONArray("output");
            if(rows!=null)for(int i=0;i<rows.length();i++){
                JSONObject row=rows.optJSONObject(i);
                if(row==null||!"message".equals(row.optString("type")))continue;
                JSONArray parts=row.optJSONArray("content");
                if(parts!=null)for(int j=0;j<parts.length();j++){
                    JSONObject part=parts.optJSONObject(j);
                    if(part!=null&&"output_text".equals(part.optString("type")))out.append(part.optString("text",""));
                }
            }
        }else if(provider.protocol==2){
            JSONArray parts=body.optJSONArray("content");
            if(parts!=null)for(int i=0;i<parts.length();i++){
                JSONObject part=parts.optJSONObject(i);
                if(part!=null&&"text".equals(part.optString("type")))out.append(part.optString("text",""));
            }
        }else{
            JSONArray choices=body.optJSONArray("choices");
            JSONObject choice=choices==null?null:choices.optJSONObject(0);
            JSONObject message=choice==null?null:choice.optJSONObject("message");
            if(message!=null&&!message.isNull("content"))out.append(message.optString("content",""));
        }
        if(out.toString().trim().isEmpty())throw new java.io.IOException("No text response");
        return out.toString();
    }
    static HttpsURLConnection connection(Provider provider,String key) throws Exception {
        if(key.isEmpty()||key.indexOf('\r')>=0||key.indexOf('\n')>=0||key.length()>4096)throw new IllegalArgumentException("Invalid key");
        HttpsURLConnection c=(HttpsURLConnection)new URL(provider.url).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(20000);c.setReadTimeout(180000);
        c.setRequestMethod("POST");c.setDoOutput(true);c.setUseCaches(false);
        c.setRequestProperty("Content-Type","application/json");
        c.setRequestProperty("Accept","application/json");
        if(provider.protocol==2){c.setRequestProperty("x-api-key",key);c.setRequestProperty("anthropic-version","2023-06-01");}
        else c.setRequestProperty("Authorization","Bearer "+key);
        return c;
    }
    static String execute(Provider provider,HttpsURLConnection c,String model,String text) throws Exception {
        byte[] payload=request(provider,model,text).toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(payload.length);
        try(java.io.OutputStream stream=c.getOutputStream()){stream.write(payload);}
        int status=c.getResponseCode();
        if(status<200||status>=300)throw new java.io.IOException("HTTP "+status); // Never expose response bodies containing secrets.
        try(InputStream stream=c.getInputStream();ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;
            while((n=stream.read(b))!=-1){
                if(buffer.size()+n>2_000_000)throw new java.io.IOException("Response limit");
                buffer.write(b,0,n);
            }
            return parse(provider,new JSONObject(buffer.toString("UTF-8")));
        }
    }
}
