package com.ollamapocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One model card tap -> choose an actually advertised Ollama quantization tag.
 * Does not synthesize tags or download before user selects and confirms.
 */
public final class OllamaQuantPicker {
    private OllamaQuantPicker(){}

    public interface Listener {
        void onChosen(String modelTag,boolean installed);
    }

    private static final AtomicBoolean active=new AtomicBoolean(false);
    private static final int MAX_HTML=4_000_000;

    public static void show(Activity activity,String baseTag,
                            List<String> installed,Listener selected) {
        if(!OllamaQuantCatalog.validBaseTag(baseTag)){
            Toast.makeText(activity,activity.getString(R.string.model_tag_invalid),Toast.LENGTH_SHORT).show();return;
        }
        if(!active.compareAndSet(false,true))return;
        final List<String> availableInstalled=new ArrayList<>(installed);
        final AtomicBoolean dismissed=new AtomicBoolean(false);
        AlertDialog loading=new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.picker_quant_check))
                .setMessage(activity.getString(R.string.picker_quant_loading,baseTag))
                .setNegativeButton(activity.getString(R.string.cancel),(d,w)->dismissed.set(true))
                .setOnCancelListener(d->dismissed.set(true)).create();
        loading.show();WorkbenchWindow.fitCompact(activity,loading);

        Thread job=new Thread(()->{
            String html="";
            String error=null;
            try {
                String name=OllamaQuantCatalog.repoName(baseTag);
                URL url=new URL("https://ollama.com/library/"+name+"/tags");
                HttpURLConnection connection=(HttpURLConnection)url.openConnection();
                connection.setConnectTimeout(9000);
                connection.setReadTimeout(16000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("Accept","text/html");
                connection.setRequestProperty("Accept-Encoding","identity");
                connection.setRequestProperty("User-Agent","AIinPocket-Android/0.9.7");
                try {
                    int code=connection.getResponseCode();
                    if(code!=200)throw new Exception("라이브러리 HTTP "+code);
                    try(InputStream input=connection.getInputStream();
                        ByteArrayOutputStream buffer=new ByteArrayOutputStream()) {
                        byte[] bytes=new byte[8192];
                        int read;
                        while((read=input.read(bytes))!=-1){
                            if(buffer.size()+read>MAX_HTML)
                                throw new Exception(activity.getString(R.string.picker_page_limit));
                            buffer.write(bytes,0,read);
                        }
                        html=buffer.toString(StandardCharsets.UTF_8.name());
                    }
                }finally{connection.disconnect();}
            }catch(Exception e) {
                error=e.getMessage()==null?activity.getString(R.string.picker_network_needed):e.getMessage();
            }
            List<OllamaQuantCatalog.Variant> options;
            try{options=OllamaQuantCatalog.parse(baseTag,html,availableInstalled);}
            catch(Exception e){options=new ArrayList<>();}
            final List<OllamaQuantCatalog.Variant> variants=options;
            final String reason=error;
            activity.runOnUiThread(()->{
                loading.dismiss();
                if(dismissed.get()||activity.isFinishing()||activity.isDestroyed()){
                    active.set(false);return;
                }
                if(variants.isEmpty()){
                    active.set(false);
                    Toast.makeText(activity,activity.getString(R.string.picker_no_versions),
                        Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] labels=new String[variants.size()];
                for(int i=0;i<variants.size();i++)labels[i]=OllamaFamilyPicker.displayLabel(activity,variants.get(i));
                String note=reason!=null?activity.getString(R.string.picker_fallback_note,reason):
                    activity.getString(variants.size()==1?R.string.picker_base_note:R.string.picker_real_note);
                AlertDialog picker=new AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.picker_quant_title,baseTag))
                    .setItems(labels,(d,which)->{
                        active.set(false);
                        OllamaQuantCatalog.Variant picked=variants.get(which);
                        selected.onChosen(picked.tag,picked.installed);
                    })
                    .setNeutralButton(activity.getString(R.string.picker_version_help),(d,w)->{
                        AlertDialog help=new AlertDialog.Builder(activity)
                            .setTitle(activity.getString(R.string.picker_quant_help)).setMessage(note)
                            .setPositiveButton(activity.getString(R.string.ok),null).show();
                        WorkbenchWindow.fitCompact(activity,help);
                    })
                    .setNegativeButton(activity.getString(R.string.close),null)
                    .create();
                picker.setOnDismissListener(d->active.set(false));
                picker.show();WorkbenchWindow.fitCompact(activity,picker);
            });
        },"ollama-quant-tags");
        job.setDaemon(true);
        job.start();
    }
}
