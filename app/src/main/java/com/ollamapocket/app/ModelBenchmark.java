package com.ollamapocket.app;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Reproducible, on-device decode/prefill benchmark; never uploads prompts,
 * device information, results or the user's conversation.
 * The warmup is excluded from scores. Native engine timings, not character
 * count, determine actual token rates.
 *
 * Limitations: not a 30-minute thermal test, prompt caching can differ between
 * engines, and an APK build cannot validate physical device performance.
 */
public final class ModelBenchmark {
    private static final int TOKENS = 72;
    private static final int WARMUP_TOKENS = 12;
    private static final int REPEATS = 2;
    private static final int TIMEOUT_MS = 180_000;
    private static final String PROMPT =
            "Explain how the memory hierarchy of a computer works, including CPU caches, " +
            "system RAM, GPU shared memory, storage, latency, bandwidth, and energy use. " +
            "Explain how an autoregressive transformer generates tokens and why KV cache " +
            "and quantized model weights affect throughput. Include two clear examples. " +
            "Write a complete, continuous technical explanation of at least 180 words. " +
            "Continue explaining details and do not stop after a single sentence.";

    private volatile boolean canceled;
    private volatile Thread worker;
    private volatile HttpURLConnection activeConnection;

    private static final class Sample {
        double promptTps;
        double decodeTps;
        long firstTokenMs = -1;
        long elapsedMs;
        long generated;
        long promptCount;
        long minFreeMiB=Long.MAX_VALUE;
    }

    private static final class Reading {
        long freeMiB;
        float batteryC = Float.NaN;
        int thermal = -1;

