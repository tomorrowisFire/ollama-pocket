package com.ollamapocket.app;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public final class CloudStreamTest {
    private static int checks;
    private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("check "+checks);}
    private static String event(String json){return "data: "+json+"\n\n";}
    private static CloudApi.Reply parse(CloudApi.Provider p,String data)throws Exception{
        return CloudApi.parseStream(p,new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)),null);
    }
    private static void rejects(CloudApi.Provider p,String data)throws Exception{
        try{parse(p,data);}catch(CloudApi.StreamFailure expected){check(expected.incomplete);return;}
        throw new AssertionError("Unexpected successful stream");
    }
    private static void recovered(CloudApi.Provider p,String data,String expected)throws Exception{
        java.util.ArrayList<String> previews=new java.util.ArrayList<>();
        try{
            CloudApi.parseStream(p,new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)),previews::add);
            throw new AssertionError("Unexpected completion");
        }catch(CloudApi.StreamFailure failure){check(failure.incomplete);}
        check(!previews.isEmpty()&&expected.equals(previews.get(previews.size()-1)));
        String last="";
        for(String value:previews){check(value.startsWith(last)&&expected.startsWith(value));last=value;}
    }
    private static final class RequestSink extends java.net.HttpURLConnection{
        final java.io.ByteArrayOutputStream sent=new java.io.ByteArrayOutputStream();
        final boolean failWrite,failOpen;
        int opened,closed;
        RequestSink(boolean failWrite,boolean failOpen)throws Exception{
            super(new java.net.URL("https://example.invalid"));this.failWrite=failWrite;this.failOpen=failOpen;
        }
        @Override public void connect(){}
        @Override public void disconnect(){}
        @Override public boolean usingProxy(){return false;}
        @Override public java.io.OutputStream getOutputStream()throws java.io.IOException{
            opened++;
            if(failOpen)throw new java.io.IOException("fixture open failure");
            return new java.io.OutputStream(){
                @Override public void write(int b)throws java.io.IOException{
                    if(failWrite)throw new java.io.IOException("fixture write failure");
                    sent.write(b);
                }
                @Override public void close(){closed++;}
            };
        }
        int fixedLength(){return fixedContentLength;}
    }
    private static boolean zeroed(byte[] bytes){for(byte b:bytes)if(b!=0)return false;return true;}
    private static void requestBufferOwnership()throws Exception{
        byte[] exact="한국어 日本語 中文 😀".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for(int mode=0;mode<4;mode++){
            RequestSink sink=new RequestSink(mode==1,mode==2);
            byte[] payload=exact.clone();
            if(mode==3)Thread.currentThread().interrupt();
            boolean rejected=false;
            try{CloudApi.writeRequestBody(sink,payload);}
            catch(java.io.IOException expected){rejected=true;}
            finally{if(mode==3)Thread.interrupted();}
            check(zeroed(payload));
            check(rejected==(mode!=0));
            if(mode==0)check(sink.fixedLength()==exact.length&&java.util.Arrays.equals(sink.sent.toByteArray(),exact)&&sink.closed==1);
            if(mode==1)check(sink.opened==1&&sink.closed==1);
            if(mode==2)check(sink.opened==1&&sink.closed==0);
            if(mode==3)check(sink.opened==0);
        }
    }
    private static void responseReadBudget()throws Exception{
        byte[] exact="{\"value\":\"한국어 日本語 中文 😀\"}".getBytes(StandardCharsets.UTF_8);
        check(CloudApi.readWireObject(new ByteArrayInputStream(exact),()->0L).getString("value").equals("한국어 日本語 中文 😀"));
        java.util.concurrent.atomic.AtomicLong clock=new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicInteger reads=new java.util.concurrent.atomic.AtomicInteger();
        java.io.InputStream slow=new java.io.InputStream(){
            @Override public int read(){throw new AssertionError("Single-byte read");}
            @Override public int read(byte[] bytes){
                reads.incrementAndGet();System.arraycopy(exact,0,bytes,0,exact.length);
                clock.set(CloudApi.JSON_READ_BUDGET_NS);return exact.length;
            }
        };
        try{CloudApi.readWireObject(slow,clock::get);throw new AssertionError("Expired read accepted");}
        catch(java.net.SocketTimeoutException expected){check(reads.get()==1);}
        for(java.io.InputStream bad:new java.io.InputStream[]{
            new ByteArrayInputStream(new byte[2_000_001]),
            new java.io.InputStream(){
                @Override public int read(){return 0;}
                @Override public int read(byte[] bytes){return 0;}
            },
            new ByteArrayInputStream(new byte[]{(byte)0xff})
        }){
            try{CloudApi.readWireObject(bad,()->0L);throw new AssertionError("Invalid wire accepted");}
            catch(java.io.IOException expected){check(true);}
        }
        Thread.currentThread().interrupt();
        try{
            try{CloudApi.readWireObject(slow,()->0L);throw new AssertionError("Cancelled read accepted");}
            catch(java.io.InterruptedIOException expected){check(reads.get()==1);}
        }finally{Thread.interrupted();}
    }
    public static void main(String[] args)throws Exception{
        requestBufferOwnership();
        responseReadBudget();
        for(String type:new String[]{"text/event-stream","Text/Event-Stream; charset=UTF-8"," text/event-stream ; charset=utf-8 "})
            check(CloudApi.isEventStream(type));
        for(String type:new String[]{null,"text/html; type=text/event-stream","application/text/event-stream","text/event-streaming",""})
            check(!CloudApi.isEventStream(type));
        check(CloudApi.parseWireObject("\ufeff{\"text\":\"한국어 中文 日本語 English 😀\"}",100).getString("text").equals("한국어 中文 日本語 English 😀"));
        for(String malformed:new String[]{"{'type':'response.completed'}","{\"ok\":true,}","{\"ok\":true} trailing","{\"ok\":false,\"ok\":true}","{\"ok\":false,\"\\u006fk\":true}","{\"value\":01}","{\"value\":\"\\ud800\"}","{\"value\":"+"[".repeat(33)+"0"+"]".repeat(33)+"}"}){
            try{CloudApi.parseWireObject(malformed,1_000_000);throw new AssertionError("Invalid cloud wire JSON");}
            catch(CloudApi.StreamFailure expected){check(expected.incomplete);}
        }
        try{CloudApi.parseWireObject("{\"value\":\"123\"}",10);throw new AssertionError("Cloud wire limit");}
        catch(CloudApi.StreamFailure expected){check(expected.incomplete);}
        String[] targets={"https://api.openai.com/v1/responses","https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            "https://api.anthropic.com/v1/messages","https://api.x.ai/v1/responses","https://api.deepseek.com/chat/completions",
            "https://openrouter.ai/api/v1/chat/completions","http://192.168.1.9:1234/v1/chat/completions","http://192.168.1.9:1234/v1/chat/completions"};
        check(CloudApi.Provider.values().length==targets.length&&CloudApi.OUTPUT_TOKEN_LIMIT==2048);
        check(CloudApi.remoteAddress("192.168.1.9:01234").equals("192.168.1.9:1234"));
        for(CloudApi.Provider p:CloudApi.Provider.values()){
            check(targets[p.ordinal()].equals(CloudApi.requestTarget(p,"192.168.1.9:01234")));
            org.json.JSONObject body=CloudApi.request(p,"model","offline");
            check(body.getInt(p.protocol==0?"max_output_tokens":"max_tokens")==CloudApi.OUTPUT_TOKEN_LIMIT);
            check("model".equals(body.getString("model")));
            check(p.protocol!=0||!body.getBoolean("store"));
        }
        try{CloudApi.requestTarget(CloudApi.Provider.PC_OLLAMA,"8.8.8.8:1234");throw new AssertionError("Public LAN target accepted");}
        catch(IllegalArgumentException expected){check(true);}
        try{CloudApi.requestTarget(null,"");throw new AssertionError("Null provider accepted");}
        catch(IllegalArgumentException expected){check(true);}
        String delta=event("{\"type\":\"response.output_text.delta\",\"delta\":\"안녕\"}");
        String end=event("{\"type\":\"response.completed\",\"response\":{\"usage\":{\"input_tokens\":7,\"output_tokens\":2}}}");
        for(CloudApi.Provider p:new CloudApi.Provider[]{CloudApi.Provider.OPENAI,CloudApi.Provider.XAI}){
            CloudApi.Reply r=parse(p,delta+end);
            check(r.text.equals("안녕")&&r.inputTokens==7&&r.outputTokens==2);
            rejects(p,delta);
            rejects(p,delta+event("{\"type\":\"response.incomplete\"}"));
        }
        String start=event("{\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":12}}}");
        String text=event("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"日本語\"}}");
        String stop=event("{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":4}}");
        String done=event("{\"type\":\"message_stop\"}");
        CloudApi.Reply a=parse(CloudApi.Provider.CLAUDE,start+text+stop+done);
        check(a.text.equals("日本語")&&a.inputTokens==12&&a.outputTokens==4);
        rejects(CloudApi.Provider.CLAUDE,start+text+stop);
        rejects(CloudApi.Provider.CLAUDE,start+text+event("{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"max_tokens\"}}")+done);
        String chunk=event("{\"choices\":[{\"delta\":{\"content\":\"中文\"},\"finish_reason\":null}]}");
        String finish=event("{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}");
        String usage=event("{\"choices\":[],\"usage\":{\"prompt_tokens\":9,\"completion_tokens\":3}}");
        for(CloudApi.Provider p:new CloudApi.Provider[]{CloudApi.Provider.GEMINI,CloudApi.Provider.DEEPSEEK,CloudApi.Provider.OPENROUTER,CloudApi.Provider.PC_OLLAMA,CloudApi.Provider.PC_LM_STUDIO}){
            CloudApi.Reply r=parse(p,chunk+finish+usage+event("[DONE]"));
            check(r.text.equals("中文")&&r.inputTokens==9&&r.outputTokens==3);
            rejects(p,chunk+event("[DONE]"));
            rejects(p,chunk+finish);
            rejects(p,chunk+event("{\"choices\":[{\"finish_reason\":\"length\"}]}")+event("[DONE]"));
        }
        check(parse(CloudApi.Provider.OPENAI,delta.replace("\n","\r\n")+end).text.equals("안녕"));
        rejects(CloudApi.Provider.OPENAI,event("{\"error\":{\"message\":\"test\"}}"));
        String more=event("{\"type\":\"response.output_text.delta\",\"delta\":\" 世界😀\"}");
        rejects(CloudApi.Provider.OPENAI,delta+event("{\"type\":\"response.output_text.delta\",\"delta\":\"bad\",\"delta\":\"duplicate\"}")+end);
        rejects(CloudApi.Provider.OPENAI,delta+event("{'type':'response.completed','response':{}}"));
        rejects(CloudApi.Provider.OPENAI,delta+event("{\"type\":\"response.completed\",\"response\":{}} trailing"));
        for(CloudApi.Provider p:new CloudApi.Provider[]{CloudApi.Provider.OPENAI,CloudApi.Provider.XAI}){
            recovered(p,delta+more,"안녕 世界😀");
            recovered(p,delta+more+event("{\"type\":\"response.incomplete\"}"),"안녕 世界😀");
        }
        String moreClaude=event("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\" ไทย😀\"}}");
        recovered(CloudApi.Provider.CLAUDE,start+text+moreClaude+stop,"日本語 ไทย😀");
        recovered(CloudApi.Provider.CLAUDE,start+text+moreClaude+event("{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"max_tokens\"}}"),"日本語 ไทย😀");
        String moreChat=event("{\"choices\":[{\"delta\":{\"content\":\" 한국어😀\"},\"finish_reason\":null}]}");
        for(CloudApi.Provider p:new CloudApi.Provider[]{CloudApi.Provider.GEMINI,CloudApi.Provider.DEEPSEEK,CloudApi.Provider.OPENROUTER,CloudApi.Provider.PC_OLLAMA,CloudApi.Provider.PC_LM_STUDIO}){
            recovered(p,chunk+moreChat+finish,"中文 한국어😀");
            recovered(p,chunk+moreChat+event("{\"choices\":[{\"finish_reason\":\"length\"}]}"),"中文 한국어😀");
        }
        check(parse(CloudApi.Provider.OPENAI,delta+more+end).text.equals("안녕 世界😀"));
        java.util.ArrayList<String> finalPreview=new java.util.ArrayList<>();
        CloudApi.flushIncompletePreview(finalPreview::add,new StringBuilder("abc"),1);
        check(finalPreview.size()==1&&finalPreview.get(0).equals("abc"));
        CloudApi.flushIncompletePreview(finalPreview::add,new StringBuilder("abc"),3);
        check(finalPreview.size()==1);
        CloudApi.flushIncompletePreview(null,new StringBuilder("abc"),0);
        CloudApi.flushIncompletePreview(value->{throw new IllegalStateException("preview failed");},new StringBuilder("abc"),1);
        check(true);
        // Strict types, framing and real terminal events; malformed data must never become saved text.
        for(CloudApi.Provider p:new CloudApi.Provider[]{CloudApi.Provider.OPENAI,CloudApi.Provider.XAI}){
            rejects(p,event("{\"type\":\"response.output_text.delta\",\"delta\":123}")+end);
            rejects(p,delta+event("{\"type\":\"response.completed\",\"response\":{\"status\":\"incomplete\"}}"));
            rejects(p,delta+event("{\"type\":\"response.refusal.delta\",\"delta\":\"no\"}")+end);
        }
        rejects(CloudApi.Provider.CLAUDE,start+text+done);
        rejects(CloudApi.Provider.CLAUDE,start+text+event("{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"pause_turn\"}}")+done);
        rejects(CloudApi.Provider.CLAUDE,start+text+stop+text+done);
        rejects(CloudApi.Provider.CLAUDE,start+event("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":{}}}")+stop+done);
        check(parse(CloudApi.Provider.CLAUDE,start+event("{\"type\":\"content_block_start\",\"content_block\":{\"type\":\"text\",\"text\":\"Initial \"}}")+text+stop+done).text.equals("Initial 日本語"));
        for(CloudApi.Provider p:new CloudApi.Provider[]{CloudApi.Provider.GEMINI,CloudApi.Provider.DEEPSEEK,CloudApi.Provider.OPENROUTER,CloudApi.Provider.PC_OLLAMA,CloudApi.Provider.PC_LM_STUDIO}){
            rejects(p,event("{\"choices\":[{\"delta\":{\"content\":false}}]}")+finish+event("[DONE]"));
            rejects(p,chunk+finish+chunk+event("[DONE]"));
            rejects(p,chunk+event("{\"choices\":[{\"finish_reason\":\"stop\"},{\"finish_reason\":\"stop\"}]}")+event("[DONE]"));
            rejects(p,chunk+event("{\"choices\":[{\"delta\":{\"tool_calls\":[{}]},\"finish_reason\":\"stop\"}]}")+event("[DONE]"));
        }
        check(parse(CloudApi.Provider.OPENAI,"\ufeff"+(delta+end).replace("\n","\r")).text.equals("안녕"));
        byte[] malformed=(delta+end).getBytes(StandardCharsets.UTF_8);
        for(int i=0;i<malformed.length;i++)if((malformed[i]&128)!=0){malformed[i]=(byte)0xff;break;}
        try{CloudApi.parseStream(CloudApi.Provider.OPENAI,new ByteArrayInputStream(malformed),null);throw new AssertionError("Malformed UTF-8 accepted");}
        catch(java.nio.charset.CharacterCodingException expected){check(true);}
        String reply="{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":9223372036854775808,\"completion_tokens\":1.5}}";
        CloudApi.Reply typed=CloudApi.parseReply(CloudApi.Provider.GEMINI,new org.json.JSONObject(reply));
        check(typed.inputTokens==-1&&typed.outputTokens==-1&&typed.text.equals("ok"));
        String maximum=reply.replace("9223372036854775808","9223372036854775807").replace("1.5","2");
        check(CloudApi.parseReply(CloudApi.Provider.GEMINI,new org.json.JSONObject(maximum)).inputTokens==Long.MAX_VALUE);
        for(String bad:new String[]{"{\"choices\":[{\"message\":{\"content\":123}}]}","{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"length\"}]}"}){
            try{CloudApi.parseReply(CloudApi.Provider.GEMINI,new org.json.JSONObject(bad));throw new AssertionError("Invalid non-stream reply accepted");}
            catch(CloudApi.StreamFailure expected){check(true);}
        }
        for(String bad:new String[]{"\ud800"," \u00a0\u200d","x".repeat(CloudHistory.MAX_PROMPT+1)}){
            try{CloudApi.request(CloudApi.Provider.OPENAI,"model",bad);throw new AssertionError("Invalid request accepted");}
            catch(IllegalArgumentException expected){check(true);}
        }
        String finalOnly=event("{\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"output\":[{\"type\":\"reasoning\"},{\"type\":\"message\",\"status\":\"completed\",\"content\":[{\"type\":\"output_text\",\"text\":\"안녕\"}]}]}}");
        for(CloudApi.Provider p:new CloudApi.Provider[]{CloudApi.Provider.OPENAI,CloudApi.Provider.XAI}){
            check(parse(p,delta+finalOnly).text.equals("안녕"));
            check(parse(p,finalOnly).text.equals("안녕"));
            rejects(p,delta+finalOnly.replace("안녕","different"));
            rejects(p,delta+finalOnly.replace("reasoning","function_call"));
            rejects(p,delta+event("{\"type\":\"response.completed\",\"response\":{\"output\":[]}}"));
            rejects(p,delta+event("{\"type\":\"response.completed\",\"response\":{\"output\":null}}"));
            rejects(p,delta+event("not JSON"));
        }
        for(CloudApi.Provider p:CloudApi.Provider.values()){
            try{CloudApi.request(p,"model",java.util.List.of(new CloudHistory.Message("user","x".repeat(16001))));
                throw new AssertionError("Oversized user input accepted");}
            catch(IllegalArgumentException expected){check(true);}
        }
        // The bad fragment must not reach even the partial preview or a completed reply.
        for(CloudApi.Provider p:new CloudApi.Provider[]{CloudApi.Provider.OPENAI,CloudApi.Provider.XAI})
            recovered(p,delta+event("{\"type\":\"response.output_text.delta\",\"delta\":\"bad\\u0000text\"}")+end,"안녕");
        recovered(CloudApi.Provider.CLAUDE,start+text+event("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"bad\\u0000text\"}}")+stop+done,"日本語");
        for(CloudApi.Provider p:new CloudApi.Provider[]{CloudApi.Provider.GEMINI,CloudApi.Provider.DEEPSEEK,CloudApi.Provider.OPENROUTER,CloudApi.Provider.PC_OLLAMA,CloudApi.Provider.PC_LM_STUDIO}){
            recovered(p,chunk+event("{\"choices\":[{\"delta\":{\"content\":\"bad\\u0000text\"}}]}")+finish+event("[DONE]"),"中文");
            try{CloudApi.parseReply(p,new org.json.JSONObject("{\"choices\":[{\"message\":{\"content\":\"bad\\u0000text\"},\"finish_reason\":\"stop\"}]}"));throw new AssertionError("Control reply accepted");}
            catch(CloudApi.StreamFailure expected){check(expected.incomplete);}
        }
        System.out.println("PASS: "+checks+" offline SSE protocol checks");
    }
}
