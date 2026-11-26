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
    private final Button send,stop,settings,conversations;
    private volatile HttpsURLConnection connection;
    private volatile int serial;
    private volatile boolean closed;
    private boolean active, busy;
    private AlertDialog keyDialog,actionDialog;
    private CloudApi.Provider provider=CloudApi.Provider.OPENAI;
    private String model="",token="";
    private double inputRate=-1,outputRate=-1;
    private volatile HttpsURLConnection catalogRequest;
    private int errorLabel(Exception e){
        if(e instanceof java.net.SocketTimeoutException)return R.string.cloud_timeout;
        if(e instanceof CloudApi.HttpFailure){int code=((CloudApi.HttpFailure)e).status;
            if(code==401||code==403)return R.string.cloud_auth_error;
            if(code==429)return R.string.cloud_rate_limit;
            if(code>=500)return R.string.cloud_provider_error;
        }return R.string.cloud_fail;
    }
    private String usageLabel(CloudApi.Reply reply){
        String unknown=getResources().getString(R.string.cloud_usage_unknown);
        String usage=getResources().getString(R.string.cloud_usage,reply.inputTokens<0?unknown:Long.toString(reply.inputTokens),reply.outputTokens<0?unknown:Long.toString(reply.outputTokens));
        if(inputRate>=0&&outputRate>=0&&reply.inputTokens>=0&&reply.outputTokens>=0){
            double amount=(inputRate*reply.inputTokens+outputRate*reply.outputTokens)/1000000d;
            usage+="\n"+getResources().getString(R.string.cloud_estimated_cost,String.format(java.util.Locale.US,"%.6f",amount));
        }return usage;
    }
    private double rate(String text){if(text.trim().isEmpty())return -1;double value=Double.parseDouble(text.trim());if(!Double.isFinite(value)||value<0||value>1000000)throw new IllegalArgumentException();return value;}
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
        conversations=button(R.string.cloud_conversations);
        conversations.setOnClickListener(v->chooseConversation());
        addView(conversations,new LayoutParams(-1,-2));
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
    void activate(){if(closed)return;active=true;owner.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);restoreDraft();render();}
    void pause(){
        rememberDraft();active=false;
        if(actionDialog!=null){actionDialog.dismiss();actionDialog=null;}
        if(keyDialog!=null){keyDialog.dismiss();keyDialog=null;}
        HttpsURLConnection catalog=catalogRequest;if(catalog!=null)catalog.disconnect();
        if(busy)cancel();token="";
    }
    void resume(){if(!closed)active=true;}
    void destroy(){pause();closed=true;history.clear();io.shutdownNow();}
    private CloudHistory.Session session(){return history.session(provider.name(),model);}
    private boolean current(CloudApi.Provider p,String id,CloudHistory.Session s,long revision){
        return active&&!closed&&!busy&&p==provider&&id.equals(model)&&history.isCurrent(p.name(),id,s,revision);
    }
    private void rememberDraft(){
        if(!model.isEmpty())try{session().draft(prompt.getText().toString());}catch(Exception ignored){}
    }
    private void restoreDraft(){
        prompt.setText(model.isEmpty()?"":session().draft());
    }
    private void showAction(AlertDialog dialog){
        if(actionDialog!=null)actionDialog.dismiss();
        actionDialog=dialog;
        dialog.setOnDismissListener(d->{if(actionDialog==dialog)actionDialog=null;});
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
    private String title(CloudHistory.Session s){
        List<CloudHistory.Turn> turns=s.turns();
        String first=turns.isEmpty()?s.draft():turns.get(0).question;
        if(first.trim().isEmpty())first=getResources().getString(R.string.new_chat);
        first=first.replace('\n',' ').replace('\r',' ').trim();
        if(first.length()>38)first=first.substring(0,38)+"…";
        return getResources().getString(s.branched?R.string.cloud_branch_label:R.string.cloud_session_label,s.id,first);
    }
    private void render(){
        endpoint.setText(model.isEmpty()?getResources().getString(R.string.cloud_provider_model):provider.title+" · "+model);
        transcript.removeAllViews();
        conversations.setEnabled(!busy&&!model.isEmpty());
        if(!model.isEmpty()){
            try{
                CloudHistory.Session s=session();List<CloudHistory.Turn> turns=s.turns();
                conversations.setText(title(s));
                conversations.setContentDescription(getResources().getString(R.string.cloud_conversations)+" · "+title(s));
                for(int i=0;i<turns.size();i++){CloudHistory.Turn t=turns.get(i);bubble(t.question,true,i);bubble(t.answer,false,i);}
            }catch(IllegalStateException e){toast(R.string.cloud_session_capacity);}
        }else conversations.setText(R.string.cloud_conversations);
        scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
    }
    private void bubble(String value,boolean user,int turn){
        TextView text=new TextView(owner);text.setTextSize(15);text.setTextIsSelectable(true);
        text.setPadding(dp(10),dp(10),dp(10),dp(10));text.setLineSpacing(dp(3),1f);text.setAutoLinkMask(0);
        View rich=user?null:ChatRichView.create(owner,provider.title,value,"",text.getCurrentTextColor(),0x22808080,1f);
        if(rich!=null)transcript.addView(rich,new LayoutParams(-1,-2));
        else{
            text.setText(user?value:ChatPresentation.format(provider.title+"\n",value,"",0x33808080));
            if(user)text.setBackgroundColor(0x14808080);
            transcript.addView(text,new LayoutParams(-1,-2));
        }
        LinearLayout actions=new LinearLayout(owner);actions.setOrientation(HORIZONTAL);
        Button edit=button(user?R.string.chat_edit_question:R.string.chat_regenerate);
        edit.setOnClickListener(v->branch(turn,user));actions.addView(edit,new LayoutParams(0,dp(48),1f));
        if(!user){
            Button copy=button(R.string.copy);
            copy.setOnClickListener(v->{ClipboardManager cb=(ClipboardManager)owner.getSystemService(Context.CLIPBOARD_SERVICE);
                if(cb!=null){cb.setPrimaryClip(ClipData.newPlainText(provider.title,value));toast(R.string.copied);}});
            actions.addView(copy,new LayoutParams(0,dp(48),1f));
        }
        transcript.addView(actions,new LayoutParams(-1,-2));
    }
    private void chooseConversation(){
        if(busy||closed||!active||model.isEmpty())return;
        rememberDraft();
        final CloudApi.Provider p=provider;final String id=model;final CloudHistory.Session old=session();
        final long rev=old.revision();
        List<CloudHistory.Session> items=history.conversations(p.name(),id);
        String[] labels=new String[items.size()];
        for(int i=0;i<items.size();i++)labels[i]=title(items.get(i));
        showAction(new AlertDialog.Builder(owner).setTitle(R.string.cloud_conversations)
            .setSingleChoiceItems(labels,items.indexOf(old),(d,w)->{
                d.dismiss();if(!current(p,id,old,rev))return;
                try{history.select(p.name(),id,items.get(w));restoreDraft();status.setText("");render();}
                catch(Exception e){toast(R.string.cloud_context_changed);}
            }).setNegativeButton(R.string.cancel,null)
            .setNeutralButton(R.string.cloud_remove_current,(d,w)->removeConversation(p,id,old,rev))
            .setPositiveButton(R.string.new_chat,(d,w)->newChat()).create());
    }
    private void removeConversation(CloudApi.Provider p,String id,CloudHistory.Session old,long rev){
        showAction(new AlertDialog.Builder(owner).setMessage(R.string.cloud_remove_confirm)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok,(d,w)->{
                if(!current(p,id,old,rev))return;
                try{history.removeCurrent(p.name(),id,old,rev);restoreDraft();status.setText("");render();}
                catch(Exception e){toast(R.string.cloud_context_changed);}
            }).create());
    }
    void newChat(){
        if(busy||!active||closed||model.isEmpty())return;
        final CloudApi.Provider p=provider;final String id=model;final CloudHistory.Session old=session();
        long rev=old.revision();
        showAction(new AlertDialog.Builder(owner).setMessage(R.string.cloud_new_session)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok,(d,w)->{
                if(!current(p,id,old,rev))return;
                try{rememberDraft();history.newSession(p.name(),id);restoreDraft();status.setText("");render();}
                catch(IllegalStateException e){toast(R.string.cloud_session_capacity);}
            }).create());
    }
    private void branch(int turn,boolean editing){
        if(busy||!active||closed||model.isEmpty())return;
        final CloudApi.Provider p=provider;final String id=model;final CloudHistory.Session source=session();
        long rev=source.revision();List<CloudHistory.Turn> turns=source.turns();
        if(turn<0||turn>=turns.size()){toast(R.string.cloud_context_changed);return;}
        final String original=turns.get(turn).question;
        final EditText changed;
        AlertDialog.Builder builder=new AlertDialog.Builder(owner).setTitle(editing?R.string.chat_edit_question:R.string.chat_regenerate);
        if(editing){
            LinearLayout form=new LinearLayout(owner);form.setOrientation(VERTICAL);int pad=dp(16);form.setPadding(pad,pad,pad,pad);
            TextView notice=new TextView(owner);notice.setText(R.string.cloud_branch_notice);form.addView(notice);
            changed=input(R.string.message_hint,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,CloudHistory.MAX_PROMPT);
            changed.setText(original);changed.setMinLines(3);changed.setMaxLines(7);form.addView(changed);
            builder.setView(form);
        }else{changed=null;builder.setMessage(R.string.cloud_branch_notice);}
        AlertDialog dialog=builder.setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.chat_branch_create,null).create();
        showAction(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!current(p,id,source,rev)){dialog.dismiss();toast(R.string.cloud_context_changed);return;}
            String q=changed==null?original:changed.getText().toString().trim();
            if(q.isEmpty()){toast(R.string.chat_prompt_limit);return;}
            try{
                rememberDraft();history.fork(p.name(),id,source,rev,turn,q);
                dialog.dismiss();restoreDraft();status.setText(R.string.cloud_branch_ready);render();
                // Branch creation is local only. The user must press Send and consent to a provider request.
            }catch(IllegalStateException e){toast(R.string.cloud_session_capacity);}
            catch(IllegalArgumentException e){toast(R.string.cloud_context_changed);}
        });
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
        Button fetch=button(R.string.cloud_fetch_models);fields.addView(fetch);
        EditText inRate=input(R.string.cloud_input_rate,InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL,20);
        EditText outRate=input(R.string.cloud_output_rate,InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL,20);
        if(inputRate>=0)inRate.setText(Double.toString(inputRate));if(outputRate>=0)outRate.setText(Double.toString(outputRate));
        fields.addView(inRate);fields.addView(outRate);
        CheckBox remember=new CheckBox(owner);remember.setText(R.string.cloud_key_remember);fields.addView(remember);
        Button load=button(R.string.cloud_key_load),delete=button(R.string.cloud_key_delete);
        fields.addView(load);fields.addView(delete);
        final int[] last={provider.ordinal()};
        pick.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
            @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                if(pos!=last[0]){last[0]=pos;chosenModel.setText("");key.setText("");inRate.setText("");outRate.setText("");remember.setChecked(false);}
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
        keyDialog=dialog;
        dialog.setOnDismissListener(d->{key.setText("");if(keyDialog==dialog)keyDialog=null;HttpsURLConnection c=catalogRequest;if(c!=null)c.disconnect();});
        fetch.setOnClickListener(v->{
            final CloudApi.Provider selected=values[pick.getSelectedItemPosition()];final String secret=key.getText().toString().trim();
            if(secret.isEmpty()){toast(R.string.cloud_key_needed);return;}
            new AlertDialog.Builder(owner).setMessage(R.string.cloud_fetch_notice).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok,(d,w)->{
                if(!active||closed||keyDialog!=dialog)return;fetch.setEnabled(false);
                io.execute(()->{HttpsURLConnection c=null;List<String> models=null;int err=R.string.cloud_fail;
                    try{c=CloudApi.catalogConnection(selected,secret);catalogRequest=c;models=CloudApi.catalog(c);}catch(Exception e){err=errorLabel(e);}finally{if(c!=null)c.disconnect();if(catalogRequest==c)catalogRequest=null;}
                    final List<String> result=models;final int failure=err;owner.runOnUiThread(()->{
                        if(!active||closed||keyDialog!=dialog||!dialog.isShowing())return;fetch.setEnabled(true);
                        if(values[pick.getSelectedItemPosition()]!=selected||!secret.equals(key.getText().toString().trim()))return;
                        if(result==null){toast(failure);return;}new AlertDialog.Builder(owner).setTitle(R.string.cloud_fetch_models).setItems(result.toArray(new String[0]),(x,i)->{if(keyDialog==dialog&&values[pick.getSelectedItemPosition()]==selected)chosenModel.setText(result.get(i));}).setNegativeButton(R.string.cancel,null).show();
                    });
                });
            }).show();
        });
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!active||closed||busy){dialog.dismiss();return;}
            CloudApi.Provider selected=values[pick.getSelectedItemPosition()];
            String id=chosenModel.getText().toString().trim(),apiKey=key.getText().toString().trim();
            if(id.isEmpty()||apiKey.isEmpty()||apiKey.indexOf('\r')>=0||apiKey.indexOf('\n')>=0){toast(R.string.cloud_fields);return;}
            try{history.session(selected.name(),id);}catch(IllegalStateException e){toast(R.string.cloud_session_capacity);return;}
            catch(IllegalArgumentException e){toast(R.string.cloud_fields);return;}
            if(remember.isChecked())try{vault.save(selected,apiKey);}catch(Exception e){toast(R.string.cloud_key_error);return;}
            double ir,or;try{ir=rate(inRate.getText().toString());or=rate(outRate.getText().toString());}catch(RuntimeException e){toast(R.string.cloud_fields);return;}
            rememberDraft();inputRate=ir;outputRate=or;provider=selected;model=id;token=apiKey;restoreDraft();status.setText("");dialog.dismiss();render();
        });
    }
    private void setBusy(boolean b){
        busy=b;send.setEnabled(!b);send.setVisibility(b?GONE:VISIBLE);
        stop.setEnabled(b);stop.setVisibility(b?VISIBLE:GONE);
        settings.setEnabled(!b);prompt.setEnabled(!b);conversations.setEnabled(!b&&!model.isEmpty());
    }
    private void prepareSend(){
        if(busy||!active||closed)return;
        final String q=prompt.getText().toString().trim();if(q.isEmpty())return;
        if(model.isEmpty()||token.isEmpty()){toast(R.string.cloud_key_needed);configure();return;}
        final CloudApi.Provider p=provider;final String id=model;final CloudHistory.Session s=session();
        final long revision=s.revision();
        final List<CloudHistory.Message> messages=s.request(q);
        String preview;
        try{
            java.util.ArrayList<LocalChatTurns.Message> selected=new java.util.ArrayList<>();
            for(CloudHistory.Message message:messages)selected.add(new LocalChatTurns.Message(message.role,message.content));
            preview=TransferReview.text(selected,getResources().getString(R.string.chat_export_user),
                getResources().getString(R.string.chat_export_assistant),getResources().getString(R.string.chat_export_other));
        }catch(Exception e){toast(R.string.transfer_review_failed);return;}
        ScrollView review=new ScrollView(owner);LinearLayout panel=new LinearLayout(owner);panel.setOrientation(VERTICAL);panel.setPadding(dp(16),dp(10),dp(16),dp(10));review.addView(panel);
        TextView notice=new TextView(owner);notice.setText(getResources().getString(R.string.cloud_send_context,p.title,messages.size())+"\n\n"+getResources().getString(R.string.transfer_cloud_notice));
        notice.setTextSize(13);panel.addView(notice,new LinearLayout.LayoutParams(-1,-2));
        TextView content=new TextView(owner);content.setText(preview);content.setTextSize(13);content.setAutoLinkMask(0);content.setTextIsSelectable(false);content.setSaveEnabled(false);
        panel.addView(content,new LinearLayout.LayoutParams(-1,-2));
        showAction(new AlertDialog.Builder(owner).setTitle(p.title+" · "+getResources().getString(R.string.transfer_review))
            .setView(review)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.send,(d,w)->{
                if(token.isEmpty()||!current(p,id,s,revision)||!q.equals(prompt.getText().toString().trim())){
                    toast(R.string.cloud_context_changed);return;
                }
                request(p,id,token,q,s,revision,messages);
            }).create());
    }
    private void request(CloudApi.Provider p,String id,String apiKey,String q,CloudHistory.Session s,long revision,List<CloudHistory.Message> messages){
        s.draft(q);int request=++serial;setBusy(true);status.setText(R.string.thinking);
        TextView question=new TextView(owner);question.setText(q);question.setTextSize(15);question.setPadding(dp(10),dp(10),dp(10),dp(10));transcript.addView(question,new LayoutParams(-1,-2));
        TextView partial=new TextView(owner);partial.setTextSize(15);partial.setPadding(dp(10),dp(10),dp(10),dp(10));partial.setAutoLinkMask(0);partial.setTextIsSelectable(true);transcript.addView(partial,new LayoutParams(-1,-2));
        io.execute(()->{
            HttpsURLConnection c=null;CloudApi.Reply result=null;int err=R.string.cloud_fail;
            try{
                if(closed||request!=serial)return;
                c=CloudApi.connection(p,apiKey);connection=c;
                if(closed||request!=serial)return;
                result=CloudApi.streamReply(p,c,id,messages,value->owner.runOnUiThread(()->{if(!closed&&active&&request==serial){partial.setText(value);scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}}));
            }catch(Exception e){err=errorLabel(e);}finally{if(c!=null)c.disconnect();if(connection==c)connection=null;}
            CloudApi.Reply output=result;final int failure=err;
            owner.runOnUiThread(()->{
                if(closed||request!=serial||!active)return;
                setBusy(false);
                if(!current(p,id,s,revision)){status.setText(R.string.cloud_context_changed);return;}
                if(output==null){render();status.setText(failure);return;}
                try{s.complete(q,output.text);prompt.setText("");status.setText(usageLabel(output));render();}
                catch(IllegalArgumentException e){status.setText(R.string.cloud_fail);}
            });
        });
    }
    private void cancel(){
        serial++;HttpsURLConnection c=connection;if(c!=null)c.disconnect();
        setBusy(false);render();status.setText(R.string.cloud_cancel_cost);
    }
}
