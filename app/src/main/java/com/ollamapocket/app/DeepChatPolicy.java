package com.ollamapocket.app;

/** Process-local, monotonic reward grant. Not a production purchase verifier or wall-clock lease. */
final class DeepChatPolicy {
    static final long PASS_MILLIS=30L*60*1000;
    static final long REWARD_FLOW_MILLIS=10L*60*1000;
    static final int NORMAL_TEXT_BYTES=1024*1024,NORMAL_STRUCTURED_BYTES=8*1024*1024;
    static final int NORMAL_SEGMENTS=256;
    private DeepChatPolicy(){}
    static int normalLimit(boolean structured){return structured?NORMAL_STRUCTURED_BYTES:NORMAL_TEXT_BYTES;}
    static boolean normalSegment(int segment){return segment>=1&&segment<=NORMAL_SEGMENTS;}
    static boolean mayStart(boolean advanced,boolean authorized){return !advanced||authorized;}
    /** A single reviewed SDK reward excursion may retain an idle, secure workspace.
     * No access grant, inference or persistence follows from this lifecycle token. */
    static final class WorkspaceReward {
        private long started=-1;
        private boolean pending,usedPause,suspended;
        boolean begin(long now){
            if(now<0||blocked())return false;
            started=now;pending=true;usedPause=false;return true;
        }
        boolean fresh(long now){return started>=0&&now>=started&&now-started<REWARD_FLOW_MILLIS;}
        boolean preservePause(boolean sdkShowing,long now){
            if(!pending||usedPause||suspended||!sdkShowing||!fresh(now))return false;
            usedPause=true;suspended=true;return true;
        }
        boolean resume(long now){
            if(blocked()&&!fresh(now))return false;
            suspended=false;return true;
        }
        void finish(){pending=false;}
        boolean blocked(){return pending||suspended;}
        boolean expired(long now){return blocked()&&!fresh(now);}
    }
    static final class Access {
        private long granted=-1,lastDisplayedEnd=-1;
        private Ticket pending;
        static final class Ticket {
            private final long started;
            private boolean showing,earned;
            private long displayedAt=-1;
            private Ticket(long now){started=now;}
        }
        synchronized long remaining(long now,boolean owned){
            if(owned)return Long.MAX_VALUE;
            if(granted<0||now<granted||now-granted>=PASS_MILLIS)return 0;
            return PASS_MILLIS-(now-granted);
        }
        synchronized boolean authorized(long now,boolean owned){return remaining(now,owned)>0;}
        synchronized Ticket begin(long now){
            if(now<0||pending!=null)return null;
            pending=new Ticket(now);return pending;
        }
        synchronized boolean active(Ticket ticket){return ticket!=null&&pending==ticket;}
        private boolean within(Ticket ticket,long now){
            return active(ticket)&&now>=ticket.started&&now-ticket.started<REWARD_FLOW_MILLIS;
        }
        synchronized boolean show(Ticket ticket,long now){
            if(!within(ticket,now)||ticket.showing)return false;
            ticket.showing=true;ticket.displayedAt=now;return true;
        }
        /** Only an SDK reward callback on the matching displayed ad can grant time. */
        synchronized boolean earned(Ticket ticket,long now){
            if(!within(ticket,now)||!ticket.showing||ticket.earned||now<ticket.displayedAt||
                    granted>=0&&now<granted)return false;
            ticket.earned=true;granted=now;return true;
        }
        synchronized boolean finish(Ticket ticket,long now){
            if(!active(ticket))return false;
            if(ticket.showing&&now>=ticket.displayedAt)
                lastDisplayedEnd=Math.max(lastDisplayedEnd,now);
            pending=null;return true;
        }
        synchronized boolean expired(Ticket ticket,long now){return active(ticket)&&!within(ticket,now);}
        synchronized boolean suppressAppOpen(long now){
            return pending!=null||lastDisplayedEnd>=0&&(now<lastDisplayedEnd||now-lastDisplayedEnd<120_000L);
        }
        synchronized boolean busy(){return pending!=null;}
    }
}
