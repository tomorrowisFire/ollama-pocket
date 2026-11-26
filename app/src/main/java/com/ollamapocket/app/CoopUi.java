package com.ollamapocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.InputType;
import java.util.Map;

/** Android-native host/guest UI; never auto-joins a network without user approval. */
public final class CoopUi {
    private static int background(Activity a) {
        return "mono_light".equals(a.getPreferences(Context.MODE_PRIVATE).getString("ui_theme","dark"))
            ? Color.rgb(250,250,250) : Color.rgb(21,23,27);
    }
    private static int foreground(Activity a) {
        return "mono_light".equals(a.getPreferences(Context.MODE_PRIVATE).getString("ui_theme","dark"))
            ? Color.rgb(24,24,24) : Color.rgb(241,243,247);
    }
    private CoopUi() { }
    private static TextView label(Activity a,String s){
        TextView v=new TextView(a);v.setText(s);v.setTextColor(foreground(a));v.setTextSize(13);
        v.setPadding(8,8,8,8);return v;
    }
    private static Button button(Activity a,String s){Button b=new Button(a);b.setAllCaps(false);b.setText(s);return b;}
    private static void add(LinearLayout l,View v){l.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private static EditText edit(Activity a,String hint){
        EditText e=new EditText(a);e.setTextSize(13);e.setTextColor(foreground(a));e.setHintTextColor(0xff8b8b8b);
        e.setHint(hint);e.setSingleLine(true);return e;
    }
    public static void show(Activity a){show(a,false);}
    public static void show(Activity a,boolean guestOnly){
        CoopSession session=CoopSession.get(a);
        ScrollView scroll=new ScrollView(a);
        LinearLayout col=new LinearLayout(a);col.setOrientation(LinearLayout.VERTICAL);col.setPadding(25,15,25,20);
        col.setBackgroundColor(background(a));scroll.addView(col);
        add(col,label(a,a.getString(R.string.coop_intro_detail)));
        TextView state=label(a,a.getString(R.string.coop_status_check));add(col,state);
        TextView secret=label(a,"");secret.setTextIsSelectable(true);add(col,secret);
        Button host=button(a,a.getString(R.string.coop_host));add(col,host);
        Button share=button(a,a.getString(R.string.coop_share));add(col,share);
        Button stop=button(a,a.getString(R.string.guest_end));add(col,stop);
        add(col,label(a,a.getString(R.string.coop_metrics_detail)));
        add(col,label(a,a.getString(R.string.coop_guest_prompt)));
        Button scan=button(a,a.getString(R.string.guest_scan));add(col,scan);
        TextView found=label(a,a.getString(R.string.coop_scan_idle));add(col,found);
        EditText ip=edit(a,a.getString(R.string.guest_ip));add(col,ip);
        EditText sessionId=edit(a,a.getString(R.string.guest_id));add(col,sessionId);
        EditText code=edit(a,a.getString(R.string.guest_code));
        code.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        add(col,code);
        Button join=button(a,a.getString(R.string.guest_session));add(col,join);
        Button preflight=button(a,a.getString(R.string.coop_preflight));add(col,preflight);
        Button enable=button(a,a.getString(R.string.coop_enable));add(col,enable);
        add(col,label(a,a.getString(R.string.guest_desc)));
        if(guestOnly){
            host.setVisibility(View.GONE);share.setVisibility(View.GONE);
            preflight.setVisibility(View.GONE);enable.setVisibility(View.GONE);
        }
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle(guestOnly?a.getString(R.string.guest):a.getString(R.string.coop_title))
            .setView(scroll).setPositiveButton(R.string.close,null).create();
        Handler handler=new Handler(Looper.getMainLooper());
        Runnable[] refresh={null};
        refresh[0]=()->{
            if(!dialog.isShowing())return;
            state.setText(session.status(a)+"\n"+a.getString(R.string.coop_package,a.getString(CoopSession.packaged(a)?R.string.coop_rpc_available:R.string.coop_rpc_missing)));
            if("host".equals(session.role())){
                secret.setText(a.getString(R.string.coop_host_details,session.hostIp()+":"+CoopSession.CONTROL_PORT,session.sessionId(),session.joinCode(),formatNodes(a,session)));
            }else secret.setText("");
            StringBuilder sb=new StringBuilder(a.getString(R.string.coop_found_hosts));
            for(Map.Entry<String,String> item:session.discovered().entrySet()) {
                sb.append(item.getKey()).append(" → ").append(item.getValue()).append("\n");
            }
            found.setText(sb.toString());
            found.setOnClickListener(v->{
                for(Map.Entry<String,String> item:session.discovered().entrySet()){
                    ip.setText(item.getValue());
                    sessionId.setText(item.getKey().replace("AI-in-Pocket-",""));
                    break;
                }
            });
            handler.postDelayed(refresh[0],1000);
        };
        host.setOnClickListener(v->new AlertDialog.Builder(a)
            .setTitle(a.getString(R.string.coop_start_host))
            .setMessage(R.string.coop_host_confirm)
            .setNegativeButton(R.string.cancel,null)
            .setPositiveButton(a.getString(R.string.coop_create),(d,w)->{
                try{CoopService.start(a,CoopService.HOST,null,null,null,null);}
                catch(Exception e){message(a,e.getMessage());}
            }).show());
        scan.setOnClickListener(v->{session.startDiscovery();message(a,a.getString(R.string.coop_searching));});
        join.setOnClickListener(v->{
            String hostIP=ip.getText().toString().trim();
            String sid=sessionId.getText().toString().trim();
            String secretKey=code.getText().toString().trim();
            if(hostIP.isEmpty() || sid.isEmpty() || secretKey.isEmpty()){
                message(a,a.getString(R.string.guest_fields));return;
            }
            new AlertDialog.Builder(a).setTitle(R.string.guest)
                .setMessage(a.getString(R.string.guest_confirm)+"\n\n"+a.getString(R.string.guest_desc))
                .setNegativeButton(R.string.cancel,null)
                .setPositiveButton(R.string.ok,(d,w)->{
                    try{CoopService.start(a,CoopService.GUEST,hostIP,sid,secretKey,
                        (Build.MANUFACTURER+" "+Build.MODEL).trim());
                        session.stopDiscovery();
                    }catch(Exception e){message(a,e.getMessage());}
                }).show();
        });
        share.setOnClickListener(v->{
            if(!"host".equals(session.role())){message(a,a.getString(R.string.coop_create_first));return;}
            String value=a.getString(R.string.coop_share_body,session.hostIp(),session.sessionId(),session.joinCode());
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");send.putExtra(Intent.EXTRA_TEXT,value);
            a.startActivity(Intent.createChooser(send,a.getString(R.string.coop_share)));
        });
        stop.setOnClickListener(v->new AlertDialog.Builder(a).setMessage(a.getString(R.string.coop_end_question))
            .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.guest_end,(d,w)->{
                CoopService.start(a,CoopService.STOP,null,null,null,null);
                a.getPreferences(Context.MODE_PRIVATE).edit().putString("compute_preference","auto").apply();
            }).show());
        preflight.setOnClickListener(v->{
            if(!"host".equals(session.role())){message(a,a.getString(R.string.coop_preflight_host));return;}
            String model=a.getPreferences(Context.MODE_PRIVATE).getString("model","");
            if(model.isEmpty()){message(a,a.getString(R.string.coop_select_model));return;}
            preflight.setEnabled(false);
            new Thread(()->{
                String result=DistributedRunner.previewLocalized(a.getApplicationContext(),model);
                a.runOnUiThread(()->{
                    preflight.setEnabled(true);
                    if(!a.isFinishing())message(a,result);
                });
            },"coop-ram-preflight").start();
        });
        enable.setOnClickListener(v->{
            if(!"host".equals(session.role()) || session.readyNodes().isEmpty()){
                message(a,a.getString(R.string.coop_connect_first));return;
            }
            a.getPreferences(Context.MODE_PRIVATE).edit().putString("compute_preference","cooperative").apply();
            message(a,a.getString(R.string.coop_enabled));
        });
        dialog.setOnDismissListener(d->{handler.removeCallbacks(refresh[0]);
            if(!"host".equals(session.role()) && !"guest".equals(session.role()))session.stopDiscovery();});
        dialog.show();handler.post(refresh[0]);
    }
    private static String formatNodes(Activity a,CoopSession s){
        StringBuilder b=new StringBuilder();
        for(CoopSession.Node node:s.nodes())
            b.append("• ").append(CoopDiagnosticPresentation.host(a,node,true)).append("\n\n");
        return b.length()==0?a.getString(R.string.node_waiting):b.toString();
    }
    private static void message(Activity a,String s){
        new AlertDialog.Builder(a).setMessage(UiText.error(a,s)).setPositiveButton(R.string.ok,null).show();
    }
}
