package com.ollamapocket.app;

import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * Distinguishes reported hardware capability from usable inference backends.
 * The distributed Ollama Android runner is CPU-only. A GPU/NPU feature flag is
 * not proof of acceleration, and never activates GPU/NPU execution.
 */
public final class ComputeProfiler {
    private ComputeProfiler() { }

    private static boolean nativeLoaded;
    static {
        try { System.loadLibrary("deviceprobe"); nativeLoaded = true; }
        catch (UnsatisfiedLinkError | SecurityException ignored) { nativeLoaded = false; }
    }

    private static native String nativeNnapiDevices();

    public static final class Report {
        public final int cpuCores;
        public final String deviceName;
        public final boolean gpuCompute;
        public final String vulkanVersion;
        public final boolean nnapiQueried;
        public final int nnapiAccelerators;
        public final int nnapiGpuDrivers;
        public final int nnapiCpuDrivers;
        public final String actualEngine;

        Report(int cpuCores, String deviceName, boolean gpuCompute, String vulkanVersion,
               boolean nnapiQueried, int nnapiAccelerators, int nnapiGpuDrivers,
               int nnapiCpuDrivers, String actualEngine) {
            this.cpuCores = cpuCores;
            this.deviceName = deviceName;
            this.gpuCompute = gpuCompute;
            this.vulkanVersion = vulkanVersion;
            this.nnapiQueried = nnapiQueried;
            this.nnapiAccelerators = nnapiAccelerators;
            this.nnapiGpuDrivers = nnapiGpuDrivers;
            this.nnapiCpuDrivers = nnapiCpuDrivers;
            this.actualEngine = actualEngine;
        }

        public String description() {
            String gpu = gpuCompute ? "Vulkan " + vulkanVersion + " 연산 지원 감지" :
                    "Vulkan 연산 지원을 확인하지 못함";
            String npu;
            if (!nnapiQueried) npu = "NNAPI 드라이버 조회 불가 (NPU 유무 미확정)";
            else if (nnapiAccelerators > 0)
                npu = "NNAPI 가속기 후보 " + nnapiAccelerators + "개 (NPU인지 별도 검증 필요)";
            else npu = "NNAPI 가속기 드라이버 미발견 (NPU 부재를 뜻하지 않음)";
            return "CPU  ·  " + cpuCores + "코어 · " + deviceName + "\n" +
                "GPU  ·  " + gpu + "\n" +
                "NPU  ·  " + npu + "\n" +
                "실제 Ollama 추론: " + actualEngine + "\n" +
                "현재 APK에는 GPU/NPU 추론 백엔드가 없습니다. 감지와 실행은 다릅니다.";
        }
    }

    public static Report scan(Context context) {
        PackageManager pm = context.getPackageManager();
        int version = 0;
        for (FeatureInfo feature : pm.getSystemAvailableFeatures()) {
            if (feature != null && PackageManager.FEATURE_VULKAN_HARDWARE_VERSION.equals(feature.name)) {
                version = feature.version;
            }
        }
        boolean compute = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_COMPUTE);
        String vk = version > 0 ? (version >>> 22) + "." + ((version >>> 12) & 0x3ff) : "버전 정보 없음";
        String[] parts = null;
        if (nativeLoaded && Build.VERSION.SDK_INT >= 29) {
            try { parts = nativeNnapiDevices().split(",", -1); }
            catch (Throwable ignored) { parts = null; }
        }
        boolean nnapiQueried = parts != null && parts.length == 3;
        int accelerator = 0, gpuDrivers = 0, cpuDrivers = 0;
        if (nnapiQueried) {
            try {
                accelerator = Integer.parseInt(parts[0]);
                gpuDrivers = Integer.parseInt(parts[1]);
                cpuDrivers = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) { nnapiQueried = false; }
        }
        String chip = Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL != null &&
                !Build.SOC_MODEL.isEmpty() && !"unknown".equalsIgnoreCase(Build.SOC_MODEL) ?
                Build.SOC_MODEL : Build.HARDWARE;
        return new Report(Runtime.getRuntime().availableProcessors(), chip, compute, vk,
                nnapiQueried, accelerator, gpuDrivers, cpuDrivers, "CPU (고정)");
    }
}
