package com.ollamapocket.app;

/** Immutable reviewed manual snapshot. */
final class KnowledgeWorkspacePolicy {
    private KnowledgeWorkspacePolicy(){}
    static final class Review {
        final String id,title,text;
        private Review(KnowledgeLibrary.Source source){id=source.id;title=source.title;text=source.text;}
        KnowledgeLibrary.State apply(KnowledgeLibrary.State current){return KnowledgeLibrary.add(current,id,title,text);}
    }
    static Review review(KnowledgeLibrary.State state,String id,String title,String text){
        return new Review(KnowledgeLibrary.add(state,id,title,text).source(id));
    }
    static boolean fitsEdit(int original,int removed,int inserted,int limit){
        return original>=0&&removed>=0&&removed<=original&&inserted>=0&&limit>=0&&(long)original-removed+inserted<=limit;
    }
}
