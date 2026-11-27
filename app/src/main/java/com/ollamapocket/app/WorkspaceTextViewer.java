package com.ollamapocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Foreground-only exact snapshot navigation. No persistence, inference or implicit transfer. */
final class WorkspaceTextViewer {
    private final Activity owner;
    private Review active;
    WorkspaceTextViewer(Activity owner){this.owner=owner;}
    private int dp(int n){return Math.round(n*owner.getResources().getDisplayMetrics().density);}
    private void toast(int res){if(!owner.isFinishing()&&!owner.isDestroyed())Toast.makeText(owner,res,Toast.LENGTH_SHORT).show();}
    private boolean allowed(View anchor,BooleanSupplier guard){
        try{return !owner.isFinishing()&&!owner.isDestroyed()&&anchor.isAttachedToWindow()&&anchor.isEnabled()&&guard.getAsBoolean();}
        catch(RuntimeException e){return false;}
    }
    Button add(LinearLayout panel,int subject,Supplier<String> source,BooleanSupplier guard){
        return add(panel,subject,source,guard,null,0,0);
    }
    Button addMatch(LinearLayout panel,int subject,Supplier<String> source,BooleanSupplier guard,String literal,int start,int end){
        if(literal==null)throw new IllegalArgumentException("Missing match");
        return add(panel,subject,source,guard,literal,start,end);
    }
    private Button add(LinearLayout panel,int subject,Supplier<String> source,BooleanSupplier guard,String literal,int start,int end){
        Button b=new Button(owner);
        b.setText(literal==null?owner.getString(R.string.workspace_text_open,owner.getString(subject)):owner.getString(R.string.knowledge_inspect_match));
        WorkbenchStyle.button(owner,b,WorkbenchVisualPolicy.Role.SECONDARY);panel.addView(b,WorkbenchStyle.row(owner));
        b.setOnClickListener(v->{
            if(!allowed(b,guard)||!b.hasWindowFocus()){toast(R.string.busy);return;}
            final WorkspaceTextPolicy.Snapshot snapshot;
            final WorkspaceTextPolicy.Window initial;
            try{
                snapshot=WorkspaceTextPolicy.review(source.get());
                initial=literal==null?snapshot.first():snapshot.atMatch(literal,start,end);
            }catch(RuntimeException e){toast(R.string.workspace_text_invalid);return;}
            cancel();Review next=new Review(b,subject,source,guard,snapshot,initial,literal);active=next;next.show();
        });
        return b;
    }
    void cancel(){Review previous=active;if(previous!=null)previous.dispose();}
    private void secure(AlertDialog d){if(d.getWindow()!=null)d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    private final class Review {
        final View anchor;
        final int subject;
        final Supplier<String> source;
        final BooleanSupplier guard;
        final WorkspaceTextPolicy.Snapshot snapshot;
        WorkspaceTextPolicy.Window current,lastMatch;
        String lastQuery="";
        AlertDialog dialog,confirmation;
        ScrollView scroll;
        EditText query;
        TextView range,status,preview;
        Button previous,next;
        boolean closed;
        final View.OnAttachStateChangeListener detach=new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){}
            public void onViewDetachedFromWindow(View v){dispose();}
        };
        Review(View anchor,int subject,Supplier<String> source,BooleanSupplier guard,WorkspaceTextPolicy.Snapshot snapshot,
               WorkspaceTextPolicy.Window initial,String literal){
            this.anchor=anchor;this.subject=subject;this.source=source;this.guard=guard;this.snapshot=snapshot;
            current=initial;
            if(initial.hasMatch()){lastMatch=initial;lastQuery=literal;}
        }
        boolean alive(){
            try{return !closed&&active==this&&allowed(anchor,guard)&&snapshot.matches(source.get())&&dialog!=null&&dialog.isShowing();}
            catch(RuntimeException e){return false;}
        }
        boolean interactive(){
            return alive()&&confirmation==null&&dialog.getWindow()!=null&&dialog.getWindow().getDecorView().hasWindowFocus();
        }
        TextView text(LinearLayout panel,int sp,boolean heading){
            TextView t=new TextView(owner);t.setSaveEnabled(false);t.setAutoLinkMask(0);
            WorkbenchStyle.label(owner,t,sp,false,heading);panel.addView(t,WorkbenchStyle.row(owner));return t;
        }
        Button button(LinearLayout panel,int label,Runnable action){
            Button b=new Button(owner);b.setText(label);WorkbenchStyle.button(owner,b,label);
            b.setOnClickListener(v->{if(interactive())action.run();else if(!alive()){toast(R.string.workspace_text_changed);dispose();}});
            panel.addView(b,new LinearLayout.LayoutParams(-2,-2));return b;
        }
        LinearLayout actions(LinearLayout panel){
            LinearLayout row=new AdaptiveAnswerActions(owner);panel.addView(row,WorkbenchStyle.row(owner));return row;
        }
        void show(){
            LinearLayout body=new LinearLayout(owner);body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(16),dp(12),dp(16),dp(12));
            text(body,14,false).setText(owner.getString(R.string.workspace_text_notice,owner.getString(subject),snapshot.length()));
            query=new EditText(owner);query.setSingleLine(true);query.setSaveEnabled(false);
            query.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            query.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH|android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
            query.setFilters(new android.text.InputFilter[]{(value,a,b,dest,c,d)->{
                if((long)dest.length()-(d-c)+(b-a)<=WorkspaceTextPolicy.MAX_QUERY)return null;
                return dest.subSequence(c,d);
            }});
            query.setHint(R.string.workspace_text_query);WorkbenchStyle.field(owner,query);
            if(lastMatch!=null){query.setText(lastQuery);query.setSelection(query.length());}
            WorkbenchStyle.fieldLabel(owner,body,R.string.workspace_text_query,query);body.addView(query,WorkbenchStyle.row(owner));
            LinearLayout find=actions(body);
            button(find,R.string.workspace_text_find_previous,()->search(false));
            button(find,R.string.workspace_text_find_next,()->search(true));
            query.setOnEditorActionListener((v,action,event)->{
                if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){if(interactive())search(true);return true;}return false;
            });
            range=text(body,14,true);status=text(body,13,false);
            status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            LinearLayout pages=actions(body);
            previous=button(pages,R.string.workspace_text_previous,()->move(false));
            next=button(pages,R.string.workspace_text_next,()->move(true));
            LinearLayout copies=actions(body);
            button(copies,R.string.workspace_text_copy_excerpt,()->copy(false));
            button(copies,R.string.workspace_text_copy_full,()->copy(true));
            preview=text(body,14,false);preview.setTextIsSelectable(false);
            scroll=new ScrollView(owner);scroll.setSaveEnabled(false);scroll.addView(body);
            dialog=new AlertDialog.Builder(owner).setTitle(R.string.workspace_text_title).setView(scroll)
                .setNegativeButton(R.string.close,null).create();
            dialog.setOnDismissListener(d->dispose());anchor.addOnAttachStateChangeListener(detach);
            try{secure(dialog);dialog.show();secure(dialog);WorkbenchWindow.fit(owner,dialog);render();}
            catch(RuntimeException e){dispose();toast(R.string.workspace_text_invalid);}
        }
        void render(){
            String visible=current.text();
            range.setText(owner.getString(R.string.workspace_text_range,current.start+1,current.end,snapshot.length()));
            if(current.hasMatch()){
                SpannableString marked=new SpannableString(visible);
                int a=current.matchStart-current.start,b=current.matchEnd-current.start;
                marked.setSpan(new StyleSpan(Typeface.BOLD),a,b,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                marked.setSpan(new UnderlineSpan(),a,b,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                preview.setText(marked);
                status.setText(owner.getString(R.string.workspace_text_match,current.matchStart+1,current.matchEnd));
            }else{preview.setText(visible);status.setText("");}
            previous.setEnabled(current.start>0);next.setEnabled(current.end<snapshot.length());
        }
        void move(boolean forward){
            WorkspaceTextPolicy.Window window=forward?snapshot.next(current):snapshot.previous(current);
            if(window==null)return;
            current=window;lastMatch=null;lastQuery="";render();
        }
        void search(boolean forward){
            String value=query.getText().toString();
            try{
                WorkspaceTextPolicy.validateQuery(value);
                if(!value.equals(lastQuery)){lastMatch=null;lastQuery=value;}
                WorkspaceTextPolicy.Window found=snapshot.find(value,lastMatch,forward);
                if(found==null){status.setText(R.string.workspace_text_no_match);return;}
                current=found;lastMatch=found;render();
            }catch(IllegalArgumentException e){status.setText(R.string.workspace_text_bad_query);}
        }
        void copy(boolean full){
            final WorkspaceTextPolicy.Window selected=current;
            final String value;
            try{value=snapshot.copy(selected,full,source.get());}
            catch(RuntimeException e){toast(R.string.workspace_text_changed);dispose();return;}
            String span=full?owner.getString(R.string.workspace_text_range,1,snapshot.length(),snapshot.length()):
                owner.getString(R.string.workspace_text_range,selected.start+1,selected.end,snapshot.length());
            AlertDialog d=new AlertDialog.Builder(owner)
                .setTitle(full?R.string.workspace_text_copy_full:R.string.workspace_text_copy_excerpt)
                .setMessage(owner.getString(full?R.string.workspace_text_full_notice:R.string.workspace_text_excerpt_notice,
                    owner.getString(subject),span)+"\n\n"+owner.getString(R.string.attachment_copy_notice))
                .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.ok,null).create();
            confirmation=d;d.setOnDismissListener(x->{if(confirmation==d)confirmation=null;});
            try{
                secure(d);d.show();secure(d);
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                    if(!alive()||confirmation!=d||!d.isShowing()||d.getWindow()==null||!d.getWindow().getDecorView().hasWindowFocus()||current!=selected){toast(R.string.workspace_text_changed);dispose();return;}
                    try{
                        if(!value.equals(snapshot.copy(selected,full,source.get())))throw new IllegalArgumentException("Changed text");
                        ClipboardManager cb=(ClipboardManager)owner.getSystemService(Context.CLIPBOARD_SERVICE);
                        if(cb==null)throw new IllegalStateException("Clipboard unavailable");
                        cb.setPrimaryClip(SensitiveClipboard.text(owner.getString(subject),value));toast(R.string.copied);d.dismiss();
                    }catch(RuntimeException e){toast(R.string.batch_export_failed);d.dismiss();}
                });
            }catch(RuntimeException e){if(confirmation==d)confirmation=null;d.dismiss();toast(R.string.batch_export_failed);}
        }
        void dispose(){
            if(closed)return;closed=true;if(active==this)active=null;
            anchor.removeOnAttachStateChangeListener(detach);
            AlertDialog c=confirmation;confirmation=null;if(c!=null)c.dismiss();
            if(preview!=null)preview.setText("");if(query!=null)query.setText("");
            if(dialog!=null&&dialog.isShowing())dialog.dismiss();
        }
    }
}
