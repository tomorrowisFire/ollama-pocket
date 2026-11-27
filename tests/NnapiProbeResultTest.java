package com.ollamapocket.app;

public final class NnapiProbeResultTest {
    private static int checks;
    private static void check(boolean value){if(!value)throw new AssertionError("probe result "+checks);checks++;}
    public static void main(String[] args){
        int[] samples={0,1,2,16,31,32,63,64,65,99};
        for(int a:samples)for(int g:samples)for(int c:samples){
            NnapiProbeResult r=NnapiProbeResult.parse(a+","+g+","+c);
            check((r!=null)==(a+g+c<=64));
            if(r!=null)check(r.accelerators==a&&r.gpu==g&&r.cpu==c);
        }
        for(String malformed:new String[]{null,"","0","0,0","0,0,0,0","0,0,","-1,0,0","+1,0,0"," 1,0,0","0,0,0\n","0, 0,0","1.0,0,0","100,0,0","2147483647,0,0","１,0,0","x,0,0","0,0,0\u0000"})
            check(NnapiProbeResult.parse(malformed)==null);
        System.out.println("PASS: "+checks+" NNAPI protocol checks");
    }
}
