package com.ollamapocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Model family returned by public search -> real Ollama size/quantization. */
public final class OllamaFamilyPicker {
    private OllamaFamilyPicker(){}
    static String displayLabel(Activity activity,OllamaQuantCatalog.Variant variant){
        String quant="기본 양자화".equals(variant.quantization)?activity.getString(R.string.picker_default_quant):variant.quantization;
        boolean active=activity instanceof MainActivity&&((MainActivity)activity).isActiveLocalModel(variant.tag);
        return activity.getString(active?R.string.model_active:variant.installed?R.string.model_installed:R.string.model_not_installed)+" · "+quant+"\n"+variant.tag;
    }
    private static final AtomicBoolean OPEN=new AtomicBoolean(false);
    private static final int MAX_PAGE=3_000_000;

    public static void show(Activity activity,String family,
            List<String> installed,OllamaQuantPicker.Listener onChoose) {
        if(family==null||!family.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,68}")){
            Toast.makeText(activity,activity.getString(R.string.model_tag_invalid),Toast.LENGTH_SHORT).show();
            return;
        }
        if(!OPEN.compareAndSet(false,true))return;
        final List<String> installedSnapshot=new ArrayList<>(installed);
        final AtomicBoolean dismissed=new AtomicBoolean(false);
        AlertDialog loading=new AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.picker_family_title,family))
            .setMessage(activity.getString(R.string.picker_family_loading))
            .setNegativeButton(activity.getString(R.string.cancel),(d,w)->dismissed.set(true))
            .setOnCancelListener(d->dismissed.set(true)).create();
        loading.show();WorkbenchWindow.fitCompact(activity,loading);
        Thread worker=new Thread(()->{
            String html="";
            String error=null;
            try{
                HttpURLConnection conn=(HttpURLConnection)new URL(
                    "https://ollama.com/library/"+family+"/tags").openConnection();
                conn.setConnectTimeout(8_000);
                conn.setReadTimeout(16_000);
                conn.setRequestProperty("Accept","text/html");
                conn.setRequestProperty("Accept-Encoding","identity");
                try{
                    int code=conn.getResponseCode();
                    if(code!=200)throw new Exception("HTTP "+code);
                    try(InputStream input=conn.getInputStream();
                        ByteArrayOutputStream out=new ByteArrayOutputStream()){
                        byte[] buffer=new byte[8192];int n;
                        while((n=input.read(buffer))!=-1){
                            if(out.size()+n>MAX_PAGE)throw new Exception(activity.getString(R.string.picker_page_limit));
                            out.write(buffer,0,n);
                        }
                        html=out.toString(StandardCharsets.UTF_8.name());
                    }
                }finally{conn.disconnect();}
            }catch(Exception ex){error=ex.getMessage();}
            List<OllamaQuantCatalog.Variant> candidates;
            try{candidates=OllamaQuantCatalog.parseFamily(family,html,installedSnapshot);}
            catch(Exception ex){candidates=new ArrayList<>();}
            final List<OllamaQuantCatalog.Variant> results=candidates;
            final String problem=error;
            activity.runOnUiThread(()->{
                loading.dismiss();
                if(dismissed.get()||activity.isFinishing()||activity.isDestroyed()){
                    OPEN.set(false);return;
                }
                if(results.isEmpty()){
                    OPEN.set(false);
                    AlertDialog errorDialog=new AlertDialog.Builder(activity)
                        .setTitle(activity.getString(R.string.picker_lookup_failed))
                        .setMessage(activity.getString(R.string.picker_no_fake)+(problem==null?"":"\n"+problem))
                        .setNegativeButton(activity.getString(R.string.close),null)
                        .setPositiveButton(activity.getString(R.string.picker_web),(d,w)->activity.startActivity(
                            new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://ollama.com/library/"+family+"/tags"))))
                        .show();
                    WorkbenchWindow.fitCompact(activity,errorDialog);
                    return;
                }
                String[] labels=new String[results.size()];int checked=-1;
                for(int i=0;i<results.size();i++){
                    labels[i]=displayLabel(activity,results.get(i));
                    if(activity instanceof MainActivity&&((MainActivity)activity).isActiveLocalModel(results.get(i).tag))checked=i;
                }
                AlertDialog chooser=new AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.picker_family_select,family))
                    .setAdapter(WorkbenchStyle.choiceRows(activity,labels,checked),(d,index)->{
                        OllamaQuantCatalog.Variant selected=results.get(index);
                        OPEN.set(false);
                        onChoose.onChosen(selected.tag,selected.installed);
                    })
                    .setNeutralButton(activity.getString(R.string.picker_view_tags),(d,w)->activity.startActivity(
                        new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://ollama.com/library/"+family+"/tags"))))
                    .setNegativeButton(activity.getString(R.string.close),null)
                    .create();
                chooser.setOnDismissListener(d->OPEN.set(false));
                chooser.show();WorkbenchWindow.fitCompact(activity,chooser);
            });
        },"ollama-search-family-tags");
        worker.setDaemon(true);worker.start();
    }
}
