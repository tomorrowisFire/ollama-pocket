package com.ollamapocket.app;

import android.content.Context;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.Locale;

/**
 * Tests the actual on-device inference paths, not just advertised hardware features.
 * Never uploads a diagnostic report or an input prompt; only the loopback servers are used.
 * Call on a background thread. The short test is not a thermal or long-term benchmark.
 */
public final class HardwareSelfTest {
    private HardwareSelfTest() { }

    public interface Progress { void onProgress(String message); }

    private static final String PROMPT = "숫자 1부터 5까지 쉼표로만 적어줘.";

    private static JSONArray messages() throws Exception {
        JSONArray list = new JSONArray();
        list.put(new JSONObject().put("role", "user").put("content", PROMPT));
        return list;
    }

    private static final class Trial {
        int chars;
        long millis;
        long firstTokenMillis;
        long evalCount;
        long evalDurationNanos;
        String preview;
        boolean hadResponse;
    }

    private static Trial cpuTrial(String model) throws Exception {
        Trial result = new Trial();
        long start = SystemClock.elapsedRealtime();
        long[] first = { -1 };
        StringBuilder preview = new StringBuilder();
        HttpURLConnection conn = OllamaHttp.newChatConnection();
        OllamaHttp.stream(conn, OllamaHttp.chatBody(model, messages()), item -> {
            JSONObject message = item.optJSONObject("message");
            if (message != null) {
                String text = message.optString("content", "");
                if (!text.isEmpty()) {
                    if (first[0] < 0) first[0] = SystemClock.elapsedRealtime() - start;
                    result.chars += text.length();
                    if (preview.length() < 120) preview.append(text, 0, Math.min(text.length(), 120 - preview.length()));
                }
            }
            if (item.optBoolean("done", false)) {
                result.evalCount = item.optLong("eval_count", 0);
                result.evalDurationNanos = item.optLong("eval_duration", 0);
            }
        });
        result.millis = SystemClock.elapsedRealtime() - start;
        result.firstTokenMillis = first[0];
        result.preview = preview.toString();
        result.hadResponse = result.chars > 0;
        return result;
    }

    private static Trial gpuTrial(String model) throws Exception {
        Trial result = new Trial();
        long start = SystemClock.elapsedRealtime();
        long[] first = { -1 };
        StringBuilder preview = new StringBuilder();
        HttpURLConnection conn = OllamaHttp.newGpuChatConnection();
        OllamaHttp.streamGpu(conn, OllamaHttp.gpuChatBody(model, messages()), item -> {
            JSONObject message = item.optJSONObject("message");
            if (message != null) {
                String text = message.optString("content", "");
                if (!text.isEmpty()) {
                    if (first[0] < 0) first[0] = SystemClock.elapsedRealtime() - start;
                    result.chars += text.length();
                    if (preview.length() < 120) preview.append(text, 0, Math.min(text.length(), 120 - preview.length()));
                }
            }
        });
        result.millis = SystemClock.elapsedRealtime() - start;
        result.firstTokenMillis = first[0];
        result.preview = preview.toString();
        result.hadResponse = result.chars > 0;
        return result;
    }

    private static String section(Context c,int id){return "["+c.getString(id)+"]\n";}
    private static String summary(Context c,String engine,Trial t) {
        StringBuilder line=new StringBuilder(c.getString(R.string.selftest_elapsed,engine,
            c.getString(t.hadResponse?R.string.selftest_response_ok:R.string.selftest_response_missing),t.millis,t.chars));
        if(t.firstTokenMillis>=0)line.append(" · ").append(c.getString(R.string.selftest_first_token,t.firstTokenMillis));
        if(t.evalDurationNanos>0&&t.evalCount>0){
            double tps=t.evalCount*1_000_000_000.0/t.evalDurationNanos;
            line.append(" · ").append(c.getString(R.string.selftest_rate,t.evalCount,
                String.format(c.getResources().getConfiguration().getLocales().get(0),"%.1f",tps)));
        }
        return line.toString();
    }
    public static String run(Context context,String model,Progress callback){
        Context c=OnHandsLocale.wrap(context);
        StringBuilder report=new StringBuilder(c.getString(R.string.selftest_title)).append('\n')
            .append(c.getString(R.string.selftest_header,model)).append("\n\n");
        ComputeProfiler.Report device=ComputeProfiler.scan(context);
        report.append(section(c,R.string.selftest_hardware)).append(device.description(c)).append("\n\n");
        if(model==null||model.isEmpty())return report.append(c.getString(R.string.test_need_model)).append('\n').toString();
        report.append(section(c,R.string.selftest_cpu));
        try{
            callback.onProgress(c.getString(R.string.selftest_cpu_progress));
            Trial cpu=cpuTrial(model);
            report.append(summary(c,"Ollama",cpu)).append('\n');
            if(!cpu.hadResponse)report.append(c.getString(R.string.selftest_cpu_no_response)).append('\n');
        }catch(Exception e){
            report.append(c.getString(R.string.bench_failed,e.getClass().getSimpleName()+" · "+e.getMessage())).append('\n');
        }
        report.append('\n').append(section(c,R.string.selftest_gpu));
        if(!GpuRunner.packaged(context)){
            report.append(c.getString(R.string.selftest_gpu_skipped,c.getString(R.string.compute_gpu_missing))).append('\n');
        }else if(!device.gpuCompute){
            report.append(c.getString(R.string.selftest_gpu_skipped,c.getString(R.string.compute_gpu_unavailable))).append('\n');
        }else{
            try{
                callback.onProgress(c.getString(R.string.selftest_gpu_prepare));
                StringBuilder reason=new StringBuilder();
                long begin=SystemClock.elapsedRealtime();
                boolean ready=GpuRunner.ensure(context,model,reason);
                long initMillis=SystemClock.elapsedRealtime()-begin;
                if(!ready){
                    report.append(c.getString(R.string.selftest_gpu_init_fail,initMillis,reason.toString())).append('\n');
                }else{
                    callback.onProgress(c.getString(R.string.selftest_gpu_progress));
                    try{
                        Trial gpu=gpuTrial(model);
                        report.append(c.getString(R.string.selftest_gpu_init,initMillis)).append('\n')
                            .append(summary(c,"Vulkan llama.cpp",gpu)).append('\n')
                            .append(c.getString(gpu.hadResponse?R.string.selftest_gpu_verified:R.string.selftest_gpu_unverified)).append('\n');
                    }catch(Exception e){
                        report.append(c.getString(R.string.selftest_gpu_token_fail,e.getClass().getSimpleName()+" · "+e.getMessage())).append('\n');
                    }
                }
            }finally{GpuRunner.stop();}
        }
        report.append('\n').append(section(c,R.string.selftest_npu))
            .append(c.getString(R.string.selftest_npu_note,device.nnapiQueried?String.valueOf(device.nnapiAccelerators):c.getString(R.string.metric_unavailable)))
            .append("\n\n").append(c.getString(R.string.selftest_local_report)).append('\n');
        callback.onProgress(c.getString(R.string.selftest_finished));
        return report.toString();
    }
}
