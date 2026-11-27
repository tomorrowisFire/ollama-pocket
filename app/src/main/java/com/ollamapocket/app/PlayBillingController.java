package com.ollamapocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchaseParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import static com.ollamapocket.app.BillingEntitlementPolicy.State;

/**
 * Explicitly enabled developer/license-test client only. Never enables production sales.
 * All callbacks are serialized on the main thread. No purchase receipt or token is persisted or logged.
 */
final class PlayBillingController {
    private static final long OFFER_TTL=120_000L, REQUEST_TIMEOUT=45_000L, FLOW_TIMEOUT=180_000L;
    private final Activity owner;
    private final BooleanSupplier eligible;
    private final Consumer<Boolean> entitlement;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final PublicKey key;
    private final boolean configured;
    private BillingClient client;
    private boolean closed,foreground,working,flowing,repeat,owned;
    private int serial,clientSerial,flowSerial;
    private State ownership;
    private int notice=R.string.billing_unavailable;
    private ProductDetails details;
    private String offerToken="",price="";
    private long offerAt;
    private AlertDialog dialog;
    private TextView status;
    private Button buy,restore;

    PlayBillingController(Activity activity,BooleanSupplier eligible,Consumer<Boolean> entitlement){
        owner=activity;this.eligible=eligible;this.entitlement=entitlement;
        key=BillingEntitlementPolicy.key(BuildConfig.BILLING_TEST_PUBLIC_KEY);
        configured=BillingEntitlementPolicy.enabled(BuildConfig.DEBUG,BuildConfig.BILLING_TEST_ENABLED,key)
            &&BillingEntitlementPolicy.PACKAGE.equals(activity.getPackageName());
    }
    private boolean alive(){return !closed&&!owner.isFinishing()&&!owner.isDestroyed();}
    private boolean current(int ticket){return alive()&&working&&ticket==serial;}
    private boolean ready(){return client!=null&&client.isReady();}
    private boolean ok(BillingResult result){return result!=null&&result.getResponseCode()==BillingClient.BillingResponseCode.OK;}
    boolean busy(){return working||flowing;}
    void resume(){if(!alive())return;foreground=true;if(configured)refresh();else render();}
    void pause(){foreground=false;}
    private int dp(int value){return Math.round(value*owner.getResources().getDisplayMetrics().density);}
    private void clearOffer(){details=null;offerToken="";price="";offerAt=0;}
    private boolean freshOffer(){
        long age=SystemClock.elapsedRealtime()-offerAt;
        return details!=null&&!offerToken.isEmpty()&&!price.isEmpty()&&age>=0&&age<=OFFER_TTL;
    }
    private boolean canBuy(){
        return BillingEntitlementPolicy.mayBuy(configured,foreground,eligible.getAsBoolean(),ready(),working,flowing,ownership,freshOffer());
    }
    private void render(){
        if(!alive()||dialog==null||!dialog.isShowing()||status==null)return;
        status.setText(notice);
        if(owned&&ownership!=State.OWNED)status.append("\n"+owner.getString(R.string.billing_cached_session));
        buy.setText(price.isEmpty()?owner.getString(R.string.billing_buy_unpriced):owner.getString(R.string.billing_buy,price));
        buy.setEnabled(canBuy());
        restore.setEnabled(configured&&foreground&&!working&&!flowing);
    }
    void show(){
        if(!alive()||!foreground||!eligible.getAsBoolean())return;
        if(dialog!=null&&dialog.isShowing())return;
        LinearLayout body=new LinearLayout(owner);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16),dp(12),dp(16),dp(12));
        TextView description=new TextView(owner);description.setText(R.string.billing_test_notice);
        WorkbenchStyle.label(owner,description,14,false,false);body.addView(description,WorkbenchStyle.row(owner));
        status=new TextView(owner);WorkbenchStyle.label(owner,status,13,true,false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(status,WorkbenchStyle.row(owner));
        buy=new Button(owner);WorkbenchStyle.button(owner,buy,WorkbenchVisualPolicy.Role.PRIMARY);
        buy.setOnClickListener(v->launch());body.addView(buy,WorkbenchStyle.row(owner));
        restore=new Button(owner);restore.setText(R.string.billing_restore);
        WorkbenchStyle.button(owner,restore,WorkbenchVisualPolicy.Role.SECONDARY);
        restore.setOnClickListener(v->refresh());body.addView(restore,WorkbenchStyle.row(owner));
        AlertDialog screen=new AlertDialog.Builder(owner).setTitle(R.string.billing_title).setView(body)
            .setPositiveButton(R.string.close,null).create();
        dialog=screen;screen.setOnDismissListener(d->{if(dialog==screen){dialog=null;status=null;buy=null;restore=null;}});
        screen.show();WorkbenchWindow.fitCompact(owner,screen);
        if(configured)refresh();render();
    }
    private void resetClient(){
        clientSerial++;BillingClient previous=client;client=null;
        if(previous!=null)try{previous.endConnection();}catch(RuntimeException ignored){}
    }
    private BillingClient newClient(){
        final int id=++clientSerial;
        return BillingClient.newBuilder(owner)
            .setListener((result,purchases)->main.post(()->{
                if(!alive()||id!=clientSerial)return;
                flowSerial++;flowing=false;
                if(result!=null&&result.getResponseCode()==BillingClient.BillingResponseCode.USER_CANCELED&&foreground)
                    Toast.makeText(owner,R.string.billing_cancelled,Toast.LENGTH_SHORT).show();
                // Do not trust callback lists: obtain an authoritative current Play client snapshot.
                refresh();
            }))
            .enablePendingPurchases(PendingPurchaseParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection().build();
    }
    private void refresh(){
        if(!alive()||!configured)return;
        if(working){repeat=true;return;}
        repeat=false;working=true;ownership=null;clearOffer();notice=R.string.billing_checking;render();
        final int ticket=++serial;
        main.postDelayed(()->{if(current(ticket)){resetClient();finish(ticket,R.string.billing_error);}},REQUEST_TIMEOUT);
        try{
            if(client==null)client=newClient();
            if(ready()){query(ticket);return;}
            client.startConnection(new BillingClientStateListener(){
                @Override public void onBillingSetupFinished(BillingResult result){
                    main.post(()->{if(current(ticket)){if(ok(result))query(ticket);else finish(ticket,R.string.billing_error);}});
                }
                @Override public void onBillingServiceDisconnected(){
                    main.post(()->{if(current(ticket))finish(ticket,R.string.billing_error);});
                }
            });
        }catch(RuntimeException unavailable){resetClient();finish(ticket,R.string.billing_error);}
    }
    private void finish(int ticket,int message){
        if(!current(ticket))return;
        working=false;notice=message;render();
        if(repeat){repeat=false;refresh();}
    }
    private void applyOwned(boolean value){
        if(owned==value)return;
        owned=value;entitlement.accept(value);
    }
    private State inspect(Purchase purchase){
        if(purchase==null)return State.INVALID;
        int state=purchase.getPurchaseState();
        return BillingEntitlementPolicy.inspect(purchase.getPackageName(),purchase.getProducts(),purchase.getPurchaseToken(),purchase.getQuantity(),
            state==Purchase.PurchaseState.PURCHASED,state==Purchase.PurchaseState.PENDING,purchase.isAcknowledged(),
            BillingEntitlementPolicy.verify(key,purchase.getOriginalJson(),purchase.getSignature()));
    }
    private void query(int ticket){
        if(!current(ticket))return;
        try{
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                (result,items)->main.post(()->{
                    if(!current(ticket))return;
                    if(!ok(result)||items==null){finish(ticket,R.string.billing_error);return;}
                    State snapshot=State.ABSENT;Purchase match=null;
                    for(Purchase purchase:items){
                        State next=inspect(purchase);
                        if(next!=State.ABSENT)match=purchase;
                        snapshot=BillingEntitlementPolicy.merge(snapshot,next);
                    }
                    ownership=snapshot;
                    switch(snapshot){
                        case OWNED:applyOwned(true);finish(ticket,R.string.billing_owned);return;
                        case NEED_ACK:
                            if(match==null){ownership=State.INVALID;applyOwned(false);finish(ticket,R.string.billing_invalid);return;}
                            acknowledge(ticket,match.getPurchaseToken());return;
                        case PENDING:applyOwned(false);finish(ticket,R.string.billing_pending);return;
                        case INVALID:applyOwned(false);finish(ticket,R.string.billing_invalid);return;
                        case ABSENT:applyOwned(false);queryOffer(ticket);return;
                    }
                }));
        }catch(RuntimeException unavailable){finish(ticket,R.string.billing_error);}
    }
    private void acknowledge(int ticket,String token){
        if(!current(ticket)||ownership!=State.NEED_ACK)return;
        try{
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build(),
                result->main.post(()->{
                    if(!current(ticket))return;
                    if(ok(result)){
                        ownership=State.OWNED;applyOwned(true);finish(ticket,R.string.billing_owned);
                    }else finish(ticket,R.string.billing_error);
                }));
        }catch(RuntimeException unavailable){finish(ticket,R.string.billing_error);}
    }
    private void queryOffer(int ticket){
        if(!current(ticket))return;
        try{
            QueryProductDetailsParams params=QueryProductDetailsParams.newBuilder().setProductList(Collections.singletonList(
                QueryProductDetailsParams.Product.newBuilder().setProductId(BillingEntitlementPolicy.PRODUCT)
                    .setProductType(BillingClient.ProductType.INAPP).build())).build();
            client.queryProductDetailsAsync(params,(result,response)->main.post(()->{
                if(!current(ticket))return;
                if(!ok(result)||response==null){finish(ticket,R.string.billing_error);return;}
                List<ProductDetails> products=response.getProductDetailsList();
                if(products==null||products.size()!=1){finish(ticket,R.string.billing_no_product);return;}
                ProductDetails product=products.get(0);
                if(product==null||!BillingEntitlementPolicy.PRODUCT.equals(product.getProductId())||
                    !BillingClient.ProductType.INAPP.equals(product.getProductType())){
                    finish(ticket,R.string.billing_no_product);return;
                }
                List<ProductDetails.OneTimePurchaseOfferDetails> offers=product.getOneTimePurchaseOfferDetailsList();
                if(offers==null||offers.isEmpty()){
                    ProductDetails.OneTimePurchaseOfferDetails only=product.getOneTimePurchaseOfferDetails();
                    offers=only==null?Collections.emptyList():Collections.singletonList(only);
                }
                ProductDetails.OneTimePurchaseOfferDetails chosen=null;
                for(ProductDetails.OneTimePurchaseOfferDetails candidate:offers){
                    if(candidate==null||candidate.getRentalDetails()!=null||candidate.getPreorderDetails()!=null)continue;
                    if(chosen!=null){finish(ticket,R.string.billing_no_product);return;}
                    chosen=candidate;
                }
                if(chosen==null||chosen.getOfferToken()==null||chosen.getOfferToken().isEmpty()||
                    chosen.getOfferToken().length()>8192||chosen.getFormattedPrice()==null||chosen.getFormattedPrice().trim().isEmpty()||
                    chosen.getPriceAmountMicros()<0){finish(ticket,R.string.billing_no_product);return;}
                details=product;offerToken=chosen.getOfferToken();price=chosen.getFormattedPrice();
                offerAt=SystemClock.elapsedRealtime();finish(ticket,R.string.billing_ready);
            }));
        }catch(RuntimeException unavailable){finish(ticket,R.string.billing_error);}
    }
    private void launch(){
        if(dialog==null||!dialog.isShowing()||!alive())return;
        if(!canBuy()){if(configured&&!working&&!flowing)refresh();return;}
        BillingFlowParams params=BillingFlowParams.newBuilder().setProductDetailsParamsList(Collections.singletonList(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(offerToken).build())).build();
        flowing=true;clearOffer();notice=R.string.billing_checking;render();
        final int ticket=++flowSerial;
        main.postDelayed(()->{
            if(alive()&&flowing&&ticket==flowSerial){flowing=false;refresh();}
        },FLOW_TIMEOUT);
        try{
            BillingResult result=client.launchBillingFlow(owner,params);
            if(!ok(result)){flowing=false;flowSerial++;refresh();}
        }catch(RuntimeException unavailable){flowing=false;flowSerial++;refresh();}
    }
    void destroy(){
        if(closed)return;
        closed=true;serial++;flowSerial++;main.removeCallbacksAndMessages(null);
        resetClient();if(dialog!=null)dialog.dismiss();
        dialog=null;status=null;buy=null;restore=null;clearOffer();
    }
}
