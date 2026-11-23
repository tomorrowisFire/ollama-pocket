package com.ollamapocket.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Best-effort local telemetry. Never reports a fictitious GPU load or SoC
 * temperature; batteryC is battery temperature. GPU sysfs access is often
 * blocked on recent Android releases, in which case the value is unavailable.
 */
public final class PerformanceHud {
    private PerformanceHud(){}
    private static long[] previousCpu;
    private static final String[] GPU_LOAD_PATHS={
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
        "/sys/class/devfreq/1c500000.mali/load",
        "/sys/class/devfreq/13000000.mali/load",
        "/sys/class/devfreq/17000000.gpu/load"
    };

    public static final class Sample {
        public final Double cpuPercent, gpuPercent;
        public final long availableMiB,totalMiB;
        public final int batteryTenths, thermalStatus;
        Sample(Double cpu,Double gpu,long available,long total,int battery,int thermal){
            cpuPercent=cpu;gpuPercent=gpu;availableMiB=available;totalMiB=total;
            batteryTenths=battery;thermalStatus=thermal;
        }
        public String compact(){
            return "CPU "+PerformanceHudMetrics.percent(cpuPercent)+
                    "   GPU "+PerformanceHudMetrics.percent(gpuPercent)+
                    "   RAM 여유 "+availableMiB+"MB"+
                    (batteryTenths<0?"":"   배터리 "+String.format(Locale.KOREA,"%.1f℃",batteryTenths/10.0));
        }
        public String detail(){
            String therm=thermalStatus<0?"조회 불가":String.valueOf(thermalStatus);
            return compact()+"\n물리 RAM "+totalMiB+"MB · Android 열 상태 "+therm+
                    "\nCPU: 시스템 전체(/proc/stat); GPU: 제조사 커널 지표(접근 허용 시)"+
                    "\n온도는 배터리 센서이며 CPU/GPU 칩 온도가 아닙니다.";
        }
        public int cpuPermille(){return cpuPercent==null?-1:(int)Math.round(cpuPercent*10);}
        public int gpuPermille(){return gpuPercent==null?-1:(int)Math.round(gpuPercent*10);}
    }

    private static String firstLine(String path) {
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(
                new FileInputStream(path),StandardCharsets.UTF_8))) {
            String line=reader.readLine();
            return line!=null&&line.length()<=200?line:null;
        }catch(Exception e){return null;}
    }

    public static synchronized Sample sample(Context app){
        long[] now=PerformanceHudMetrics.cpuTicks(firstLine("/proc/stat"));
        Double cpu=PerformanceHudMetrics.cpuPercent(previousCpu,now);
        previousCpu=now;
        Double gpu=null;
        for(String path:GPU_LOAD_PATHS){
            gpu=PerformanceHudMetrics.gpuPercent(firstLine(path));
            if(gpu!=null)break;
        }
        ActivityManager manager=(ActivityManager)app.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mem=new ActivityManager.MemoryInfo();
        if(manager!=null) manager.getMemoryInfo(mem);
        Intent battery=app.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int bat=battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1);
        int heat=-1;
        if(Build.VERSION.SDK_INT>=29){
            PowerManager pm=(PowerManager)app.getSystemService(Context.POWER_SERVICE);
            if(pm!=null)heat=pm.getCurrentThermalStatus();
        }
        return new Sample(cpu,gpu,mem.availMem>>20,mem.totalMem>>20,bat,heat);
    }
}
