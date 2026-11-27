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
import java.net.HttpURLConnection;

/** Embedded cloud conversation panel; never reads local chat state or local model identifiers. */
final class CloudChatPanel extends LinearLayout {
    private final Activity owner;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final CloudHistory history=new CloudHistory();
    private final CloudKeyVault vault;
    private final TextView endpoint,status,usageSummary;
    private final CloudUsageLedger ledger=new CloudUsageLedger();
    private final java.util.EnumMap<CloudApi.Provider,Profile> profiles=new java.util.EnumMap<>(CloudApi.Provider.class);
    private static final class Profile {
        final String model,address;final double in,out,budget;
        Profile(String m,String a,double i,double o,double b){model=m;address=a;in=i;out=o;budget=b;}
    }
    private double budget=-1;
    private final android.content.SharedPreferences profilePrefs;
    private void restoreProfiles(){
        for(CloudApi.Provider p:CloudApi.Provider.values())try{
            String raw=profilePrefs.getString(p.name(),null);if(raw==null)continue;
            org.json.JSONObject j=new org.json.JSONObject(raw);
            String id=j.getString("model"),address=j.optString("address","");
            if(!PrivacyPolicy.validModelId(id))continue;
            if(p.remote())address=CloudApi.remoteAddress(address);else address="";
            double in=rate(j.optString("in","")),out=rate(j.optString("out","")),limit=rate(j.optString("budget",""));
            profiles.put(p,new Profile(id,address,in,out,limit));
        }catch(Exception ignored){}
        try{
            CloudApi.Provider p=CloudApi.Provider.valueOf(profilePrefs.getString("selected","OPENAI"));
            Profile saved=profiles.get(p);if(saved!=null){provider=p;model=saved.model;pcAddress=saved.address;inputRate=saved.in;outputRate=saved.out;budget=saved.budget;}
        }catch(Exception ignored){}
    }
    private boolean saveProfile(CloudApi.Provider p,Profile value){
        try{
            org.json.JSONObject j=new org.json.JSONObject().put("model",value.model).put("address",value.address)
                .put("in",value.in<0?"":Double.toString(value.in)).put("out",value.out<0?"":Double.toString(value.out))
                .put("budget",value.budget<0?"":Double.toString(value.budget));
            if(!profilePrefs.edit().putString(p.name(),j.toString()).putString("selected",p.name()).commit())return false;
            profiles.put(p,value);return true;
        }catch(Exception e){return false;}
    }
    private String usageScope(CloudApi.Provider p){return scope(p);}
    private boolean budgetAllows(){
        if(provider.remote()||budget<0)return true;
        return ledger.allows(usageScope(provider),budget,inputRate,outputRate);
    }
    private void refreshUsage(){
        if(model.isEmpty()){usageSummary.setText("");return;}
        CloudUsageLedger.Totals t=ledger.get(usageScope(provider));
        String label=getResources().getString(R.string.cloud_session_usage,Long.toString(t.requests),Long.toString(t.input),Long.toString(t.output));
        if(!provider.remote())label+="\n"+getResources().getString(R.string.cloud_session_cost,String.format(java.util.Locale.US,"%.6f",t.estimated),Long.toString(t.unpriced));
        usageSummary.setText(label);
    }
    private final LinearLayout transcript;
    private final ScrollView scroll;
    private final Button latest;
    private final CloudViewportPolicy viewportPolicy=new CloudViewportPolicy();
    private boolean programmaticScroll;
    private final Runnable followTask=this::followIfCurrent;
    private long followTicket;
    private final EditText prompt;
    private final Button send,stop,settings,conversations;
    private final LinearLayout header,composer,composerActions;
    private final Button back;
    private Boolean stackedHeader,stackedComposer,compactHeight;
    private volatile HttpURLConnection connection;
    private volatile int serial;
    private volatile boolean closed;
    private boolean active, busy;
    private AlertDialog keyDialog,actionDialog;
    private CloudApi.Provider provider=CloudApi.Provider.OPENAI;
    private String model="",token="",pcAddress="";
    private String scope(CloudApi.Provider p){return p.remote()?p.name()+"@"+pcAddress:p.name();}
    private double inputRate=-1,outputRate=-1;
    private volatile HttpURLConnection catalogRequest;
    private volatile long catalogSerial;
    private java.util.concurrent.Future<?> catalogTask,activeRequest;
    private static void disconnect(HttpURLConnection c){
        if(c!=null)try{c.disconnect();}catch(RuntimeException ignored){}
    }
    private static void disconnectAsync(HttpURLConnection c){
        if(c==null)return;
        try{
            Thread t=new Thread(()->disconnect(c),"onhand-cloud-cancel");
            t.setDaemon(true);t.start();
        }catch(RuntimeException|OutOfMemoryError unavailable){
            android.util.Log.w("OnHandAI","Cloud cancellation worker unavailable");
        }
    }
    private void cancelCatalog(){
        catalogSerial++;
        java.util.concurrent.Future<?> task=catalogTask;catalogTask=null;
        if(task!=null)task.cancel(true);
        disconnectAsync(catalogRequest);
    }
    private int errorLabel(Exception e){
        if(e instanceof CloudApi.StreamFailure)return R.string.chat_stream_incomplete;
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
        if(!provider.remote()&&inputRate>=0&&outputRate>=0&&reply.inputTokens>=0&&reply.outputTokens>=0){
            double amount=(inputRate*reply.inputTokens+outputRate*reply.outputTokens)/1000000d;
            usage+="\n"+getResources().getString(R.string.cloud_estimated_cost,String.format(java.util.Locale.US,"%.6f",amount));
        }return usage;
    }
    private double rate(String text){if(text.trim().isEmpty())return -1;double value=Double.parseDouble(text.trim());if(!Double.isFinite(value)||value<0||value>1000000)throw new IllegalArgumentException();return value;}
    CloudChatPanel(Activity activity,Runnable local){
        super(activity);owner=activity;vault=new CloudKeyVault(activity);
        profilePrefs=activity.getSharedPreferences("onhand_cloud_profiles_v1",Context.MODE_PRIVATE);restoreProfiles();
        setOrientation(VERTICAL);
        setPadding(dp(6),dp(4),dp(6),dp(6));
        header=new LinearLayout(owner);
        header.setOrientation(HORIZONTAL);
        back=button(R.string.cloud_return);
        Button configure=button(R.string.api_manager_title);
        settings=configure;
        header.addView(back,new LayoutParams(0,-2,1f));
        header.addView(configure,new LayoutParams(0,-2,1f));
        addView(header,new LayoutParams(-1,-2));
        back.setOnClickListener(v->local.run());configure.setOnClickListener(v->showApiManager());
        endpoint=new TextView(owner);WorkbenchStyle.label(owner,endpoint,16,false,true);OnHandGlyphs.accentLabel(endpoint,OnHandGlyphs.Kind.CLOUD);
        endpoint.setSaveEnabled(false);
        addView(endpoint,new LayoutParams(-1,-2));
        usageSummary=new TextView(owner);WorkbenchStyle.label(owner,usageSummary,12,true,false);
        usageSummary.setPadding(dp(12),dp(8),dp(12),dp(8));addView(usageSummary,WorkbenchStyle.row(owner));
        conversations=button(R.string.cloud_conversations);
        conversations.setOnClickListener(v->chooseConversation());
        addView(conversations,new LayoutParams(-1,-2));
        TextView notice=new TextView(owner);notice.setText(R.string.cloud_context_notice);notice.setTextSize(11);notice.setPadding(0,dp(5),0,dp(5));
        addView(notice,new LayoutParams(-1,-2));
        scroll=new ScrollView(owner);scroll.setFillViewport(true);
        transcript=new LinearLayout(owner);transcript.setOrientation(VERTICAL);
        scroll.addView(transcript,new ScrollView.LayoutParams(-1,-2));
        FrameLayout viewport=new FrameLayout(owner);
        viewport.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        latest=button(R.string.workspace_latest);latest.setText("↓");latest.setMinWidth(dp(48));latest.setMinimumWidth(dp(48));OnHandGlyphs.label(latest,OnHandGlyphs.Kind.DOWN,true);
        latest.setContentDescription(getResources().getString(R.string.workspace_latest));
        latest.setTooltipText(getResources().getString(R.string.workspace_latest));latest.setVisibility(GONE);
        FrameLayout.LayoutParams jump=new FrameLayout.LayoutParams(dp(48),dp(48),android.view.Gravity.BOTTOM|android.view.Gravity.END);
        jump.bottomMargin=dp(8);jump.rightMargin=dp(6);viewport.addView(latest,jump);
        latest.setOnClickListener(v->{viewportPolicy.reset();scheduleFollow();});
        scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{
            if(!programmaticScroll&&y!=oldY)viewportPolicy.observed(transcript.getBottom(),scroll.getHeight(),y,dp(96));
            refreshLatest();
        });
        transcript.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            if(b-t!=ob-ot&&viewportPolicy.following())scheduleFollow();else refreshLatest();
        });
        addView(viewport,new LayoutParams(-1,0,1f));
        status=new TextView(owner);status.setTextSize(12);status.setPadding(0,dp(3),0,dp(3));
        addView(status,new LayoutParams(-1,-2));
        composer=new LinearLayout(owner);composer.setOrientation(HORIZONTAL);
        composer.setGravity(android.view.Gravity.CENTER_VERTICAL);
        WorkbenchStyle.card(owner,composer,false);composer.setPadding(dp(8),dp(6),dp(8),dp(6));
        prompt=input(R.string.message_hint,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,CloudHistory.MAX_PROMPT);
        prompt.setMinLines(1);prompt.setMaxLines(4);prompt.setMinimumHeight(dp(48));prompt.setSaveEnabled(false);
        prompt.setContentDescription(getResources().getString(R.string.message_hint));
        composer.addView(prompt,new LayoutParams(0,-2,1f));
        send=button(R.string.send);stop=button(R.string.stop);
        send.setContentDescription(getResources().getString(R.string.send));stop.setContentDescription(getResources().getString(R.string.stop));
        OnHandGlyphs.label(send,OnHandGlyphs.Kind.SEND,true);OnHandGlyphs.label(stop,OnHandGlyphs.Kind.STOP,true);
        composerActions=new LinearLayout(owner);composerActions.setOrientation(HORIZONTAL);
        composerActions.addView(send,new LayoutParams(-2,-2));composerActions.addView(stop,new LayoutParams(-2,-2));
        composer.addView(composerActions,new LayoutParams(-2,-2));
        addView(composer,new LayoutParams(-1,-2));
        send.setOnClickListener(v->prepareSend());stop.setOnClickListener(v->cancel());
        setBusy(false);render();
        addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            if(r-l!=or-ol||b-t!=ob-ot)applyResponsiveLayout();
        });
        post(this::applyResponsiveLayout);
    }
    private void applyResponsiveLayout(){
        if(closed||getWidth()<=0)return;
        float density=getResources().getDisplayMetrics().density,scale=getResources().getConfiguration().fontScale;
        int full=(int)(getWidth()/density);
        int max=dp(WorkbenchLayoutPolicy.cloudContentDp(full));
        int side=dp(6)+Math.max(0,(getWidth()-max)/2);
        if(getPaddingLeft()!=side||getPaddingRight()!=side)setPadding(side,dp(4),side,dp(6));
        int width=(int)(Math.max(0,getWidth()-side*2)/density),height=(int)(getHeight()/density);
        boolean hs=WorkbenchLayoutPolicy.cloudHeaderStacked(width,scale);
        if(stackedHeader==null||stackedHeader!=hs){
            stackedHeader=hs;header.setOrientation(hs?VERTICAL:HORIZONTAL);
            back.setLayoutParams(hs?new LayoutParams(-1,-2):new LayoutParams(0,-2,1f));
            settings.setLayoutParams(hs?new LayoutParams(-1,-2):new LayoutParams(0,-2,1f));
        }
        boolean cs=WorkbenchLayoutPolicy.cloudComposerStacked(width,scale);
        if(stackedComposer==null||stackedComposer!=cs){
            stackedComposer=cs;composer.setOrientation(cs?VERTICAL:HORIZONTAL);
            prompt.setLayoutParams(cs?new LayoutParams(-1,-2):new LayoutParams(0,-2,1f));
            composerActions.setLayoutParams(new LayoutParams(cs?-1:-2,-2));
            send.setLayoutParams(cs?new LayoutParams(0,-2,1f):new LayoutParams(-2,-2));
            stop.setLayoutParams(cs?new LayoutParams(0,-2,1f):new LayoutParams(-2,-2));
        }
        boolean compact=WorkbenchLayoutPolicy.compactCloudHeight(height,scale);
        if(compactHeight==null||compactHeight!=compact){
            compactHeight=compact;
            prompt.setMaxLines(compact?2:4);
            usageSummary.setVisibility(compact?GONE:VISIBLE);
        }
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private Button button(int label){
        Button b=new Button(owner);b.setText(label);WorkbenchStyle.button(owner,b,label);return b;
    }
    private EditText input(int hint,int type,int limit){
        EditText field=new EditText(owner);field.setHint(hint);field.setInputType(type);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(limit)});
        field.setSaveEnabled(false);WorkbenchStyle.field(owner,field);return field;
    }
    private void toast(int label){Toast.makeText(owner,label,Toast.LENGTH_SHORT).show();}
    boolean isBusy(){return busy;}
    void activate(){if(closed)return;active=true;owner.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);restoreDraft();render();}
    void pause(){
        rememberDraft();active=false;
        viewportPolicy.invalidate();scroll.removeCallbacks(followTask);
        if(actionDialog!=null){actionDialog.dismiss();actionDialog=null;}
        if(keyDialog!=null){keyDialog.dismiss();keyDialog=null;}
        cancelCatalog();
        if(busy)cancel();token="";
    }
    void resume(){if(!closed)active=true;}
    void destroy(){pause();closed=true;history.clear();ledger.clear();profiles.clear();io.shutdownNow();}
    private CloudHistory.Session session(){return history.session(scope(provider),model);}
    private boolean current(CloudApi.Provider p,String id,CloudHistory.Session s,long revision){
        return active&&!closed&&!busy&&p==provider&&id.equals(model)&&history.isCurrent(scope(p),id,s,revision);
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
        WorkbenchWindow.fit(owner,dialog);
    }
    private String title(CloudHistory.Session s){
        List<CloudHistory.Turn> turns=s.turns();
        String first=turns.isEmpty()?s.draft():turns.get(0).question;
        if(first.trim().isEmpty())first=getResources().getString(R.string.new_chat);
        first=CloudViewportPolicy.shortTitle(first,38);
        return getResources().getString(s.branched?R.string.cloud_branch_label:R.string.cloud_session_label,s.id,first);
    }
    private void render(){render(true);}
    private void render(boolean resetPosition){
        if(resetPosition)viewportPolicy.reset();else viewportPolicy.invalidate();
        scroll.removeCallbacks(followTask);
        int priorY=scroll.getScrollY();
        final boolean follow=viewportPolicy.following();
        final long ticket=viewportPolicy.ticket();
        endpoint.setText(model.isEmpty()?getResources().getString(R.string.cloud_provider_model):provider.title+(provider.remote()?" · "+pcAddress:"")+" · "+model);
        refreshUsage();
        programmaticScroll=true;
        try{
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
        }finally{programmaticScroll=false;}
        if(follow)scheduleFollow();
        else scroll.post(()->{
            if(!closed&&active&&viewportPolicy.accepts(ticket)&&!viewportPolicy.following()){
                programmaticScroll=true;
                try{scroll.scrollTo(0,Math.min(priorY,Math.max(0,transcript.getBottom()-scroll.getHeight())));}
                finally{programmaticScroll=false;}
                refreshLatest();
            }
        });
        refreshLatest();
    }
    private void refreshLatest(){
        latest.setVisibility(active&&!closed&&transcript.getChildCount()>0&&!ChatWorkspacePolicy.nearLatest(
            transcript.getBottom(),scroll.getHeight(),scroll.getScrollY(),dp(96))?VISIBLE:GONE);
    }
    private void scheduleFollow(){
        if(closed||!active||!viewportPolicy.following())return;
        followTicket=viewportPolicy.ticket();
        scroll.removeCallbacks(followTask);scroll.post(followTask);
    }
    private void followIfCurrent(){
        if(closed||!active||!viewportPolicy.shouldFollow(followTicket))return;
        programmaticScroll=true;
        try{scroll.scrollTo(0,Math.max(0,transcript.getBottom()-scroll.getHeight()));}
        finally{programmaticScroll=false;}
        refreshLatest();
    }
    private void bubble(String value,boolean user,int turn){
        TextView text=new TextView(owner);text.setTextSize(15);text.setTextIsSelectable(true);
        text.setPadding(dp(10),dp(10),dp(10),dp(10));text.setLineSpacing(dp(3),1f);text.setAutoLinkMask(0);
        View rich=user?null:ChatRichView.create(owner,provider.title,value,"",text.getCurrentTextColor(),0x22808080,1f);
        if(rich!=null)transcript.addView(rich,new LayoutParams(-1,-2));
        else{
            text.setText(user?value:ChatPresentation.format(provider.title+"\n",value,"",0x33808080));
            if(user){
                WorkbenchVisualPolicy.Palette p=WorkbenchStyle.palette(owner);
                android.graphics.drawable.GradientDrawable bubble=new android.graphics.drawable.GradientDrawable();
                bubble.setColor(p.bubble);bubble.setCornerRadius(dp(18));text.setBackground(bubble);text.setTextColor(p.text);
            }
            transcript.addView(text,new LayoutParams(-1,-2));
        }
        LinearLayout actions=new AdaptiveAnswerActions(owner);
        Button edit=button(user?R.string.chat_edit_question:R.string.chat_regenerate);
        edit.setOnClickListener(v->branch(turn,user));actions.addView(edit,new LayoutParams(-2,-2));
        if(!user){
            Button copy=button(R.string.copy);
            copy.setOnClickListener(v->{ClipboardManager cb=(ClipboardManager)owner.getSystemService(Context.CLIPBOARD_SERVICE);
                if(cb!=null){cb.setPrimaryClip(SensitiveClipboard.text(provider.title,value));toast(R.string.copied);}});
            actions.addView(copy,new LayoutParams(-2,-2));
        }
        transcript.addView(actions,new LayoutParams(-1,-2));
    }
    private void chooseConversation(){
        if(busy||closed||!active||model.isEmpty())return;
        rememberDraft();
        final CloudApi.Provider p=provider;final String id=model;final CloudHistory.Session old=session();
        final long rev=old.revision();
        List<CloudHistory.Session> items=history.conversations(scope(p),id);
        String[] labels=new String[items.size()];
        for(int i=0;i<items.size();i++)labels[i]=title(items.get(i));
        showAction(new AlertDialog.Builder(owner).setTitle(R.string.cloud_conversations)
            .setSingleChoiceItems(labels,items.indexOf(old),(d,w)->{
                d.dismiss();if(!current(p,id,old,rev))return;
                try{history.select(scope(p),id,items.get(w));restoreDraft();status.setText("");render();}
                catch(Exception e){toast(R.string.cloud_context_changed);}
            }).setNegativeButton(R.string.cancel,null)
            .setNeutralButton(R.string.cloud_remove_current,(d,w)->removeConversation(p,id,old,rev))
            .setPositiveButton(R.string.new_chat,(d,w)->newChat()).create());
    }
    private void removeConversation(CloudApi.Provider p,String id,CloudHistory.Session old,long rev){
        showAction(new AlertDialog.Builder(owner).setMessage(R.string.cloud_remove_confirm)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok,(d,w)->{
                if(!current(p,id,old,rev))return;
                try{history.removeCurrent(scope(p),id,old,rev);restoreDraft();status.setText("");render();}
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
                try{rememberDraft();history.newSession(scope(p),id);restoreDraft();status.setText("");render();}
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
                rememberDraft();history.fork(scope(p),id,source,rev,turn,q);
                dialog.dismiss();restoreDraft();status.setText(R.string.cloud_branch_ready);render();
                // Branch creation is local only. The user must press Send and consent to a provider request.
            }catch(IllegalStateException e){toast(R.string.cloud_session_capacity);}
            catch(IllegalArgumentException e){toast(R.string.cloud_context_changed);}
        });
    }
    private void showApiManager(){
        if(busy||closed||!active)return;
        ScrollView scroll=new ScrollView(owner);
        LinearLayout list=new LinearLayout(owner);list.setOrientation(VERTICAL);
        list.setPadding(dp(18),dp(12),dp(18),dp(12));scroll.addView(list);
        TextView intro=new TextView(owner);intro.setText(R.string.api_overview_intro);
        WorkbenchStyle.label(owner,intro,14,false,false);list.addView(intro,WorkbenchStyle.row(owner));
        TextView capacity=new TextView(owner);
        capacity.setText(getResources().getString(R.string.api_overview_capacity,ledger.trackedScopes(),CloudUsageLedger.MAX_SCOPES));
        WorkbenchStyle.label(owner,capacity,13,true,false);list.addView(capacity,WorkbenchStyle.row(owner));
        AlertDialog dialog=new AlertDialog.Builder(owner).setTitle(R.string.api_manager_title)
            .setView(scroll).setPositiveButton(R.string.close,null).create();
        for(CloudApi.Provider p:CloudApi.Provider.values()){
            Profile saved=profiles.get(p);
            boolean selected=p==provider;
            LinearLayout card=new LinearLayout(owner);card.setOrientation(VERTICAL);WorkbenchStyle.card(owner,card,selected);
            list.addView(card,WorkbenchStyle.row(owner));
            boolean exists=saved!=null;
            try{exists=exists||profilePrefs.contains(p.name());}catch(RuntimeException ignored){}
            String id=saved==null?"":saved.model,address=saved==null?"":saved.address;
            CloudProviderOverview.ProfileState config=CloudProviderOverview.profileState(p,exists,id,address);
            int configuration=config==CloudProviderOverview.ProfileState.CONFIGURED?R.string.api_overview_configured:
                config==CloudProviderOverview.ProfileState.INVALID?R.string.api_overview_invalid:R.string.api_overview_missing;
            TextView title=new TextView(owner);
            title.setText(p.title+(selected?" · "+getResources().getString(R.string.api_overview_current):""));
            WorkbenchStyle.label(owner,title,16,false,true);card.addView(title,WorkbenchStyle.row(owner));
            String route=config==CloudProviderOverview.ProfileState.CONFIGURED?
                routeText(p,id,CloudApi.requestTarget(p,address)):
                getResources().getString(p.remote()?R.string.api_manager_lan:R.string.api_manager_https);
            StringBuilder detail=new StringBuilder(getResources().getString(configuration)).append('\n').append(route);
            CloudProviderOverview.CredentialState key=CloudProviderOverview.credentialState(p,
                selected&&!p.remote()&&PrivacyPolicy.validApiKey(token),vault.savedEntry(p));
            int keyLabel;
            switch(key){
                case LAN:keyLabel=R.string.api_overview_key_lan;break;
                case SESSION_SAVED:keyLabel=R.string.api_overview_key_session_saved;break;
                case SESSION_ONLY:keyLabel=R.string.api_overview_key_session_only;break;
                case SESSION_UNKNOWN:keyLabel=R.string.api_overview_key_session_unknown;break;
                case SAVED_ONLY:keyLabel=R.string.api_overview_key_saved_only;break;
                case MISSING:keyLabel=R.string.api_overview_key_missing;break;
                default:keyLabel=R.string.api_overview_key_unknown;
            }
            detail.append("\n").append(getResources().getString(keyLabel));
            CloudUsageLedger.Totals totals=ledger.snapshotForProvider(p.name(),p.remote());
            detail.append("\n\n").append(getResources().getString(R.string.cloud_session_usage,
                Long.toString(totals.requests),Long.toString(totals.input),Long.toString(totals.output)));
            detail.append("\n").append(getResources().getString(R.string.api_overview_unknown_usage,totals.unknown));
            if(p.remote()){
                detail.append("\n").append(getResources().getString(R.string.api_overview_lan_usage));
            }else{
                java.math.BigDecimal spent=Double.isFinite(totals.estimated)&&totals.estimated>=0?
                    java.math.BigDecimal.valueOf(totals.estimated):null;
                detail.append("\n").append(getResources().getString(R.string.cloud_session_cost,amount(spent),Long.toString(totals.unpriced)));
                if(config==CloudProviderOverview.ProfileState.CONFIGURED&&saved!=null){
                    detail.append("\n").append(getResources().getString(R.string.api_review_rates,manualRate(saved.in),manualRate(saved.out)));
                    detail.append("\n").append(saved.budget<0?getResources().getString(R.string.api_review_no_limit):
                        getResources().getString(R.string.api_review_limit,manualRate(saved.budget)));
                }
            }
            if(config==CloudProviderOverview.ProfileState.CONFIGURED&&!ledger.canTrack(p.remote()?p.name()+"@"+address:p.name()))
                detail.append("\n").append(getResources().getString(R.string.api_overview_ledger_full));
            TextView body=new TextView(owner);body.setText(detail.toString());WorkbenchStyle.label(owner,body,14,false,false);
            body.setAutoLinkMask(0);body.setSaveEnabled(false);card.addView(body,WorkbenchStyle.row(owner));
            Button edit=button(R.string.api_overview_configure);WorkbenchStyle.button(owner,edit,R.string.api_overview_configure);
            edit.setContentDescription(getResources().getString(R.string.api_overview_configure)+" · "+p.title);
            card.addView(edit,new LinearLayout.LayoutParams(-1,-2));
            edit.setOnClickListener(v->{
                if(!active||closed||busy||actionDialog!=dialog||!dialog.isShowing())return;
                dialog.dismiss();configure(p);
            });
         }
        showAction(dialog);WorkbenchWindow.fit(owner,dialog);
    }
    private void configure(){configure(provider);}
    private void configure(CloudApi.Provider initial){
        if(busy||closed||!active||initial==null)return;
        Profile initialProfile=profiles.get(initial);
        boolean currentProvider=initial==provider;
        String initialModel=currentProvider?model:initialProfile==null?"":initialProfile.model;
        String initialAddress=currentProvider?pcAddress:initialProfile==null?"":initialProfile.address;
        double initialIn=currentProvider?inputRate:initialProfile==null?-1:initialProfile.in;
        double initialOut=currentProvider?outputRate:initialProfile==null?-1:initialProfile.out;
        double initialLimit=currentProvider?budget:initialProfile==null?-1:initialProfile.budget;
        ScrollView sc=new ScrollView(owner);LinearLayout fields=new LinearLayout(owner);fields.setOrientation(VERTICAL);
        int pad=dp(18);fields.setPadding(pad,pad,pad,pad);sc.addView(fields);
        CloudApi.Provider[] values=CloudApi.Provider.values();
        String[] names=new String[values.length];for(int i=0;i<names.length;i++)names[i]=values[i].title;
        Spinner pick=new Spinner(owner);pick.setAdapter(new ArrayAdapter<>(owner,android.R.layout.simple_spinner_dropdown_item,names));
        pick.setSelection(initial.ordinal());pick.setMinimumHeight(dp(48));
        WorkbenchStyle.fieldLabel(owner,fields,R.string.cloud_provider_model,pick);fields.addView(pick,WorkbenchStyle.row(owner));
        EditText chosenModel=input(R.string.cloud_model,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,200);
        chosenModel.setText(initialModel);WorkbenchStyle.field(owner,chosenModel);
        WorkbenchStyle.fieldLabel(owner,fields,R.string.cloud_model,chosenModel);fields.addView(chosenModel,WorkbenchStyle.row(owner));
        EditText key=input(R.string.cloud_key,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,4096);
        key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        key.setText(currentProvider?token:"");WorkbenchStyle.field(owner,key);
        TextView keyCaption=WorkbenchStyle.fieldLabel(owner,fields,R.string.cloud_key,key);fields.addView(key,WorkbenchStyle.row(owner));
        EditText address=input(R.string.cloud_pc_address,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,22);
        address.setText(initialAddress);WorkbenchStyle.field(owner,address);
        TextView addressCaption=WorkbenchStyle.fieldLabel(owner,fields,R.string.cloud_pc_address,address);fields.addView(address,WorkbenchStyle.row(owner));
        TextView lanNotice=new TextView(owner);lanNotice.setText(R.string.cloud_pc_notice);WorkbenchStyle.label(owner,lanNotice,13,false,false);fields.addView(lanNotice,WorkbenchStyle.row(owner));
        TextView routeDetails=new TextView(owner);routeDetails.setTextSize(12);routeDetails.setAutoLinkMask(0);
        routeDetails.setSaveEnabled(false);WorkbenchStyle.label(owner,routeDetails,13,false,false);fields.addView(routeDetails,WorkbenchStyle.row(owner));
        Button fetch=button(R.string.cloud_fetch_models);WorkbenchStyle.button(owner,fetch,R.string.cloud_fetch_models);fields.addView(fetch,WorkbenchStyle.row(owner));
        EditText inRate=input(R.string.cloud_input_rate,InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL,20);
        EditText outRate=input(R.string.cloud_output_rate,InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL,20);
        if(initialIn>=0)inRate.setText(Double.toString(initialIn));if(initialOut>=0)outRate.setText(Double.toString(initialOut));
        WorkbenchStyle.field(owner,inRate);WorkbenchStyle.field(owner,outRate);
        TextView inCaption=WorkbenchStyle.fieldLabel(owner,fields,R.string.cloud_input_rate,inRate);fields.addView(inRate,WorkbenchStyle.row(owner));
        TextView outCaption=WorkbenchStyle.fieldLabel(owner,fields,R.string.cloud_output_rate,outRate);fields.addView(outRate,WorkbenchStyle.row(owner));
        EditText spending=input(R.string.cloud_budget,InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL,20);
        if(initialLimit>=0)spending.setText(Double.toString(initialLimit));WorkbenchStyle.field(owner,spending);
        TextView budgetCaption=WorkbenchStyle.fieldLabel(owner,fields,R.string.cloud_budget,spending);fields.addView(spending,WorkbenchStyle.row(owner));
        TextView priceNotice=new TextView(owner);priceNotice.setText(R.string.api_manager_price_note);WorkbenchStyle.label(owner,priceNotice,13,true,false);fields.addView(priceNotice,WorkbenchStyle.row(owner));
        CheckBox remember=new CheckBox(owner);remember.setText(R.string.cloud_key_remember);remember.setMinimumHeight(dp(48));fields.addView(remember,WorkbenchStyle.row(owner));
        Button load=button(R.string.cloud_key_load),delete=button(R.string.cloud_key_delete);
        WorkbenchStyle.button(owner,load,R.string.cloud_key_load);WorkbenchStyle.button(owner,delete,R.string.cloud_key_delete);
        fields.addView(load,WorkbenchStyle.row(owner));fields.addView(delete,WorkbenchStyle.row(owner));
        Runnable updateRoute=()->{
            int index=pick.getSelectedItemPosition();
            if(index<0||index>=values.length){routeDetails.setText(R.string.unknown);return;}
            CloudApi.Provider selected=values[index];
            String id=chosenModel.getText().toString().trim(),destination;
            try{destination=CloudApi.requestTarget(selected,address.getText().toString().trim());}
            catch(IllegalArgumentException e){destination=getResources().getString(R.string.api_manager_invalid_route);}
            routeDetails.setText(routeText(selected,PrivacyPolicy.validModelId(id)?id:getResources().getString(R.string.unknown),destination));
        };
        android.text.TextWatcher routeWatcher=new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){updateRoute.run();}
            public void afterTextChanged(android.text.Editable e){}
        };
        chosenModel.addTextChangedListener(routeWatcher);address.addTextChangedListener(routeWatcher);
        Runnable updateFields=()->{int index=pick.getSelectedItemPosition();if(index<0||index>=values.length)return;boolean remote=values[index].remote();
            for(View v:new View[]{keyCaption,key,inCaption,inRate,outCaption,outRate,budgetCaption,spending,priceNotice,remember,load,delete})v.setVisibility(remote?GONE:VISIBLE);
            addressCaption.setVisibility(remote?VISIBLE:GONE);address.setVisibility(remote?VISIBLE:GONE);lanNotice.setVisibility(remote?VISIBLE:GONE);updateRoute.run();};
        updateFields.run();
        final int[] last={initial.ordinal()};
        pick.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
            @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                if(pos!=last[0]){last[0]=pos;chosenModel.setText("");key.setText("");inRate.setText("");outRate.setText("");remember.setChecked(false);address.setText("");spending.setText("");
                    Profile saved=profiles.get(values[pos]);if(saved!=null){chosenModel.setText(saved.model);address.setText(saved.address);if(saved.in>=0)inRate.setText(Double.toString(saved.in));if(saved.out>=0)outRate.setText(Double.toString(saved.out));if(saved.budget>=0)spending.setText(Double.toString(saved.budget));}}
                updateFields.run();
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
        AlertDialog dialog=new AlertDialog.Builder(owner).setTitle(R.string.api_manager_title).setView(sc)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok,null).create();
        keyDialog=dialog;
        dialog.setOnDismissListener(d->{key.setText("");if(keyDialog==dialog){keyDialog=null;cancelCatalog();}});
        fetch.setOnClickListener(v->{
            final CloudApi.Provider selected=values[pick.getSelectedItemPosition()];final String secret=selected.remote()?address.getText().toString().trim():key.getText().toString().trim();
            if(selected.remote()){try{CloudApi.remoteBase(secret);}catch(RuntimeException e){toast(R.string.cloud_pc_address);return;}}
            if(secret.isEmpty()){toast(R.string.cloud_key_needed);return;}
            new AlertDialog.Builder(owner).setMessage(selected.remote()?getResources().getString(R.string.cloud_pc_notice)+"\n"+secret:getResources().getString(R.string.cloud_fetch_notice)).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok,(d,w)->{
                if(!active||closed||keyDialog!=dialog||!dialog.isShowing()||values[pick.getSelectedItemPosition()]!=selected||!secret.equals((selected.remote()?address:key).getText().toString().trim()))return;fetch.setEnabled(false);
                cancelCatalog();final long ticket=catalogSerial;
                try{catalogTask=io.submit(()->{
                    HttpURLConnection c=null;List<String> models=null;int err=R.string.cloud_fail;
                    try{
                        if(closed||ticket!=catalogSerial||Thread.currentThread().isInterrupted())return;
                        c=selected.remote()?CloudApi.remoteConnection(selected,secret,false):CloudApi.catalogConnection(selected,secret);
                        catalogRequest=c;
                        if(closed||ticket!=catalogSerial||Thread.currentThread().isInterrupted())return;
                        models=CloudApi.catalog(c);
                    }catch(Exception e){err=errorLabel(e);}
                    finally{disconnect(c);if(catalogRequest==c)catalogRequest=null;}
                    final List<String> result=models;final int failure=err;owner.runOnUiThread(()->{
                        if(!active||closed||ticket!=catalogSerial||keyDialog!=dialog||!dialog.isShowing())return;
                        catalogTask=null;fetch.setEnabled(true);
                        if(values[pick.getSelectedItemPosition()]!=selected||!secret.equals((selected.remote()?address:key).getText().toString().trim()))return;
                        if(result==null){toast(failure);return;}
                        AlertDialog choices=new AlertDialog.Builder(owner).setTitle(R.string.cloud_fetch_models)
                            .setItems(result.toArray(new String[0]),(x,i)->{
                                if(active&&!closed&&ticket==catalogSerial&&keyDialog==dialog&&dialog.isShowing()&&
                                    values[pick.getSelectedItemPosition()]==selected&&secret.equals((selected.remote()?address:key).getText().toString().trim()))
                                    chosenModel.setText(result.get(i));
                            }).setNegativeButton(R.string.cancel,null).create();
                        choices.show();if(choices.getWindow()!=null)choices.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                    });
                });}catch(java.util.concurrent.RejectedExecutionException unavailable){fetch.setEnabled(true);toast(R.string.cloud_fail);}
            }).show();
        });
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        WorkbenchWindow.fit(owner,dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!active||closed||busy){dialog.dismiss();return;}
            CloudApi.Provider selected=values[pick.getSelectedItemPosition()];
            String id=chosenModel.getText().toString().trim(),apiKey=key.getText().toString().trim();
            String target=address.getText().toString().trim();
            if(selected.remote()){try{target=CloudApi.remoteAddress(target);}catch(RuntimeException e){toast(R.string.cloud_pc_address);return;}}
            if(!PrivacyPolicy.validModelId(id)||(!selected.remote()&&!PrivacyPolicy.validApiKey(apiKey))){toast(R.string.cloud_fields);return;}
            try{history.session(selected.remote()?selected.name()+"@"+target:selected.name(),id);}catch(IllegalStateException e){toast(R.string.cloud_session_capacity);return;}
            catch(IllegalArgumentException e){toast(R.string.cloud_fields);return;}
            double ir,or,limit;try{ir=rate(inRate.getText().toString());or=rate(outRate.getText().toString());limit=rate(spending.getText().toString());}catch(RuntimeException e){toast(R.string.cloud_fields);return;}
            if(!selected.remote()&&remember.isChecked())try{vault.save(selected,apiKey);}catch(Exception e){toast(R.string.cloud_key_error);return;}
            if(!saveProfile(selected,new Profile(id,selected.remote()?target:"",ir,or,limit))){toast(R.string.cloud_fail);return;}
            rememberDraft();inputRate=ir;outputRate=or;budget=limit;provider=selected;model=id;pcAddress=selected.remote()?target:"";token=selected.remote()?"":apiKey;restoreDraft();status.setText("");dialog.dismiss();render();
        });
    }
    private void setBusy(boolean b){
        busy=b;send.setEnabled(!b);send.setVisibility(b?GONE:VISIBLE);
        stop.setEnabled(b);stop.setVisibility(b?VISIBLE:GONE);
        settings.setEnabled(!b);prompt.setEnabled(!b);conversations.setEnabled(!b&&!model.isEmpty());
    }
    private String routeText(CloudApi.Provider p,String id,String destination){
        return getResources().getString(R.string.api_manager_route,p.title,id,destination,
            getResources().getString(p.remote()?R.string.api_manager_lan:R.string.api_manager_https));
    }
    private String amount(java.math.BigDecimal value){
        return value==null?getResources().getString(R.string.unknown):value.stripTrailingZeros().toPlainString();
    }
    private String manualRate(double rate){
        return CloudRequestAdvice.validRate(rate)?amount(java.math.BigDecimal.valueOf(rate)):getResources().getString(R.string.unknown);
    }
    private String requestAdviceText(CloudRequestAdvice.Estimate estimate,double ir,double or){
        StringBuilder text=new StringBuilder(getResources().getString(R.string.api_review_units,estimate.inputUnits,estimate.outputLimit));
        text.append("\n").append(getResources().getString(R.string.api_review_heuristic));
        if(estimate.state==CloudRequestAdvice.State.LAN)
            return text.append("\n").append(getResources().getString(R.string.api_review_lan_cost)).toString();
        text.append("\n\n").append(getResources().getString(R.string.api_review_rates,manualRate(ir),manualRate(or)))
            .append("\n").append(getResources().getString(R.string.api_review_cost,amount(estimate.requestCost)))
            .append("\n").append(getResources().getString(R.string.api_review_spent,amount(estimate.knownSpent),
                estimate.unpriced<0?getResources().getString(R.string.unknown):Long.toString(estimate.unpriced)))
            .append("\n").append(getResources().getString(R.string.api_review_projected,amount(estimate.projected)))
            .append("\n").append(estimate.limit==null?getResources().getString(R.string.api_review_no_limit):
                getResources().getString(R.string.api_review_limit,amount(estimate.limit)));
        if(estimate.state==CloudRequestAdvice.State.UNKNOWN_USAGE)text.append("\n\n").append(getResources().getString(R.string.api_review_unknown_block));
        if(estimate.state==CloudRequestAdvice.State.OVER_BUDGET)text.append("\n\n").append(getResources().getString(R.string.api_review_over_block));
        return text.toString();
    }
    private boolean sameEstimate(CloudRequestAdvice.Estimate a,CloudRequestAdvice.Estimate b){
        return a.state==b.state&&a.inputUnits==b.inputUnits&&a.outputLimit==b.outputLimit&&a.unpriced==b.unpriced&&
            java.util.Objects.equals(a.requestCost,b.requestCost)&&java.util.Objects.equals(a.knownSpent,b.knownSpent)&&
            java.util.Objects.equals(a.projected,b.projected)&&java.util.Objects.equals(a.limit,b.limit);
    }
    private void showBudgetReview(String message){
        showAction(new AlertDialog.Builder(owner).setTitle(R.string.api_manager_title).setMessage(message)
            .setPositiveButton(R.string.close,null).create());
    }
    private void prepareSend(){
        if(busy||!active||closed)return;
        final String q=prompt.getText().toString().trim();if(q.isEmpty())return;
        if(model.isEmpty()||(provider.remote()?pcAddress.isEmpty():token.isEmpty())){toast(R.string.cloud_key_needed);configure();return;}
        final CloudApi.Provider p=provider;final String id=model;final CloudHistory.Session s=session();
        final long revision=s.revision();
        final String target=p.remote()?pcAddress:token;
        final double ir=inputRate,or=outputRate,limit=budget;
        if(!ledger.canTrack(usageScope(p))){toast(R.string.api_overview_ledger_full);return;}
        final List<CloudHistory.Message> messages;
        try{messages=s.request(q);}catch(IllegalArgumentException invalid){toast(R.string.transfer_review_failed);return;}
        final String destination;
        try{destination=CloudApi.requestTarget(p,pcAddress);}
        catch(IllegalArgumentException e){toast(R.string.cloud_fields);return;}
        final CloudRequestAdvice.Estimate estimate=CloudRequestAdvice.estimate(p.remote(),messages,
            CloudApi.OUTPUT_TOKEN_LIMIT,ir,or,limit,ledger.get(usageScope(p)));
        if(estimate.state==CloudRequestAdvice.State.INVALID){toast(R.string.transfer_review_failed);return;}
        final String advice=routeText(p,id,destination)+"\n\n"+requestAdviceText(estimate,ir,or);
        if(!estimate.allows()||!budgetAllows()){showBudgetReview(advice+"\n\n"+getResources().getString(R.string.cloud_budget_stop));return;}
        String preview;
        try{
            java.util.ArrayList<LocalChatTurns.Message> selected=new java.util.ArrayList<>();
            for(CloudHistory.Message message:messages)selected.add(new LocalChatTurns.Message(message.role,message.content));
            preview=TransferReview.text(selected,getResources().getString(R.string.chat_export_user),
                getResources().getString(R.string.chat_export_assistant),getResources().getString(R.string.chat_export_other));
        }catch(Exception e){toast(R.string.transfer_review_failed);return;}
        ScrollView review=new ScrollView(owner);LinearLayout panel=new LinearLayout(owner);panel.setOrientation(VERTICAL);panel.setPadding(dp(16),dp(10),dp(16),dp(10));review.addView(panel);
        TextView notice=new TextView(owner);notice.setText(getResources().getString(R.string.cloud_send_context,p.title,messages.size())+"\n\n"+advice+"\n\n"+getResources().getString(R.string.transfer_cloud_notice)+(p.remote()?"\n\n"+getResources().getString(R.string.cloud_pc_notice):""));
        notice.setTextSize(13);panel.addView(notice,new LinearLayout.LayoutParams(-1,-2));
        TextView content=new TextView(owner);content.setText(preview);content.setTextSize(13);content.setAutoLinkMask(0);content.setTextIsSelectable(false);content.setSaveEnabled(false);
        panel.addView(content,new LinearLayout.LayoutParams(-1,-2));
        showAction(new AlertDialog.Builder(owner).setTitle(p.title+" · "+getResources().getString(R.string.transfer_review))
            .setView(review)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.send,(d,w)->{
                if(target.isEmpty()||!target.equals(p.remote()?pcAddress:token)||!current(p,id,s,revision)||
                    !q.equals(prompt.getText().toString().trim())||Double.compare(ir,inputRate)!=0||
                    Double.compare(or,outputRate)!=0||Double.compare(limit,budget)!=0){
                    toast(R.string.cloud_context_changed);return;
                }
                try{if(!destination.equals(CloudApi.requestTarget(p,pcAddress))){toast(R.string.cloud_context_changed);return;}}
                catch(IllegalArgumentException e){toast(R.string.cloud_context_changed);return;}
                if(!ledger.canTrack(usageScope(p))){toast(R.string.api_overview_ledger_full);return;}
                CloudRequestAdvice.Estimate fresh=CloudRequestAdvice.estimate(p.remote(),messages,
                    CloudApi.OUTPUT_TOKEN_LIMIT,ir,or,limit,ledger.get(usageScope(p)));
                if(!fresh.allows()||!budgetAllows()){toast(R.string.cloud_budget_stop);return;}
                if(!sameEstimate(estimate,fresh)){toast(R.string.cloud_context_changed);return;}
                request(p,id,target,q,s,revision,messages);
            }).create());
    }
    private void request(CloudApi.Provider p,String id,String apiKey,String q,CloudHistory.Session s,long revision,List<CloudHistory.Message> messages){
        final String ledgerScope=usageScope(p);final double ir=p.remote()?-1:inputRate,or=p.remote()?-1:outputRate;
        s.draft(q);int request=++serial;viewportPolicy.reset();setBusy(true);status.setText(R.string.thinking);
        TextView question=new TextView(owner);question.setText(q);question.setTextSize(15);question.setPadding(dp(10),dp(10),dp(10),dp(10));transcript.addView(question,new LayoutParams(-1,-2));
        TextView partial=new TextView(owner);partial.setTextSize(15);partial.setPadding(dp(10),dp(10),dp(10),dp(10));partial.setAutoLinkMask(0);partial.setTextIsSelectable(true);transcript.addView(partial,new LayoutParams(-1,-2));
        try{activeRequest=io.submit(()->{
            HttpURLConnection c=null;CloudApi.Reply result=null;int err=R.string.cloud_fail;
            try{
                if(closed||request!=serial||Thread.currentThread().isInterrupted())return;
                c=p.remote()?CloudApi.remoteConnection(p,apiKey,true):CloudApi.connection(p,apiKey);connection=c;
                if(closed||request!=serial||Thread.currentThread().isInterrupted())return;
                result=CloudApi.streamReply(p,c,id,messages,value->owner.runOnUiThread(()->{if(!closed&&active&&request==serial){partial.setText(value);scheduleFollow();}}));
            }catch(Exception e){err=errorLabel(e);}finally{disconnect(c);if(connection==c)connection=null;}
            CloudApi.Reply output=result;final int failure=err;
            owner.runOnUiThread(()->{
                if(closed||request!=serial||!active)return;
                activeRequest=null;setBusy(false);
                if(!current(p,id,s,revision)){status.setText(R.string.cloud_context_changed);return;}
                if(output==null){ledger.record(ledgerScope,-1,-1,ir,or);String recovered=partial.getText().toString();render(false);prompt.setText(q);
                    if(!recovered.trim().isEmpty()){TextView fragment=new TextView(owner);fragment.setText(getResources().getString(R.string.chat_stream_incomplete)+"\n\n"+recovered);fragment.setTextIsSelectable(true);fragment.setAutoLinkMask(0);transcript.addView(fragment,new LayoutParams(-1,-2));}
                    status.setText(getResources().getString(failure)+"\n"+getResources().getString(R.string.chat_draft_recovered)+"\n"+getResources().getString(R.string.cloud_cancel_cost));return;}
                ledger.record(ledgerScope,output.inputTokens,output.outputTokens,ir,or);
                try{s.complete(q,output.text);prompt.setText("");status.setText(usageLabel(output));render(false);}
                catch(IllegalArgumentException e){status.setText(R.string.cloud_fail);}
            });
        });}catch(java.util.concurrent.RejectedExecutionException unavailable){
            activeRequest=null;setBusy(false);render(false);prompt.setText(q);status.setText(R.string.cloud_fail);
        }
    }
    private void cancel(){
        if(busy)ledger.record(usageScope(provider),-1,-1,-1,-1);
        serial++;
        java.util.concurrent.Future<?> task=activeRequest;activeRequest=null;
        if(task!=null)task.cancel(true);
        disconnectAsync(connection);
        setBusy(false);render(false);status.setText(R.string.cloud_cancel_cost);
    }
}
