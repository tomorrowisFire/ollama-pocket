package com.ollamapocket.app;

import android.os.Looper;
import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.GenerationConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig;
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Typed local inference adapter for the pinned Sherpa-ONNX Android API.
 * Compile-only until trusted model manifests, native/license evidence and foreground routing are accepted.
 * A caller must hold exclusive app-private model ownership for the entire request. */
final class SherpaNeuralVoice {
    private SherpaNeuralVoice() {}
    private static void workerOnly() {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Neural speech requires a worker");
    }
    static String transcribe(VerifiedSpeechFiles pack, short[] pcm, int count, Locale locale,
                             BooleanSupplier cancelled) throws IOException {
        workerOnly();
        String language=NeuralVoicePolicy.whisperLanguage(locale);
        try (NeuralVoicePolicy.Lease lease=NeuralVoicePolicy.acquire(cancelled)) {
            pack.recheck(cancelled);
            OfflineWhisperModelConfig whisper=new OfflineWhisperModelConfig();
            whisper.setEncoder(pack.path("tiny-encoder.int8.onnx"));
            whisper.setDecoder(pack.path("tiny-decoder.int8.onnx"));
            whisper.setLanguage(language);
            whisper.setTask("transcribe");
            OfflineRecognizerConfig config=new OfflineRecognizerConfig();
            config.getFeatConfig().setSampleRate(NeuralVoicePolicy.SAMPLE_RATE);
            config.getFeatConfig().setFeatureDim(80);
            config.getModelConfig().setWhisper(whisper);
            config.getModelConfig().setTokens(pack.path("tiny-tokens.txt"));
            config.getModelConfig().setModelType("whisper");
            config.getModelConfig().setProvider("cpu");
            config.getModelConfig().setDebug(false);
            config.getModelConfig().setNumThreads(NeuralVoicePolicy.threads(Runtime.getRuntime().availableProcessors()));
            float[] samples=NeuralVoicePolicy.capture(pcm,count,cancelled);
            try {
                NeuralVoicePolicy.checkCancelled(cancelled);
                OfflineRecognizer recognizer=new OfflineRecognizer(null,config);
                try {
                    NeuralVoicePolicy.checkCancelled(cancelled);
                    OfflineStream stream=recognizer.createStream();
                    try {
                        stream.acceptWaveform(samples,NeuralVoicePolicy.SAMPLE_RATE);
                        NeuralVoicePolicy.checkCancelled(cancelled);
                        // Native decode is blocking. Discard cancelled results; do not release a live pointer.
                        recognizer.decode(stream);
                        NeuralVoicePolicy.checkCancelled(cancelled);
                        OfflineRecognizerResult result=recognizer.getResult(stream);
                        NeuralVoicePolicy.checkCancelled(cancelled);
                        return VoiceInteractionPolicy.finalTranscript(result.getText(),LocalChatTurns.MAX_PROMPT);
                    } finally { stream.release(); }
                } finally { recognizer.release(); }
            } finally { Arrays.fill(samples,0f); }
        }
    }
    static final class Pcm {
        final int sampleRate;
        final short[] samples;
        Pcm(int sampleRate,short[] samples){this.sampleRate=sampleRate;this.samples=samples;}
    }
    static Pcm synthesize(VerifiedSpeechFiles pack,String text,Locale locale,int speaker,float speed,
                          BooleanSupplier cancelled) throws IOException {
        workerOnly();
        String language=NeuralVoicePolicy.supertonicLanguage(locale);
        NeuralVoicePolicy.speechText(text);
        NeuralVoicePolicy.speed(speed);
        if(speaker<0||speaker>=10)throw new IllegalArgumentException("Speaker index");
        try(NeuralVoicePolicy.Lease lease=NeuralVoicePolicy.acquire(cancelled)){
            pack.recheck(cancelled);
            OfflineTtsSupertonicModelConfig model=new OfflineTtsSupertonicModelConfig();
            model.setDurationPredictor(pack.path("duration_predictor.int8.onnx"));
            model.setTextEncoder(pack.path("text_encoder.int8.onnx"));
            model.setVectorEstimator(pack.path("vector_estimator.int8.onnx"));
            model.setVocoder(pack.path("vocoder.int8.onnx"));
            model.setTtsJson(pack.path("tts.json"));
            model.setUnicodeIndexer(pack.path("unicode_indexer.bin"));
            model.setVoiceStyle(pack.path("voice.bin"));
            OfflineTtsConfig config=new OfflineTtsConfig();
            config.getModel().setSupertonic(model);
            config.getModel().setProvider("cpu");
            config.getModel().setDebug(false);
            config.getModel().setNumThreads(NeuralVoicePolicy.threads(Runtime.getRuntime().availableProcessors()));
            config.setMaxNumSentences(1);
            NeuralVoicePolicy.checkCancelled(cancelled);
            OfflineTts tts=new OfflineTts(null,config);
            try{
                NeuralVoicePolicy.checkCancelled(cancelled);
                int rate=tts.sampleRate();
                int limit=NeuralVoicePolicy.outputLimit(rate);
                if(speaker>=tts.numSpeakers())throw new IllegalArgumentException("Speaker unavailable");
                GenerationConfig generation=new GenerationConfig();
                generation.setSid(speaker);
                generation.setSpeed(speed);
                generation.setNumSteps(8);
                generation.setExtra(Collections.singletonMap("lang",language));
                AtomicLong emitted=new AtomicLong();
                AtomicBoolean stopped=new AtomicBoolean();
                AtomicReference<RuntimeException> failure=new AtomicReference<>();
                GeneratedAudio audio=tts.generateWithConfigAndCallback(text,generation,chunk->{
                    if(stopped.get())return 0;
                    try{
                        NeuralVoicePolicy.checkCancelled(cancelled);
                        if(chunk==null||emitted.addAndGet(chunk.length)>limit)
                            throw new IllegalArgumentException("Native speech output limit");
                        return 1;
                    }catch(RuntimeException ex){failure.compareAndSet(null,ex);stopped.set(true);return 0;}
                });
                if(failure.get()!=null)throw failure.get();
                NeuralVoicePolicy.checkCancelled(cancelled);
                if(audio==null||audio.getSampleRate()!=rate)throw new IllegalArgumentException("Invalid native audio rate");
                float[] samples=audio.getSamples();
                try{return new Pcm(rate,NeuralVoicePolicy.output(samples,rate,cancelled));}
                finally{if(samples!=null)Arrays.fill(samples,0f);}
            }finally{tts.release();}
        }
    }
}
