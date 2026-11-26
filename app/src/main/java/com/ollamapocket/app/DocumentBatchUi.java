package com.ollamapocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Dedicated, in-memory document batch; no chat or workflow checkpoint mutation. */
final class DocumentBatchUi {
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private final Activity owner;
    private final String model;
    private final BooleanSupplier eligible;
    private final AttachmentText.Encoding encoding;
    private volatile Thread worker;
    private volatile InputStream activeInput;
    private volatile DocumentBatchRunner runner;
    private volatile boolean closed,cancelRequested;
    private AlertDialog dialog,confirmation;
    private List<DocumentBatchPolicy.Source> sources;
    private DocumentBatchPolicy.Result result;
    private DocumentBatchPolicy.Plan analyzed;
    private int reportRequests;
    private long reportElapsedMillis;
    private TextView status,output;
    private EditText goal;
    private ProgressBar progress;
    private Button preview,copy;
    static boolean busy(){return BUSY.get();}
    DocumentBatchUi(Activity owner,String model,AttachmentText.Encoding encoding,BooleanSupplier eligible){
        this.owner=owner;this.model=model;this.encoding=encoding;this.eligible=eligible;
    }
    private String s(int resource){return owner.getString(resource);}
    private int dp(int value){return Math.round(value*owner.getResources().getDisplayMetrics().density);}
    private void toast(int resource){Toast.makeText(owner,resource,Toast.LENGTH_SHORT).show();}
    private boolean visible(){return !closed&&!owner.isFinishing()&&!owner.isDestroyed()&&dialog!=null&&dialog.isShowing();}
    private void secure(AlertDialog d){if(d.getWindow()!=null)d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    private LinearLayout column(){LinearLayout panel=new LinearLayout(owner);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(16),dp(12),dp(16),dp(12));return panel;}
    private TextView label(LinearLayout panel,String value,boolean selectable){
        TextView v=new TextView(owner);v.setText(value);v.setTextSize(14);v.setSaveEnabled(false);v.setTextIsSelectable(selectable);v.setAutoLinkMask(0);
        panel.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;
    }
    private Button button(LinearLayout panel,int resource,Runnable action){
        Button b=new Button(owner);b.setText(resource);b.setAllCaps(false);b.setMinHeight(dp(48));b.setOnClickListener(v->action.run());panel.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;
    }
    private void confirm(int title,String body,Runnable action){
        if(confirmation!=null)confirmation.dismiss();
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(title).setMessage(body).setNegativeButton(R.string.cancel,null)
            .setPositiveButton(R.string.ok,(x,w)->{if(visible())action.run();}).create();
        confirmation=d;d.setOnDismissListener(x->{if(confirmation==d)confirmation=null;});d.show();secure(d);
    }
    private void enabled(){
        if(!visible())return;
        boolean idle=!busy();
        goal.setEnabled(idle);preview.setEnabled(idle&&sources!=null);copy.setEnabled(idle&&result!=null);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(idle&&sources!=null);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(!idle&&!cancelRequested);
    }
    private <T> void work(Callable<T> task,Consumer<T> done,Consumer<Exception> failure){
        if(!BUSY.compareAndSet(false,true)){toast(R.string.busy);return;}
        cancelRequested=false;
        Thread t=new Thread(()->{
            T value=null;Exception problem=null;
            try{value=task.call();}catch(Exception e){problem=e;}
            finally{worker=null;}
            final T completed=value;final Exception error=problem;
            owner.runOnUiThread(()->{
                BUSY.set(false);
                if(!visible())return;
                if(error!=null)failure.accept(error);else done.accept(completed);
                enabled();
            });
        },"onhand-document-batch");
        worker=t;t.start();enabled();
    }
    void pause(){
        if(closed)return;closed=true;stop();
        AlertDialog c=confirmation;confirmation=null;if(c!=null)c.dismiss();
        AlertDialog d=dialog;dialog=null;if(d!=null)d.dismiss();
        sources=null;result=null;analyzed=null;
    }
    private void stop(){
        cancelRequested=true;
        DocumentBatchRunner r=runner;if(r!=null)r.cancel();
        Thread t=worker;if(t!=null)t.interrupt();
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
        panel.addView(goal,new LinearLayout.LayoutParams(-1,-2));
        preview=button(panel,R.string.batch_preview,this::showSourcePreview);
        copy=button(panel,R.string.batch_copy,()->{
            if(result==null||analyzed==null||busy())return;
            final String report=report(analyzed,result);
            confirm(R.string.batch_copy,s(R.string.attachment_copy_notice),()->{
                ClipboardManager clipboard=(ClipboardManager)owner.getSystemService(Context.CLIPBOARD_SERVICE);
                if(clipboard!=null){clipboard.setPrimaryClip(SensitiveClipboard.text(s(R.string.batch_title),report));toast(R.string.copied);}
            });
        });
        output=label(panel,"",true);
        ScrollView scroll=new ScrollView(owner);scroll.addView(panel);
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(R.string.batch_title).setView(scroll)
            .setNegativeButton(R.string.close,null).setNeutralButton(R.string.stop,null).setPositiveButton(R.string.batch_start,null).create();
        dialog=d;d.setOnDismissListener(x->{if(dialog==d)pause();});d.show();secure(d);
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
            if(busy()&&!cancelRequested){stop();status.setText(R.string.auto_pause_requested);enabled();}
        });
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->start());
        final List<Uri> immutable=new ArrayList<>(selected);
        work(()->load(immutable),loaded->{
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
    private List<DocumentBatchPolicy.Source> load(List<Uri> selected)throws Exception{
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
            owner.runOnUiThread(()->{if(visible()&&!cancelRequested)progress.setProgress(complete);});
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
        confirmation=d;d.setOnDismissListener(x->{if(confirmation==d)confirmation=null;});d.show();secure(d);
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
            result=null;analyzed=null;reportRequests=0;reportElapsedMillis=0;output.setText(coverage(sources)+"\n\n"+plannedCoverage(plan));status.setText(R.string.thinking);progress.setMax(plan.maxTotal());progress.setProgress(0);
            final DocumentBatchRunner active=new DocumentBatchRunner(owner,model);runner=active;
            work(()->active.run(plan,done->owner.runOnUiThread(()->{
                if(visible()&&!cancelRequested){
                    progress.setProgress(done);status.setText(owner.getString(R.string.batch_progress,Integer.toString(done),"≤"+plan.maxTotal()));
                }
            })),finished->{
                runner=null;analyzed=plan;result=finished;
                reportRequests=active.requests();reportElapsedMillis=active.elapsedMillis();
                status.setText(finished.status==DocumentBatchPolicy.Status.COMPLETED?R.string.batch_completed:
                    finished.status==DocumentBatchPolicy.Status.STOPPED?stopLabel(active.reason()):
                    finished.contextLimited?R.string.batch_context_limit:R.string.batch_failed);
                if(finished.status==DocumentBatchPolicy.Status.COMPLETED)progress.setProgress(progress.getMax());
                output.setText(report(plan,finished));
            },error->{runner=null;status.setText(R.string.batch_failed);});
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
    private String report(DocumentBatchPolicy.Plan plan,DocumentBatchPolicy.Result result){
        StringBuilder b=new StringBuilder(coverage(plan.sources)).append("\n\n").append(plannedCoverage(plan)).append("\n\n").append(s(R.string.batch_goal)).append(": ").append(plan.goal);
        b.append("\n\n").append(owner.getString(R.string.batch_processed,Integer.toString(result.notes.size()),Integer.toString(plan.units.size())));
        b.append('\n').append(s(R.string.batch_quality_notice));
        b.append("\n\n").append(s(R.string.batch_actual_scope));
        for(int i=0;i<plan.sources.size();i++){
            b.append("\nD").append(i+1).append(" · ").append(owner.getString(R.string.batch_actual_range,
                Integer.toString(result.completedEnd(i)),Integer.toString(plan.includedEnd(i))));
        }
        b.append("\n\n").append(owner.getString(R.string.batch_run_metrics,
            Integer.toString(reportRequests),Long.toString(reportElapsedMillis)));
        if(result.status==DocumentBatchPolicy.Status.COMPLETED){
            b.append("\n\n").append(s(R.string.batch_synthesis)).append('\n').append(result.synthesis);
            DocumentBatchPolicy.Citations refs=DocumentBatchPolicy.citations(result.synthesis,result.notes);
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
