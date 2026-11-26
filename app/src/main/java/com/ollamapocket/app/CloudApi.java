package com.ollamapocket.app;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/** Fixed HTTPS endpoints; no redirects, automatic retries or local history. */
final class CloudApi {
    static final int OUTPUT_TOKEN_LIMIT=2048;
    enum Provider {
        OPENAI("OpenAI","https://api.openai.com/v1/responses",0),
        GEMINI("Google Gemini","https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",1),
        CLAUDE("Anthropic Claude","https://api.anthropic.com/v1/messages",2),
        XAI("xAI Grok","https://api.x.ai/v1/responses",0),
        DEEPSEEK("DeepSeek","https://api.deepseek.com/chat/completions",1),
        OPENROUTER("OpenRouter","https://openrouter.ai/api/v1/chat/completions",1),
        PC_OLLAMA("PC Ollama","",1), PC_LM_STUDIO("PC LM Studio","",1);
        boolean remote(){return this==PC_OLLAMA||this==PC_LM_STUDIO;}
        static Provider[] hosted(){return java.util.Arrays.stream(values()).filter(p->!p.remote()).toArray(Provider[]::new);}
        final String title,url;final int protocol;
        Provider(String title,String url,int protocol){this.title=title;this.url=url;this.protocol=protocol;}
    }
    static final class HttpFailure extends java.io.IOException {
        final int status;
        HttpFailure(int status){super("HTTP "+status);this.status=status;}
    }
    static String catalogUrl(Provider p){
        switch(p){
            case OPENAI:return "https://api.openai.com/v1/models";
            case GEMINI:return "https://generativelanguage.googleapis.com/v1beta/openai/models";
            case CLAUDE:return "https://api.anthropic.com/v1/models";
            case XAI:return "https://api.x.ai/v1/models";
            case DEEPSEEK:return "https://api.deepseek.com/models";
            case OPENROUTER:return "https://openrouter.ai/api/v1/models";
            default:throw new IllegalArgumentException();
        }
    }
    static String remoteBase(String address){
        if(address==null||!address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}:[0-9]{1,5}"))throw new IllegalArgumentException("Private IPv4:port required");
        String[] pair=address.split(":"),parts=pair[0].split("\\.");
        int[] ip=new int[4];for(int i=0;i<4;i++){ip[i]=Integer.parseInt(parts[i]);if(ip[i]>255||!Integer.toString(ip[i]).equals(parts[i]))throw new IllegalArgumentException("Invalid IPv4");}
        int port=Integer.parseInt(pair[1]);
        if(port<1||port>65535||!(ip[0]==10||ip[0]==172&&ip[1]>=16&&ip[1]<=31||ip[0]==192&&ip[1]==168))throw new IllegalArgumentException("Private IPv4:port required");
        return "http://"+pair[0]+":"+port;
    }
    /** Exact POST target for the review UI and connection; no network activity here. */
    static String requestTarget(Provider p,String address){
        if(p==null)throw new IllegalArgumentException("Provider required");
        return p.remote()?remoteBase(address)+"/v1/chat/completions":p.url;
    }
    static java.net.HttpURLConnection remoteConnection(Provider p,String address,boolean post)throws Exception{
        if(!p.remote())throw new IllegalArgumentException();
        java.net.HttpURLConnection c=(java.net.HttpURLConnection)new URL(post?requestTarget(p,address):remoteBase(address)+"/v1/models").openConnection(java.net.Proxy.NO_PROXY);
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(180000);
        c.setRequestMethod(post?"POST":"GET");c.setDoOutput(post);c.setUseCaches(false);
        c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json");
        return c;
    }
    static HttpsURLConnection catalogConnection(Provider p,String key)throws Exception{
        return open(p,key,catalogUrl(p),false);
    }
    static java.util.List<String> catalog(java.net.HttpURLConnection c)throws Exception{
        int status=c.getResponseCode();
        if(status<200||status>=300)throw new HttpFailure(status);
        JSONObject json=readJson(c);JSONArray rows=json.optJSONArray("data");
        java.util.TreeSet<String> ids=new java.util.TreeSet<>();
        if(rows!=null)for(int i=0;i<Math.min(rows.length(),5000);i++){
            JSONObject item=rows.optJSONObject(i);if(item==null)continue;
            String id=item.optString("id","");
            if(PrivacyPolicy.validModelId(id))ids.add(id);
        }
        if(ids.isEmpty())throw new java.io.IOException("Empty catalog");
        return new java.util.ArrayList<>(ids);
    }
    static final class Reply {
        final String text; final long inputTokens, outputTokens;
        Reply(String text,long input,long output){this.text=text;inputTokens=input;outputTokens=output;}
    }
    private static long tokenCount(JSONObject obj,String key){
        if(obj==null)return -1;
        Object raw=obj.opt(key);
        if(!(raw instanceof Number))return -1;
        double d=((Number)raw).doubleValue();
        return Double.isFinite(d)&&d>=0&&d<=Long.MAX_VALUE&&d==Math.floor(d)?((Number)raw).longValue():-1;
    }
    static Reply parseReply(Provider provider,JSONObject body)throws Exception{
        JSONObject usage=body.optJSONObject("usage");
        return new Reply(parse(provider,body),tokenCount(usage,provider.protocol==1?"prompt_tokens":"input_tokens"),
            tokenCount(usage,provider.protocol==1?"completion_tokens":"output_tokens"));
    }
    static JSONObject request(Provider provider,String model,String text) throws Exception {
        return request(provider,model,java.util.Collections.singletonList(new CloudHistory.Message("user",text)));
    }
    static JSONObject request(Provider provider,String model,java.util.List<CloudHistory.Message> messages) throws Exception {
        if(provider==null||!PrivacyPolicy.validModelId(model))throw new IllegalArgumentException("Invalid model ID");
        if(messages==null||messages.isEmpty()||messages.size()>25||!"user".equals(messages.get(messages.size()-1).role))
            throw new IllegalArgumentException("Invalid cloud context");
        JSONArray input=new JSONArray();int chars=0;
        for(int i=0;i<messages.size();i++){
            CloudHistory.Message m=messages.get(i);
            if(!(i%2==0?"user":"assistant").equals(m.role)||m.content==null||m.content.trim().isEmpty())
                throw new IllegalArgumentException("Invalid cloud message");
            chars+=m.content.length();if(chars>CloudHistory.MAX_CONTEXT)throw new IllegalArgumentException("Context limit");
            input.put(new JSONObject().put("role",m.role).put("content",m.content));
        }
        JSONObject body=new JSONObject().put("model",model);
        if(provider.protocol==0){
            body.put("input",input);
            body.put("max_output_tokens",OUTPUT_TOKEN_LIMIT).put("store",false);
        }else{
            body.put("messages",input);
            body.put("max_tokens",OUTPUT_TOKEN_LIMIT).put("stream",false);
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
        if(out.toString().trim().isEmpty()||out.length()>CloudHistory.MAX_REPLY)throw new java.io.IOException("Invalid text response");
        return out.toString();
    }
    static HttpsURLConnection connection(Provider provider,String key) throws Exception {
        if(provider.remote())throw new IllegalArgumentException("Remote requires explicit address");
        return open(provider,key,requestTarget(provider,""),true);
    }
    private static HttpsURLConnection open(Provider provider,String key,String url,boolean post)throws Exception{
        if(provider==null||provider.remote()||!PrivacyPolicy.validApiKey(key))throw new IllegalArgumentException("Invalid key");
        HttpsURLConnection c=(HttpsURLConnection)new URL(url).openConnection(java.net.Proxy.NO_PROXY);
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(20000);c.setReadTimeout(180000);
        c.setRequestMethod(post?"POST":"GET");c.setDoOutput(post);c.setUseCaches(false);
        c.setRequestProperty("Content-Type","application/json");
        c.setRequestProperty("Accept","application/json");
        if(provider.protocol==2){c.setRequestProperty("x-api-key",key);c.setRequestProperty("anthropic-version","2023-06-01");}
        else c.setRequestProperty("Authorization","Bearer "+key);
        return c;
    }
    static String execute(Provider provider,java.net.HttpURLConnection c,String model,String text) throws Exception {
        return execute(provider,c,model,java.util.Collections.singletonList(new CloudHistory.Message("user",text)));
    }
    static String execute(Provider provider,java.net.HttpURLConnection c,String model,java.util.List<CloudHistory.Message> messages) throws Exception {
        return executeReply(provider,c,model,messages).text;
    }
    static Reply executeReply(Provider provider,java.net.HttpURLConnection c,String model,java.util.List<CloudHistory.Message> messages) throws Exception {
        byte[] payload=request(provider,model,messages).toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(payload.length);
        try(java.io.OutputStream stream=c.getOutputStream()){stream.write(payload);}
        int status=c.getResponseCode();
        if(status<200||status>=300)throw new HttpFailure(status); // Never expose response bodies containing secrets.
        return parseReply(provider,readJson(c));
    }
    interface Progress { void update(String text); }
    static final class StreamFailure extends java.io.IOException {
        final boolean incomplete;
        StreamFailure(String reason,boolean incomplete){super(reason);this.incomplete=incomplete;}
    }
    static Reply streamReply(Provider provider,java.net.HttpURLConnection c,String model,java.util.List<CloudHistory.Message> messages,Progress progress)throws Exception{
        JSONObject body=request(provider,model,messages).put("stream",true);
        if(provider==Provider.DEEPSEEK||provider==Provider.OPENROUTER)body.put("stream_options",new JSONObject().put("include_usage",true));
        c.setRequestProperty("Accept","text/event-stream");
        byte[] payload=body.toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(payload.length);
        try(java.io.OutputStream stream=c.getOutputStream()){stream.write(payload);}
        int status=c.getResponseCode();if(status<200||status>=300)throw new HttpFailure(status);
        String contentType=c.getContentType();
        if(contentType==null||!contentType.toLowerCase(java.util.Locale.ROOT).contains("text/event-stream"))throw new java.io.IOException("Expected SSE");
        try(InputStream raw=c.getInputStream()){return parseStream(provider,raw,progress);}
    }
    static Reply parseStream(Provider provider,InputStream raw,Progress progress)throws Exception{
        StringBuilder answer=new StringBuilder();long input=-1,output=-1,lastUi=0;
        final long deadline=System.nanoTime()+300000000000L;
        boolean complete=false,finishedChoice=false;
        try(java.io.Reader reader=new java.io.InputStreamReader(raw,StandardCharsets.UTF_8)){
            StringBuilder line=new StringBuilder(),event=new StringBuilder();int ch;long consumed=0;
            while((ch=reader.read())!=-1){
                if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Stream interrupted");
                if(System.nanoTime()-deadline>=0)throw new java.net.SocketTimeoutException("Stream deadline");
                if(++consumed>4000000)throw new StreamFailure("Stream limit",true);
                if(ch!='\n'){if(ch!='\r')line.append((char)ch);if(line.length()>1000000)throw new java.io.IOException("Event limit");continue;}
                String row=line.toString();line.setLength(0);
                if(row.startsWith("data:")){if(event.length()>0)event.append('\n');event.append(row.substring(5).trim());if(event.length()>1000000)throw new java.io.IOException("Event limit");continue;}
                if(!row.isEmpty()||event.length()==0)continue;
                String data=event.toString();event.setLength(0);
                if("[DONE]".equals(data)){complete=provider.protocol==1&&finishedChoice;break;}
                JSONObject j=new JSONObject(data);String type=j.optString("type","");
                if(!j.isNull("error")||"error".equals(type)||"response.failed".equals(type)||"response.incomplete".equals(type))throw new StreamFailure("Provider stream failed",true);
                JSONObject usage=j.optJSONObject("usage");
                String part="";
                if(provider.protocol==0){
                    if("response.output_text.delta".equals(type))part=j.optString("delta","");
                    if("response.completed".equals(type)){
                        JSONObject response=j.optJSONObject("response");if(response==null)throw new java.io.IOException("Missing response");
                        usage=response.optJSONObject("usage");complete=true;
                    }
                }else if(provider.protocol==2){
                    if("message_start".equals(type)){JSONObject message=j.optJSONObject("message");if(message!=null)usage=message.optJSONObject("usage");}
                    if("content_block_delta".equals(type)){JSONObject delta=j.optJSONObject("delta");if(delta!=null&&"text_delta".equals(delta.optString("type")))part=delta.optString("text","");}
                    if("message_delta".equals(type)){JSONObject delta=j.optJSONObject("delta");String reason=delta==null?"":delta.optString("stop_reason","");if("max_tokens".equals(reason)||"refusal".equals(reason)||"tool_use".equals(reason))throw new StreamFailure("Incomplete message",true);}
                    if("message_stop".equals(type))complete=true;
                }else{
                    JSONArray choices=j.optJSONArray("choices");JSONObject choice=choices==null?null:choices.optJSONObject(0);
                    if(choice!=null){JSONObject delta=choice.optJSONObject("delta");if(delta!=null&&!delta.isNull("content"))part=delta.optString("content","");
                        if(!choice.isNull("finish_reason")){String reason=choice.optString("finish_reason");if(!"stop".equals(reason))throw new StreamFailure("Incomplete choice",true);finishedChoice=true;}}
                }
                long a=tokenCount(usage,provider.protocol==1?"prompt_tokens":"input_tokens"),b=tokenCount(usage,provider.protocol==1?"completion_tokens":"output_tokens");
                if(a>=0)input=a;if(b>=0)output=b;
                if((long)answer.length()+part.length()>CloudHistory.MAX_REPLY)throw new java.io.IOException("Reply limit");answer.append(part);
                long now=System.nanoTime();if(progress!=null&&!part.isEmpty()&&(lastUi==0||now-lastUi>=100000000L)){lastUi=now;progress.update(answer.toString());}
                if(complete)break;
            }
        }
        if(!complete||answer.toString().trim().isEmpty())throw new StreamFailure("Incomplete stream",true);
        return new Reply(answer.toString(),input,output);
    }
    private static JSONObject readJson(java.net.HttpURLConnection c)throws Exception{
        try(InputStream stream=c.getInputStream();ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;
            while((n=stream.read(b))!=-1){
                if(buffer.size()+n>2_000_000)throw new java.io.IOException("Response limit");
                buffer.write(b,0,n);
            }
            return new JSONObject(buffer.toString("UTF-8"));
        }
    }
}
