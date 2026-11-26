package com.ollamapocket.app;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.Locale;

/** Presentation only. No network activity, session mutations or protocol changes. */
final class CoopDiagnosticPresentation {
    private CoopDiagnosticPresentation(){}
    private static Locale locale(Context c){return c.getResources().getConfiguration().getLocales().get(0);}
    private static String unavailable(Context c){return c.getString(R.string.metric_unavailable);}
    private static String display(Context c,String value){return DiagnosticValues.unknownText(value,c.getString(R.string.unknown));}
    private static String connection(Context c,boolean ok){return c.getString(ok?R.string.node_online:R.string.node_check);}
    static String host(Context c,CoopSession.Node n,boolean includeIp){
        String unknown=unavailable(c);Locale l=locale(c);
        long age=Math.max(0,(SystemClock.elapsedRealtime()-n.lastSeen)/1000);
        String identity=n.name+(includeIp?" ("+n.ip+")":"");
        return c.getString(R.string.node_identity,identity,connection(c,age<30&&n.workerOk))+"\n"+
            c.getString(R.string.node_device,display(c,n.deviceModel),DiagnosticValues.positive(n.androidApi,unknown))+"\n"+
            c.getString(R.string.node_chip,display(c,n.chip),DiagnosticValues.positive(n.cpuCores,unknown),display(c,n.abi))+"\n"+
            c.getString(R.string.node_ram,DiagnosticValues.positive(n.totalRamMiB,unknown),DiagnosticValues.nonnegative(n.safeRamMiB,unknown))+"\n"+
            c.getString(R.string.node_storage,DiagnosticValues.nonnegative(n.freeStorageMiB,unknown))+"\n"+
            c.getString(R.string.node_usage,DiagnosticValues.percentage(n.cpuPermille,l,unknown),DiagnosticValues.percentage(n.gpuPermille,l,unknown))+"\n"+
            c.getString(R.string.node_accelerators,c.getString(n.vulkanCompute?R.string.node_detected:R.string.node_unverified),Integer.toString(n.nnapiAccelerators))+"\n"+
            c.getString(R.string.node_thermal,DiagnosticValues.battery(n.batteryTenths,l,unknown),DiagnosticValues.nonnegative(n.thermalStatus,unknown),c.getString(n.workerOk?R.string.status_running:R.string.status_stopped))+"\n"+
            c.getString(R.string.node_last_seen,age);
    }
    static String peer(Context c,JSONObject n){
        JSONObject info=n.optJSONObject("info");if(info==null)info=new JSONObject();
        Locale l=locale(c);String unknown=unavailable(c);
        long age=n.optLong("age",-1);
        boolean worker=n.optBoolean("worker",false);
        String identity=display(c,n.optString("name",""))+" ("+display(c,n.optString("ip",""))+")";
        StringBuilder out=new StringBuilder(c.getString(R.string.node_identity,identity,connection(c,worker&&age>=0&&age<30))).append('\n')
            .append(c.getString(R.string.node_device,display(c,info.optString("model","")),DiagnosticValues.positive(info.optInt("api",0),unknown))).append('\n')
            .append(c.getString(R.string.node_chip,display(c,info.optString("chip","")),DiagnosticValues.positive(info.optInt("cores",0),unknown),display(c,info.optString("abi","")))).append('\n')
            .append(c.getString(R.string.node_ram,DiagnosticValues.positive(info.optLong("ram_total",0),unknown),DiagnosticValues.nonnegative(n.optLong("ram_safe",-1),unknown))).append('\n')
            .append(c.getString(R.string.node_storage,DiagnosticValues.nonnegative(info.optLong("storage_free",-1),unknown))).append('\n')
            .append(c.getString(R.string.node_usage,DiagnosticValues.percentage(n.optInt("cpu",-1),l,unknown),DiagnosticValues.percentage(n.optInt("gpu",-1),l,unknown))).append('\n')
            .append(c.getString(R.string.node_accelerators,c.getString(info.optBoolean("vulkan",false)?R.string.node_detected:R.string.node_unverified),DiagnosticValues.nonnegative(info.optInt("npu",-1),unknown))).append('\n')
            .append(c.getString(R.string.node_peer_battery,DiagnosticValues.battery(n.optInt("battery",-1),l,unknown))).append('\n')
            .append(c.getString(R.string.coop_worker_status,c.getString(worker?R.string.status_running:R.string.status_stopped)));
        if(age>=0)out.append('\n').append(c.getString(R.string.node_last_seen,age));
        return out.toString();
    }
}
