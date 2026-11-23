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
    private static final AtomicBoolean OPEN=new AtomicBoolean(false);
    private static final int MAX_PAGE=3_000_000;

    public static void show(Activity activity,String family,
            List<String> installed,OllamaQuantPicker.Listener onChoose) {
        if(family==null||!family.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,68}")){
            Toast.makeText(activity,"모델명을 확인하세요",Toast.LENGTH_SHORT).show();
            return;
        }
        if(!OPEN.compareAndSet(false,true))return;
        final List<String> installedSnapshot=new ArrayList<>(installed);
        final AtomicBoolean dismissed=new AtomicBoolean(false);
        AlertDialog loading=new AlertDialog.Builder(activity)
            .setTitle(family+" · 모델 목록")
            .setMessage("공식 Ollama에서 모델 크기와 양자화 버전을 확인합니다…")
            .setNegativeButton("취소",(d,w)->dismissed.set(true))
            .setOnCancelListener(d->dismissed.set(true)).create();
        loading.show();
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
                            if(out.size()+n>MAX_PAGE)throw new Exception("태그 목록 너무 큼");
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
                    new AlertDialog.Builder(activity)
                        .setTitle("모델 버전 조회 실패")
                        .setMessage("Ollama에서 이 모델의 실제 버전을 확인하지 못했습니다."+
                            (problem==null?"":"\n"+problem)+
                            "\n없는 모델명을 임의로 생성하지 않습니다.")
                        .setNegativeButton("닫기",null)
                        .setPositiveButton("Ollama에서 확인",(d,w)->activity.startActivity(
                            new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://ollama.com/library/"+family+"/tags"))))
                        .show();
                    return;
                }
                String[] labels=new String[results.size()];
                for(int i=0;i<results.size();i++)labels[i]=results.get(i).display();
                AlertDialog chooser=new AlertDialog.Builder(activity)
                    .setTitle(family+" · 크기와 양자화 선택")
                    .setItems(labels,(d,index)->{
                        OllamaQuantCatalog.Variant selected=results.get(index);
                        OPEN.set(false);
                        onChoose.onChosen(selected.tag,selected.installed);
                    })
                    .setNeutralButton("웹에서 태그 확인",(d,w)->activity.startActivity(
                        new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://ollama.com/library/"+family+"/tags"))))
                    .setNegativeButton("닫기",null)
                    .create();
                chooser.setOnDismissListener(d->OPEN.set(false));
                chooser.show();
            });
        },"ollama-search-family-tags");
        worker.setDaemon(true);worker.start();
    }
}
