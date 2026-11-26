package com.ollamapocket.app;

public final class CloudViewportPolicyTest {
    private static int checks;
    private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("cloud viewport "+checks);}
    public static void main(String[] args){
        CloudViewportPolicy p=new CloudViewportPolicy();
        long first=p.ticket();check(p.following()&&p.shouldFollow(first));
        p.observed(1000,400,200,96);check(!p.following()&&!p.accepts(first));
        long reading=p.ticket();check(!p.shouldFollow(reading)&&p.accepts(reading));
        p.observed(1500,400,200,96);check(!p.accepts(reading)&&!p.following());
        long newerReading=p.ticket();
        p.observed(1500,0,1100,96);check(p.accepts(newerReading)&&!p.following());
        p.observed(1500,400,1004,96);check(p.following()&&!p.accepts(reading));
        long latest=p.ticket();p.invalidate();check(!p.accepts(latest)&&p.following());
        p.observed(2000,400,0,96);check(!p.following());long old=p.ticket();
        p.reset();check(p.following()&&!p.accepts(old));
        check(CloudViewportPolicy.shortTitle(null,38).isEmpty());
        check(CloudViewportPolicy.shortTitle(" A\nB\tC\r ",38).equals("A B C"));
        check(CloudViewportPolicy.shortTitle("가나다",3).equals("가나다"));
        check(CloudViewportPolicy.shortTitle("a".repeat(37)+"😀끝",38).equals("a".repeat(37)+"…"));
        check(CloudViewportPolicy.shortTitle("a".repeat(36)+"😀끝",38).equals("a".repeat(36)+"😀…"));
        check(CloudViewportPolicy.shortTitle("😀x",1).equals("…"));
        check(CloudViewportPolicy.shortTitle("😀x",2).equals("😀…"));
        check(CloudViewportPolicy.shortTitle("x",0).isEmpty());
        System.out.println("PASS: "+checks+" cloud viewport and Unicode title assertions");
    }
}
