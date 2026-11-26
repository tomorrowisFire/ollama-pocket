package com.ollamapocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Foreground-only manual knowledge snapshots; no inference or automatic ingestion. */
final class KnowledgeWorkspaceUi {
    private static final AtomicBoolean MODAL=new AtomicBoolean(),WORK=new AtomicBoolean();
    private final Activity owner;
    private final BooleanSupplier eligible;
    private final ProjectWorkspacePolicy.Session session=new ProjectWorkspacePolicy.Session();
    private boolean ownsModal;
    private AlertDialog dialog,confirmation;
    private LinearLayout body;
    private ScrollView scroll;
    private TextView notice;
    private ProjectTransactions.Snapshot<KnowledgeLibrary.State> snapshot;
    KnowledgeWorkspaceUi(Activity owner,BooleanSupplier eligible){this.owner=owner;this.eligible=eligible;}
    static boolean busy(){return MODAL.get()||WORK.get();}
    private boolean valid(long token){return session.valid(token)&&eligible.getAsBoolean()&&!owner.isFinishing()&&!owner.isDestroyed();}
    private String s(int id,Object... args){return owner.getString(id,args);}
    private int dp(int n){return Math.round(n*owner.getResources().getDisplayMetrics().density);}
    private void secure(AlertDialog d){if(d.getWindow()!=null)d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    void open(){open(null);}
    void open(AttachmentText.Result imported){
        if(!eligible.getAsBoolean()||WORK.get()||!MODAL.compareAndSet(false,true)){
            Toast.makeText(owner,R.string.busy,Toast.LENGTH_SHORT).show();return;
        }
        ownsModal=true;session.open();
        body=new LinearLayout(owner);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(8),dp(16),dp(16));
        scroll=new ScrollView(owner);scroll.setFillViewport(true);scroll.setSaveEnabled(false);scroll.addView(body);
        dialog=new AlertDialog.Builder(owner).setTitle(R.string.knowledge_workspace).setView(scroll).setCancelable(false).create();
        dialog.setOnDismissListener(d->{if(dialog==d)pause();});secure(dialog);dialog.show();secure(dialog);reload(imported);
    }
    void pause(){
        session.close();
        if(confirmation!=null){AlertDialog d=confirmation;confirmation=null;d.dismiss();}
        if(dialog!=null){AlertDialog d=dialog;dialog=null;d.dismiss();}
        snapshot=null;body=null;scroll=null;notice=null;
        if(ownsModal){ownsModal=false;MODAL.set(false);}
        // An already confirmed worker write can finish; its stale UI callback cannot run.
    }
    private void reset(){
        body.removeAllViews();notice=new TextView(owner);notice.setSaveEnabled(false);notice.setTextSize(14);body.addView(notice);
        ScrollView current=scroll;current.post(()->{if(scroll==current)current.scrollTo(0,0);});
    }
    private void label(String value,boolean selectable){
        TextView t=new TextView(owner);t.setText(value);t.setTextSize(14);t.setSaveEnabled(false);
        t.setAutoLinkMask(0);t.setTextIsSelectable(selectable);body.addView(t,new LinearLayout.LayoutParams(-1,-2));
    }
    private void status(int id){notice.setText(id);}
    private void button(String value,boolean enabled,Runnable action){
        Button b=new Button(owner);b.setText(value);b.setAllCaps(false);b.setMinHeight(dp(48));b.setEnabled(enabled);
        long token=session.token();
        b.setOnClickListener(v->{if(valid(token)&&!WORK.get()&&confirmation==null)action.run();});
        body.addView(b,new LinearLayout.LayoutParams(-1,-2));
    }
    private void button(int id,Runnable action){button(s(id),true,action);}
    private EditText field(int hint,String value,int max,boolean multiline){
        EditText e=new EditText(owner);e.setHint(hint);e.setContentDescription(s(hint));e.setSaveEnabled(false);e.setFreezesText(false);
        e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|
            (multiline?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));
        e.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        // Reject the entire oversized edit, including replacements, rather than clipping the source.
        e.setFilters(new InputFilter[]{(source,start,end,dest,dstart,dend)->{
            if(KnowledgeWorkspacePolicy.fitsEdit(dest.length(),dend-dstart,end-start,max))return null;
            if(notice!=null)status(R.string.knowledge_input_limit);
            return dest.subSequence(dstart,dend);
        }});
        e.setText(value);if(multiline){e.setMinLines(4);e.setMaxLines(10);}
        body.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;
    }
    private void confirm(int message,Runnable action){
        long token=session.token();
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(R.string.knowledge_workspace).setMessage(message)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok,(x,w)->{
                if(valid(token)&&!WORK.get())action.run();
            }).create();
        confirmation=d;d.setOnDismissListener(x->{if(confirmation==d)confirmation=null;});secure(d);d.show();secure(d);
    }
    private void lockControls(boolean locked){
        for(int i=0;i<body.getChildCount();i++){
            View v=body.getChildAt(i);
            if(v instanceof Button||v instanceof EditText){
                if(locked){v.setTag(Boolean.valueOf(v.isEnabled()));v.setEnabled(false);}
                else if(v.getTag() instanceof Boolean){v.setEnabled((Boolean)v.getTag());v.setTag(null);}
            }
        }
    }
    private interface Task<T>{T run()throws Exception;}
    private <T> void work(Task<T> task,Consumer<T> success,Consumer<Exception> failure){
        long token=session.token();
        if(!valid(token)||!WORK.compareAndSet(false,true))return;
        lockControls(true);status(R.string.project_working);
        Thread worker=new Thread(()->{
            T value=null;Exception error=null;
            try{value=task.run();}catch(Exception e){error=e;}
            final T result=value;final Exception problem=error;
            owner.runOnUiThread(()->{
                WORK.set(false);if(!valid(token))return;
                lockControls(false);if(problem==null)success.accept(result);else failure.accept(problem);
            });
        },"onhand-knowledge-store");
        try{worker.start();}catch(RuntimeException e){WORK.set(false);lockControls(false);failure.accept(e);}
    }
    private void reload(){reload(null);}
    private void reload(AttachmentText.Result imported){
        reset();snapshot=null;
        work(()->new KnowledgeStore(owner).read(),saved->{
            snapshot=saved;
            if(imported==null){root();return;}
            try{reviewImport(KnowledgeImportPolicy.review(snapshot.value,UUID.randomUUID().toString(),imported,imported.text));}
            catch(IllegalArgumentException|ArithmeticException e){root();status(R.string.project_invalid);}
        },error->blocked(R.string.project_unreadable));
    }
    private void reviewImport(KnowledgeImportPolicy.Review reviewed){
        reset();label(s(R.string.knowledge_import_notice),false);label(reviewed.title,true);
        label(s(R.string.knowledge_id,reviewed.id),true);showOrigin(reviewed.origin);label(reviewed.text,true);
        button(R.string.project_save,()->save(reviewed::apply));
        button(R.string.project_back,this::root);button(R.string.close,this::pause);
    }
    private void showOrigin(KnowledgeLibrary.Origin o){
        if(o==null){label(s(R.string.knowledge_manual),false);return;}
        label(s(R.string.knowledge_import_notice),false);label(o.name,true);
        switch(o.kind){
            case TEXT:case OFFICE:
                if(o.kind==KnowledgeLibrary.Kind.OFFICE)label(s(R.string.attachment_office_note),false);
                label(s(R.string.attachment_range_note,o.segment,o.first,o.last),false);break;
            case PDF_TEXT:
                label(s(R.string.attachment_pdf_note,o.first,o.last,o.total,o.readable),false);break;
            case PDF_OCR:
                label(s(R.string.attachment_ocr_pdf_page,o.first,o.total),false);
                // fall through: both OCR origins have recognition limitations
            case IMAGE_OCR:
                label(s(R.string.attachment_ocr_note),false);break;
        }
        if(o.partial)label(s(R.string.attachment_partial),false);
    }
    private void blocked(int error){
        snapshot=null;reset();status(error);button(R.string.project_reload,this::reload);button(R.string.close,this::pause);
    }
    private void save(ProjectTransactions.Mutation<KnowledgeLibrary.State> mutation){
        if(snapshot==null)return;
        final ProjectTransactions.Snapshot<KnowledgeLibrary.State> reviewed=snapshot;
        work(()->new KnowledgeStore(owner).commit(reviewed,mutation),saved->{snapshot=saved;root();status(R.string.project_saved);},error->{
            ProjectWorkspacePolicy.Outcome outcome=ProjectWorkspacePolicy.failure(error);
            if(ProjectWorkspacePolicy.reloadRequired(outcome)){
                blocked(outcome==ProjectWorkspacePolicy.Outcome.STALE?R.string.project_stale:
                    outcome==ProjectWorkspacePolicy.Outcome.UNCERTAIN?R.string.project_uncertain:R.string.project_unreadable);
            }else status(outcome==ProjectWorkspacePolicy.Outcome.NO_CHANGE?R.string.project_no_change:R.string.project_invalid);
        });
    }
    private void root(){
        if(snapshot==null)return;
        reset();KnowledgeLibrary.State state=snapshot.value;
        label(s(R.string.knowledge_usage,state.sources.size(),KnowledgeLibrary.MAX_SOURCES,snapshot.storedByteCount(),KnowledgeLibrary.MAX_BYTES),false);
        label(s(R.string.knowledge_notice),false);
        button(s(R.string.knowledge_add),state.sources.size()<KnowledgeLibrary.MAX_SOURCES,()->editor("",""));
        button(s(R.string.knowledge_search),!state.sources.isEmpty(),()->searchEditor(""));
        if(state.sources.isEmpty())label(s(R.string.knowledge_empty),false);
        for(KnowledgeLibrary.Source source:state.sources)button(source.title+" · "+source.id,true,()->showSource(source,this::root));
        button(R.string.project_reload,this::reload);button(R.string.close,this::pause);
    }
    private void editor(String title,String text){
        reset();label(s(R.string.project_draft_notice),false);label(s(R.string.knowledge_manual),false);
        EditText titleField=field(R.string.project_title,title,80,false);
        EditText textField=field(R.string.knowledge_text,text,KnowledgeLibrary.MAX_TEXT,true);
        button(R.string.knowledge_review,()->{
            String t=titleField.getText().toString(),v=textField.getText().toString();
            try{review(KnowledgeWorkspacePolicy.review(snapshot.value,UUID.randomUUID().toString(),t,v),t,v);}
            catch(IllegalArgumentException error){status(R.string.project_invalid);}
        });
        button(R.string.project_back,()->{
            if(titleField.getText().length()!=0||textField.getText().length()!=0)confirm(R.string.project_discard_message,this::root);
            else root();
        });
        button(R.string.close,()->{
            if(titleField.getText().length()!=0||textField.getText().length()!=0)confirm(R.string.project_discard_message,this::pause);
            else pause();
        });
    }
    private void review(KnowledgeWorkspacePolicy.Review reviewed,String originalTitle,String originalText){
        reset();label(s(R.string.knowledge_review_notice),false);label(reviewed.title,true);label(s(R.string.knowledge_id,reviewed.id),true);
        label(reviewed.text,true);
        button(R.string.project_save,()->save(reviewed::apply));
        button(R.string.project_back,()->editor(originalTitle,originalText));
        button(R.string.close,()->confirm(R.string.project_discard_message,this::pause));
    }
    private void showSource(KnowledgeLibrary.Source source,Runnable back){
        reset();label(source.title,true);label(s(R.string.knowledge_id,source.id),true);showOrigin(source.origin);label(source.text,true);
        button(R.string.project_delete,()->confirm(R.string.knowledge_delete_notice,()->save(st->KnowledgeLibrary.remove(st,source.id))));
        button(R.string.project_back,back);button(R.string.close,this::pause);
    }
    private void searchEditor(String previous){
        reset();label(s(R.string.knowledge_search_notice),false);
        EditText query=field(R.string.knowledge_query,previous,KnowledgeLibrary.MAX_QUERY,false);
        button(R.string.knowledge_search,()->{
            final String exact=query.getText().toString();
            final KnowledgeLibrary.State state=snapshot.value;
            work(()->KnowledgeLibrary.search(state,exact),hits->showHits(state,exact,hits),error->status(R.string.project_invalid));
        });
        button(R.string.project_back,this::root);button(R.string.close,this::pause);
    }
    private void showHits(KnowledgeLibrary.State state,String query,List<KnowledgeLibrary.Hit> hits){
        reset();label(s(R.string.knowledge_search_notice),false);label(s(R.string.knowledge_hits,hits.size(),KnowledgeLibrary.MAX_HITS),false);
        for(KnowledgeLibrary.Hit hit:hits){
            label(hit.title,true);label(s(R.string.knowledge_id,hit.id),true);
            label(s(R.string.knowledge_range,hit.start,hit.end),false);label(hit.match,true);
            button(R.string.knowledge_open_source,()->showSource(state.source(hit.id),()->showHits(state,query,hits)));
        }
        button(R.string.project_back,()->searchEditor(query));button(R.string.close,this::pause);
    }
}
