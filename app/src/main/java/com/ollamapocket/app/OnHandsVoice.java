package com.ollamapocket.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;

/** Offline-only, user-initiated speech. Never falls back to network speech recognition. */
final class OnHandsVoice {
    interface Result { void accepted(String text); }
    private final Activity a;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ready, initialized, closed;
    private String pendingSpeech;
    private int generation;
    OnHandsVoice(Activity a) {
        this.a=a;
        tts=new TextToSpeech(a.getApplicationContext(), status->a.runOnUiThread(()->{
            if(closed)return;
            initialized=true;ready=status==TextToSpeech.SUCCESS;
            String queued=pendingSpeech;pendingSpeech=null;
            if(queued!=null){if(ready)speak(queued);else toast(R.string.tts_unavailable);}
        }));
    }
    boolean listening(){return recognizer!=null;}
    boolean speaking(){return pendingSpeech!=null || (tts!=null && tts.isSpeaking());}
    private void toast(int id){if(!closed)Toast.makeText(a,id,Toast.LENGTH_SHORT).show();}
    void listen(Result cb) {
        cancel();
        if(closed||Build.VERSION.SDK_INT<31||!SpeechRecognizer.isOnDeviceRecognitionAvailable(a)){
            toast(R.string.voice_unavailable);return;
        }
        final int id=++generation;
        try {
            recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(a);
            recognizer.setRecognitionListener(new RecognitionListener(){
                public void onReadyForSpeech(Bundle b){if(id==generation)toast(R.string.listening);}
                public void onBeginningOfSpeech(){}
                public void onRmsChanged(float r){}
                public void onBufferReceived(byte[] b){}
                public void onEndOfSpeech(){}
                public void onPartialResults(Bundle b){}
                public void onEvent(int type,Bundle b){}
                public void onError(int error){if(id==generation){cancel();toast(R.string.voice_error);}}
                public void onResults(Bundle b){
                    if(closed||id!=generation)return;
                    ArrayList<String> rows=b==null?null:b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    cancel();
                    if(rows==null||rows.isEmpty()||rows.get(0).trim().isEmpty()){toast(R.string.voice_error);return;}
                    cb.accepted(rows.get(0).trim());
                }
            });
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE,OnHandsLocale.speechLocale(a).toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
            recognizer.startListening(intent);
        }catch(RuntimeException e){cancel();toast(R.string.voice_unavailable);}
    }
    void speak(String text){
        if(closed||text==null||text.isEmpty())return;
        if(!initialized){pendingSpeech=text;return;}
        if(!ready||tts==null){toast(R.string.tts_unavailable);return;}
        Locale target=OnHandsLocale.speechLocale(a);
        Voice selected=null;
        try {
            for(Voice v:tts.getVoices()){
                if(v.isNetworkConnectionRequired()||!v.getLocale().getLanguage().equals(target.getLanguage()))continue;
                if(v.getLocale().getCountry().equals(target.getCountry())){selected=v;break;}
                if(!"zh".equals(target.getLanguage()) && selected==null)selected=v;
            }
            if(selected==null||tts.setVoice(selected)!=TextToSpeech.SUCCESS){toast(R.string.tts_unavailable);return;}
            int max=Math.min(2000,TextToSpeech.getMaxSpeechInputLength());
            tts.stop();
            for(int i=0;i<text.length();){
                int end=Math.min(text.length(),i+max);
                if(end<text.length()&&Character.isHighSurrogate(text.charAt(end-1)))end--;
                if(tts.speak(text.substring(i,end),i==0?TextToSpeech.QUEUE_FLUSH:TextToSpeech.QUEUE_ADD,null,"oh-"+i)==TextToSpeech.ERROR){
                    tts.stop();toast(R.string.tts_unavailable);break;
                }
                i=end;
            }
        }catch(RuntimeException e){toast(R.string.tts_unavailable);}
    }
    void cancel(){
        generation++;pendingSpeech=null;
        SpeechRecognizer old=recognizer;recognizer=null;
        if(old!=null){try{old.cancel();}catch(RuntimeException ignored){}try{old.destroy();}catch(RuntimeException ignored){}}
        if(tts!=null)tts.stop();
    }
    void close(){closed=true;cancel();if(tts!=null){tts.shutdown();tts=null;}}
}
