package com.ollamapocket.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded metadata-only journal. No prompts, answers, endpoint strings or exception bodies. */
final class WorkflowRunJournal {
    static final int LIMIT=96;
    enum Reason {
        NONE, UNKNOWN, VERIFIED, VERIFY_FAILED, TRUNCATED, ATTEMPT_LIMIT,
        USER_PAUSE, USER_CANCEL, RECOVERED, TIME_LIMIT, THERMAL, MEMORY,
        SERVER_OFF, COOP_ACTIVE, OUTPUT_LIMIT, INVALID_TOKENS, TRANSPORT, INTERRUPTED
    }
    enum State { QUEUED, RUNNING, VERIFYING, RETRY_WAIT, PAUSED, BLOCKED, COMPLETED, CANCELLED }
    static final class Event {
        final long at;
        final State state;
        final Reason reason;
        final int completed,attempt,total;
        Event(long at,State state,Reason reason,int completed,int attempt,int total){
            this.at=at;this.state=state;this.reason=reason;this.completed=completed;this.attempt=attempt;this.total=total;
        }
        boolean matches(State s,Reason r,int done,int a,int t){
            return state==s&&reason==r&&completed==done&&attempt==a&&total==t;
        }
        JSONObject json()throws Exception{return new JSONObject().put("at",at).put("state",state.name()).put("reason",reason.name())
            .put("completed",completed).put("attempt",attempt).put("total",total);}
    }
    private final List<Event> events=new ArrayList<>();
    List<Event> events(){return Collections.unmodifiableList(new ArrayList<>(events));}
    static boolean interruptedState(String state){return "RUNNING".equals(state)||"VERIFYING".equals(state);}
    static Reason stopReason(boolean cancelled,boolean paused,boolean expired,InferenceSafetyPolicy.Reason risk,boolean serverRunning,boolean coopOff){
        if(cancelled)return Reason.USER_CANCEL;
        if(paused)return Reason.USER_PAUSE;
        if(expired)return Reason.TIME_LIMIT;
        if(risk==InferenceSafetyPolicy.Reason.THERMAL)return Reason.THERMAL;
        if(risk==InferenceSafetyPolicy.Reason.LOW_MEMORY)return Reason.MEMORY;
        if(!serverRunning)return Reason.SERVER_OFF;
        if(!coopOff)return Reason.COOP_ACTIVE;
        return null;
    }
    static State stopState(Reason reason){
        if(reason==Reason.USER_CANCEL)return State.CANCELLED;
        if(reason==Reason.USER_PAUSE||reason==Reason.INTERRUPTED||reason==Reason.RECOVERED)return State.PAUSED;
        return State.BLOCKED;
    }
    static Reason failure(Exception error){
        if(error instanceof java.net.SocketTimeoutException)return Reason.TRANSPORT;
        String code=error.getMessage();
        if("WORKFLOW_OUTPUT_LIMIT".equals(code)||"CHAT_STREAM_LIMIT".equals(code))return Reason.OUTPUT_LIMIT;
        if("WORKFLOW_INVALID_TOKENS".equals(code))return Reason.INVALID_TOKENS;
        return Reason.TRANSPORT;
    }
    private static void validate(Event event,int steps,long started){
        if(event.at<started||started<=0||steps<1||steps>WorkflowPolicy.MAX_STEPS||
            event.completed<0||event.completed>steps||event.attempt<0||event.attempt>WorkflowPolicy.MAX_ATTEMPTS||
            event.total<0||event.total>30||(event.state==State.COMPLETED)!=(event.completed==steps))
            throw new IllegalArgumentException("Journal event range");
        if(event.reason==Reason.VERIFIED&&event.state!=State.QUEUED&&event.state!=State.COMPLETED)
            throw new IllegalArgumentException("Journal verification state");
    }
    void record(long at,String state,Reason reason,int completed,int attempt,int total,int steps,long started){
        Event next=new Event(at,State.valueOf(state),reason,completed,attempt,total);validate(next,steps,started);
        if(!events.isEmpty()){
            Event last=events.get(events.size()-1);
            if(next.at<last.at||next.completed<last.completed||next.total<last.total||last.state==State.COMPLETED||last.state==State.CANCELLED){
                if(last.matches(next.state,next.reason,next.completed,next.attempt,next.total)&&next.at>=last.at)return;
                throw new IllegalArgumentException("Journal order");
            }
            if(last.matches(next.state,next.reason,next.completed,next.attempt,next.total))return;
        }
        if(events.size()>=LIMIT)events.remove(0);
        events.add(next);
    }
    JSONArray json()throws Exception{JSONArray array=new JSONArray();for(Event event:events)array.put(event.json());return array;}
    static WorkflowRunJournal read(JSONArray raw,int steps,long started)throws Exception{
        if(raw==null||raw.length()>LIMIT)throw new IllegalArgumentException("Journal limit");
        WorkflowRunJournal result=new WorkflowRunJournal();
        for(int i=0;i<raw.length();i++){
            JSONObject row=raw.getJSONObject(i);
            result.record(row.getLong("at"),row.getString("state"),Reason.valueOf(row.getString("reason")),
                row.getInt("completed"),row.getInt("attempt"),row.getInt("total"),steps,started);
        }
        if(result.events.size()!=raw.length())throw new IllegalArgumentException("Duplicate journal event");
        return result;
    }
    boolean matchesLast(String state,Reason reason,int completed,int attempt,int total,long updated){
        if(events.isEmpty())return reason==Reason.UNKNOWN; // old checkpoint, no invented historical events
        Event last=events.get(events.size()-1);
        return last.at<=updated&&last.matches(State.valueOf(state),reason,completed,attempt,total);
    }
}
