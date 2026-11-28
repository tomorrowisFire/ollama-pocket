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
    static final class Access {
        private long granted=-1;
        private Ticket pending;
        static final class Ticket {
            private final long started;
            private boolean showing,earned;
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
            ticket.showing=true;return true;
        }
        /** Only an SDK reward callback on the matching displayed ad can grant time. */
        synchronized boolean earned(Ticket ticket,long now){
            if(!within(ticket,now)||!ticket.showing||ticket.earned)return false;
            ticket.earned=true;granted=now;return true;
        }
        synchronized boolean finish(Ticket ticket){
            if(!active(ticket))return false;
            pending=null;return true;
        }
        synchronized boolean busy(){return pending!=null;}
    }
}
