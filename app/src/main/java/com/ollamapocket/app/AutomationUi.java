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
    private LocalWorkflowPlanner planner;
    private LocalConferenceRunner conference;
    private ProjectWorkspaceUi reportWorkspace;
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
        if(reportWorkspace!=null){reportWorkspace.pause();reportWorkspace=null;}
        if(mcp!=null){mcp.cancel();mcp=null;}
        if(workflow!=null)workflow.pause();
        if(planner!=null){planner.cancel();planner=null;}
        if(conference!=null){conference.cancel();conference=null;}
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
            finally{worker=null;}
            final T value=result;final boolean done=ok;
            owner.runOnUiThread(()->{BUSY.set(false);if(valid(token)){if(done)success.accept(value);else failure.run();}});
        },"onhand-automation");
        worker=t;t.start();
    }
    void open(String model){
        if(busy()){toast(R.string.busy);return;}
        stopped=false;epoch++;
        final int token=epoch;
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(R.string.auto_title)
            .setItems(new String[]{s(R.string.auto_mcp),s(R.string.auto_workflow),s(R.string.conference_title)},(x,w)->{
                if(!valid(token))return;
                if(w==0)showMcp();else if(w==1)showWorkflow(model);else showConference(model);
            }).setNegativeButton(R.string.cancel,null).create();
        confirmation=d;d.setOnDismissListener(x->{if(confirmation==d)confirmation=null;});d.show();secure(d);
    }
    private String conferenceRole(ConferencePolicy.Role role){
        switch(role){
            case PLANNER:return s(R.string.conference_planner);
            case PRODUCER:return s(R.string.conference_producer);
            default:return s(R.string.conference_reviewer);
        }
    }
    private String conferenceState(ConferencePolicy.Result result){
        switch(result.status){
            case COMPLETE:return s(R.string.conference_complete);
            case BLOCKED:return s(R.string.conference_blocked);
            case STOPPED:return s(R.string.conference_stopped);
            case FAILED:
                if(result.cause==ConferencePolicy.Cause.IMPOSSIBLE_CONDITION)return s(R.string.conference_capacity_impossible);
                if(result.cause==ConferencePolicy.Cause.CONTEXT)return s(R.string.conference_context);
                if(result.cause==ConferencePolicy.Cause.INVALID_REPLY)return s(R.string.conference_invalid_reply);
                return s(R.string.conference_failed);
            default:return result.activeRole==null?s(R.string.thinking):conferenceRole(result.activeRole)+" · "+s(R.string.thinking);
        }
    }
    private String conferenceRecommendation(ConferencePolicy.Recommendation recommendation){
        return recommendation==ConferencePolicy.Recommendation.ACCEPT?s(R.string.conference_accept):s(R.string.conference_revise);
    }
    private String conferencePredicate(WorkflowPolicy.Sample sample){
        if(sample==null)return s(R.string.conference_pending);
        return sample==WorkflowPolicy.Sample.MATCH?s(R.string.conference_match):s(R.string.conference_mismatch);
    }
    private String conferenceCapacity(ConferencePolicy.Capacity cap){
        if(!cap.ready())return s(cap.cause==ConferencePolicy.Cause.IMPOSSIBLE_CONDITION?
            R.string.conference_capacity_impossible:R.string.conference_capacity_insufficient);
        return owner.getString(R.string.conference_capacity_summary,Integer.toString(cap.plan),Integer.toString(cap.artifact),
            Integer.toString(cap.issues),Integer.toString(cap.producerTokens))+"\n"+s(R.string.conference_capacity_estimate);
    }
    private String conferenceReport(ConferencePolicy.Request request,ConferencePolicy.Result result){
        StringBuilder out=new StringBuilder(conferenceState(result));
        out.append("\n").append(owner.getString(R.string.conference_calls,Integer.toString(result.attempts),Integer.toString(ConferencePolicy.MAX_CALLS)))
            .append("\n").append(s(R.string.model_label)).append(": ").append(request.model)
            .append("\n").append(s(R.string.conference_goal)).append(": ").append(request.goal)
            .append("\n").append(gateLabel(request.condition.gate)).append(": ").append(request.condition.required)
            .append("\n").append(conferenceCapacity(ConferencePolicy.capacity(request)))
            .append("\n").append(s(R.string.conference_evidence_notice));
        if(result.artifact!=null){
            out.append("\n\n").append(s(R.string.conference_current)).append("\n").append(result.artifact)
                .append("\n").append(conferencePredicate(result.predicate))
                .append("\n").append(result.recommendation==null?s(R.string.conference_pending):conferenceRecommendation(result.recommendation));
        }
        for(ConferencePolicy.Turn turn:result.turns){
            out.append("\n\n").append(conferenceRole(turn.role)).append(" · ").append(turn.round);
            if(turn.predicate!=null)out.append("\n").append(conferencePredicate(turn.predicate));
            if(turn.recommendation!=null)out.append("\n").append(conferenceRecommendation(turn.recommendation));
            if(!turn.text.isEmpty())out.append("\n").append(turn.text);
        }
        return out.toString();
    }
    private String conferenceTerminalReport(ConferenceReport report){
        return conferenceReport(report.request,report.result)+"\n\n"+
            owner.getString(R.string.conference_elapsed,Long.toString(report.elapsedMillis))+
            (report.stopReason==null?"":"\n"+s(R.string.conference_stop_reason)+": "+report.stopReason);
    }
    private void copyConference(ConferenceReport[] current,int title,String value,String extra){
        if(busy()||current[0]==null)return;
        final ConferenceReport reviewed=current[0];
        confirm(s(title),s(R.string.attachment_copy_notice)+(extra.isEmpty()?"":"\n\n"+extra),()->{
            if(busy()||current[0]!=reviewed)return;
            try{
                android.content.ClipboardManager clipboard=(android.content.ClipboardManager)owner.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if(clipboard==null)throw new IllegalStateException("Clipboard unavailable");
                clipboard.setPrimaryClip(SensitiveClipboard.text(s(R.string.conference_title),value));toast(R.string.copied);
            }catch(RuntimeException e){toast(R.string.batch_export_failed);}
        });
    }
    private void showConference(String model){
        if(busy()||model==null||model.isEmpty()){toast(R.string.test_need_model);return;}
        final int token=epoch;
        final String language=OnHandsLocale.normalize(owner.getResources().getConfiguration().getLocales().get(0));
        LinearLayout panel=column();
        label(panel,s(R.string.conference_notice));label(panel,s(R.string.model_label)+": "+model);
        EditText goal=field(panel,R.string.conference_goal,"",ConferencePolicy.MAX_GOAL+2,false);
        goal.setSingleLine(true);
        label(panel,s(R.string.auto_verification_type));
        Spinner picker=new Spinner(owner);
        WorkflowPolicy.Gate[] gates=WorkflowPolicy.Gate.values();
        String[] names=new String[gates.length];for(int i=0;i<gates.length;i++)names[i]=gateLabel(gates[i]);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(owner,android.R.layout.simple_spinner_item,names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);picker.setAdapter(adapter);
        picker.setMinimumHeight(dp(48));panel.addView(picker,new LinearLayout.LayoutParams(-1,-2));
        picker.setSelection(WorkflowPolicy.Gate.MIN_CHARS.ordinal());
        EditText required=field(panel,R.string.auto_step_condition,"",202,false);
        required.setHint(gateHint(WorkflowPolicy.Gate.MIN_CHARS));
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                if(position>=0&&position<gates.length)required.setHint(gateHint(gates[position]));
            }
            public void onNothingSelected(AdapterView<?> parent){}
        });
        java.util.function.Supplier<ConferencePolicy.Request> capture=()->{
            int i=picker.getSelectedItemPosition();
            if(i<0||i>=gates.length)throw new IllegalArgumentException("Condition");
            return new ConferencePolicy.Request(goal.getText().toString(),model,language,gates[i],required.getText().toString());
        };
        TextView capacityStatus=output(panel);capacityStatus.setTextIsSelectable(false);
        capacityStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        TextView status=output(panel);status.setTextIsSelectable(false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        final Button[] controls=new Button[7];
        final ConferencePolicy.Result[] latest={null};
        final ConferenceReport[] terminal={null};
        final boolean[] stopRequested={false};
        Runnable enabled=()->{
            boolean idle=!busy();
            goal.setEnabled(idle);picker.setEnabled(idle);required.setEnabled(idle);
            controls[0].setEnabled(idle);controls[3].setEnabled(idle);
            controls[1].setEnabled(!idle&&conference!=null&&!stopRequested[0]);
            controls[2].setEnabled(idle&&terminal[0]!=null);
            controls[4].setEnabled(idle&&terminal[0]!=null&&terminal[0].hasArtifact());
            controls[5].setEnabled(idle&&terminal[0]!=null);
            controls[6].setEnabled(idle&&terminal[0]!=null);
        };
        android.text.TextWatcher resetCapacity=new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence text,int start,int count,int after){}
            public void onTextChanged(CharSequence text,int start,int before,int count){capacityStatus.setText("");}
            public void afterTextChanged(android.text.Editable text){}
        };
        goal.addTextChangedListener(resetCapacity);required.addTextChangedListener(resetCapacity);
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                capacityStatus.setText("");
                if(position>=0&&position<gates.length)required.setHint(gateHint(gates[position]));
            }
            public void onNothingSelected(AdapterView<?> parent){capacityStatus.setText("");}
        });
        controls[3]=button(panel,R.string.conference_capacity_check,()->{
            if(!valid(token)||busy())return;
            try{capacityStatus.setText(conferenceCapacity(ConferencePolicy.capacity(capture.get())));}
            catch(IllegalArgumentException e){capacityStatus.setText(R.string.auto_invalid_plan);}
        });
        controls[0]=button(panel,R.string.conference_start,()->{
            if(!valid(token)||busy())return;
            final ConferencePolicy.Request request;
            try{request=capture.get();}catch(IllegalArgumentException e){toast(R.string.auto_invalid_plan);return;}
            ConferencePolicy.Capacity cap=ConferencePolicy.capacity(request);
            capacityStatus.setText(conferenceCapacity(cap));
            if(!cap.ready())return;
            if(!OllamaForegroundService.isRunning()){toast(R.string.test_need_server);return;}
            if(!"off".equals(CoopSession.get(owner).role())){toast(R.string.guest_stop_first);return;}
            confirm(s(R.string.conference_start),s(R.string.conference_notice)+"\n\n"+s(R.string.model_label)+": "+request.model+
                "\n"+request.goal+"\n"+gateLabel(request.condition.gate)+": "+request.condition.required+
                "\n\n"+conferenceCapacity(cap),()->{
                if(!valid(token)||busy())return;
                try{if(!request.same(capture.get())){toast(R.string.auto_preview_first);return;}}
                catch(IllegalArgumentException e){toast(R.string.auto_invalid_plan);return;}
                InferenceSafetyPolicy.Reason risk=InferenceSafety.check(owner);
                if(risk!=InferenceSafetyPolicy.Reason.NONE){toast(InferenceSafety.label(risk));return;}
                if(!OllamaForegroundService.isRunning()){toast(R.string.test_need_server);return;}
                if(!"off".equals(CoopSession.get(owner).role())){toast(R.string.guest_stop_first);return;}
                final LocalConferenceRunner runner=new LocalConferenceRunner(owner);
                conference=runner;latest[0]=null;terminal[0]=null;stopRequested[0]=false;
                status.setText(R.string.thinking);
                work(()->runner.run(request,value->owner.runOnUiThread(()->{
                    if(valid(token)&&conference==runner&&!stopRequested[0]){
                        latest[0]=value;status.setText(conferenceReport(request,value));
                    }
                })),value->{
                    if(conference!=runner)return;
                    conference=null;
                    latest[0]=stopRequested[0]?value.stopped():value;
                    String stopReason=null;
                    if(latest[0].status==ConferencePolicy.Status.STOPPED)
                        stopReason=runner.reason()!=null?runner.reason().name():stopRequested[0]?"USER":"UNKNOWN";
                    try{
                        terminal[0]=new ConferenceReport(request,latest[0],stopReason,runner.elapsedMillis());
                        status.setText(conferenceTerminalReport(terminal[0]));
                    }catch(IllegalArgumentException e){
                        terminal[0]=null;status.setText(R.string.conference_report_invalid);
                    }
                    enabled.run();
                },()->{
                    if(conference!=runner)return;
                    conference=null;terminal[0]=null;latest[0]=null;status.setText(R.string.conference_failed);enabled.run();
                });
                enabled.run();
            });
        });
        controls[1]=button(panel,R.string.stop,()->{
            if(conference!=null&&busy()&&!stopRequested[0]){
                stopRequested[0]=true;conference.cancel();status.setText(R.string.auto_pause_requested);enabled.run();
            }
        });
        controls[2]=button(panel,R.string.conference_copy,()->{
            if(busy()||terminal[0]==null)return;
            copyConference(terminal,R.string.conference_copy,conferenceTerminalReport(terminal[0]),"");
        });
        controls[4]=button(panel,R.string.conference_copy_artifact,()->{
            if(busy()||terminal[0]==null||!terminal[0].hasArtifact())return;
            ConferenceReport report=terminal[0];
            String notice=(report.completed()?s(R.string.conference_evidence_notice):s(R.string.conference_partial_copy))+
                "\n"+conferenceState(report.result)+"\n"+conferencePredicate(report.result.predicate)+"\n"+
                (report.result.recommendation==null?s(R.string.conference_pending):conferenceRecommendation(report.result.recommendation));
            copyConference(terminal,R.string.conference_copy_artifact,report.result.artifact,notice);
        });
        controls[5]=button(panel,R.string.conference_copy_json,()->{
            if(busy()||terminal[0]==null)return;
            try{copyConference(terminal,R.string.conference_copy_json,terminal[0].json(),s(R.string.conference_json_notice));}
            catch(Exception e){toast(R.string.batch_export_failed);}
        });
        controls[6]=button(panel,R.string.project_report_save,()->{
            if(!valid(token)||busy()||ProjectWorkspaceUi.busy()||terminal[0]==null)return;
            final ConferenceReport reviewed=terminal[0];
            try{
                ProjectReportHandoff handoff=ProjectReportHandoff.of(reviewed);
                reportWorkspace=new ProjectWorkspaceUi(owner,()->valid(token)&&!busy()&&terminal[0]==reviewed&&dialog!=null&&dialog.isShowing());
                reportWorkspace.openReport(handoff,s(R.string.conference_title)+" · "+handoff.status);
            }catch(ProjectReportHandoff.TooLarge e){toast(R.string.project_report_too_large);}
            catch(Exception e){toast(R.string.batch_export_failed);}
        });
        enabled.run();show(s(R.string.conference_title),panel);
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
        Button[] buttons=new Button[11];
        EditText plan=field(l,R.string.auto_plan,saved==null?"":saved.plan,24000,true);
        final LocalWorkflow.Checkpoint[] checkpoint={saved};
        buttons[8]=button(l,R.string.auto_visual_editor,()->{
            if(!busy())showVisualEditor(plan,checkpoint[0]);
        });
        buttons[9]=button(l,R.string.auto_business_templates,()->{
            if(!busy())showBusinessTemplates(plan);
        });
        TextView status=output(l);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ProgressBar progress=new ProgressBar(owner,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(10);l.addView(progress,new LinearLayout.LayoutParams(-1,dp(12)));
        if(saved!=null){display(status,saved);progress.setMax(saved.steps().size());progress.setProgress(saved.results.size());}
        Runnable enabled=()->{boolean idle=!busy();plan.setEnabled(idle);for(Button b:buttons)if(b!=null)b.setEnabled(idle);};
        buttons[10]=button(l,R.string.auto_goal_planner,()->{if(!busy())showGoalPlanner(model,plan,enabled);});
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
            if(checkpoint[0]!=null&&checkpoint[0].format==1&&checkpoint[0].plan.equals(spec)){toast(R.string.auto_legacy_convert);return;}
            try{WorkflowPolicy.parse(spec);}catch(Exception e){toast(R.string.auto_invalid_plan);return;}
            if(model==null||model.isEmpty()||!OllamaForegroundService.isRunning()){toast(R.string.test_need_model);return;}
            confirm(s(R.string.auto_start),s(R.string.auto_workflow_notice)+"\n\n"+s(R.string.auto_history_notice)+"\n\n"+spec,()->{
                if(busy()||!spec.equals(plan.getText().toString()))return;
                try{checkpoint[0]=runner.create(spec,model);start.accept(checkpoint[0]);}catch(Exception e){toast(R.string.auto_checkpoint_error);}
            });
        });
        buttons[1]=button(l,R.string.auto_resume,()->{
            if(busy()||checkpoint[0]==null){toast(R.string.auto_blocked);return;}
            LocalWorkflow.Checkpoint c=checkpoint[0];
            if(!c.plan.equals(plan.getText().toString())||!c.model.equals(model)){toast(R.string.auto_invalid_plan);return;}
            if(!c.resumable()){display(status,c);toast(R.string.auto_blocked);return;}
            final String reviewed;
            try{reviewed=runner.reviewSnapshot(c);}catch(Exception e){toast(R.string.auto_checkpoint_error);return;}
            confirm(s(R.string.auto_resume),s(R.string.auto_workflow_notice)+"\n\n"+summary(c)+"\n"+s(R.string.auto_resume_remaining),()->{
                if(busy()||checkpoint[0]!=c){toast(R.string.auto_resume_changed);return;}
                try{
                    LocalWorkflow.Checkpoint fresh=runner.reviewedResume(reviewed,plan.getText().toString(),model);
                    if(fresh==null){toast(R.string.auto_resume_changed);return;}
                    if(!OllamaForegroundService.isRunning()){toast(R.string.test_need_server);return;}
                    if(!"off".equals(CoopSession.get(owner).role())){toast(R.string.guest_stop_first);return;}
                    InferenceSafetyPolicy.Reason risk=InferenceSafety.check(owner);
                    if(risk!=InferenceSafetyPolicy.Reason.NONE){toast(InferenceSafety.label(risk));return;}
                    checkpoint[0]=fresh;start.accept(fresh);
                }catch(Exception e){toast(R.string.auto_checkpoint_error);}
            });
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
            if(planner!=null&&busy()){toast(R.string.busy);return;}
            if(checkpoint[0]==null)return;
            confirm(s(R.string.auto_cancel_run),s(R.string.auto_cancel_notice),()->{
                if(planner!=null&&busy()){toast(R.string.busy);return;}
                if(busy()){runner.cancel();status.setText(R.string.auto_cancel_notice);}
                else try{runner.cancelSaved(checkpoint[0]);display(status,checkpoint[0]);}catch(Exception e){toast(R.string.auto_checkpoint_error);}
            });
        });
        buttons[7]=button(l,R.string.auto_journal,()->{
            if(busy()||checkpoint[0]==null)return;
            showJournal(checkpoint[0]);
        });
        button(l,R.string.auto_pause_now,()->{
            if(planner!=null&&busy()){planner.cancel();return;}
            if(busy()){runner.pause();status.setText(R.string.auto_pause_requested);}
        });
        button(l,R.string.auto_pause,()->{pause();});
        show(s(R.string.auto_workflow),l);
    }
    private String gateLabel(WorkflowPolicy.Gate gate){
        switch(gate){
            case EXACT:return s(R.string.auto_gate_exact);
            case MIN_CHARS:return s(R.string.auto_gate_minchars);
            case JSON_KEYS:return s(R.string.auto_gate_jsonkeys);
            case JSON_FIELDS:return s(R.string.auto_gate_jsonfields);
            case JSON_VALUES:return s(R.string.auto_gate_jsonvalues);
            default:return s(R.string.auto_gate_contains);
        }
    }
    private String gateHint(WorkflowPolicy.Gate gate){
        switch(gate){
            case EXACT:return s(R.string.auto_gate_exact_hint);
            case MIN_CHARS:return "80";
            case JSON_KEYS:return "title,result";
            case JSON_FIELDS:return "title:string,items:array";
            case JSON_VALUES:return "{\"status\":\"OK\",\"count\":3}";
            default:return s(R.string.auto_gate_contains_hint);
        }
    }
    private int sampleLabel(WorkflowPolicy.Sample result){
        switch(result){
            case MATCH:return R.string.auto_sample_match;
            case MISMATCH:return R.string.auto_sample_mismatch;
            case EMPTY:return R.string.auto_sample_empty;
            case OVERSIZED:return R.string.auto_sample_oversized;
            case TRUNCATED:return R.string.auto_sample_rejected_truncated;
            default:return R.string.auto_sample_invalid;
        }
    }
    private void showVisualEditor(EditText plan,LocalWorkflow.Checkpoint checkpoint){
        final String original=plan.getText().toString();
        final WorkflowPlanEditor editor;
        try{
            editor=new WorkflowPlanEditor(original,checkpoint!=null&&checkpoint.plan.equals(original)?checkpoint.format:2);
        }catch(IllegalArgumentException e){toast(R.string.auto_invalid_plan);return;}
        final int token=epoch;
        LinearLayout l=column();
        label(l,s(R.string.auto_visual_notice));
        TextView stepLabel=output(l);
        EditText prompt=field(l,R.string.auto_step_prompt,"",WorkflowPolicy.MAX_STEP_CHARS,true);
        prompt.setMinLines(3);prompt.setMaxLines(6);
        label(l,s(R.string.auto_verification_type));
        Spinner picker=new Spinner(owner);
        WorkflowPolicy.Gate[] gates=WorkflowPolicy.Gate.values();
        String[] labels=new String[gates.length];
        for(int i=0;i<gates.length;i++)labels[i]=gateLabel(gates[i]);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(owner,android.R.layout.simple_spinner_item,labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);picker.setAdapter(adapter);
        picker.setMinimumHeight(dp(48));l.addView(picker,new LinearLayout.LayoutParams(-1,-2));
        EditText required=field(l,R.string.auto_step_condition,"",200,false);
        TextView gateHelp=output(l);gateHelp.setText(s(R.string.auto_gate_jsonvalues_help));gateHelp.setVisibility(View.GONE);
        TextView error=output(l);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        label(l,s(R.string.auto_sample_notice));
        // Permit one extra code point so an over-limit paste cannot silently become a passing sample.
        EditText sample=field(l,R.string.auto_sample_text,"",WorkflowPolicy.MAX_OUTPUT+2,true);
        sample.setMinLines(2);sample.setMaxLines(5);
        CheckBox truncated=new CheckBox(owner);truncated.setText(R.string.auto_sample_truncated);
        truncated.setSaveEnabled(false);l.addView(truncated,new LinearLayout.LayoutParams(-1,-2));
        TextView sampleResult=output(l);
        sampleResult.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        final int[] index={0};
        final Button[] controls=new Button[6];
        Runnable capture=()->{
            WorkflowPlanEditor.Row row=editor.row(index[0]);
            row.prompt=prompt.getText().toString();
            row.required=required.getText().toString();
            row.gate=gates[Math.max(0,picker.getSelectedItemPosition())];
        };
        button(l,R.string.auto_sample_check,()->{
            if(!valid(token)||busy())return;
            capture.run();
            sampleResult.setText(sampleLabel(WorkflowPlanEditor.sample(editor.row(index[0]),sample.getText().toString(),truncated.isChecked())));
        });
        android.text.TextWatcher invalidateSample=new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence text,int start,int count,int after){}
            public void onTextChanged(CharSequence text,int start,int before,int count){sampleResult.setText("");}
            public void afterTextChanged(android.text.Editable value){}
        };
        prompt.addTextChangedListener(invalidateSample);
        required.addTextChangedListener(invalidateSample);
        sample.addTextChangedListener(invalidateSample);
        truncated.setOnCheckedChangeListener((v,checked)->sampleResult.setText(""));
        Runnable render=()->{
            WorkflowPlanEditor.Row row=editor.row(index[0]);
            stepLabel.setText(owner.getString(R.string.auto_editor_index,Integer.toString(index[0]+1),Integer.toString(editor.size())));
            prompt.setText(row.prompt);required.setText(row.required);picker.setSelection(row.gate.ordinal());
            required.setHint(gateHint(row.gate));gateHelp.setVisibility(row.gate==WorkflowPolicy.Gate.JSON_VALUES?View.VISIBLE:View.GONE);error.setText("");
            sample.setText("");truncated.setChecked(false);sampleResult.setText("");
            if(controls[0]!=null)controls[0].setEnabled(index[0]>0);
            if(controls[1]!=null)controls[1].setEnabled(index[0]+1<editor.size());
            if(controls[2]!=null)controls[2].setEnabled(editor.size()<WorkflowPolicy.MAX_STEPS);
            if(controls[3]!=null)controls[3].setEnabled(editor.size()>1);
            if(controls[4]!=null)controls[4].setEnabled(index[0]>0);
            if(controls[5]!=null)controls[5].setEnabled(index[0]+1<editor.size());
        };
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                sampleResult.setText("");
                if(position>=0&&position<gates.length){required.setHint(gateHint(gates[position]));gateHelp.setVisibility(gates[position]==WorkflowPolicy.Gate.JSON_VALUES?View.VISIBLE:View.GONE);}
            }
            public void onNothingSelected(AdapterView<?> parent){sampleResult.setText("");}
        });
        controls[0]=button(l,R.string.auto_editor_previous,()->{capture.run();if(index[0]>0)index[0]--;render.run();});
        controls[1]=button(l,R.string.auto_editor_next,()->{capture.run();if(index[0]+1<editor.size())index[0]++;render.run();});
        controls[2]=button(l,R.string.auto_editor_add,()->{capture.run();if(editor.size()<WorkflowPolicy.MAX_STEPS){editor.insertAfter(index[0]);index[0]++;render.run();}});
        controls[3]=button(l,R.string.auto_editor_remove,()->{
            capture.run();if(editor.size()>1){editor.remove(index[0]);index[0]=Math.min(index[0],editor.size()-1);render.run();}
        });
        controls[4]=button(l,R.string.auto_move_up,()->{capture.run();index[0]=editor.move(index[0],-1);render.run();});
        controls[5]=button(l,R.string.auto_move_down,()->{capture.run();index[0]=editor.move(index[0],1);render.run();});
        render.run();
        ScrollView sc=new ScrollView(owner);sc.addView(l);
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(R.string.auto_visual_editor).setView(sc)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.auto_apply_draft,null).create();
        trackDialog(d);
        d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!valid(token)||busy()||!original.equals(plan.getText().toString())){d.dismiss();return;}
            capture.run();int invalid=editor.firstInvalid();
            if(invalid>=0){index[0]=invalid;render.run();error.setText(owner.getString(R.string.auto_editor_invalid,Integer.toString(invalid+1)));return;}
            try{plan.setText(editor.encode());d.dismiss();}
            catch(IllegalArgumentException e){error.setText(R.string.auto_invalid_plan);}
        });
    }
    private void showGoalPlanner(String model,EditText draft,Runnable updateParent){
        if(busy()||model==null||model.isEmpty()||!OllamaForegroundService.isRunning()){
            toast(R.string.test_need_model);return;
        }
        if(!"off".equals(CoopSession.get(owner).role())){toast(R.string.guest_stop_first);return;}
        final int token=epoch;
        final String original=draft.getText().toString();
        LinearLayout l=column();
        label(l,s(R.string.auto_goal_planner_notice));
        label(l,s(R.string.model_label)+": "+model);
        EditText goal=field(l,R.string.auto_goal_planner_goal,"",WorkflowGoalPlanner.MAX_GOAL,false);
        goal.setSingleLine(true);
        TextView preview=output(l);preview.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        final String[] candidate={null},reviewedGoal={null};
        final LocalWorkflowPlanner[] active={null};
        final boolean[] cancelling={false};
        final Button[] generate={null};
        ScrollView sc=new ScrollView(owner);sc.addView(l);
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(R.string.auto_goal_planner).setView(sc)
            .setNegativeButton(R.string.close,null).setNeutralButton(R.string.stop,null).setPositiveButton(R.string.auto_apply_draft,null).create();
        generate[0]=button(l,R.string.auto_goal_generate,()->{
            if(!valid(token)||!d.isShowing()||busy()||active[0]!=null)return;
            final String requested;
            try{requested=WorkflowGoalPlanner.goal(goal.getText().toString());}
            catch(IllegalArgumentException e){toast(R.string.auto_goal_planner_goal);return;}
            if(!original.equals(draft.getText().toString())){d.dismiss();return;}
            final String locale=OnHandsLocale.normalize(owner.getResources().getConfiguration().getLocales().get(0));
            LocalWorkflowPlanner request=new LocalWorkflowPlanner(owner);
            active[0]=request;planner=request;cancelling[0]=false;
            candidate[0]=null;reviewedGoal[0]=null;
            preview.setText(R.string.thinking);
            work(()->request.generate(requested,model,locale),value->{
                if(planner==request)planner=null;
                if(active[0]!=request){updateParent.run();return;}
                active[0]=null;updateParent.run();
                if(!d.isShowing())return;
                goal.setEnabled(true);
                if(!cancelling[0]&&requested.equals(goal.getText().toString().trim())&&original.equals(draft.getText().toString())){
                    candidate[0]=value;reviewedGoal[0]=requested;preview.setText(value);
                }else preview.setText(R.string.auto_goal_cancelled);
                d.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(candidate[0]!=null);
                d.getButton(DialogInterface.BUTTON_NEUTRAL).setEnabled(false);
                d.getButton(DialogInterface.BUTTON_NEGATIVE).setEnabled(true);
                generate[0].setEnabled(true);
            },()->{
                if(planner==request)planner=null;
                if(active[0]!=request){updateParent.run();return;}
                active[0]=null;updateParent.run();
                if(!d.isShowing())return;
                preview.setText(cancelling[0]?R.string.auto_goal_cancelled:R.string.auto_goal_failed);
                goal.setEnabled(true);d.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                d.getButton(DialogInterface.BUTTON_NEUTRAL).setEnabled(false);
                d.getButton(DialogInterface.BUTTON_NEGATIVE).setEnabled(true);
                generate[0].setEnabled(true);
            });
            updateParent.run();goal.setEnabled(false);
            d.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
            d.getButton(DialogInterface.BUTTON_NEUTRAL).setEnabled(true);
            generate[0].setEnabled(false);
        });
        confirmation=d;d.setOnDismissListener(x->{
            if(active[0]!=null){cancelling[0]=true;active[0].cancel();}
            if(confirmation==d)confirmation=null;
            updateParent.run();
        });
        d.show();secure(d);
        d.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
        d.getButton(DialogInterface.BUTTON_NEUTRAL).setEnabled(false);
        d.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v->{
            if(active[0]==null)return;
            cancelling[0]=true;candidate[0]=null;reviewedGoal[0]=null;
            active[0].cancel();preview.setText(R.string.auto_pause_requested);
            v.setEnabled(false);
        });
        d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!valid(token)||busy()||active[0]!=null||candidate[0]==null||!original.equals(draft.getText().toString())){
                toast(R.string.auto_preview_first);return;
            }
            try{
                if(!WorkflowGoalPlanner.goal(goal.getText().toString()).equals(reviewedGoal[0]))throw new IllegalArgumentException("Goal changed");
                WorkflowPolicy.parse(candidate[0]);
                draft.setText(candidate[0]);d.dismiss();
            }catch(IllegalArgumentException e){toast(R.string.auto_preview_first);}
        });
    }
    private void showBusinessTemplates(EditText plan){
        final int token=epoch;final String original=plan.getText().toString();
        LinearLayout l=column();label(l,s(R.string.auto_business_notice));
        Spinner type=new Spinner(owner);
        String[] options={s(R.string.auto_preset_report),s(R.string.auto_preset_actions),s(R.string.auto_preset_code)};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(owner,android.R.layout.simple_spinner_item,options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);type.setAdapter(adapter);
        type.setMinimumHeight(dp(48));l.addView(type,new LinearLayout.LayoutParams(-1,-2));
        EditText goal=field(l,R.string.auto_preset_goal,"",1000,true);
        TextView preview=output(l);
        final String[] candidate={null};
        final int[][] prompts={
            {R.string.auto_report_step1,R.string.auto_report_step2,R.string.auto_report_step3},
            {R.string.auto_actions_step1,R.string.auto_actions_step2,R.string.auto_actions_step3},
            {R.string.auto_code_step1,R.string.auto_code_step2,R.string.auto_code_step3}
        };
        final String[][] fields={
            {"outline:array,missing:array","draft:string,uncertainties:array","report:string,checklist:array"},
            {"tasks:array,dependencies:array","plan:string,risks:array","actions:array,open_questions:array"},
            {"requirements:array,tests:array","code:string,limitations:array","code:string,test_plan:array"}
        };
        Runnable generate=()->{
            candidate[0]=null;
            try{
                int i=type.getSelectedItemPosition();
                String[] values=new String[]{s(prompts[i][0]),s(prompts[i][1]),s(prompts[i][2])};
                candidate[0]=WorkflowPlanEditor.preset(goal.getText().toString(),values,fields[i]);
                preview.setText(candidate[0]);
            }catch(Exception e){preview.setText(R.string.auto_invalid_plan);}
        };
        button(l,R.string.auto_preview_plan,generate);
        ScrollView sc=new ScrollView(owner);sc.addView(l);
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(R.string.auto_business_templates).setView(sc)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.auto_apply_draft,null).create();
        trackDialog(d);
        d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!valid(token)||busy()||!original.equals(plan.getText().toString())){d.dismiss();return;}
            String reviewed=candidate[0];generate.run();
            // A change in goal or preset requires a fresh preview before replacing the editor.
            if(candidate[0]==null||!candidate[0].equals(reviewed)){toast(R.string.auto_preview_first);return;}
            plan.setText(candidate[0]);d.dismiss();
        });
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
        WorkflowPolicy.Budget budget=c.budget(System.currentTimeMillis());
        String allowance=owner.getString(R.string.auto_budget_remaining,Integer.toString(budget.stepAttempts),
            Integer.toString(budget.totalAttempts),Long.toString(budget.millis/1000));
        if(budget.state==WorkflowPolicy.BudgetState.CLOCK)allowance+="\n"+s(R.string.auto_budget_clock);
        String next=c.complete()?"":"\n"+owner.getString(R.string.auto_current_step,Integer.toString(c.results.size()+1),Integer.toString(c.steps().size()),gateLabel(c.steps().get(c.results.size()).gate));
        return stateLabel(c.status)+" · "+c.results.size()+"/"+c.steps().size()+" · "+c.attempts+"/"+WorkflowPolicy.MAX_ATTEMPTS+
            (reason.isEmpty()?"":"\n"+reason)+next+
            "\n"+owner.getString(R.string.auto_run_stats,seconds,Integer.toString(c.totalAttempts),Long.toString(c.reportedTokens),Integer.toString(c.unknownAttempts))+
            "\n"+allowance+(c.legacyStats?"\n"+s(R.string.auto_legacy_stats):"");
    }
    private void display(TextView status,LocalWorkflow.Checkpoint c){
        StringBuilder text=new StringBuilder(summary(c));
        for(int i=0;i<c.results.size();i++)text.append("\n\n").append(i+1).append(". PASS (").append(gateLabel(c.steps().get(i).gate)).append(")\n").append(c.results.get(i));
        status.setText(text.toString());
    }
}
