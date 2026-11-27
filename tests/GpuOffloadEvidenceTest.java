package com.ollamapocket.app;

/** Offline fixtures for completed GPU evidence and the fail-closed readiness gate. */
public final class GpuOffloadEvidenceTest {
    private static int checks;
    private static void ok(boolean value){if(!value)throw new AssertionError("check "+checks);checks++;}
    private static String line(int n,int total){return "load_tensors: offloaded "+n+"/"+total+" layers to GPU";}
    public static void main(String[] args){
        for(String value:new String[]{
            "load_tensors: offloaded 25/25 layers to GPU",
            "llm_load_tensors: offloaded 1/25 layers to GPU",
            "\tload_tensors: offloaded 1/1 layer to gpu\t",
            line(1,GpuOffloadEvidence.MAX_LAYERS),line(GpuOffloadEvidence.MAX_LAYERS,GpuOffloadEvidence.MAX_LAYERS)})
            ok(GpuOffloadEvidence.completed(value));
        for(String value:new String[]{null,"","offloaded 25/25 layers to GPU",
            "load_tensors: offloading 25 layers to GPU","load_tensors: offloading 25/25 layers to GPU",
            "load_tensors: offloaded 0/25 layers to GPU","load_tensors: offloaded 25/0 layers to GPU",
            "load_tensors: offloaded 26/25 layers to GPU","load_tensors: offloaded -1/25 layers to GPU",
            "load_tensors: offloaded 01/25 layers to GPU","load_tensors: offloaded 1/025 layers to GPU",
            "load_tensors: offloaded 25/25 layers to CPU","load_tensors: offloaded 25/25 layers to GPU failed",
            "unrelated: offloaded 25/25 layers to GPU","GPU logs: load_tensors: offloaded 25/25 layers to GPU",
            "load_tensors: offloaded 99999999999999999999/99999999999999999999 layers to GPU",
            "load_tensors: offloaded 1.0/25 layers to GPU","load_tensors: offloaded 1 /25 layers to GPU",
            "load_tensors: offloaded 25/25 layers to GPU\nextra",
            "load_tensors: offloaded 25/25 layers to GPU\n",
            line(1,GpuOffloadEvidence.MAX_LAYERS+1)," ".repeat(GpuOffloadEvidence.MAX_LOG_LINE)+line(1,1)})
            ok(!GpuOffloadEvidence.completed(value));
        for(int n=0;n<=128;n++)for(int total=0;total<=128;total++)
            ok(GpuOffloadEvidence.completed(line(n,total))==(n>0&&n<=total));
        for(int mask=0;mask<128;mask++){
            boolean same=(mask&1)!=0,alive=(mask&2)!=0,proof=(mask&4)!=0,health=(mask&8)!=0;
            boolean cancelled=(mask&16)!=0,interrupted=(mask&32)!=0,safe=(mask&64)!=0;
            ok(GpuOffloadEvidence.ready(same,alive,proof,health,cancelled,interrupted,safe)==(mask==79));
        }
        String exact=" ".repeat(GpuOffloadEvidence.MAX_LOG_LINE-line(1,1).length())+line(1,1);
        ok(GpuOffloadEvidence.completed(exact));ok(!GpuOffloadEvidence.completed(" "+exact));
        System.out.println("PASS: "+checks+" GPU offload evidence and readiness assertions");
    }
}
