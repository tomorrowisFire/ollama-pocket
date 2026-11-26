package com.ollamapocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

/** Embedded cloud conversation panel; never reads local chat state or local model identifiers. */
final class CloudChatPanel extends LinearLayout {
    private final Activity owner;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final CloudHistory history=new CloudHistory();
    private final CloudKeyVault vault;
    private final TextView endpoint,status;
    private final LinearLayout transcript;
    private final ScrollView scroll;
    private final EditText prompt;
    private final Button send,stop,settings;
    private volatile HttpsURLConnection connection;
    private volatile int serial;
    private volatile boolean closed;
    private boolean active, busy;
    private CloudApi.Provider provider=CloudApi.Provider.OPENAI;
    private String model="",token="";
    CloudChatPanel(Activity activity,Runnable local){
        super(activity);owner=activity;vault=new CloudKeyVault(activity);
        setOrientation(VERTICAL);
        setPadding(dp(6),dp(4),dp(6),dp(6));
        LinearLayout header=new LinearLayout(owner);
        header.setOrientation(HORIZONTAL);
        Button back=button(R.string.cloud_return),configure=button(R.string.cloud_config);
        settings=configure;
        header.addView(back,new LayoutParams(0,dp(48),1f));
        header.addView(configure,new LayoutParams(0,dp(48),1f));
        addView(header,new LayoutParams(-1,-2));
        back.setOnClickListener(v->local.run());configure.setOnClickListener(v->configure());
        endpoint=new TextView(owner);endpoint.setTextSize(16);endpoint.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        addView(endpoint,new LayoutParams(-1,-2));
        TextView notice=new TextView(owner);notice.setText(R.string.cloud_context_notice);notice.setTextSize(11);notice.setPadding(0,dp(5),0,dp(5));
        addView(notice,new LayoutParams(-1,-2));
        scroll=new ScrollView(owner);scroll.setFillViewport(true);
        transcript=new LinearLayout(owner);transcript.setOrientation(VERTICAL);
        scroll.addView(transcript,new ScrollView.LayoutParams(-1,-2));
        addView(scroll,new LayoutParams(-1,0,1f));
        status=new TextView(owner);status.setTextSize(12);status.setPadding(0,dp(3),0,dp(3));
        addView(status,new LayoutParams(-1,-2));
        LinearLayout composer=new LinearLayout(owner);composer.setOrientation(HORIZONTAL);
        composer.setGravity(android.view.Gravity.CENTER_VERTICAL);
        prompt=input(R.string.message_hint,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,CloudHistory.MAX_PROMPT);
        prompt.setMinLines(1);prompt.setMaxLines(4);prompt.setSaveEnabled(false);
        composer.addView(prompt,new LayoutParams(0,-2,1f));
        send=button(R.string.send);stop=button(R.string.stop);
        composer.addView(send,new LayoutParams(-2,dp(48)));
        composer.addView(stop,new LayoutParams(-2,dp(48)));
        addView(composer,new LayoutParams(-1,-2));
        send.setOnClickListener(v->prepareSend());stop.setOnClickListener(v->cancel());
        setBusy(false);render();
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private Button button(int label){Button b=new Button(owner);b.setText(label);b.setAllCaps(false);b.setMinHeight(dp(48));return b;}
    private EditText input(int hint,int type,int limit){
        EditText field=new EditText(owner);field.setHint(hint);field.setInputType(type);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(limit)});
        field.setSaveEnabled(false);return field;
    }
    private void toast(int label){Toast.makeText(owner,label,Toast.LENGTH_SHORT).show();}
    boolean isBusy(){return busy;}
    void activate(){if(closed)return;active=true;owner.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);render();}
    void pause(){if(busy)cancel();token="";active=false;}
    void resume(){if(!closed)active=true;}
    void destroy(){pause();closed=true;history.clear();io.shutdownNow();}
    private CloudHistory.Session session(){return history.session(provider.name(),model);}
    private void render(){
        endpoint.setText(model.isEmpty()?getResources().getString(R.string.cloud_provider_model):provider.title+" · "+model);
        transcript.removeAllViews();
        if(!model.isEmpty()){
            try{for(CloudHistory.Turn t:session().turns()){bubble(t.question,true);bubble(t.answer,false);}}
            catch(IllegalStateException e){toast(R.string.cloud_session_limit);}
        }
        scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
    }
    private void bubble(String value,boolean user){
        TextView text=new TextView(owner);text.setTextSize(15);text.setTextIsSelectable(true);
        text.setPadding(dp(10),dp(10),dp(10),dp(10));text.setLineSpacing(dp(3),1f);
        text.setText(user?value:ChatPresentation.format(provider.title+"\n",value,"",0x33808080));
        if(user)text.setBackgroundColor(0x14808080);
        transcript.addView(text,new LayoutParams(-1,-2));
        if(!user){
            Button copy=button(R.string.copy);
            copy.setOnClickListener(v->{ClipboardManager cb=(ClipboardManager)owner.getSystemService(Context.CLIPBOARD_SERVICE);
                if(cb!=null){cb.setPrimaryClip(ClipData.newPlainText(provider.title,value));toast(R.string.copied);}});
            transcript.addView(copy,new LayoutParams(-2,dp(48)));
        }
    }
    void newChat(){
        if(busy||model.isEmpty())return;
        new AlertDialog.Builder(owner).setMessage(R.string.cloud_reset)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok,(d,w)->{
                if(!active||busy)return;session().clear();prompt.setText("");status.setText("");render();
            }).show();
    }
    private void configure(){
        if(busy||closed||!active)return;
        ScrollView sc=new ScrollView(owner);LinearLayout fields=new LinearLayout(owner);fields.setOrientation(VERTICAL);
        int pad=dp(18);fields.setPadding(pad,pad,pad,pad);sc.addView(fields);
        CloudApi.Provider[] values=CloudApi.Provider.values();
        String[] names=new String[values.length];for(int i=0;i<names.length;i++)names[i]=values[i].title;
        Spinner pick=new Spinner(owner);pick.setAdapter(new ArrayAdapter<>(owner,android.R.layout.simple_spinner_dropdown_item,names));
        pick.setSelection(provider.ordinal());fields.addView(pick);
        EditText chosenModel=input(R.string.cloud_model,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,200);
        chosenModel.setText(model);fields.addView(chosenModel);
        EditText key=input(R.string.cloud_key,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,4096);
        key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        key.setText(token);fields.addView(key);
        CheckBox remember=new CheckBox(owner);remember.setText(R.string.cloud_key_remember);fields.addView(remember);
        Button load=button(R.string.cloud_key_load),delete=button(R.string.cloud_key_delete);
        fields.addView(load);fields.addView(delete);
        final int[] last={provider.ordinal()};
        pick.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
            @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                if(pos!=last[0]){last[0]=pos;chosenModel.setText("");key.setText("");remember.setChecked(false);}
            }
        });
        load.setOnClickListener(v->{
            try{String saved=vault.load(values[pick.getSelectedItemPosition()]);
                if(saved.isEmpty())toast(R.string.cloud_key_missing);else key.setText(saved);
            }catch(Exception e){toast(R.string.cloud_key_error);}
        });
        delete.setOnClickListener(v->{
            final CloudApi.Provider selected=values[pick.getSelectedItemPosition()];
            new AlertDialog.Builder(owner).setMessage(R.string.cloud_key_clear_confirm)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok,(d,w)->{
                    try{vault.delete(selected);if(provider==selected)token="";
                        if(values[pick.getSelectedItemPosition()]==selected){key.setText("");remember.setChecked(false);}
                        toast(R.string.cloud_key_deleted);
                    }catch(Exception e){toast(R.string.cloud_key_error);}
                }).show();
        });
        AlertDialog dialog=new AlertDialog.Builder(owner).setTitle(R.string.cloud_config).setView(sc)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok,null).create();
        dialog.setOnDismissListener(d->key.setText(""));
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!active||closed||busy){dialog.dismiss();return;}
            CloudApi.Provider selected=values[pick.getSelectedItemPosition()];
            String id=chosenModel.getText().toString().trim(),apiKey=key.getText().toString().trim();
            if(id.isEmpty()||apiKey.isEmpty()||apiKey.indexOf('\r')>=0||apiKey.indexOf('\n')>=0){toast(R.string.cloud_fields);return;}
            try{history.session(selected.name(),id);}catch(IllegalStateException e){toast(R.string.cloud_session_limit);return;}
            catch(IllegalArgumentException e){toast(R.string.cloud_fields);return;}
            if(remember.isChecked())try{vault.save(selected,apiKey);}catch(Exception e){toast(R.string.cloud_key_error);return;}
            provider=selected;model=id;token=apiKey;status.setText("");dialog.dismiss();render();
        });
    }
    private void setBusy(boolean b){
        busy=b;send.setEnabled(!b);send.setVisibility(b?GONE:VISIBLE);
        stop.setEnabled(b);stop.setVisibility(b?VISIBLE:GONE);
        settings.setEnabled(!b);prompt.setEnabled(!b);
    }
    private void prepareSend(){
        if(busy||!active||closed)return;
        final String q=prompt.getText().toString().trim();if(q.isEmpty())return;
        if(model.isEmpty()||token.isEmpty()){toast(R.string.cloud_key_needed);configure();return;}
        final CloudApi.Provider p=provider;final String id=model;final CloudHistory.Session s=session();
        final List<CloudHistory.Message> messages=s.request(q);
        new AlertDialog.Builder(owner).setTitle(p.title)
            .setMessage(getResources().getString(R.string.cloud_send_context,p.title,messages.size()))
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.send,(d,w)->{
                if(!active||busy||closed||token.isEmpty()||p!=provider||!id.equals(model))return;
                request(p,id,token,q,s,messages);
            }).show();
    }
    private void request(CloudApi.Provider p,String id,String apiKey,String q,CloudHistory.Session s,List<CloudHistory.Message> messages){
        int request=++serial;setBusy(true);status.setText(R.string.thinking);
        io.execute(()->{
            HttpsURLConnection c=null;String result=null;
            try{
                if(closed||request!=serial)return;
                c=CloudApi.connection(p,apiKey);connection=c;
                if(closed||request!=serial)return;
                result=CloudApi.execute(p,c,id,messages);
            }catch(Exception ignored){}finally{if(c!=null)c.disconnect();if(connection==c)connection=null;}
            String output=result;
            owner.runOnUiThread(()->{
                if(closed||request!=serial||!active)return;
                setBusy(false);
                if(output==null){status.setText(R.string.cloud_fail);return;}
                s.complete(q,output);prompt.setText("");status.setText(R.string.cloud_history_limit);render();
            });
        });
    }
    private void cancel(){
        serial++;HttpsURLConnection c=connection;if(c!=null)c.disconnect();
        setBusy(false);status.setText(R.string.cloud_cancel_cost);
    }
}
