package com.ollamapocket.app;
import java.util.List;
public final class CloudBranchTest {
    private static int n;
    private static void ok(boolean b,String msg){if(!b)throw new AssertionError(msg);n++;}
    private static void fails(Runnable r,String msg){try{r.run();}catch(IllegalArgumentException|IllegalStateException e){n++;return;}throw new AssertionError(msg);}
    public static void main(String[] args){
        CloudHistory h=new CloudHistory();
        CloudHistory.Session original=h.session("OPENAI","a");
        original.complete("first","answer");original.complete("second","two");original.complete("third","three");
        original.draft("unsent original");
        long rev=original.revision();
        CloudHistory.Session branch=h.fork("OPENAI","a",original,rev,1,"edited second");
        ok(branch!=original&&branch.branched,"new branch identity");
        ok(branch.turns().size()==1&&branch.turns().get(0).answer.equals("answer"),"prefix only");
        ok(original.turns().size()==3&&original.draft().equals("unsent original"),"original intact");
        ok(branch.draft().equals("edited second"),"draft isolated");
        List<CloudHistory.Message> request=branch.request(branch.draft());
        ok(request.size()==3&&request.get(2).content.equals("edited second"),"request excludes later turns");
        ok(branch.turns().size()==1,"no committed edit before successful response");
        fails(()->h.select("CLAUDE","a",branch),"cross provider rejected");
        fails(()->h.select("OPENAI","b",branch),"cross model rejected");
        fails(()->h.fork("OPENAI","a",original,rev,1,"stale"),"inactive source rejected");
        branch.complete("edited second","new reply");
        ok(branch.draft().isEmpty()&&original.turns().get(1).answer.equals("two"),"completion isolated");
        h.select("OPENAI","a",original);
        ok(h.session("OPENAI","a")==original&&original.draft().equals("unsent original"),"switch and restore draft");
        fails(()->h.fork("OPENAI","a",original,rev-1,0,"bad"),"stale revision rejected");
        CloudHistory.Session fresh=h.newSession("OPENAI","a");
        ok(fresh.turns().isEmpty()&&h.conversations("OPENAI","a").size()==3,"new preserves old");
        h.removeCurrent("OPENAI","a",fresh,fresh.revision());
        ok(h.conversations("OPENAI","a").size()==2,"remove selected only");
        ok(original.turns().size()==3&&branch.turns().size()==2,"other branches intact");
        for(int i=0;i<14;i++)h.newSession("OPENAI","a");
        fails(()->h.newSession("OPENAI","a"),"global cap");
        fails(()->h.session("CLAUDE","a"),"global cap includes providers");
        h.clear();
        ok(h.session("CLAUDE","a").turns().isEmpty()&&original.turns().isEmpty(),"memory clear");
        System.out.println("CloudBranchTest PASS: "+n+" assertions");
    }
}
