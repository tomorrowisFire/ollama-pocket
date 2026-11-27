package com.ollamapocket.app;

import android.content.Context;
import android.content.ClipboardManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Native chart preview. Never loads artifact HTML in a WebView or contacts the network. */
final class ChartArtifactView {
    private ChartArtifactView(){}
    private static int dp(Context c,int value){return Math.round(c.getResources().getDisplayMetrics().density*value);}
    static View create(Context c,ChartArtifact chart,int ink,float scale){
        LinearLayout panel=new LinearLayout(c);panel.setOrientation(LinearLayout.VERTICAL);
        TextView title=new TextView(c);title.setText(chart.title);title.setTextColor(ink);title.setTextSize(16*scale);
        title.setTextIsSelectable(true);title.setAutoLinkMask(0);title.setSaveEnabled(false);
        if(android.os.Build.VERSION.SDK_INT>=28)title.setAccessibilityHeading(true);
        panel.addView(title,new LinearLayout.LayoutParams(-1,-2));
        WorkbenchVisualPolicy.Palette palette=WorkbenchStyle.palette(c);
        Plot plot=new Plot(c,chart,palette.primary,palette.muted);
        android.graphics.drawable.GradientDrawable frame=new android.graphics.drawable.GradientDrawable();
        frame.setColor(palette.surface);frame.setCornerRadius(dp(c,WorkbenchVisualPolicy.RADIUS_DP));
        frame.setStroke(dp(c,1),palette.border);plot.setBackground(frame);
        plot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        panel.addView(plot,new LinearLayout.LayoutParams(-1,dp(c,180)));
        StringBuilder values=new StringBuilder();
        for(ChartArtifact.Point point:chart.points){
            if(values.length()>0)values.append('\n');
            values.append(point.label).append(": ").append(Double.toString(point.value));
        }
        TextView table=new TextView(c);table.setText(values);table.setTextColor(ink);table.setTextSize(14*scale);
        table.setAutoLinkMask(0);table.setTextIsSelectable(true);table.setSaveEnabled(false);
        panel.addView(table,new LinearLayout.LayoutParams(-1,-2));
        AdaptiveAnswerActions actions=new AdaptiveAnswerActions(c);
        addCopy(c,actions,R.string.integration_copy_csv,scale,chart::csv);
        addCopy(c,actions,R.string.integration_copy_html,scale,chart::html);
        panel.addView(actions,new LinearLayout.LayoutParams(-1,-2));
        return panel;
    }
    private static void addCopy(Context c,LinearLayout row,int label,float scale,java.util.function.Supplier<String> export){
        Button b=new Button(c);b.setText(label);b.setAllCaps(false);b.setSingleLine(false);b.setMinHeight(dp(c,48));b.setMinWidth(dp(c,48));
        if(c instanceof android.app.Activity)WorkbenchStyle.button((android.app.Activity)c,b,label);
        b.setTextSize(13*scale);b.setEllipsize(null);
        b.setOnClickListener(v->{
            try{
                ClipboardManager clipboard=(ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);
                if(clipboard==null)throw new IllegalStateException();
                clipboard.setPrimaryClip(SensitiveClipboard.text(c.getString(label),export.get()));
                Toast.makeText(c,R.string.copied,Toast.LENGTH_SHORT).show();
            }catch(RuntimeException e){Toast.makeText(c,R.string.chat_block_copy_failed,Toast.LENGTH_SHORT).show();}
        });
        row.addView(b,new LinearLayout.LayoutParams(-2,-2));
    }
    private static final class Plot extends View{
        final ChartArtifact chart;final int ink,axisInk;final ChartPlotPolicy.Axis axis;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Plot(Context c,ChartArtifact chart,int ink,int axisInk){
            super(c);this.chart=chart;this.ink=ink;this.axisInk=axisInk;
            double minimum=0,maximum=0;
            for(ChartArtifact.Point point:chart.points){minimum=Math.min(minimum,point.value);maximum=Math.max(maximum,point.value);}
            axis=new ChartPlotPolicy.Axis(minimum,maximum);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float pad=dp(getContext(),12),w=getWidth()-2*pad,h=getHeight()-2*pad;
            float inset=dp(getContext(),4),span=h-2*inset;
            if(w<=0||span<=0)return;
            float top=pad+inset,zero=top+(float)axis.y(0)*span;
            paint.setColor(axisInk);paint.setStrokeWidth(dp(getContext(),1));
            canvas.drawLine(pad,zero,pad+w,zero,paint);
            paint.setColor(ink);paint.setStrokeWidth(dp(getContext(),2));
            float slot=w/chart.points.size(),oldX=0,oldY=0;
            for(int i=0;i<chart.points.size();i++){
                float x=pad+slot*(i+.5f),y=top+(float)axis.y(chart.points.get(i).value)*span;
                if(chart.kind==ChartArtifact.Kind.LINE){
                    if(i>0)canvas.drawLine(oldX,oldY,x,y,paint);
                    canvas.drawCircle(x,y,dp(getContext(),3),paint);
                }else{
                    float half=Math.max(.5f,slot*.32f);
                    if(Math.abs(y-zero)<1)canvas.drawLine(x-half,zero,x+half,zero,paint);
                    else canvas.drawRect(x-half,Math.min(zero,y),x+half,Math.max(zero,y),paint);
                }
                oldX=x;oldY=y;
            }
        }
    }
}
