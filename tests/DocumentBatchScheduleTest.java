package com.ollamapocket.app;

import java.util.Arrays;

public final class DocumentBatchScheduleTest {
    private static int checks;
    private static void ok(boolean pass){checks++;if(!pass)throw new AssertionError("check "+checks);}
    private static void rejects(Runnable action){
        try{action.run();throw new AssertionError("Invalid schedule accepted");}
        catch(IllegalArgumentException expected){checks++;}
    }
    private static void verify(int... counts){
        int[] original=counts.clone(),order=DocumentBatchSchedule.order(counts);
        int total=Arrays.stream(counts).sum();
        ok(order.length==total);ok(Arrays.equals(original,counts));
        boolean[] seen=new boolean[total];
        int[] bases=new int[counts.length],done=new int[counts.length];
        for(int i=1;i<counts.length;i++)bases[i]=bases[i-1]+counts[i-1];
        int cursor=0;
        while(cursor<total){
            for(int s=0;s<counts.length;s++)if(done[s]<counts[s]){
                int next=order[cursor++];
                ok(next>=0&&next<total&&!seen[next]);
                ok(next==bases[s]+done[s]);
                seen[next]=true;done[s]++;
            }
        }
        ok(Arrays.equals(done,counts));
        for(boolean included:seen)ok(included);
        for(int s=0;s<counts.length;s++)ok(order[s]==bases[s]);
        ok(Arrays.equals(order,DocumentBatchSchedule.order(counts)));
    }
    public static void main(String[] args){
        ok(Arrays.equals(DocumentBatchSchedule.order(4,4,4),new int[]{0,4,8,1,5,9,2,6,10,3,7,11}));
        ok(Arrays.equals(DocumentBatchSchedule.order(1,3,2),new int[]{0,1,4,2,5,3}));
        for(int a=1;a<=24;a++){
            verify(a);
            for(int b=1;a+b<=24;b++){
                verify(a,b);
                for(int c=1;a+b+c<=24;c++)verify(a,b,c);
            }
        }
        rejects(()->DocumentBatchSchedule.order());
        rejects(()->DocumentBatchSchedule.order((int[])null));
        verify(1,1,1,1);
        int[] twenty=new int[20];Arrays.fill(twenty,4);verify(twenty);
        int[] excessive=new int[21];Arrays.fill(excessive,1);
        rejects(()->DocumentBatchSchedule.order(excessive));
        rejects(()->DocumentBatchSchedule.order(0,2));
        rejects(()->DocumentBatchSchedule.order(-1));
        verify(24,1);
        rejects(()->DocumentBatchSchedule.order(80,1));
        rejects(()->DocumentBatchSchedule.order(Integer.MAX_VALUE));
        System.out.println("PASS: "+checks+" document schedule assertions");
    }
}
