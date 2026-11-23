package com.ollamapocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.StatFs;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Public Hugging Face GGUF catalog and downloader.
 * - No Hugging Face token is requested or stored.
 * - No restricted/gated/private weights are bypassed.
 * - No external inference calls: the GGUF is registered with local Ollama.
 * - On-device users must review each model's own licence and storage costs.
 */
public final class HuggingFaceCatalog {
    private static final String BASE="https://huggingface.co";
    private static final int MAX_JSON=3_000_000;
    private static final long MIB=1024L*1024L;
    private final Activity activity;
    private final Runnable onInstalled;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean DOWNLOAD_LOCK=new AtomicBoolean(false);
    public static boolean isDownloading(){return DOWNLOAD_LOCK.get();}
    private final AtomicBoolean stopped=new AtomicBoolean(false);
    private volatile HttpURLConnection downloading;
    private volatile Process registration;

    private static final class Model {
        final String id, license;
        final long downloads;
        final boolean gated;
        Model(String id,String license,long downloads,boolean gated) {
            this.id=id;this.license=license;this.downloads=downloads;this.gated=gated;
        }
    }
    private static final class Weight {
        final String path;
        final long size;
        Weight(String path,long size){this.path=path;this.size=size;}
    }

    private HuggingFaceCatalog(Activity activity,Runnable installed){
        this.activity=activity;this.onInstalled=installed;
    }
    public static void open(Activity activity,Runnable installed){
        new HuggingFaceCatalog(activity,installed).showSearch();
    }
    private void onUi(Runnable r) {
        activity.runOnUiThread(()->{if(!activity.isFinishing())r.run();});
    }
    private static TextView label(Activity a,String text) {
        TextView t=new TextView(a);t.setText(text);t.setTextSize(13);
        t.setPadding(12,10,12,10);return t;
    }
    private static LinearLayout panel(Activity a){
        LinearLayout l=new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(14*a.getResources().getDisplayMetrics().density);
        l.setPadding(pad,pad,pad,pad);
        return l;
    }
    private static void add(LinearLayout group, View view) {
        group.addView(view,new LinearLayout.LayoutParams(-1,-2));
    }
    private void fail(String message){
        onUi(()->new AlertDialog.Builder(activity).setTitle("Hugging Face")
            .setMessage(message).setPositiveButton("확인",null).show());
    }
    private static String encode(String part) throws Exception {
        return URLEncoder.encode(part,StandardCharsets.UTF_8.name()).replace("+","%20");
    }
    private static boolean validRepo(String repo){
        return repo!=null&&repo.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,98}/[A-Za-z0-9][A-Za-z0-9._-]{0,98}")
                &&!repo.contains("..");
    }
    private static String encodePath(String path) throws Exception {
        if(path==null||!path.toLowerCase(Locale.ROOT).endsWith(".gguf"))
            throw new Exception("GGUF 파일 경로 오류");
        String[] segments=path.split("/",-1);
        if(segments.length<1||segments.length>15)throw new Exception("GGUF 경로 깊이 초과");
        StringBuilder out=new StringBuilder();
        for(String seg:segments){
            if(seg.isEmpty()||".".equals(seg)||"..".equals(seg)||seg.length()>180
                    ||seg.indexOf('\\')>=0)throw new Exception("잘못된 GGUF 파일 경로");
            if(out.length()>0)out.append('/');
            out.append(encode(seg));
        }
        return out.toString();
    }
    private static int dp(int n){return (int)(n*android.content.res.Resources.getSystem().getDisplayMetrics().density+0.5f);}
    private static String size(long bytes) {
        return bytes>0?String.format(Locale.KOREA,"%.2f GB",bytes/1_000_000_000.0):"용량 미확인";
    }

    private static String getText(String url,int max) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(9000);conn.setReadTimeout(20000);
        conn.setRequestProperty("Accept","application/json");
        conn.setRequestProperty("User-Agent","AIinPocket-Android/0.9");
        try {
            int status=conn.getResponseCode();
            if(status==401||status==403)
                throw new Exception("이 저장소는 로그인·라이선스 동의가 필요합니다. 공개 GGUF만 지원합니다.");
            if(status>=400)throw new Exception("Hugging Face HTTP "+status);
            try(InputStream stream=conn.getInputStream()){
                byte[] buffer=new byte[8192];
                java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
                int n;
                while((n=stream.read(buffer))!=-1){
                    if(out.size()+n>max)throw new Exception("Hugging Face 목록이 너무 큽니다");
                    out.write(buffer,0,n);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {conn.disconnect();}
    }
    private static String license(JSONObject item){
        JSONObject card=item.optJSONObject("cardData");
        String value=card==null?"":card.optString("license","");
        return value.isEmpty()?"모델 카드 확인 필요":value;
    }
    private void showSearch(){
        LinearLayout root=panel(activity);
        add(root,label(activity,"Hugging Face 공개 GGUF 모델 검색\n다운로드·등록은 이 기기에서 처리합니다."));
        EditText query=new EditText(activity);
        query.setSingleLine(true);query.setHint("모델 검색: Qwen, Gemma 등");query.setText("Qwen GGUF");
        add(root,query);
        Button search=new Button(activity);search.setAllCaps(false);search.setText("모델 찾기");
        add(root,search);
        ScrollView scroller=new ScrollView(activity);
        LinearLayout results=panel(activity);scroller.addView(results);
        root.addView(scroller,new LinearLayout.LayoutParams(-1,dp(340)));
        add(results,label(activity,"모델과 라이선스를 확인한 뒤 GGUF 파일을 선택하세요."));
        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle("Hugging Face 모델")
                .setView(root).setNegativeButton("닫기",null).create();
        dialog.setOnDismissListener(d->{
            // Browsing exits safely. Download has its own cancellable dialog.
            if(downloading==null)io.shutdown();
        });
        search.setOnClickListener(v->{
            String q=query.getText().toString().trim();
            if(q.isEmpty()){query.setError("검색어를 입력하세요");return;}
            search.setEnabled(false);
            results.removeAllViews();add(results,label(activity,"Hugging Face 검색 중…"));
            io.execute(()->{
                try {
                    JSONArray array=new JSONArray(getText(BASE+"/api/models?search="+encode(q)
                        +"&filter=gguf&sort=downloads&direction=-1&limit=20",MAX_JSON));
                    List<Model> models=new ArrayList<>();
                    for(int i=0;i<array.length();i++){
                        JSONObject o=array.optJSONObject(i);
                        if(o==null)continue;
                        String id=o.optString("id","");
                        if(!validRepo(id))continue;
                        Object gated=o.opt("gated");
                        boolean isGated=Boolean.TRUE.equals(gated) ||
                                (gated instanceof String && !"false".equalsIgnoreCase((String)gated));
                        models.add(new Model(id,license(o),o.optLong("downloads",0),isGated));
                    }
                    onUi(()->{
                        search.setEnabled(true);results.removeAllViews();
                        if(models.isEmpty()){add(results,label(activity,"검색된 공개 GGUF 저장소가 없습니다."));return;}
                        for(Model item:models) {
                            Button b=new Button(activity);b.setAllCaps(false);
                            b.setText(item.id+"\n"+(item.gated?"접근 제한 · 로그인 필요":
                                "다운로드 "+item.downloads+" · 라이선스 "+item.license));
                            add(results,b);
                            b.setEnabled(!item.gated);
                            b.setOnClickListener(w->showFiles(item));
                        }
                    });
                }catch(Exception e){onUi(()->{
                    search.setEnabled(true);results.removeAllViews();
                    add(results,label(activity,"검색 실패: "+e.getMessage()));
                });}
            });
        });
        dialog.show();
    }
    private void showFiles(Model model){
        AlertDialog loading=new AlertDialog.Builder(activity).setTitle(model.id)
            .setMessage("GGUF 파일 목록과 라이선스를 확인 중…")
            .setNegativeButton("닫기",null).show();
        io.execute(()->{
            try {
                String repoPath=model.id.split("/")[0]+"/"+model.id.split("/")[1];
                String root=BASE+"/api/models/"+repoPath;
                JSONObject meta=new JSONObject(getText(root+"?full=true&cardData=true",MAX_JSON));
                final String license=license(meta);
                JSONArray files=new JSONArray(getText(root+"/tree/main?recursive=true",MAX_JSON));
                List<Weight> gguf=new ArrayList<>();
                for(int i=0;i<files.length();i++){
                    JSONObject file=files.optJSONObject(i);
                    if(file==null||!"file".equals(file.optString("type")))continue;
                    String path=file.optString("path","");
                    String base=path.toLowerCase(Locale.ROOT);
                    if(!base.endsWith(".gguf")||base.contains("mmproj")||
                       base.matches(".*-\\d{5}-of-\\d{5}\\.gguf"))continue;
                    long bytes=file.optLong("size",0);
                    JSONObject lfs=file.optJSONObject("lfs");
                    if(lfs!=null)bytes=Math.max(bytes,lfs.optLong("size",0));
                    if(bytes>0)gguf.add(new Weight(path,bytes));
                }
                gguf.sort(Comparator.comparingLong(w->w.size));
                onUi(()->{
                    loading.dismiss();
                    showFileOptions(model,license,gguf);
                });
            }catch(Exception e){onUi(()->{loading.dismiss();fail("파일 목록 실패: "+e.getMessage());});}
        });
    }
    private void showFileOptions(Model model,String license,List<Weight> files){
        LinearLayout content=panel(activity);
        add(content,label(activity,"저장소: "+model.id+"\n라이선스: "+license+
            "\n모델별 사용·재배포 조건을 직접 확인해야 합니다."+
            "\n분할 GGUF·프로젝터 파일은 이 버전에서 제외합니다."));
        Button card=new Button(activity);card.setAllCaps(false);card.setText("Hugging Face 모델 카드 열기");
        add(content,card);
        card.setOnClickListener(v->activity.startActivity(new Intent(Intent.ACTION_VIEW,
            Uri.parse(BASE+"/"+model.id))));
        ScrollView scroll=new ScrollView(activity);
        LinearLayout list=panel(activity);scroll.addView(list);
        content.addView(scroll,new LinearLayout.LayoutParams(-1,dp(330)));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("GGUF 파일 선택")
            .setView(content).setNegativeButton("닫기",null).create();
        for(int i=0;i<Math.min(35,files.size());i++){
            Weight w=files.get(i);
            Button b=new Button(activity);b.setAllCaps(false);
            b.setText(w.path+"\n"+size(w.size));
            add(list,b);
            b.setOnClickListener(v->{
                dialog.dismiss();
                confirmDownload(model,license,w);
            });
        }
        if(files.isEmpty())add(list,label(activity,"지원되는 단일 GGUF 파일이 없습니다."));
        dialog.show();
    }
    private void confirmDownload(Model repo,String license,Weight weight){
        long free=new StatFs(activity.getFilesDir().getAbsolutePath()).getAvailableBytes();
        // Registration through Ollama creates another blob; the temp GGUF and its
        // destination may coexist. 2x model size is a conservative minimum.
        final long needed=weight.size*2+512*MIB;
        if(free<needed) {
            fail("저장 공간 부족: 필요한 여유 "+size(needed)+" / 현재 "+size(free)+
                "\nGGUF 다운로드와 Ollama 등록 중 두 복사본이 일시적으로 필요합니다.");
            return;
        }
        new AlertDialog.Builder(activity).setTitle("모델 다운로드·데이터 사용")
            .setMessage("모델: "+repo.id+"\n파일: "+weight.path+"\n다운로드 용량 "+size(weight.size)+
                "\n임시 저장공간 포함 필요 "+size(needed)+
                "\n라이선스: "+license+
                "\n\nWi-Fi 또는 모바일 데이터가 사용되며 요금제에 따라 통신료가 발생할 수 있습니다."+
                " 다운로드한 모델의 사용 조건을 확인하고 동의한 경우에만 진행하세요.")
            .setNegativeButton("취소",null)
            .setPositiveButton("확인 후 다운로드",(d,w)->startDownload(repo,weight))
            .show();
    }
    private void startDownload(Model repo,Weight weight){
        if(!DOWNLOAD_LOCK.compareAndSet(false,true)){
            fail("다른 GGUF 다운로드·등록이 진행 중입니다.");return;
        }
        if(!OllamaForegroundService.isRunning()){
            DOWNLOAD_LOCK.set(false);
            fail("로컬 Ollama 엔진이 실행 중이어야 합니다. 설정에서 서버 상태를 확인하세요.");
            return;
        }
        stopped.set(false);
        LinearLayout panel=panel(activity);
        TextView status=label(activity,"다운로드 준비 중…");add(panel,status);
        ProgressBar progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);add(panel,progress);
        add(panel,label(activity,"광고 · 네트워크 데이터 사용 가능"));
        FrameLayout adSlot=new FrameLayout(activity);
        add(panel,adSlot);
        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle("Hugging Face → Ollama")
                .setView(panel)
                .setNegativeButton("다운로드 중지",(d,w)->cancel())
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(d->cancel());
        dialog.setOnDismissListener(d->{
            if(activity instanceof MainActivity)
                ((MainActivity)activity).unregisterHfDownloadAd(adSlot);
        });
        dialog.show();
        if(activity instanceof MainActivity)
            ((MainActivity)activity).registerHfDownloadAd(adSlot);
        io.execute(()->{
            File gguf=null,modelfile=null;
            try {
                File folder=new File(activity.getFilesDir(),"imports");
                if(!folder.isDirectory()&&!folder.mkdirs())throw new Exception("임시 폴더 생성 실패");
                String slug=repo.id.replace('/','-').toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9._-]","-");
                if(slug.length()>60)slug=slug.substring(0,60);
                String modelName="hf-"+slug+"-"+System.currentTimeMillis();
                gguf=new File(folder,modelName+".gguf");
                modelfile=new File(folder,modelName+".Modelfile");
                String[] names=repo.id.split("/");
                String url=BASE+"/"+encode(names[0])+"/"+encode(names[1])+
                    "/resolve/main/"+encodePath(weight.path)+"?download=true";
                HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
                downloading=conn;conn.setConnectTimeout(15000);conn.setReadTimeout(60000);
                conn.setRequestProperty("Accept-Encoding","identity");
                int code=conn.getResponseCode();
                if(code==401||code==403)throw new Exception("라이선스 동의·로그인 필요. 공개 GGUF만 지원합니다.");
                if(code>=400)throw new Exception("GGUF HTTP "+code);
                long downloaded=0;
                try(InputStream input=conn.getInputStream();
                    FileOutputStream output=new FileOutputStream(gguf)) {
                    byte[] buffer=new byte[1024*1024];int count;
                    long last=0;
                    while((count=input.read(buffer))!=-1) {
                        if(stopped.get())throw new Exception("사용자가 다운로드를 중지했습니다.");
                        output.write(buffer,0,count);downloaded+=count;
                        if(System.currentTimeMillis()-last>350){
                            final int pct=(int)Math.min(100,downloaded*100/weight.size);
                            final String txt=size(downloaded)+" / "+size(weight.size);
                            onUi(()->{progress.setProgress(pct);
                                status.setText("파일 다운로드 "+pct+"% · "+txt);});
                            last=System.currentTimeMillis();
                        }
                    }
                } finally { conn.disconnect();downloading=null; }
                if(stopped.get())throw new Exception("취소되었습니다.");
                if(downloaded!=weight.size)throw new Exception("받은 파일 크기가 목록과 다릅니다. 불완전한 파일 삭제.");
                try(OutputStreamWriter writer=new OutputStreamWriter(
                    new FileOutputStream(modelfile),StandardCharsets.UTF_8)){
                    writer.write("FROM "+gguf.getAbsolutePath()+"\n");
                }
                onUi(()->status.setText("다운로드 완료 · 로컬 Ollama 모델 등록 중…"));
                ProcessBuilder pb=new ProcessBuilder(
                    OllamaForegroundService.ollamaBinary(activity).getAbsolutePath(),
                    "create",modelName,"-f",modelfile.getAbsolutePath());
                OllamaForegroundService.configureEnvironment(activity,pb);
                Process p=pb.start();registration=p;
                StringBuilder output=new StringBuilder();
                try(BufferedReader br=new BufferedReader(new InputStreamReader(
                        p.getInputStream(),StandardCharsets.UTF_8))){
                    String line;
                    while((line=br.readLine())!=null){
                        if(stopped.get()){p.destroyForcibly();throw new Exception("등록 취소됨");}
                        if(output.length()<2500)output.append(line).append('\n');
                    }
                }
                int exit=p.waitFor();
                registration=null;
                if(stopped.get())throw new Exception("등록 취소됨");
                if(exit!=0)throw new Exception("Ollama 모델 등록 실패: "+output);
                onUi(()->{
                    dialog.dismiss();
                    onInstalled.run();
                    new AlertDialog.Builder(activity)
                        .setTitle("모델 설치 완료").setMessage(modelName+"\n모델 탭에서 선택할 수 있습니다.")
                        .setPositiveButton("확인",null).show();
                });
            } catch(Exception e){
                fail("Hugging Face 다운로드/등록 실패: "+e.getMessage());
                onUi(dialog::dismiss);
            } finally {
                downloading=null;registration=null;
                if(gguf!=null)gguf.delete();
                if(modelfile!=null)modelfile.delete();
                DOWNLOAD_LOCK.set(false);
                io.shutdown();
            }
        });
    }
    private void cancel(){
        stopped.set(true);
        HttpURLConnection conn=downloading;
        if(conn!=null)conn.disconnect();
        Process p=registration;
        if(p!=null)p.destroyForcibly();
    }
}
