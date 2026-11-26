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
        scan[0]=button(l,R.string.cloud_fetch_models,()->{
            final String target=address.getText().toString().trim();final McpLanClient client;
            try{client=new McpLanClient(target);}catch(Exception e){toast(R.string.cloud_pc_address);return;}
            confirm(s(R.string.auto_mcp),client.endpoint()+"\n\n"+s(R.string.auto_mcp_notice),()->{
                if(busy()||!target.equals(address.getText().toString().trim()))return;
                if(mcp!=null)mcp.cancel();mcp=client;tools.removeAllViews();scan[0].setEnabled(false);status.setText(R.string.thinking);
                work(client::connect,list->{
                    scan[0].setEnabled(true);
                    status.setText(s(R.string.auto_mcp_tools)+" "+list.size());
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
        ScrollView scroll=new ScrollView(owner);scroll.addView(l);
        int token=epoch;
        AlertDialog d=new AlertDialog.Builder(owner).setTitle(tool.name).setView(scroll)
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.send,null).create();
        confirmation=d;d.setOnDismissListener(x->{if(confirmation==d)confirmation=null;});d.show();secure(d);
        d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!valid(token)||busy()||mcp!=client)return;
            final String value=args.getText().toString();
            try{new org.json.JSONObject(value);}catch(Exception e){toast(R.string.cloud_fields);return;}
            d.dismiss();
            confirm(tool.name,client.endpoint()+"\n\n"+value+"\n\n"+s(R.string.auto_mcp_call_notice),()->{
                if(mcp!=client||busy())return;
                status.setText(R.string.thinking);
                work(()->client.invoke(tool,value),result->status.setText(result.toString()),()->status.setText(R.string.auto_mcp_failed));
            });
        });
    }
    private void showWorkflow(String model){
        LinearLayout l=column();label(l,s(R.string.auto_workflow_notice));label(l,s(R.string.model_label)+": "+model);
        final LocalWorkflow runner=new LocalWorkflow(owner);workflow=runner;
        LocalWorkflow.Checkpoint saved=null;
        try{saved=runner.load();}catch(Exception e){toast(R.string.auto_checkpoint_error);}
        EditText plan=field(l,R.string.auto_plan,saved==null?"":saved.plan,24000,true);
        TextView status=output(l);
        final LocalWorkflow.Checkpoint[] checkpoint={saved};
        if(saved!=null)display(status,saved);
        Button[] buttons=new Button[3];
        Runnable enabled=()->{boolean idle=!busy();plan.setEnabled(idle);for(Button b:buttons)if(b!=null)b.setEnabled(idle);};
        Consumer<LocalWorkflow.Checkpoint> start=c->{
            if(!c.model.equals(model)){toast(R.string.need_model);return;}
            if(!c.resumable()){display(status,c);toast(R.string.auto_blocked);return;}
            int token=epoch;
            status.setText(R.string.thinking);
            work(()->runner.run(c,state->{String message=summary(state);owner.runOnUiThread(()->{if(valid(token))status.setText(message);});},()->stopped),
                result->{checkpoint[0]=result;display(status,result);enabled.run();},
                ()->{try{checkpoint[0]=runner.load();if(checkpoint[0]!=null)display(status,checkpoint[0]);}catch(Exception ignored){}toast(R.string.auto_checkpoint_error);enabled.run();});
            enabled.run();
        };
        buttons[0]=button(l,R.string.auto_start,()->{
            if(busy())return;final String spec=plan.getText().toString();
            try{WorkflowPolicy.parse(spec);}catch(Exception e){toast(R.string.auto_invalid_plan);return;}
            if(model==null||model.isEmpty()||!OllamaForegroundService.isRunning()){toast(R.string.test_need_model);return;}
            confirm(s(R.string.auto_start),s(R.string.auto_workflow_notice)+"\n\n"+spec,()->{
                if(busy())return;
                try{checkpoint[0]=runner.create(spec,model);start.accept(checkpoint[0]);}catch(Exception e){toast(R.string.auto_checkpoint_error);}
            });
        });
        buttons[1]=button(l,R.string.auto_resume,()->{
            if(busy()||checkpoint[0]==null){toast(R.string.auto_blocked);return;}
            LocalWorkflow.Checkpoint c=checkpoint[0];
            if(!c.plan.equals(plan.getText().toString())||!c.model.equals(model)){toast(R.string.auto_invalid_plan);return;}
            confirm(s(R.string.auto_resume),s(R.string.auto_workflow_notice),()->start.accept(c));
        });
        buttons[2]=button(l,R.string.auto_clear,()->{
            if(busy())return;
            confirm(s(R.string.auto_clear),s(R.string.auto_clear),()->{try{runner.clear();checkpoint[0]=null;plan.setText("");status.setText("");}catch(Exception e){toast(R.string.auto_checkpoint_error);}});
        });
        button(l,R.string.auto_pause,()->{pause();});
        show(s(R.string.auto_workflow),l);
    }
    private String summary(LocalWorkflow.Checkpoint c){
        return c.status+" · "+c.results.size()+"/"+c.steps().size()+" · "+c.attempts+"/"+WorkflowPolicy.MAX_ATTEMPTS;
    }
    private void display(TextView status,LocalWorkflow.Checkpoint c){
        StringBuilder text=new StringBuilder(summary(c));
        for(int i=0;i<c.results.size();i++)text.append("\n\n").append(i+1).append(". PASS (literal)\n").append(c.results.get(i));
        status.setText(text.toString());
    }
}
