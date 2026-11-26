package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory cloud-only conversations, isolated by provider and exact model ID. */
final class CloudHistory {
    static final int MAX_PROMPT=16000, MAX_REPLY=48000, MAX_CONTEXT=64000, MAX_STORED=96000;
    private static final int MAX_TURNS=12, MAX_SESSIONS=16;
    static final class Message {
        final String role, content;
        Message(String role,String content){this.role=role;this.content=content;}
    }
    static final class Turn {
        final String question, answer;
        Turn(String q,String a){question=q;answer=a;}
        int chars(){return question.length()+answer.length();}
    }
    static final class Session {
        private final List<Turn> turns=new ArrayList<>();
        List<Turn> turns(){return Collections.unmodifiableList(new ArrayList<>(turns));}
        void clear(){turns.clear();}
        void complete(String prompt,String answer){
            check(prompt,MAX_PROMPT);check(answer,MAX_REPLY);
            turns.add(new Turn(prompt,answer));
            int chars=0;for(Turn t:turns)chars+=t.chars();
            while(turns.size()>MAX_TURNS||chars>MAX_STORED)chars-=turns.remove(0).chars();
        }
        List<Message> request(String prompt){
            check(prompt,MAX_PROMPT);
            int chars=prompt.length(),first=turns.size();
            for(int i=turns.size()-1;i>=0;i--){
                Turn t=turns.get(i);
                if(chars+t.chars()>MAX_CONTEXT)break;
                chars+=t.chars();first=i;
            }
            List<Message> messages=new ArrayList<>();
            for(int i=first;i<turns.size();i++){
                Turn t=turns.get(i);
                messages.add(new Message("user",t.question));
                messages.add(new Message("assistant",t.answer));
            }
            messages.add(new Message("user",prompt));
            return Collections.unmodifiableList(messages);
        }
    }
    private final Map<String,Session> sessions=new LinkedHashMap<>();
    Session session(String provider,String model){
        if(provider==null||provider.isEmpty()||provider.indexOf('\0')>=0||model==null||model.trim().isEmpty()||model.length()>200||model.indexOf('\0')>=0)
            throw new IllegalArgumentException("Invalid cloud scope");
        String scope=provider+"\0"+model;
        Session existing=sessions.get(scope);if(existing!=null)return existing;
        if(sessions.size()>=MAX_SESSIONS)throw new IllegalStateException("Cloud session limit");
        Session created=new Session();sessions.put(scope,created);return created;
    }
    void clear(){sessions.clear();}
    private static void check(String s,int max){
        if(s==null||s.trim().isEmpty()||s.length()>max)throw new IllegalArgumentException("Invalid message");
    }
}
