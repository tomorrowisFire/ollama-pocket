package com.ollamapocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Foreground-only tools and workflows. A single active worker survives Activity recreation only until cancelled. */
final class AutomationUi {
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private final Activity owner;
    private AlertDialog dialog,confirmation;
    private McpLanClient mcp;
    private LocalWorkflow workflow;
    private volatile Thread worker;
    private volatile boolean stopped;
    private int epoch;
    AutomationUi(Activity a){owner=a;}
    static boolean busy(){return BUSY.get();}
    private String s(int id){return owner.getString(id);}
    private int dp(int n){return Math.round(n*owner.getResources().getDisplayMetrics().density);}
    private void toast(int id){Toast.makeText(owner,id,Toast.LENGTH_SHORT).show();}
    private LinearLayout column(){LinearLayout l=new LinearLayout(owner);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(16),dp(12),dp(16),dp(12));return l;}
    private void label(LinearLayout l,String value){TextView t=new TextView(owner);t.setText(value);t.setTextSize(14);l.addView(t,new LinearLayout.LayoutParams(-1,-2));}
    private TextView output(LinearLayout l){TextView t=new TextView(owner);t.setTextIsSelectable(true);t.setSaveEnabled(false);t.setAutoLinkMask(0);t.setTextSize(13);l.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;}
    private EditText field(LinearLayout l,int hint,String value,int max,boolean multiline){
        EditText e=new EditText(owner);e.setHint(hint);e.setText(value);e.setSaveEnabled(false);e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        e.setInputType(InputType.TYPE_CLASS_TEXT|(multiline?InputType.TYPE_TEXT_FLAG_MULTI_LINE:InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});
        if(multiline){e.setMinLines(3);e.setMaxLines(7);}
        l.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;
    }
    private Button button(LinearLayout l,int id,Runnable run){Button b=new Button(owner);b.setText(id);b.setAllCaps(false);b.setMinHeight(dp(48));b.setOnClickListener(v->run.run());l.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    private boolean valid(int token){return token==epoch&&!stopped&&!owner.isFinishing()&&!owner.isDestroyed();}
    private void secure(AlertDialog d){if(d.getWindow()!=null)d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    private void confirm(String title,String body,Runnable action){
        if(confirmation!=null)confirmation.dismiss();
        final int token=epoch;
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(title).setMessage(body)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok,(x,w)->{if(valid(token))action.run();}).create();
        confirmation=d;d.setOnDismissListener(x->{if(confirmation==d)confirmation=null;});d.show();secure(d);
    }
    private void show(String title,LinearLayout l){
        ScrollView scroll=new ScrollView(owner);scroll.addView(l);
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(title).setView(scroll)
            .setNegativeButton(R.string.close,null).create();
        dialog=d;d.setOnDismissListener(x->{if(dialog==d){dialog=null;pause();}});d.show();secure(d);
    }
    void pause(){
        stopped=true;epoch++;
        if(mcp!=null){mcp.cancel();mcp=null;}
        if(workflow!=null)workflow.pause();
        Thread t=worker;if(t!=null)t.interrupt();
        AlertDialog c=confirmation;confirmation=null;if(c!=null)c.dismiss();
        AlertDialog d=dialog;dialog=null;if(d!=null)d.dismiss();
    }
    private <T> void work(Callable<T> task,Consumer<T> success,Runnable failure){
        if(!BUSY.compareAndSet(false,true)){toast(R.string.busy);return;}
        final int token=epoch;
        Thread t=new Thread(()->{
            T result=null;boolean ok=false;
            try{result=task.call();ok=true;}catch(Exception ignored){}
            finally{worker=null;BUSY.set(false);}
            final T value=result;final boolean done=ok;
            owner.runOnUiThread(()->{if(valid(token)){if(done)success.accept(value);else failure.run();}});
        },"onhand-automation");
        worker=t;t.start();
    }
    void open(String model){
        if(busy()){toast(R.string.busy);return;}
        stopped=false;epoch++;
        final int token=epoch;
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(R.string.auto_title)
            .setItems(new String[]{s(R.string.auto_mcp),s(R.string.auto_workflow)},(x,w)->{
                if(!valid(token))return;
                if(w==0)showMcp();else showWorkflow(model);
            }).setNegativeButton(R.string.cancel,null).create();
        confirmation=d;d.setOnDismissListener(x->{if(confirmation==d)confirmation=null;});d.show();secure(d);
    }
    private void showMcp(){
        LinearLayout l=column();label(l,s(R.string.auto_mcp_notice));
        EditText address=field(l,R.string.cloud_pc_address,"",22,false);
        TextView status=output(l);
        LinearLayout tools=column();tools.setPadding(0,0,0,0);
        final Button[] scan=new Button[1];
        scan[0]=button(l,R.string.auto_fetch_tools,()->{
            final String target=address.getText().toString().trim();final McpLanClient client;
            try{client=new McpLanClient(target);}catch(Exception e){toast(R.string.cloud_pc_address);return;}
            confirm(s(R.string.auto_mcp),client.endpoint()+"\n\n"+s(R.string.auto_mcp_notice),()->{
                if(busy()||!target.equals(address.getText().toString().trim()))return;
                if(mcp!=null)mcp.cancel();mcp=client;tools.removeAllViews();scan[0].setEnabled(false);status.setText(R.string.thinking);
                work(client::connect,list->{
                    scan[0].setEnabled(true);
                    long eligible=list.stream().filter(tool->tool.eligible).count();
                    status.setText(s(R.string.auto_mcp_tools)+" "+eligible+"/"+list.size()+"\n"+s(R.string.auto_schema_notice));
                    for(McpLanClient.Tool tool:list){
                        if(!tool.eligible)continue;
                        Button b=new Button(owner);b.setAllCaps(false);b.setText(tool.name);b.setMinHeight(dp(48));
                        tools.addView(b,new LinearLayout.LayoutParams(-1,-2));
                        b.setOnClickListener(v->{if(!busy()&&mcp==client&&target.equals(address.getText().toString().trim()))reviewTool(client,tool,status);});
                    }
                },()->{scan[0].setEnabled(true);status.setText(R.string.cloud_fail);client.cancel();});
            });
        });
        l.addView(tools,new LinearLayout.LayoutParams(-1,-2));show(s(R.string.auto_mcp),l);
    }
    private void reviewTool(McpLanClient client,McpLanClient.Tool tool,TextView status){
        LinearLayout l=column();label(l,client.endpoint()+"\n"+tool.name+"\n"+tool.description+"\n"+tool.schema);
        label(l,s(R.string.auto_mcp_call_notice));
        EditText args=field(l,R.string.auto_arguments,"{}",16000,true);
        CheckBox allowed=new CheckBox(owner);allowed.setText(R.string.auto_tool_allow);allowed.setChecked(client.permitted(tool));l.addView(allowed);
        ScrollView scroll=new ScrollView(owner);scroll.addView(l);
        int token=epoch;
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(tool.name).setView(scroll)
            .setNegativeButton(R.string.cancel,null).setNeutralButton(R.string.auto_tool_revoke,(x,w)->client.revoke(tool)).setPositiveButton(R.string.send,null).create();
        confirmation=d;d.setOnDismissListener(x->{if(confirmation==d)confirmation=null;});d.show();secure(d);
        d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!valid(token)||busy()||mcp!=client)return;
            final String value=args.getText().toString();
            if(!allowed.isChecked()){toast(R.string.auto_tool_allow);return;}
            if(!client.validArguments(tool,value)){toast(R.string.auto_schema_notice);return;}
            d.dismiss();
            confirm(tool.name,client.endpoint()+"\n\n"+value+"\n\n"+s(R.string.auto_mcp_call_notice),()->{
                if(mcp!=client||busy())return;
                client.authorize(tool);
                status.setText(R.string.thinking);
                work(()->client.invoke(tool,value),result->status.setText((result.optBoolean("isError",false)?s(R.string.auto_mcp_failed)+"\n":"")+result.toString()),()->status.setText(R.string.auto_mcp_failed));
            });
        });
    }
    private void showWorkflow(String model){
        LinearLayout l=column();label(l,s(R.string.auto_workflow_notice));label(l,s(R.string.auto_verifier_help));label(l,s(R.string.model_label)+": "+model);
        final LocalWorkflow runner=new LocalWorkflow(owner);workflow=runner;
        LocalWorkflow.Checkpoint saved=null;
        try{saved=runner.load();}catch(Exception e){toast(R.string.auto_checkpoint_error);}
        EditText plan=field(l,R.string.auto_plan,saved==null?"":saved.plan,24000,true);
        TextView status=output(l);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ProgressBar progress=new ProgressBar(owner,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(10);l.addView(progress,new LinearLayout.LayoutParams(-1,dp(12)));
        final LocalWorkflow.Checkpoint[] checkpoint={saved};
        if(saved!=null){display(status,saved);progress.setMax(saved.steps().size());progress.setProgress(saved.results.size());}
        Button[] buttons=new Button[8];
        Runnable enabled=()->{boolean idle=!busy();plan.setEnabled(idle);for(Button b:buttons)if(b!=null)b.setEnabled(idle);};
        Consumer<LocalWorkflow.Checkpoint> start=c->{
            if(!c.model.equals(model)){toast(R.string.need_model);return;}
            if(!c.resumable()){display(status,c);toast(R.string.auto_blocked);return;}
            int token=epoch;runner.prepare();progress.setMax(c.steps().size());progress.setProgress(c.results.size());
            status.setText(R.string.thinking);
            work(()->runner.run(c,state->{String message=summary(state);int done=state.results.size();owner.runOnUiThread(()->{if(valid(token)){status.setText(message);progress.setProgress(done);}});},()->stopped),
                result->{checkpoint[0]=result;display(status,result);progress.setProgress(result.results.size());enabled.run();},
                ()->{try{checkpoint[0]=runner.load();if(checkpoint[0]!=null)display(status,checkpoint[0]);}catch(Exception ignored){}toast(R.string.auto_checkpoint_error);enabled.run();});
            enabled.run();
        };
        buttons[0]=button(l,R.string.auto_start,()->{
            if(busy())return;final String spec=plan.getText().toString();
            try{WorkflowPolicy.parse(spec);}catch(Exception e){toast(R.string.auto_invalid_plan);return;}
            if(model==null||model.isEmpty()||!OllamaForegroundService.isRunning()){toast(R.string.test_need_model);return;}
            confirm(s(R.string.auto_start),s(R.string.auto_workflow_notice)+"\n\n"+s(R.string.auto_history_notice)+"\n\n"+spec,()->{
                if(busy())return;
                try{checkpoint[0]=runner.create(spec,model);start.accept(checkpoint[0]);}catch(Exception e){toast(R.string.auto_checkpoint_error);}
            });
        });
        buttons[1]=button(l,R.string.auto_resume,()->{
            if(busy()||checkpoint[0]==null){toast(R.string.auto_blocked);return;}
            LocalWorkflow.Checkpoint c=checkpoint[0];
            if(!c.plan.equals(plan.getText().toString())||!c.model.equals(model)){toast(R.string.auto_invalid_plan);return;}
            confirm(s(R.string.auto_resume),s(R.string.auto_workflow_notice)+"\n\n"+summary(c)+"\n"+s(R.string.auto_resume_remaining),()->start.accept(c));
        });
        buttons[3]=button(l,R.string.auto_example,()->{if(busy())return;confirm(s(R.string.auto_example),s(R.string.auto_example_text),()->plan.setText(s(R.string.auto_example_text)));});
        buttons[4]=button(l,R.string.auto_reorder,()->{if(!busy())reorder(plan);});
        buttons[5]=button(l,R.string.auto_templates,()->{if(!busy())showTemplates(runner,plan);});
        buttons[6]=button(l,R.string.auto_history,()->{if(!busy())showHistory(runner);});
        buttons[2]=button(l,R.string.auto_clear,()->{
            if(busy())return;
            confirm(s(R.string.auto_clear),s(R.string.auto_clear_current_notice),()->{try{runner.clear();checkpoint[0]=null;plan.setText("");status.setText("");progress.setProgress(0);}catch(Exception e){toast(R.string.auto_checkpoint_error);}});
        });
        button(l,R.string.auto_cancel_run,()->{
            if(checkpoint[0]==null)return;
            confirm(s(R.string.auto_cancel_run),s(R.string.auto_cancel_notice),()->{
                if(busy()){runner.cancel();status.setText(R.string.auto_cancel_notice);}
                else try{runner.cancelSaved(checkpoint[0]);display(status,checkpoint[0]);}catch(Exception e){toast(R.string.auto_checkpoint_error);}
            });
        });
        buttons[7]=button(l,R.string.auto_journal,()->{
            if(busy()||checkpoint[0]==null)return;
            showJournal(checkpoint[0]);
        });
        button(l,R.string.auto_pause_now,()->{
            if(busy()){runner.pause();status.setText(R.string.auto_pause_requested);}
        });
        button(l,R.string.auto_pause,()->{pause();});
        show(s(R.string.auto_workflow),l);
    }
    private String date(long millis){
        java.util.Date value=new java.util.Date(millis);
        return android.text.format.DateFormat.getDateFormat(owner).format(value)+" "+android.text.format.DateFormat.getTimeFormat(owner).format(value);
    }
    private void trackDialog(AlertDialog d){
        confirmation=d;d.setOnDismissListener(x->{if(confirmation==d)confirmation=null;});d.show();secure(d);
    }
    private void showTemplates(LocalWorkflow runner,EditText plan){
        final List<WorkflowLibrary.Template> items;
        try{items=runner.templates();}catch(Exception e){toast(R.string.auto_library_error);return;}
        String[] rows=new String[items.size()+1];rows[0]=s(R.string.auto_template_save);
        for(int i=0;i<items.size();i++)rows[i+1]=items.get(i).name+" · "+date(items.get(i).saved);
        final int token=epoch;
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(R.string.auto_templates).setItems(rows,(x,index)->{
            if(!valid(token)||busy())return;
            if(index==0){saveTemplate(runner,plan);return;}
            WorkflowLibrary.Template item=items.get(index-1);
            LinearLayout panel=column();label(panel,s(R.string.auto_template_load_notice));
            TextView body=output(panel);body.setText(item.plan);
            ScrollView sc=new ScrollView(owner);sc.addView(panel);
            AlertDialog view=new AlertDialog.Builder(owner).setTitle(item.name).setView(sc)
                .setNegativeButton(R.string.close,null)
                .setPositiveButton(R.string.auto_template_load,(z,w)->{if(valid(token)&&!busy())plan.setText(item.plan);})
                .setNeutralButton(R.string.auto_delete_item,(z,w)->{
                    if(!valid(token)||busy())return;
                    confirm(s(R.string.auto_delete_item),item.name,()->{
                        if(busy())return;
                        try{runner.deleteTemplate(item.id);toast(R.string.auto_saved);}catch(Exception e){toast(R.string.auto_library_error);}
                    });
                }).create();
            trackDialog(view);
        }).setNegativeButton(R.string.close,null).create();trackDialog(d);
    }
    private void saveTemplate(LocalWorkflow runner,EditText plan){
        final String captured=plan.getText().toString();
        try{WorkflowPolicy.parse(captured);}catch(Exception e){toast(R.string.auto_invalid_plan);return;}
        LinearLayout l=column();label(l,s(R.string.auto_template_notice));
        EditText title=field(l,R.string.auto_template_name,"",60,false);
        final int token=epoch;
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(R.string.auto_template_save).setView(l)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok,null).create();
        trackDialog(d);d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!valid(token)||busy()||!captured.equals(plan.getText().toString()))return;
            try{runner.saveTemplate(title.getText().toString(),captured);d.dismiss();toast(R.string.auto_saved);}
            catch(Exception e){toast(R.string.auto_library_error);}
        });
    }
    private void showHistory(LocalWorkflow runner){
        final List<LocalWorkflow.ArchivedRun> rows;
        try{rows=runner.history();}catch(Exception e){
            confirm(s(R.string.auto_history_clear),s(R.string.auto_history_recovery),()->{
                if(!busy())try{runner.clearHistory();toast(R.string.auto_saved);}catch(Exception ex){toast(R.string.auto_library_error);}
            });return;
        }
        final int token=epoch;
        String[] labels=new String[rows.size()];
        for(int i=0;i<rows.size();i++){
            LocalWorkflow.ArchivedRun r=rows.get(i);LocalWorkflow.Checkpoint c=r.checkpoint;
            labels[i]=date(r.archived)+" · "+stateLabel(c.status)+" · "+c.results.size()+"/"+c.steps().size()+"\n"+c.model;
        }
        AlertDialog.Builder builder=new AlertDialog.Builder(owner).setTitle(R.string.auto_history)
            .setNegativeButton(R.string.close,null)
            .setNeutralButton(R.string.auto_history_clear,(x,w)->{
                if(!valid(token)||busy())return;
                confirm(s(R.string.auto_history_clear),s(R.string.auto_history_clear_notice),()->{
                    if(!busy())try{runner.clearHistory();toast(R.string.auto_saved);}catch(Exception ex){toast(R.string.auto_library_error);}
                });
            });
        if(rows.isEmpty())builder.setMessage(R.string.auto_history_empty);
        else builder.setItems(labels,(x,index)->{if(valid(token)&&!busy())showArchived(runner,rows.get(index));});
        trackDialog(builder.create());
    }
    private void showArchived(LocalWorkflow runner,LocalWorkflow.ArchivedRun run){
        LocalWorkflow.Checkpoint c=run.checkpoint;
        LinearLayout l=column();label(l,s(R.string.auto_archive_readonly));label(l,date(run.archived)+"\n"+c.model);
        TextView record=output(l);display(record,c);
        button(l,R.string.auto_journal,()->{if(!busy())showJournal(c);});
        TextView source=output(l);source.setText(s(R.string.auto_plan)+"\n"+c.plan);
        ScrollView sc=new ScrollView(owner);sc.addView(l);final int token=epoch;
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(R.string.auto_history).setView(sc)
            .setNegativeButton(R.string.close,null)
            .setNeutralButton(R.string.auto_delete_item,(x,w)->{
                if(!valid(token)||busy())return;
                confirm(s(R.string.auto_delete_item),s(R.string.auto_archive_readonly),()->{
                    if(!busy())try{runner.deleteHistory(c.id);toast(R.string.auto_saved);}catch(Exception e){toast(R.string.auto_library_error);}
                });
            }).create();trackDialog(d);
    }
    private String stateLabel(String state){
        switch(state){
            case "QUEUED":return s(R.string.auto_state_queued);
            case "RUNNING":return s(R.string.auto_state_running);
            case "VERIFYING":return s(R.string.auto_state_verifying);
            case "RETRY_WAIT":return s(R.string.auto_state_retry);
            case "PAUSED":return s(R.string.auto_state_paused);
            case "BLOCKED":return s(R.string.auto_state_blocked);
            case "COMPLETED":return s(R.string.auto_state_completed);
            case "CANCELLED":return s(R.string.auto_state_cancelled);
            default:return state;
        }
    }
    private void reorder(EditText plan){
        final String original=plan.getText().toString();
        final List<String> lines=new ArrayList<>();
        try{
            WorkflowPolicy.parse(original);
            for(String row:original.split("\\r?\\n"))if(!row.trim().isEmpty())lines.add(row);
        }catch(Exception e){toast(R.string.auto_invalid_plan);return;}
        if(lines.size()<2)return;
        final int token=epoch;
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(R.string.auto_reorder)
            .setItems(lines.toArray(new String[0]),(x,index)->{
                if(!valid(token)||busy())return;
                AlertDialog move=new AlertDialog.Builder(owner).setTitle(R.string.auto_reorder)
                    .setItems(new String[]{s(R.string.auto_move_up),s(R.string.auto_move_down)},(y,direction)->{
                        if(!valid(token)||busy()||!original.equals(plan.getText().toString()))return;
                        int dest=index+(direction==0?-1:1);
                        if(dest>=0&&dest<lines.size()){java.util.Collections.swap(lines,index,dest);plan.setText(String.join("\n",lines));}
                    }).setNegativeButton(R.string.cancel,null).create();
                confirmation=move;move.setOnDismissListener(z->{if(confirmation==move)confirmation=null;});move.show();secure(move);
            }).setNegativeButton(R.string.cancel,null).create();
        confirmation=d;d.setOnDismissListener(x->{if(confirmation==d)confirmation=null;});d.show();secure(d);
    }
    private String reasonLabel(WorkflowRunJournal.Reason reason){
        switch(reason){
            case NONE:return "";
            case VERIFIED:return s(R.string.auto_reason_verified);
            case VERIFY_FAILED:return s(R.string.auto_reason_verify_failed);
            case TRUNCATED:return s(R.string.auto_reason_truncated);
            case ATTEMPT_LIMIT:return s(R.string.auto_reason_attempt_limit);
            case USER_PAUSE:return s(R.string.auto_state_paused);
            case USER_CANCEL:return s(R.string.auto_state_cancelled);
            case RECOVERED:return s(R.string.auto_reason_recovered);
            case TIME_LIMIT:return s(R.string.auto_reason_time_limit);
            case THERMAL:return s(R.string.bench_hot);
            case MEMORY:return s(R.string.bench_memory);
            case SERVER_OFF:return s(R.string.test_need_server);
            case COOP_ACTIVE:return s(R.string.guest_stop_first);
            case OUTPUT_LIMIT:return s(R.string.auto_reason_output_limit);
            case INVALID_TOKENS:return s(R.string.auto_reason_invalid_tokens);
            case TRANSPORT:return s(R.string.auto_reason_transport);
            case INTERRUPTED:return s(R.string.auto_reason_recovered);
            default:return s(R.string.metric_unavailable);
        }
    }
    private String journalText(LocalWorkflow.Checkpoint c){
        List<WorkflowRunJournal.Event> events=c.journal.events();
        if(events.isEmpty())return s(R.string.auto_journal_empty);
        StringBuilder out=new StringBuilder(s(R.string.auto_journal_notice));
        for(WorkflowRunJournal.Event event:events){
            out.append("\n\n").append(date(event.at)).append(" · ").append(stateLabel(event.state.name()));
            out.append("\n").append(owner.getString(R.string.auto_journal_counts,Integer.toString(event.completed),Integer.toString(c.steps().size()),Integer.toString(event.attempt),Integer.toString(event.total)));
            String reason=reasonLabel(event.reason);if(!reason.isEmpty())out.append("\n").append(reason);
        }
        return out.toString();
    }
    private void showJournal(LocalWorkflow.Checkpoint c){
        LinearLayout l=column();TextView details=output(l);details.setText(journalText(c));
        ScrollView sc=new ScrollView(owner);sc.addView(l);
        trackDialog(new AlertDialog.Builder(owner).setTitle(R.string.auto_journal).setView(sc)
            .setPositiveButton(R.string.close,null).create());
    }
    private String summary(LocalWorkflow.Checkpoint c){
        String seconds=c.updated<=0?s(R.string.metric_unavailable):Long.toString(Math.max(0,Math.min(WorkflowPolicy.MAX_MILLIS,c.updated-c.started))/1000);
        String reason=reasonLabel(c.reason);
        String next=c.complete()?"":"\n"+owner.getString(R.string.auto_current_step,Integer.toString(c.results.size()+1),Integer.toString(c.steps().size()),c.steps().get(c.results.size()).gate.name());
        return stateLabel(c.status)+" · "+c.results.size()+"/"+c.steps().size()+" · "+c.attempts+"/"+WorkflowPolicy.MAX_ATTEMPTS+
            (reason.isEmpty()?"":"\n"+reason)+next+
            "\n"+owner.getString(R.string.auto_run_stats,seconds,Integer.toString(c.totalAttempts),Long.toString(c.reportedTokens),Integer.toString(c.unknownAttempts))+
            (c.legacyStats?"\n"+s(R.string.auto_legacy_stats):"");
    }
    private void display(TextView status,LocalWorkflow.Checkpoint c){
        StringBuilder text=new StringBuilder(summary(c));
        for(int i=0;i<c.results.size();i++)text.append("\n\n").append(i+1).append(". PASS (").append(c.steps().get(i).gate.name()).append(")\n").append(c.results.get(i));
        status.setText(text.toString());
    }
}
