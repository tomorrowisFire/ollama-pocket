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
    private final VoiceModelStore voiceModels;
    private static java.lang.ref.WeakReference<OnHandsVoice> owner=new java.lang.ref.WeakReference<>(null);
    private SpeechRecognizer recognizer;
    private WhisperCapture whisper;
    private NeuralTtsPlayer neural;
    private TextToSpeech tts;
    private boolean ready, initialized, closed;
    private String pendingSpeech;
    private int generation;
    private boolean continuous, bargeOptIn,reviewPending;
    private String partialTranscript="";
    private String speechLanguage="app";
    private int rateIndex=1;
    private VoiceInteractionPolicy.Capture capture;
    void configure(String language,int rate){speechLanguage=VoiceInteractionPolicy.language(language);rateIndex=VoiceInteractionPolicy.rateIndex(rate);}
    private Locale targetLocale(){return VoiceInteractionPolicy.locale(speechLanguage,OnHandsLocale.speechLocale(a));}
    void reviewPending(boolean value){reviewPending=value;}
    boolean awaitingReview(){return reviewPending;}
    boolean processingSpeech(){return capture!=null&&capture.state()==VoiceInteractionPolicy.Capture.State.FINALIZING;}
    boolean speechReady(){return capture!=null&&capture.state()==VoiceInteractionPolicy.Capture.State.LISTENING;}
    boolean canFinishListening(){return (recognizer!=null||whisper!=null)&&capture!=null&&capture.canFinish();}
    void finishListening(){
        if(whisper!=null){
            if(!closed&&capture!=null&&capture.finish())whisper.finish();
            return;
        }
        SpeechRecognizer current=recognizer;
        if(closed||current==null||capture==null||!capture.finish())return;
        try{current.stopListening();}catch(RuntimeException e){cancel();toast(R.string.voice_error);}
    }
    String partialTranscript(){return partialTranscript;}
    private BargeMonitor barge;
    private long bargeEpoch;
    void enableBarge(boolean enabled){bargeOptIn=enabled;}
    boolean bargeEnabled(){return bargeOptIn;}
    private void stopBarge(){bargeEpoch++;BargeMonitor old=barge;barge=null;if(old!=null)old.stop();}
    private void startBarge(){
        if(!continuous||!bargeOptIn||!continuation.pending()||barge!=null)return;
        final int token=generation;
        final long epoch=++bargeEpoch;
        final VoiceInteractionPolicy.Playback currentPlayback=playback;
        BargeMonitor monitor=new BargeMonitor(a,()->handler.post(()->{
            if(closed||token!=generation||epoch!=bargeEpoch||barge==null||!continuous||!continuation.pending()||currentPlayback==null||playback!=currentPlayback||currentPlayback.paused())return;
            closeNeural();clearPlayback();
            stopBarge();clearTimeout();if(tts!=null)try{tts.stop();}catch(RuntimeException ignored){}resumeAfterPlayback();
        }));
        barge=monitor;monitor.start();
    }
    private final VoiceContinuation continuation=new VoiceContinuation();
    private VoiceInteractionPolicy.Playback playback;
    private java.util.function.BooleanSupplier playbackGuard=()->true;
    private boolean playbackComplete;
    private long speechDeadline;
    private String playbackVoiceName;
    private Locale playbackVoiceLocale;
    void setPlaybackGuard(java.util.function.BooleanSupplier guard){playbackGuard=guard==null?()->true:guard;}
    boolean playbackCompleted(){return playbackComplete;}
    private boolean playbackAllowed(){try{return playbackGuard.getAsBoolean();}catch(RuntimeException e){return false;}}
    /** Main-thread ownership also covers separate chat/workspace controller instances. */
    private void claim(){OnHandsVoice previous=owner.get();if(previous!=null&&previous!=this)previous.cancel();owner=new java.lang.ref.WeakReference<>(this);}
    private void closeNeural(){NeuralTtsPlayer old=neural;neural=null;if(old!=null)old.close();}
    private void clearPlayback(){playback=null;speechDeadline=0;playbackVoiceName=null;playbackVoiceLocale=null;}
    private static boolean offlineVoice(Locale target,Voice voice){
        return voice!=null&&VoiceOfflinePolicy.eligible(voice.getName(),
            VoiceInteractionPolicy.localeRank(target,voice.getLocale()),voice.isNetworkConnectionRequired(),voice.getFeatures());
    }
    private boolean selectedVoiceCurrent(){
        Voice voice=tts.getVoice();
        return voice!=null&&VoiceOfflinePolicy.matches(playbackVoiceName,voice.getName(),
            VoiceInteractionPolicy.localeRank(playbackVoiceLocale,voice.getLocale()),voice.isNetworkConnectionRequired(),voice.getFeatures());
    }
    boolean playbackPaused(){return playback!=null&&playback.paused();}
    boolean canPausePlayback(){return playback!=null&&!playback.finished();}
    int playbackPercent(){return playback==null?0:playback.percent();}
    boolean playbackActive(){return playback!=null&&!playback.paused();}
    void togglePlaybackPause(){
        if(closed||playback==null||(tts==null&&neural==null))return;
        if(playback.paused()){
            if(playback.resume())playNextSpeechChunk();return;
        }
        if(playback.pause()){
            stopBarge();clearTimeout();
            if(neural!=null)neural.stop();
            if(tts!=null)try{tts.stop();}catch(RuntimeException ignored){}
            long left=speechDeadline-android.os.SystemClock.elapsedRealtime();
            if(left<=0){cancel();toast(R.string.tts_unavailable);}else armTimeout(left);
        }
    }
    private long speechSequence;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable timeout, resumeListening;
    private static final long PLAYBACK_TAIL_MS=350L;
    private void clearResume(){if(resumeListening!=null)handler.removeCallbacks(resumeListening);resumeListening=null;}
    private void resumeAfterPlayback(){
        if(!continuous){continuation.clear();return;}
        final VoiceContinuation.Ticket ticket=continuation.schedule();
        if(ticket==null)return;
        clearResume(); final int token=generation;
        resumeListening=()->{
            Runnable next=continuation.consume(ticket);
            if(next==null)return;
            resumeListening=null;
            if(!closed&&continuous&&token==generation&&playbackAllowed())next.run();
        };
        handler.postDelayed(resumeListening,PLAYBACK_TAIL_MS);
    }
    private void clearTimeout(){if(timeout!=null)handler.removeCallbacks(timeout);timeout=null;}
    private void armTimeout(long ms){clearTimeout();final int token=generation;timeout=()->{if(token==generation){cancel();toast(R.string.voice_error);}};handler.postDelayed(timeout,ms);}
    boolean continuous(){return continuous;}
    int sessionToken(){return generation;}
    void beginContinuous(){continuous=true;}
    private void finishSpeech(String id, boolean success){
        if(closed)return;
        VoiceInteractionPolicy.Playback p=playback;
        if(p==null||!p.current(id))return;
        if(!playbackAllowed()){cancel();return;}
        if(!success){cancel();toast(R.string.tts_unavailable);return;}
        if(!p.acknowledge(id))return;
        clearTimeout();
        if(!p.finished()){playNextSpeechChunk();return;}
        completePlayback();
    }
    private void completePlayback(){
        playbackComplete=true;
        stopBarge();closeNeural();clearPlayback();clearTimeout();
        resumeAfterPlayback();
    }
    private void playNextSpeechChunk(){
        if(closed||(tts==null&&neural==null)||playback==null||playback.finished()||playback.paused())return;
        if(!playbackAllowed()){cancel();return;}
        long left=speechDeadline-android.os.SystemClock.elapsedRealtime();
        if(left<=0){cancel();toast(R.string.tts_unavailable);return;}
        try{
            if(neural!=null){
                VoiceInteractionPolicy.Playback.Chunk chunk;
                // Formatting-only chunks have no waveform; advance without recursive callbacks.
                for(;;){
                    chunk=playback.next();
                    if(!VoiceQueuePolicy.silent(chunk.text))break;
                    playback.acknowledge(chunk.id);
                    if(playback.finished()){completePlayback();return;}
                }
                armTimeout(Math.min(left,180000L));
                if(!neural.speak(chunk.text,chunk.id)){cancel();toast(R.string.tts_unavailable);}
                return;
            }
            if(!selectedVoiceCurrent()){cancel();toast(R.string.tts_unavailable);return;}
            VoiceInteractionPolicy.Playback.Chunk chunk=playback.next();
            armTimeout(Math.min(left,VoiceQueuePolicy.timeout(chunk.text.length())));
            if(tts.speak(chunk.text,TextToSpeech.QUEUE_FLUSH,null,chunk.id)!=TextToSpeech.SUCCESS){
                cancel();toast(R.string.tts_unavailable);
            }
        }catch(RuntimeException e){cancel();toast(R.string.tts_unavailable);}
    }
    OnHandsVoice(Activity a){this(a,true);}
    /** Capture-only workspace use does not initialize a TTS engine. */
    OnHandsVoice(Activity a,boolean playbackEnabled) {
        this.a=a;
        voiceModels=new VoiceModelStore(new java.io.File(a.getNoBackupFilesDir(),"voice-models"));
        if(!playbackEnabled){initialized=true;return;}
        tts=new TextToSpeech(a.getApplicationContext(), status->handler.post(()->{
            if(closed)return;
            initialized=true;ready=status==TextToSpeech.SUCCESS;
            if(ready)tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){
                public void onStart(String id){a.runOnUiThread(()->{if(!closed&&playback!=null&&playback.current(id))startBarge();});}
                public void onDone(String id){a.runOnUiThread(()->finishSpeech(id,true));}
                public void onError(String id){a.runOnUiThread(()->finishSpeech(id,false));}
                public void onStop(String id,boolean interrupted){a.runOnUiThread(()->finishSpeech(id,false));}
            });
            String queued=pendingSpeech;pendingSpeech=null;
            if(queued!=null){if(ready)playSpeech(queued);else {cancel();toast(R.string.tts_unavailable);}}
        }));
    }
    boolean listening(){return recognizer!=null||whisper!=null;}
    boolean speaking(){return pendingSpeech!=null||playback!=null;}
    private void toast(int id){if(!closed)Toast.makeText(a,id,Toast.LENGTH_SHORT).show();}
    void listen(Result cb) {
        boolean loop=continuous, opt=bargeOptIn; cancel(); continuous=loop;bargeOptIn=opt;
        if(closed)return;
        if(a.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){cancel();toast(R.string.voice_permission);return;}
        claim();
        final int id=++generation;
        final VoiceInteractionPolicy.Capture turn=new VoiceInteractionPolicy.Capture();
        capture=turn;
        if(a.getPreferences(Activity.MODE_PRIVATE).getBoolean("use_whisper",true)&&voiceModels.ready(VoiceModelCatalog.WHISPER)){
            whisper=new WhisperCapture(a,voiceModels,targetLocale(),new WhisperCapture.Callback(){
                private boolean current(){return !closed&&id==generation&&capture==turn;}
                public void onReady(){if(current())turn.ready();}
                public void onProcessing(){if(current()){turn.end();armTimeout(150000L);}}
                public void onResult(String text){if(current())acceptTranscript(turn,text,cb);}
                public void onError(){if(current()&&turn.consumeTerminal()){cancel();toast(R.string.voice_error);}}
            });
            armTimeout(180000L);
            if(!whisper.start()){cancel();toast(R.string.voice_unavailable);}
            return;
        }
        if(Build.VERSION.SDK_INT<31){cancel();toast(R.string.voice_unavailable);return;}
        try {
            if(!SpeechRecognizer.isOnDeviceRecognitionAvailable(a)){cancel();toast(R.string.voice_unavailable);return;}
            recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(a);
            recognizer.setRecognitionListener(new RecognitionListener(){
                private boolean current(){return !closed&&id==generation&&capture==turn;}
                public void onReadyForSpeech(Bundle b){if(current())turn.ready();}
                public void onBeginningOfSpeech(){if(current())turn.ready();}
                public void onRmsChanged(float r){}
                public void onBufferReceived(byte[] b){}
                public void onEndOfSpeech(){if(current())turn.end();}
                public void onPartialResults(Bundle b){
                    if(!current()||turn.state()==VoiceInteractionPolicy.Capture.State.TERMINAL)return;
                    ArrayList<String> rows=b==null?null:b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if(rows!=null&&!rows.isEmpty())partialTranscript=VoiceInteractionPolicy.partial(rows.get(0));
                }
                public void onEvent(int type,Bundle b){}
                public void onError(int error){if(current()&&turn.consumeTerminal()){cancel();toast(error==SpeechRecognizer.ERROR_NO_MATCH||error==SpeechRecognizer.ERROR_SPEECH_TIMEOUT?R.string.voice_no_match:error==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS?R.string.voice_permission:R.string.voice_error);}}
                public void onResults(Bundle b){
                    if(!current())return;
                    ArrayList<String> rows=b==null?null:b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    acceptTranscript(turn,rows==null||rows.isEmpty()?null:rows.get(0),cb);
                }
            });
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE,targetLocale().toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
            armTimeout(45000);recognizer.startListening(intent);
        }catch(RuntimeException e){cancel();toast(R.string.voice_unavailable);}
    }
    private void acceptTranscript(VoiceInteractionPolicy.Capture turn,String text,Result cb){
        if(!turn.consumeTerminal())return;
        final String result;
        try{result=VoiceInteractionPolicy.finalTranscript(text,LocalChatTurns.MAX_PROMPT);}
        catch(IllegalArgumentException e){cancel();toast(R.string.voice_invalid_transcript);return;}
        boolean loop=continuous,opt=bargeOptIn;cancel();continuous=loop;bargeOptIn=opt;
        cb.accepted(result);
    }
    void speakThen(String text,Runnable next){startSpeech(text,next);}
    void speak(String text){startSpeech(text,null);}
    private void startSpeech(String text,Runnable next){
        if(closed)return;
        boolean loop=continuous,opt=bargeOptIn;cancel();continuous=loop;bargeOptIn=opt;
        claim();continuation.replace(next);
        playSpeech(text);
    }
    private void playSpeech(String text){
        if(closed)return;
        playbackComplete=false;
        if(!playbackAllowed()){cancel();return;}
        if(text==null||text.trim().isEmpty()){cancel();return;}
        if(text.length()>160000){cancel();toast(R.string.tts_unavailable);return;}
        Locale target=targetLocale();
        if(Build.VERSION.SDK_INT>=27&&a.getPreferences(Activity.MODE_PRIVATE).getBoolean("use_neural_tts",true)&&
                NeuralTtsPlayer.supports(target)&&voiceModels.ready(VoiceModelCatalog.TTS)){
            try{
                stopBarge();closeNeural();clearPlayback();clearTimeout();
                if(tts!=null)tts.stop();
                final VoiceInteractionPolicy.Playback session=new VoiceInteractionPolicy.Playback(text,"oh-"+generation+"-"+(++speechSequence)+"-",120);
                playback=session;
                neural=new NeuralTtsPlayer(a.getApplicationContext(),voiceModels,target,VoiceInteractionPolicy.RATES[rateIndex],new NeuralTtsPlayer.Callback(){
                    public void onStart(String id){if(!closed&&playback==session&&session.current(id))startBarge();}
                    public void onDone(String id){if(playback==session)finishSpeech(id,true);}
                    public void onError(String id){if(playback==session)finishSpeech(id,false);}
                });
                speechDeadline=android.os.SystemClock.elapsedRealtime()+Math.max(180000L,VoiceQueuePolicy.timeout(text.length()));
                playNextSpeechChunk();
            }catch(RuntimeException e){cancel();toast(R.string.tts_unavailable);}
            return;
        }
        if(!initialized){pendingSpeech=text;armTimeout(15000);return;}
        if(!ready||tts==null){cancel();toast(R.string.tts_unavailable);return;}
        Voice selected=null;
        try {
            int bestRank=-1;
            for(Voice v:tts.getVoices()){
                if(!offlineVoice(target,v))continue;
                int rank=VoiceInteractionPolicy.localeRank(target,v.getLocale());
                if(VoiceInteractionPolicy.betterVoice(rank,v.getQuality(),v.getLatency(),v.getName(),
                    bestRank,selected==null?0:selected.getQuality(),selected==null?0:selected.getLatency(),selected==null?null:selected.getName())){
                    selected=v;bestRank=rank;
                }
            }
            if(selected==null||tts.setVoice(selected)!=TextToSpeech.SUCCESS||tts.setSpeechRate(VoiceInteractionPolicy.RATES[rateIndex])!=TextToSpeech.SUCCESS){cancel();toast(R.string.tts_unavailable);return;}
            int max=Math.min(2000,TextToSpeech.getMaxSpeechInputLength());
            if(max<2||text.length()>160000){cancel();toast(R.string.tts_unavailable);return;}
            stopBarge();clearPlayback();clearTimeout();tts.stop();
            playback=new VoiceInteractionPolicy.Playback(text,"oh-"+generation+"-"+(++speechSequence)+"-",max);
            playbackVoiceName=selected.getName();playbackVoiceLocale=target;
            speechDeadline=android.os.SystemClock.elapsedRealtime()+VoiceQueuePolicy.timeout(text.length());
            playNextSpeechChunk();
            // Start monitoring only when the current utterance actually begins playback.
        }catch(RuntimeException e){cancel();toast(R.string.tts_unavailable);}
    }
    void cancel(){
        playbackComplete=false;
        generation++;
        if(owner.get()==this)owner.clear();
        WhisperCapture oldWhisper=whisper;whisper=null;if(oldWhisper!=null)oldWhisper.cancel();
        closeNeural();
        stopBarge();clearTimeout();clearResume();pendingSpeech=null;continuous=false;bargeOptIn=false;continuation.clear();clearPlayback();
        partialTranscript="";reviewPending=false;
        if(capture!=null)capture.cancel();capture=null;
        SpeechRecognizer old=recognizer;recognizer=null;
        if(old!=null){try{old.cancel();}catch(RuntimeException ignored){}try{old.destroy();}catch(RuntimeException ignored){}}
        if(tts!=null)try{tts.stop();}catch(RuntimeException ignored){}
    }
    void close(){closed=true;cancel();if(tts!=null){try{tts.shutdown();}catch(RuntimeException ignored){}tts=null;}}
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
