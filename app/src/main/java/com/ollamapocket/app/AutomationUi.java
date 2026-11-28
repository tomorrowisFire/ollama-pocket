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
import java.util.function.Consumer;

/** Foreground-only tools and workflows. A single active worker survives Activity recreation only until cancelled. */
final class AutomationUi {
    private static final ProjectWorkGate BUSY=new ProjectWorkGate();
    private final Activity owner;
    private final WorkspaceDictation dictation;
    private final WorkspaceReadAloud reading;
    private final WorkspaceTextViewer viewer;
    private AlertDialog dialog,confirmation;
    private McpLanClient mcp;
    private LocalWorkflow workflow;
    private LocalWorkflowPlanner planner;
    private LocalConferenceRunner conference;
    private volatile VisionModelStore visionStore;
    private volatile VisionLocalServer visionServer;
    private ProjectWorkspaceUi reportWorkspace;
    private volatile Thread worker;
    private volatile boolean stopped;
    private volatile int epoch;
    AutomationUi(Activity a){owner=a;dictation=new WorkspaceDictation(a);reading=new WorkspaceReadAloud(a);viewer=new WorkspaceTextViewer(a);}
    static boolean busy(){return BUSY.busy()||VisionLocalServer.busy();}
    private String s(int id){return owner.getString(id);}
    private int dp(int n){return Math.round(n*owner.getResources().getDisplayMetrics().density);}
    private void toast(int id){Toast.makeText(owner,id,Toast.LENGTH_SHORT).show();}
    private LinearLayout column(){LinearLayout l=new LinearLayout(owner);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(16),dp(12),dp(16),dp(12));return l;}
    private void label(LinearLayout l,String value){TextView t=new TextView(owner);t.setText(value);WorkbenchStyle.label(owner,t,14,false,false);l.addView(t,WorkbenchStyle.row(owner));}
    private TextView output(LinearLayout l){TextView t=new TextView(owner);t.setTextIsSelectable(true);t.setSaveEnabled(false);t.setAutoLinkMask(0);WorkbenchStyle.label(owner,t,14,false,false);l.addView(t,WorkbenchStyle.row(owner));return t;}
    private EditText field(LinearLayout l,int hint,String value,int max,boolean multiline){
        EditText e=new EditText(owner);e.setHint(hint);e.setText(value);e.setSaveEnabled(false);e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        e.setInputType(InputType.TYPE_CLASS_TEXT|(multiline?InputType.TYPE_TEXT_FLAG_MULTI_LINE:InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});
        if(multiline){e.setMinLines(3);e.setMaxLines(7);}
        WorkbenchStyle.field(owner,e);l.addView(e,WorkbenchStyle.row(owner));return e;
    }
    private Button button(LinearLayout l,int id,Runnable run){Button b=new Button(owner);b.setText(id);WorkbenchStyle.button(owner,b,id);b.setOnClickListener(v->run.run());l.addView(b,WorkbenchStyle.row(owner));return b;}
    private boolean valid(int token){return token==epoch&&!stopped&&!owner.isFinishing()&&!owner.isDestroyed();}
    private void secure(AlertDialog d){if(d.getWindow()!=null)d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    private void confirm(String title,String body,Runnable action){confirm(title,body,action,()->{});}
    private void confirm(String title,String body,Runnable action,Runnable abandoned){
        if(confirmation!=null)confirmation.dismiss();
        final int token=epoch;final boolean[] accepted={false};
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(title).setMessage(body)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok,(x,w)->{
                if(valid(token)){accepted[0]=true;action.run();}
            }).create();
        confirmation=d;d.setOnDismissListener(x->{if(confirmation==d)confirmation=null;if(!accepted[0])abandoned.run();});d.show();secure(d);
    }
    private void show(String title,LinearLayout l){
        ScrollView scroll=new ScrollView(owner);scroll.addView(l);
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(title).setView(scroll)
            .setNegativeButton(R.string.close,null).create();
        dialog=d;d.setOnDismissListener(x->{if(dialog==d){dialog=null;pause();}});d.show();secure(d);WorkbenchWindow.fit(owner,d);
    }
    void pause(){
        stopped=true;epoch++;
        Thread current=worker;if(current!=null)current.interrupt();
        viewer.cancel();reading.cancel();dictation.cancel();
        VisionModelStore visualStore=visionStore;visionStore=null;
        if(visualStore!=null)visualStore.cancelInstall();
        VisionLocalServer visualServer=visionServer;
        if(visualServer!=null){
            Thread cancel=new Thread(visualServer::cancel,"onhand-vision-cancel");cancel.setDaemon(true);cancel.start();
        }
        if(reportWorkspace!=null){reportWorkspace.pause();reportWorkspace=null;}
        if(mcp!=null){mcp.cancel();mcp=null;}
        if(workflow!=null)workflow.pause();
        if(planner!=null){planner.cancel();planner=null;}
        if(conference!=null){conference.cancel();conference=null;}
        AlertDialog c=confirmation;confirmation=null;if(c!=null)c.dismiss();
        AlertDialog d=dialog;dialog=null;if(d!=null)d.dismiss();
    }
    private <T> void work(Callable<T> task,Consumer<T> success,Runnable failure){
        final ProjectWorkGate.Lease lease=BUSY.acquire();
        if(lease==null){toast(R.string.busy);return;}
        final int token=epoch;
        Thread launched=null;
        try{
            viewer.cancel();reading.cancel();dictation.cancel();
            android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
            launched=new Thread(()->{
                try{
                    if(!lease.execute(()->{
                        try{return task.call();}
                        catch(OutOfMemoryError memory){throw new java.io.IOException("Automation allocation failed",memory);}
                        finally{if(worker==Thread.currentThread())worker=null;}
                    },main::post,(value,error)->{
                        if(valid(token)){if(error==null)success.accept(value);else failure.run();}
                    }))android.util.Log.w("OnHandAI","Automation completion not delivered");
                }finally{if(worker==Thread.currentThread())worker=null;}
            },"onhand-automation");
            worker=launched;launched.start();
        }catch(RuntimeException|OutOfMemoryError e){
            if(worker==launched)worker=null;
            if(lease.release()&&valid(token))failure.run();
        }catch(Error fatal){
            if(worker==launched)worker=null;
            lease.release();throw fatal;
        }
    }
    void open(String model){
        if(busy()){toast(R.string.busy);return;}
        stopped=false;epoch++;
        final int token=epoch;
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(R.string.auto_title)
            .setItems(new String[]{s(R.string.auto_mcp),s(R.string.auto_workflow),s(R.string.conference_title),
                s(R.string.integration_chart),s(R.string.integration_agent),s(R.string.integration_branch),
                s(R.string.integration_vision)},(x,w)->{
                if(!valid(token))return;
                if(w==0)showMcp();else if(w==1)showWorkflow(model);else if(w==2)showConference(model);
                else if(w==3)showChart();else if(w==4)showAgentProfile(model);else if(w==5)showBranch(model);
                else if(w==6)showVision(null);
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
    private String conferenceStopLabel(String reason){
        switch(reason){
            case "USER":return s(R.string.run_stop_user);
            case "DEADLINE":return s(R.string.batch_deadline);
            case "THERMAL":return s(R.string.bench_hot);
            case "MEMORY":return s(R.string.bench_memory);
            case "SERVER":return s(R.string.test_need_server);
            case "COOP":return s(R.string.guest_stop_first);
            case "INTERRUPTED":return s(R.string.run_stop_interrupted);
            default:return s(R.string.run_stop_unknown);
        }
    }
    private String conferenceTerminalReport(ConferenceReport report){
        return conferenceReport(report.request,report.result)+"\n\n"+
            owner.getString(R.string.conference_elapsed,Long.toString(report.elapsedMillis))+
            (report.stopReason==null?"":"\n"+s(R.string.conference_stop_reason)+": "+conferenceStopLabel(report.stopReason));
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
        Button voiceGoal=dictation.add(panel,goal,R.string.conference_goal,ConferencePolicy.MAX_GOAL,false,
            ()->valid(token)&&!busy()&&confirmation==null&&dialog!=null&&dialog.isShowing());
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
        final Button[] controls=new Button[9];
        final ConferencePolicy.Result[] latest={null};
        final ConferenceReport[] terminal={null};
        final boolean[] stopRequested={false};
        Runnable enabled=()->{
            boolean idle=!busy();
            goal.setEnabled(idle);voiceGoal.setEnabled(idle);picker.setEnabled(idle);required.setEnabled(idle);
            controls[0].setEnabled(idle);controls[3].setEnabled(idle);
            controls[1].setEnabled(!idle&&conference!=null&&!stopRequested[0]);
            controls[2].setEnabled(idle&&terminal[0]!=null);
            controls[4].setEnabled(idle&&terminal[0]!=null&&terminal[0].hasArtifact());
            controls[5].setEnabled(idle&&terminal[0]!=null);
            controls[6].setEnabled(idle&&terminal[0]!=null);
            controls[7].setEnabled(idle&&terminal[0]!=null);
            controls[8].setEnabled(idle&&terminal[0]!=null);
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
        controls[8]=viewer.add(panel,R.string.conference_title,()->status.getText().toString(),
            ()->valid(token)&&!busy()&&terminal[0]!=null&&confirmation==null&&dialog!=null&&dialog.isShowing());
        panel.removeView(controls[8]);panel.addView(controls[8],panel.indexOfChild(status),WorkbenchStyle.row(owner));
        controls[7]=reading.add(panel,R.string.conference_title,()->status.getText().toString(),
            ()->valid(token)&&!busy()&&terminal[0]!=null&&confirmation==null&&dialog!=null&&dialog.isShowing());
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
                    status.setText(s(R.string.auto_mcp_tools)+" "+eligible+"/"+list.size()+"\n"+s(R.string.auto_schema_notice)+"\n"+owner.getString(R.string.integration_tool_budget,client.remaining()));
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
            final ToolCallApproval.Review review;
            try{review=client.prepareReview(tool,value);}catch(RuntimeException e){
                status.setText(owner.getString(R.string.integration_tool_budget,client.remaining()));toast(R.string.auto_mcp_failed);return;
            }
            d.dismiss();
            confirm(tool.name,client.endpoint()+"\n\n"+value+"\n\n"+s(R.string.auto_mcp_call_notice)+"\n"+
                    owner.getString(R.string.integration_tool_budget,client.remaining()),()->{
                if(mcp!=client||busy()||!client.approve(review,tool,value)){
                    client.decline(review);status.setText(R.string.auto_mcp_failed);return;
                }
                status.setText(R.string.thinking);
                work(()->client.invoke(review,tool,value),result->status.setText(
                    (result.optBoolean("isError",false)?s(R.string.auto_mcp_failed)+"\n":"")+result.toString()+"\n"+
                    owner.getString(R.string.integration_tool_budget,client.remaining())),
                    ()->status.setText(s(R.string.auto_mcp_failed)+"\n"+owner.getString(R.string.integration_tool_budget,client.remaining())));
            },()->client.decline(review));
        });
    }
    void openVision(android.net.Uri uri){
        if(busy()){toast(R.string.busy);return;}
        stopped=false;epoch++;showVision(uri);
    }
    private boolean visualSafe(int token){
        if(!valid(token)||InferenceSafety.blocked(owner)||!"off".equals(CoopSession.get(owner).role()))return false;
        try{
            android.app.ActivityManager manager=(android.app.ActivityManager)owner.getSystemService(android.content.Context.ACTIVITY_SERVICE);
            if(manager==null)return false;
            android.app.ActivityManager.MemoryInfo info=new android.app.ActivityManager.MemoryInfo();manager.getMemoryInfo(info);
            return !info.lowMemory&&info.availMem>=768L*1024*1024;
        }catch(RuntimeException unavailable){return false;}
    }
    private static final class VisualInput {
        final VisionImagePolicy.Prepared image;final android.graphics.Bitmap preview;
        VisualInput(VisionImagePolicy.Prepared image,android.graphics.Bitmap preview){this.image=image;this.preview=preview;}
    }
    private VisualInput prepareVisual(android.net.Uri uri,int token)throws Exception{
        if(uri==null)return null;
        if(!"content".equals(uri.getScheme()))throw new java.io.IOException("Invalid visual URI");
        try(java.io.InputStream input=owner.getContentResolver().openInputStream(uri)){
            if(input==null)throw new java.io.IOException("Missing visual source");
            VisionImagePolicy.Prepared image=VisionImageInput.prepare(
                    new AttachmentText.BoundedSource(input,DeepChatPolicy.NORMAL_STRUCTURED_BYTES),()->!valid(token));
            byte[] bytes=image.copyBytes();
            try{
                android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                if(bitmap==null||bitmap.getWidth()!=image.width||bitmap.getHeight()!=image.height||!valid(token)){
                    if(bitmap!=null)bitmap.recycle();
                    throw new java.io.IOException("Visual preview unavailable or cancelled");
                }
                return new VisualInput(image,bitmap);
            }catch(OutOfMemoryError e){throw new java.io.IOException("Visual preview allocation",e);}
            finally{java.util.Arrays.fill(bytes,(byte)0);}
        }
    }
    private void showVision(android.net.Uri uri){
        LinearLayout l=column();label(l,s(R.string.integration_vision_notice));
        label(l,"SmolVLM 256M Q8_0 · "+(VisionModelProfile.TOTAL_BYTES/1048576)+" MiB · "+VisionModelProfile.LICENSE);
        TextView status=output(l);
        java.io.File binary=new java.io.File(owner.getApplicationInfo().nativeLibraryDir,"libllama-distributed-server.so");
        if(android.os.Build.VERSION.SDK_INT<28||!binary.isFile()){
            status.setText(R.string.integration_vision_unsupported);show(s(R.string.integration_vision),l);return;
        }
        final VisionModelStore[] models={null};final VisualInput[] prepared={null};
        final int token=epoch;
        android.widget.ImageView preview=new android.widget.ImageView(owner);
        preview.setAdjustViewBounds(true);preview.setMaxHeight(dp(280));preview.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        preview.setContentDescription(s(R.string.integration_vision_preview));preview.setSaveEnabled(false);
        l.addView(preview,new LinearLayout.LayoutParams(-1,-2));
        EditText prompt=field(l,R.string.integration_agent_prompt,"",VisionChatRequest.MAX_PROMPT_CHARS,true);
        Runnable refresh=()->{
            if(models[0]!=null)status.setText(models[0].ready()?R.string.integration_vision_installed:R.string.integration_vision_missing);
        };
        button(l,R.string.integration_vision_install,()->{
            if(busy()||models[0]==null||models[0].present())return;
            VisionModelStore store=models[0];
            confirm(s(R.string.integration_vision_install),s(R.string.integration_vision_download)+"\n\n"+
                    VisionModelProfile.TOTAL_BYTES+" bytes\n"+VisionModelProfile.LICENSE+"\n"+VisionModelProfile.SOURCE,()->{
                if(busy()||models[0]!=store)return;
                status.setText(R.string.thinking);
                work(()->{store.install(true,()->!valid(token),null);return true;},ignored->refresh.run(),
                        ()->status.setText(R.string.integration_execution_failed));
            });
        });
        button(l,R.string.integration_vision_remove,()->{
            if(busy()||models[0]==null||!models[0].present())return;
            VisionModelStore store=models[0];
            confirm(s(R.string.integration_vision_remove),"SmolVLM 256M Q8_0",()->{
                if(busy()||models[0]!=store)return;
                work(()->{store.remove();return true;},ignored->refresh.run(),()->status.setText(R.string.integration_execution_failed));
            });
        });
        button(l,R.string.integration_vision_pick,()->{
            if(!busy()&&owner instanceof MainActivity)((MainActivity)owner).pickVisionForAutomation();
        });
        button(l,R.string.integration_vision_analyze,()->{
            if(busy()||models[0]==null||prepared[0]==null||!models[0].ready()){
                toast(R.string.integration_vision_missing);return;
            }
            final VisualInput selected=prepared[0];final VisionModelStore store=models[0];
            final String question=prompt.getText().toString();
            try{ReviewedLocalPolicy.text(question,VisionChatRequest.MAX_PROMPT_CHARS);}catch(RuntimeException bad){toast(R.string.integration_execution_failed);return;}
            if(!visualSafe(token)){toast(R.string.integration_execution_failed);return;}
            prompt.setEnabled(false);status.setText(R.string.integration_vision_checking);
            work(()->{
                java.util.function.BooleanSupplier stop=()->!visualSafe(token);
                VisionModelStore.Lease files=store.acquire(stop);
                VisionLocalServer server=null;
                try{
                    if(stop.getAsBoolean())throw new java.io.InterruptedIOException();
                    GpuRunner.stop();DistributedRunner.stop();
                    if(OllamaForegroundService.isRunning())OllamaHttp.releaseResidentModels(stop);
                    if(stop.getAsBoolean())throw new java.io.InterruptedIOException();
                    java.util.Map<String,String> environment=new java.util.HashMap<>();
                    environment.put("LD_LIBRARY_PATH",owner.getApplicationInfo().nativeLibraryDir);
                    environment.put("HOME",owner.getFilesDir().getAbsolutePath());
                    environment.put("TMPDIR",owner.getCacheDir().getAbsolutePath());
                    server=VisionLocalServer.start(binary,files.weights(),files.projector(),owner.getFilesDir(),environment,stop);
                    visionServer=server;
                    try(VisionTurnReview gate=new VisionTurnReview()){
                        VisionTurnReview.Backend backend=server.backend();
                        VisionTurnReview.Review review=gate.prepare(backend,selected.image,question,VisionChatRequest.MAX_TOKENS,stop);
                        java.util.concurrent.CountDownLatch decision=new java.util.concurrent.CountDownLatch(1);
                        java.util.concurrent.atomic.AtomicBoolean accepted=new java.util.concurrent.atomic.AtomicBoolean();
                        owner.runOnUiThread(()->{
                            if(!valid(token)){decision.countDown();return;}
                            status.setText(R.string.integration_vision_ready);
                            confirm(s(R.string.integration_vision_analyze),s(R.string.integration_vision_send)+"\n\n"+
                                review.width+" × "+review.height+"\nSHA-256: "+review.imageSha256+"\n\n"+question,()->{
                                    accepted.set(valid(token)&&gate.approve(review));decision.countDown();
                                },()->{gate.decline(review);decision.countDown();});
                        });
                        long until=System.nanoTime()+VisionTurnReview.TTL_NANOS;
                        while(decision.getCount()>0&&System.nanoTime()-until<0&&!stop.getAsBoolean())
                            decision.await(200,java.util.concurrent.TimeUnit.MILLISECONDS);
                        if(!accepted.get()||stop.getAsBoolean())return null;
                        try(VisionTurnReview.Lease request=gate.begin(review,backend,selected.image,question,VisionChatRequest.MAX_TOKENS,stop)){
                            if(request==null)throw new java.io.IOException("Visual review expired");
                            return server.infer(request,VisionChatRequest.MAX_TOKENS,stop);
                        }
                    }
                }finally{
                    try{if(server!=null)server.close();}
                    finally{
                        visionServer=null;
                        // Never release verified model files while the native process may still own them.
                        if(!VisionLocalServer.busy())files.close();
                    }
                }
            },answer->{
                if(confirmation!=null)confirmation.dismiss();prompt.setEnabled(true);
                if(answer==null)refresh.run();
                else status.setText((answer.truncated?s(R.string.integration_partial)+"\n":"")+answer.text);
            },()->{if(confirmation!=null)confirmation.dismiss();prompt.setEnabled(true);status.setText(R.string.integration_execution_failed);});
        });
        show(s(R.string.integration_vision),l);
        status.setText(R.string.thinking);
        work(()->{
            VisionModelStore store=new VisionModelStore(owner.getNoBackupFilesDir().getCanonicalFile());
            visionStore=store;
            return new java.util.AbstractMap.SimpleImmutableEntry<>(store,prepareVisual(uri,token));
        },pair->{
            models[0]=pair.getKey();prepared[0]=pair.getValue();
            if(prepared[0]!=null)preview.setImageBitmap(prepared[0].preview);
            refresh.run();
        },()->status.setText(R.string.integration_execution_failed));
    }
    private void showChart(){
        LinearLayout l=column();label(l,s(R.string.integration_chart_notice));
        EditText raw=field(l,R.string.integration_chart_json,
            "{\"kind\":\"bar\",\"title\":\"Example\",\"points\":[{\"label\":\"A\",\"value\":3},{\"label\":\"B\",\"value\":-2}]}",
            ToolJsonPolicy.MAX_CHARS,true);
        TextView status=output(l);
        LinearLayout preview=column();preview.setPadding(0,0,0,0);
        button(l,R.string.integration_preview,()->{
            if(busy())return;
            try{
                ChartArtifact chart=ChartArtifactParser.parse(raw.getText().toString());
                preview.removeAllViews();preview.addView(ChartArtifactView.create(owner,chart,status.getCurrentTextColor(),1f),
                        new LinearLayout.LayoutParams(-1,-2));
                status.setText("");
            }catch(Exception invalid){preview.removeAllViews();status.setText(R.string.integration_chart_invalid);}
        });
        l.addView(preview,WorkbenchStyle.row(owner));show(s(R.string.integration_chart),l);
    }
    private EditText formField(LinearLayout parent,int caption,String value,int limit,boolean multiline){
        LinearLayout group=new LinearLayout(owner);group.setOrientation(LinearLayout.VERTICAL);
        EditText input=field(group,caption,value,limit,multiline);
        TextView title=WorkbenchStyle.fieldLabel(owner,group,caption,input);
        group.removeView(title);group.addView(title,0);
        input.setHint("");
        parent.addView(group,WorkbenchStyle.row(owner));return input;
    }
    private void formCaption(EditText input,int caption){
        LinearLayout group=(LinearLayout)input.getParent();
        ((TextView)group.getChildAt(0)).setText(caption);
    }
    private EditText draftInput(LinearLayout l,int hint,String model,boolean branch){
        LinearLayout advanced=new LinearLayout(owner);advanced.setOrientation(LinearLayout.VERTICAL);
        EditText raw=formField(advanced,hint,"",ToolJsonPolicy.MAX_CHARS+1,true);
        Button edit=button(l,R.string.integration_form_editor,()->{
            if(branch)showBranchForm(raw,model);else showAgentForm(raw,model);
        });
        WorkbenchStyle.button(owner,edit,WorkbenchVisualPolicy.Role.PRIMARY);
        TextView summary=output(l);
        summary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        CheckBox toggle=draftCheck(l,R.string.integration_form_advanced,false);
        l.addView(advanced,WorkbenchStyle.row(owner));advanced.setVisibility(View.GONE);
        toggle.setOnCheckedChangeListener((view,checked)->advanced.setVisibility(checked?View.VISIBLE:View.GONE));
        Runnable render=()->{
            String current=raw.getText().toString();
            if(current.trim().isEmpty()){summary.setText(R.string.integration_form_empty);return;}
            try{
                if(branch){
                    BranchWorkflowRun.Definition value=BranchWorkflowRun.parse(current);
                    summary.setText(owner.getString(R.string.integration_form_branch_summary,value.model,value.graph.nodes().size())+
                            "\n"+branchBudget(value)+"\n"+s(value.retainOutputs?R.string.integration_retained_notice:R.string.integration_metadata_only));
                }else{
                    AgentProfile value=AgentProfile.decode(current);
                    summary.setText(owner.getString(R.string.integration_form_agent_summary,value.model,value.temperatureMilli,value.maxTokens));
                }
            }catch(Exception invalid){summary.setText(R.string.integration_form_bad_summary);}
        };
        raw.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence value,int start,int count,int after){}
            public void onTextChanged(CharSequence value,int start,int before,int count){}
            public void afterTextChanged(android.text.Editable value){render.run();}
        });
        render.run();return raw;
    }
    private CheckBox draftCheck(LinearLayout l,int label,boolean checked){
        CheckBox box=new CheckBox(owner);box.setText(label);box.setChecked(checked);
        box.setSaveEnabled(false);box.setMinimumHeight(dp(48));
        l.addView(box,WorkbenchStyle.row(owner));return box;
    }
    private AlertDialog draftDialog(LinearLayout l){
        if(confirmation!=null)confirmation.dismiss();
        ScrollView scroll=new ScrollView(owner);scroll.addView(l);
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(R.string.integration_form_editor).setView(scroll)
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.auto_apply_draft,null).create();
        trackDialog(d);WorkbenchWindow.fit(owner,d);return d;
    }
    private Button formModelButton(LinearLayout l){
        return button(l,R.string.integration_form_bind_model,()->{});
    }
    private void bindFormModel(Button bind,AlertDialog editor,EditText model,EditText digest,TextView status,int token){
        bind.setOnClickListener(v->{
            if(!valid(token)||busy()||confirmation!=editor||!editor.isShowing())return;
            final String tag=model.getText().toString(),before=digest.getText().toString();
            if(!ReviewedLocalPolicy.modelTag(tag)){status.setText(R.string.integration_agent_invalid);return;}
            Button apply=editor.getButton(DialogInterface.BUTTON_POSITIVE);
            bind.setEnabled(false);apply.setEnabled(false);model.setEnabled(false);digest.setEnabled(false);
            status.setText(R.string.thinking);
            Runnable enable=()->{bind.setEnabled(true);apply.setEnabled(true);model.setEnabled(true);digest.setEnabled(true);};
            work(()->new ReviewedCpuTask(owner,()->!valid(token)).installed(tag),result->{
                if(confirmation!=editor||!editor.isShowing())return;
                enable.run();
                if(!tag.equals(model.getText().toString())||!before.equals(digest.getText().toString())){
                    status.setText(R.string.integration_form_invalid);return;
                }
                digest.setText(result);status.setText(R.string.integration_form_bound);
            },()->{
                if(confirmation!=editor||!editor.isShowing())return;
                enable.run();status.setText(R.string.integration_execution_failed);
            });
        });
    }
    private void showAgentForm(EditText raw,String selectedModel){
        if(busy()||!raw.isEnabled())return;
        final String original=raw.getText().toString();final int token=epoch;
        final AgentProfile current;
        try{current=original.trim().isEmpty()?null:AgentProfile.decode(original);}
        catch(Exception invalid){toast(R.string.integration_agent_invalid);return;}
        LinearLayout l=column();label(l,s(R.string.integration_form_notice));
        EditText name=formField(l,R.string.integration_form_name,current==null?"":current.name,81,false);
        EditText model=formField(l,R.string.model_label,current==null?selectedModel:current.model,201,false);
        EditText digest=formField(l,R.string.integration_form_digest,current==null?"":current.modelSha256,65,false);
        Button bind=formModelButton(l);
        EditText instruction=formField(l,R.string.integration_form_instruction,current==null?"":current.instruction,4097,true);
        EditText temperature=formField(l,R.string.integration_form_temperature,current==null?"700":Integer.toString(current.temperatureMilli),5,false);
        temperature.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText tokens=formField(l,R.string.integration_form_tokens,current==null?"256":Integer.toString(current.maxTokens),4,false);
        tokens.setInputType(InputType.TYPE_CLASS_NUMBER);
        TextView error=output(l);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        AlertDialog d=draftDialog(l);
        bindFormModel(bind,d,model,digest,error,token);
        d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!valid(token)||busy()||!raw.isEnabled()||!original.equals(raw.getText().toString())){d.dismiss();return;}
            try{
                String value=AutomationDraftForms.agent(name.getText().toString(),model.getText().toString(),
                        digest.getText().toString(),instruction.getText().toString(),temperature.getText().toString(),tokens.getText().toString());
                raw.setText(value);d.dismiss();
            }catch(Exception invalid){error.setText(R.string.integration_agent_invalid);}
        });
    }
    /** Forward-edge picker retains stale input visibly until explicitly repaired. */
    private final class BranchTargetField {
        final LinearLayout group;
        final TextView title;
        final Spinner picker;
        List<String> values=new ArrayList<>();
        BranchTargetField(LinearLayout parent,int caption){
            group=new LinearLayout(owner);group.setOrientation(LinearLayout.VERTICAL);
            picker=new Spinner(owner);picker.setSaveEnabled(false);picker.setMinimumHeight(dp(48));
            title=WorkbenchStyle.fieldLabel(owner,group,caption,picker);
            group.addView(picker,WorkbenchStyle.row(owner));parent.addView(group,WorkbenchStyle.row(owner));
        }
        void caption(int resource){title.setText(resource);picker.setContentDescription(s(resource));}
        void render(List<String> allowed,String current){
            values=new ArrayList<>(allowed);List<String> labels=new ArrayList<>();
            for(String target:values)labels.add(branchTargetLabel(target));
            if(!values.contains(current)){
                values.add(current);
                labels.add(owner.getString(R.string.integration_form_missing_target,current==null?"":current));
            }
            ArrayAdapter<String> adapter=new ArrayAdapter<>(owner,android.R.layout.simple_spinner_item,labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            picker.setAdapter(adapter);picker.setSelection(values.indexOf(current));
        }
        String value(){
            int index=picker.getSelectedItemPosition();return index<0||index>=values.size()?"":values.get(index);
        }
    }
    private String branchTargetLabel(String target){
        if(WorkflowBranchPlan.COMPLETE.equals(target))return s(R.string.auto_state_completed);
        if(WorkflowBranchPlan.BLOCK.equals(target))return s(R.string.auto_state_blocked);
        return target;
    }
    private String branchDraftOverview(AutomationDraftForms.BranchDraft draft){
        StringBuilder out=new StringBuilder();
        for(AutomationDraftForms.Row row:draft.rows){
            if(out.length()>0)out.append('\n');
            out.append(row.id).append(" → ");
            if(row.condition)out.append(s(R.string.integration_outcome_true)).append(": ").append(branchTargetLabel(row.next))
                .append(" / ").append(s(R.string.integration_outcome_false)).append(": ").append(branchTargetLabel(row.other));
            else out.append(branchTargetLabel(row.next));
        }
        return out.toString();
    }
    private void showBranchForm(EditText spec,String selectedModel){
        if(busy()||!spec.isEnabled())return;
        final String original=spec.getText().toString();final int token=epoch;
        final AutomationDraftForms.BranchDraft draft;
        try{draft=new AutomationDraftForms.BranchDraft(original,selectedModel);}
        catch(Exception invalid){toast(R.string.auto_invalid_plan);return;}
        LinearLayout l=column();label(l,s(R.string.integration_form_notice));
        EditText model=formField(l,R.string.model_label,draft.model,201,false);
        EditText digest=formField(l,R.string.integration_form_digest,draft.digest,65,false);
        Button bind=formModelButton(l);
        CheckBox retain=draftCheck(l,R.string.integration_form_retain,draft.retain);
        label(l,s(R.string.integration_form_edges));
        TextView step=output(l),ids=output(l);
        EditText node=formField(l,R.string.integration_form_node,"",33,false);
        CheckBox condition=draftCheck(l,R.string.integration_form_condition,false);
        EditText prompt=formField(l,R.string.auto_step_prompt,"",2001,true);
        EditText expect=formField(l,R.string.auto_step_condition,"",201,false);
        CheckBox contains=draftCheck(l,R.string.auto_gate_contains,false);
        CheckBox previous=draftCheck(l,R.string.integration_form_previous,false);
        BranchTargetField next=new BranchTargetField(l,R.string.integration_form_next);
        BranchTargetField other=new BranchTargetField(l,R.string.integration_form_false);
        TextView error=output(l);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        int[] index={0};Button[] controls=new Button[4];
        Runnable toggle=()->{
            boolean c=condition.isChecked();
            ((View)expect.getParent()).setVisibility(c?View.GONE:View.VISIBLE);
            contains.setVisibility(c?View.GONE:View.VISIBLE);
            other.group.setVisibility(c?View.VISIBLE:View.GONE);
            next.caption(c?R.string.integration_form_true:R.string.integration_form_next);
        };
        condition.setOnCheckedChangeListener((view,checked)->toggle.run());
        Runnable capture=()->{
            AutomationDraftForms.Row row=draft.rows.get(index[0]);
            draft.model=model.getText().toString();draft.digest=digest.getText().toString();draft.retain=retain.isChecked();
            row.id=node.getText().toString();row.prompt=prompt.getText().toString();row.condition=condition.isChecked();
            row.expect=expect.getText().toString();row.contains=!row.condition&&contains.isChecked();
            row.previous=previous.isChecked();row.next=next.value();row.other=other.value();
        };
        Runnable render=()->{
            AutomationDraftForms.Row row=draft.rows.get(index[0]);
            step.setText(owner.getString(R.string.auto_editor_index,Integer.toString(index[0]+1),Integer.toString(draft.rows.size())));
            ids.setText(branchDraftOverview(draft));
            node.setText(row.id);prompt.setText(row.prompt);condition.setChecked(row.condition);
            expect.setText(row.expect);contains.setChecked(row.contains);previous.setChecked(row.previous);
            next.render(draft.targets(index[0]),row.next);other.render(draft.targets(index[0]),row.other);
            other.caption(R.string.integration_form_false);toggle.run();error.setText("");
            if(controls[0]!=null)controls[0].setEnabled(index[0]>0);
            if(controls[1]!=null)controls[1].setEnabled(index[0]+1<draft.rows.size());
            if(controls[2]!=null)controls[2].setEnabled(draft.rows.size()<WorkflowBranchPlan.MAX_NODES);
            if(controls[3]!=null)controls[3].setEnabled(draft.rows.size()>1);
        };
        LinearLayout navigationActions=new AdaptiveAnswerActions(owner);l.addView(navigationActions,WorkbenchStyle.row(owner));
        controls[0]=button(navigationActions,R.string.auto_editor_previous,()->{capture.run();if(index[0]>0)index[0]--;render.run();});
        controls[1]=button(navigationActions,R.string.auto_editor_next,()->{capture.run();if(index[0]+1<draft.rows.size())index[0]++;render.run();});
        controls[2]=button(l,R.string.auto_editor_add,()->{capture.run();if(draft.rows.size()<WorkflowBranchPlan.MAX_NODES){draft.insertAfter(index[0]);index[0]++;render.run();}});
        controls[3]=button(l,R.string.auto_editor_remove,()->{
            capture.run();if(draft.rows.size()>1){draft.remove(index[0]);index[0]=Math.min(index[0],draft.rows.size()-1);render.run();}
        });
        render.run();
        AlertDialog d=draftDialog(l);
        bindFormModel(bind,d,model,digest,error,token);
        d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!valid(token)||busy()||!spec.isEnabled()||!original.equals(spec.getText().toString())){d.dismiss();return;}
            capture.run();
            int invalidRow=draft.firstInvalidRow();
            if(invalidRow>=0){
                index[0]=invalidRow;render.run();
                error.setText(owner.getString(R.string.integration_form_row_invalid,invalidRow+1));return;
            }
            try{spec.setText(draft.encode());d.dismiss();}
            catch(Exception invalid){error.setText(R.string.integration_form_invalid);}
        });
    }
    private void showAgentProfile(String model){
        LinearLayout l=column();label(l,s(R.string.integration_agent_notice));
        EditText raw=draftInput(l,R.string.integration_agent_json,model,false);
        TextView status=output(l);
        final AgentProfile[] saved={null};final boolean[] loaded={false};
        Runnable load=()->{
            if(busy())return;
            loaded[0]=false;raw.setEnabled(false);status.setText(R.string.thinking);
            work(()->new AgentProfileStore(owner.getNoBackupFilesDir().getCanonicalFile()).read(),profile->{
                saved[0]=profile;loaded[0]=true;raw.setEnabled(true);raw.setText(profile==null?"":profile.encode());
                status.setText(profile==null?s(R.string.integration_agent_empty):profile.reviewDigest());
            },()->{raw.setEnabled(true);status.setText(R.string.integration_agent_conflict);});
        };
        button(l,R.string.integration_agent_load,()->{
            if(!busy())confirm(s(R.string.integration_agent_load),s(R.string.integration_agent_notice),load);
        });
        button(l,R.string.integration_agent_save,()->{
            if(busy()||!loaded[0]){toast(R.string.integration_agent_conflict);return;}
            final String original=raw.getText().toString();
            final AgentProfile reviewed;
            try{reviewed=AgentProfile.decode(original);}catch(Exception invalid){toast(R.string.integration_agent_invalid);return;}
            final String expected=saved[0]==null?null:saved[0].reviewDigest();
            confirm(s(R.string.integration_agent_save),s(R.string.integration_agent_notice)+"\n\n"+reviewed.encode()+"\n\n"+reviewed.reviewDigest(),()->{
                if(busy()||!original.equals(raw.getText().toString()))return;
                raw.setEnabled(false);status.setText(R.string.thinking);
                work(()->new AgentProfileStore(owner.getNoBackupFilesDir().getCanonicalFile()).save(expected,reviewed),profile->{
                    saved[0]=profile;loaded[0]=true;raw.setEnabled(true);raw.setText(profile.encode());
                    status.setText(s(R.string.integration_agent_saved)+"\n"+profile.reviewDigest());
                },()->{loaded[0]=false;raw.setEnabled(true);status.setText(R.string.integration_agent_conflict);});
            });
        });
        button(l,R.string.integration_agent_copy,()->{
            if(busy())return;
            final String original=raw.getText().toString();final AgentProfile reviewed;
            try{reviewed=AgentProfile.decode(original);}catch(Exception invalid){toast(R.string.integration_agent_invalid);return;}
            confirm(s(R.string.integration_agent_copy),s(R.string.integration_agent_notice)+"\n\n"+reviewed.encode(),()->{
                if(busy()||!original.equals(raw.getText().toString()))return;
                try{
                    android.content.ClipboardManager clipboard=(android.content.ClipboardManager)owner.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    if(clipboard==null)throw new IllegalStateException();
                    clipboard.setPrimaryClip(SensitiveClipboard.text(s(R.string.integration_agent),reviewed.encode()));toast(R.string.copied);
                }catch(RuntimeException e){toast(R.string.chat_block_copy_failed);}
            });
        });
        button(l,R.string.integration_agent_delete,()->{
            if(busy()||!loaded[0]||saved[0]==null)return;
            final AgentProfile reviewed=saved[0];
            confirm(s(R.string.integration_agent_delete),reviewed.name+"\n"+reviewed.reviewDigest(),()->{
                if(busy()||saved[0]!=reviewed)return;
                final String draft=raw.getText().toString();
                raw.setEnabled(false);
                work(()->{new AgentProfileStore(owner.getNoBackupFilesDir().getCanonicalFile()).delete(reviewed.reviewDigest());return true;},ignored->{
                    saved[0]=null;raw.setEnabled(true);
                    if(draft.equals(reviewed.encode())&&draft.equals(raw.getText().toString()))raw.setText("");
                    status.setText(R.string.integration_agent_empty);
                },()->{loaded[0]=false;raw.setEnabled(true);status.setText(R.string.integration_agent_conflict);});
            });
        });
        button(l,R.string.integration_use_model,()->{
            if(busy())return;
            final String original=raw.getText().toString();
            final int token=epoch;
            confirm(s(R.string.integration_use_model),s(R.string.integration_model_notice)+"\n\n"+model,()->{
                if(busy()||!original.equals(raw.getText().toString()))return;
                raw.setEnabled(false);status.setText(R.string.thinking);
                work(()->{
                    String digest=new ReviewedCpuTask(owner,()->!valid(token)).installed(model);
                    return new AgentProfile("Local agent",model,digest,"Answer the user concisely.",700,256);
                },profile->{raw.setEnabled(true);raw.setText(profile.encode());status.setText(profile.modelSha256);},
                    ()->{raw.setEnabled(true);status.setText(R.string.integration_execution_failed);});
            });
        });
        EditText prompt=field(l,R.string.integration_agent_prompt,"",4096,true);
        button(l,R.string.integration_agent_execute,()->{
            if(busy()||!loaded[0]||saved[0]==null)return;
            final String original=raw.getText().toString(),question=prompt.getText().toString();
            final AgentProfile reviewed;
            try{
                reviewed=AgentProfile.decode(original);ReviewedLocalPolicy.text(question,4096);
                if(!reviewed.reviewDigest().equals(saved[0].reviewDigest()))throw new IllegalArgumentException();
            }catch(Exception invalid){toast(R.string.integration_agent_invalid);return;}
            final int token=epoch;
            confirm(s(R.string.integration_agent_execute),s(R.string.integration_model_notice)+"\n\n"+reviewed.encode()+"\n\n"+question,()->{
                if(busy()||!original.equals(raw.getText().toString())||!question.equals(prompt.getText().toString()))return;
                raw.setEnabled(false);prompt.setEnabled(false);status.setText(R.string.thinking);
                work(()->{
                    AgentProfile actual=new AgentProfileStore(owner.getNoBackupFilesDir().getCanonicalFile()).read();
                    if(actual==null||!actual.reviewDigest().equals(reviewed.reviewDigest()))throw new java.io.IOException("Profile changed");
                    return new ReviewedCpuTask(owner,()->!valid(token)).generate(actual.model,actual.modelSha256,
                            actual.instruction,question,actual.maxTokens,actual.temperatureMilli);
                },answer->{raw.setEnabled(true);prompt.setEnabled(true);status.setText(
                        (answer.truncated?s(R.string.integration_partial)+"\n":"")+answer.text);},
                    ()->{raw.setEnabled(true);prompt.setEnabled(true);status.setText(R.string.integration_execution_failed);});
            });
        });
        show(s(R.string.integration_agent),l);load.run();
    }
    private String branchStatus(WorkflowBranchPlan.Status value){
        switch(value){
            case READY:return s(R.string.integration_branch_ready);
            case COMPLETED:return s(R.string.auto_state_completed);
            case BLOCKED:return s(R.string.auto_state_blocked);
            case CANCELLED:return s(R.string.auto_state_cancelled);
            default:throw new IllegalArgumentException("Branch status");
        }
    }
    private String branchOutcome(WorkflowAttemptStore.Outcome value){
        switch(value){
            case PENDING:return s(R.string.integration_outcome_pending);
            case VERIFIED:return s(R.string.integration_outcome_verified);
            case CONDITION_TRUE:return s(R.string.integration_outcome_true);
            case CONDITION_FALSE:return s(R.string.integration_outcome_false);
            case RETRY_SAFE:return s(R.string.auto_state_retry);
            case BLOCKED:return s(R.string.auto_state_blocked);
            case UNCERTAIN:return s(R.string.integration_outcome_uncertain);
            case CANCELLED:return s(R.string.auto_state_cancelled);
            default:throw new IllegalArgumentException("Branch outcome");
        }
    }
    private String branchBudget(BranchWorkflowRun.Definition value){
        return owner.getString(R.string.integration_branch_budget,value.graph.maxPathNodes(),value.maxRequests(),value.maxOutputTokens());
    }
    private String branchSummary(BranchWorkflowRun run,BranchWorkflowRun.Progress progress){
        WorkflowAttemptStore.Ledger value=progress.snapshot.value;
        StringBuilder text=new StringBuilder(s(R.string.integration_run_id)+": "+run.id+"\n"+
                s(R.string.model_label)+": "+run.definition.model+"\n"+branchStatus(value.cursor.status)+"\n");
        text.append(branchBudget(run.definition)).append('\n');
        text.append(owner.getString(R.string.integration_branch_remaining,value.attempts.size(),
                run.definition.remainingRequests(value),run.definition.remainingOutputTokens(value))).append('\n');
        if(value.pending())text.append(s(R.string.integration_pending)).append('\n');
        if(value.cursor.nextNode!=null)text.append(s(R.string.integration_next_node)).append(": ").append(value.cursor.nextNode).append('\n');
        text.append(run.definition.retainOutputs?s(R.string.integration_retained_notice):s(R.string.integration_metadata_only)).append('\n');
        for(WorkflowAttemptStore.Attempt a:value.attempts){
            text.append(a.node).append(" · ").append(branchOutcome(a.outcome)).append('\n');
            if(a.output!=null)text.append(s(R.string.integration_retained_output)).append(": ").append(a.output).append('\n');
        }
        if(!progress.lastAnswer.isEmpty()&&!java.util.Objects.equals(value.previousOutput(),progress.lastAnswer))
            text.append("\n").append(s(R.string.integration_live_answer)).append(": ").append(progress.lastAnswer);
        return text.toString();
    }
    private void showBranch(String model){
        LinearLayout l=column();label(l,s(R.string.integration_branch_notice));
        label(l,s(R.string.integration_retained_help));
        EditText spec=draftInput(l,R.string.integration_branch_json,model,true);
        EditText id=field(l,R.string.integration_run_id,"",64,false);
        TextView status=output(l);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        final BranchWorkflowRun[] selected={null};
        final BranchWorkflowRun.Progress[] snapshot={null};
        final int token=epoch;
        Consumer<Boolean> editable=enabled->{spec.setEnabled(enabled);id.setEnabled(enabled);};
        java.util.function.BiConsumer<BranchWorkflowRun,BranchWorkflowRun.Progress> display=(run,progress)->{
            selected[0]=run;snapshot[0]=progress;id.setText(run.id);spec.setText(run.definition.raw);
            status.setText(branchSummary(run,progress));editable.accept(true);
        };
        Consumer<BranchWorkflowRun> execute=run->{
            BranchWorkflowRun.Progress reviewed=snapshot[0];
            if(reviewed==null||selected[0]!=run)return;
            if(reviewed.snapshot.value.pending()||reviewed.snapshot.value.cursor.status!=WorkflowBranchPlan.Status.READY){
                status.setText(branchSummary(run,reviewed));return;
            }
            editable.accept(false);status.setText(R.string.thinking);
            work(()->run.execute(reviewed.snapshot,task->{
                ReviewedCpuTask cpu=new ReviewedCpuTask(owner,()->!valid(token)||System.currentTimeMillis()<run.started||
                        System.currentTimeMillis()-run.started>=WorkflowAttemptStore.WINDOW_MS);
                return cpu.generate(run.definition.model,run.definition.digest,null,task.request(),BranchWorkflowRun.MAX_TASK_OUTPUT_TOKENS,0);
            },()->!valid(token),progress->owner.runOnUiThread(()->{
                if(valid(token))status.setText(branchSummary(run,progress));
            })),result->display.accept(run,result),()->{
                snapshot[0]=null;editable.accept(true);status.setText(R.string.integration_execution_failed);
            });
        };
        button(l,R.string.integration_use_model,()->{
            if(busy())return;final String original=spec.getText().toString();
            confirm(s(R.string.integration_use_model),s(R.string.integration_model_notice)+"\n\n"+model,()->{
                if(busy()||!original.equals(spec.getText().toString()))return;
                editable.accept(false);status.setText(R.string.thinking);
                work(()->BranchWorkflowRun.chainedExample(model,new ReviewedCpuTask(owner,()->!valid(token)).installed(model)),
                    example->{spec.setText(example);selected[0]=null;snapshot[0]=null;id.setText("");editable.accept(true);status.setText("");},
                    ()->{editable.accept(true);status.setText(R.string.integration_execution_failed);});
            });
        });
        button(l,R.string.integration_branch_new,()->{
            if(busy())return;final String raw=spec.getText().toString();final BranchWorkflowRun.Definition reviewed;
            try{reviewed=BranchWorkflowRun.parse(raw);}catch(Exception invalid){toast(R.string.auto_invalid_plan);return;}
            confirm(s(R.string.integration_branch_new),s(R.string.integration_branch_notice)+"\n\n"+
                    branchBudget(reviewed)+"\n\n"+
                    s(reviewed.retainOutputs?R.string.integration_retained_notice:R.string.integration_metadata_only)+"\n\n"+raw,()->{
                if(busy()||!raw.equals(spec.getText().toString()))return;
                editable.accept(false);status.setText(R.string.thinking);
                work(()->{
                    String actual=new ReviewedCpuTask(owner,()->!valid(token)).installed(reviewed.model);
                    if(!reviewed.digest.equals(actual))throw new java.io.IOException("Local model changed");
                    return BranchWorkflowRun.create(owner.getNoBackupFilesDir().getCanonicalFile(),reviewed,System.currentTimeMillis());
                },run->{
                    selected[0]=run;id.setText(run.id);status.setText(run.id);
                    // Disk read on a worker, then execute the exact created plan.
                    work(()->new BranchWorkflowRun.Progress(run.read(),""),initial->{
                        display.accept(run,initial);execute.accept(run);
                    },()->{snapshot[0]=null;editable.accept(true);status.setText(R.string.integration_execution_failed);});
                },()->{editable.accept(true);status.setText(R.string.integration_execution_failed);});
            });
        });
        button(l,R.string.integration_branch_list,()->{
            if(busy())return;work(()->BranchWorkflowRun.list(owner.getNoBackupFilesDir().getCanonicalFile()),ids->{
                if(ids.isEmpty()){status.setText(R.string.integration_agent_empty);return;}
                String[] rows=ids.toArray(new String[0]);
                if(confirmation!=null)confirmation.dismiss();
                AlertDialog d=new AlertDialog.Builder(owner).setTitle(R.string.integration_branch_list).setItems(rows,(x,w)->{
                    if(valid(token)&&!busy())id.setText(rows[w]);
                }).setNegativeButton(R.string.cancel,null).create();
                confirmation=d;d.setOnDismissListener(x->{if(confirmation==d)confirmation=null;});d.show();secure(d);
            },()->status.setText(R.string.integration_execution_failed));
        });
        button(l,R.string.integration_branch_load,()->{
            if(busy())return;final String runId=id.getText().toString().trim();
            editable.accept(false);status.setText(R.string.thinking);
            work(()->{
                BranchWorkflowRun run=BranchWorkflowRun.open(owner.getNoBackupFilesDir().getCanonicalFile(),runId);
                return new java.util.AbstractMap.SimpleImmutableEntry<>(run,new BranchWorkflowRun.Progress(run.read(),""));
            },pair->display.accept(pair.getKey(),pair.getValue()),()->{
                selected[0]=null;snapshot[0]=null;editable.accept(true);status.setText(R.string.integration_execution_failed);
            });
        });
        button(l,R.string.auto_resume,()->{
            if(busy()||selected[0]==null||snapshot[0]==null)return;
            BranchWorkflowRun run=selected[0];BranchWorkflowRun.Progress reviewed=snapshot[0];
            if(!run.id.equals(id.getText().toString().trim())||!run.definition.raw.equals(spec.getText().toString())||
                    reviewed.snapshot.value.pending()||reviewed.snapshot.value.cursor.status!=WorkflowBranchPlan.Status.READY){
                toast(R.string.auto_resume_changed);return;
            }
            confirm(s(R.string.auto_resume),s(R.string.integration_branch_notice)+"\n\n"+branchSummary(run,reviewed)+"\n\n"+run.definition.raw,()->{
                if(!busy()&&selected[0]==run&&snapshot[0]==reviewed&&run.id.equals(id.getText().toString().trim())&&
                        run.definition.raw.equals(spec.getText().toString()))execute.accept(run);
            });
        });
        button(l,R.string.auto_cancel_run,()->{
            if(busy()||selected[0]==null||snapshot[0]==null||!snapshot[0].snapshot.value.pending())return;
            BranchWorkflowRun run=selected[0];BranchWorkflowRun.Progress reviewed=snapshot[0];
            confirm(s(R.string.auto_cancel_run),s(R.string.integration_pending)+"\n\n"+branchSummary(run,reviewed),()->{
                if(busy()||selected[0]!=run||snapshot[0]!=reviewed)return;
                work(()->run.abandon(reviewed.snapshot,System.currentTimeMillis()),result->display.accept(run,result),
                    ()->status.setText(R.string.integration_execution_failed));
            });
        });
        button(l,R.string.integration_branch_delete,()->{
            if(busy()||selected[0]==null||snapshot[0]==null)return;
            BranchWorkflowRun run=selected[0];BranchWorkflowRun.Progress reviewed=snapshot[0];
            confirm(s(R.string.integration_branch_delete),branchSummary(run,reviewed),()->{
                if(busy()||selected[0]!=run||snapshot[0]!=reviewed)return;
                work(()->{BranchWorkflowRun.delete(owner.getNoBackupFilesDir().getCanonicalFile(),run.id,reviewed.snapshot.revision);return true;},
                    ignored->{selected[0]=null;snapshot[0]=null;if(run.id.equals(id.getText().toString().trim()))id.setText("");status.setText("");},
                    ()->status.setText(R.string.integration_execution_failed));
            });
        });
        show(s(R.string.integration_branch),l);
    }
    private void showWorkflow(String model){
        LinearLayout l=column();label(l,s(R.string.auto_workflow_notice));label(l,s(R.string.auto_verifier_help));label(l,s(R.string.model_label)+": "+model);
        final LocalWorkflow runner=new LocalWorkflow(owner);workflow=runner;
        LocalWorkflow.Checkpoint saved=null;
        try{saved=runner.load();}catch(Exception e){toast(R.string.auto_checkpoint_error);}
        Button[] buttons=new Button[13];
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
        Runnable enabled=()->{boolean idle=!busy();plan.setEnabled(idle);for(Button b:buttons)if(b!=null)b.setEnabled(idle);
            LocalWorkflow.Checkpoint current=checkpoint[0];
            if(buttons[1]!=null)buttons[1].setEnabled(current!=null&&WorkflowPolicy.resumeChoiceEnabled(idle,current.resumable(),current.plan,plan.getText().toString(),current.model,model));
            if(buttons[7]!=null)buttons[7].setEnabled(idle&&current!=null);
            if(buttons[11]!=null)buttons[11].setEnabled(idle&&checkpoint[0]!=null);
            if(buttons[12]!=null)buttons[12].setEnabled(idle&&checkpoint[0]!=null);};
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
            try{
                if(!DocumentBatchPolicy.fits(WorkflowPolicy.firstPrompt(spec),WorkflowPolicy.MAX_TOKENS)){
                    toast(R.string.attachment_context_limit);return;
                }
            }catch(Exception e){toast(R.string.auto_invalid_plan);return;}
            if(model==null||model.isEmpty()||!OllamaForegroundService.isRunning()){toast(R.string.test_need_model);return;}
            confirm(s(R.string.auto_start),s(R.string.auto_workflow_notice)+"\n\n"+s(R.string.auto_history_notice)+"\n\n"+spec,()->{
                if(busy()||!spec.equals(plan.getText().toString()))return;
                if(!OllamaForegroundService.isRunning()){toast(R.string.test_need_server);return;}
                if(!"off".equals(CoopSession.get(owner).role())){toast(R.string.guest_stop_first);return;}
                InferenceSafetyPolicy.Reason risk=InferenceSafety.check(owner);
                if(risk!=InferenceSafetyPolicy.Reason.NONE){toast(InferenceSafety.label(risk));return;}
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
            confirm(s(R.string.auto_clear),s(R.string.auto_clear_current_notice),()->{try{runner.clear();checkpoint[0]=null;plan.setText("");status.setText("");progress.setProgress(0);enabled.run();}catch(Exception e){toast(R.string.auto_checkpoint_error);}});
        });
        button(l,R.string.auto_cancel_run,()->{
            if(planner!=null&&busy()){toast(R.string.busy);return;}
            if(checkpoint[0]==null)return;
            confirm(s(R.string.auto_cancel_run),s(R.string.auto_cancel_notice),()->{
                if(planner!=null&&busy()){toast(R.string.busy);return;}
                if(busy()){runner.cancel();status.setText(R.string.auto_cancel_notice);}
                else try{runner.cancelSaved(checkpoint[0]);display(status,checkpoint[0]);enabled.run();}catch(Exception e){toast(R.string.auto_checkpoint_error);}
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
        final int readToken=epoch;
        buttons[12]=viewer.add(l,R.string.auto_workflow,()->status.getText().toString(),
            ()->valid(readToken)&&!busy()&&checkpoint[0]!=null&&confirmation==null&&dialog!=null&&dialog.isShowing());
        buttons[12].setEnabled(checkpoint[0]!=null&&!busy());
        l.removeView(buttons[12]);l.addView(buttons[12],l.indexOfChild(status),WorkbenchStyle.row(owner));
        buttons[11]=reading.add(l,R.string.auto_workflow,()->status.getText().toString(),
            ()->valid(readToken)&&!busy()&&checkpoint[0]!=null&&confirmation==null&&dialog!=null&&dialog.isShowing());
        buttons[11].setEnabled(checkpoint[0]!=null&&!busy());
        plan.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence text,int start,int count,int after){}
            public void onTextChanged(CharSequence text,int start,int before,int count){}
            public void afterTextChanged(android.text.Editable text){enabled.run();}
        });
        enabled.run();
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
        LinearLayout navigationActions=new AdaptiveAnswerActions(owner);l.addView(navigationActions,WorkbenchStyle.row(owner));
        controls[0]=button(navigationActions,R.string.auto_editor_previous,()->{capture.run();if(index[0]>0)index[0]--;render.run();});
        controls[1]=button(navigationActions,R.string.auto_editor_next,()->{capture.run();if(index[0]+1<editor.size())index[0]++;render.run();});
        controls[2]=button(l,R.string.auto_editor_add,()->{capture.run();if(editor.size()<WorkflowPolicy.MAX_STEPS){editor.insertAfter(index[0]);index[0]++;render.run();}});
        controls[3]=button(l,R.string.auto_editor_remove,()->{
            capture.run();if(editor.size()>1){editor.remove(index[0]);index[0]=Math.min(index[0],editor.size()-1);render.run();}
        });
        LinearLayout orderActions=new AdaptiveAnswerActions(owner);l.addView(orderActions,WorkbenchStyle.row(owner));
        controls[4]=button(orderActions,R.string.auto_move_up,()->{capture.run();index[0]=editor.move(index[0],-1);render.run();});
        controls[5]=button(orderActions,R.string.auto_move_down,()->{capture.run();index[0]=editor.move(index[0],1);render.run();});
        render.run();
        ScrollView sc=new ScrollView(owner);sc.addView(l);
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(R.string.auto_visual_editor).setView(sc)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.auto_apply_draft,null).create();
        trackDialog(d);
        d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!valid(token)||busy()||!original.equals(plan.getText().toString())){d.dismiss();return;}
            capture.run();
            if(editor.unchanged()){d.dismiss();return;}
            int invalid=editor.firstInvalid();
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
        Button voiceGoal=dictation.add(l,goal,R.string.auto_goal_planner_goal,WorkflowGoalPlanner.MAX_GOAL,false,
            ()->valid(token)&&!busy()&&confirmation==d&&d.isShowing()&&original.equals(draft.getText().toString()));
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
                goal.setEnabled(true);voiceGoal.setEnabled(true);
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
                goal.setEnabled(true);voiceGoal.setEnabled(true);d.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                d.getButton(DialogInterface.BUTTON_NEUTRAL).setEnabled(false);
                d.getButton(DialogInterface.BUTTON_NEGATIVE).setEnabled(true);
                generate[0].setEnabled(true);
            });
            updateParent.run();goal.setEnabled(false);voiceGoal.setEnabled(false);
            d.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
            d.getButton(DialogInterface.BUTTON_NEUTRAL).setEnabled(true);
            generate[0].setEnabled(false);
        });
        confirmation=d;d.setOnDismissListener(x->{
            if(active[0]!=null){cancelling[0]=true;active[0].cancel();}
            if(confirmation==d)confirmation=null;
            updateParent.run();
        });
        d.show();secure(d);WorkbenchWindow.fit(owner,d);
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
            case CONTEXT_LIMIT:return s(R.string.conference_context)+" · "+s(R.string.attachment_context_limit);
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
