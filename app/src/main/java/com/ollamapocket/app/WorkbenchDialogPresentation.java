package com.ollamapocket.app;

/** Display-only popup policy; never changes option identity, text or actions. */
final class WorkbenchDialogPresentation {
    private WorkbenchDialogPresentation(){}
    private static float safeScale(float scale){
        return Float.isFinite(scale)&&scale>0?Math.max(1f,Math.min(6f,scale)):1f;
    }
    static float combinedScale(float system,float app){
        return Math.min(6f,safeScale(system)*safeScale(app));
    }
    /** Bounds the supplied owner width by the inset-safe window width, if available. */
    static int usableWidth(int ownerWidth,int windowWidth,int leftInset,int rightInset){
        int owner=Math.max(0,ownerWidth);
        if(windowWidth<=0)return owner;
        int window=(int)Math.max(0L,(long)windowWidth-Math.max(0,leftInset)-Math.max(0,rightInset));
        return owner>0?Math.min(owner,window):window;
    }
    /** Return a UTF-16 span boundary only for nonempty title and detail. Preserve the original string. */
    static int detailStart(String label){
        if(label==null)return -1;
        int line=label.indexOf('\n');
        if(line<=0||line==label.length()-1)return -1;
        return visible(label,0,line)&&visible(label,line+1,label.length())?line+1:-1;
    }
    private static boolean visible(String s,int from,int end){
        for(int i=from;i<end;){
            int cp=s.codePointAt(i);i+=Character.charCount(cp);
            if(!Character.isWhitespace(cp)&&!Character.isSpaceChar(cp)&&!Character.isISOControl(cp)&&
                Character.getType(cp)!=Character.FORMAT&&Character.getType(cp)!=Character.SURROGATE)return true;
        }
        return false;
    }
}
