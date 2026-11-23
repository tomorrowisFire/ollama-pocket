package com.ollamapocket.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pure Java, deliberately conservative cooperative memory preflight.
 * Android's availMem is NOT directly usable as model capacity. These are
 * admission and split hints, not OS memory limits or a guarantee of success.
 */
public final class CoopMemoryPlanner {
    public static final long MIN_REMOTE_MIB = 384;
    private CoopMemoryPlanner() {}

    /** Estimate temporary model budget from live ActivityManager.MemoryInfo. */
    public static long safeMiB(long totalMiB, long availableMiB,
                               long lowThresholdMiB, boolean lowMemory,
                               boolean host) {
        if (totalMiB <= 0 || availableMiB <= 0 || lowMemory) return 0;
        long available = Math.min(totalMiB, availableMiB);
        long threshold = Math.min(totalMiB, Math.max(0, lowThresholdMiB));
        long appReserve = host ? Math.max(768, totalMiB / 12)
                               : Math.max(384, totalMiB / 10);
        long afterReserve = Math.max(0, available - threshold - appReserve);
        long budget = Math.min(totalMiB * 70 / 100, afterReserve * 70 / 100);
        return budget < 256 ? 0 : budget;
    }

    public static final class Plan {
        public final boolean allowed;
        public final String reason;
        public final long modelMiB;
        public final long overheadMiB;
        public final long requiredMiB;
        public final long hostSafeMiB;
        public final long totalSafeMiB;
        public final List<Integer> selectedIndices;
        public final List<Long> remoteSafeMiB;
        public final String tensorSplit;

        private Plan(boolean allowed, String reason, long modelMiB,
                     long overheadMiB, long requiredMiB, long hostSafeMiB,
                     long totalSafeMiB, List<Integer> selectedIndices,
                     List<Long> remoteSafeMiB, String tensorSplit) {
            this.allowed = allowed;
            this.reason = reason;
            this.modelMiB = modelMiB;
            this.overheadMiB = overheadMiB;
            this.requiredMiB = requiredMiB;
            this.hostSafeMiB = hostSafeMiB;
            this.totalSafeMiB = totalSafeMiB;
            this.selectedIndices = Collections.unmodifiableList(new ArrayList<>(selectedIndices));
            this.remoteSafeMiB = Collections.unmodifiableList(new ArrayList<>(remoteSafeMiB));
            this.tensorSplit = tensorSplit;
        }

        public String summary() {
            return String.format(Locale.KOREA,
                    "GGUF 약 %,d MiB + KV/작업 여유 최소 %,d MiB\n"
                    + "예상 필요 %,d MiB / 안전 배정 합계 %,d MiB\n"
                    + "호스트 %,d MiB · 보조 노드 %d대\n"
                    + "%s\n"
                    + "※ OS 메모리 한도를 강제하지 않는 사전 추정입니다.",
                    modelMiB, overheadMiB, requiredMiB, totalSafeMiB,
                    hostSafeMiB, selectedIndices.size(), reason);
        }
    }

    /**
     * Splits specify relative remote RPC device weights, NOT a host/RPC
     * split. Local CPU tensors and KV allocation are chosen by llama.cpp.
     */
    public static Plan evaluate(long modelMiB, long hostSafeMiB, List<Long> remotes) {
        long weight = Math.max(0, modelMiB);
        long host = Math.max(0, hostSafeMiB);
        long overhead = Math.max(768, (weight + 4) / 5);
        long required = weight + overhead;
        List<Integer> used = new ArrayList<>();
        List<Long> capacities = new ArrayList<>();
        List<String> split = new ArrayList<>();
        long safe = host;
        if (remotes != null) for (int i = 0; i < remotes.size(); i++) {
            Long raw = remotes.get(i);
            long budget = raw == null ? 0 : Math.max(0, raw);
            if (budget < MIN_REMOTE_MIB) continue;
            used.add(i);
            capacities.add(budget);
            safe += budget;
            // Keep a conservative RPC runtime overhead outside tensor weights.
            split.add(Long.toString(Math.max(1, budget - 192)));
        }
        String reason;
        boolean allowed = false;
        if (weight < 128) reason = "모델 GGUF 용량을 확인할 수 없습니다.";
        else if (used.isEmpty()) reason = "안전 RAM 384 MiB 이상인 보조 노드가 없습니다.";
        else if (host < Math.max(512, overhead / 2))
            reason = "호스트의 KV 캐시·추론 작업용 RAM이 부족합니다.";
        else if (safe < required)
            reason = "안전 RAM 약 " + (required - safe) + " MiB 추가 확보가 필요합니다.";
        else {
            allowed = true;
            reason = "사전 RAM 조건 통과 · 실제 RPC 로딩 및 토큰 생성 검증 필요";
        }
        return new Plan(allowed, reason, weight, overhead, required, host, safe,
                used, capacities, String.join(",", split));
    }
}
