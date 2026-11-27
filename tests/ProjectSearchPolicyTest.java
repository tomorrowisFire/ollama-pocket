package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ProjectSearchPolicyTest {
    private static int n;
    private static void ok(boolean b){if(!b)throw new AssertionError("project search "+n);n++;}
    private static void rejects(Runnable r){try{r.run();}catch(IllegalArgumentException e){n++;return;}throw new AssertionError("Invalid input accepted");}
    private static ProjectSearchPolicy.Source source(String id,ProjectSearchPolicy.Kind kind,String text){
        boolean artifact=kind==ProjectSearchPolicy.Kind.ARTIFACT_TITLE||kind==ProjectSearchPolicy.Kind.CURRENT_TEXT;
        return new ProjectSearchPolicy.Source(id,artifact?"a":null,"Project",artifact?"Artifact":null,kind,kind==ProjectSearchPolicy.Kind.CURRENT_TEXT?2:0,text);
    }
    private static ProjectSearchPolicy.Result search(List<ProjectSearchPolicy.Source> sources,String query){return ProjectSearchPolicy.search(sources,query);}
    public static void main(String[] args){
        var title=source("p",ProjectSearchPolicy.Kind.PROJECT_TITLE,"한글 日本語 😀");
        var notes=source("p",ProjectSearchPolicy.Kind.INSTRUCTIONS,"Hello hello hello");
        var artifact=source("p",ProjectSearchPolicy.Kind.ARTIFACT_TITLE,"hello artifact");
        var version=source("q",ProjectSearchPolicy.Kind.CURRENT_TEXT,"x hello\nhello");
        List<ProjectSearchPolicy.Source> sources=Arrays.asList(title,notes,artifact,version);
        var r=search(sources,"hello");ok(r.hits.size()==3&&!r.truncated);
        ok(r.hits.get(0).source==notes&&r.hits.get(0).start==6&&r.hits.get(0).end==11);
        ok(r.hits.get(1).source==artifact&&r.hits.get(1).start==0);
        ok(r.hits.get(2).source==version&&r.hits.get(2).start==2&&r.hits.get(2).source.ordinal==2&&r.hits.get(2).source.projectId.equals("q"));
        for(var h:r.hits){ok(h.source.text.substring(h.start,h.end).equals("hello"));ok(h.viewerCompatible("hello"));ok(!h.viewerCompatible("HELLO"));}
        ok(search(sources,"Hello").hits.size()==1);ok(search(sources,"HELLO").hits.isEmpty());
        ok(search(sources,"hello ").hits.size()==2);ok(search(sources,"日本語").hits.get(0).source==title);
        var emoji=search(sources,"😀").hits.get(0);ok(emoji.end-emoji.start==2&&emoji.viewerCompatible("😀"));
        ok(search(Collections.emptyList(),"x").hits.isEmpty());
        rejects(()->search(sources,""));rejects(()->search(sources," \t"));rejects(()->search(sources,"\u0000"));rejects(()->search(sources,"\uD800"));rejects(()->search(sources,"a".repeat(161)));
        rejects(()->search(null,"x"));rejects(()->search(Arrays.asList(title,null),"x"));
        rejects(()->source("p",ProjectSearchPolicy.Kind.CURRENT_TEXT,"\uDC00"));
        rejects(()->source("p",ProjectSearchPolicy.Kind.CURRENT_TEXT,"a".repeat(32001)));
        var large=source("p",ProjectSearchPolicy.Kind.CURRENT_TEXT,"😀"+"a".repeat(59)+"needle"+"b".repeat(59)+"😃"+"c".repeat(100));
        var h=search(List.of(large),"needle").hits.get(0);ok(h.contextStart==0&&h.contextEnd==128);ok(h.context().startsWith("😀")&&h.context().endsWith("😃"));ok(h.viewerCompatible("needle"));
        var nul=source("p",ProjectSearchPolicy.Kind.INSTRUCTIONS,"a\u0000needle");var nh=search(List.of(nul),"needle").hits.get(0);ok(nh.start==2&&!nh.viewerCompatible("needle"));
        List<ProjectSearchPolicy.Source> fields=new ArrayList<>();for(int i=0;i<40;i++)fields.add(source("p"+i,ProjectSearchPolicy.Kind.INSTRUCTIONS,"x"));
        ok(search(fields,"x").hits.size()==40&&!search(fields,"x").truncated);
        fields.add(source("p40",ProjectSearchPolicy.Kind.INSTRUCTIONS,"not a match"));ok(!search(fields,"x").truncated);
        fields.add(source("p41",ProjectSearchPolicy.Kind.INSTRUCTIONS,"x"));ok(search(fields,"x").truncated);
        for(int i=0;i<40;i++)ok(search(fields,"x").hits.get(i).source==fields.get(i));
        rejects(()->search(Collections.nCopies(505,title),"x"));
        var max=source("p",ProjectSearchPolicy.Kind.CURRENT_TEXT,"x".repeat(32000));ok(search(List.of(max),"x".repeat(160)).hits.get(0).end==160);
        rejects(()->search(Collections.nCopies(66,max),"x"));
        try{r.hits.clear();throw new AssertionError("mutable hits");}catch(UnsupportedOperationException e){n++;}
        ok(sources.get(1).text.equals("Hello hello hello")&&sources.size()==4);
        for(int i=0;i<500;i++){
            String value="😀".repeat(i%31)+"a".repeat(i)+"target"+"😃".repeat(i%53);
            var item=source("s",ProjectSearchPolicy.Kind.CURRENT_TEXT,value);var hit=search(List.of(item),"target").hits.get(0);
            ok(hit.start==value.indexOf("target")&&hit.end==hit.start+6);
            ok(hit.context().substring(hit.start-hit.contextStart,hit.end-hit.contextStart).equals("target"));
            ok(hit.context().length()<=128&&hit.viewerCompatible("target"));
        }
        System.out.println("PASS: "+n+" project search assertions");
    }
}
