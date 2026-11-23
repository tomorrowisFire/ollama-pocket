package com.ollamapocket.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit, temporary foreground service for a host or guest compute session. */
public final class CoopService extends Service {
    public static final String HOST="com.ollamapocket.COOP_HOST";
    public static final String GUEST="com.ollamapocket.COOP_GUEST";
    public static final String STOP="com.ollamapocket.COOP_STOP";
    private static final String CHANNEL="aipocket_coop";
    private static final int NOTIFY=58;
    private final ExecutorService jobs=Executors.newSingleThreadExecutor();

    @Override public void onCreate(){
        super.onCreate();
        NotificationManager mgr=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch=new NotificationChannel(CHANNEL,"AI in Pocket 협력 추론", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("사용자가 켠 협력 노드 세션을 유지합니다");
        mgr.createNotificationChannel(ch);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        String action=intent.getAction();
        if(STOP.equals(action)){
            DistributedRunner.stop();CoopSession.get(this).stop();
            stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;
        }
        startForeground(NOTIFY,notification());
        jobs.execute(()->{
            try{
                if(HOST.equals(action))CoopSession.get(this).startHost();
                else if(GUEST.equals(action))CoopSession.get(this).startGuest(
                    intent.getStringExtra("ip"),intent.getStringExtra("session"),intent.getStringExtra("code"),
                    intent.getStringExtra("name"));
            }catch(Exception e){
                Log.e("AiPocketCoop","Could not start cooperative session",e);
                CoopSession.get(this).stop();
                CoopSession.get(this).setError(e.getMessage());
            }
        });
        return START_NOT_STICKY;
    }
    private Notification notification(){
        Intent launch=new Intent(this,MainActivity.class);
        PendingIntent pending=PendingIntent.getActivity(this,0,launch,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent quit=new Intent(this,CoopService.class).setAction(STOP);
        PendingIntent stop=PendingIntent.getService(this,1,quit,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("AI in Pocket · 협력 노드 연결")
            .setContentText("신뢰할 수 있는 동일 Wi-Fi에서 일시적으로 분산 추론을 준비합니다")
            .setContentIntent(pending).setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause,"세션 종료",stop).build();
    }
    @Override public void onDestroy(){
        DistributedRunner.stop();CoopSession.get(this).stop();jobs.shutdownNow();super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
    public static void start(Context context,String action,String host,String session,String code,String name){
        Intent intent=new Intent(context,CoopService.class).setAction(action);
        if(host!=null)intent.putExtra("ip",host);
        if(session!=null)intent.putExtra("session",session);
        if(code!=null)intent.putExtra("code",code);
        if(name!=null)intent.putExtra("name",name);
        if(STOP.equals(action))context.startService(intent);else context.startForegroundService(intent);
    }
}
