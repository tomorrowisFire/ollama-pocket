package com.ollamapocket.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

/** A foreground, one-shot, offline microphone for one attached workspace field.
 *  Only a separately confirmed append can change the original draft. */
final class WorkspaceDictation {
    private final Activity owner;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Capture active;
    WorkspaceDictation(Activity owner){this.owner=owner;}
    private int dp(int n){return Math.round(n*owner.getResources().getDisplayMetrics().density);}
    private void toast(int id){if(!owner.isFinishing()&&!owner.isDestroyed())Toast.makeText(owner,id,Toast.LENGTH_SHORT).show();}
    private boolean valid(EditText target,BooleanSupplier guard){
        try{return !owner.isFinishing()&&!owner.isDestroyed()&&target.isAttachedToWindow()&&target.isEnabled()&&guard.getAsBoolean();}
        catch(RuntimeException e){return false;}
    }
    Button add(LinearLayout parent,EditText target,int fieldLabel,int limit,boolean multiline,BooleanSupplier guard){
        Button b=new Button(owner);b.setAllCaps(false);b.setMinHeight(dp(48));
        b.setText(owner.getString(R.string.workspace_voice_field,owner.getString(fieldLabel)));
        parent.addView(b,new LinearLayout.LayoutParams(-1,-2));
        b.setOnClickListener(v->{
            if(!valid(target,guard)||!target.hasWindowFocus()){toast(R.string.busy);return;}
            if(owner.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
                toast(R.string.workspace_voice_permission);return;
            }
            cancel();
            Capture next=new Capture(target,fieldLabel,limit,multiline,guard);
            active=next;next.show();
        });
        return b;
    }
    void cancel(){Capture current=active;if(current!=null)current.dispose();}
    private final class Capture {
        final EditText target;
        final String original;
        final int fieldLabel,limit;
        final boolean multiline;
        final BooleanSupplier guard;
        final Runnable tick=this::poll;
        AlertDialog dialog;
        Spinner language;
        Button start;
        TextView state,transcript;
        OnHandsVoice speech;
        WorkspaceDictationPolicy.Review review;
        boolean closed;
        final View.OnAttachStateChangeListener detach=new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){}
            public void onViewDetachedFromWindow(View v){dispose();}
        };
        Capture(EditText target,int fieldLabel,int limit,boolean multiline,BooleanSupplier guard){
            this.target=target;this.original=target.getText().toString();this.fieldLabel=fieldLabel;
            this.limit=limit;this.multiline=multiline;this.guard=guard;
        }
        boolean alive(){return !closed&&active==this&&valid(target,guard)&&original.equals(target.getText().toString());}
        boolean visible(){return alive()&&dialog!=null&&dialog.isShowing()&&dialog.getWindow()!=null&&
            dialog.getWindow().getDecorView().hasWindowFocus();}
        void show(){
            LinearLayout body=new LinearLayout(owner);body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(16),dp(12),dp(16),dp(12));
            TextView note=new TextView(owner);
            note.setText(owner.getString(R.string.workspace_voice_notice,owner.getString(fieldLabel)));
            body.addView(note,new LinearLayout.LayoutParams(-1,-2));
            TextView caption=new TextView(owner);caption.setText(R.string.voice_language);body.addView(caption);
            language=new Spinner(owner);language.setMinimumHeight(dp(48));
            String[] labels={owner.getString(R.string.voice_app_language),"한국어","English","简体中文","繁體中文","日本語"};
            ArrayAdapter<String> adapter=new ArrayAdapter<>(owner,android.R.layout.simple_spinner_item,labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);language.setAdapter(adapter);
            String saved=VoiceInteractionPolicy.language(owner.getPreferences(Context.MODE_PRIVATE).getString("voice_language","app"));
            language.setSelection(Math.max(0,Arrays.asList(VoiceInteractionPolicy.LANGUAGES).indexOf(saved)));
            language.setContentDescription(owner.getString(R.string.voice_language));body.addView(language);
            start=new Button(owner);start.setText(R.string.workspace_voice_start);start.setAllCaps(false);start.setMinHeight(dp(48));body.addView(start);
            state=new TextView(owner);state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(state);
            transcript=new TextView(owner);transcript.setTextSize(14);transcript.setSaveEnabled(false);
            transcript.setAutoLinkMask(0);transcript.setTextIsSelectable(false);
            transcript.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(transcript);
            ScrollView scroll=new ScrollView(owner);scroll.setSaveEnabled(false);scroll.addView(body);
            dialog=new AlertDialog.Builder(owner).setTitle(R.string.workspace_voice_title).setView(scroll)
                .setNegativeButton(R.string.cancel,null).setNeutralButton(R.string.voice_finish_speaking,null)
                .setPositiveButton(R.string.workspace_voice_append,null).create();
            dialog.setOnDismissListener(d->dispose());
            target.addOnAttachStateChangeListener(detach);
            try{
                dialog.show();
                if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                WorkbenchWindow.fit(owner,dialog);
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setEnabled(false);
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->apply());
                dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v->{
                    if(visible()&&speech!=null&&speech.canFinishListening())speech.finishListening();
                });
                start.setOnClickListener(v->begin());
            }catch(RuntimeException e){dispose();toast(R.string.voice_error);}
        }
        void begin(){
            if(!visible()){dispose();return;}
            if(speech!=null)return;
            if(owner.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
                toast(R.string.workspace_voice_permission);dispose();return;
            }
            int index=language.getSelectedItemPosition();
            if(index<0||index>=VoiceInteractionPolicy.LANGUAGES.length)return;
            review=null;transcript.setText("");state.setText(R.string.voice_preparing);
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
            language.setEnabled(false);start.setEnabled(false);
            try{
                speech=new OnHandsVoice(owner,false);
                speech.configure(VoiceInteractionPolicy.LANGUAGES[index],1);
                speech.listen(this::recognized);
                handler.removeCallbacks(tick);handler.post(tick);
            }catch(RuntimeException e){releaseSpeech();retry(R.string.voice_error);}
        }
        void poll(){
            if(speech==null)return;
            if(!visible()){dispose();return;}
            if(!speech.listening()){releaseSpeech();retry(R.string.voice_error);return;}
            state.setText(speech.processingSpeech()?R.string.voice_recognizing:
                speech.speechReady()?R.string.workspace_voice_listening:R.string.voice_preparing);
            String partial=speech.partialTranscript();
            transcript.setText(partial.isEmpty()?"":owner.getString(R.string.voice_partial,partial));
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setEnabled(speech.canFinishListening());
            handler.postDelayed(tick,200);
        }
        void retry(int message){
            if(!alive()){dispose();return;}
            review=null;transcript.setText("");state.setText(message);
            language.setEnabled(true);start.setEnabled(true);
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setEnabled(false);
        }
        void recognized(String value){
            if(!visible()){dispose();return;}
            releaseSpeech();
            try{review=WorkspaceDictationPolicy.review(original,value,limit,multiline);}
            catch(IllegalArgumentException e){retry(R.string.workspace_voice_limit);return;}
            transcript.setText(review.spoken);
            state.setText(owner.getString(R.string.workspace_voice_review,review.merged.length(),limit));
            start.setEnabled(true);language.setEnabled(true);
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setEnabled(false);
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
        }
        void apply(){
            if(!visible()||review==null||speech!=null||!review.matches(target.getText().toString())){
                toast(R.string.workspace_voice_changed);dispose();return;
            }
            try{
                String expected=review.apply(target.getText().toString());
                target.getText().append(review.suffix);
                if(!expected.equals(target.getText().toString())){
                    target.setText(original);toast(R.string.workspace_voice_limit);
                }else target.setSelection(target.length());
            }catch(RuntimeException e){target.setText(original);toast(R.string.workspace_voice_limit);}
            dispose();
        }
        void releaseSpeech(){
            handler.removeCallbacks(tick);OnHandsVoice old=speech;speech=null;
            if(old!=null)old.close();
        }
        void dispose(){
            if(closed)return;closed=true;
            if(active==this)active=null;
            releaseSpeech();review=null;target.removeOnAttachStateChangeListener(detach);
            if(transcript!=null)transcript.setText("");
            if(dialog!=null&&dialog.isShowing())dialog.dismiss();
        }
    }
}
