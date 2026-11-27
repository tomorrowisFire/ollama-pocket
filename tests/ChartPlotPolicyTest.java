package com.ollamapocket.app;

public final class ChartPlotPolicyTest {
    private static int n;
    private static void check(boolean ok){n++;if(!ok)throw new AssertionError("Chart plot "+n);}
    private static void near(double a,double b){check(Math.abs(a-b)<1e-12);}
    private static void reject(Runnable r){try{r.run();throw new AssertionError("Invalid domain accepted");}catch(IllegalArgumentException expected){n++;}}
    public static void main(String[] args){
        ChartPlotPolicy.Axis positive=new ChartPlotPolicy.Axis(4,10);
        near(positive.y(0),1);near(positive.y(10),0);near(positive.y(5),.5);
        ChartPlotPolicy.Axis negative=new ChartPlotPolicy.Axis(-10,-4);
        near(negative.y(0),0);near(negative.y(-10),1);near(negative.y(-5),.5);
        ChartPlotPolicy.Axis mixed=new ChartPlotPolicy.Axis(-2,8);
        near(mixed.y(0),.8);near(mixed.y(-2),1);near(mixed.y(8),0);
        near(new ChartPlotPolicy.Axis(0,0).y(0),.5);
        near(new ChartPlotPolicy.Axis(Double.MIN_VALUE,Double.MIN_VALUE).y(Double.MIN_VALUE),0);
        near(new ChartPlotPolicy.Axis(-Double.MIN_VALUE,Double.MIN_VALUE).y(0),.5);
        reject(()->new ChartPlotPolicy.Axis(1,-1));reject(()->new ChartPlotPolicy.Axis(Double.NaN,1));
        reject(()->new ChartPlotPolicy.Axis(0,Double.POSITIVE_INFINITY));reject(()->mixed.y(Double.NaN));
        double[] samples={-Double.MAX_VALUE,-1e9,-5,-1,-Double.MIN_NORMAL,-Double.MIN_VALUE,0,Double.MIN_VALUE,Double.MIN_NORMAL,1,5,1e9,Double.MAX_VALUE};
        for(int i=0;i<samples.length;i++)for(int j=i;j<samples.length;j++){
            ChartPlotPolicy.Axis axis=new ChartPlotPolicy.Axis(samples[i],samples[j]);
            double previous=1;
            for(double value:samples){
                double y=axis.y(value);check(Double.isFinite(y)&&y>=0&&y<=1);check(y<=previous);previous=y;
            }
            if(samples[j]>0)near(axis.y(samples[j]),0);
            if(samples[i]<0)near(axis.y(samples[i]),1);
        }
        System.out.println("PASS: "+n+" signed chart geometry assertions");
    }
}
