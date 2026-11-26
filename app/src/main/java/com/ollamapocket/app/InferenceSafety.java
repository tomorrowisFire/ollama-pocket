package com.ollamapocket.app;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

final class InferenceSafety {
    private InferenceSafety(){}
    static InferenceSafetyPolicy.Reason check(Context context){
        int thermal=-1;
        boolean low=false;
        try{
            ActivityManager am=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            if(am!=null){
                ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();
                am.getMemoryInfo(memory);
                low=memory.lowMemory;
            }
        }catch(RuntimeException ignored){}
        if(Build.VERSION.SDK_INT>=29)try{
            PowerManager pm=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
            if(pm!=null)thermal=pm.getCurrentThermalStatus();
        }catch(RuntimeException ignored){}
        return InferenceSafetyPolicy.evaluate(thermal,low);
    }
    static boolean blocked(Context c){return check(c)!=InferenceSafetyPolicy.Reason.NONE;}
    static int label(InferenceSafetyPolicy.Reason reason){
        return reason==InferenceSafetyPolicy.Reason.THERMAL?R.string.bench_hot:
               reason==InferenceSafetyPolicy.Reason.LOW_MEMORY?R.string.bench_memory:R.string.session_no_nodes;
    }
}
