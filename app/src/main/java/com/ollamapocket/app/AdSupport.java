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
    private boolean adsInitialized;
    private boolean disposed;

    public AdSupport(Activity activity) {
        this.activity = activity;
        this.consent = UserMessagingPlatform.getConsentInformation(activity);
        this.prefs = activity.getSharedPreferences("ad_options", Context.MODE_PRIVATE);
    }

    public boolean isEnabled() { return BuildConfig.DEBUG && prefs.getBoolean("enabled", true); }

    private void status(FrameLayout parent, String value) {
        if (disposed || parent == null) return;
        parent.removeAllViews();
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
        status(parent, "광고 영역 · 데이터 사용 가능" + (BuildConfig.DEBUG ? " · 테스트 광고" : ""));
    }

    public void showBanner(FrameLayout parent) {
        if (disposed || parent == null || !placements.containsKey(parent)) return;
        if (!requested.contains(parent)) requested.add(parent);
        if (!isEnabled()) { status(parent, "광고 표시 꺼짐"); return; }
        if (consent.canRequestAds() && adsInitialized) load(parent);
        else {
            status(parent, "광고 개인정보 확인 중 · 표시되지 않을 수 있습니다");
            prepareConsent();
        }
    }

    public void prepareConsent() {
        if (disposed || consentRequestStarted || !isEnabled()) return;
        consentRequestStarted = true;
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        consent.requestConsentInfoUpdate(activity, params,
            () -> {
                if (disposed) return;
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, formError -> {
                    if (!disposed) maybeLoadAfterConsent();
                });
                // Cached previous consent can be eligible while forms are being checked.
                if (consent.canRequestAds()) maybeLoadAfterConsent();
            },
            requestError -> { if (!disposed) maybeLoadAfterConsent(); });
    }

    private void maybeLoadAfterConsent() {
        if (!consent.canRequestAds()) {
            for (FrameLayout parent : new ArrayList<>(requested))
                status(parent, "광고 개인정보 선택 또는 네트워크 확인 필요");
            return;
        }
        if (!adsInitialized) {
            adsInitialized = true;
            MobileAds.initialize(activity, state -> { });
        }
        for (FrameLayout parent : new ArrayList<>(requested)) load(parent);
        preloadAppOpen();
    }

    private void load(FrameLayout parent) {
        if (disposed || !isEnabled() || !adsInitialized || !consent.canRequestAds()
                || !placements.containsKey(parent) || !requested.contains(parent) || live.containsKey(parent)) return;
        // A layout event will retry when attached and measured. Never spin on an unattached view,
        // and do not display a fixed-width 320dp banner in a slot too narrow to contain it.
        if (parent.getWindowToken() == null || parent.getWidth() < toDp(320)) return;
        AdView banner = new AdView(activity);
        banner.setAdSize(AdSize.BANNER);
        String placement = placements.get(parent);
        String adUnitId = BuildConfig.DEBUG ? DEMO_BANNER :
            (PLACEMENT_ONBOARDING.equals(placement) ? LIVE_ONBOARDING_BANNER : LIVE_DOWNLOAD_BANNER);
        banner.setAdUnitId(adUnitId);
        banner.setAdListener(new AdListener() {
            @Override public void onAdFailedToLoad(LoadAdError error) {
                if (live.get(parent) == banner) {
                    live.remove(parent);
                    banner.destroy();
                    status(parent, "테스트 광고 표시 불가 · 네트워크 또는 동의 상태 확인");
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
        if (disposed || !isEnabled() || !consent.canRequestAds() || appOpenLoading) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (appOpenAd != null && now - appOpenLoadedTime < APP_OPEN_TTL) return;
        appOpenAd = null;
        appOpenLoading = true;
        AppOpenAd.load(activity, BuildConfig.DEBUG ? DEMO_APP_OPEN : LIVE_APP_OPEN,
            new AdRequest.Builder().build(), new AppOpenAd.AppOpenAdLoadCallback() {
                @Override public void onAdLoaded(AppOpenAd ad) {
                    if (disposed) return;
                    appOpenAd = ad;
                    appOpenLoadedTime = android.os.SystemClock.elapsedRealtime();
                    appOpenLoading = false;
                }
                @Override public void onAdFailedToLoad(LoadAdError error) {
                    appOpenLoading = false;
                }
            });
    }

    /** Called only when returning to the app after a meaningful absence. */
    public void showAppOpenIfReady() {
        if (disposed || !isEnabled() || !consent.canRequestAds() || appOpenShowing) return;
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
        ad.show(activity);
    }

    public void showPrivacyChoices() {
        if (consent.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            UserMessagingPlatform.showPrivacyOptionsForm(activity, error -> {
                if (consent.canRequestAds()) maybeLoadAfterConsent();
            });
        } else {
            new AlertDialog.Builder(activity)
                .setTitle("광고 개인정보 설정")
                .setMessage("현재 Google 동의 관리에서 별도 개인정보 선택 화면을 요청하지 않았습니다. " +
                    "아래에서 앱 내 테스트 광고 표시를 끌 수 있습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton(isEnabled() ? "광고 끄기" : "광고 켜기", (d,w) -> {
                    if (isEnabled()) disable(); else enable();
                }).show();
        }
    }

    private void disable() {
        prefs.edit().putBoolean("enabled", false).apply();
        appOpenAd = null;
        for (FrameLayout parent : new ArrayList<>(requested)) {
            AdView view = live.remove(parent);
            if (view != null) view.destroy();
            status(parent, "광고 표시 꺼짐");
        }
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
        AdView banner = live.remove(parent);
        if (banner != null) banner.destroy();
        if (parent != null) parent.removeAllViews();
    }

    public void destroy() {
        disposed = true;
        for(Map.Entry<FrameLayout,View.OnLayoutChangeListener> e:layoutListeners.entrySet())
            e.getKey().removeOnLayoutChangeListener(e.getValue());
        layoutListeners.clear();
        for (AdView banner : live.values()) banner.destroy();
        live.clear();
        requested.clear();
        placements.clear();
        appOpenAd = null;
    }
}
