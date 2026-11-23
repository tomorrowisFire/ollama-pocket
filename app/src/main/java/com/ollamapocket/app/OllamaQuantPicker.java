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
            Toast.makeText(activity,"모델명을 확인하세요",Toast.LENGTH_SHORT).show();return;
        }
        if(!active.compareAndSet(false,true))return;
        final List<String> availableInstalled=new ArrayList<>(installed);
        final AtomicBoolean dismissed=new AtomicBoolean(false);
        AlertDialog loading=new AlertDialog.Builder(activity)
                .setTitle("양자화 버전 확인")
                .setMessage(baseTag+"\nOllama 라이브러리의 다운로드 가능한 버전을 조회합니다…")
                .setNegativeButton("취소",(d,w)->dismissed.set(true))
                .setOnCancelListener(d->dismissed.set(true)).create();
        loading.show();

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
                                throw new Exception("태그 목록이 너무 큽니다");
                            buffer.write(bytes,0,read);
                        }
                        html=buffer.toString(StandardCharsets.UTF_8.name());
                    }
                }finally{connection.disconnect();}
            }catch(Exception e) {
                error=e.getMessage()==null?"인터넷 연결 확인 필요":e.getMessage();
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
                    Toast.makeText(activity,"선택 가능한 모델 버전이 없습니다.",
                        Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] labels=new String[variants.size()];
                for(int i=0;i<variants.size();i++)labels[i]=variants.get(i).display();
                String note=reason!=null?
                    "온라인 버전 확인 실패: "+reason+
                    "\n기본 모델 또는 이미 설치한 버전만 표시합니다.":
                    variants.size()==1?
                    "등록된 양자화 별도 태그를 찾지 못했습니다. 기본 버전만 표시합니다.":
                    "실제로 게시된 Ollama 태그만 표시합니다. 버전별 용량과 " +
                    "메모리 사용량은 다르며, 선택 후 다운로드 요금을 안내합니다.";
                AlertDialog picker=new AlertDialog.Builder(activity)
                    .setTitle("양자화 선택 · "+baseTag)
                    .setItems(labels,(d,which)->{
                        active.set(false);
                        OllamaQuantCatalog.Variant picked=variants.get(which);
                        selected.onChosen(picked.tag,picked.installed);
                    })
                    .setNeutralButton("버전 안내",(d,w)->new AlertDialog.Builder(activity)
                        .setTitle("양자화 버전 안내").setMessage(note)
                        .setPositiveButton("확인",null).show())
                    .setNegativeButton("닫기",null)
                    .create();
                picker.setOnDismissListener(d->active.set(false));
                picker.show();
            });
        },"ollama-quant-tags");
        job.setDaemon(true);
        job.start();
    }
}
