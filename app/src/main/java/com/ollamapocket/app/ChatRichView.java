package com.ollamapocket.app;

import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** Native views only. No HTML, JavaScript, network, links or executable code. */
final class ChatRichView {
    private ChatRichView(){}
    private static int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    private static LinearLayout column(Context c){
        LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;
    }
    private static TextView label(Context c,CharSequence value,int ink,float sp,boolean mono){
        TextView t=new TextView(c);t.setText(value);t.setTextColor(ink);t.setTextSize(sp);
        t.setTextIsSelectable(true);t.setAutoLinkMask(0);t.setLineSpacing(dp(c,3),1f);
        if(mono)t.setTypeface(Typeface.MONOSPACE);
        int pad=dp(c,9);t.setPadding(pad,pad,pad,pad);
        return t;
    }
    private static void copy(Context c,String value){copy(c,value,R.string.copied);}
    private static void copy(Context c,String value,int notice){
        ClipboardManager cm=(ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);
        if(cm!=null){
            try{
                cm.setPrimaryClip(SensitiveClipboard.text(c.getString(R.string.app_name),value));
                Toast.makeText(c,notice,Toast.LENGTH_SHORT).show();
            }catch(RuntimeException e){Toast.makeText(c,R.string.chat_block_copy_failed,Toast.LENGTH_SHORT).show();}
        }else Toast.makeText(c,R.string.chat_block_copy_failed,Toast.LENGTH_SHORT).show();
    }
    private static Button action(Context c,int title,float scale,Runnable onClick){
        Button b=new Button(c);b.setText(title);b.setAllCaps(false);b.setMinHeight(dp(c,48));
        b.setMinimumHeight(dp(c,48));b.setTextSize(13*scale);b.setSingleLine(false);b.setEllipsize(null);
        if(c instanceof android.app.Activity)WorkbenchStyle.button((android.app.Activity)c,b,title);
        if(onClick!=null)b.setOnClickListener(v->onClick.run());
        return b;
    }
    private static void section(Context c,LinearLayout root,String title,int button,String original,View content,int ink,int surface,float scale){
        section(c,root,title,button,original,content,ink,surface,scale,new Button[0]);
    }
    private static void section(Context c,LinearLayout root,String title,int button,String original,View content,int ink,int surface,float scale,Button... extras){
        LinearLayout card=column(c);
        GradientDrawable frame=new GradientDrawable();frame.setColor(surface);frame.setCornerRadius(dp(c,16));
        frame.setStroke(dp(c,1),WorkbenchStyle.palette(c).border);
        card.setBackground(frame);card.setPadding(dp(c,4),dp(c,2),dp(c,4),dp(c,4));
        AdaptiveAnswerActions header=new AdaptiveAnswerActions(c);
        TextView heading=label(c,title,ink,12*scale,false);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        heading.setTextIsSelectable(false);
        OnHandGlyphs.accentLabel(heading,button==R.string.chat_copy_code?OnHandGlyphs.Kind.CODE:
            button==R.string.chat_copy_table?OnHandGlyphs.Kind.PLAN:OnHandGlyphs.Kind.DOC);
        header.addView(heading,new LinearLayout.LayoutParams(-2,-2));
        header.addView(action(c,button,scale,()->copy(c,original)),new LinearLayout.LayoutParams(-2,-2));
        for(Button extra:extras)if(extra!=null)header.addView(extra,new LinearLayout.LayoutParams(-2,-2));
        card.addView(header,new LinearLayout.LayoutParams(-1,-2));
        card.addView(content,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(c,8);
        root.addView(card,p);
    }
    private static HorizontalScrollView horizontal(Context c,View child){
        HorizontalScrollView h=new HorizontalScrollView(c);
        h.setFillViewport(false);h.setHorizontalScrollBarEnabled(true);
        h.addView(child,new android.widget.FrameLayout.LayoutParams(-2,-2));return h;
    }
    static View create(Context c,String prefix,String source,String suffix,int ink,int surface,float scale){
        List<ChatBlocks.Block> blocks=ChatBlocks.parse(source);
        if(!ChatBlocks.hasSpecial(blocks))return null;
        LinearLayout root=column(c);
        if(!prefix.isEmpty()){
            TextView name=label(c,prefix,ink,13*scale,false);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            name.setTextIsSelectable(false);root.addView(name,new LinearLayout.LayoutParams(-1,-2));
        }
        int renderedCharts=0;
        for(ChatBlocks.Block b:blocks){
            if(b.kind==ChatBlocks.Kind.TEXT){
                root.addView(label(c,ChatPresentation.format("",b.body,"",surface),ink,15*scale,false),new LinearLayout.LayoutParams(-1,-2));
            }else if(b.kind==ChatBlocks.Kind.CODE){
                ChartArtifact chart=renderedCharts<2?ChartArtifactParser.fromFence(b.language,b.body):null;
                if(chart!=null){
                    renderedCharts++;
                    section(c,root,c.getString(R.string.integration_chart),R.string.chat_copy_code,b.body,
                        ChartArtifactView.create(c,chart,ink,scale),ink,surface,scale);
                    continue;
                }
                TextView code=label(c,b.body,ink,14*scale,true);
                code.setHorizontallyScrolling(true);
                LinearLayout holder=column(c),lineRow=new LinearLayout(c);
                lineRow.setOrientation(LinearLayout.HORIZONTAL);
                String digits=ChatBlocks.codeLineGutter(b.body);
                TextView gutter=label(c,digits,ink,14*scale,true);
                gutter.setTextIsSelectable(false);gutter.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                gutter.setGravity(android.view.Gravity.END);gutter.setVisibility(View.GONE);
                gutter.setHorizontallyScrolling(true);
                lineRow.addView(gutter,new LinearLayout.LayoutParams(-2,-2));
                lineRow.addView(code,new LinearLayout.LayoutParams(-2,-2));
                HorizontalScrollView scrolling=horizontal(c,lineRow);
                holder.addView(scrolling,new LinearLayout.LayoutParams(-1,-2));
                Button wrap=action(c,R.string.chat_wrap_code,scale,null);
                Button numbers=digits.isEmpty()?null:action(c,R.string.chat_show_line_numbers,scale,null);
                final boolean[] wrapped={false};
                if(numbers!=null)numbers.setOnClickListener(v->{
                    if(wrapped[0])return;
                    boolean show=gutter.getVisibility()!=View.VISIBLE;
                    gutter.setVisibility(show?View.VISIBLE:View.GONE);
                    numbers.setText(show?R.string.chat_hide_line_numbers:R.string.chat_show_line_numbers);
                });
                wrap.setOnClickListener(v->{
                    if(!wrapped[0]){
                        lineRow.removeView(code);holder.removeView(scrolling);
                        code.setHorizontallyScrolling(false);
                        holder.addView(code,new LinearLayout.LayoutParams(-1,-2));
                    }else{
                        holder.removeView(code);code.setHorizontallyScrolling(true);
                        lineRow.addView(code,new LinearLayout.LayoutParams(-2,-2));
                        holder.addView(scrolling,new LinearLayout.LayoutParams(-1,-2));
                        scrolling.scrollTo(0,0);
                    }
                    wrapped[0]=!wrapped[0];
                    if(numbers!=null)numbers.setEnabled(!wrapped[0]);
                    wrap.setText(wrapped[0]?R.string.chat_unwrap_code:R.string.chat_wrap_code);
                });
                section(c,root,c.getString(R.string.chat_code_block)+(b.language.isEmpty()?"":" · "+b.language)+
                        " · "+c.getString(R.string.chat_code_line_count,ChatBlocks.codeLineCount(b.body)),
                    R.string.chat_copy_code,b.body,holder,ink,surface,scale,wrap,numbers);
            }else if(b.kind==ChatBlocks.Kind.TABLE){
                TableLayout table=new TableLayout(c);
                int count=b.rows.get(0).size();int[] widths=new int[count];
                for(int j=0;j<count;j++){
                    int len=0;for(List<String> row:b.rows)len=Math.max(len,row.get(j).length());
                    widths[j]=dp(c,Math.min(240,Math.max(112,72+Math.min(21,len)*8)));
                }
                for(int i=0;i<b.rows.size();i++){
                    TableRow row=new TableRow(c);
                    for(int j=0;j<count;j++){
                        TextView cell=label(c,ChatPresentation.format("",b.rows.get(i).get(j),"",surface),ink,14*scale,false);
                        if(i==0)cell.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                        if(i%2==0)cell.setBackgroundColor(0x14808080);
                        TableRow.LayoutParams params=new TableRow.LayoutParams(widths[j],ViewGroup.LayoutParams.WRAP_CONTENT);
                        params.setMargins(0,0,dp(c,1),dp(c,1));row.addView(cell,params);
                    }
                    table.addView(row,new TableLayout.LayoutParams(-2,-2));
                }
                Button spreadsheet=action(c,R.string.chat_copy_spreadsheet,scale,()->{
                    try{copy(c,ChatBlocks.spreadsheetTsv(b),R.string.chat_spreadsheet_copied);}
                    catch(IllegalArgumentException e){Toast.makeText(c,R.string.chat_spreadsheet_limit,Toast.LENGTH_SHORT).show();}
                });
                section(c,root,c.getString(R.string.chat_table_block),R.string.chat_copy_table,b.raw,
                    horizontal(c,table),ink,surface,scale,spreadsheet);
            }else{
                String formula=SimpleFormula.display(b.body);
                boolean converted=!b.body.equals(formula);
                section(c,root,c.getString(converted?R.string.chat_math_block:R.string.chat_math_raw),
                    R.string.chat_copy_math,b.raw,label(c,formula,ink,15*scale,true),ink,surface,scale);
            }
        }
        if(!suffix.isEmpty())root.addView(label(c,suffix,ink,13*scale,false),new LinearLayout.LayoutParams(-1,-2));
        return root;
    }
}
