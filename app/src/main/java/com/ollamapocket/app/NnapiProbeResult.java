package com.ollamapocket.app;

/** Bounded diagnostic protocol, not proof that an NPU inference backend exists. */
final class NnapiProbeResult {
    final int accelerators,gpu,cpu;
    private NnapiProbeResult(int accelerators,int gpu,int cpu){
        this.accelerators=accelerators;this.gpu=gpu;this.cpu=cpu;
    }
    static NnapiProbeResult parse(String raw){
        if(raw==null||raw.length()>8||!raw.matches("[0-9]{1,2},[0-9]{1,2},[0-9]{1,2}"))return null;
        String[] parts=raw.split(",",-1);
        int a=Integer.parseInt(parts[0]),g=Integer.parseInt(parts[1]),c=Integer.parseInt(parts[2]);
        if(a+g+c>64)return null;
        return new NnapiProbeResult(a,g,c);
    }
}
