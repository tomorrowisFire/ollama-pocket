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
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.text.TextWatcher;
import android.text.Editable;
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
        activity.runOnUiThread(()->{if(!activity.isFinishing()&&!activity.isDestroyed())r.run();});
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
            .setMessage(message).setPositiveButton(activity.getString(R.string.ok),null).show());
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
    private String size(long bytes) {
        return bytes>0?String.format(activity.getResources().getConfiguration().getLocales().get(0),"%.2f GB",bytes/1_000_000_000.0):activity.getString(R.string.storage_unknown);
    }
    private String licenseLabel(String value){
        return "모델 카드 확인 필요".equals(value)?activity.getString(R.string.hf_license_unknown):value;
    }
    private String quantLabel(String value){
        if("전체 양자화".equals(value))return activity.getString(R.string.quant_all);
        if("미확인".equals(value))return activity.getString(R.string.unknown);
        if(value.endsWith(" 전체"))return activity.getString(R.string.quant_band,quantLabel(value.substring(0,value.length()-3)));
        return value;
    }
    // Only known app-authored errors are mapped. Preserve external details verbatim.
    private static String errorLabel(Activity activity,String value){
        if(value==null)return activity.getString(R.string.coop_unknown_error);
        switch(value){
            case "이 저장소는 로그인·라이선스 동의가 필요합니다. 공개 GGUF만 지원합니다.":
            case "공개 GGUF만 지원합니다.":
            case "라이선스 동의·로그인 필요. 공개 GGUF만 지원합니다.":return activity.getString(R.string.hf_public_only);
            case "잘못된 Hugging Face 저장소":
            case "잘못된 저장소":return activity.getString(R.string.hf_invalid_repo);
            case "선택한 GGUF 파일을 찾을 수 없습니다.":return activity.getString(R.string.hf_missing_file);
            case "GGUF 파일 경로 오류":case "GGUF 경로 깊이 초과":case "잘못된 GGUF 파일 경로":return activity.getString(R.string.hf_invalid_path);
            case "Hugging Face 목록이 너무 큽니다":return activity.getString(R.string.hf_catalog_large);
            case "공개 GGUF 파일 조회 실패":return activity.getString(R.string.hf_weights_failed);
            case "임시 폴더 생성 실패":return activity.getString(R.string.file_directory_failed);
            case "사용자가 다운로드를 중지했습니다.":case "취소되었습니다.":return activity.getString(R.string.download_stopped);
            case "받은 파일 크기가 목록과 다릅니다. 불완전한 파일 삭제.":return activity.getString(R.string.hf_size_mismatch);
            case "등록 취소됨":return activity.getString(R.string.hf_registration_cancelled);
            default:
                String prefix="Ollama 모델 등록 실패: ";
                return value.startsWith(prefix)?activity.getString(R.string.hf_register_failed,value.substring(prefix.length())):value;
        }
    }

    private static String getText(String url,int max) throws Exception {
        if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Search cancelled");
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
                    if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Search cancelled");
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
        add(root,label(activity,activity.getString(R.string.hf_search_intro)));
        EditText query=new EditText(activity);
        query.setSingleLine(true);query.setHint(activity.getString(R.string.hf_search_hint));query.setText("Qwen GGUF");
        add(root,query);
        add(root,label(activity,activity.getString(R.string.hf_family)));
        Spinner family=new Spinner(activity);
        String[] families={activity.getString(R.string.hf_all_families),"Qwen","Llama","Gemma","DeepSeek","Mistral","Phi","GLM","GPT-OSS"};
        ArrayAdapter<String> familiesAdapter=new ArrayAdapter<>(activity,
            android.R.layout.simple_spinner_item,families);
        familiesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        family.setAdapter(familiesAdapter);
        add(root,family);
        family.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(AdapterView<?> parent){}
            @Override public void onItemSelected(AdapterView<?> parent,View view,int pos,long id){
                if(pos>0)query.setText(families[pos]+" GGUF");
            }
        });
        Button search=new Button(activity);search.setAllCaps(false);search.setText(activity.getString(R.string.search));
        add(root,search);
        ScrollView scroller=new ScrollView(activity);
        LinearLayout results=panel(activity);scroller.addView(results);
        root.addView(scroller,new LinearLayout.LayoutParams(-1,dp(340)));
        add(results,label(activity,activity.getString(R.string.hf_review_license)));
        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.hf_title))
                .setView(root).setNegativeButton(activity.getString(R.string.close),null).create();
        dialog.setOnDismissListener(d->{
            // Browsing exits safely. Download has its own cancellable dialog.
            if(downloading==null)io.shutdown();
        });
        search.setOnClickListener(v->{
            String q=query.getText().toString().trim();
            if(q.isEmpty()){query.setError(activity.getString(R.string.hf_query_required));return;}
            search.setEnabled(false);
            results.removeAllViews();add(results,label(activity,activity.getString(R.string.hf_searching)));
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
                        if(models.isEmpty()){add(results,label(activity,activity.getString(R.string.hf_no_results)));return;}
                        for(Model item:models) {
                            Button b=new Button(activity);b.setAllCaps(false);
                            b.setText(item.id+"\n"+(item.gated?activity.getString(R.string.hf_gated):
                                activity.getString(R.string.hf_download_license,item.downloads,licenseLabel(item.license))));
                            add(results,b);
                            b.setEnabled(!item.gated);
                            b.setOnClickListener(w->showFiles(item));
                        }
                    });
                }catch(Exception e){onUi(()->{
                    search.setEnabled(true);results.removeAllViews();
                    add(results,label(activity,activity.getString(R.string.hf_search_failed,errorLabel(activity,e.getMessage()))));
                });}
            });
        });
        dialog.show();
    }
    /** Integrated search uses exact public GGUF files rather than repository-size guesses. */
    static ModelVariantCatalog.SearchBatch searchVariantsDetailed(String query)throws Exception{
        JSONArray models=new JSONArray(getText(BASE+"/api/models?search="+encode(query)+
            "&filter=gguf&sort=downloads&direction=-1&limit=16",MAX_JSON));
        List<Model> publicModels=new ArrayList<>();
        for(int i=0;i<models.length();i++){
            JSONObject o=models.optJSONObject(i);if(o==null)continue;
            String id=o.optString("id","");Object gate=o.opt("gated");
            if(!validRepo(id)||o.optBoolean("private",false)||Boolean.TRUE.equals(gate)||
                gate instanceof String&&!"false".equalsIgnoreCase((String)gate))continue;
            publicModels.add(new Model(id,license(o),o.optLong("downloads",0),false));
        }
        java.util.concurrent.ExecutorService pool=Executors.newFixedThreadPool(4);
        List<java.util.concurrent.Callable<List<ModelVariantCatalog.Variant>>> jobs=new ArrayList<>();
        for(Model model:publicModels)jobs.add(()->{
            List<ModelVariantCatalog.Variant> list=new ArrayList<>();
            for(Weight weight:fetchWeights(model.id))
                list.add(new ModelVariantCatalog.Variant("Hugging Face",model.id,weight.path,weight.size));
            return list;
        });
        try{return ModelVariantCatalog.collect(pool.invokeAll(jobs,30,java.util.concurrent.TimeUnit.SECONDS));}
        finally{pool.shutdownNow();}
    }
    static List<ModelVariantCatalog.Variant> searchVariants(String query)throws Exception{
        ModelVariantCatalog.SearchBatch batch=searchVariantsDetailed(query);
        if(batch.values.isEmpty()&&!batch.complete())throw new Exception("공개 GGUF 파일 조회 실패");
        return new ArrayList<>(batch.values);
    }
    private static List<Weight> fetchWeights(String id) throws Exception{
        if(!validRepo(id))throw new Exception("잘못된 Hugging Face 저장소");
        JSONArray files=new JSONArray(getText(BASE+"/api/models/"+id+"/tree/main?recursive=true",MAX_JSON));
        List<Weight> gguf=new ArrayList<>();
        for(int i=0;i<files.length();i++){
            JSONObject file=files.optJSONObject(i);
            if(file==null||!"file".equals(file.optString("type")))continue;
            String path=file.optString("path",""),name=path.toLowerCase(Locale.ROOT);
            if(!name.endsWith(".gguf")||name.contains("mmproj")||name.matches(".*-\\d{5}-of-\\d{5}\\.gguf"))continue;
            long bytes=file.optLong("size",0);
            JSONObject lfs=file.optJSONObject("lfs");
            if(lfs!=null)bytes=Math.max(bytes,lfs.optLong("size",0));
            if(bytes>0){try{encodePath(path);gguf.add(new Weight(path,bytes));}catch(Exception ignored){}}
        }
        gguf.sort(Comparator.comparingLong(w->w.size));
        return gguf;
    }
    /** Revalidate the selected repo/file and preserve existing license/storage/download safeguards. */
    static void openVariant(Activity activity,ModelVariantCatalog.Variant variant,Runnable installed){
        HuggingFaceCatalog catalog=new HuggingFaceCatalog(activity,installed);
        AlertDialog loading=new AlertDialog.Builder(activity).setTitle("Hugging Face")
            .setMessage(activity.getString(R.string.hf_check_file)).setNegativeButton(activity.getString(R.string.cancel),null).create();
        AtomicBoolean cancelled=new AtomicBoolean(false);
        loading.setOnCancelListener(d->cancelled.set(true));
        loading.show();
        loading.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{cancelled.set(true);loading.dismiss();});
        catalog.io.execute(()->{
            try{
                if(!validRepo(variant.model))throw new Exception("잘못된 저장소");
                JSONObject meta=new JSONObject(getText(BASE+"/api/models/"+variant.model+"?full=true&cardData=true",MAX_JSON));
                Object gate=meta.opt("gated");
                if(meta.optBoolean("private",false)||Boolean.TRUE.equals(gate)||
                    gate instanceof String&&!"false".equalsIgnoreCase((String)gate))throw new Exception("공개 GGUF만 지원합니다.");
                Weight chosen=null;
                for(Weight w:fetchWeights(variant.model))if(w.path.equals(variant.file)){chosen=w;break;}
                if(chosen==null)throw new Exception("선택한 GGUF 파일을 찾을 수 없습니다.");
                final Weight weight=chosen;final String lic=license(meta);
                catalog.onUi(()->{loading.dismiss();if(cancelled.get()||activity.isDestroyed()){catalog.io.shutdown();return;}
                    catalog.confirmDownload(new Model(variant.model,lic,meta.optLong("downloads",0),false),lic,weight);
                });
            }catch(Exception ex){catalog.onUi(()->{loading.dismiss();if(!cancelled.get())catalog.fail(activity.getString(R.string.hf_file_check_failed,errorLabel(activity,ex.getMessage())));});catalog.io.shutdown();}
        });
    }
    private void showFiles(Model model){
        AlertDialog loading=new AlertDialog.Builder(activity).setTitle(model.id)
            .setMessage(activity.getString(R.string.hf_check_list))
            .setNegativeButton(activity.getString(R.string.close),null).show();
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
            }catch(Exception e){onUi(()->{loading.dismiss();fail(activity.getString(R.string.hf_list_failed,errorLabel(activity,e.getMessage())));});}
        });
    }
    private static String ggufQuantization(String path) {
        java.util.regex.Matcher match=java.util.regex.Pattern.compile(
                "(?i)(?:^|[^A-Za-z0-9])(Q[2-8](?:_[A-Z0-9]+){0,3}|BF16|F16)(?:[^A-Za-z0-9]|$)")
                .matcher(path);
        return match.find()?match.group(1).toUpperCase(Locale.ROOT):"미확인";
    }
    private static String quantBand(String value) {
        if(value.startsWith("Q")&&value.length()>1&&Character.isDigit(value.charAt(1)))
            return value.substring(0,2);
        if("BF16".equals(value)||"F16".equals(value))return "F16/BF16";
        return value;
    }
    private void showFileOptions(Model model,String license,List<Weight> files){
        LinearLayout content=panel(activity);
        add(content,label(activity,activity.getString(R.string.hf_repo_details,model.id,licenseLabel(license))));
        Button card=new Button(activity);card.setAllCaps(false);card.setText(activity.getString(R.string.hf_open_card));
        add(content,card);
        card.setOnClickListener(v->activity.startActivity(new Intent(Intent.ACTION_VIEW,
            Uri.parse(BASE+"/"+model.id))));
        add(content,label(activity,activity.getString(R.string.hf_quant_help)));
        Spinner quant=new Spinner(activity);
        List<String> options=new ArrayList<>();
        options.add("전체 양자화");
        List<String> variants=new ArrayList<>();
        for(Weight w:files){
            String q=ggufQuantization(w.path);
            if(!variants.contains(q))variants.add(q);
        }
        for(String band:new String[]{"Q2","Q3","Q4","Q5","Q6","Q8","F16/BF16","미확인"}){
            for(String q:variants)if(band.equals(quantBand(q))){
                options.add(band+" 전체");break;
            }
        }
        java.util.Collections.sort(variants);
        for(String variant:variants)if(!"미확인".equals(variant))options.add(variant);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_item,options){
            @Override public View getView(int position,View convert,android.view.ViewGroup parent){
                View v=super.getView(position,convert,parent);
                if(v instanceof TextView)((TextView)v).setText(quantLabel(getItem(position)));
                return v;
            }
            @Override public View getDropDownView(int position,View convert,android.view.ViewGroup parent){
                View v=super.getDropDownView(position,convert,parent);
                if(v instanceof TextView)((TextView)v).setText(quantLabel(getItem(position)));
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        quant.setAdapter(adapter);add(content,quant);
        EditText filter=new EditText(activity);
        filter.setSingleLine(true);filter.setHint(activity.getString(R.string.hf_file_search));add(content,filter);
        ScrollView scroll=new ScrollView(activity);
        LinearLayout list=panel(activity);scroll.addView(list);
        content.addView(scroll,new LinearLayout.LayoutParams(-1,dp(300)));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.hf_pick_file))
            .setView(content).setNegativeButton(activity.getString(R.string.close),null).create();
        Runnable refresh=()->{
            list.removeAllViews();
            String selected=String.valueOf(quant.getSelectedItem());
            String find=filter.getText().toString().trim().toLowerCase(Locale.ROOT);
            int shown=0;
            for(Weight w:files){
                String q=ggufQuantization(w.path);
                boolean match="전체 양자화".equals(selected) ||
                    (selected.endsWith(" 전체") &&
                        quantBand(q).equals(selected.substring(0,selected.length()-3))) ||
                    selected.equals(q);
                if(!match||(!find.isEmpty()&&!w.path.toLowerCase(Locale.ROOT).contains(find)))continue;
                shown++;
                if(shown>50)break;
                Button item=new Button(activity);item.setAllCaps(false);
                item.setText(w.path+"\n"+size(w.size)+" · "+quantLabel(q));
                add(list,item);
                item.setOnClickListener(v->{dialog.dismiss();confirmDownload(model,license,w);});
            }
            if(shown==0)add(list,label(activity,activity.getString(R.string.hf_no_file)));
        };
        quant.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(AdapterView<?> parent){}
            @Override public void onItemSelected(AdapterView<?> parent,View view,int pos,long id){
                refresh.run();
            }
        });
        filter.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){refresh.run();}
            @Override public void afterTextChanged(Editable s){}
        });
        refresh.run();
        dialog.show();
    }
    private void confirmDownload(Model repo,String license,Weight weight){
        long free=new StatFs(activity.getFilesDir().getAbsolutePath()).getAvailableBytes();
        // Registration through Ollama creates another blob; the temp GGUF and its
        // destination may coexist. 2x model size is a conservative minimum.
        final long needed=ModelDownloadBudget.importRequiredBytes(weight.size);
        if(free<needed) {
            fail(activity.getString(R.string.hf_storage_low,size(needed),size(free)));
            return;
        }
        new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.download_data_title))
            .setMessage(activity.getString(R.string.hf_confirm_download,repo.id,weight.path,size(weight.size),size(needed),licenseLabel(license)))
            .setNegativeButton(activity.getString(R.string.cancel),null)
            .setPositiveButton(activity.getString(R.string.download_confirm),(d,w)->startDownload(repo,weight))
            .show();
    }
    private void startDownload(Model repo,Weight weight){
        if(activity.isFinishing()||activity.isDestroyed())return;
        if(activity instanceof MainActivity&&!((MainActivity)activity).canStartModelImport()){
            fail(activity.getString(R.string.busy));return;
        }
        long free=new StatFs(activity.getFilesDir().getAbsolutePath()).getAvailableBytes();
        long needed=ModelDownloadBudget.importRequiredBytes(weight.size);
        if(weight.size<=0||free<needed){fail(activity.getString(R.string.hf_storage_low,size(needed),size(free)));return;}
        if(!DOWNLOAD_LOCK.compareAndSet(false,true)){
            fail(activity.getString(R.string.hf_busy));return;
        }
        if(!OllamaForegroundService.isRunning()){
            DOWNLOAD_LOCK.set(false);
            fail(activity.getString(R.string.hf_server_required));
            return;
        }
        stopped.set(false);
        LinearLayout panel=panel(activity);
        TextView status=label(activity,activity.getString(R.string.model_download_preparing));add(panel,status);
        ProgressBar progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);add(panel,progress);
        add(panel,label(activity,activity.getString(R.string.ad_area)));
        FrameLayout adSlot=new FrameLayout(activity);
        add(panel,adSlot);
        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle("Hugging Face → Ollama")
                .setView(panel)
                .setNegativeButton(activity.getString(R.string.cancel_download),(d,w)->cancel())
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
                if(stopped.get())throw new java.io.InterruptedIOException("Download cancelled");
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
                        if(count>weight.size-downloaded)throw new java.io.IOException(activity.getString(R.string.hf_size_mismatch));
                        output.write(buffer,0,count);downloaded+=count;
                        if(System.currentTimeMillis()-last>350){
                            final int pct=ModelDownloadBudget.layerPercent(downloaded,weight.size);
                            final String txt=size(downloaded)+" / "+size(weight.size);
                            onUi(()->{progress.setProgress(pct);
                                status.setText(activity.getString(R.string.hf_progress,pct,txt));});
                            last=System.currentTimeMillis();
                        }
                    }
                } finally { conn.disconnect();downloading=null; }
                if(stopped.get())throw new Exception("취소되었습니다.");
                if(downloaded!=weight.size)throw new Exception("받은 파일 크기가 목록과 다릅니다. 불완전한 파일 삭제.");
                if(!NativeWeightPreflight.validGguf(gguf,weight.size))throw new java.io.IOException(activity.getString(R.string.gguf_pick));
                long registrationFree=new StatFs(folder.getAbsolutePath()).getAvailableBytes();
                long registrationNeed=ModelDownloadBudget.registrationRequiredBytes(weight.size);
                if(registrationFree<registrationNeed)throw new java.io.IOException(activity.getString(R.string.hf_storage_low,size(registrationNeed),size(registrationFree)));
                try(OutputStreamWriter writer=new OutputStreamWriter(
                    new FileOutputStream(modelfile),StandardCharsets.UTF_8)){
                    writer.write("FROM "+gguf.getAbsolutePath()+"\n");
                }
                onUi(()->status.setText(activity.getString(R.string.hf_registering)));
                ProcessBuilder pb=new ProcessBuilder(
                    OllamaForegroundService.ollamaBinary(activity).getAbsolutePath(),
                    "create",modelName,"-f",modelfile.getAbsolutePath());
                OllamaForegroundService.configureEnvironment(activity,pb);
                if(stopped.get())throw new java.io.InterruptedIOException("Registration cancelled");
                Process p=pb.start();registration=p;
                if(stopped.get()){p.destroyForcibly();throw new java.io.InterruptedIOException("Registration cancelled");}
                StringBuilder output=new StringBuilder();
                try(BufferedReader br=new BufferedReader(new InputStreamReader(
                        p.getInputStream(),StandardCharsets.UTF_8))){
                    String line;
                    while((line=GpuOffloadEvidence.readDiagnosticLine(br))!=null){
                        if(stopped.get()||Thread.currentThread().isInterrupted()){p.destroyForcibly();throw new Exception("등록 취소됨");}
                        if(output.length()<2500)output.append(line,0,Math.min(line.length(),2499-output.length())).append('\n');
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
                        .setTitle(activity.getString(R.string.model_ready)).setMessage(activity.getString(R.string.hf_installed,modelName))
                        .setPositiveButton(activity.getString(R.string.ok),null).show();
                });
            } catch(Exception e){
                fail(activity.getString(R.string.hf_download_error,errorLabel(activity,e.getMessage())));
                onUi(dialog::dismiss);
            } finally {
                downloading=null;
                Process remaining=registration;registration=null;
                if(remaining!=null&&remaining.isAlive())remaining.destroyForcibly();
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
