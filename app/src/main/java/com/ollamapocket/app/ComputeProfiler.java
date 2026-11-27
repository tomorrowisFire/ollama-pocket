package com.ollamapocket.app;

import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * Distinguishes reported hardware capability from usable inference backends.
 * The embedded Ollama Android backend is CPU-only. A separately compiled Vulkan
 * llama.cpp runner may accelerate GPU inference, but it is reported as active
 * only after successful offload verification. NPU execution is not available.
 */
public final class ComputeProfiler {
    private ComputeProfiler() { }

    private static boolean nativeLoaded;
    static {
        try { System.loadLibrary("deviceprobe"); nativeLoaded = true; }
        catch (UnsatisfiedLinkError | SecurityException ignored) { nativeLoaded = false; }
    }

    private static native String nativeNnapiDevicesV2();

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

        public String description(Context c){
            String version="버전 정보 없음".equals(vulkanVersion)?c.getString(R.string.unknown):vulkanVersion;
            String gpu=gpuCompute?c.getString(R.string.compute_vulkan_version,version):c.getString(R.string.compute_gpu_unavailable);
            String npu=!nnapiQueried?c.getString(R.string.compute_nnapi_unknown):
                nnapiAccelerators>0?c.getString(R.string.compute_nnapi_found,nnapiAccelerators):c.getString(R.string.compute_nnapi_none);
            String backend=c.getString(actualEngine.contains("runner 포함")?R.string.compute_backend_packaged:R.string.compute_backend_cpu);
            return c.getString(R.string.compute_report,cpuCores,deviceName,gpu,npu,backend);
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
                "백엔드  ·  " + actualEngine + "\n" +
                "GPU는 실제 오프로드 확인 후에만 활성화합니다. NPU는 별도 백엔드가 필요합니다.";
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
        NnapiProbeResult probe = null;
        if (nativeLoaded && Build.VERSION.SDK_INT >= 29) {
            try { probe = NnapiProbeResult.parse(nativeNnapiDevicesV2()); }
            catch (LinkageError | RuntimeException ignored) { probe = null; }
        }
        boolean nnapiQueried = probe != null;
        int accelerator = nnapiQueried ? probe.accelerators : 0;
        int gpuDrivers = nnapiQueried ? probe.gpu : 0;
        int cpuDrivers = nnapiQueried ? probe.cpu : 0;
        String chip = Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL != null &&
                !Build.SOC_MODEL.isEmpty() && !"unknown".equalsIgnoreCase(Build.SOC_MODEL) ?
                Build.SOC_MODEL : Build.HARDWARE;
        return new Report(Runtime.getRuntime().availableProcessors(), chip, compute, vk,
                nnapiQueried, accelerator, gpuDrivers, cpuDrivers,
                GpuRunner.packaged(context) ? "CPU Ollama + Vulkan GPU runner 포함" :
                    "CPU Ollama (GPU runner 미포함)");
    }
}
