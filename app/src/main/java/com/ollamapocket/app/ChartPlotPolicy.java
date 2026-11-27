package com.ollamapocket.app;

/** Native chart geometry; include zero, use the full signed domain and normalize before subtraction. */
final class ChartPlotPolicy {
    private ChartPlotPolicy(){}
    static final class Axis {
        private final double scale,low,high;
        Axis(double minimum,double maximum){
            if(!Double.isFinite(minimum)||!Double.isFinite(maximum)||minimum>maximum)
                throw new IllegalArgumentException("Chart domain");
            double magnitude=Math.max(Math.abs(minimum),Math.abs(maximum));
            scale=magnitude==0?1:magnitude;
            low=magnitude==0?-1:Math.min(0,minimum)/scale;
            high=magnitude==0?1:Math.max(0,maximum)/scale;
        }
        /** Fraction measured down from the top. Out-of-domain values clamp to the plot. */
        double y(double value){
            if(!Double.isFinite(value))throw new IllegalArgumentException("Chart value");
            double normalized=Math.max(low,Math.min(high,value/scale));
            return (high-normalized)/(high-low);
        }
    }
}
