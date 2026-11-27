package com.ollamapocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit foreground management of optional voice data. Opening this screen never downloads. */
final class VoiceModelsUi {
    private static final Map<Activity,Session> OPEN=new WeakHashMap<>();
    static void show(Activity owner){
        if(owner.isFinishing()||owner.isDestroyed())return;
        cancel(owner);Session session=new Session(owner);OPEN.put(owner,session);session.show();
    }
    static void cancel(Activity owner){Session current=OPEN.remove(owner);if(current!=null)current.close();}
    private VoiceModelsUi(){}
    private static final class Row {
        final VoiceModelCatalog.Model model;
        final TextView state;
        final Button install,remove,license;
        final CheckBox enabled;
        Row(VoiceModelCatalog.Model model,TextView state,Button install,Button remove,Button license,CheckBox enabled){
            this.model=model;this.state=state;this.install=install;this.remove=remove;this.license=license;this.enabled=enabled;
        }
    }
    private static final class Session {
        final Activity owner;
        final VoiceModelStore store;
        final Handler main=new Handler(Looper.getMainLooper());
        final ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"voice-model-io");t.setDaemon(true);return t;});
        final List<Row> rows=new ArrayList<>();
        AlertDialog dialog,child;
        TextView status;
        ProgressBar progress;
        Button stop;
        AtomicBoolean cancelled;
        boolean closed,busy,downloading;
        volatile long lastProgress;
        Session(Activity owner){this.owner=owner;store=new VoiceModelStore(new File(owner.getNoBackupFilesDir(),"voice-models"));}
        int dp(int value){return Math.round(value*owner.getResources().getDisplayMetrics().density);}
        boolean alive(){return !closed&&!owner.isFinishing()&&!owner.isDestroyed()&&OPEN.get(owner)==this;}
        LinearLayout panel(){LinearLayout panel=new LinearLayout(owner);panel.setOrientation(LinearLayout.VERTICAL);return panel;}
        TextView text(LinearLayout parent,int label){TextView v=new TextView(owner);v.setText(label);WorkbenchStyle.label(owner,v,14,false,false);parent.addView(v,WorkbenchStyle.row(owner));return v;}
        Button button(LinearLayout parent,int label){Button b=new Button(owner);b.setText(label);WorkbenchStyle.button(owner,b,label);parent.addView(b,WorkbenchStyle.row(owner));return b;}
        void show(){
            LinearLayout body=panel();body.setPadding(dp(16),dp(12),dp(16),dp(12));
            text(body,R.string.voice_models_notice);text(body,R.string.voice_models_chinese);
            for(VoiceModelCatalog.Model model:VoiceModelCatalog.ALL){
                LinearLayout section=panel();WorkbenchStyle.card(owner,section,false);body.addView(section,WorkbenchStyle.row(owner));
                TextView name=new TextView(owner);name.setText(model.name);WorkbenchStyle.label(owner,name,16,false,true);
                OnHandGlyphs.accentLabel(name,OnHandGlyphs.Kind.MIC);section.addView(name,WorkbenchStyle.row(owner));
                TextView details=new TextView(owner);details.setText(owner.getString(R.string.voice_model_details,
                    size(model.archiveBytes),size(model.expandedBytes),model.languages,model.license));
                if(model!=VoiceModelCatalog.WHISPER)details.append("\n"+owner.getString(R.string.voice_model_android_requirement));
                WorkbenchStyle.label(owner,details,13,false,false);section.addView(details,WorkbenchStyle.row(owner));
                TextView state=text(section,R.string.voice_model_not_installed);
                CheckBox enabled=new CheckBox(owner);enabled.setText(model==VoiceModelCatalog.WHISPER?R.string.voice_models_use_whisper:
                    model==VoiceModelCatalog.MELO?R.string.voice_models_use_melo:R.string.voice_models_use_tts);
                String key=model==VoiceModelCatalog.WHISPER?"use_whisper":model==VoiceModelCatalog.MELO?"use_melo_tts":"use_neural_tts";
                enabled.setChecked(owner.getPreferences(Context.MODE_PRIVATE).getBoolean(key,model!=VoiceModelCatalog.MELO));
                enabled.setOnCheckedChangeListener((b,value)->{if(alive())owner.getPreferences(Context.MODE_PRIVATE).edit().putBoolean(key,value).apply();});
                enabled.setMinHeight(dp(48));section.addView(enabled,WorkbenchStyle.row(owner));
                AdaptiveAnswerActions actions=new AdaptiveAnswerActions(owner);
                Button install=button(actions,R.string.voice_model_install),remove=button(actions,R.string.voice_model_remove),license=button(actions,R.string.voice_model_license);
                section.addView(actions,WorkbenchStyle.row(owner));
                Row row=new Row(model,state,install,remove,license,enabled);rows.add(row);
                install.setOnClickListener(v->confirmInstall(model));remove.setOnClickListener(v->confirmRemove(model));license.setOnClickListener(v->license(model));
            }
            status=text(body,R.string.voice_models_idle);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            progress=new ProgressBar(owner,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setVisibility(View.GONE);body.addView(progress,WorkbenchStyle.row(owner));
            stop=button(body,R.string.voice_model_cancel_download);stop.setVisibility(View.GONE);stop.setOnClickListener(v->stopOperation());
            ScrollView scroll=new ScrollView(owner);scroll.addView(body);
            dialog=new AlertDialog.Builder(owner).setTitle(R.string.voice_models_title).setView(scroll).setNegativeButton(R.string.close,null).create();
            dialog.setOnDismissListener(d->close());
            try{dialog.show();WorkbenchWindow.fit(owner,dialog);refresh();}catch(RuntimeException e){close();}
        }
        String size(long bytes){return String.format(Locale.getDefault(),"%,d B (%.1f MiB)",bytes,bytes/(1024.0*1024.0));}
        void refresh(){
            if(!alive())return;
            for(Row row:rows){
                boolean installed=store.ready(row.model),present=store.present(row.model);
                boolean supported=row.model==VoiceModelCatalog.WHISPER||android.os.Build.VERSION.SDK_INT>=27;
                row.state.setText(installed?R.string.voice_model_installed:present?R.string.voice_model_reinstall:R.string.voice_model_not_installed);
                WorkbenchVisualPolicy.Palette palette=WorkbenchStyle.palette(owner);
                WorkbenchVisualPolicy.ModelStatus visual=installed?WorkbenchVisualPolicy.ModelStatus.INSTALLED:
                    WorkbenchVisualPolicy.ModelStatus.AVAILABLE;
                android.graphics.drawable.GradientDrawable badge=new android.graphics.drawable.GradientDrawable();
                badge.setColor(!installed&&present?palette.danger:palette.modelStatusFill(visual));badge.setCornerRadius(dp(10));
                row.state.setBackground(badge);row.state.setTextColor(!installed&&present?palette.dangerText:palette.modelStatusInk(visual));
                row.state.setPadding(dp(10),dp(6),dp(10),dp(6));
                row.install.setEnabled(!busy&&!present&&supported);row.remove.setEnabled(!busy&&present);row.license.setEnabled(!busy);row.enabled.setEnabled(!busy&&supported);
            }
            progress.setVisibility(busy?View.VISIBLE:View.GONE);stop.setVisibility(busy&&downloading?View.VISIBLE:View.GONE);stop.setEnabled(busy&&downloading);
        }
        void confirmInstall(VoiceModelCatalog.Model model){
            if(!alive()||busy||model!=VoiceModelCatalog.WHISPER&&android.os.Build.VERSION.SDK_INT<27)return;
            String message=owner.getString(R.string.voice_model_download_review,model.name,size(model.archiveBytes),size(model.requiredSpace()),model.license,model.url,model.licenseUrl);
            if(model==VoiceModelCatalog.MELO){
                // The download confirmation itself includes the pinned license, without a network request.
                try{message+="\n\n"+bundledLicense(model);}
                catch(Exception e){status.setText(R.string.voice_model_failure);return;}
            }
            child=new AlertDialog.Builder(owner).setTitle(R.string.voice_model_install)
                .setMessage(message)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.voice_model_download_agree,(d,w)->run(model,false)).create();
            child.show();WorkbenchWindow.fit(owner,child);
        }
        void confirmRemove(VoiceModelCatalog.Model model){
            if(!alive()||busy)return;
            child=new AlertDialog.Builder(owner).setTitle(R.string.voice_model_remove).setMessage(owner.getString(R.string.voice_model_remove_review,model.name))
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.voice_model_remove,(d,w)->run(model,true)).create();
            child.show();WorkbenchWindow.fitCompact(owner,child);
        }
        String bundledLicense(VoiceModelCatalog.Model model)throws java.io.IOException{
            String asset=model==VoiceModelCatalog.WHISPER?"licenses/WHISPER-MODEL-MIT.txt":
                model==VoiceModelCatalog.MELO?"licenses/MELOTTS-MODEL-MIT.txt":"licenses/SUPERTONIC-3-MODEL-LICENSE.txt";
            // Small bundled text only; no network request is made when reviewing the license.
            try(InputStream in=owner.getAssets().open(asset);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[2048];int n;
                while((n=in.read(buffer))!=-1){if(out.size()+n>32768)throw new java.io.IOException("License text limit");out.write(buffer,0,n);}
                byte[] data=out.toByteArray();
                if(model==VoiceModelCatalog.MELO&&!VoiceModelCatalog.matchesMeloLicense(data))throw new java.io.IOException("MeloTTS license integrity");
                return new String(data,StandardCharsets.UTF_8);
            }
        }
        void license(VoiceModelCatalog.Model model){
            if(!alive()||busy)return;
            String contents;
            try{contents=model.license+"\n"+model.licenseUrl+"\n\n"+bundledLicense(model);}
            catch(Exception e){
                if(model==VoiceModelCatalog.MELO){status.setText(R.string.voice_model_failure);return;}
                contents=model.license+"\n"+model.licenseUrl;
            }
            TextView text=new TextView(owner);text.setText(contents);text.setTextIsSelectable(true);text.setPadding(dp(16),dp(12),dp(16),dp(12));WorkbenchStyle.label(owner,text,13,false,false);
            ScrollView scroll=new ScrollView(owner);scroll.addView(text);
            child=new AlertDialog.Builder(owner).setTitle(R.string.voice_model_license).setView(scroll).setPositiveButton(R.string.close,null).create();child.show();WorkbenchWindow.fit(owner,child);
        }
        void run(VoiceModelCatalog.Model model,boolean remove){
            if(!alive()||busy)return;
            busy=true;downloading=!remove;cancelled=new AtomicBoolean();AtomicBoolean operation=cancelled;lastProgress=0;
            status.setText(remove?R.string.voice_model_removing:R.string.voice_model_downloading);progress.setProgress(0);progress.setIndeterminate(remove);refresh();
            io.execute(()->{
                int result=remove?R.string.voice_model_removed:R.string.voice_model_installed;
                try{
                    if(remove){if(operation.get())throw new VoiceModelStore.CancelledException();store.remove(model);}
                    else store.install(model,operation::get,(done,total)->{
                        long now=System.nanoTime();if(done<total&&now-lastProgress<150000000L)return;lastProgress=now;
                        main.post(()->{if(alive()&&busy&&cancelled==operation&&!operation.get()){
                            progress.setProgress((int)(done*100/total));
                            status.setText(done==total?owner.getString(R.string.voice_model_verifying):owner.getString(R.string.voice_model_progress,(int)(done*100/total)));
                        }});
                    });
                }catch(VoiceModelStore.BusyException e){result=R.string.voice_model_in_use;}
                catch(VoiceModelStore.CancelledException e){result=R.string.voice_model_cancelled;}
                catch(Exception e){result=operation.get()?R.string.voice_model_cancelled:R.string.voice_model_failure;}
                final int message=result;
                main.post(()->{if(alive()&&cancelled==operation){busy=false;cancelled=null;status.setText(message);refresh();}});
            });
        }
        void stopOperation(){
            if(!alive()||!busy||cancelled==null)return;
            cancelled.set(true);store.cancelPendingInstall();stop.setEnabled(false);status.setText(R.string.voice_model_cancelling);
        }
        void close(){
            if(closed)return;closed=true;
            if(OPEN.get(owner)==this)OPEN.remove(owner);
            if(cancelled!=null)cancelled.set(true);store.cancelPendingInstall();io.shutdownNow();main.removeCallbacksAndMessages(null);
            if(child!=null&&child.isShowing())child.dismiss();if(dialog!=null&&dialog.isShowing())dialog.dismiss();
        }
    }
}
