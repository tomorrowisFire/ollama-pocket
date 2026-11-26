package com.ollamapocket.app;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Experimental llama.cpp RPC host for larger GGUF models. Does NOT route through Ollama RPC. */
public final class DistributedRunner {
    private static final int PORT=11439;
    private static volatile Process process;
    private static volatile String activeModel="",activeWeightsKey="";
    private static volatile String rpcEndpointSet="";
    private static volatile long generation;
    private static final StringBuilder logs=new StringBuilder();
    private DistributedRunner(){}
    public static String url(){return "http://127.0.0.1:"+PORT;}
    public static synchronized String logs(){return logs.toString();}
    private static synchronized void log(String s){
        Log.i("AiPocketDistributed",s);
        logs.append(s).append('\n');
        if(logs.length()>12000)logs.delete(0,6000);
    }
    public static void stop(){
        synchronized(DistributedRunner.class){
            generation++;
            if(process!=null){process.destroy();process=null;}
            activeModel="";activeWeightsKey="";rpcEndpointSet="";
        }
    }
    public static boolean peersAvailable(Context context){
        String expected=rpcEndpointSet;
        if(expected.isEmpty())return true;
        return InferenceSafetyPolicy.hasEndpoints(expected,CoopSession.get(context).connectedRpcEndpoints());
    }
    private static boolean healthy(){
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(url()+"/health").openConnection(java.net.Proxy.NO_PROXY);
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(800);c.setReadTimeout(800);
            return c.getResponseCode()==200;
        }catch(Exception ignored){return false;}
        finally{if(c!=null)c.disconnect();}
    }
    private static final class BudgetSelection {
        final CoopMemoryPlanner.Plan budget;
        final List<CoopSession.Node> selected;
        final String endpoints;
        BudgetSelection(CoopMemoryPlanner.Plan budget,List<CoopSession.Node> selected,
                        String endpoints) {
            this.budget=budget;this.selected=selected;this.endpoints=endpoints;
        }
    }

    private static BudgetSelection plan(Context context, File modelWeights) {
        CoopSession coop=CoopSession.get(context);
        List<CoopSession.Node> ready=coop.readyNodes(); // ID-sorted, matching RPC order
        List<Long> capacities=new ArrayList<>();
        for(CoopSession.Node node:ready) capacities.add(node.safeRamMiB);
        long weightMiB=(modelWeights.length()+1048575L)/1048576L;
        CoopMemoryPlanner.Plan budget=CoopMemoryPlanner.evaluateCompact(
                weightMiB,coop.localSafeRamMiB(),capacities);
        List<CoopSession.Node> picked=new ArrayList<>();
        for(int index:budget.selectedIndices)picked.add(ready.get(index));
        return new BudgetSelection(budget,picked,coop.rpcEndpointsFor(picked));
    }

    /** Model-size and current RAM preflight, no model allocation or network transfer. */
    public static String preview(Context context,String model) {
        CoopSession coop=CoopSession.get(context);
        if(!"host".equals(coop.role()))return "호스트 세션을 만든 뒤 모델을 점검하세요.";
        try {
            File weights=findModel(context,model);
            BudgetSelection selection=plan(context,weights);
            return "모델: "+model+"\n"+selection.budget.summary()+
                "\nRPC 분할(보조 노드 순서): "+selection.budget.tensorSplit;
        }catch(Exception e){return "모델 사전 점검 실패: "+e.getMessage();}
    }

    /** Same read-only preflight, localized; no model allocation or transfer. */
    public static String previewLocalized(Context context,String model){
        Context c=OnHandsLocale.wrap(context);
        CoopSession coop=CoopSession.get(context);
        if(!"host".equals(coop.role()))return c.getString(R.string.coop_preflight_host);
        try{
            File weights=findModel(context,model);
            BudgetSelection selection=plan(context,weights);
            return c.getString(R.string.advisor_current,model)+"\n"+CoopMemoryPresentation.summary(c,selection.budget)+"\n"+
                c.getString(R.string.rpc_plan_split,selection.budget.tensorSplit);
        }catch(Exception e){return c.getString(R.string.rpc_preflight_failed,UiText.error(c,e.getMessage()));}
    }

    /** Called only from a worker thread, before the distributed server is started. */
    public static boolean ensure(Context context,String model,StringBuilder reason){
        return ensure(context,model,reason,()->false);
    }
    public static boolean ensure(Context context,String model,StringBuilder reason,java.util.function.BooleanSupplier cancelled){
        return ensure(context,model,reason,cancelled,null);
    }
    public static boolean ensure(Context context,String model,StringBuilder reason,java.util.function.BooleanSupplier cancelled,String approvedEndpoints){
        if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())return false;
        CoopSession coop=CoopSession.get(context);
        InferenceSafetyPolicy.Reason risk=InferenceSafety.check(context);
        if(risk!=InferenceSafetyPolicy.Reason.NONE){reason.append(OnHandsLocale.wrap(context).getString(InferenceSafety.label(risk)));return false;}
        if(!"host".equals(coop.role()) || coop.connectedRpcEndpoints().isEmpty()){
            reason.append("호스트 세션 또는 활성 보조 노드가 없습니다");return false;
        }
        File binary=new File(context.getApplicationInfo().nativeLibraryDir,
                             "libllama-distributed-server.so");
        if(!binary.isFile()){reason.append("RPC llama-server 실행 파일이 APK에 없습니다");return false;}
        File weights;String weightsKey;
        try { weights=findModel(context,model);weightsKey=EngineLoadPolicy.weightKey(weights); }
        catch(Exception e){reason.append("호스트의 GGUF 모델 확인 실패: ").append(e.getMessage());return false;}

        // An already-loaded model consumes RAM; never re-evaluate its admission
        // against the reduced availMem, but do detect a disconnected node.
        Process resident=process;
        if(resident!=null && resident.isAlive() && activeModel.equals(model)&&activeWeightsKey.equals(weightsKey)
                && healthy()&&process==resident){
            boolean stillConnected=true;
            String current=coop.connectedRpcEndpoints();
            for(String endpoint:rpcEndpointSet.split(","))
                if(!(","+current+",").contains(","+endpoint+","))stillConnected=false;
            if(stillConnected&&!cancelled.getAsBoolean()&&(approvedEndpoints==null||InferenceSafetyPolicy.hasEndpoints(rpcEndpointSet,approvedEndpoints)))return true;
        }
        final long startup;
        synchronized(DistributedRunner.class){stop();startup=generation;}
        try{
            GpuRunner.stop();
            OllamaHttp.releaseResidentModels(cancelled);
        }catch(Exception e){reason.append(e.getMessage());return false;}
        if(cancelled.getAsBoolean()||startup!=generation||InferenceSafety.blocked(context))return false;

        BudgetSelection selection=plan(context,weights);
        CoopMemoryPlanner.Plan budget=selection.budget;
        if(!budget.allowed){
            Context locale=OnHandsLocale.wrap(context);
            reason.append(CoopMemoryPresentation.summary(locale,budget))
                  .append('\n').append(locale.getString(R.string.rpc_plan_retry));
            return false;
        }
        if(selection.endpoints.isEmpty() || budget.tensorSplit.isEmpty() || selection.endpoints.split(",").length!=budget.selectedIndices.size() ||
                (approvedEndpoints!=null&&!InferenceSafetyPolicy.hasEndpoints(selection.endpoints,approvedEndpoints))){
            reason.append("RAM 조건을 만족하는 RPC 노드가 없습니다");return false;
        }
        log("RAM admission: "+budget.summary().replace('\n',' '));
        log("RPC endpoints="+selection.endpoints+" tensor-split="+budget.tensorSplit);

        try {
            // Admission was recomputed after releasing competing engines.
            List<String> args=new ArrayList<>();
            args.add(binary.getAbsolutePath());args.add("-m");args.add(weights.getAbsolutePath());
            args.add("--host");args.add("127.0.0.1");args.add("--port");args.add(Integer.toString(PORT));
            // RPC CPU workers appear as offloadable devices in llama.cpp.
            // Auto-fit can keep layers on the host when remote RAM is insufficient.
            args.add("--gpu-layers");args.add("auto");
            args.add("--fit");args.add("on");
            args.add("--fit-target");args.add("512");
            args.add("--rpc");args.add(selection.endpoints);
            args.add("--tensor-split");args.add(budget.tensorSplit);
            args.add("-c");args.add("2048");
            args.add("--threads");args.add(Integer.toString(
                EngineLoadPolicy.hostThreads(Runtime.getRuntime().availableProcessors())));
            ProcessBuilder pb=new ProcessBuilder(args);
            pb.redirectErrorStream(true);pb.directory(context.getFilesDir());
            pb.environment().put("LD_LIBRARY_PATH",context.getApplicationInfo().nativeLibraryDir);
            pb.environment().put("GGML_RPC_NO_RDMA","1");
            pb.environment().put("HOME",context.getFilesDir().getAbsolutePath());
            pb.environment().put("TMPDIR",context.getCacheDir().getAbsolutePath());
            final Process current;
            synchronized(DistributedRunner.class){
                if(startup!=generation||cancelled.getAsBoolean()||InferenceSafety.blocked(context))return false;
                if(!InferenceSafetyPolicy.hasEndpoints(selection.endpoints,coop.connectedRpcEndpoints())||
                        (approvedEndpoints!=null&&!InferenceSafetyPolicy.hasEndpoints(selection.endpoints,approvedEndpoints)))return false;
                current=pb.start();process=current;activeModel=model;activeWeightsKey=weightsKey;rpcEndpointSet=selection.endpoints;
            }
            new Thread(()->{
                try(BufferedReader r=new BufferedReader(new InputStreamReader(
                        current.getInputStream(),StandardCharsets.UTF_8))){
                    String line;while((line=r.readLine())!=null)log(line);
                }catch(Exception e){log("RPC host log ended: "+e.getMessage());}
            },"llama-rpc-host-logs").start();
            // Cache warming/network transfer can take minutes for a large GGUF.
            long seconds=Math.max(180,Math.min(900,weights.length()/(12L*1024*1024)));
            long until=SystemClock.elapsedRealtime()+seconds*1000;
            while(SystemClock.elapsedRealtime()<until && current.isAlive()){
                if(startup!=generation||process!=current||cancelled.getAsBoolean()||Thread.currentThread().isInterrupted()){stopGeneration(startup);return false;}
                if(InferenceSafety.blocked(context)){stopGeneration(startup);reason.append("Unsafe inference resources");return false;}
                String now=coop.connectedRpcEndpoints();
                for(String endpoint:selection.endpoints.split(",")){
                    if(!(","+now+",").contains(","+endpoint+",")){
                        reason.append("모델 로딩 중 보조 노드가 연결 해제되었습니다");
                        stopGeneration(startup);return false;
                    }
                }
                if(healthy()&&process==current&&startup==generation&&!cancelled.getAsBoolean()){
                    log("RPC HTTP ready; real remote compute needs measured token output");
                    return true;
                }
                Thread.sleep(400);
            }
            reason.append(current.isAlive()?"RPC 모델 로딩 시간 초과":
                "RPC 호스트 종료 · RAM/버전/모델 호환성 확인");
            stopGeneration(startup);return false;
        }catch(Exception e){reason.append(e.getMessage());stopGeneration(startup);return false;}
    }
    private static synchronized void stopGeneration(long expected){if(expected==generation)stop();}
    private static File findModel(Context context,String model) throws Exception{
        if(model==null||!model.matches("[A-Za-z0-9._/-]{1,90}(:[A-Za-z0-9._-]{1,45})?"))
            throw new Exception("일반 Ollama library 모델 이름만 지원됩니다");
        String[] parts=model.split(":",2);
        String tag=parts.length>1?parts[1]:"latest";
        File root=new File(context.getFilesDir(),"models");
        File library=new File(root,"manifests/registry.ollama.ai/library");
        File manifest=new File(library,parts[0]+"/"+tag);
        if(!manifest.getCanonicalPath().startsWith(library.getCanonicalPath()+File.separator)||!manifest.isFile())
            throw new Exception("모델을 호스트에서 먼저 다운로드하세요");
        if(manifest.length()>2_000_000)throw new Exception("모델 메타데이터가 너무 큽니다");
        byte[] data=new byte[(int)manifest.length()];
        try(FileInputStream in=new FileInputStream(manifest)){
            int read=0,n;while(read<data.length&&(n=in.read(data,read,data.length-read))>0)read+=n;
            if(read!=data.length)throw new Exception("모델 메타데이터를 읽지 못함");
        }
        JSONArray layers=new JSONObject(new String(data,StandardCharsets.UTF_8)).getJSONArray("layers");
        for(int i=0;i<layers.length();i++){
            JSONObject item=layers.getJSONObject(i);
            if(!"application/vnd.ollama.image.model".equals(item.optString("mediaType")))continue;
            String sha=item.optString("digest");
            if(!sha.matches("sha256:[0-9a-fA-F]{64}"))continue;
            File blob=new File(root,"blobs/sha256-"+sha.substring(7));
            if(blob.isFile()&&blob.length()>1048576)return blob;
        }
        throw new Exception("GGUF 가중치를 찾지 못했습니다");
    }
}
