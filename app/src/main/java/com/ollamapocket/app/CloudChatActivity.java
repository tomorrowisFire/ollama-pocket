package com.ollamapocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

/** Separate opt-in single-turn cloud client. No automatic transfer from local chats. */
public final class CloudChatActivity extends Activity {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private volatile HttpsURLConnection connection;
    private volatile boolean closed;
    private volatile int serial;
    private Spinner provider;
    private EditText model,key,prompt;
    private TextView answer;
    private Button send,stop;
    @Override protected void attachBaseContext(Context base){super.attachBaseContext(OnHandsLocale.wrap(base));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(18*getResources().getDisplayMetrics().density);form.setPadding(pad,pad,pad,pad);
        scroll.addView(form);setContentView(scroll);
        TextView title=new TextView(this);title.setText(R.string.cloud);WorkbenchStyle.label(this,title,24,false,true);
        OnHandGlyphs.accentLabel(title,OnHandGlyphs.Kind.CLOUD);form.addView(title,WorkbenchStyle.row(this));
        TextView note=new TextView(this);note.setText(R.string.cloud_desc);WorkbenchStyle.label(this,note,13,true,false);form.addView(note,WorkbenchStyle.row(this));
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);WorkbenchStyle.card(this,fields,false);
        form.addView(fields,WorkbenchStyle.row(this));
        provider=new Spinner(this);
        CloudApi.Provider[] values=CloudApi.Provider.hosted();
        String[] names=new String[values.length];for(int i=0;i<names.length;i++)names[i]=values[i].title;
        provider.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
        OnHandGlyphs.spinner(provider);WorkbenchStyle.fieldLabel(this,fields,R.string.cloud_provider_model,provider);fields.addView(provider,WorkbenchStyle.row(this));
        model=edit(R.string.cloud_model,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,200);addLabeledField(fields,model,R.string.cloud_model);
        key=edit(R.string.cloud_key,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,4096);addLabeledField(fields,key,R.string.cloud_key);
        key.setSaveEnabled(false);key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        prompt=edit(R.string.cloud_prompt,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,16000);prompt.setMinLines(3);addLabeledField(fields,prompt,R.string.cloud_prompt);
        TextView single=new TextView(this);single.setText(R.string.cloud_single);WorkbenchStyle.label(this,single,12,true,false);form.addView(single,WorkbenchStyle.row(this));
        send=new Button(this);send.setText(R.string.send);WorkbenchStyle.button(this,send,R.string.send);form.addView(send,WorkbenchStyle.row(this));
        stop=new Button(this);stop.setText(R.string.stop);WorkbenchStyle.button(this,stop,R.string.stop);stop.setEnabled(false);form.addView(stop,WorkbenchStyle.row(this));
        answer=new TextView(this);answer.setTextIsSelectable(true);WorkbenchStyle.label(this,answer,16,false,false);form.addView(answer,WorkbenchStyle.row(this));
        Button close=new Button(this);close.setText(R.string.close);WorkbenchStyle.button(this,close,R.string.close);form.addView(close,WorkbenchStyle.row(this));
        close.setOnClickListener(v->finish());stop.setOnClickListener(v->cancel());
        send.setOnClickListener(v->{
            final CloudApi.Provider p=values[provider.getSelectedItemPosition()];
            String id=model.getText().toString().trim(), token=key.getText().toString().trim(), q=prompt.getText().toString().trim();
            if(id.isEmpty()||token.isEmpty()||q.isEmpty()){Toast.makeText(this,R.string.cloud_fields,Toast.LENGTH_SHORT).show();return;}
            AlertDialog confirmation=new AlertDialog.Builder(this).setTitle(p.title).setMessage(R.string.cloud_confirm)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.send,(d,w)->request(p,id,token,q)).show();
            WorkbenchWindow.fitCompact(this,confirmation);
        });
    }
    private void addLabeledField(LinearLayout parent,EditText field,int label){
        WorkbenchStyle.fieldLabel(this,parent,label,field);field.setHint(null);parent.addView(field,WorkbenchStyle.row(this));
    }
    private EditText edit(int hint,int type,int limit){
        EditText field=new EditText(this);field.setHint(hint);field.setInputType(type);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(limit)});
        field.setSaveEnabled(false);WorkbenchStyle.field(this,field);return field;
    }
    private void busy(boolean b){send.setEnabled(!b);stop.setEnabled(b);provider.setEnabled(!b);model.setEnabled(!b);key.setEnabled(!b);prompt.setEnabled(!b);}
    private void request(CloudApi.Provider p,String id,String token,String q){
        if(closed||!send.isEnabled())return;
        final int request=++serial;busy(true);answer.setText(R.string.thinking);
        executor.execute(()->{
            HttpsURLConnection c=null;String result=null;
            try{
                if(closed||request!=serial)return;
                c=CloudApi.connection(p,token);connection=c;
                if(closed||request!=serial)return;
                result=CloudApi.execute(p,c,id,q);
            }catch(Exception ignored){}finally{if(c!=null)c.disconnect();if(connection==c)connection=null;}
            final String output=result;
            runOnUiThread(()->{if(closed||request!=serial)return;busy(false);if(output==null)answer.setText(R.string.cloud_fail);else answer.setText(ChatPresentation.format("",output,"",0x33808080));});
        });
    }
    private void cancel(){
        serial++;HttpsURLConnection c=connection;if(c!=null)c.disconnect();
        busy(false);answer.setText(R.string.stopped);
    }
    @Override protected void onStop(){super.onStop();if(stop!=null&&stop.isEnabled())cancel();if(key!=null)key.setText("");}
    @Override protected void onDestroy(){
        closed=true;serial++;HttpsURLConnection c=connection;if(c!=null)c.disconnect();executor.shutdownNow();
        if(key!=null)key.setText("");super.onDestroy();
    }
}
