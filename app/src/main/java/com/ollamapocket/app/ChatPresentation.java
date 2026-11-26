package com.ollamapocket.app;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

/** Plain Android spans only: no WebView, HTML, remote images or automatic URL execution. */
final class ChatPresentation {
    private ChatPresentation(){}
    static CharSequence format(String prefix,String source,String suffix,int codeBackground){
        ChatMarkup.Result result=ChatMarkup.parse(source);
        SpannableStringBuilder text=new SpannableStringBuilder(prefix);
        int offset=text.length();text.append(result.text).append(suffix);
        // Distinguish the short assistant/provider label from the answer body.
        int labelEnd=prefix.length();
        while(labelEnd>0&&Character.isWhitespace(prefix.charAt(labelEnd-1)))labelEnd--;
        if(labelEnd>0&&labelEnd<=80&&prefix.substring(0,labelEnd).indexOf('\n')<0){
            text.setSpan(new StyleSpan(Typeface.BOLD),0,labelEnd,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new RelativeSizeSpan(0.88f),0,labelEnd,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        for(ChatMarkup.Span s:result.spans){
            int start=offset+s.start,end=offset+s.end;
            if(start>=end)continue;
            switch(s.kind){
                case HEADING:
                    text.setSpan(new RelativeSizeSpan(1.22f),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    text.setSpan(new StyleSpan(Typeface.BOLD),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);break;
                case BOLD:
                    text.setSpan(new StyleSpan(Typeface.BOLD),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);break;
                case ITALIC:
                    text.setSpan(new StyleSpan(Typeface.ITALIC),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);break;
                case CODE: case CODE_BLOCK: case TABLE:
                    text.setSpan(new TypefaceSpan("monospace"),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    text.setSpan(new BackgroundColorSpan(codeBackground),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);break;
            }
        }
        return text;
    }
}
