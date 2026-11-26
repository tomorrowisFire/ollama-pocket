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
    private boolean continuous, bargeOptIn;
    private BargeMonitor barge;
    void enableBarge(boolean enabled){bargeOptIn=enabled;}
    boolean bargeEnabled(){return bargeOptIn;}
    private void stopBarge(){BargeMonitor old=barge;barge=null;if(old!=null)old.stop();}
    private void startBarge(){
        if(!continuous||!bargeOptIn||afterSpeech==null||barge!=null)return;
        final int token=generation;
        BargeMonitor monitor=new BargeMonitor(a,()->handler.post(()->{
            if(closed||token!=generation||barge==null||!continuous||afterSpeech==null)return;
            Runnable next=afterSpeech;afterSpeech=null;utterancePrefix=null;lastUtterance=null;
            stopBarge();clearTimeout();if(tts!=null)tts.stop();resumeAfterPlayback(next);
        }));
        barge=monitor;monitor.start();
    }
    private Runnable afterSpeech;
    private String lastUtterance;
    private String utterancePrefix;
    private long speechSequence;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable timeout, resumeListening;
    private static final long PLAYBACK_TAIL_MS=350L;
    private void clearResume(){if(resumeListening!=null)handler.removeCallbacks(resumeListening);resumeListening=null;}
    private void resumeAfterPlayback(Runnable next){
        clearResume(); final int token=generation;
        resumeListening=()->{resumeListening=null;if(!closed&&continuous&&token==generation)next.run();};
        handler.postDelayed(resumeListening,PLAYBACK_TAIL_MS);
    }
    private void clearTimeout(){if(timeout!=null)handler.removeCallbacks(timeout);timeout=null;}
    private void armTimeout(long ms){clearTimeout();final int token=generation;timeout=()->{if(token==generation){cancel();toast(R.string.voice_error);}};handler.postDelayed(timeout,ms);}
    boolean continuous(){return continuous;}
    int sessionToken(){return generation;}
    void beginContinuous(){continuous=true;}
    private void finishSpeech(String id, boolean success){
        if(closed)return;
        VoiceQueuePolicy.Event event=VoiceQueuePolicy.event(utterancePrefix,lastUtterance,id,success);
        if(event==VoiceQueuePolicy.Event.ERROR){cancel();toast(R.string.tts_unavailable);return;}
        if(event!=VoiceQueuePolicy.Event.COMPLETE)return;
        stopBarge();clearTimeout();utterancePrefix=null;lastUtterance=null; Runnable next=afterSpeech; afterSpeech=null;
        if(success&&continuous&&next!=null)resumeAfterPlayback(next);
    }
    OnHandsVoice(Activity a) {
        this.a=a;
        tts=new TextToSpeech(a.getApplicationContext(), status->a.runOnUiThread(()->{
            if(closed)return;
            initialized=true;ready=status==TextToSpeech.SUCCESS;
            if(ready)tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){
                public void onStart(String id){}
                public void onDone(String id){a.runOnUiThread(()->finishSpeech(id,true));}
                public void onError(String id){a.runOnUiThread(()->finishSpeech(id,false));}
                public void onStop(String id,boolean interrupted){a.runOnUiThread(()->finishSpeech(id,false));}
            });
            String queued=pendingSpeech;pendingSpeech=null;
            if(queued!=null){if(ready)speak(queued);else {cancel();toast(R.string.tts_unavailable);}}
        }));
    }
    boolean listening(){return recognizer!=null;}
    boolean speaking(){return pendingSpeech!=null || lastUtterance!=null || (tts!=null && tts.isSpeaking());}
    private void toast(int id){if(!closed)Toast.makeText(a,id,Toast.LENGTH_SHORT).show();}
    void listen(Result cb) {
        boolean loop=continuous, opt=bargeOptIn; cancel(); continuous=loop;bargeOptIn=opt;
        if(closed||Build.VERSION.SDK_INT<31||!SpeechRecognizer.isOnDeviceRecognitionAvailable(a)){
            cancel();toast(R.string.voice_unavailable);return;
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
                    boolean loop=continuous, opt=bargeOptIn;cancel();continuous=loop;bargeOptIn=opt;
                    if(rows==null||rows.isEmpty()||rows.get(0)==null||rows.get(0).trim().isEmpty()){cancel();toast(R.string.voice_error);return;}
                    cb.accepted(rows.get(0).trim());
                }
            });
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE,OnHandsLocale.speechLocale(a).toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
            recognizer.startListening(intent);armTimeout(45000);
        }catch(RuntimeException e){cancel();toast(R.string.voice_unavailable);}
    }
    void speakThen(String text,Runnable next){afterSpeech=next;speak(text);}
    void speak(String text){
        if(closed)return;
        if(text==null||text.trim().isEmpty()){cancel();return;}
        if(!initialized){pendingSpeech=text;armTimeout(15000);return;}
        if(!ready||tts==null){cancel();toast(R.string.tts_unavailable);return;}
        Locale target=OnHandsLocale.speechLocale(a);
        Voice selected=null;
        try {
            for(Voice v:tts.getVoices()){
                if(v.isNetworkConnectionRequired()||!v.getLocale().getLanguage().equals(target.getLanguage()))continue;
                if(v.getLocale().getCountry().equals(target.getCountry())){selected=v;break;}
                if(!"zh".equals(target.getLanguage()) && selected==null)selected=v;
            }
            if(selected==null||tts.setVoice(selected)!=TextToSpeech.SUCCESS){cancel();toast(R.string.tts_unavailable);return;}
            int max=Math.min(2000,TextToSpeech.getMaxSpeechInputLength());
            stopBarge();utterancePrefix=null;lastUtterance=null;tts.stop();
            utterancePrefix="oh-"+generation+"-"+(++speechSequence)+"-";
            armTimeout(VoiceQueuePolicy.timeout(text.length()));
            for(int i=0;i<text.length();){
                int end=VoiceQueuePolicy.chunkEnd(text,i,max);
                String utterance=utterancePrefix+i;
                if(end==text.length())lastUtterance=utterance;
                if(tts.speak(text.substring(i,end),i==0?TextToSpeech.QUEUE_FLUSH:TextToSpeech.QUEUE_ADD,null,utterance)==TextToSpeech.ERROR){
                    cancel();toast(R.string.tts_unavailable);break;
                }
                i=end;
            }
            if(lastUtterance!=null)startBarge();
        }catch(RuntimeException e){cancel();toast(R.string.tts_unavailable);}
    }
    void cancel(){
        generation++;stopBarge();clearTimeout();clearResume();pendingSpeech=null;continuous=false;bargeOptIn=false;afterSpeech=null;lastUtterance=null;utterancePrefix=null;
        SpeechRecognizer old=recognizer;recognizer=null;
        if(old!=null){try{old.cancel();}catch(RuntimeException ignored){}try{old.destroy();}catch(RuntimeException ignored){}}
        if(tts!=null)tts.stop();
    }
    void close(){closed=true;cancel();if(tts!=null){tts.shutdown();tts=null;}}
    /** Experimental energy detector. Wired headset plus enabled platform AEC required.
        Never interprets captured audio as a command; the user repeats after playback stops. */
    private static final class BargeMonitor {
        private final Activity activity; private final Runnable detected;
        private volatile boolean stopped;
        private android.media.AudioRecord recorder;
        BargeMonitor(Activity a,Runnable callback){activity=a;detected=callback;}
        void start(){new Thread(this::run,"onhand-barge").start();}
        synchronized void stop(){stopped=true;if(recorder!=null)try{recorder.stop();}catch(RuntimeException ignored){}}
        private static boolean wired(android.media.AudioDeviceInfo device){
            return device!=null&&(device.getType()==android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET||
                device.getType()==android.media.AudioDeviceInfo.TYPE_USB_HEADSET);
        }
        private void run(){
            android.media.AudioRecord rec=null;
            android.media.audiofx.AcousticEchoCanceler aec=null;
            try{
                android.media.AudioManager am=(android.media.AudioManager)activity.getSystemService(android.content.Context.AUDIO_SERVICE);
                if(am==null||!android.media.audiofx.AcousticEchoCanceler.isAvailable())return;
                boolean headset=false;
                for(android.media.AudioDeviceInfo d:am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS))if(wired(d))headset=true;
                if(!headset)return;
                int min=android.media.AudioRecord.getMinBufferSize(16000,android.media.AudioFormat.CHANNEL_IN_MONO,android.media.AudioFormat.ENCODING_PCM_16BIT);
                if(min<=0)return;
                rec=new android.media.AudioRecord(android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION,16000,
                    android.media.AudioFormat.CHANNEL_IN_MONO,android.media.AudioFormat.ENCODING_PCM_16BIT,Math.max(min,3200));
                if(rec.getState()!=android.media.AudioRecord.STATE_INITIALIZED)return;
                aec=android.media.audiofx.AcousticEchoCanceler.create(rec.getAudioSessionId());
                if(aec==null||aec.setEnabled(true)!=android.media.audiofx.AudioEffect.SUCCESS||!aec.getEnabled())return;
                synchronized(this){if(stopped)return;recorder=rec;rec.startRecording();}
                short[] frame=new short[320];int consecutive=0;int warmup=20;
                while(!stopped){
                    int n=rec.read(frame,0,frame.length);
                    if(n<=0||!wired(rec.getRoutedDevice()))break;
                    if(warmup>0){warmup--;continue;}
                    consecutive=VoiceQueuePolicy.bargeFrames(frame,n,consecutive);
                    if(consecutive>=8){if(!stopped)detected.run();break;}
                }
            }catch(RuntimeException ignored){}finally{
                synchronized(this){recorder=null;if(rec!=null)try{rec.stop();}catch(RuntimeException ignored){}}
                if(aec!=null)try{aec.release();}catch(RuntimeException ignored){}
                if(rec!=null)try{rec.release();}catch(RuntimeException ignored){}
            }
        }
    }
}
