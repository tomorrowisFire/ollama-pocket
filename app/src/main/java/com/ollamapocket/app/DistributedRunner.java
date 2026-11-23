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
    private static volatile String activeModel="";
    private static volatile String rpcEndpointSet="";
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
            if(process!=null){process.destroy();process=null;}
            activeModel="";rpcEndpointSet="";
        }
    }
    private static boolean healthy(){
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(url()+"/health").openConnection();
            c.setConnectTimeout(800);c.setReadTimeout(800);
            return c.getResponseCode()==200;
        }catch(Exception ignored){return false;}
        finally{if(c!=null)c.disconnect();}
    }
    /** Called only from a background thread; requires a currently paired node. */
    public static boolean ensure(Context context,String model,StringBuilder reason){
        CoopSession coop=CoopSession.get(context);
        if(!"host".equals(coop.role()) || coop.readyNodes().isEmpty()){
            reason.append("호스트 세션 또는 활성 보조 노드가 없습니다");return false;
        }
        String endpoints=coop.rpcEndpoints();
        if(endpoints.isEmpty()){reason.append("안전한 RPC 중계 포트가 준비되지 않았습니다");return false;}
        if(process!=null && process.isAlive() && activeModel.equals(model) &&
              rpcEndpointSet.equals(endpoints) && healthy())return true;
        stop();
        File binary=new File(context.getApplicationInfo().nativeLibraryDir,"libllama-distributed-server.so");
        if(!binary.isFile()){reason.append("RPC CPU llama-server 실행 파일이 APK에 없습니다");return false;}
        File weights;
        try{weights=findModel(context,model);}catch(Exception e){reason.append("GGUF 모델 준비 실패: ").append(e.getMessage());return false;}
        try{
            // Drop Ollama's active model before holding a second set of weights.
            try{OllamaHttp.post("/api/generate",new JSONObject().put("model",model)
                .put("keep_alive",0).put("stream",false),"POST");}catch(Exception ignored){}
            GpuRunner.stop();
            List<String> args=new ArrayList<>();
            args.add(binary.getAbsolutePath());args.add("-m");args.add(weights.getAbsolutePath());
            args.add("--host");args.add("127.0.0.1");args.add("--port");args.add(Integer.toString(PORT));
            args.add("-ngl");args.add("99");args.add("--rpc");args.add(endpoints);
            args.add("-c");args.add("2048");
            args.add("--threads");args.add(Integer.toString(Math.max(1,Math.min(4,Runtime.getRuntime().availableProcessors()/2))));
            ProcessBuilder pb=new ProcessBuilder(args);
            pb.redirectErrorStream(true);pb.directory(context.getFilesDir());
            pb.environment().put("LD_LIBRARY_PATH",context.getApplicationInfo().nativeLibraryDir);
            pb.environment().put("GGML_RPC_NO_RDMA","1");
            pb.environment().put("HOME",context.getFilesDir().getAbsolutePath());
            pb.environment().put("TMPDIR",context.getCacheDir().getAbsolutePath());
            final Process current=pb.start();
            synchronized(DistributedRunner.class){process=current;activeModel=model;rpcEndpointSet=endpoints;}
            new Thread(()->{
                try(BufferedReader r=new BufferedReader(new InputStreamReader(current.getInputStream(),StandardCharsets.UTF_8))){
                    String line;while((line=r.readLine())!=null)log(line);
                }catch(Exception e){log("RPC host log ended: "+e.getMessage());}
            },"llama-rpc-host-logs").start();
            long until=SystemClock.elapsedRealtime()+70000;
            while(SystemClock.elapsedRealtime()<until && current.isAlive()){
                if(coop.readyNodes().isEmpty()){reason.append("보조 노드가 연결 해제됐습니다");stop();return false;}
                if(healthy()){
                    log("RPC host HTTP ready; physical remote offload requires a token benchmark");
                    return true;
                }
                Thread.sleep(400);
            }
            reason.append(current.isAlive()?"RPC 모델 로딩 시간 초과 (대형 가중치 전송 지연 가능)":
                "RPC 호스트가 종료됐습니다 (RAM 부족·모델/버전 호환 확인)");
            stop();return false;
        }catch(Exception e){reason.append(e.getMessage());stop();return false;}
    }
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
