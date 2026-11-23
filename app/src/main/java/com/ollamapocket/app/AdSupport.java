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

/** Test banners only. Production monetization requires publisher IDs and privacy configuration. */
public final class AdSupport {
    // Official Google demo IDs do not produce publisher revenue.
    private static final String DEMO_BANNER = "ca-app-pub-3940256099942544/6300978111";
    private final Activity activity;
    private final ConsentInformation consent;
    private final SharedPreferences prefs;
    private final Map<FrameLayout, AdView> live = new IdentityHashMap<>();
    private final ArrayList<FrameLayout> requested = new ArrayList<>();
    private boolean consentRequestStarted;
    private boolean adsInitialized;
    private boolean disposed;

    public AdSupport(Activity activity) {
        this.activity = activity;
        this.consent = UserMessagingPlatform.getConsentInformation(activity);
        this.prefs = activity.getSharedPreferences("ad_options", Context.MODE_PRIVATE);
    }

    public boolean isEnabled() { return prefs.getBoolean("enabled", true); }

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

    public void register(FrameLayout parent) {
        if (disposed) return;
        status(parent, "광고 영역 · 데이터 사용 가능 · 테스트 광고");
    }

    public void showBanner(FrameLayout parent) {
        if (disposed || parent == null) return;
        if (!requested.contains(parent)) requested.add(parent);
        if (!isEnabled()) { status(parent, "광고 표시 꺼짐"); return; }
        if (consent.canRequestAds()) load(parent);
        else status(parent, "광고 개인정보 확인 중 · 표시되지 않을 수 있습니다");
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
    }

    private void load(FrameLayout parent) {
        if (disposed || !isEnabled() || !consent.canRequestAds() || live.containsKey(parent)) return;
        if (parent.getWindowToken() == null) {
            // Dialogs and pages can be attached after the consent callback arrives.
            parent.post(() -> { if (!disposed && requested.contains(parent)) load(parent); });
            return;
        }
        AdView banner = new AdView(activity);
        banner.setAdSize(AdSize.BANNER);
        banner.setAdUnitId(DEMO_BANNER);
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
        requested.remove(parent);
        AdView banner = live.remove(parent);
        if (banner != null) banner.destroy();
        if (parent != null) parent.removeAllViews();
    }

    public void destroy() {
        disposed = true;
        for (AdView banner : live.values()) banner.destroy();
        live.clear();
        requested.clear();
    }
}
