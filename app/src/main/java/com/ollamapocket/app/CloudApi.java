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
    static String remoteAddress(String address){return remoteBase(address).substring("http://".length());}
    private static void checkCancelled()throws java.io.InterruptedIOException{
        if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Cloud request cancelled");
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
        checkCancelled();int status=c.getResponseCode();checkCancelled();
        if(status<200||status>=300)throw new HttpFailure(status);
        JSONObject json=readJson(c);JSONArray rows=json.optJSONArray("data");
        java.util.TreeSet<String> ids=new java.util.TreeSet<>();
        if(rows!=null)for(int i=0;i<Math.min(rows.length(),5000);i++){
            JSONObject item=rows.optJSONObject(i);if(item==null)continue;
            Object id=item.opt("id");
            if(id instanceof String&&PrivacyPolicy.validModelId((String)id))ids.add((String)id);
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
        try{
            long value=new java.math.BigDecimal(raw.toString()).longValueExact();
            return value>=0?value:-1;
        }catch(ArithmeticException|NumberFormatException invalid){return -1;}
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
        if(CloudHistory.requestChars(messages)<0)throw new IllegalArgumentException("Invalid cloud context");
        JSONArray input=new JSONArray();
        for(CloudHistory.Message m:messages)
            input.put(new JSONObject().put("role",m.role).put("content",m.content));
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
    private static StreamFailure incomplete(){return new StreamFailure("Invalid or incomplete provider response",true);}
    private static String typedText(JSONObject value,String key,boolean optional)throws StreamFailure{
        Object raw=value==null?null:value.opt(key);
        if(raw==null||raw==JSONObject.NULL){if(optional)return "";throw incomplete();}
        if(!(raw instanceof String))throw incomplete();
        String text=(String)raw;
        if(!CloudHistory.validFragment(text))throw incomplete();
        return text;
    }
    private static boolean claudeStop(String reason){return "end_turn".equals(reason)||"stop_sequence".equals(reason);}
    private static void responseCompleted(JSONObject response)throws StreamFailure{
        if(response==null||!response.isNull("error")||
            !response.isNull("status")&&!"completed".equals(response.opt("status")))throw incomplete();
    }
    private static void appendText(StringBuilder out,String text)throws StreamFailure{
        if((long)out.length()+text.length()>CloudHistory.MAX_REPLY)throw incomplete();
        out.append(text);
    }
    static String parse(Provider provider,JSONObject body) throws Exception {
        if(provider==null||body==null||!body.isNull("error"))throw incomplete();
        StringBuilder out=new StringBuilder();
        if(provider.protocol==0){
            responseCompleted(body);
            JSONArray rows=body.optJSONArray("output");
            if(rows!=null)for(int i=0;i<rows.length();i++){
                JSONObject row=rows.optJSONObject(i);
                if(row==null)throw incomplete();
                Object kind=row.opt("type");
                if(!(kind instanceof String))throw incomplete();
                if("reasoning".equals(kind))continue;
                if(!"message".equals(kind))throw incomplete();
                if(!row.isNull("status")&&!"completed".equals(row.opt("status")))throw incomplete();
                JSONArray parts=row.optJSONArray("content");
                if(parts==null)throw incomplete();
                for(int j=0;j<parts.length();j++){
                    JSONObject part=parts.optJSONObject(j);
                    if(part==null)throw incomplete();
                    if("output_text".equals(part.opt("type")))appendText(out,typedText(part,"text",false));
                    else if("refusal".equals(part.opt("type")))throw incomplete();
                }
            }
        }else if(provider.protocol==2){
            if(!body.isNull("stop_reason")&&!claudeStop(typedText(body,"stop_reason",false)))throw incomplete();
            JSONArray parts=body.optJSONArray("content");
            if(parts!=null)for(int i=0;i<parts.length();i++){
                JSONObject part=parts.optJSONObject(i);
                if(part==null)throw incomplete();
                if("text".equals(part.opt("type")))appendText(out,typedText(part,"text",false));
                else if("tool_use".equals(part.opt("type"))||"server_tool_use".equals(part.opt("type")))throw incomplete();
            }
        }else{
            JSONArray choices=body.optJSONArray("choices");
            if(choices==null||choices.length()!=1)throw incomplete();
            JSONObject choice=choices.optJSONObject(0);
            if(choice==null||!choice.isNull("finish_reason")&&!"stop".equals(choice.opt("finish_reason")))throw incomplete();
            JSONObject message=choice.optJSONObject("message");
            if(message!=null){
                rejectCalls(message);
                appendText(out,typedText(message,"content",false));
            }
        }
        if(!CloudHistory.validMessage(out.toString(),CloudHistory.MAX_REPLY))throw incomplete();
        return out.toString();
    }
    private static void rejectCalls(JSONObject message)throws StreamFailure{
        Object calls=message.opt("tool_calls");
        if(calls!=null&&calls!=JSONObject.NULL&&(!(calls instanceof JSONArray)||((JSONArray)calls).length()!=0))throw incomplete();
        if(!message.isNull("function_call")||!message.isNull("refusal"))throw incomplete();
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
        checkCancelled();byte[] payload=request(provider,model,messages).toString().getBytes(StandardCharsets.UTF_8);
        writeRequestBody(c,payload);
        int status=c.getResponseCode();
        if(status<200||status>=300)throw new HttpFailure(status); // Never expose response bodies containing secrets.
        return parseReply(provider,readJson(c));
    }
    /** The caller transfers ownership of this temporary UTF-8 buffer; wipe it on all exits.
     * Request strings and networking-stack copies are outside this best-effort protection. */
    static void writeRequestBody(java.net.HttpURLConnection connection,byte[] payload)throws Exception{
        if(payload==null)throw new IllegalArgumentException("Missing request body");
        try{
            checkCancelled();
            connection.setFixedLengthStreamingMode(payload.length);
            checkCancelled();
            try(java.io.OutputStream stream=connection.getOutputStream()){
                checkCancelled();
                stream.write(payload);
            }
            checkCancelled();
        }finally{java.util.Arrays.fill(payload,(byte)0);}
    }
    static boolean isEventStream(String contentType){
        if(contentType==null)return false;
        int separator=contentType.indexOf(';');
        String media=(separator<0?contentType:contentType.substring(0,separator)).trim();
        return "text/event-stream".equalsIgnoreCase(media);
    }
    static JSONObject parseWireObject(String raw,int limit)throws StreamFailure{
        try{
            String json=raw!=null&&raw.startsWith("\ufeff")?raw.substring(1):raw;
            ToolJsonPolicy.requireCloudObject(json,limit);
            return new JSONObject(json);
        }catch(Exception invalid){throw incomplete();}
    }
    interface Progress { void update(String text); }
    static final class StreamFailure extends java.io.IOException {
        final boolean incomplete;
        StreamFailure(String reason,boolean incomplete){super(reason);this.incomplete=incomplete;}
    }
    static Reply streamReply(Provider provider,java.net.HttpURLConnection c,String model,java.util.List<CloudHistory.Message> messages,Progress progress)throws Exception{
        checkCancelled();JSONObject body=request(provider,model,messages).put("stream",true);
        if(provider==Provider.DEEPSEEK||provider==Provider.OPENROUTER)body.put("stream_options",new JSONObject().put("include_usage",true));
        c.setRequestProperty("Accept","text/event-stream");
        byte[] payload=body.toString().getBytes(StandardCharsets.UTF_8);
        writeRequestBody(c,payload);
        int status=c.getResponseCode();if(status<200||status>=300)throw new HttpFailure(status);
        String contentType=c.getContentType();
        if(!isEventStream(contentType))throw new java.io.IOException("Expected SSE");
        try(InputStream raw=c.getInputStream()){return parseStream(provider,raw,progress);}
    }
    static Reply parseStream(Provider provider,InputStream raw,Progress progress)throws Exception{
        StringBuilder answer=new StringBuilder();long input=-1,output=-1,lastUi=0;
        final long deadline=System.nanoTime()+300000000000L;
        boolean complete=false,finishedChoice=false,finishedClaude=false;
        int published=0;
        try{
        try(java.io.Reader reader=new java.io.InputStreamReader(raw,StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT))){
            StringBuilder line=new StringBuilder(),event=new StringBuilder();int ch;long consumed=0;
            boolean afterCr=false;
            while((ch=reader.read())!=-1){
                if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Stream interrupted");
                if(System.nanoTime()-deadline>=0)throw new java.net.SocketTimeoutException("Stream deadline");
                if(++consumed>4000000)throw new StreamFailure("Stream limit",true);
                if(consumed==1&&ch==0xfeff)continue;
                if(afterCr){afterCr=false;if(ch=='\n')continue;}
                if(ch!='\n'&&ch!='\r'){line.append((char)ch);if(line.length()>1000000)throw new java.io.IOException("Event limit");continue;}
                afterCr=ch=='\r';
                String row=line.toString();line.setLength(0);
                if(row.startsWith("data:")){
                    String value=row.substring(5);if(value.startsWith(" "))value=value.substring(1);
                    if(event.length()>0)event.append('\n');event.append(value);
                    if(event.length()>1000000)throw new java.io.IOException("Event limit");continue;
                }
                if(!row.isEmpty()||event.length()==0)continue;
                String data=event.toString();event.setLength(0);
                if("[DONE]".equals(data.trim())){complete=provider.protocol==1&&finishedChoice;break;}
                JSONObject j=parseWireObject(data,1_000_000);
                String type=j.optString("type","");
                if(!j.isNull("error")||"error".equals(type)||"response.failed".equals(type)||"response.incomplete".equals(type))throw new StreamFailure("Provider stream failed",true);
                JSONObject usage=j.optJSONObject("usage");
                String part="";
                if(provider.protocol==0){
                    if("response.output_text.delta".equals(type))part=typedText(j,"delta",false);
                    if("response.refusal.delta".equals(type)||"response.refusal.done".equals(type))throw incomplete();
                    if("response.completed".equals(type)){
                        JSONObject response=j.optJSONObject("response");responseCompleted(response);
                        if(response.has("output")){
                            String finalText=parse(provider,response);
                            if(answer.length()==0)part=finalText;
                            else if(!finalText.contentEquals(answer))throw incomplete();
                        }
                        usage=response.optJSONObject("usage");complete=true;
                    }
                }else if(provider.protocol==2){
                    if("message_start".equals(type)){JSONObject message=j.optJSONObject("message");if(message==null)throw incomplete();usage=message.optJSONObject("usage");}
                    if("content_block_start".equals(type)){
                        if(finishedClaude)throw incomplete();
                        JSONObject block=j.optJSONObject("content_block");
                        if(block==null)throw incomplete();
                        if("text".equals(block.opt("type")))part=typedText(block,"text",false);
                        else if("tool_use".equals(block.opt("type"))||"server_tool_use".equals(block.opt("type")))throw incomplete();
                    }
                    if("content_block_delta".equals(type)){
                        if(finishedClaude)throw incomplete();
                        JSONObject delta=j.optJSONObject("delta");if(delta==null)throw incomplete();
                        if("text_delta".equals(delta.opt("type")))part=typedText(delta,"text",false);
                        if("input_json_delta".equals(delta.opt("type")))throw incomplete();
                    }
                    if("message_delta".equals(type)){
                        JSONObject delta=j.optJSONObject("delta");if(delta==null)throw incomplete();
                        if(!delta.isNull("stop_reason")){
                            if(finishedClaude||!claudeStop(typedText(delta,"stop_reason",false)))throw incomplete();
                            finishedClaude=true;
                        }
                    }
                    if("message_stop".equals(type)){if(!finishedClaude)throw incomplete();complete=true;}
                }else{
                    Object rawChoices=j.opt("choices");
                    if(rawChoices!=null&&rawChoices!=JSONObject.NULL&&!(rawChoices instanceof JSONArray))throw incomplete();
                    JSONArray choices=j.optJSONArray("choices");
                    if(choices!=null&&choices.length()>1)throw incomplete();
                    if(choices!=null&&choices.length()==1){
                        if(finishedChoice)throw incomplete();
                        JSONObject choice=choices.optJSONObject(0);if(choice==null)throw incomplete();
                        Object index=choice.opt("index");
                        if(index!=null&&index!=JSONObject.NULL&&(!(index instanceof Number)||((Number)index).doubleValue()!=0))throw incomplete();
                        Object rawDelta=choice.opt("delta");
                        if(rawDelta!=null&&rawDelta!=JSONObject.NULL&&!(rawDelta instanceof JSONObject))throw incomplete();
                        JSONObject delta=choice.optJSONObject("delta");
                        if(delta!=null){rejectCalls(delta);part=typedText(delta,"content",true);}
                        if(!choice.isNull("finish_reason")){
                            if(!"stop".equals(choice.opt("finish_reason")))throw incomplete();
                            finishedChoice=true;
                        }
                    }
                }
                long a=tokenCount(usage,provider.protocol==1?"prompt_tokens":"input_tokens"),b=tokenCount(usage,provider.protocol==1?"completion_tokens":"output_tokens");
                if(a>=0)input=Math.max(input,a);if(b>=0)output=Math.max(output,b);
                appendText(answer,part);
                long now=System.nanoTime();if(progress!=null&&!part.isEmpty()&&(lastUi==0||now-lastUi>=100000000L)){
                    lastUi=now;published=answer.length();progress.update(answer.toString());
                }
                if(complete)break;
            }
        }
        if(!complete||!CloudHistory.validMessage(answer.toString(),CloudHistory.MAX_REPLY))throw new StreamFailure("Incomplete stream",true);
        return new Reply(answer.toString(),input,output);
        }catch(Exception failure){
            // Only previously accepted, bounded text; never synthesize a successful completion.
            // The preview throttle must not discard the tail of an interrupted reply.
            flushIncompletePreview(progress,answer,published);
            throw failure;
        }
    }
    static void flushIncompletePreview(Progress progress,StringBuilder accepted,int published){
        if(progress!=null&&accepted.length()>published&&!Thread.currentThread().isInterrupted()){
            try{progress.update(accepted.toString());}catch(RuntimeException ignored){}
        }
    }
    private static JSONObject readJson(java.net.HttpURLConnection c)throws Exception{
        try(InputStream stream=c.getInputStream();ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;
            while((n=stream.read(b))!=-1){
                checkCancelled();
                if(buffer.size()+n>2_000_000)throw new java.io.IOException("Response limit");
                buffer.write(b,0,n);
            }
            String decoded=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(buffer.toByteArray())).toString();
            checkCancelled();
            return parseWireObject(decoded,2_000_000);
        }
    }
}
