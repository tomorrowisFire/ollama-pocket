package com.ollamapocket.app;

final class ProjectWorkspacePolicy {
    private ProjectWorkspacePolicy(){}
    enum Outcome { NO_CHANGE, INVALID, STALE, UNCERTAIN, UNREADABLE }
    static Outcome failure(Exception e){
        if(e instanceof ProjectTransactions.TransactionFailure){
            switch(((ProjectTransactions.TransactionFailure)e).reason){
                case NO_CHANGE:return Outcome.NO_CHANGE;
                case INVALID:return Outcome.INVALID;
                case STALE:return Outcome.STALE;
                case WRITE_UNCERTAIN:return Outcome.UNCERTAIN;
            }
        }
        return Outcome.UNREADABLE;
    }
    static boolean reloadRequired(Outcome o){return o==Outcome.STALE||o==Outcome.UNCERTAIN||o==Outcome.UNREADABLE;}
    static boolean dirty(String title,String text,String newTitle,String newText){return !title.equals(newTitle)||!text.equals(newText);}
    static boolean canAppend(ProjectLibrary.Artifact a){return a.versions.size()<ProjectLibrary.MAX_VERSIONS;}
    static boolean canRestore(ProjectLibrary.Artifact a,int v){
        return canAppend(a)&&v>=1&&v<a.current().ordinal&&!a.version(v).text.equals(a.current().text);
    }
    static long savedAt(ProjectLibrary.Artifact a,long now){return Math.max(a.current().saved,now);}
    static final class Selection {
        final String project,artifact;final int ordinal;
        Selection(String p,String a,int v){project=p;artifact=a;ordinal=v;}
        static Selection root(){return new Selection(null,null,0);}
        Selection parent(){return ordinal>0?new Selection(project,artifact,0):artifact!=null?new Selection(project,null,0):root();}
        Selection resolve(ProjectLibrary.State state){
            if(project!=null)for(ProjectLibrary.Project p:state.projects)if(p.id.equals(project)){
                if(artifact!=null)for(ProjectLibrary.Artifact a:p.artifacts)if(a.id.equals(artifact))
                    return new Selection(project,artifact,ordinal>=1&&ordinal<=a.versions.size()?ordinal:0);
                return new Selection(project,null,0);
            }
            return root();
        }
    }
    static final class Session {
        private long epoch;private boolean active;
        long open(){active=true;return ++epoch;}
        void close(){active=false;epoch++;}
        long token(){return epoch;}
        boolean valid(long token){return active&&token==epoch;}
    }
}
