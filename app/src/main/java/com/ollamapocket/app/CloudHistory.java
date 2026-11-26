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
        final String role,content;
        Message(String role,String content){this.role=role;this.content=content;}
    }
    static final class Turn {
        final String question,answer;
        Turn(String q,String a){question=q;answer=a;}
        int chars(){return question.length()+answer.length();}
    }
    static final class Session {
        final long id;
        final boolean branched;
        private final List<Turn> turns=new ArrayList<>();
        private String draft="";
        private long revision;
        private Session(long id,boolean branch){this.id=id;branched=branch;}
        List<Turn> turns(){return Collections.unmodifiableList(new ArrayList<>(turns));}
        long revision(){return revision;}
        String draft(){return draft;}
        void draft(String value){if(value==null||value.length()>MAX_PROMPT)throw new IllegalArgumentException("Invalid draft");draft=value;}
        void clear(){turns.clear();draft="";revision++;}
        void complete(String prompt,String answer){
            check(prompt,MAX_PROMPT);check(answer,MAX_REPLY);
            turns.add(new Turn(prompt,answer));
            int chars=0;for(Turn t:turns)chars+=t.chars();
            while(turns.size()>MAX_TURNS||chars>MAX_STORED)chars-=turns.remove(0).chars();
            draft="";revision++;
        }
        List<Message> request(String prompt){
            check(prompt,MAX_PROMPT);
            int chars=prompt.length(),first=turns.size();
            for(int i=turns.size()-1;i>=0;i--){
                Turn t=turns.get(i);if(chars+t.chars()>MAX_CONTEXT)break;
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
    private static final class Group {
        final List<Session> items=new ArrayList<>();
        Session active;
    }
    private final Map<String,Group> groups=new LinkedHashMap<>();
    private int count;
    private long nextId=1;
    private static String scope(String provider,String model){
        if(provider==null||provider.isEmpty()||provider.indexOf('\0')>=0||model==null||model.trim().isEmpty()||model.length()>200||model.indexOf('\0')>=0)
            throw new IllegalArgumentException("Invalid cloud scope");
        return provider+"\0"+model;
    }
    private Session allocate(Group g,boolean branch){
        if(count>=MAX_SESSIONS)throw new IllegalStateException("Cloud session limit");
        Session s=new Session(nextId++,branch);
        g.items.add(s);g.active=s;count++;return s;
    }
    Session session(String provider,String model){
        String key=scope(provider,model);
        Group g=groups.get(key);
        if(g!=null)return g.active;
        g=new Group();Session s=allocate(g,false);groups.put(key,g);return s;
    }
    List<Session> conversations(String provider,String model){
        session(provider,model);
        return Collections.unmodifiableList(new ArrayList<>(groups.get(scope(provider,model)).items));
    }
    Session newSession(String provider,String model){
        session(provider,model);
        return allocate(groups.get(scope(provider,model)),false);
    }
    boolean isCurrent(String provider,String model,Session source,long revision){
        Group g=groups.get(scope(provider,model));
        return g!=null&&g.active==source&&source!=null&&source.revision()==revision;
    }
    Session select(String provider,String model,Session selected){
        Group g=groups.get(scope(provider,model));
        if(g==null||!g.items.contains(selected))throw new IllegalArgumentException("Wrong cloud scope");
        g.active=selected;return selected;
    }
    Session fork(String provider,String model,Session source,long revision,int turnIndex,String question){
        check(question,MAX_PROMPT);
        if(!isCurrent(provider,model,source,revision)||turnIndex<0||turnIndex>=source.turns.size())
            throw new IllegalArgumentException("Stale branch source");
        Group g=groups.get(scope(provider,model));
        Session fork=allocate(g,true);
        fork.turns.addAll(source.turns.subList(0,turnIndex));
        fork.draft(question);return fork;
    }
    Session removeCurrent(String provider,String model,Session expected,long revision){
        if(!isCurrent(provider,model,expected,revision))throw new IllegalArgumentException("Stale cloud session");
        Group g=groups.get(scope(provider,model));
        g.items.remove(expected);count--;
        expected.clear();
        if(g.items.isEmpty())return allocate(g,false);
        g.active=g.items.get(g.items.size()-1);return g.active;
    }
    void clear(){
        for(Group g:groups.values())for(Session s:g.items)s.clear();
        groups.clear();count=0;
    }
    private static void check(String s,int max){
        if(s==null||s.trim().isEmpty()||s.length()>max)throw new IllegalArgumentException("Invalid message");
    }
}
