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
    public static void main(String[] args)throws Exception{
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
        System.out.println("PASS: "+checks+" offline SSE protocol checks");
    }
}
