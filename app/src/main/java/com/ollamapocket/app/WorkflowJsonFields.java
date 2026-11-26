package com.ollamapocket.app;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Strict bounded JSON syntax and exact top-level field types for NEW workflow gates.
 * Legacy JSON_KEYS gates deliberately retain their old behavior.
 */
final class WorkflowJsonFields {
    private static final int MAX_DEPTH=12,MAX_NODES=512;
    private WorkflowJsonFields(){}
    static Map<String,String> fields(String spec){
        if(spec==null||spec.isEmpty()||spec.length()>200)throw new IllegalArgumentException("Field specification");
        Map<String,String> result=new LinkedHashMap<>();
        for(String pair:spec.split(",",-1)){
            String[] parts=pair.trim().split(":",-1);
            if(parts.length!=2)throw new IllegalArgumentException("Field type required");
            String name=parts[0].trim(),type=parts[1].trim();
            if(!name.matches("[A-Za-z0-9_.-]{1,64}")||
                !java.util.Arrays.asList("string","number","integer","boolean","object","array").contains(type)||
                result.putIfAbsent(name,type)!=null||result.size()>12)
                throw new IllegalArgumentException("Invalid field specification");
        }
        return result;
    }
    static boolean matches(String spec,String output){
        if(output==null||output.length()>WorkflowPolicy.MAX_OUTPUT)return false;
        try{
            Map<String,String> expected=fields(spec);
            Parser parser=new Parser(output);
            parser.space();
            Map<String,String> actual=parser.object(0);
            parser.space();if(parser.pos!=output.length()||!actual.keySet().equals(expected.keySet()))return false;
            for(Map.Entry<String,String> field:expected.entrySet()){
                String type=actual.get(field.getKey());
                if(!field.getValue().equals(type)&&!("number".equals(field.getValue())&&"integer".equals(type)))return false;
            }
            return true;
        }catch(IllegalArgumentException e){return false;}
    }
    private static final class Parser {
        final String source;int pos,nodes;
        Parser(String source){this.source=source;}
        IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid JSON");}
        void space(){while(pos<source.length()&&" \t\r\n".indexOf(source.charAt(pos))>=0)pos++;}
        boolean take(char ch){space();if(pos<source.length()&&source.charAt(pos)==ch){pos++;return true;}return false;}
        void require(char ch){if(!take(ch))throw invalid();}
        void bound(int depth){if(depth>MAX_DEPTH||++nodes>MAX_NODES)throw invalid();}
        Map<String,String> object(int depth){
            bound(depth);require('{');Map<String,String> fields=new LinkedHashMap<>();
            if(take('}'))return fields;
            do{
                space();String name=string();require(':');String type=value(depth+1);
                if(fields.putIfAbsent(name,type)!=null)throw invalid();
                if(take('}'))return fields;
                require(',');
            }while(true);
        }
        void array(int depth){
            bound(depth);require('[');if(take(']'))return;
            do{value(depth+1);if(take(']'))return;require(',');}while(true);
        }
        String string(){
            if(pos>=source.length()||source.charAt(pos++)!='"')throw invalid();
            StringBuilder out=new StringBuilder();
            while(pos<source.length()){
                char c=source.charAt(pos++);
                if(c=='"'){
                    String s=out.toString();
                    for(int i=0;i<s.length();i++){
                        char x=s.charAt(i);
                        if(Character.isHighSurrogate(x)){if(++i>=s.length()||!Character.isLowSurrogate(s.charAt(i)))throw invalid();}
                        else if(Character.isLowSurrogate(x))throw invalid();
                    }
                    return s;
                }
                if(c<32)throw invalid();
                if(c!='\\'){out.append(c);continue;}
                if(pos>=source.length())throw invalid();
                char e=source.charAt(pos++);
                switch(e){
                    case '"':case '\\':case '/':out.append(e);break;
                    case 'b':out.append('\b');break;case 'f':out.append('\f');break;
                    case 'n':out.append('\n');break;case 'r':out.append('\r');break;case 't':out.append('\t');break;
                    case 'u':
                        if(pos+4>source.length())throw invalid();
                        int hex=0;
                        for(int i=0;i<4;i++){
                            char h=source.charAt(pos++);int digit=h>='0'&&h<='9'?h-'0':h>='a'&&h<='f'?h-'a'+10:h>='A'&&h<='F'?h-'A'+10:-1;
                            if(digit<0)throw invalid();hex=hex*16+digit;
                        }
                        out.append((char)hex);break;
                    default:throw invalid();
                }
            }
            throw invalid();
        }
        String value(int depth){
            space();if(pos>=source.length())throw invalid();
            char ch=source.charAt(pos);
            if(ch=='{'){object(depth);return "object";}
            if(ch=='['){array(depth);return "array";}
            bound(depth);
            if(ch=='"'){string();return "string";}
            if(source.startsWith("true",pos)){pos+=4;return "boolean";}
            if(source.startsWith("false",pos)){pos+=5;return "boolean";}
            if(source.startsWith("null",pos)){pos+=4;return "null";}
            int start=pos;
            if(ch=='-')pos++;
            if(pos>=source.length())throw invalid();
            if(source.charAt(pos)=='0')pos++;
            else{
                if(source.charAt(pos)<'1'||source.charAt(pos)>'9')throw invalid();
                digits();
            }
            boolean fractional=false;
            if(pos<source.length()&&source.charAt(pos)=='.'){pos++;fractional=true;int from=pos;digits();if(pos==from)throw invalid();}
            if(pos<source.length()&&(source.charAt(pos)=='e'||source.charAt(pos)=='E')){
                pos++;fractional=true;if(pos<source.length()&&(source.charAt(pos)=='+'||source.charAt(pos)=='-'))pos++;
                int from=pos;digits();if(pos==from)throw invalid();
            }
            if(pos-start>128)throw invalid();
            // Reject non-finite numbers; integer is a JSON integer literal, not a rounded decimal.
            double number=Double.parseDouble(source.substring(start,pos));
            if(!Double.isFinite(number))throw invalid();
            return fractional?"number":"integer";
        }
        void digits(){while(pos<source.length()&&source.charAt(pos)>='0'&&source.charAt(pos)<='9')pos++;}
    }
}
