package com.ollamapocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Dedicated, in-memory document batch; no chat or workflow checkpoint mutation. */
final class DocumentBatchUi {
    private static final ProjectWorkGate BUSY=new ProjectWorkGate();
    private final Activity owner;
    private final WorkspaceDictation dictation;
    private final WorkspaceReadAloud reading;
    private final WorkspaceTextViewer viewer;
    private final String model;
    private final BooleanSupplier eligible;
    private final AttachmentText.Encoding encoding;
    private final AtomicReference<Thread> worker=new AtomicReference<>();
    private volatile InputStream activeInput;
    private volatile DocumentBatchRunner runner;
    private volatile boolean closed,cancelRequested;
    private AlertDialog dialog,confirmation;
    private List<DocumentBatchPolicy.Source> sources;
    private DocumentBatchPolicy.Result result;
    private DocumentBatchPolicy.Plan analyzed;
    private int reportRequests;
    private long reportElapsedMillis;
    private int reportStatus;
    private String reportStopReason;
    private TextView status,output;
    private EditText goal;
    private ProgressBar progress;
    private Button preset,preview,copy,inspect,copyJson,saveProject,dictateGoal,readReport,viewReport;
    private ProjectWorkspaceUi reportWorkspace;
    static boolean busy(){return BUSY.busy();}
    DocumentBatchUi(Activity owner,String model,AttachmentText.Encoding encoding,BooleanSupplier eligible){
        this.owner=owner;this.model=model;this.encoding=encoding;this.eligible=eligible;this.dictation=new WorkspaceDictation(owner);this.reading=new WorkspaceReadAloud(owner);this.viewer=new WorkspaceTextViewer(owner);
    }
    private String s(int resource){return owner.getString(resource);}
    private int dp(int value){return Math.round(value*owner.getResources().getDisplayMetrics().density);}
    private void toast(int resource){Toast.makeText(owner,resource,Toast.LENGTH_SHORT).show();}
    private boolean visible(){return !closed&&!owner.isFinishing()&&!owner.isDestroyed()&&dialog!=null&&dialog.isShowing();}
    private void secure(AlertDialog d){if(d.getWindow()!=null)d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    private LinearLayout column(){LinearLayout panel=new LinearLayout(owner);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(16),dp(12),dp(16),dp(12));return panel;}
    private TextView label(LinearLayout panel,String value,boolean selectable){
        TextView v=new TextView(owner);v.setText(value);WorkbenchStyle.label(owner,v,14,false,false);v.setSaveEnabled(false);v.setTextIsSelectable(selectable);v.setAutoLinkMask(0);
        panel.addView(v,WorkbenchStyle.row(owner));return v;
    }
    private Button button(LinearLayout panel,int resource,Runnable action){
        Button b=new Button(owner);b.setText(resource);WorkbenchStyle.button(owner,b,resource);
        b.setOnClickListener(v->{if(visible()&&b.isAttachedToWindow()&&b.isEnabled()&&!busy()&&confirmation==null)action.run();});
        panel.addView(b,WorkbenchStyle.row(owner));return b;
    }
    private void confirm(int title,String body,Runnable action){
        if(confirmation!=null)confirmation.dismiss();
        final AlertDialog screen=dialog;
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(title).setMessage(body).setNegativeButton(R.string.cancel,null)
            .setPositiveButton(R.string.ok,(x,w)->{if(visible()&&dialog==screen&&confirmation==x&&!busy())action.run();}).create();
        confirmation=d;d.setOnDismissListener(x->{if(confirmation==d)confirmation=null;});d.show();secure(d);WorkbenchWindow.fit(owner,d);
    }
    private void enabled(){
        if(!visible())return;
        boolean idle=!busy();
        goal.setEnabled(idle);dictateGoal.setEnabled(idle);preset.setEnabled(idle);preview.setEnabled(idle&&sources!=null);copy.setEnabled(idle&&result!=null);
        inspect.setEnabled(idle&&result!=null&&!result.notes.isEmpty());copyJson.setEnabled(idle&&result!=null);
        saveProject.setEnabled(idle&&result!=null);
        readReport.setEnabled(idle&&result!=null&&analyzed!=null);
        viewReport.setEnabled(idle&&result!=null&&analyzed!=null);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(idle&&sources!=null);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(!idle&&!cancelRequested);
    }
    private interface Work<T>{
        T run(ProjectWorkGate.Lease lease,AlertDialog screen,Handler main)throws Exception;
    }
    private <T> boolean work(Work<T> task,Consumer<T> done,Consumer<Exception> failure){
        if(!visible())return false;
        final AlertDialog screen=dialog;
        final ProjectWorkGate.Lease lease=BUSY.acquire();
        if(lease==null){toast(R.string.busy);return false;}
        Thread launched=null;
        try{
            viewer.cancel();reading.cancel();dictation.cancel();cancelRequested=false;
            final Handler main=new Handler(Looper.getMainLooper());
            launched=new Thread(()->{
                try{
                    if(!lease.execute(()->{
                        try{return task.run(lease,screen,main);}
                        finally{worker.compareAndSet(Thread.currentThread(),null);}
                    },main::post,(value,error)->{
                        if(!visible()||dialog!=screen)return;
                        if(error!=null)failure.accept(error);else done.accept(value);
                        enabled();
                    }))android.util.Log.w("OnHandAI","Document completion not delivered");
                }finally{worker.compareAndSet(Thread.currentThread(),null);}
            },"onhand-document-batch");
            worker.set(launched);enabled();launched.start();return true;
        }catch(RuntimeException e){
            if(launched!=null)worker.compareAndSet(launched,null);
            if(lease.release()&&visible()&&dialog==screen){failure.accept(e);enabled();}
            return false;
        }catch(Error fatal){
            if(launched!=null)worker.compareAndSet(launched,null);
            lease.release();throw fatal;
        }
    }
    private void postProgress(ProjectWorkGate.Lease lease,AlertDialog screen,Handler main,Runnable update){
        if(!lease.isCurrent()||closed||cancelRequested)return;
        try{main.post(()->{
            if(lease.isCurrent()&&visible()&&dialog==screen&&!cancelRequested)update.run();
        });}catch(RuntimeException rejected){
            // Progress is advisory; only terminal delivery controls ownership.
        }
    }
    void pause(){
        viewer.cancel();reading.cancel();dictation.cancel();
        if(closed)return;closed=true;stop();
        if(reportWorkspace!=null){reportWorkspace.pause();reportWorkspace=null;}
        AlertDialog c=confirmation;confirmation=null;if(c!=null)c.dismiss();
        AlertDialog d=dialog;dialog=null;if(d!=null)d.dismiss();
        sources=null;result=null;analyzed=null;
    }
    private void stop(){
        if(cancelRequested)return;
        cancelRequested=true;
        DocumentBatchRunner r=runner;if(r!=null)r.cancel();
        Thread t=worker.get();if(t!=null)t.interrupt();
        InputStream input=activeInput;
        if(input!=null){
            Thread closer=new Thread(()->{try{input.close();}catch(Exception ignored){}},"document-batch-close");
            closer.setDaemon(true);closer.start();
        }
    }
    private void check()throws IOException{
        if(closed||cancelRequested||Thread.currentThread().isInterrupted())throw new IOException("Batch stopped");
    }
    void show(List<Uri> selected){
        if(closed||dialog!=null||busy()||!eligible.getAsBoolean()){toast(R.string.busy);return;}
        LinearLayout panel=column();
        label(panel,s(R.string.batch_notice),false);
        label(panel,s(R.string.model_label)+": "+model,false);
        status=label(panel,s(R.string.attachment_reading),false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        progress=new ProgressBar(owner,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(selected.size());
        panel.addView(progress,new LinearLayout.LayoutParams(-1,dp(12)));
        goal=new EditText(owner);goal.setHint(R.string.batch_goal);goal.setText(s(R.string.batch_default_goal));goal.setTextSize(15);
        goal.setMinLines(2);goal.setMaxLines(4);goal.setSaveEnabled(false);goal.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        goal.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(DocumentBatchPolicy.MAX_GOAL)});
        WorkbenchStyle.field(owner,goal);WorkbenchStyle.fieldLabel(owner,panel,R.string.batch_goal,goal);
        panel.addView(goal,WorkbenchStyle.row(owner));
        dictateGoal=dictation.add(panel,goal,R.string.batch_goal,DocumentBatchPolicy.MAX_GOAL,true,
            ()->visible()&&!busy()&&confirmation==null&&eligible.getAsBoolean());
        preset=button(panel,R.string.batch_goal_templates,this::chooseGoal);
        LinearLayout inspectionActions=new AdaptiveAnswerActions(owner);panel.addView(inspectionActions,WorkbenchStyle.row(owner));
        preview=button(inspectionActions,R.string.batch_preview,this::showSourcePreview);
        inspect=button(inspectionActions,R.string.batch_inspect_excerpt,this::inspectCompleted);
        LinearLayout copyActions=new AdaptiveAnswerActions(owner);panel.addView(copyActions,WorkbenchStyle.row(owner));
        copy=button(copyActions,R.string.batch_copy,()->{
            if(result==null||analyzed==null||busy())return;
            copyReport(R.string.batch_copy,s(R.string.attachment_copy_notice),report(analyzed,result));
        });
        copyJson=button(copyActions,R.string.batch_copy_json,()->{
            if(result==null||analyzed==null||busy())return;
            try{copyReport(R.string.batch_copy_json,s(R.string.attachment_copy_notice)+"\n\n"+s(R.string.batch_json_notice),snapshot().json());}
            catch(Exception e){toast(R.string.batch_export_failed);}
        });
        saveProject=button(panel,R.string.project_report_save,()->{
            if(!visible()||busy()||ProjectWorkspaceUi.busy()||!eligible.getAsBoolean()||result==null||analyzed==null)return;
            final DocumentBatchPolicy.Result reviewed=result;
            try{
                ProjectReportHandoff handoff=ProjectReportHandoff.of(snapshot());
                reportWorkspace=new ProjectWorkspaceUi(owner,()->visible()&&!busy()&&result==reviewed);
                reportWorkspace.openReport(handoff,s(R.string.batch_title)+" · "+batchState(reviewed.status));
            }catch(ProjectReportHandoff.TooLarge e){toast(R.string.project_report_too_large);}
            catch(Exception e){toast(R.string.batch_export_failed);}
        });
        viewReport=viewer.add(panel,R.string.batch_title,()->output.getText().toString(),
            ()->visible()&&!busy()&&result!=null&&analyzed!=null&&confirmation==null&&eligible.getAsBoolean());
        viewReport.setEnabled(false);
        readReport=reading.add(panel,R.string.batch_title,()->output.getText().toString(),
            ()->visible()&&!busy()&&result!=null&&analyzed!=null&&confirmation==null&&eligible.getAsBoolean());
        readReport.setEnabled(false);
        output=label(panel,"",true);
        ScrollView scroll=new ScrollView(owner);scroll.addView(panel);
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(R.string.batch_title).setView(scroll)
            .setNegativeButton(R.string.close,null).setNeutralButton(R.string.stop,null).setPositiveButton(R.string.batch_start,null).create();
        dialog=d;d.setOnDismissListener(x->{if(dialog==d)pause();});d.show();secure(d);WorkbenchWindow.fit(owner,d);
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
            if(visible()&&dialog==d&&v.isEnabled()&&busy()&&!cancelRequested){stop();status.setText(R.string.auto_pause_requested);enabled();}
        });
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(visible()&&dialog==d&&v.isEnabled()&&confirmation==null)start();});
        final List<Uri> immutable=new ArrayList<>(selected);
        work((lease,screen,main)->load(immutable,lease,screen,main),loaded->{
            sources=loaded;status.setText(R.string.batch_ready);output.setText(coverage(loaded));progress.setProgress(immutable.size());
        },error->{
            sources=null;status.setText(cancelRequested?R.string.batch_stopped:R.string.batch_extract_failed);
            output.setText(error instanceof SourceFailure?((SourceFailure)error).display:"");
        });
    }
    private static final class SourceFailure extends IOException{
        final String display;
        SourceFailure(String display){super("Source extraction failed");this.display=display;}
    }
    private List<DocumentBatchPolicy.Source> load(List<Uri> selected,ProjectWorkGate.Lease lease,AlertDialog screen,Handler main)throws Exception{
        int limit=DocumentBatchPolicy.prefixLimit(selected.size());
        List<DocumentBatchPolicy.Source> out=new ArrayList<>();
        for(int i=0;i<selected.size();i++){
            check();String name=s(R.string.batch_unknown_file);
            try{
                Uri uri=selected.get(i);if(!"content".equals(uri.getScheme()))throw new AttachmentText.Failure(AttachmentText.Problem.UNSUPPORTED);
                long bytes=-1;
                try(Cursor cursor=owner.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
                    if(cursor!=null&&cursor.moveToFirst()){
                        int ni=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME),si=cursor.getColumnIndex(OpenableColumns.SIZE);
                        if(ni>=0&&!cursor.isNull(ni))name=AttachmentText.safeName(cursor.getString(ni));
                        if(si>=0&&!cursor.isNull(si))bytes=cursor.getLong(si);
                    }
                }
                check();
                if(bytes>AttachmentText.maxBytes(name))throw new AttachmentText.Failure(AttachmentText.Problem.TOO_LARGE);
                try(InputStream input=owner.getContentResolver().openInputStream(uri)){
                    if(input==null)throw new AttachmentText.Failure(AttachmentText.Problem.INVALID);
                    activeInput=input;check();
                    if(PdfExcerpt.supports(name)){
                        if(Build.VERSION.SDK_INT<35)throw new AttachmentText.Failure(AttachmentText.Problem.PDF_VERSION);
                        AttachmentText.Result pdf=PdfTextClient.read(owner,name,input,1);
                        AttachmentText.Prefix prefix=new AttachmentText.Prefix(pdf.text,pdf.partial).bounded(limit);
                        out.add(new DocumentBatchPolicy.Source(name,prefix.text,DocumentBatchPolicy.Kind.PDF,prefix.partial,pdf.pdfPages,pdf.pdfExamined,pdf.pdfTextPages));
                    }else{
                        AttachmentText.Prefix prefix=AttachmentText.readPrefix(name,input,encoding,limit);
                        out.add(new DocumentBatchPolicy.Source(name,prefix.text,OfficeText.supports(name)?DocumentBatchPolicy.Kind.OFFICE:DocumentBatchPolicy.Kind.TEXT,prefix.partial,0,0,0));
                    }
                }finally{activeInput=null;}
            }catch(Exception e){
                check();
                throw new SourceFailure("D"+(i+1)+" · "+name+"\n"+s(errorLabel(e)));
            }
            final int complete=i+1;
            postProgress(lease,screen,main,()->progress.setProgress(complete));
        }
        check();return out;
    }
    private int errorLabel(Exception e){
        if(e instanceof AttachmentText.Failure)switch(((AttachmentText.Failure)e).problem){
            case UNSUPPORTED:return R.string.attachment_unsupported;
            case TOO_LARGE:return R.string.attachment_too_large;
            case EMPTY:return R.string.attachment_empty;
            case PDF_VERSION:return R.string.attachment_pdf_version;
            case PDF_PROTECTED:return R.string.attachment_pdf_protected;
            case PDF_NO_TEXT:return R.string.attachment_pdf_no_text;
            case PDF_TIMEOUT:return R.string.attachment_pdf_timeout;
            default:break;
        }
        return R.string.attachment_invalid;
    }
    private String coverage(List<DocumentBatchPolicy.Source> files){
        StringBuilder b=new StringBuilder(s(R.string.batch_coverage));
        for(int i=0;i<files.size();i++){
            DocumentBatchPolicy.Source f=files.get(i);
            b.append("\n\nD").append(i+1).append(" · ").append(f.name).append('\n')
                .append(owner.getString(R.string.batch_scope,Integer.toString(f.text.length()),s(f.partial?R.string.batch_partial:R.string.batch_extracted)));
            if(f.kind==DocumentBatchPolicy.Kind.OFFICE)b.append('\n').append(s(R.string.attachment_office_note));
            if(f.kind==DocumentBatchPolicy.Kind.PDF)b.append('\n').append(owner.getString(R.string.attachment_pdf_note,1,f.examined,f.pages,f.readable));
        }
        return b.toString();
    }
    private String plannedCoverage(DocumentBatchPolicy.Plan plan){
        StringBuilder b=new StringBuilder(s(R.string.batch_plan_scope));
        for(int i=0;i<plan.sources.size();i++){
            int included=plan.includedEnd(i),extracted=plan.sources.get(i).text.length();
            b.append("\nD").append(i+1).append(" · ").append(owner.getString(R.string.batch_plan_range,
                Integer.toString(included),Integer.toString(extracted)));
            if(included<extracted)b.append(" · ").append(s(R.string.batch_budget_omitted));
        }
        return b.toString();
    }
    private void showSourcePreview(){
        if(!visible()||busy()||sources==null)return;
        LinearLayout panel=column();label(panel,coverage(sources),false);
        for(int i=0;i<sources.size();i++){
            DocumentBatchPolicy.Source file=sources.get(i);
            label(panel,"\nD"+(i+1)+" · "+file.name+"\n"+file.text,true);
        }
        ScrollView scroll=new ScrollView(owner);scroll.addView(panel);
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(R.string.batch_preview).setView(scroll).setPositiveButton(R.string.close,null).create();
        confirmation=d;d.setOnDismissListener(x->{if(confirmation==d)confirmation=null;});d.show();secure(d);WorkbenchWindow.fit(owner,d);
    }
    private DocumentBatchReport snapshot(){
        return new DocumentBatchReport(analyzed,result,model,reportStopReason,reportRequests,reportElapsedMillis);
    }
    private void copyReport(int title,String notice,String value){
        final DocumentBatchPolicy.Result reviewed=result;
        confirm(title,notice,()->{
            if(busy()||result!=reviewed||reviewed==null)return;
            try{
                ClipboardManager clipboard=(ClipboardManager)owner.getSystemService(Context.CLIPBOARD_SERVICE);
                if(clipboard==null)throw new IllegalStateException("Clipboard unavailable");
                clipboard.setPrimaryClip(SensitiveClipboard.text(s(R.string.batch_title),value));toast(R.string.copied);
            }catch(RuntimeException e){toast(R.string.batch_export_failed);}
        });
    }
    private void inspectCompleted(){
        if(!visible()||busy()||result==null||result.notes.isEmpty())return;
        final DocumentBatchReport snapshot;
        try{snapshot=snapshot();}catch(IllegalArgumentException e){toast(R.string.batch_export_failed);return;}
        if(confirmation!=null)confirmation.dismiss();
        LinearLayout panel=column();
        label(panel,s(R.string.batch_inspect_notice),false);
        Spinner selector=new Spinner(owner);
        List<String> labels=new ArrayList<>();
        for(DocumentBatchPolicy.Note note:snapshot.completed){
            DocumentBatchPolicy.Unit u=note.unit;
            String label=u.id()+" · "+snapshot.plan.sources.get(u.source).name+" ["+(u.start+1)+"–"+u.end+"]";
            if(snapshot.result.status==DocumentBatchPolicy.Status.COMPLETED&&snapshot.hasExtractedEvidence(note))
                label+=" · "+s(snapshot.citations.valid.contains(u.id())?R.string.batch_citation_referenced:R.string.batch_citation_unreferenced);
            labels.add(label);
        }
        selector.setAdapter(WorkbenchStyle.spinnerChoices(selector,labels.toArray(new String[0])));
        OnHandGlyphs.spinner(selector);selector.setMinimumHeight(dp(48));
        selector.setContentDescription(s(R.string.batch_inspect_excerpt));
        panel.addView(selector,new LinearLayout.LayoutParams(-1,-2));
        TextView range=label(panel,"",false);
        label(panel,s(R.string.batch_extracted_excerpt),false);
        TextView excerpt=label(panel,"",true);
        label(panel,s(R.string.batch_generated_note),false);
        TextView generated=label(panel,"",true);
        Runnable update=()->{
            int index=selector.getSelectedItemPosition();
            if(index<0||index>=snapshot.completed.size())return;
            DocumentBatchPolicy.Note note=snapshot.completed.get(index);
            range.setText(labels.get(index));
            excerpt.setText(snapshot.extracted(note));
            generated.setText(snapshot.hasExtractedEvidence(note)?note.text:s(R.string.batch_whitespace_record));
        };
        selector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){update.run();}
            public void onNothingSelected(android.widget.AdapterView<?> parent){range.setText("");excerpt.setText("");generated.setText("");}
        });
        update.run();
        ScrollView scroll=new ScrollView(owner);scroll.addView(panel);
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(R.string.batch_inspect_excerpt).setView(scroll).setPositiveButton(R.string.close,null).create();
        confirmation=d;d.setOnDismissListener(x->{if(confirmation==d)confirmation=null;});d.show();secure(d);WorkbenchWindow.fit(owner,d);
    }
    /** Choosing a template only edits the current draft; the existing start review remains mandatory. */
    private void chooseGoal(){
        if(!visible()||busy()||!eligible.getAsBoolean()||confirmation!=null)return;
        final AlertDialog screen=dialog;final String original=goal.getText().toString();
        final int[] prompts={R.string.batch_default_goal,R.string.batch_goal_compare_text,
                R.string.batch_goal_extract_text,R.string.batch_goal_review_text,R.string.batch_goal_code_text};
        String[] titles={s(R.string.batch_goal_summary),s(R.string.batch_goal_compare),
                s(R.string.batch_goal_extract),s(R.string.batch_goal_review),s(R.string.batch_goal_code)};
        AlertDialog chooser=new AlertDialog.Builder(owner).setTitle(R.string.batch_goal_templates).setItems(titles,(x,index)->{
            if(!visible()||dialog!=screen||confirmation!=x||busy()||!eligible.getAsBoolean()||
                    !original.equals(goal.getText().toString()))return;
            String candidate=s(prompts[index]);
            if(candidate.trim().isEmpty()||candidate.length()>DocumentBatchPolicy.MAX_GOAL){toast(R.string.batch_goal);return;}
            confirm(R.string.auto_apply_draft,candidate,()->{
                if(eligible.getAsBoolean()&&original.equals(goal.getText().toString())){
                    dictation.cancel();goal.setText(candidate);goal.setSelection(goal.length());
                }
            });
        }).setNegativeButton(R.string.cancel,null).create();
        confirmation=chooser;chooser.setOnDismissListener(x->{if(confirmation==chooser)confirmation=null;});
        chooser.show();secure(chooser);WorkbenchWindow.fitCompact(owner,chooser);
    }
    private void start(){
        if(!visible()||busy()||sources==null||!eligible.getAsBoolean())return;
        final DocumentBatchPolicy.Plan plan;
        try{plan=new DocumentBatchPolicy.Plan(goal.getText().toString(),sources);}catch(IllegalArgumentException e){toast(R.string.batch_goal);return;}
        final String reviewed=goal.getText().toString();
        confirm(R.string.batch_start,s(R.string.batch_run_notice)+"\n\n"+coverage(sources)+"\n\n"+plannedCoverage(plan)+"\n\n"+s(R.string.batch_goal)+": "+reviewed,()->{
            if(busy()||!eligible.getAsBoolean()||!reviewed.equals(goal.getText().toString()))return;
            if(!OllamaForegroundService.isRunning()){toast(R.string.test_need_server);return;}
            if(!"off".equals(CoopSession.get(owner).role())){toast(R.string.guest_stop_first);return;}
            result=null;analyzed=null;reportRequests=0;reportElapsedMillis=0;reportStopReason=null;reportStatus=R.string.thinking;output.setText(coverage(sources)+"\n\n"+plannedCoverage(plan));status.setText(R.string.thinking);progress.setMax(plan.maxTotal());progress.setProgress(0);
            final DocumentBatchRunner active=new DocumentBatchRunner(owner,model);runner=active;
            boolean started=work((lease,screen,main)->active.run(plan,done->postProgress(lease,screen,main,()->{
                progress.setProgress(done);status.setText(owner.getString(R.string.batch_progress,Integer.toString(done),"≤"+plan.maxTotal()));
            })),finished->{
                runner=null;analyzed=plan;result=finished;
                reportRequests=active.requests();reportElapsedMillis=active.elapsedMillis();
                reportStopReason=finished.status==DocumentBatchPolicy.Status.STOPPED?
                    (active.reason()==DocumentBatchRunner.Stop.NONE?"UNKNOWN":active.reason().name()):null;
                reportStatus=finished.status==DocumentBatchPolicy.Status.COMPLETED?R.string.batch_completed:
                    finished.status==DocumentBatchPolicy.Status.STOPPED?stopLabel(active.reason()):
                    finished.contextLimited?R.string.batch_context_limit:R.string.batch_failed;
                status.setText(reportStatus);
                if(finished.status==DocumentBatchPolicy.Status.COMPLETED)progress.setProgress(progress.getMax());
                output.setText(report(plan,finished));
            },error->{runner=null;status.setText(R.string.batch_failed);});
            if(!started&&runner==active){
                runner=null;active.cancel();status.setText(R.string.batch_failed);enabled();
            }
        });
    }
    private int stopLabel(DocumentBatchRunner.Stop stop){
        switch(stop){
            case THERMAL:return R.string.bench_hot;
            case MEMORY:return R.string.bench_memory;
            case SERVER:return R.string.test_need_server;
            case COOP:return R.string.guest_stop_first;
            case DEADLINE:return R.string.batch_deadline;
            default:return R.string.batch_stopped;
        }
    }
    private String batchState(DocumentBatchPolicy.Status value){
        switch(value){
            case COMPLETED:return s(R.string.auto_state_completed);
            case STOPPED:return s(R.string.batch_status_stopped);
            case FAILED:return s(R.string.batch_status_failed);
            default:throw new IllegalArgumentException("Batch status");
        }
    }
    private String report(DocumentBatchPolicy.Plan plan,DocumentBatchPolicy.Result result){
        StringBuilder b=new StringBuilder(s(R.string.batch_report_status)).append(": ").append(batchState(result.status))
            .append(" · ").append(s(reportStatus)).append("\n").append(s(R.string.model_label)).append(": ").append(model);
        if("UNKNOWN".equals(reportStopReason))b.append(" · ").append(s(R.string.run_stop_unknown));
        b.append("\n\n").append(coverage(plan.sources)).append("\n\n").append(plannedCoverage(plan)).append("\n\n").append(s(R.string.batch_goal)).append(": ").append(plan.goal);
        b.append("\n\n").append(owner.getString(R.string.batch_processed,Integer.toString(result.notes.size()),Integer.toString(plan.units.size())));
        b.append('\n').append(s(R.string.batch_quality_notice));
        b.append("\n\n").append(s(R.string.batch_actual_scope));
        for(int i=0;i<plan.sources.size();i++){
            b.append("\nD").append(i+1).append(" · ").append(owner.getString(R.string.batch_actual_range,
                Integer.toString(result.completedEnd(i)),Integer.toString(plan.includedEnd(i))));
        }
        b.append("\n\n").append(owner.getString(R.string.batch_run_metrics,
            Integer.toString(reportRequests),Long.toString(reportElapsedMillis)));
        boolean complete=result.status==DocumentBatchPolicy.Status.COMPLETED;
        DocumentBatchPolicy.Citations refs=DocumentBatchPolicy.citations(complete?result.synthesis:"",result.notes);
        List<DocumentCitationCoverage.Source> counts=DocumentBatchPolicy.citationCoverage(plan.sources.size(),result,refs);
        b.append("\n\n").append(s(R.string.batch_citation_source_scope));
        for(int i=0;i<counts.size();i++){
            DocumentCitationCoverage.Source row=counts.get(i);
            b.append("\nD").append(i+1).append(" · ").append(row.referencedEvidence<0?
                owner.getString(R.string.batch_citation_source_pending,row.completedEvidence):
                owner.getString(R.string.batch_citation_source_counts,row.referencedEvidence,row.completedEvidence));
        }
        if(complete){
            b.append("\n\n").append(s(R.string.batch_synthesis)).append('\n').append(result.synthesis);
            b.append("\n\n").append(s(R.string.batch_citation_check));
            if(refs.valid.isEmpty())b.append('\n').append(s(R.string.batch_citation_missing));
            else for(DocumentBatchPolicy.Note n:result.notes)if(refs.valid.contains(n.unit.id()))
                b.append("\n").append(n.unit.id()).append(" · ").append(plan.sources.get(n.unit.source).name)
                    .append(" [").append(n.unit.start+1).append("–").append(n.unit.end).append("]");
            if(!refs.unknown.isEmpty())b.append('\n').append(s(R.string.batch_citation_unknown)).append(": ").append(String.join(", ",refs.unknown));
        }else{
            b.append("\n\n").append(s(R.string.batch_no_synthesis));
            if(result.contextLimited)b.append('\n').append(s(R.string.batch_context_limit));
        }
        b.append("\n\n").append(s(R.string.batch_unit_notes));
        for(DocumentBatchPolicy.Note note:result.notes)b.append("\n\n").append(note.unit.id())
            .append(" [").append(note.unit.start+1).append("–").append(note.unit.end).append("]\n").append(note.text);
        return b.toString();
    }
}
