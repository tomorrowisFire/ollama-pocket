package com.ollamapocket.app;
import org.json.JSONObject;
public final class McpInputPolicyTest {
    private static int n;
    private static void ok(boolean pass){if(!pass)throw new AssertionError("check "+n);n++;}
    private static JSONObject j(String s)throws Exception{return new JSONObject(s);}
    public static void main(String[] args)throws Exception{
        JSONObject schema=j("{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":5},\"count\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":3},\"flags\":{\"type\":\"array\",\"items\":{\"type\":\"boolean\"},\"maxItems\":2}},\"required\":[\"query\"]}");
        ok(McpInputPolicy.supportsObject(schema));
        ok(McpInputPolicy.valid(schema,j("{\"query\":\"hi\",\"count\":2,\"flags\":[true,false]}")));
        ok(!McpInputPolicy.valid(schema,j("{}")));
        ok(!McpInputPolicy.valid(schema,j("{\"query\":\"\"}")));
        ok(!McpInputPolicy.valid(schema,j("{\"query\":\"abcdef\"}")));
        ok(!McpInputPolicy.valid(schema,j("{\"query\":\"x\",\"count\":1.5}")));
        ok(!McpInputPolicy.valid(schema,j("{\"query\":\"x\",\"count\":4}")));
        ok(!McpInputPolicy.valid(schema,j("{\"query\":\"x\",\"flags\":[true,1]}")));
        ok(!McpInputPolicy.valid(schema,j("{\"query\":\"x\",\"flags\":[true,false,true]}")));
        ok(!McpInputPolicy.valid(schema,j("{\"query\":\"x\",\"extra\":1}")));
        ok(!McpInputPolicy.supportsObject(j("{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\",\"pattern\":\"x\"}}}")));
        ok(!McpInputPolicy.supportsObject(j("{\"type\":\"object\",\"$ref\":\"https://example.test\"}")));
        ok(!McpInputPolicy.supportsObject(j("{\"type\":\"object\",\"required\":[\"missing\"]}")));
        ok(McpInputPolicy.valid(j("{\"type\":\"object\",\"properties\":{\"v\":{\"type\":\"number\",\"enum\":[1,2]}}}"),j("{\"v\":1.0}")));
        ok(!McpInputPolicy.valid(j("{\"type\":\"object\",\"properties\":{\"v\":{\"type\":\"number\",\"enum\":[1,2]}}}"),j("{\"v\":3}")));
        boolean rejected=false;try{McpInputPolicy.arguments("{} trailing");}catch(Exception e){rejected=true;}ok(rejected);
        System.out.println("PASS: "+n+" MCP input policy assertions");
    }
}
