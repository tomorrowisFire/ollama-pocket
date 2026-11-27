package com.ollamapocket.app;

public final class WorkspaceTextAnchorTest {
    private static int n;
    private static void ok(boolean value){if(!value)throw new AssertionError("anchor "+n);n++;}
    private static void rejects(Runnable action){
        try{action.run();}catch(IllegalArgumentException expected){n++;return;}
        throw new AssertionError("Invalid anchor accepted");
    }
    private static void anchor(WorkspaceTextPolicy.Snapshot s,String query,int start){
        WorkspaceTextPolicy.Window w=s.atMatch(query,start,start+query.length());
        ok(w.hasMatch()&&w.matchStart==start&&w.matchEnd==start+query.length());
        ok(w.start<=start&&w.end>=w.matchEnd&&w.end-w.start<=WorkspaceTextPolicy.PAGE);
        ok(w.text().substring(start-w.start,w.matchEnd-w.start).equals(query));
        ok(!Character.isLowSurrogate(w.text().charAt(0))&&!Character.isHighSurrogate(w.text().charAt(w.text().length()-1)));
        ok(s.copy(w,false,s.text).equals(w.text())&&s.copy(w,true,s.text).equals(s.text));
        WorkspaceTextPolicy.Window p=s.previous(w),next=s.next(w);
        ok(p==null?w.start==0:p.end==w.start);
        ok(next==null?w.end==s.length():next.start==w.end);
    }
    public static void main(String[] args){
        String q="한글😀";
        String text=q+"x".repeat(3995)+q+"y".repeat(4200)+q;
        WorkspaceTextPolicy.Snapshot s=WorkspaceTextPolicy.review(text);
        int second=text.indexOf(q,q.length()),third=text.lastIndexOf(q);
        anchor(s,q,0);anchor(s,q,second);anchor(s,q,third);
        WorkspaceTextPolicy.Window w=s.atMatch(q,second,second+q.length());
        ok(s.find(q,w,true).matchStart==third&&s.find(q,w,false).matchStart==0);
        ok(s.find(q,s.atMatch(q,0,q.length()),false)==null);
        ok(s.find(q,s.atMatch(q,third,third+q.length()),true)==null);
        ok(s.first().start==0&&!s.first().hasMatch());
        rejects(()->s.atMatch(q,-1,3));rejects(()->s.atMatch(q,3,2));
        rejects(()->s.atMatch(q,0,Integer.MAX_VALUE));rejects(()->s.atMatch(q,0,3));
        rejects(()->s.atMatch(q,1,1+q.length()));rejects(()->s.atMatch("한글X",0,3));
        rejects(()->s.atMatch("",0,0));rejects(()->s.atMatch(null,0,1));
        rejects(()->s.atMatch("\uD800",2,3));rejects(()->s.atMatch("\uDC00",3,4));
        rejects(()->s.atMatch("x".repeat(161),4,165));
        rejects(()->s.copy(w,true,text+"x"));
        WorkspaceTextPolicy.Snapshot caseSensitive=WorkspaceTextPolicy.review("Test test");
        rejects(()->caseSensitive.atMatch("test",0,4));anchor(caseSensitive,"test",5);
        String limit="😀".repeat(80);
        WorkspaceTextPolicy.Snapshot max=WorkspaceTextPolicy.review("x".repeat(159840)+limit);
        anchor(max,limit,159840);
        for(int i=0;i<64;i++){
            String original="😀".repeat(i+1985)+"abc"+"😀".repeat(2010-i);
            WorkspaceTextPolicy.Snapshot snap=WorkspaceTextPolicy.review(original);
            anchor(snap,"abc",(i+1985)*2);
        }
        System.out.println("PASS: "+n+" exact knowledge-search anchor assertions");
    }
}
