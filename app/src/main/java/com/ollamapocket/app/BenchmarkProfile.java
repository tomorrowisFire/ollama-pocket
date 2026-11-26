package com.ollamapocket.app;

import org.json.JSONObject;
import java.util.Locale;

/** Recent measurements are scoped to the exact model manifest and runtime installation.
 * These are observed rates, not promised throughput or proof of thermal stability. */
final class BenchmarkProfile {
    static final long MAX_AGE_MS=7L*24*60*60*1000;
    static final class Measurement {
        final double decode,ttft,decay;
        final long freeMiB;
        final int thermalStart,thermalEnd;
        Measurement(double decode,double ttft,double decay,long freeMiB,int thermalStart,int thermalEnd){
            if(!Double.isFinite(decode)||decode<=0||decode>100000||!Double.isFinite(ttft)||ttft<0||ttft>300000||
                !Double.isFinite(decay)||decay<0||decay>100||freeMiB<0||freeMiB>1048576||
                thermalStart< -1||thermalStart>6||thermalEnd< -1||thermalEnd>6)
                throw new IllegalArgumentException("Invalid measurement");
            this.decode=decode;this.ttft=ttft;this.decay=decay;this.freeMiB=freeMiB;
            this.thermalStart=thermalStart;this.thermalEnd=thermalEnd;
        }
        boolean stableCandidate(){
            return thermalStart>=0&&thermalStart<3&&thermalEnd>=0&&thermalEnd<3&&decay<=20&&freeMiB>=256;
        }
        JSONObject json()throws Exception{return new JSONObject().put("decode",decode).put("ttft",ttft).put("decay",decay)
            .put("freeMiB",freeMiB).put("thermalStart",thermalStart).put("thermalEnd",thermalEnd);}
        static Measurement read(JSONObject o)throws Exception{
            return o==null?null:new Measurement(o.getDouble("decode"),o.getDouble("ttft"),o.getDouble("decay"),
                o.getLong("freeMiB"),o.getInt("thermalStart"),o.getInt("thermalEnd"));
        }
    }
    final String model,digest,runtime;
    final long measuredAt;
    final Measurement cpu,gpu;
    BenchmarkProfile(String model,String digest,String runtime,long measuredAt,Measurement cpu,Measurement gpu){
        String normalized=normalizeDigest(digest);
        if(model==null||model.isEmpty()||model.length()>256||normalized.isEmpty()||runtime==null||
           !runtime.matches("[0-9a-f]{64}")||measuredAt<=0||(cpu==null&&gpu==null))
            throw new IllegalArgumentException("Invalid benchmark scope");
        this.model=model;this.digest=normalized;this.runtime=runtime;this.measuredAt=measuredAt;this.cpu=cpu;this.gpu=gpu;
    }
    static String normalizeDigest(String value){
        if(value==null)return "";
        String d=value.toLowerCase(Locale.ROOT);
        if(d.startsWith("sha256:"))d=d.substring(7);
        return d.matches("[0-9a-f]{64}")?d:"";
    }
    boolean fresh(String currentModel,String currentDigest,String currentRuntime,long now){
        return model.equals(currentModel)&&digest.equals(normalizeDigest(currentDigest))&&runtime.equals(currentRuntime)&&
            now>=measuredAt&&now-measuredAt<=MAX_AGE_MS;
    }
    /** Auto never selects cooperative or overrides a manually selected mode.
     * Use only completed local CPU/GPU comparisons with known safety readings. */
    String preferredLocalMode(){
        if(cpu==null||gpu==null||!cpu.stableCandidate()||gpu.thermalStart<0||gpu.thermalEnd<0)return null;
        if(!gpu.stableCandidate())return "cpu";
        return gpu.decode>cpu.decode*1.10&&gpu.ttft<=cpu.ttft*1.5+500?"gpu":"cpu";
    }
    Double cpuRate(){return cpu==null?null:cpu.decode;}
    Double gpuRate(){return gpu==null?null:gpu.decode;}
    String encode()throws Exception{
        JSONObject j=new JSONObject().put("schema",1).put("model",model).put("digest",digest).put("runtime",runtime).put("measuredAt",measuredAt);
        if(cpu!=null)j.put("cpu",cpu.json());
        if(gpu!=null)j.put("gpu",gpu.json());
        return j.toString();
    }
    private static BenchmarkProfile decode(String raw){
        if(raw==null||raw.length()>8192)return null;
        try{
            JSONObject o=new JSONObject(raw);if(o.getInt("schema")!=1)return null;
            return new BenchmarkProfile(o.getString("model"),o.getString("digest"),o.getString("runtime"),
                o.getLong("measuredAt"),Measurement.read(o.optJSONObject("cpu")),Measurement.read(o.optJSONObject("gpu")));
        }catch(Exception e){return null;}
    }
    static BenchmarkProfile read(String raw,String model,String digest,String runtime,long now){
        BenchmarkProfile p=decode(raw);
        return p!=null&&p.fresh(model,digest,runtime,now)?p:null;
    }
    /** Skip metadata I/O unless a locally stored comparison could actually control Auto.
     * A stored digest is never sufficient: any returned route must match a live digest. */
    static String measuredRoute(String raw,String model,java.util.function.Supplier<String> runtime,
                                java.util.function.Supplier<String> liveDigest,java.util.function.LongSupplier now){
        try{
            BenchmarkProfile p=decode(raw);
            if(p==null||!p.model.equals(model))return null;
            long before=now.getAsLong();
            if(before<p.measuredAt||before-p.measuredAt>MAX_AGE_MS)return null;
            String route=p.preferredLocalMode();
            if(route==null)return null;
            String identity=runtime.get();
            if(!p.runtime.equals(identity))return null;
            String digest=liveDigest.get();
            return p.fresh(model,digest,identity,now.getAsLong())?route:null;
        }catch(RuntimeException e){return null;}
    }
    /** Minimum sample coverage for a fairer comparison, not a quality test. */
    static boolean adequateTokens(long generated,int requested){
        return requested>0&&generated>=Math.max(1,(requested*8L+9)/10)&&generated<=requested;
    }
}
