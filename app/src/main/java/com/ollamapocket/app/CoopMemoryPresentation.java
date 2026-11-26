package com.ollamapocket.app;
import android.content.Context;
/** Locale-aware display of unchanged conservative RAM decisions. */
final class CoopMemoryPresentation {
    private CoopMemoryPresentation(){}
    static String summary(Context c,CoopMemoryPlanner.Plan p){
        String reason;
        switch(p.reasonCode){
            case UNKNOWN_MODEL:reason=c.getString(R.string.rpc_plan_unknown);break;
            case NO_REMOTE:reason=c.getString(R.string.rpc_plan_no_remote);break;
            case HOST_LOW:reason=c.getString(R.string.rpc_plan_host_low);break;
            case SHORTFALL:reason=c.getString(R.string.rpc_plan_shortfall,p.requiredMiB-p.totalSafeMiB);break;
            case PASS:default:reason=c.getString(R.string.rpc_plan_pass);break;
        }
        return c.getString(R.string.rpc_plan_numbers,p.modelMiB,p.overheadMiB,p.requiredMiB,p.totalSafeMiB,p.hostSafeMiB,p.selectedIndices.size())+"\n"+reason+"\n"+c.getString(R.string.rpc_plan_limit);
    }
}
