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
    public static void main(String[] args)throws Exception{
        String[] targets={"https://api.openai.com/v1/responses","https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            "https://api.anthropic.com/v1/messages","https://api.x.ai/v1/responses","https://api.deepseek.com/chat/completions",
            "https://openrouter.ai/api/v1/chat/completions","http://192.168.1.9:1234/v1/chat/completions","http://192.168.1.9:1234/v1/chat/completions"};
        check(CloudApi.Provider.values().length==targets.length&&CloudApi.OUTPUT_TOKEN_LIMIT==2048);
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
        System.out.println("PASS: "+checks+" offline SSE protocol checks");
    }
}