        static Reading capture(Context context) {
            Reading r = new Reading();
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(info);
                r.freeMiB = info.availMem >> 20;
            }
            Intent battery = context.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery != null) {
                int x = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                if (x >= 0) r.batteryC = x / 10f;
            }
            if (Build.VERSION.SDK_INT >= 29) {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (pm != null) r.thermal = pm.getCurrentThermalStatus();
            }
            return r;
        }
    }

    private static String engineLabel(Context c,String mode){
        return "cpu".equals(mode)?"CPU · Ollama":"gpu".equals(mode)?"GPU · Vulkan":c.getString(R.string.bench_rpc);
    }
    private static String decimal(Context c,double value,int digits){
        return Double.isFinite(value)?String.format(c.getResources().getConfiguration().getLocales().get(0),"%."+digits+"f",value):c.getString(R.string.metric_unavailable);
    }
    private static String metricTemperature(Context c,double value){
        return Double.isFinite(value)?decimal(c,value,1)+"℃":c.getString(R.string.metric_unavailable);
    }
    private static String metricThermal(Context c,int value){
        return value>=0?Integer.toString(value):c.getString(R.string.metric_unavailable);
    }
    private static String errorLabel(Context c,String error){
        if(error==null||error.isEmpty())return c.getString(R.string.metric_unavailable);
        switch(error){
            case "검사 취소":return c.getString(R.string.bench_cancelled_error);
            case "기기가 심하게 뜨거워져 안전을 위해 검사 중단":return c.getString(R.string.bench_hot);
            case "Android 저메모리 경고로 검사 중단":return c.getString(R.string.bench_memory);
            case "Ollama 서버 중지":return c.getString(R.string.test_need_server);
            case "Vulkan 실행 파일 없음":return c.getString(R.string.compute_gpu_missing);
            case "기기 Vulkan compute 미보고":return c.getString(R.string.compute_gpu_unavailable);
            case "호스트 세션·활성 보조 노드 없음":return c.getString(R.string.coop_connect_first);
            default:return UiText.error(c,error);
        }
    }
    public static final class EngineResult {
        public final String mode;
        public final String name;
        public String error = "";
        public double decodeTps;
        public double prefillTps;
        public double ttftMs;
        public double decayPct;
        public long minFreeMiB;
        public float batteryStartC = Float.NaN, batteryEndC = Float.NaN;
        public int thermalStart = -1, thermalEnd = -1;
        public int passes;
        EngineResult(String mode, String name) {
            this.mode = mode; this.name = name;
        }
        public boolean valid() {
            return passes == REPEATS && Double.isFinite(decodeTps)&&decodeTps > 0 && thermalStart<3&&thermalEnd<3&&error.isEmpty();
        }
        String localizedSummary(Context c){
            String name=engineLabel(c,mode);
            if(!valid())return c.getString(R.string.bench_unmeasured,name,errorLabel(c,error));
            return c.getString(R.string.bench_rates,name,decimal(c,decodeTps,1),decimal(c,prefillTps,1))+"\n"+
                c.getString(R.string.bench_latency,decimal(c,ttftMs,0),decimal(c,decayPct,1),passes,REPEATS)+"\n"+
                c.getString(R.string.bench_resources,minFreeMiB,metricTemperature(c,batteryStartC),metricTemperature(c,batteryEndC),
                    metricThermal(c,thermalStart),metricThermal(c,thermalEnd));
        }
        JSONObject structured()throws Exception{
            return new JSONObject().put("mode",mode).put("error",error).put("decode",decodeTps)
                .put("prefill",prefillTps).put("ttft",ttftMs).put("decay",decayPct).put("ram",minFreeMiB)
                .put("batteryStart",Float.isFinite(batteryStartC)?(Object)Double.valueOf(batteryStartC):JSONObject.NULL)
                .put("batteryEnd",Float.isFinite(batteryEndC)?(Object)Double.valueOf(batteryEndC):JSONObject.NULL)
                .put("thermalStart",thermalStart).put("thermalEnd",thermalEnd).put("passes",passes);
        }
        static EngineResult fromStructured(JSONObject o)throws Exception{
            String mode=o.getString("mode");
            if(!Arrays.asList("cpu","gpu","cooperative").contains(mode))throw new IOException("Invalid engine");
            EngineResult e=new EngineResult(mode,mode);
            e.error=o.getString("error");e.decodeTps=o.getDouble("decode");e.prefillTps=o.getDouble("prefill");
            e.ttftMs=o.getDouble("ttft");e.decayPct=o.getDouble("decay");e.minFreeMiB=o.getLong("ram");
            e.batteryStartC=o.isNull("batteryStart")?Float.NaN:(float)o.getDouble("batteryStart");
            e.batteryEndC=o.isNull("batteryEnd")?Float.NaN:(float)o.getDouble("batteryEnd");
            e.thermalStart=o.getInt("thermalStart");e.thermalEnd=o.getInt("thermalEnd");e.passes=o.getInt("passes");
            if(e.passes<0||e.passes>REPEATS||e.minFreeMiB<0||!Double.isFinite(e.decodeTps)||!Double.isFinite(e.prefillTps)||
                !Double.isFinite(e.ttftMs)||!Double.isFinite(e.decayPct))throw new IOException("Invalid benchmark");
            return e;
        }
        public String summary() {
            if (!valid()) return name + ": 미측정 · " + error;
            return String.format(Locale.KOREA,
                    "%s: 생성 %.1f tok/s · 프롬프트 %.1f tok/s\n" +
                    "첫 출력 %.0fms · 반복 속도 저하 %.1f%% · 완료 %d/%d\n" +
                    "최소 가용 RAM %d MiB · 배터리 %.1f→%.1f℃ · 열 상태 %d→%d",
                    name, decodeTps, prefillTps, ttftMs, decayPct,
                    passes, REPEATS, minFreeMiB,
                    batteryStartC, batteryEndC, thermalStart, thermalEnd);
        }
    }

    public static final class Report {
        public final String model;
        public final List<EngineResult> results = new ArrayList<>();
        public String recommendedMode;
        public String recommendedName;
        public boolean interrupted;
        Report(String model) { this.model = model; }
        String localizedText(Context c){
            StringBuilder s=new StringBuilder(c.getString(R.string.bench_header,model,WARMUP_TOKENS,TOKENS,REPEATS)).append("\n\n");
            for(EngineResult result:results)s.append(result.localizedSummary(c)).append("\n\n");
            s.append(c.getString(R.string.bench_npu)).append('\n');
            s.append(recommendedMode==null?c.getString(R.string.bench_no_best):
                c.getString(R.string.bench_best,engineLabel(c,recommendedMode))).append('\n');
            if(interrupted)s.append(c.getString(R.string.bench_interrupted)).append('\n');
            return s.append('\n').append(c.getString(R.string.bench_limitations)).toString();
        }
        String structured()throws Exception{
            org.json.JSONArray values=new org.json.JSONArray();
            for(EngineResult e:results)values.put(e.structured());
            return new JSONObject().put("schema",1).put("model",model).put("results",values)
                .put("recommended",recommendedMode==null?JSONObject.NULL:recommendedMode).put("interrupted",interrupted).toString();
        }
        static Report fromStructured(String value,String expectedModel)throws Exception{
            if(value==null||value.length()>120000)throw new IOException("Invalid report");
            JSONObject o=new JSONObject(value);
            if(o.getInt("schema")!=1||!expectedModel.equals(o.getString("model")))throw new IOException("Invalid report scope");
            Report r=new Report(expectedModel);org.json.JSONArray list=o.getJSONArray("results");
            if(list.length()>3)throw new IOException("Invalid report size");
            for(int i=0;i<list.length();i++)r.results.add(EngineResult.fromStructured(list.getJSONObject(i)));
            r.recommendedMode=o.isNull("recommended")?null:o.getString("recommended");
            if(r.recommendedMode!=null&&!Arrays.asList("cpu","gpu","cooperative").contains(r.recommendedMode))throw new IOException("Invalid recommendation");
            r.interrupted=o.getBoolean("interrupted");return r;
        }
        public String text() {
            StringBuilder s = new StringBuilder();
            s.append("OnHand AI · 모델 최적화 벤치마크\n")
                .append("모델: ").append(model).append("\n")
                .append("측정: 각 엔진 준비 후 워밍업 ").append(WARMUP_TOKENS)
                .append("토큰(점수 제외), 본 측정 ").append(TOKENS)
                .append("토큰 × ").append(REPEATS).append("회\n")
                .append("동일 RAW 프롬프트 · 컨텍스트 2048 · 온도 0 · 시드 7\n\n");
            for (EngineResult result: results) {
                s.append(result.summary()).append("\n\n");
            }
            s.append("NPU: 실제 추론 백엔드 미탑재, 점수 미산정\n");
            if (recommendedMode != null)
                s.append("가장 빠른 확인된 모드: ").append(recommendedName).append("\n");
            else s.append("통과한 비교 결과 없음\n");
            if (interrupted) s.append("사용자가 검사를 중단했습니다.\n");
            s.append("\n참고: 토큰 속도는 엔진이 반환한 실제 생성·평가 통계입니다. " +
                "배터리 온도는 SoC 온도가 아니며, 속도 저하는 2회 반복의 변화량이지 " +
                "30분 안정성 인증이 아닙니다. 앱 버전·모델·기기 상태에 따라 달라집니다.\n" +
                "이 결과는 기기에만 저장되며 자동 업로드되지 않습니다.");
            return s.toString();
        }
    }

    public interface Progress { void update(String message); }

    public void cancel() {
        canceled = true;
        HttpURLConnection conn = activeConnection;
        if (conn != null) conn.disconnect();
        Thread t = worker;
        if (t != null) t.interrupt();
        GpuRunner.stop();
        DistributedRunner.stop();
    }

    private void check(Context context) throws Exception {
        if (canceled || Thread.currentThread().isInterrupted())
            throw new InterruptedIOException("검사 취소");
        Reading reading = Reading.capture(context);
        if (Build.VERSION.SDK_INT >= 29 &&
                reading.thermal >= PowerManager.THERMAL_STATUS_SEVERE)
            throw new IOException("기기가 심하게 뜨거워져 안전을 위해 검사 중단");
        ActivityManager am = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo m = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(m);
            if (m.lowMemory)
                throw new IOException("Android 저메모리 경고로 검사 중단");
        }
    }

    private static JSONObject requestBody(String mode, String model, int predict) throws Exception {
        JSONObject body = new JSONObject();
        if ("cpu".equals(mode)) {
            body.put("model", model);
            body.put("prompt", PROMPT);
            body.put("raw", true);
            body.put("stream", true);
            body.put("keep_alive", "5m");
            body.put("options", new JSONObject()
                    .put("num_ctx", 2048).put("num_predict", predict)
                    .put("num_gpu", 0)
                    .put("num_thread", Math.max(1, Math.min(6,
                            Runtime.getRuntime().availableProcessors())))
                    .put("temperature", 0).put("seed", 7));
        } else {
            body.put("prompt", PROMPT);
            body.put("stream", true);
            body.put("cache_prompt", false);
            body.put("n_predict", predict);
            body.put("temperature", 0);
            body.put("seed", 7);
        }
        return body;
    }

    private Sample measure(Context context, String model, String mode, int tokens)
            throws Exception {
        check(context);
        String endpoint = "cpu".equals(mode)
                ? "http://127.0.0.1:11434/api/generate"
                : ("gpu".equals(mode) ? GpuRunner.url()
                   : DistributedRunner.url()) + "/completion";
        HttpURLConnection conn = (HttpURLConnection)new URL(endpoint).openConnection();
        activeConnection = conn;
        conn.setRequestMethod("POST");
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setUseCaches(false);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "cpu".equals(mode) ?
                "application/x-ndjson" : "text/event-stream");
        long started = SystemClock.elapsedRealtime();
        Sample sample = new Sample();
        boolean terminal=false;long lastCheck=0;
        try {
            byte[] bytes = requestBody(mode,model,tokens).toString()
                    .getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out = conn.getOutputStream()) { out.write(bytes); }
            int responseCode = conn.getResponseCode();
            if (responseCode >= 400)
                throw new IOException(mode + " 추론 HTTP " + responseCode);
            try(BufferedReader reader = new BufferedReader(new InputStreamReader(
                    conn.getInputStream(),StandardCharsets.UTF_8))) {
                String line;
                while((line=reader.readLine())!=null) {
                    if(canceled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("검사 취소");
                    long now=SystemClock.elapsedRealtime();
                    if(now-started>300_000)throw new java.net.SocketTimeoutException("Benchmark deadline");
                    if(now-lastCheck>=1000){check(context);if("cooperative".equals(mode)&&!DistributedRunner.peersAvailable(context))throw new IOException("RPC peer disconnected");sample.minFreeMiB=Math.min(sample.minFreeMiB,Reading.capture(context).freeMiB);lastCheck=now;}
                    String data;
                    if("cpu".equals(mode)) data=line.trim();
                    else {
                        if(!line.startsWith("data:"))continue;
                        data=line.substring(5).trim();
                        if("[DONE]".equals(data))break;
                    }
                    if(data.isEmpty())continue;
                    JSONObject obj = new JSONObject(data);
                    if(obj.has("error"))throw new IOException(obj.optString("error"));
                    String chunk = "cpu".equals(mode) ?
                            obj.optString("response","") : obj.optString("content","");
                    if(!chunk.isEmpty() && sample.firstTokenMs<0)
                        sample.firstTokenMs=SystemClock.elapsedRealtime()-started;
                    if("cpu".equals(mode) && obj.optBoolean("done",false)) {
                        terminal=true;
                        sample.generated=obj.optLong("eval_count",0);
                        sample.promptCount=obj.optLong("prompt_eval_count",0);
                        sample.decodeTps=BenchmarkStats.tokensPerSecond(
                            sample.generated, obj.optDouble("eval_duration",0)/1_000_000.0);
                        sample.promptTps=BenchmarkStats.tokensPerSecond(
                            sample.promptCount,obj.optDouble("prompt_eval_duration",0)/1_000_000.0);
                    }
                    if(!"cpu".equals(mode)) {
                        JSONObject timings=obj.optJSONObject("timings");
                        if(timings!=null&&obj.optBoolean("stop",false)) {
                            terminal=true;
                            sample.generated=timings.optLong("predicted_n",0);
                            sample.promptCount=timings.optLong("prompt_n",0);
                            sample.decodeTps=BenchmarkStats.tokensPerSecond(
                                sample.generated,timings.optDouble("predicted_ms",0));
                            sample.promptTps=BenchmarkStats.tokensPerSecond(
                                sample.promptCount,timings.optDouble("prompt_ms",0));
                        }
                    }
                }
            }
            check(context);
            if("cooperative".equals(mode)&&!DistributedRunner.peersAvailable(context))throw new IOException("RPC peer disconnected");
            sample.minFreeMiB=Math.min(sample.minFreeMiB,Reading.capture(context).freeMiB);
            if(!terminal)throw new IOException("Incomplete benchmark stream");
            sample.elapsedMs = SystemClock.elapsedRealtime() - started;
            if(sample.generated < Math.min(12, tokens) || sample.decodeTps <= 0
                    || sample.firstTokenMs < 0)
                throw new IOException("토큰 통계 부족 또는 첫 출력 누락: " +
                        sample.generated + " tokens");
            return sample;
        } finally {
            activeConnection = null;
            conn.disconnect();
        }
    }

    private EngineResult measureEngine(Context context, String model, String mode,
                                       Progress progress) {
        EngineResult result = new EngineResult(mode,
                "cpu".equals(mode) ? "CPU · Ollama" :
                ("gpu".equals(mode) ? "GPU · Vulkan" : "협력 RPC"));
        Context locale=OnHandsLocale.wrap(context);
        String displayEngine=engineLabel(locale,mode);
        Reading before=Reading.capture(context);
        result.batteryStartC=before.batteryC;
        result.thermalStart=before.thermal;
        result.minFreeMiB=before.freeMiB;
        try {
            progress.update(locale.getString(R.string.bench_preparing,displayEngine));
            check(context);
            if("cpu".equals(mode)) {
                GpuRunner.stop();DistributedRunner.stop();
                if(!OllamaForegroundService.isRunning()) throw new IOException("Ollama 서버 중지");
            } else if("gpu".equals(mode)) {
                DistributedRunner.stop();
                if(!GpuRunner.packaged(context)) throw new IOException("Vulkan 실행 파일 없음");
                if(!ComputeProfiler.scan(context).gpuCompute)
                    throw new IOException("기기 Vulkan compute 미보고");
                StringBuilder reason=new StringBuilder();
                if(!GpuRunner.ensure(context,model,reason,()->canceled))
                    throw new IOException("GPU 오프로드 불가: "+reason);
            } else {
                GpuRunner.stop();
                CoopSession session=CoopSession.get(context);
                if(!"host".equals(session.role()) || session.connectedRpcEndpoints().isEmpty())
                    throw new IOException("호스트 세션·활성 보조 노드 없음");
                StringBuilder reason=new StringBuilder();
                if(!DistributedRunner.ensure(context,model,reason,()->canceled))
                    throw new IOException("분산 모델 로딩 실패: "+reason);
            }

            progress.update(locale.getString(R.string.bench_warmup,displayEngine));
            measure(context,model,mode,WARMUP_TOKENS);
            List<Double> tgs=new ArrayList<>(), pps=new ArrayList<>(), firsts=new ArrayList<>();
            double firstRate=0,lastRate=0;
            for(int i=0;i<REPEATS;i++) {
                check(context);
                progress.update(locale.getString(R.string.bench_measuring,displayEngine,i+1,REPEATS));
                Sample m=measure(context,model,mode,TOKENS);
                result.passes++;
                result.minFreeMiB=Math.min(result.minFreeMiB,m.minFreeMiB);
                tgs.add(m.decodeTps);
                pps.add(m.promptTps);
                firsts.add((double)m.firstTokenMs);
                if(i==0)firstRate=m.decodeTps;
                lastRate=m.decodeTps;
                Reading now=Reading.capture(context);
                result.minFreeMiB=Math.min(result.minFreeMiB,now.freeMiB);
            }
            result.decodeTps=BenchmarkStats.median(tgs);
            result.prefillTps=BenchmarkStats.median(pps);
            result.ttftMs=BenchmarkStats.median(firsts);
            result.decayPct=BenchmarkStats.dropPercent(firstRate,lastRate);
        } catch (Exception e) {
            result.error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
        } finally {
            Reading after=Reading.capture(context);
            result.minFreeMiB=Math.min(result.minFreeMiB,after.freeMiB);
            result.batteryEndC=after.batteryC;
            result.thermalEnd=after.thermal;
            if("gpu".equals(mode))GpuRunner.stop();
            if("cooperative".equals(mode))DistributedRunner.stop();
        }
        return result;
    }

    private Report run(Context context, String model, boolean compare, Progress progress) {
        worker=Thread.currentThread();
        Report report=new Report(model);
        String selected=context.getSharedPreferences("MainActivity",Context.MODE_PRIVATE)
                .getString("compute_preference","auto");
        List<String> modes=new ArrayList<>();
        if(compare) modes.addAll(Arrays.asList("cpu","gpu","cooperative"));
        else modes.add("cooperative".equals(selected) ? "cooperative" :
                "gpu".equals(selected) ? "gpu" : "cpu");
        double bestRate=0;
        try {
            for(String mode:modes) {
                if(canceled||InferenceSafety.blocked(context))break;
                EngineResult result=measureEngine(context,model,mode,progress);
                report.results.add(result);
                if(result.valid()) {
                    String best=BenchmarkStats.winner(report.recommendedMode,bestRate,
                            mode,result.decodeTps);
                    if(!mode.equals(best))continue;
                    report.recommendedMode=mode;
                    report.recommendedName=result.name;
                    bestRate=result.decodeTps;
                }
                if(canceled)break;
            }
        } finally {
            GpuRunner.stop();DistributedRunner.stop();
            report.interrupted=canceled;
        }
        return report;
    }

    public static void showSaved(Activity activity,String model) {
        android.content.SharedPreferences prefs=activity.getSharedPreferences("ai_pocket_bench",Context.MODE_PRIVATE);
        String old=prefs.getString("model_"+model,null);
        String structured=prefs.getString("structured_"+model,null);
        if(structured!=null)try{
            Report report=Report.fromStructured(structured,model);
            showReport(activity,report.localizedText(activity),null);return;
        }catch(Exception ignored){}
        showReport(activity,old==null?activity.getString(R.string.bench_none):
            activity.getString(R.string.bench_legacy)+"\n\n"+old,null);
    }

    private static void showReport(Activity activity,String content,String best) {
        TextView view=new TextView(activity);
        view.setText(content);
        view.setTextSize(13);
        view.setTextIsSelectable(true);
        int pad=(int)(16*activity.getResources().getDisplayMetrics().density);
        view.setPadding(pad,pad,pad,pad);
        ScrollView scroll=new ScrollView(activity);
        scroll.addView(view);
        AlertDialog.Builder builder=new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.bench_title))
                .setView(scroll)
                .setNegativeButton(activity.getString(R.string.close),null)
                .setNeutralButton(activity.getString(R.string.copy_result),(d,w)->{
                    ClipboardManager clipboard=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    if(clipboard!=null)clipboard.setPrimaryClip(
                            ClipData.newPlainText(activity.getString(R.string.bench_title),content));
                });
        if(best!=null)builder.setPositiveButton(activity.getString(R.string.bench_apply),(d,w)->
                activity.getPreferences(Context.MODE_PRIVATE).edit()
                        .putString("compute_preference",best).apply());
        builder.show();
    }

    public static void show(Activity activity, String model, Runnable started,
                            Runnable finished) {
        if(model==null||model.isEmpty()) {
            new AlertDialog.Builder(activity).setMessage(activity.getString(R.string.test_need_model))
                    .setPositiveButton(activity.getString(R.string.ok),null).show();return;
        }
        new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.bench_speed))
                .setItems(new String[]{
                    activity.getString(R.string.bench_compare),
                    activity.getString(R.string.bench_current),
                    activity.getString(R.string.bench_previous)
                },(d,which)->{
                    if(which==2){showSaved(activity,model);return;}
                    ModelBenchmark bench=new ModelBenchmark();
                    LinearLayout panel=new LinearLayout(activity);
                    panel.setPadding(30,24,30,16);
                    panel.setOrientation(LinearLayout.VERTICAL);
                    ProgressBar spinner=new ProgressBar(activity);
                    panel.addView(spinner);
                    TextView status=new TextView(activity);
                    status.setText(activity.getString(R.string.bench_intro,model));
                    status.setPadding(0,14,0,10);
                    panel.addView(status);
                    AlertDialog dialog=new AlertDialog.Builder(activity)
                            .setTitle(activity.getString(R.string.bench_running))
                            .setView(panel)
                            .setNegativeButton(activity.getString(R.string.stop),(b,w)->bench.cancel())
                            .create();
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.setOnCancelListener(x->bench.cancel());
                    started.run();
                    activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    dialog.show();
                    Thread t=new Thread(()->{
                        Report report=null;
                        String failure=null;
                        try {
                            report=bench.run(activity.getApplicationContext(),model,which==0,
                                    message->activity.runOnUiThread(()->{
                                        if(dialog.isShowing())status.setText(message+"\n" +
                                            activity.getString(R.string.bench_repeat_note,TOKENS,REPEATS));
                                    }));
                        }catch(Exception e){failure=e.getMessage();}
                        final Report done=report;
                        final String problem=failure;
                        activity.runOnUiThread(()->{
                            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                            if(dialog.isShowing())dialog.dismiss();
                            finished.run();
                            if(activity.isFinishing())return;
                            if(done!=null){
                                String text=done.text(); // Preserve the canonical benchmark format used by recommendations.
                                android.content.SharedPreferences.Editor edit=activity.getSharedPreferences("ai_pocket_bench",Context.MODE_PRIVATE).edit()
                                    .putString("model_"+model,text).remove("structured_"+model);
                                try{edit.putString("structured_"+model,done.structured());}catch(Exception ignored){}
                                edit.apply();
                                showReport(activity,done.localizedText(activity),done.interrupted?null:done.recommendedMode);
                            }else showReport(activity,activity.getString(R.string.bench_failed,errorLabel(activity,problem)),null);
                        });
                    },"model-benchmark");
                    bench.worker=t;
                    t.start();
                }).show();
    }
}
