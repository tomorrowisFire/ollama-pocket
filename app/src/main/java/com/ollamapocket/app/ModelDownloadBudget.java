package com.ollamapocket.app;

/** Conservative storage checks for local Ollama pulls; exact layer sizes may differ. */
public final class ModelDownloadBudget {
    private ModelDownloadBudget(){}
    private static final TransferGate TRANSFERS=new TransferGate();
    static boolean transferBusy(){return TRANSFERS.busy();}
    static TransferGate.Lease tryTransfer(){return TRANSFERS.acquire();}
    /** Process-wide mutual exclusion for model mutations, retained through worker cleanup. */
    public static final class TransferGate {
        private Lease active;
        public synchronized Lease acquire(){
            if(active!=null)return null;
            active=new Lease(this);return active;
        }
        public synchronized boolean busy(){return active!=null;}
        private synchronized void release(Lease expected){if(active==expected)active=null;}
        public static final class Lease implements AutoCloseable {
            private final TransferGate gate;
            private Lease(TransferGate gate){this.gate=gate;}
            @Override public void close(){gate.release(this);}
        }
    }
    public static final long MIB=1024L*1024*1024/1024;
    private static final long RESERVE=512L*1024*1024;
    /** Returns zero for unknown model size: show a warning, do not fabricate a size. */
    public static long requiredBytes(long modelBytes){
        if(modelBytes<=0)return 0;
        long extra=modelBytes/5+(modelBytes%5==0?0:1);
        return modelBytes>Long.MAX_VALUE-RESERVE-extra?Long.MAX_VALUE:modelBytes+extra+RESERVE;
    }
    /** Imported weight and registered blob can coexist; saturate instead of overflowing. */
    public static long importRequiredBytes(long bytes){
        if(bytes<=0)return 0;
        return bytes>(Long.MAX_VALUE-RESERVE)/2?Long.MAX_VALUE:bytes*2+RESERVE;
    }
    public static long registrationRequiredBytes(long bytes){
        if(bytes<=0)return 0;
        return bytes>Long.MAX_VALUE-RESERVE?Long.MAX_VALUE:bytes+RESERVE;
    }
    /** A larger or newly known estimate needs renewed user review. */
    public static boolean requiresReconfirmation(long reviewedBytes,long currentBytes){
        return currentBytes>0&&currentBytes>Math.max(0L,reviewedBytes);
    }
    public static boolean enough(long availableBytes,long modelBytes){
        return modelBytes<=0||availableBytes>=requiredBytes(modelBytes);
    }
    public static int layerPercent(long completed,long total){
        if(total<=0||completed<0||completed>total)return -1;
        // Exact floor: floating-point rounding can show 28 for 29/100, or 100 before completion.
        if(completed<=Long.MAX_VALUE/100)return (int)(completed*100/total);
        return java.math.BigInteger.valueOf(completed).multiply(java.math.BigInteger.valueOf(100))
            .divide(java.math.BigInteger.valueOf(total)).intValueExact();
    }
    public static String layerLabel(String status,long completed,long total){
        int percent=layerPercent(completed,total);
        if(percent<0)return status;
        return status+" · 현재 레이어 "+percent+"% (전체 다운로드 아님)";
    }
}
