package com.ollamapocket.app;

public final class ProjectContextTest {
    private static int n;
    private static void ok(boolean v){if(!v)throw new AssertionError("context "+n);n++;}
    private static void reject(Runnable r){try{r.run();}catch(IllegalArgumentException e){n++;return;}throw new AssertionError("accepted invalid context");}
    public static void main(String[] args)throws Exception{
        ProjectLibrary.State empty=ProjectLibrary.empty();
        ProjectLibrary.State p=ProjectLibrary.createProject(empty,"p","Project","notes 😀");
        ProjectLibrary.State a=ProjectLibrary.createArtifact(p,"p","a","Artifact","first\nexact",1);
        ProjectLibrary.State v=ProjectLibrary.editArtifact(a,"p","a","latest",2);
        ProjectContext notes=ProjectContext.select(v,"p",null,0);
        ProjectContext old=ProjectContext.select(v,"p","a",1);
        ProjectContext latest=ProjectContext.select(v,"p","a",2);
        ok(notes.content.equals("notes 😀")&&old.content.equals("first\nexact")&&latest.content.equals("latest"));
        ok(old.ordinal==1&&latest.ordinal==2&&old.pid.equals("p")&&old.aid.equals("a"));
        String draft=old.appendTo("my draft");
        ok(draft.startsWith("my draft\n\n")&&draft.contains("Artifact (a) / version 1"));
        ok(draft.contains("11 UTF-16 code units")&&draft.contains("\nfirst\nexact\n[End project snapshot]"));
        ok(!draft.contains("latest")&&draft.contains("not instructions"));
        ok(notes.appendTo("").contains("project notes")&&notes.appendTo("").contains("notes 😀"));
        ok(old.stillAvailable(v)&&old.stillAvailable(ProjectLibrary.decode(ProjectLibrary.encode(v))));
        ok(!old.stillAvailable(a)&&!old.stillAvailable(null)&&!old.stillAvailable(ProjectLibrary.deleteArtifact(v,"p","a")));
        ok(!old.stillAvailable(ProjectLibrary.deleteProject(v,"p")));
        ok(!old.stillAvailable(ProjectLibrary.renameArtifact(v,"p","a","New title")));
        ProjectLibrary.State different=ProjectLibrary.editArtifact(a,"p","a","different",2);
        ok(!latest.stillAvailable(different));
        reject(()->ProjectContext.select(v,"missing",null,0));
        reject(()->ProjectContext.select(v,"p","missing",1));
        reject(()->ProjectContext.select(v,"p","a",0));
        reject(()->ProjectContext.select(v,"p","a",3));
        reject(()->ProjectContext.select(v,"p",null,1));
        int overhead=old.appendTo("x").length()-1;
        ok(old.appendTo("x".repeat(LocalChatTurns.MAX_PROMPT-overhead)).length()==LocalChatTurns.MAX_PROMPT);
        reject(()->old.appendTo("x".repeat(LocalChatTurns.MAX_PROMPT-overhead+1)));
        reject(()->old.appendTo(null));
        reject(()->old.appendTo(String.valueOf((char)0xd800)));
        ProjectLibrary.State blank=ProjectLibrary.editProject(v,"p","Project"," \n ");
        reject(()->ProjectContext.select(blank,"p",null,0).appendTo("draft"));
        ProjectLibrary.State large=ProjectLibrary.editArtifact(v,"p","a","x".repeat(16000),3);
        reject(()->ProjectContext.select(large,"p","a",3).appendTo(""));
        ok(ProjectContext.sameDestination("s","i","[]","d","s","i","[]","d"));
        ok(!ProjectContext.sameDestination("s","i","[]","d","t","i","[]","d"));
        ok(!ProjectContext.sameDestination("s","i","[]","d","s","j","[]","d"));
        ok(!ProjectContext.sameDestination("s","i","[]","d","s","i","[1]","d"));
        ok(!ProjectContext.sameDestination("s","i","[]","d","s","i","[]","e"));
        ok(!ProjectContext.sameDestination("","i","[]","d","","i","[]","d"));
        ok(!ProjectContext.sameDestination("s","","[]","d","s","","[]","d"));
        System.out.println("PASS: "+n+" reviewed project context identity, exact content, stale source, destination and prompt bounds assertions");
    }
}
