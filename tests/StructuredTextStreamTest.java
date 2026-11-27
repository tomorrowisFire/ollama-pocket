package com.ollamapocket.app;

import java.io.IOException;
import org.json.JSONObject;

public final class StructuredTextStreamTest {
    private static int checks;
    private static void ok(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    private interface Action{void run()throws Exception;}
    private static void reject(Action action)throws Exception{
        checks++;
        try{action.run();}catch(IOException expected){return;}
        throw new AssertionError("Invalid structured response accepted");
    }
    private static JSONObject json(String raw){return new JSONObject(raw);}
    public static void main(String[] args)throws Exception{
        var valid=new StructuredTextStream(12,4);
        valid.accept(json("{\"message\":{\"content\":\"한글\"},\"done\":false}"));
        valid.accept(json("{\"message\":{\"content\":\" OK\",\"tool_calls\":[]},\"done\":true,\"done_reason\":\"stop\",\"eval_count\":4}"));
        ok(valid.text().equals("한글 OK")&&!valid.truncated());
        reject(()->valid.accept(json("{\"done\":true}")));
        reject(valid::text);
        var old=new StructuredTextStream(12,4);
        old.accept(json("{\"message\":{\"content\":\"old\"},\"done\":true}"));
        ok(old.text().equals("old")&&!old.truncated());
        var partial=new StructuredTextStream(12,4);
        partial.accept(json("{\"message\":{\"content\":\"partial\"},\"done\":true,\"done_reason\":\"length\"}"));
        ok(partial.truncated()&&partial.text().equals("partial"));
        var incomplete=new StructuredTextStream(12,4);
        incomplete.accept(json("{\"message\":{\"content\":\"draft\"}}"));
        reject(incomplete::text);reject(incomplete::truncated);
        for(String raw:new String[]{
            "{\"message\":{\"content\":123},\"done\":true}",
            "{\"message\":{\"content\":{}},\"done\":true}",
            "{\"message\":\"not an object\",\"done\":true}",
            "{\"message\":{\"content\":\"a\",\"tool_calls\":[{}]},\"done\":true}",
            "{\"message\":{\"tool_calls\":{}},\"done\":true}",
            "{\"done\":\"true\"}","{\"done\":null}",
            "{\"done\":true,\"done_reason\":\"tool_calls\"}",
            "{\"done\":true,\"eval_count\":\"3\"}",
            "{\"done\":true,\"eval_count\":4.5}",
            "{\"done\":true,\"eval_count\":5}",
            "{\"done\":true,\"eval_count\":-1}",
            "{\"error\":\"failed\",\"done\":true}"}){
            var s=new StructuredTextStream(12,4);reject(()->s.accept(json(raw)));
            reject(s::text);reject(()->s.accept(json("{\"done\":true}")));
        }
        var overflow=new StructuredTextStream(3,4);
        overflow.accept(json("{\"message\":{\"content\":\"ab\"}}"));
        reject(()->overflow.accept(json("{\"message\":{\"content\":\"cd\"},\"done\":true}")));
        reject(overflow::text);
        var exact=new StructuredTextStream(3,4);
        exact.accept(json("{\"message\":{\"content\":\"abc\"},\"done\":true,\"eval_count\":0}"));
        ok(exact.text().equals("abc"));
        System.out.println("PASS: "+checks+" structured text-only stream checks");
    }
}
