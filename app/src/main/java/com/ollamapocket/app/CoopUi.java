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
        add(col,label(a,"하나의 호스트가 세션을 열면 같은 Wi-Fi의 보조 기기가 검색해 참가합니다.\n"+
            "RPC는 기기 내부에서 실행하고 기기 사이 연결은 암호화 중계합니다.\n"+
            "신뢰하는 Wi-Fi에서만 사용하세요. 최대 3대 · 45분 뒤 종료."));
        TextView state=label(a,a.getString(R.string.coop_status_check));add(col,state);
        TextView secret=label(a,"");secret.setTextIsSelectable(true);add(col,secret);
        Button host=button(a,a.getString(R.string.coop_host));add(col,host);
        Button share=button(a,a.getString(R.string.coop_share));add(col,share);
        Button stop=button(a,a.getString(R.string.guest_end));add(col,stop);
        add(col,label(a,"호스트에서 연결된 보조 기기들의 RAM·CPU·GPU·배터리 온도·연결 상태를 확인할 수 있습니다.\n" +
            "GPU 지표는 제조사 커널에서 제공하고 앱에 읽기 권한이 있을 때만 표시합니다."));
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
            state.setText(session.status()+"\n패키지: "+(CoopSession.packaged(a)?"협력용 RPC 엔진 포함":"협력 RPC 엔진 없음"));
            if("host".equals(session.role())){
                secret.setText("호스트: "+session.hostIp()+":"+CoopSession.CONTROL_PORT+"\n"+
                    "세션 ID: "+session.sessionId()+"\n연결 코드: "+session.joinCode()+"\n"+
                    "보조 노드 (승인된 코드 보유자만):\n"+formatNodes(session));
            }else secret.setText("");
            StringBuilder sb=new StringBuilder("발견된 호스트 (탭하면 주소·세션 ID 입력):\n");
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
            .setMessage("이 기기를 호스트로 설정합니다. 신뢰하는 같은 Wi-Fi의 기기만 연결하세요. " +
                "기기 이름과 메모리 여유량이 세션에 표시됩니다. 45분 뒤 자동 종료됩니다.")
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
            String value="OnHand AI 협력 세션\n호스트 IP: "+session.hostIp()+"\n세션 ID: "+session.sessionId()+
                "\n연결 코드: "+session.joinCode()+"\n이 코드는 신뢰하는 보조 기기에만 공유하세요.";
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
                String result=DistributedRunner.preview(a.getApplicationContext(),model);
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
    private static String formatNodes(CoopSession s){
        StringBuilder b=new StringBuilder();
        for(CoopSession.Node node:s.nodes()) {
            long age=(SystemClock.elapsedRealtime()-node.lastSeen)/1000;
            boolean online=age<30&&node.workerOk;
            b.append(" • ").append(node.name).append(" (").append(node.ip).append(")")
                .append(online?" · 정상":" · 연결/워커 확인 필요")
                .append(" · 마지막 보고 ").append(age).append("초 전\n")
                .append("   안전 RAM ").append(node.safeRamMiB).append(" MiB")
                .append(" / CPU ").append(node.cpuPermille<0?"조회 불가":(node.cpuPermille/10)+"%")
                .append(" / GPU ").append(node.gpuPermille<0?"조회 불가":(node.gpuPermille/10)+"%")
                .append("\n   배터리 ").append(node.batteryTenths<0?"조회 불가":(node.batteryTenths/10.0)+"℃")
                .append(" · 열 단계 ").append(node.thermalStatus<0?"조회 불가":node.thermalStatus)
                .append(" · RPC ").append(node.workerOk?"실행":"중지").append("\n");
        }
        if(b.length()==0)b.append("대기 중 · 최대 3대\n");
        return b.toString();
    }
    private static void message(Activity a,String s){
        new AlertDialog.Builder(a).setMessage(s==null?a.getString(R.string.coop_unknown_error):s).setPositiveButton(R.string.ok,null).show();
    }
}
