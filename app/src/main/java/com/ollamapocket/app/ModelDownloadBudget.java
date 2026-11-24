package com.ollamapocket.app;

/** Conservative storage checks for local Ollama pulls; exact layer sizes may differ. */
public final class ModelDownloadBudget {
    private ModelDownloadBudget(){}
    public static final long MIB=1024L*1024*1024/1024;
    private static final long RESERVE=512L*1024*1024;
    /** Returns zero for unknown model size: show a warning, do not fabricate a size. */
    public static long requiredBytes(long modelBytes){
        if(modelBytes<=0)return 0;
        double needed=modelBytes*1.20+RESERVE;
        return needed>=Long.MAX_VALUE?Long.MAX_VALUE:(long)needed;
    }
    public static boolean enough(long availableBytes,long modelBytes){
        return modelBytes<=0||availableBytes>=requiredBytes(modelBytes);
    }
    public static int layerPercent(long completed,long total){
        if(total<=0||completed<0)return -1;
        return (int)Math.max(0,Math.min(100,completed/(double)total*100));
    }
    public static String layerLabel(String status,long completed,long total){
        int percent=layerPercent(completed,total);
        if(percent<0)return status;
        return status+" · 현재 레이어 "+percent+"% (전체 다운로드 아님)";
    }
}
