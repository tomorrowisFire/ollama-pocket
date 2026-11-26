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
        if(onClick!=null)b.setOnClickListener(v->onClick.run());
        return b;
    }
    private static void section(Context c,LinearLayout root,String title,int button,String original,View content,int ink,int surface,float scale){
        section(c,root,title,button,original,content,ink,surface,scale,null);
    }
    private static void section(Context c,LinearLayout root,String title,int button,String original,View content,int ink,int surface,float scale,Button extra){
        LinearLayout card=column(c);
        GradientDrawable frame=new GradientDrawable();frame.setColor(surface);frame.setCornerRadius(dp(c,12));
        card.setBackground(frame);card.setPadding(dp(c,4),dp(c,2),dp(c,4),dp(c,4));
        AdaptiveAnswerActions header=new AdaptiveAnswerActions(c);
        TextView heading=label(c,title,ink,12*scale,false);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        heading.setTextIsSelectable(false);
        header.addView(heading,new LinearLayout.LayoutParams(-2,-2));
        header.addView(action(c,button,scale,()->copy(c,original)),new LinearLayout.LayoutParams(-2,-2));
        if(extra!=null)header.addView(extra,new LinearLayout.LayoutParams(-2,-2));
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
        for(ChatBlocks.Block b:blocks){
            if(b.kind==ChatBlocks.Kind.TEXT){
                root.addView(label(c,ChatPresentation.format("",b.body,"",surface),ink,15*scale,false),new LinearLayout.LayoutParams(-1,-2));
            }else if(b.kind==ChatBlocks.Kind.CODE){
                TextView code=label(c,b.body,ink,14*scale,true);
                code.setHorizontallyScrolling(true);
                LinearLayout holder=column(c);
                HorizontalScrollView scrolling=horizontal(c,code);
                holder.addView(scrolling,new LinearLayout.LayoutParams(-1,-2));
                Button wrap=action(c,R.string.chat_wrap_code,scale,null);
                final boolean[] wrapped={false};
                wrap.setOnClickListener(v->{
                    if(!wrapped[0]){
                        scrolling.removeView(code);holder.removeView(scrolling);
                        code.setHorizontallyScrolling(false);
                        holder.addView(code,new LinearLayout.LayoutParams(-1,-2));
                    }else{
                        holder.removeView(code);code.setHorizontallyScrolling(true);
                        scrolling.addView(code,new android.widget.FrameLayout.LayoutParams(-2,-2));
                        holder.addView(scrolling,new LinearLayout.LayoutParams(-1,-2));
                        scrolling.scrollTo(0,0);
                    }
                    wrapped[0]=!wrapped[0];
                    wrap.setText(wrapped[0]?R.string.chat_unwrap_code:R.string.chat_wrap_code);
                });
                section(c,root,c.getString(R.string.chat_code_block)+(b.language.isEmpty()?"":" · "+b.language),
                    R.string.chat_copy_code,b.body,holder,ink,surface,scale,wrap);
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
