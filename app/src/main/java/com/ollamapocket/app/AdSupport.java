package com.ollamapocket.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

/** Debug builds show demo ads. Release ad units require a verified publisher app ID. */
public final class AdSupport {
    // Official Google demo IDs do not produce publisher revenue.
    private static final String DEMO_BANNER = "ca-app-pub-3940256099942544/6300978111";
    private static final String DEMO_ADAPTIVE_BANNER = "ca-app-pub-3940256099942544/9214589741";
    private static final String DEMO_APP_OPEN = "ca-app-pub-3940256099942544/9257395921";
    // Publisher-supplied AdMob ad-unit IDs; active only in configured release builds.
    private static final String LIVE_APP_OPEN = "ca-app-pub-4763897710237099/8245753612";
    private static final String LIVE_DOWNLOAD_BANNER = "ca-app-pub-4763897710237099/7124243639";
    private static final String LIVE_ONBOARDING_BANNER = "ca-app-pub-4763897710237099/9558835282";
    public static final String PLACEMENT_DOWNLOAD = "download";
    public static final String PLACEMENT_ONBOARDING = "onboarding";
    private final Activity activity;
    private final ConsentInformation consent;
    private final SharedPreferences prefs;
    private final Map<FrameLayout, AdView> live = new IdentityHashMap<>();
    private final Map<FrameLayout, String> placements = new IdentityHashMap<>();
    private final Map<FrameLayout, View.OnLayoutChangeListener> layoutListeners = new IdentityHashMap<>();
    private AppOpenAd appOpenAd;
    private boolean appOpenLoading;
    private boolean appOpenShowing;
    private long appOpenLoadedTime;
    private long lastAppOpenShownTime;
    private static final long APP_OPEN_TTL = 4L * 60 * 60 * 1000;
    private static final long APP_OPEN_COOLDOWN = 4L * 60 * 60 * 1000;
    private final ArrayList<FrameLayout> requested = new ArrayList<>();
    private boolean consentRequestStarted;
    private boolean sdkInitStarted;
    private boolean adsInitialized;
    private boolean disposed;
    private boolean testAdFree;
    private final Map<FrameLayout,Integer> purchaseHidden=new IdentityHashMap<>();
    private boolean foreground;
    private boolean consentFlowActive;
    private boolean privacyShowing;
    private int adGeneration;

    public AdSupport(Activity activity) {
        this.activity = activity;
        this.consent = UserMessagingPlatform.getConsentInformation(activity);
        this.prefs = activity.getSharedPreferences("ad_options", Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        if(testAdFree)return false;
        return BuildConfig.DEBUG && prefs.getBoolean("enabled", true);
    }
    /** Only the configured test billing controller can suppress demo ads. Never enable live ads. */
    void setTestAdFree(boolean owned){
        if(disposed||!BuildConfig.DEBUG||!BuildConfig.BILLING_TEST_ENABLED||testAdFree==owned)return;
        testAdFree=owned;
        if(owned){
            clearLoadedAds(R.string.billing_owned);
            for(FrameLayout slot:new ArrayList<>(placements.keySet()))status(slot,activity.getString(R.string.billing_owned));
        }else{
            for(FrameLayout slot:new ArrayList<>(purchaseHidden.keySet()))restorePurchaseVisibility(slot);
            for(FrameLayout slot:new ArrayList<>(requested))showBanner(slot);
            if(isEnabled())prepareConsent();
        }
    }
    private void restorePurchaseVisibility(FrameLayout slot){
        Integer visibility=purchaseHidden.remove(slot);
        if(visibility!=null)slot.setVisibility(visibility);
    }

    private void status(FrameLayout parent, String value) {
        if (disposed || parent == null) return;
        parent.removeAllViews();
        if(testAdFree){
            if(!purchaseHidden.containsKey(parent))purchaseHidden.put(parent,parent.getVisibility());
            parent.setVisibility(View.GONE);return;
        }
        restorePurchaseVisibility(parent);
        TextView placeholder = new TextView(activity);
        placeholder.setText(value);
        placeholder.setTextSize(11f);
        placeholder.setTextColor(Color.rgb(170, 183, 195));
        placeholder.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, toDp(56));
        parent.addView(placeholder, lp);
    }

    private int toDp(int px) { return (int)(activity.getResources().getDisplayMetrics().density * px + 0.5f); }

    public void register(FrameLayout parent, String placement) {
        if (disposed) return;
        placements.put(parent, placement);
        if(!layoutListeners.containsKey(parent)){
            View.OnLayoutChangeListener listener=(view,l,t,r,b,ol,ot,or,ob)->{
                if(r-l!=or-ol && requested.contains(parent))load(parent);
            };
            layoutListeners.put(parent,listener);
            parent.addOnLayoutChangeListener(listener);
        }
        status(parent, activity.getString(R.string.ad_area) + (BuildConfig.DEBUG ? " · "+activity.getString(R.string.ad_test) : ""));
    }

