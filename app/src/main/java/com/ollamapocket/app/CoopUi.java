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
    public static void show(Activity a){
        CoopSession session=CoopSession.get(a);
        ScrollView scroll=new ScrollView(a);
        LinearLayout col=new LinearLayout(a);col.setOrientation(LinearLayout.VERTICAL);col.setPadding(25,15,25,20);
        col.setBackgroundColor(background(a));scroll.addView(col);
        add(col,label(a,"하나의 호스트가 세션을 열면 같은 Wi-Fi의 보조 기기가 검색해 참가합니다.\n"+
            "RPC는 기기 내부에서 실행하고 기기 사이 연결은 암호화 중계합니다.\n"+
            "신뢰하는 Wi-Fi에서만 사용하세요. 최대 3대 · 45분 뒤 종료."));
        TextView state=label(a,"상태 확인 중");add(col,state);
        TextView secret=label(a,"");secret.setTextIsSelectable(true);add(col,secret);
        Button host=button(a,"① 이 기기로 호스트 세션 만들기");add(col,host);
        Button share=button(a,"세션 정보 공유");add(col,share);
        Button stop=button(a,"연결 종료 (모든 노드 종료)");add(col,stop);
        add(col,label(a,"호스트에서 연결된 보조 기기들의 RAM·CPU·GPU·배터리 온도·연결 상태를 확인할 수 있습니다.\n" +
            "GPU 지표는 제조사 커널에서 제공하고 앱에 읽기 권한이 있을 때만 표시합니다."));
        add(col,label(a,"② 보조 기기에서 호스트 검색 → 아래 정보로 참가"));
        Button scan=button(a,"동일 Wi-Fi 세션 검색");add(col,scan);
        TextView found=label(a,"검색 전");add(col,found);
        EditText ip=edit(a,"호스트 IP (예: 192.168.0.10)");add(col,ip);
        EditText sessionId=edit(a,"호스트 세션 ID");add(col,sessionId);
        EditText code=edit(a,"호스트가 표시한 43자리 연결 코드");
        code.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        add(col,code);
        Button join=button(a,"③ 이 기기를 보조 노드로 연결");add(col,join);
        Button preflight=button(a,"선택 모델의 RAM 안전 배정량 점검");add(col,preflight);
        Button enable=button(a,"협력 채팅 모드 사용 (호스트에서 선택)");add(col,enable);
        add(col,label(a,"협력 모드는 큰 모델 실행을 위한 실험 기능입니다.\n"+
            "호스트에만 GGUF 파일이 필요하며, 전송 시간·배터리·발열이 증가할 수 있습니다.\n"+
            "일반 채팅은 단독 모드 그대로 사용 가능합니다.\n" +
            "참가 시 기기·성능 상태가 암호화된 세션 안에서 공유됩니다."));
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("협력 추론 · LAN 세션")
            .setView(scroll).setPositiveButton("닫기",null).create();
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
            .setTitle("협력 호스트 시작")
            .setMessage("이 기기를 호스트로 설정합니다. 신뢰하는 같은 Wi-Fi의 기기만 연결하세요. " +
                "기기 이름과 메모리 여유량이 세션에 표시됩니다. 45분 뒤 자동 종료됩니다.")
            .setNegativeButton("취소",null)
            .setPositiveButton("세션 생성",(d,w)->{
                try{CoopService.start(a,CoopService.HOST,null,null,null,null);}
                catch(Exception e){message(a,e.getMessage());}
            }).show());
        scan.setOnClickListener(v->{session.startDiscovery();message(a,"동일 Wi-Fi 호스트를 검색합니다");});
        join.setOnClickListener(v->{
            String hostIP=ip.getText().toString().trim();
            String sid=sessionId.getText().toString().trim();
            String secretKey=code.getText().toString().trim();
            if(hostIP.isEmpty() || sid.isEmpty() || secretKey.isEmpty()){
                message(a,"호스트 주소, 세션 ID, 연결 코드를 입력하세요");return;
            }
            new AlertDialog.Builder(a).setTitle("보조 노드 허용")
                .setMessage("이 기기의 RAM/CPU 자원을 호스트의 추론 작업에 제공합니다. " +
                    "발열·전력 소모가 증가하며 45분 뒤 자동 종료됩니다.\n" +
                    "데이터는 동일 Wi-Fi 안의 암호화된 중계를 통해 전송됩니다.")
                .setNegativeButton("취소",null)
                .setPositiveButton("연결 허용",(d,w)->{
                    try{CoopService.start(a,CoopService.GUEST,hostIP,sid,secretKey,
                        (Build.MANUFACTURER+" "+Build.MODEL).trim());
                        session.stopDiscovery();
                    }catch(Exception e){message(a,e.getMessage());}
                }).show();
        });
        share.setOnClickListener(v->{
            if(!"host".equals(session.role())){message(a,"먼저 호스트 세션을 만드세요");return;}
            String value="AI in Pocket 협력 세션\n호스트 IP: "+session.hostIp()+"\n세션 ID: "+session.sessionId()+
                "\n연결 코드: "+session.joinCode()+"\n이 코드는 신뢰하는 보조 기기에만 공유하세요.";
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");send.putExtra(Intent.EXTRA_TEXT,value);
            a.startActivity(Intent.createChooser(send,"세션 정보 공유"));
        });
        stop.setOnClickListener(v->new AlertDialog.Builder(a).setMessage("협력 추론 세션을 종료할까요?")
            .setNegativeButton("취소",null).setPositiveButton("종료",(d,w)->{
                CoopService.start(a,CoopService.STOP,null,null,null,null);
                a.getPreferences(Context.MODE_PRIVATE).edit().putString("compute_preference","auto").apply();
            }).show());
        preflight.setOnClickListener(v->{
            if(!"host".equals(session.role())){message(a,"호스트 세션을 만든 뒤 점검하세요");return;}
            String model=a.getPreferences(Context.MODE_PRIVATE).getString("model","");
            if(model.isEmpty()){message(a,"모델을 먼저 선택하세요");return;}
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
                message(a,"호스트에서 보조 노드 연결을 먼저 완료하세요");return;
            }
            a.getPreferences(Context.MODE_PRIVATE).edit().putString("compute_preference","cooperative").apply();
            message(a,"협력 추론 선택됨 · 다음 채팅부터 큰 모델 실행을 시도합니다");
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
        new AlertDialog.Builder(a).setMessage(s==null?"알 수 없는 오류":s).setPositiveButton("확인",null).show();
    }
}
