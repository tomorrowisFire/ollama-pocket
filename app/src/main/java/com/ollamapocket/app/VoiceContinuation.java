package com.ollamapocket.app;

/** Main-thread ownership of an optional, one-shot voice continuation. */
final class VoiceContinuation {
    static final class Ticket { private Ticket(){} }
    private Runnable action;
    private Ticket scheduled;
    void replace(Runnable next){action=next;scheduled=null;}
    void clear(){replace(null);}
    boolean pending(){return action!=null&&scheduled==null;}
    Ticket schedule(){
        if(!pending())return null;
        scheduled=new Ticket();return scheduled;
    }
    Runnable consume(Ticket expected){
        if(expected==null||scheduled!=expected)return null;
        Runnable next=action;clear();return next;
    }
}