    public void showBanner(FrameLayout parent) {
        if (disposed || parent == null || !placements.containsKey(parent)) return;
        if (!requested.contains(parent)) requested.add(parent);
        if (!isEnabled()) { status(parent, activity.getString(R.string.ad_off)); return; }
        if (consent.canRequestAds() && adsInitialized) load(parent);
        else {
            status(parent, activity.getString(R.string.ad_consent_check));
            prepareConsent();
        }
    }

    public void prepareConsent() {
        if (disposed || consentRequestStarted || !isEnabled()) return;
        consentRequestStarted = true;
        consentFlowActive = true;
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        consent.requestConsentInfoUpdate(activity, params,
            () -> {
                if (disposed) return;
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, formError -> {
                    consentFlowActive=false;
                    if (!disposed) maybeLoadAfterConsent();
                });
            },
            requestError -> { consentFlowActive=false; if (!disposed) maybeLoadAfterConsent(); });
    }

    private void maybeLoadAfterConsent() {
        if(disposed||consentFlowActive||privacyShowing)return;
        if (!consent.canRequestAds()) {
            clearLoadedAds(R.string.ad_consent_required);
            return;
        }
        if (!isEnabled()||!foreground)return;
        if (!adsInitialized) {
            if(!sdkInitStarted){
                sdkInitStarted=true;
                MobileAds.initialize(activity, state -> activity.runOnUiThread(()->{
                    if(disposed)return;
                    adsInitialized=true;
                    maybeLoadAfterConsent();
                }));
            }
            return;
        }
        for (FrameLayout parent : new ArrayList<>(requested)) load(parent);
        preloadAppOpen();
    }

    private void load(FrameLayout parent) {
        if (disposed || !foreground || consentFlowActive || privacyShowing || !isEnabled() || !adsInitialized || !consent.canRequestAds()
                || !placements.containsKey(parent) || !requested.contains(parent) || live.containsKey(parent)) return;
        // Preserve standard 320dp banner; adapt only narrow slots instead of clipping the ad.
        if (parent.getWindowToken() == null) return;
        int widthDp=(int)((parent.getWidth()-parent.getPaddingLeft()-parent.getPaddingRight())/
            activity.getResources().getDisplayMetrics().density);
        if(widthDp<200)return;
        boolean adaptive=widthDp<320;
        AdSize size=adaptive?AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity,widthDp):AdSize.BANNER;
        if(size==null||size.getWidth()<=0||size.getWidth()>widthDp||size.getHeight()<=0)return;
        AdView banner = new AdView(activity);
        banner.setAdSize(size);
        String placement = placements.get(parent);
        String adUnitId = BuildConfig.DEBUG ? (adaptive?DEMO_ADAPTIVE_BANNER:DEMO_BANNER) :
            (PLACEMENT_ONBOARDING.equals(placement) ? LIVE_ONBOARDING_BANNER : LIVE_DOWNLOAD_BANNER);
        banner.setAdUnitId(adUnitId);
        banner.setAdListener(new AdListener() {
            @Override public void onAdFailedToLoad(LoadAdError error) {
                if (live.get(parent) == banner) {
                    live.remove(parent);
                    banner.destroy();
                    status(parent, activity.getString(R.string.ad_load_failed));
                }
            }
        });
        live.put(parent, banner);
        parent.removeAllViews();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        parent.addView(banner, lp);
        banner.loadAd(new AdRequest.Builder().build());
    }

    /** Preload a closeable app-open ad for a later foreground entry; never interrupt onboarding. */
    private void preloadAppOpen() {
        if (disposed || !foreground || consentFlowActive || privacyShowing || !adsInitialized || !isEnabled() || !consent.canRequestAds() || appOpenLoading) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (appOpenAd != null && now - appOpenLoadedTime < APP_OPEN_TTL) return;
        appOpenAd = null;
        appOpenLoading = true;
        final int generation=adGeneration;
        AppOpenAd.load(activity, BuildConfig.DEBUG ? DEMO_APP_OPEN : LIVE_APP_OPEN,
            new AdRequest.Builder().build(), new AppOpenAd.AppOpenAdLoadCallback() {
                @Override public void onAdLoaded(AppOpenAd ad) {
                    if (disposed||generation!=adGeneration) return;
                    appOpenLoading=false;
                    if(!isEnabled()||!consent.canRequestAds()||privacyShowing)return;
                    appOpenAd = ad;
                    appOpenLoadedTime = android.os.SystemClock.elapsedRealtime();
                    appOpenLoading = false;
                }
                @Override public void onAdFailedToLoad(LoadAdError error) {
                    if(generation==adGeneration)appOpenLoading = false;
                }
            });
    }

    /** Called only when returning to the app after a meaningful absence. */
    public void showAppOpenIfReady() {
        if (disposed || !foreground || consentFlowActive || privacyShowing || !isEnabled() || !consent.canRequestAds() || appOpenShowing || activity.isFinishing() || activity.isDestroyed()) return;
        for(FrameLayout parent:requested){
            if(PLACEMENT_ONBOARDING.equals(placements.get(parent)) || (live.containsKey(parent)&&parent.isShown()))return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        if (appOpenAd == null || now - appOpenLoadedTime >= APP_OPEN_TTL) {
            preloadAppOpen();
            return;
        }
        if (lastAppOpenShownTime > 0 && now - lastAppOpenShownTime < APP_OPEN_COOLDOWN) return;
        AppOpenAd ad = appOpenAd;
        appOpenAd = null;
        appOpenShowing = true;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                appOpenShowing = false;
                preloadAppOpen();
            }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) {
                appOpenShowing = false;
                preloadAppOpen();
            }
        });
        lastAppOpenShownTime = now;
        try{ad.show(activity);}catch(RuntimeException e){appOpenShowing=false;}
    }

    public void showPrivacyChoices() {
        if(disposed||consentFlowActive||privacyShowing)return;
        if(!BuildConfig.DEBUG){new AlertDialog.Builder(activity).setTitle(R.string.privacy_settings).setMessage(R.string.ad_off).setPositiveButton(R.string.close,null).show();return;}
        if (consent.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            privacyShowing=true;
            clearLoadedAds(R.string.ad_consent_check);
            UserMessagingPlatform.showPrivacyOptionsForm(activity, error -> {
                privacyShowing=false;
                if (!disposed) maybeLoadAfterConsent();
            });
        } else if(testAdFree){
            new AlertDialog.Builder(activity).setTitle(R.string.privacy_settings).setMessage(R.string.billing_owned)
                .setPositiveButton(R.string.close,null).show();
        } else {
            new AlertDialog.Builder(activity)
                .setTitle(R.string.privacy_settings)
                .setMessage(activity.getString(R.string.ad_privacy_fallback))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(isEnabled() ? activity.getString(R.string.ad_turn_off) : activity.getString(R.string.ad_turn_on), (d,w) -> {
                    if (isEnabled()) disable(); else enable();
                }).show();
        }
    }

    private void disable() {
        prefs.edit().putBoolean("enabled", false).apply();
        clearLoadedAds(R.string.ad_off);
    }

    private void clearLoadedAds(int message){
        adGeneration++;appOpenLoading=false;appOpenAd=null;
        for (FrameLayout parent : new ArrayList<>(requested)) {
            AdView view = live.remove(parent);
            if (view != null) view.destroy();
            status(parent, activity.getString(message));
        }
    }
    public void pause(){foreground=false;for(AdView view:new ArrayList<>(live.values()))view.pause();}
    public void resume(){
        if(disposed)return;foreground=true;
        if(!isEnabled()){clearLoadedAds(R.string.ad_off);return;}
        if(!consentFlowActive&&!privacyShowing&&consent.canRequestAds())for(AdView view:new ArrayList<>(live.values()))view.resume();
        if(consentRequestStarted)maybeLoadAfterConsent();
    }

    private void enable() {
        prefs.edit().putBoolean("enabled", true).apply();
        if (!consentRequestStarted) prepareConsent();
        else maybeLoadAfterConsent();
    }

    public void detach(FrameLayout parent) {
        View.OnLayoutChangeListener listener=layoutListeners.remove(parent);
        if(parent!=null&&listener!=null)parent.removeOnLayoutChangeListener(listener);
        placements.remove(parent);
        requested.remove(parent);
        if(parent!=null)restorePurchaseVisibility(parent);
        AdView banner = live.remove(parent);
        if (banner != null) banner.destroy();
        if (parent != null) parent.removeAllViews();
    }

    public void destroy() {
        disposed = true;foreground=false;adGeneration++;appOpenLoading=false;
        for(Map.Entry<FrameLayout,View.OnLayoutChangeListener> e:layoutListeners.entrySet())
            e.getKey().removeOnLayoutChangeListener(e.getValue());
        layoutListeners.clear();
        for (AdView banner : live.values()) banner.destroy();
        live.clear();
        requested.clear();
        placements.clear();purchaseHidden.clear();
        appOpenAd = null;
    }
}
