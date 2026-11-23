package com.ollamapocket.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java counter parsing, independently unit tested. */
public final class PerformanceHudMetrics {
    private PerformanceHudMetrics() {}
    private static final Pattern FIRST_NUMBER=Pattern.compile("(-?\\d+)");

    public static long[] cpuTicks(String line) {
        if(line==null||!line.matches("^cpu\\s+.*"))return null;
        String[] parts=line.trim().split("\\s+");
        if(parts.length<5||!"cpu".equals(parts[0]))return null;
        try {
            long total=0, idle=0;
            for(int i=1;i<parts.length&&i<=8;i++){
                long ticks=Long.parseLong(parts[i]);
                if(ticks<0)return null;
                total+=ticks;
                if(i==4||i==5)idle+=ticks;
            }
            return new long[]{total,idle};
        }catch(NumberFormatException e){return null;}
    }

    public static Double cpuPercent(long[] previous,long[] now) {
        if(previous==null||now==null||previous.length!=2||now.length!=2)return null;
        long total=now[0]-previous[0],idle=now[1]-previous[1];
        if(total<=0||idle<0||idle>total)return null;
        return Math.max(0,Math.min(100,100.0*(total-idle)/total));
    }

    /**
     * App-process CPU time per wall time, normalized by available cores.
     * Not the device-wide or separate native inference subprocess load.
     */
    public static Double processCpuPercent(long usedCpuMs,long wallMs,int cores) {
        if(wallMs<=0 || usedCpuMs<0 || cores<=0)return null;
        double percent=100.0*usedCpuMs/(wallMs*(double)cores);
        return Double.isFinite(percent)?Math.max(0.0,Math.min(100.0,percent)):null;
    }

    /** sysfs GPU provider output; returns null if inaccessible/unknown. */
    public static Double gpuPercent(String line) {
        if(line==null||line.length()>100)return null;
        Matcher matcher=FIRST_NUMBER.matcher(line);
        if(!matcher.find())return null;
        try {
            int value=Integer.parseInt(matcher.group(1));
            return value>=0&&value<=100?(double)value:null;
        }catch(NumberFormatException e){return null;}
    }

    public static String percent(Double n) {
        return n==null?"조회 불가":String.format(java.util.Locale.KOREA,"%.0f%%",n);
    }
}
