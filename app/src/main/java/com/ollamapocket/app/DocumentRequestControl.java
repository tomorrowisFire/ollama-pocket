package com.ollamapocket.app;

import java.net.HttpURLConnection;
import java.util.Objects;

/** One-shot request ownership; transport operations never run under this monitor. */
final class DocumentRequestControl<R> {
    private boolean started,finished;
    private R reason;
    private HttpURLConnection active;

    synchronized boolean begin(){
        if(started||finished)return false;
        started=true;return true;
    }
    synchronized R reason(){return reason;}
    synchronized boolean stop(R value){
        Objects.requireNonNull(value);
        if(finished||reason!=null)return false;
        reason=value;return true;
    }
    synchronized boolean attach(HttpURLConnection connection){
        Objects.requireNonNull(connection);
        if(!started||finished||reason!=null||active!=null)return false;
        active=connection;return true;
    }
    synchronized boolean release(HttpURLConnection expected){
        if(expected==null||active!=expected)return false;
        active=null;return true;
    }
    /** Only the watchdog claims a stopped request for asynchronous disconnection. */
    synchronized HttpURLConnection takeStopped(){
        if(reason==null)return null;
        HttpURLConnection current=active;active=null;return current;
    }
    /** Seal the reason before shutdown, returning any remaining connection for cleanup. */
    synchronized HttpURLConnection finish(){
        finished=true;
        HttpURLConnection current=active;active=null;return current;
    }
}
