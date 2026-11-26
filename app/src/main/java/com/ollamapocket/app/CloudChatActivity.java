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
        TextView title=new TextView(this);title.setText(R.string.cloud);title.setTextSize(24);form.addView(title);
        TextView note=new TextView(this);note.setText(R.string.cloud_desc);form.addView(note);
        provider=new Spinner(this);
        CloudApi.Provider[] values=CloudApi.Provider.hosted();
        String[] names=new String[values.length];for(int i=0;i<names.length;i++)names[i]=values[i].title;
        provider.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));form.addView(provider);
        model=edit(R.string.cloud_model,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,200);form.addView(model);
        key=edit(R.string.cloud_key,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,4096);form.addView(key);
        key.setSaveEnabled(false);key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        prompt=edit(R.string.cloud_prompt,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,16000);prompt.setMinLines(3);form.addView(prompt);
        TextView single=new TextView(this);single.setText(R.string.cloud_single);form.addView(single);
        send=new Button(this);send.setText(R.string.send);form.addView(send);
        stop=new Button(this);stop.setText(R.string.stop);stop.setEnabled(false);form.addView(stop);
        answer=new TextView(this);answer.setTextIsSelectable(true);answer.setTextSize(16);form.addView(answer);
        Button close=new Button(this);close.setText(R.string.close);form.addView(close);
        close.setOnClickListener(v->finish());stop.setOnClickListener(v->cancel());
        send.setOnClickListener(v->{
            final CloudApi.Provider p=values[provider.getSelectedItemPosition()];
            String id=model.getText().toString().trim(), token=key.getText().toString().trim(), q=prompt.getText().toString().trim();
            if(id.isEmpty()||token.isEmpty()||q.isEmpty()){Toast.makeText(this,R.string.cloud_fields,Toast.LENGTH_SHORT).show();return;}
            new AlertDialog.Builder(this).setTitle(p.title).setMessage(R.string.cloud_confirm)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.send,(d,w)->request(p,id,token,q)).show();
        });
    }
    private EditText edit(int hint,int type,int limit){
        EditText field=new EditText(this);field.setHint(hint);field.setInputType(type);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(limit)});
        field.setSaveEnabled(false);return field;
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
