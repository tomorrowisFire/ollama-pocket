package com.ollamapocket.app;

/** Interleave source-grouped units without changing any source ID, offset, quota or request count. */
final class DocumentBatchSchedule {
    private DocumentBatchSchedule(){}
    static int[] order(int... counts){
        if(counts==null||counts.length<1||counts.length>DocumentBatchPolicy.MAX_SOURCES)throw new IllegalArgumentException("Source count");
        int[] starts=new int[counts.length];
        int total=0,rounds=0;
        for(int s=0;s<counts.length;s++){
            int n=counts[s];
            if(n<1||n>DocumentBatchPolicy.MAX_UNITS-total)throw new IllegalArgumentException("Unit count");
            starts[s]=total;total+=n;rounds=Math.max(rounds,n);
        }
        int[] ordered=new int[total];int cursor=0;
        for(int round=0;round<rounds;round++)
            for(int s=0;s<counts.length;s++)
                if(round<counts[s])ordered[cursor++]=starts[s]+round;
        return ordered;
    }
}
