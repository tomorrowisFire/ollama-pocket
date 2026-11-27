package com.ollamapocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
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
    private static final AtomicBoolean MODAL=new AtomicBoolean();
    private static final ProjectWorkGate WORK=new ProjectWorkGate();
    private final Activity owner;
    private final WorkspaceDictation dictation;
    private final WorkspaceReadAloud reading;
    private final WorkspaceTextViewer viewer;
    private final BooleanSupplier eligible,answerEligible;
    private final String answerModel;
    private volatile DocumentBatchRunner answerRunner;
    private final ProjectWorkspacePolicy.Session session=new ProjectWorkspacePolicy.Session();
    private boolean ownsModal;
    private AlertDialog dialog,confirmation;
    private LinearLayout body;
    private ScrollView scroll;
    private TextView notice;
    private ProjectTransactions.Snapshot<KnowledgeLibrary.State> snapshot;
    KnowledgeWorkspaceUi(Activity owner,BooleanSupplier eligible){this(owner,eligible,"",()->false);}
    KnowledgeWorkspaceUi(Activity owner,BooleanSupplier eligible,String model,BooleanSupplier answerEligible){
        this.owner=owner;this.eligible=eligible;this.answerModel=model;this.answerEligible=answerEligible;this.dictation=new WorkspaceDictation(owner);this.reading=new WorkspaceReadAloud(owner);this.viewer=new WorkspaceTextViewer(owner);
    }
    static boolean busy(){return MODAL.get()||WORK.busy();}
    private boolean valid(long token){return session.valid(token)&&eligible.getAsBoolean()&&!owner.isFinishing()&&!owner.isDestroyed();}
    private String s(int id,Object... args){return owner.getString(id,args);}
    private int dp(int n){return Math.round(n*owner.getResources().getDisplayMetrics().density);}
    private void secure(AlertDialog d){if(d.getWindow()!=null)d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    void open(){open(null);}
    void open(AttachmentText.Result imported){
        if(!eligible.getAsBoolean()||WORK.busy()||!MODAL.compareAndSet(false,true)){
            Toast.makeText(owner,R.string.busy,Toast.LENGTH_SHORT).show();return;
        }
        ownsModal=true;session.open();
        body=new LinearLayout(owner);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(8),dp(16),dp(16));
        scroll=new ScrollView(owner);scroll.setFillViewport(true);scroll.setSaveEnabled(false);scroll.addView(body);
        dialog=new AlertDialog.Builder(owner).setTitle(R.string.knowledge_workspace).setView(scroll).setCancelable(false).create();
        dialog.setOnDismissListener(d->{if(dialog==d)pause();});secure(dialog);dialog.show();secure(dialog);WorkbenchWindow.fit(owner,dialog);reload(imported);
    }
    void pause(){
        viewer.cancel();reading.cancel();dictation.cancel();
        session.close();
        DocumentBatchRunner active=answerRunner;answerRunner=null;if(active!=null)active.cancel();
        if(confirmation!=null){AlertDialog d=confirmation;confirmation=null;d.dismiss();}
        if(dialog!=null){AlertDialog d=dialog;dialog=null;d.dismiss();}
        snapshot=null;body=null;scroll=null;notice=null;
        if(ownsModal){ownsModal=false;MODAL.set(false);}
        // An already confirmed worker write can finish; its stale UI callback cannot run.
    }
    private void reset(){
        viewer.cancel();reading.cancel();dictation.cancel();
        body.removeAllViews();notice=new TextView(owner);notice.setSaveEnabled(false);WorkbenchStyle.label(owner,notice,14,false,false);
        notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(notice);
        final ScrollView current=scroll;final TextView screen=notice;
        current.post(()->{if(scroll==current&&notice==screen)current.scrollTo(0,0);});
    }
    private void label(String value,boolean selectable){
        TextView t=new TextView(owner);t.setText(value);WorkbenchStyle.label(owner,t,14,false,false);t.setSaveEnabled(false);
        t.setAutoLinkMask(0);t.setTextIsSelectable(selectable);body.addView(t,WorkbenchStyle.row(owner));
    }
    private void status(int id){notice.setText(id);}
    private void button(String value,boolean enabled,Runnable action){
        button(value,enabled,action,WorkbenchVisualPolicy.Role.SECONDARY);
    }
    private void button(String value,boolean enabled,Runnable action,WorkbenchVisualPolicy.Role role){
        Button b=new Button(owner);b.setText(value);WorkbenchStyle.button(owner,b,role);b.setEnabled(enabled);
        final long token=session.token();final TextView screen=notice;
        b.setOnClickListener(v->{if(valid(token)&&notice==screen&&b.isAttachedToWindow()&&b.isEnabled()&&!WORK.busy()&&confirmation==null)action.run();});
        body.addView(b,WorkbenchStyle.row(owner));
    }
    private void button(int id,Runnable action){button(s(id),true,action,WorkbenchStyle.role(id));}
    private EditText field(int hint,String value,int max,boolean multiline){
        EditText e=new EditText(owner);e.setHint(hint);e.setContentDescription(s(hint));e.setSaveEnabled(false);e.setFreezesText(false);
        e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|
            (multiline?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));
        e.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        final long token=session.token();final TextView screen=notice;
        // Reject the entire oversized edit, including replacements, rather than clipping the source.
        e.setFilters(new InputFilter[]{(source,start,end,dest,dstart,dend)->{
            if(KnowledgeWorkspacePolicy.fitsEdit(dest.length(),dend-dstart,end-start,max))return null;
            if(valid(token)&&notice==screen)status(R.string.knowledge_input_limit);
            return dest.subSequence(dstart,dend);
        }});
        e.setText(value);if(multiline){e.setMinLines(4);e.setMaxLines(10);}
        WorkbenchStyle.field(owner,e);
        body.addView(e,WorkbenchStyle.row(owner));
        dictation.add(body,e,hint,max,multiline,()->valid(token)&&notice==screen&&e.isAttachedToWindow()&&!WORK.busy()&&confirmation==null&&dialog!=null&&dialog.isShowing());
        return e;
    }
    private void confirm(int message,Runnable action){
        final long token=session.token();final TextView screen=notice;
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(R.string.knowledge_workspace).setMessage(message)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok,(x,w)->{
                if(valid(token)&&notice==screen&&confirmation==x&&!WORK.busy())action.run();
            }).create();
        confirmation=d;d.setOnDismissListener(x->{if(confirmation==d)confirmation=null;});secure(d);d.show();secure(d);WorkbenchWindow.fitCompact(owner,d);
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
    private <T> boolean work(ProjectWorkGate.Task<T> task,Consumer<T> success,Consumer<Exception> failure){
        final long token=session.token();final TextView screen=notice;
        if(!valid(token))return false;
        final ProjectWorkGate.Lease lease=WORK.acquire();
        if(lease==null)return false;
        try{
            viewer.cancel();reading.cancel();dictation.cancel();lockControls(true);status(R.string.project_working);
            final Handler main=new Handler(Looper.getMainLooper());
            Thread worker=new Thread(()->{
                if(!lease.execute(task,main::post,(result,problem)->{
                    if(!valid(token)||notice!=screen)return;
                    lockControls(false);
                    if(problem==null)success.accept(result);else failure.accept(problem);
                }))android.util.Log.w("OnHandAI","Knowledge completion not delivered");
            },"onhand-knowledge-store");
            worker.start();return true;
        }catch(RuntimeException e){
            if(lease.release()&&valid(token)&&notice==screen){lockControls(false);failure.accept(e);}
            return false;
        }catch(Error fatal){lease.release();throw fatal;}
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
        if(!answerModel.isEmpty())button(s(R.string.knowledge_ask),!state.sources.isEmpty()&&answerEligible.getAsBoolean(),()->answerEditor("",""));
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
        reset();label(source.title,true);label(s(R.string.knowledge_id,source.id),true);showOrigin(source.origin);
        readText(R.string.knowledge_text,source.text);label(source.text,true);
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
    private void answerEditor(String previousQuery,String previousQuestion){
        reset();label(s(R.string.knowledge_answer_notice),false);label(s(R.string.model_label)+": "+answerModel,false);
        EditText query=field(R.string.knowledge_query,previousQuery,KnowledgeLibrary.MAX_QUERY,false);
        EditText question=field(R.string.knowledge_question,previousQuestion,KnowledgeAnswerPolicy.MAX_QUESTION,true);
        button(R.string.knowledge_review,()->{
            if(!answerEligible.getAsBoolean()){status(R.string.busy);return;}
            final String q=query.getText().toString(),goal=question.getText().toString();
            final KnowledgeLibrary.State state=snapshot.value;
            work(()->KnowledgeAnswerPolicy.plan(state,q,goal),plan->{
                if(plan.excerpts.isEmpty()){status(R.string.knowledge_no_evidence);return;}
                answerReview(plan);
            },error->status(R.string.project_invalid));
        });
        button(R.string.project_back,this::root);button(R.string.close,this::pause);
    }
    private void evidence(KnowledgeAnswerPolicy.Plan plan){
        label(s(R.string.knowledge_evidence_count,plan.includedSources,plan.matchingSources,KnowledgeAnswerPolicy.MAX_SOURCES),false);
        label(s(R.string.knowledge_excerpt_count,plan.excerpts.size(),KnowledgeAnswerPolicy.MAX_EXCERPTS),false);
        for(KnowledgeAnswerPolicy.Excerpt e:plan.excerpts){
            label("["+e.marker+"] "+e.title,true);label(s(R.string.knowledge_id,e.sourceId),true);
            label(s(R.string.knowledge_range,e.start,e.end),false);showOrigin(e.origin);label(e.text,true);
        }
    }
    private void answerReview(KnowledgeAnswerPolicy.Plan plan){
        reset();label(s(R.string.knowledge_answer_notice),false);label(s(R.string.model_label)+": "+answerModel,false);
        evidence(plan);label(s(R.string.knowledge_exact_prompt),false);label(plan.prompt,true);
        button(R.string.knowledge_generate,()->runAnswer(plan));
        button(R.string.project_back,()->answerEditor(plan.query,plan.question));button(R.string.close,this::pause);
    }
    private void runAnswer(KnowledgeAnswerPolicy.Plan plan){
        if(!answerEligible.getAsBoolean()){status(R.string.busy);return;}
        if(!OllamaForegroundService.isRunning()){status(R.string.test_need_server);return;}
        if(!"off".equals(CoopSession.get(owner).role())){status(R.string.guest_stop_first);return;}
        reset();label(s(R.string.knowledge_answer_notice),false);
        final DocumentBatchRunner active=new DocumentBatchRunner(owner,answerModel);answerRunner=active;
        final long token=session.token();final TextView screen=notice;
        Button stop=new Button(owner);stop.setText(R.string.stop);WorkbenchStyle.button(owner,stop,R.string.stop);
        stop.setOnClickListener(v->{if(valid(token)&&notice==screen&&stop.isAttachedToWindow()&&stop.isEnabled()&&answerRunner==active){active.cancel();stop.setEnabled(false);status(R.string.auto_pause_requested);}});
        body.addView(stop,new LinearLayout.LayoutParams(-1,-2));
        boolean started=work(()->{
            if(!plan.stillAvailable(new KnowledgeStore(owner).read().value))
                throw new ProjectTransactions.TransactionFailure(ProjectTransactions.Failure.STALE,null);
            return active.runKnowledge(plan);
        },answer->{
            answerRunner=null;
            if(active.reason()!=DocumentBatchRunner.Stop.NONE||!answerEligible.getAsBoolean()){answerReview(plan);status(R.string.batch_stopped);return;}
            answerResult(plan,answer);
        },error->{
            answerRunner=null;
            if(error instanceof ProjectTransactions.TransactionFailure){blocked(R.string.project_stale);return;}
            answerReview(plan);status(active.reason()==DocumentBatchRunner.Stop.NONE?R.string.batch_failed:R.string.batch_stopped);
        });
        if(started&&valid(token)&&notice==screen&&answerRunner==active)stop.setEnabled(true);
        else if(answerRunner==active){answerRunner=null;active.cancel();}
    }
    private void answerResult(KnowledgeAnswerPolicy.Plan plan,KnowledgeAnswerPolicy.Answer answer){
        reset();label(s(R.string.knowledge_citation_notice),false);label(s(R.string.model_label)+": "+answerModel,false);
        String citations=s(R.string.knowledge_citation_ids,answer.known.isEmpty()?"—":String.join(", ",answer.known),
            answer.unknown.isEmpty()?"—":String.join(", ",answer.unknown));
        readText(R.string.workspace_read_answer,s(R.string.knowledge_citation_notice)+"\n"+s(R.string.model_label)+": "+answerModel+"\n"+answer.text+"\n"+citations);
        label(answer.text,true);label(citations,false);
        evidence(plan);button(R.string.project_back,()->answerEditor(plan.query,plan.question));button(R.string.close,this::pause);
    }
    private void readText(int title,String value){
        final long token=session.token();final TextView screen=notice;
        BooleanSupplier guard=()->valid(token)&&notice==screen&&!WORK.busy()&&confirmation==null&&dialog!=null&&dialog.isShowing();
        viewer.add(body,title,()->value,guard);reading.add(body,title,()->value,guard);
    }
    private void showHits(KnowledgeLibrary.State state,String query,List<KnowledgeLibrary.Hit> hits){
        reset();label(s(R.string.knowledge_search_notice),false);label(s(R.string.knowledge_hits,hits.size(),KnowledgeLibrary.MAX_HITS),false);
        final long token=session.token();final TextView screen=notice;
        if(!answerModel.isEmpty()&&!hits.isEmpty())
            button(s(R.string.knowledge_ask),answerEligible.getAsBoolean(),()->answerEditor(query,""));
        for(KnowledgeLibrary.Hit hit:hits){
            final KnowledgeLibrary.Source source=state.source(hit.id);
            label(hit.title,true);label(s(R.string.knowledge_id,hit.id),true);
            label(s(R.string.knowledge_range,hit.start,hit.end),false);label(hit.match,true);
            BooleanSupplier guard=()->valid(token)&&notice==screen&&!WORK.busy()&&confirmation==null&&dialog!=null&&dialog.isShowing()&&
                snapshot!=null&&snapshot.value==state;
            viewer.addMatch(body,R.string.knowledge_text,()->source.text,guard,hit.match,hit.start-1,hit.end);
            button(R.string.knowledge_open_source,()->showSource(source,()->showHits(state,query,hits)));
        }
        button(R.string.project_back,()->searchEditor(query));button(R.string.close,this::pause);
    }
}
