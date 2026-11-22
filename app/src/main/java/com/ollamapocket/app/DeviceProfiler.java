package com.ollamapocket.app;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.StatFs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Reads only public on-device information. No device identifiers, location,
 * network calls, permissions, benchmark results or telemetry.
 *
 * Estimates are intentionally conservative for CPU-only ARM64 and num_ctx=2048.
 * A recommendation is not a promise that any given model will load or run quickly.
 */
public final class DeviceProfiler {
    private DeviceProfiler() {}

    public static final class Profile {
        public final String device;
        public final String chip;
        public final String abi;
        public final int cores;
        public final int androidApi;
        public final double ramGb, availableRamGb, freeStorageGb;
        public final boolean arm64, lowMemory;

        private Profile(String device, String chip, String abi, int cores, int androidApi,
                double ramGb, double availableRamGb, double freeStorageGb,
                boolean arm64, boolean lowMemory) {
            this.device = device;
            this.chip = chip;
            this.abi = abi;
            this.cores = cores;
            this.androidApi = androidApi;
            this.ramGb = ramGb;
            this.availableRamGb = availableRamGb;
            this.freeStorageGb = freeStorageGb;
            this.arm64 = arm64;
            this.lowMemory = lowMemory;
        }

        public String description() {
            return "기기: " + device +
                "\n칩셋: " + chip +
                "\nCPU: " + cores + "코어 · ABI: " + abi +
                "\nRAM: " + fmt(ramGb) + " GB (현재 사용 가능 " + fmt(availableRamGb) + " GB)" +
                "\n앱 저장공간 여유: " + fmt(freeStorageGb) + " GB" +
                "\nAndroid API " + androidApi + (lowMemory ? " · Android 메모리 부족 상태" : "") +
                "\n분석 정보는 기기 밖으로 전송하지 않습니다.";
        }
    }

    public static final class Recommendation {
        public final String model, description, category, reason;
        public final double downloadGb;
        public final int priority;
        public final boolean selectable;

        private Recommendation(String model, String description, String category,
                String reason, double downloadGb, int priority, boolean selectable) {
            this.model = model;
            this.description = description;
            this.category = category;
            this.reason = reason;
            this.downloadGb = downloadGb;
            this.priority = priority;
            this.selectable = selectable;
        }

        public String label() {
            return model + "  ·  " + category +
                "\n" + description + " · 다운로드 약 " + fmt(downloadGb) + " GB" +
                "\n" + reason;
        }
    }

    private static final class Candidate {
        final String tag, summary;
        final double sizeGb, minimumRamGb, recommendedRamGb;
        final int preference;
        Candidate(String tag, String summary, double sizeGb, double minimumRamGb,
                double recommendedRamGb, int preference) {
            this.tag = tag;
            this.summary = summary;
            this.sizeGb = sizeGb;
            this.minimumRamGb = minimumRamGb;
            this.recommendedRamGb = recommendedRamGb;
            this.preference = preference;
        }
    }

    private static final Candidate[] CANDIDATES = {
        new Candidate("gemma3:270m", "최저 사양 · 빠른 테스트용", 0.292, 2.4, 3.5, 1),
        new Candidate("qwen2.5:0.5b", "한국어 입문 · 초경량", 0.398, 3.0, 4.5, 2),
        new Candidate("gemma3:1b", "짧은 질문 · 가벼운 대화", 0.806, 4.5, 6.0, 3),
        new Candidate("qwen2.5:1.5b", "한국어 일상 대화 · 균형형", 0.986, 5.0, 7.0, 5),
        new Candidate("llama3.2:1b", "영문 중심 · 경량", 1.3, 5.0, 7.0, 2),
        new Candidate("qwen2.5:3b", "한국어 추론 · 중급", 1.9, 7.5, 10.0, 8),
        new Candidate("llama3.2:3b", "영문·일반 대화 · 중급", 2.0, 8.0, 11.0, 6),
        new Candidate("gemma3:4b", "상급 기기 · CPU 속도 주의", 3.4, 10.0, 14.0, 4),
        new Candidate("qwen2.5:7b", "대형 모델 · 상당히 느릴 수 있음", 4.7, 14.0, 18.0, 3)
    };

    private static String fmt(double v) {
        return String.format(Locale.KOREA, "%.1f", Math.max(0, v));
    }

    public static Profile scan(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);

        StatFs fs = new StatFs(ctx.getFilesDir().getAbsolutePath());
        final double gib = 1024.0 * 1024.0 * 1024.0;
        double ram = mi.totalMem / gib;
        double freeRam = mi.availMem / gib;
        double freeStorage = fs.getAvailableBytes() / gib;
        String[] abis = Build.SUPPORTED_ABIS;
        String abi = abis != null && abis.length > 0 ? abis[0] : "unknown";
        boolean arm64 = false;
        if (abis != null) for (String value : abis) {
            if ("arm64-v8a".equals(value)) { arm64 = true; break; }
        }
        String device = (Build.MANUFACTURER + " " + Build.MODEL).trim();
        String chip = Build.VERSION.SDK_INT >= 31 && !Build.SOC_MODEL.isEmpty()
            && !"unknown".equalsIgnoreCase(Build.SOC_MODEL) ? Build.SOC_MODEL : Build.HARDWARE;
        return new Profile(device, chip, abi,
            Runtime.getRuntime().availableProcessors(), Build.VERSION.SDK_INT,
            ram, freeRam, freeStorage, arm64, mi.lowMemory);
    }

    /** Returns all known examples with suitable candidates ranked first. */
    public static List<Recommendation> recommend(Profile p) {
        List<Recommendation> out = new ArrayList<>();
        for (Candidate c : CANDIDATES) {
            String status, why;
            int priority;
            boolean canDownload;
            if (!p.arm64) {
                status = "미지원"; why = "ARM64 안드로이드가 필요합니다.";
                priority = 0; canDownload = false;
            } else if (p.freeStorageGb < c.sizeGb + 1.0) {
                status = "저장공간 부족"; why = "다운로드 용량 외에 최소 1 GB 여유가 필요합니다.";
                priority = 0; canDownload = false;
            } else if (p.ramGb < c.minimumRamGb) {
                status = "RAM 부족 예상"; why = "기기 전체 RAM 대비 실행 중 종료될 가능성이 높습니다.";
                priority = 0; canDownload = false;
            } else if (p.ramGb >= c.recommendedRamGb && !p.lowMemory && c.sizeGb <= 2.1) {
                status = "추천"; why = "현재 RAM·저장공간 기준으로 우선 시도할 만합니다.";
                priority = 3; canDownload = true;
            } else {
                status = "주의"; why = "실행 가능성은 있으나 느리거나 메모리 부족일 수 있습니다.";
                priority = 1; canDownload = true;
            }
            out.add(new Recommendation(c.tag, c.summary, status, why, c.sizeGb,
                priority * 100 + c.preference, canDownload));
        }
        Collections.sort(out, (a, b) -> Integer.compare(b.priority, a.priority));
        return out;
    }
}
