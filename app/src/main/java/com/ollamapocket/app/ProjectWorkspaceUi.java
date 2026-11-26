package com.ollamapocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.text.DateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/** Foreground-only private workspace. Exact reviewed snapshot, explicit writes, no inference. */
final class ProjectWorkspaceUi {
    private static final AtomicBoolean MODAL=new AtomicBoolean(),WORK=new AtomicBoolean();
    private final Activity owner;
    private final WorkspaceDictation dictation;
    private final WorkspaceReadAloud reading;
    private final BooleanSupplier eligible;
    private final ProjectWorkspacePolicy.Session session=new ProjectWorkspacePolicy.Session();
    private ProjectTransactions.Snapshot<ProjectLibrary.State> snapshot;
    private ProjectWorkspacePolicy.Selection selection=ProjectWorkspacePolicy.Selection.root();
    private AlertDialog dialog,confirmation;
    private LinearLayout body;
    private ScrollView scroll;
    private TextView notice;
    private EditText titleField,textField;
    private String oldTitle="",oldText="";
    private boolean ownsModal,reloadRequired,reportSaved;
    private ProjectReportHandoff report;
    private String reportTitle;
    private Function<ProjectContext,String> contextPreview;
    private Consumer<String> contextApply;
    private ProjectLibrary.Link currentLink;
    private Function<ProjectLibrary.Link,ProjectChatLinkPolicy.Status> linkResolver;
    private Consumer<ProjectLibrary.Link> linkOpener;
    ProjectWorkspaceUi(Activity owner,BooleanSupplier eligible){this.owner=owner;this.eligible=eligible;this.dictation=new WorkspaceDictation(owner);this.reading=new WorkspaceReadAloud(owner);}
    static boolean busy(){return MODAL.get()||WORK.get();}
    private boolean valid(long token){return session.valid(token)&&eligible.getAsBoolean()&&!owner.isFinishing()&&!owner.isDestroyed();}
    private String s(int id,Object... args){return owner.getString(id,args);}
    private int dp(int n){return Math.round(n*owner.getResources().getDisplayMetrics().density);}
    private void secure(AlertDialog d){if(d.getWindow()!=null)d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    void open(){openInternal(null,null,null,null,null,null,null);}
    void openContext(Function<ProjectContext,String> preview,Consumer<String> apply,ProjectLibrary.Link current,
            Function<ProjectLibrary.Link,ProjectChatLinkPolicy.Status> resolver,Consumer<ProjectLibrary.Link> opener){
        openInternal(null,null,preview,apply,current,resolver,opener);
    }
    void openReport(ProjectReportHandoff handoff,String title){openInternal(handoff,title,null,null,null,null,null);}
    private void openInternal(ProjectReportHandoff handoff,String title,Function<ProjectContext,String> preview,Consumer<String> apply,
            ProjectLibrary.Link current,Function<ProjectLibrary.Link,ProjectChatLinkPolicy.Status> resolver,Consumer<ProjectLibrary.Link> opener){
        if(!eligible.getAsBoolean()||WORK.get()||!MODAL.compareAndSet(false,true)){
            Toast.makeText(owner,R.string.busy,Toast.LENGTH_SHORT).show();return;
        }
        contextPreview=preview;contextApply=apply;currentLink=current;linkResolver=resolver;linkOpener=opener;
        ownsModal=true;report=handoff;reportTitle=title;reportSaved=false;session.open();selection=ProjectWorkspacePolicy.Selection.root();
        body=new LinearLayout(owner);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(8),dp(16),dp(16));
        scroll=new ScrollView(owner);scroll.setFillViewport(true);scroll.setSaveEnabled(false);scroll.addView(body);
        dialog=new AlertDialog.Builder(owner).setTitle(R.string.project_workspace).setView(scroll).setCancelable(false).create();
        dialog.setOnDismissListener(d->{if(dialog==d)pause();});
        secure(dialog);dialog.show();secure(dialog);reload();
    }
    void pause(){
        reading.cancel();dictation.cancel();
        session.close();
        if(confirmation!=null){AlertDialog d=confirmation;confirmation=null;d.dismiss();}
        if(dialog!=null){AlertDialog d=dialog;dialog=null;d.dismiss();}
        titleField=null;textField=null;oldTitle="";oldText="";snapshot=null;body=null;scroll=null;notice=null;reloadRequired=false;
        report=null;reportTitle=null;reportSaved=false;contextPreview=null;contextApply=null;currentLink=null;linkResolver=null;linkOpener=null;
        if(ownsModal){ownsModal=false;MODAL.set(false);}
        // An explicitly dispatched write can finish; WORK stays held until callback.
    }
    private void label(String value,boolean selectable){
        TextView t=new TextView(owner);t.setText(value);t.setTextSize(14);t.setSaveEnabled(false);
        t.setAutoLinkMask(0);t.setTextIsSelectable(selectable);body.addView(t,new LinearLayout.LayoutParams(-1,-2));
    }
    private void button(String value,boolean enabled,Runnable run){
        Button b=new Button(owner);b.setAllCaps(false);b.setText(value);b.setMinHeight(dp(48));b.setEnabled(enabled);
        final long token=session.token();
        b.setOnClickListener(v->{if(valid(token)&&!WORK.get()&&confirmation==null)run.run();});
        body.addView(b,new LinearLayout.LayoutParams(-1,-2));
    }
    private void button(int id,Runnable run){button(s(id),true,run);}
    private EditText field(int hint,String value,int max,boolean multi){
        EditText e=new EditText(owner);e.setHint(hint);e.setContentDescription(s(hint));e.setSaveEnabled(false);e.setFreezesText(false);
        e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        e.setInputType(InputType.TYPE_CLASS_TEXT|(multi?InputType.TYPE_TEXT_FLAG_MULTI_LINE:InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        e.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});
        e.setText(value);if(multi){e.setMinLines(4);e.setMaxLines(10);}
        body.addView(e,new LinearLayout.LayoutParams(-1,-2));
        final long token=session.token();
        dictation.add(body,e,hint,max,multi,()->valid(token)&&!WORK.get()&&confirmation==null&&dialog!=null&&dialog.isShowing());
        return e;
    }
    private void reset(){
        reading.cancel();dictation.cancel();
        body.removeAllViews();titleField=null;textField=null;oldTitle="";oldText="";reloadRequired=false;
        notice=new TextView(owner);notice.setSaveEnabled(false);notice.setTextSize(14);body.addView(notice);
        final ScrollView current=scroll;current.post(()->{if(scroll==current)current.scrollTo(0,0);});
    }
    private void status(int id){notice.setText(id);}
    private void confirm(int title,int message,Runnable action){
        final long token=session.token();
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(title).setMessage(message)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok,(x,w)->{
                if(valid(token)&&!WORK.get())action.run();
            }).create();
        confirmation=d;d.setOnDismissListener(x->{if(confirmation==d)confirmation=null;});secure(d);d.show();secure(d);
    }
    private boolean dirty(){
        return ProjectWorkspacePolicy.dirty(oldTitle,oldText,titleField==null?oldTitle:titleField.getText().toString(),
            textField==null?oldText:textField.getText().toString());
    }
    private void leave(Runnable action){
        if(dirty())confirm(R.string.project_discard,R.string.project_discard_message,action);else action.run();
    }
    private void nav(){
        if(selection.project!=null)button(R.string.project_back,()->{selection=selection.parent();render();});
        button(R.string.close,()->leave(this::pause));
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
        final long token=session.token();
        if(!valid(token)||!WORK.compareAndSet(false,true))return;
        reading.cancel();dictation.cancel();lockControls(true);status(R.string.project_working);
        Thread worker=new Thread(()->{
            T value=null;Exception error=null;
            try{value=task.run();}catch(Exception e){error=e;}
            final T result=value;final Exception problem=error;
            owner.runOnUiThread(()->{
                WORK.set(false);
                if(!valid(token))return;
                lockControls(false);
                if(problem==null)success.accept(result);else failure.accept(problem);
            });
        },"onhand-project-store");
        try{worker.start();}catch(RuntimeException e){WORK.set(false);lockControls(false);failure.accept(e);}
    }
    private void reload(){
        reset();snapshot=null;
        work(()->new ProjectStore(owner).read(),saved->{snapshot=saved;selection=selection.resolve(saved.value);render();},error->{
            reset();status(R.string.project_unreadable);button(R.string.retry,this::reload);button(R.string.close,this::pause);
        });
    }
    private void save(ProjectTransactions.Mutation<ProjectLibrary.State> mutation,ProjectWorkspacePolicy.Selection destination){
        if(reloadRequired||snapshot==null)return;
        final ProjectTransactions.Snapshot<ProjectLibrary.State> reviewed=snapshot;
        work(()->new ProjectStore(owner).commit(reviewed,mutation),saved->{
            snapshot=saved;selection=destination.resolve(saved.value);if(report!=null)reportSaved=true;render();status(R.string.project_saved);
        },error->{
            ProjectWorkspacePolicy.Outcome outcome=ProjectWorkspacePolicy.failure(error);
            if(ProjectWorkspacePolicy.reloadRequired(outcome)){
                reloadRequired=true;snapshot=null;
                status(outcome==ProjectWorkspacePolicy.Outcome.STALE?R.string.project_stale:
                    outcome==ProjectWorkspacePolicy.Outcome.UNCERTAIN?R.string.project_uncertain:R.string.project_unreadable);
                // Keep draft visible but invalidate reviewed actions. Explicit reload only.
                for(int i=0;i<body.getChildCount();i++)if(body.getChildAt(i) instanceof Button)body.getChildAt(i).setEnabled(false);
                if(report==null)button(R.string.project_reload,()->leave(this::reload));
                button(R.string.close,()->leave(this::pause));
            }else status(outcome==ProjectWorkspacePolicy.Outcome.NO_CHANGE?R.string.project_no_change:R.string.project_invalid);
        });
    }
    private void render(){
        if(snapshot==null){reload();return;}
        selection=selection.resolve(snapshot.value);reset();
        ProjectLibrary.State state=snapshot.value;
        final String pid=selection.project,aid=selection.artifact;
        if(report!=null){renderReport(state,pid);return;}
        if(pid==null){
            label(s(R.string.project_usage,state.projects.size(),ProjectLibrary.MAX_PROJECTS,snapshot.storedByteCount(),ProjectLibrary.MAX_BYTES),false);
            label(s(R.string.project_limits),false);
            if(state.projects.isEmpty())label(s(R.string.project_empty),false);
            button(s(R.string.project_new),state.projects.size()<ProjectLibrary.MAX_PROJECTS,()->projectEditor(null));
            for(ProjectLibrary.Project p:state.projects)button(p.title,true,()->{selection=new ProjectWorkspacePolicy.Selection(p.id,null,0);render();});
        }else{
            ProjectLibrary.Project p=state.project(pid);label(p.title,true);
            if(aid==null){
                label(s(R.string.project_instructions),false);label(p.instructions,true);
                readText(R.string.project_instructions,p.instructions);
                contextButton(state,p.id,null,0);
                linkButtons(p);
                button(R.string.project_edit,()->projectEditor(p));
                button(s(R.string.project_new_artifact),p.artifacts.size()<ProjectLibrary.MAX_ARTIFACTS,()->artifactEditor(p,null,false));
                if(p.artifacts.isEmpty())label(s(R.string.project_empty_artifacts),false);
                for(ProjectLibrary.Artifact a:p.artifacts)button(a.title,true,()->{selection=new ProjectWorkspacePolicy.Selection(pid,a.id,0);render();});
                final ProjectWorkspacePolicy.Selection dest=selection.parent();
                button(R.string.project_delete,()->confirm(R.string.project_delete,R.string.project_delete_project_message,
                    ()->save(st->ProjectLibrary.deleteProject(st,pid),dest)));
            }else{
                ProjectLibrary.Artifact a=p.artifact(aid);label(a.title,true);
                if(selection.ordinal>0){
                    final int ordinal=selection.ordinal;
                    version(a.version(ordinal));contextButton(state,p.id,a.id,ordinal);label(s(R.string.project_restore_note),false);
                    button(s(R.string.project_restore),ProjectWorkspacePolicy.canRestore(a,ordinal),
                        ()->confirm(R.string.project_restore,R.string.project_restore_message,
                            ()->save(st->ProjectLibrary.restoreArtifact(st,pid,aid,ordinal,ProjectWorkspacePolicy.savedAt(a,System.currentTimeMillis())),
                                new ProjectWorkspacePolicy.Selection(pid,aid,0))));
                }else{
                    version(a.current());contextButton(state,p.id,a.id,a.current().ordinal);
                    button(R.string.project_rename,()->artifactEditor(p,a,true));
                    button(s(R.string.project_new_version),ProjectWorkspacePolicy.canAppend(a),()->artifactEditor(p,a,false));
                    if(!ProjectWorkspacePolicy.canAppend(a))label(s(R.string.project_version_limit),false);
                    for(ProjectLibrary.Version v:a.versions)button(s(R.string.project_version,v.ordinal),true,()->{selection=new ProjectWorkspacePolicy.Selection(pid,aid,v.ordinal);render();});
                    final ProjectWorkspacePolicy.Selection dest=selection.parent();
                    button(R.string.project_delete,()->confirm(R.string.project_delete,R.string.project_delete_artifact_message,
                        ()->save(st->ProjectLibrary.deleteArtifact(st,pid,aid),dest)));
                }
            }
        }
        nav();
    }
    private int linkStatus(ProjectChatLinkPolicy.Status state){
        switch(state){
            case AVAILABLE:return R.string.project_link_available;
            case MISSING:return R.string.project_link_missing;
            case MODEL_UNAVAILABLE:return R.string.project_link_model_missing;
            default:return R.string.project_link_unreadable;
        }
    }
    private ProjectChatLinkPolicy.Status resolveLink(ProjectLibrary.Link link){
        if(linkResolver==null)return ProjectChatLinkPolicy.Status.UNREADABLE;
        try{return linkResolver.apply(link);}catch(RuntimeException e){return ProjectChatLinkPolicy.Status.UNREADABLE;}
    }
    private String identity(ProjectLibrary.Link link){return link.model+" / "+link.session;}
    private void linkButtons(ProjectLibrary.Project project){
        label(s(R.string.project_links_heading,project.links.size(),ProjectLibrary.MAX_LINKS),false);
        label(s(R.string.project_link_notice),false);
        final ProjectLibrary.Link current=currentLink;
        boolean add=current!=null&&!project.links.contains(current)&&project.links.size()<ProjectLibrary.MAX_LINKS&&
            resolveLink(current)==ProjectChatLinkPolicy.Status.AVAILABLE;
        button(s(R.string.project_link_add),add,()->reviewAddLink(project,current));
        if(current==null||resolveLink(current)!=ProjectChatLinkPolicy.Status.AVAILABLE)label(s(R.string.project_link_current_unavailable),false);
        for(ProjectLibrary.Link link:project.links)button(identity(link),true,()->reviewLink(project,link));
    }
    private void reviewAddLink(ProjectLibrary.Project project,ProjectLibrary.Link link){
        reset();label(project.title,false);label(identity(link),false);label(s(R.string.project_link_notice),false);
        button(R.string.project_link_add,()->confirm(R.string.project_link_add,R.string.project_link_notice,()->{
            if(resolveLink(link)!=ProjectChatLinkPolicy.Status.AVAILABLE){status(linkStatus(resolveLink(link)));return;}
            save(st->ProjectLibrary.addLink(st,project.id,link),selection);
        }));
        button(R.string.project_back,this::render);button(R.string.close,this::pause);
    }
    private void reviewLink(ProjectLibrary.Project project,ProjectLibrary.Link link){
        reset();label(project.title,false);label(identity(link),false);
        ProjectChatLinkPolicy.Status state=resolveLink(link);
        label(s(linkStatus(state)),false);
        button(s(R.string.project_link_open),state==ProjectChatLinkPolicy.Status.AVAILABLE&&linkOpener!=null,
            ()->confirm(R.string.project_link_open,R.string.project_link_notice,()->{
                if(resolveLink(link)!=ProjectChatLinkPolicy.Status.AVAILABLE){status(linkStatus(resolveLink(link)));return;}
                final ProjectTransactions.Snapshot<ProjectLibrary.State> reviewed=snapshot;
                work(()->{
                    ProjectTransactions.Snapshot<ProjectLibrary.State> fresh=new ProjectStore(owner).read();
                    return reviewed.revision==fresh.revision&&java.util.Arrays.equals(ProjectLibrary.encode(reviewed.value),ProjectLibrary.encode(fresh.value))
                        &&fresh.value.project(project.id).links.contains(link);
                },available->{
                    if(!available){status(R.string.project_stale);return;}
                    final Consumer<ProjectLibrary.Link> opener=linkOpener;
                    pause();if(opener!=null)opener.accept(link);
                },error->status(R.string.project_unreadable));
            }));
        button(R.string.project_link_remove,()->confirm(R.string.project_link_remove,R.string.project_link_remove_notice,
            ()->save(st->ProjectLibrary.removeLink(st,project.id,link),selection)));
        button(R.string.project_back,this::render);button(R.string.close,this::pause);
    }
    private void contextButton(ProjectLibrary.State state,String pid,String aid,int ordinal){
        if(contextPreview==null||contextApply==null)return;
        ProjectContext chosen=ProjectContext.select(state,pid,aid,ordinal);
        button(s(R.string.project_context_review),!chosen.content.trim().isEmpty(),()->reviewContext(chosen));
    }
    private void reviewContext(ProjectContext chosen){
        final String preview;
        try{preview=contextPreview.apply(chosen);}
        catch(IllegalArgumentException e){status(R.string.project_context_limit);return;}
        reset();
        label(s(R.string.project_context_notice),false);
        label(preview,false);
        button(R.string.project_context_insert,()->confirm(R.string.project_context_insert,R.string.project_context_notice,
            ()->work(()->chosen.stillAvailable(new ProjectStore(owner).read().value),available->{
                if(!available){status(R.string.project_stale);return;}
                final Consumer<String> apply=contextApply;
                pause();
                if(apply!=null)apply.accept(preview);
            },error->status(R.string.project_unreadable))));
        button(R.string.project_back,this::render);button(R.string.close,this::pause);
    }
    private void renderReport(ProjectLibrary.State state,String pid){
        if(reportSaved){button(R.string.close,this::pause);return;}
        label(s(R.string.project_report_notice),false);
        if(pid==null){
            if(state.projects.isEmpty())label(s(R.string.project_report_no_projects),false);
            for(ProjectLibrary.Project p:state.projects)button(p.title,true,()->{
                selection=new ProjectWorkspacePolicy.Selection(p.id,null,0);render();
            });
        }else{
            ProjectLibrary.Project p=state.project(pid);label(p.title,true);
            button(s(R.string.project_new_artifact),p.artifacts.size()<ProjectLibrary.MAX_ARTIFACTS,()->reviewReport(p,null));
            for(ProjectLibrary.Artifact a:p.artifacts)
                button(s(R.string.project_report_append)+" · "+a.title,
                    ProjectWorkspacePolicy.canAppend(a)&&!a.current().text.equals(report.json),()->reviewReport(p,a));
            button(R.string.project_back,()->{selection=ProjectWorkspacePolicy.Selection.root();render();});
        }
        button(R.string.close,this::pause);
    }
    private void reviewReport(ProjectLibrary.Project p,ProjectLibrary.Artifact a){
        reset();
        final ProjectReportHandoff reviewed=report;
        final String pid=p.id,aid=a==null?UUID.randomUUID().toString():a.id;
        final String title=a==null?reportTitle:a.title;
        label(s(R.string.project_report_notice),false);
        label(p.title+" / "+title,true);
        label(reviewed.status+" · "+s(R.string.project_version,a==null?1:a.current().ordinal+1),false);
        label(reviewed.json,true);
        button(R.string.project_save,()->confirm(R.string.project_report_save,R.string.project_report_notice,
            ()->save(st->a==null?reviewed.create(st,pid,aid,title,System.currentTimeMillis()):
                reviewed.append(st,pid,aid,System.currentTimeMillis()),
                new ProjectWorkspacePolicy.Selection(pid,aid,0))));
        button(R.string.project_back,this::render);button(R.string.close,this::pause);
    }
    private void version(ProjectLibrary.Version v){
        label(s(R.string.project_version,v.ordinal)+" · "+DateFormat.getDateTimeInstance().format(new Date(v.saved)),false);
        label(v.text,true);readText(R.string.project_text,v.text);
    }
    private void readText(int title,String value){
        final long token=session.token();
        if(!value.trim().isEmpty())reading.add(body,title,()->value,
            ()->valid(token)&&!WORK.get()&&confirmation==null&&dialog!=null&&dialog.isShowing());
    }
    private void editorNav(){
        label(s(R.string.project_draft_notice),false);
        button(R.string.cancel,()->leave(this::render));button(R.string.close,()->leave(this::pause));
    }
    private void projectEditor(ProjectLibrary.Project p){
        reset();oldTitle=p==null?"":p.title;oldText=p==null?"":p.instructions;
        titleField=field(R.string.project_title,oldTitle,ProjectLibrary.MAX_TITLE,false);
        textField=field(R.string.project_instructions,oldText,ProjectLibrary.MAX_INSTRUCTIONS,true);
        final String id=p==null?UUID.randomUUID().toString():p.id;
        button(R.string.project_save,()->{
            final String name=titleField.getText().toString(),notes=textField.getText().toString();
            save(st->p==null?ProjectLibrary.createProject(st,id,name,notes):ProjectLibrary.editProject(st,id,name,notes),
                new ProjectWorkspacePolicy.Selection(id,null,0));
        });editorNav();
    }
    private void artifactEditor(ProjectLibrary.Project p,ProjectLibrary.Artifact a,boolean rename){
        reset();oldTitle=a==null?"":a.title;oldText=a==null?"":a.current().text;
        if(a==null||rename)titleField=field(R.string.project_title,oldTitle,ProjectLibrary.MAX_TITLE,false);
        if(!rename)textField=field(R.string.project_text,oldText,ProjectLibrary.MAX_TEXT,true);
        final String id=a==null?UUID.randomUUID().toString():a.id,pid=p.id;
        button(R.string.project_save,()->{
            final String title=titleField==null?oldTitle:titleField.getText().toString();
            final String value=textField==null?oldText:textField.getText().toString();
            save(st->a==null?ProjectLibrary.createArtifact(st,pid,id,title,value,Math.max(1,System.currentTimeMillis())):
                rename?ProjectLibrary.renameArtifact(st,pid,id,title):
                    ProjectLibrary.editArtifact(st,pid,id,value,ProjectWorkspacePolicy.savedAt(a,System.currentTimeMillis())),
                new ProjectWorkspacePolicy.Selection(pid,id,0));
        });editorNav();
    }
}
