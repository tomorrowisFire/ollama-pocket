package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure local-chat policy. Persisted history is never trimmed by request construction. */
final class LocalChatTurns {
    static final int MAX_PROMPT=16000, MAX_REPLY=160000;
    static final int MAX_STORED_MESSAGES=256, MAX_STORED_JSON_CHARS=512000;
    static final int MAX_CONTEXT_CHARS=48000, MAX_CONTEXT_PAIRS=15;
    static final class Message {
        final String role, content;
        Message(String role,String content){this.role=role;this.content=content;}
        boolean user(){return "user".equals(role)&&content!=null&&!content.trim().isEmpty();}
        boolean assistant(){return "assistant".equals(role)&&content!=null&&!content.trim().isEmpty();}
    }
    static final class Branch {
        final int prefixLength;
        final String prompt;
        Branch(int prefixLength,String prompt){this.prefixLength=prefixLength;this.prompt=prompt;}
    }
    private LocalChatTurns(){}
    static boolean validPrompt(String prompt){
        return prompt!=null&&!prompt.trim().isEmpty()&&prompt.length()<=MAX_PROMPT;
    }
    static boolean validReply(String answer){
        return answer!=null&&!answer.trim().isEmpty()&&answer.length()<=MAX_REPLY;
    }
    static boolean storageFits(int messages,int serializedChars){
        return messages>=0&&messages<=MAX_STORED_MESSAGES&&serializedChars>=0&&serializedChars<=MAX_STORED_JSON_CHARS;
    }
    static Branch branch(List<Message> source,int selected){
        if(source==null||selected<0||selected>=source.size())throw new IllegalArgumentException("Invalid selected message");
        Message m=source.get(selected);
        if(m!=null&&m.assistant())selected--;
        if(selected<0)throw new IllegalArgumentException("No paired user question");
        Message question=source.get(selected);
        if(question==null||!question.user()||!validPrompt(question.content))
            throw new IllegalArgumentException("No valid user question");
        return new Branch(selected,question.content);
    }
    static List<Message> request(List<Message> source,String prompt){
        if(!validPrompt(prompt))throw new IllegalArgumentException("Invalid prompt");
        List<Message> out=new ArrayList<>(2*MAX_CONTEXT_PAIRS+1);
        int chars=prompt.length();
        // Walk the stored suffix only. Adjacent user/assistant pairs cannot overlap.
        // Preserve the old whole-pair budget and orphan handling exactly.
        if(source!=null){
            java.util.ListIterator<Message> cursor=source.listIterator(source.size());
            Message following=null;
            while(cursor.hasPrevious()&&out.size()/2<MAX_CONTEXT_PAIRS){
                Message candidate=cursor.previous();
                if(candidate!=null&&following!=null&&candidate.user()&&following.assistant()){
                    long next=(long)chars+candidate.content.length()+following.content.length();
                    if(next>MAX_CONTEXT_CHARS)break;
                    out.add(following);out.add(candidate);chars=(int)next;
                    following=null;
                }else following=candidate;
            }
            Collections.reverse(out);
        }
        out.add(new Message("user",prompt));
        return Collections.unmodifiableList(out);
    }
    static boolean gpuTerminal(String finishReason){
        return "stop".equals(finishReason)||"length".equals(finishReason);
    }
}
