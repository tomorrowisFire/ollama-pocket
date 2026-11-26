package com.ollamapocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Explicit foreground review and read-only, non-network-voice playback. */
final class WorkspaceReadAloud {
    private final Activity owner;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Reading active;
    WorkspaceReadAloud(Activity owner){this.owner=owner;}
    private int dp(int value){return Math.round(value*owner.getResources().getDisplayMetrics().density);}
    private void toast(int id){if(!owner.isFinishing()&&!owner.isDestroyed())Toast.makeText(owner,id,Toast.LENGTH_SHORT).show();}
    private boolean allowed(View anchor,BooleanSupplier guard){
        try{return !owner.isFinishing()&&!owner.isDestroyed()&&anchor.isAttachedToWindow()&&anchor.isEnabled()&&guard.getAsBoolean();}
        catch(RuntimeException e){return false;}
    }
    private Button button(LinearLayout panel,int label){
        Button b=new Button(owner);b.setText(label);WorkbenchStyle.button(owner,b,label);
        panel.addView(b,WorkbenchStyle.row(owner));return b;
    }
    Button add(LinearLayout panel,int subject,Supplier<String> source,BooleanSupplier guard){
        Button b=button(panel,R.string.workspace_read_open);
        b.setText(owner.getString(R.string.workspace_read_field,owner.getString(subject)));
        b.setOnClickListener(v->{
            if(!allowed(b,guard)||!b.hasWindowFocus()){toast(R.string.busy);return;}
            final String text;
            try{text=source.get();WorkspaceReadPolicy.review(text,"app",1);}
            catch(RuntimeException e){toast(R.string.workspace_read_invalid);return;}
            cancel();
            Reading next=new Reading(b,subject,source,text,guard);active=next;next.show();
        });
        return b;
    }
    void cancel(){Reading r=active;if(r!=null)r.dispose();}
    private final class Reading {
        final View anchor;
        final int subject;
        final Supplier<String> source;
        final String text;
        final BooleanSupplier guard;
        final Runnable tick=this::poll;
        AlertDialog dialog;
        Spinner language,rate;
        TextView status,preview;
        Button start,pause,stop;
        ProgressBar progress;
        OnHandsVoice speech;
        boolean closed;
        final View.OnAttachStateChangeListener detach=new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){}
            public void onViewDetachedFromWindow(View v){dispose();}
        };
        Reading(View anchor,int subject,Supplier<String> source,String text,BooleanSupplier guard){
            this.anchor=anchor;this.subject=subject;this.source=source;this.text=text;this.guard=guard;
        }
        boolean visible(){
            try{return !closed&&active==this&&allowed(anchor,guard)&&text.equals(source.get())&&
                dialog!=null&&dialog.isShowing()&&dialog.getWindow()!=null&&dialog.getWindow().getDecorView().hasWindowFocus();}
            catch(RuntimeException e){return false;}
        }
        private Spinner options(LinearLayout panel,int caption,String[] rows,int selected){
            TextView label=new TextView(owner);label.setText(caption);WorkbenchStyle.label(owner,label,13,false,true);panel.addView(label);
            Spinner pick=new Spinner(owner);
            ArrayAdapter<String> adapter=new ArrayAdapter<>(owner,android.R.layout.simple_spinner_item,rows);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            pick.setAdapter(adapter);pick.setSelection(selected);pick.setMinimumHeight(dp(48));
            pick.setContentDescription(owner.getString(caption));panel.addView(pick);return pick;
        }
        void show(){
            LinearLayout panel=new LinearLayout(owner);panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(16),dp(12),dp(16),dp(12));
            TextView note=new TextView(owner);
            note.setText(owner.getString(R.string.workspace_read_notice,owner.getString(subject),text.length()));
            WorkbenchStyle.label(owner,note,14,false,false);panel.addView(note,WorkbenchStyle.row(owner));
            String saved=VoiceInteractionPolicy.language(owner.getPreferences(Context.MODE_PRIVATE).getString("voice_language","app"));
            language=options(panel,R.string.voice_language,new String[]{owner.getString(R.string.voice_app_language),"한국어","English","简体中文","繁體中文","日本語"},
                Math.max(0,Arrays.asList(VoiceInteractionPolicy.LANGUAGES).indexOf(saved)));
            rate=options(panel,R.string.voice_rate,new String[]{"0.8×","1.0×","1.2×","1.4×"},
                VoiceInteractionPolicy.rateIndex(owner.getPreferences(Context.MODE_PRIVATE).getInt("voice_rate",1)));
            start=button(panel,R.string.workspace_read_start);
            pause=button(panel,R.string.voice_pause_playback);pause.setEnabled(false);
            stop=button(panel,R.string.stop);stop.setEnabled(false);
            status=new TextView(owner);WorkbenchStyle.label(owner,status,14,false,false);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            status.setText(R.string.workspace_read_ready);panel.addView(status);
            progress=new ProgressBar(owner,null,android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100);progress.setContentDescription(owner.getString(R.string.workspace_read_progress));
            panel.addView(progress,new LinearLayout.LayoutParams(-1,dp(12)));
            preview=new TextView(owner);preview.setText(text);WorkbenchStyle.label(owner,preview,14,false,false);preview.setAutoLinkMask(0);
            preview.setTextIsSelectable(false);preview.setSaveEnabled(false);panel.addView(preview);
            ScrollView scroll=new ScrollView(owner);scroll.setSaveEnabled(false);scroll.addView(panel);
            dialog=new AlertDialog.Builder(owner).setTitle(R.string.workspace_read_open).setView(scroll)
                .setNegativeButton(R.string.close,null).create();
            dialog.setOnDismissListener(d->dispose());anchor.addOnAttachStateChangeListener(detach);
            try{
                if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                dialog.show();
                if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                WorkbenchWindow.fit(owner,dialog);
                start.setOnClickListener(v->begin());
                pause.setOnClickListener(v->{if(visible()&&speech!=null&&speech.canPausePlayback()){
                    speech.togglePlaybackPause();poll();
                }});
                stop.setOnClickListener(v->{release();idle(R.string.workspace_read_stopped,false);});
            }catch(RuntimeException e){dispose();toast(R.string.tts_unavailable);}
        }
        void begin(){
            if(!visible()){dispose();return;}
            if(speech!=null)return;
            int i=language.getSelectedItemPosition(),r=rate.getSelectedItemPosition();
            if(i<0||i>=VoiceInteractionPolicy.LANGUAGES.length)return;
            final WorkspaceReadPolicy.Review review;
            try{review=WorkspaceReadPolicy.review(text,VoiceInteractionPolicy.LANGUAGES[i],r);}
            catch(IllegalArgumentException e){toast(R.string.workspace_read_invalid);return;}
            start.setEnabled(false);pause.setEnabled(false);stop.setEnabled(true);language.setEnabled(false);rate.setEnabled(false);
            progress.setProgress(0);status.setText(R.string.workspace_read_preparing);
            try{
                speech=new OnHandsVoice(owner);
                speech.configure(review.language,review.rate);
                speech.setPlaybackGuard(()->visible()&&review.matches(source.get()));
                speech.speak(review.text);
                handler.removeCallbacks(tick);handler.post(tick);
            }catch(RuntimeException e){release();idle(R.string.tts_unavailable,false);}
        }
        void poll(){
            handler.removeCallbacks(tick);
            if(speech==null)return;
            if(!visible()){dispose();return;}
            if(!speech.speaking()){
                boolean completed=speech.playbackCompleted();
                release();idle(completed?R.string.workspace_read_complete:R.string.tts_unavailable,completed);return;
            }
            if(speech.canPausePlayback()){
                int percent=speech.playbackPercent();
                progress.setProgress(percent);
                String value=owner.getString(speech.playbackPaused()?R.string.voice_playback_paused:R.string.workspace_read_playing)+
                    " · "+owner.getString(R.string.voice_playback_percent,Integer.toString(percent));
                if(!value.contentEquals(status.getText()))status.setText(value);
                pause.setEnabled(true);pause.setText(speech.playbackPaused()?R.string.voice_resume_playback:R.string.voice_pause_playback);
            }
            handler.postDelayed(tick,250);
        }
        void idle(int message,boolean complete){
            if(closed)return;
            status.setText(message);
            if(complete)progress.setProgress(100);
            start.setText(R.string.workspace_read_restart);start.setEnabled(true);
            pause.setEnabled(false);pause.setText(R.string.voice_pause_playback);stop.setEnabled(false);
            language.setEnabled(true);rate.setEnabled(true);
        }
        void release(){
            handler.removeCallbacks(tick);OnHandsVoice previous=speech;speech=null;
            if(previous!=null)previous.close();
        }
        void dispose(){
            if(closed)return;closed=true;if(active==this)active=null;
            release();anchor.removeOnAttachStateChangeListener(detach);
            if(preview!=null)preview.setText("");
            if(dialog!=null&&dialog.isShowing())dialog.dismiss();
        }
    }
}
