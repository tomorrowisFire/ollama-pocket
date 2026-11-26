# Stage 12-A — answer interaction and visual hierarchy candidate

Baseline 138b68c4e629e3263599ce3d35f78f0e0f5bf3b6. This change targets the response interaction gap rather than adding another feature. It preserves the original onboarding ad, production ad lock, package, signing, model/history storage, routing, and workflow policies.

- Local and cloud answer actions share a native width-aware toolbar; each translated and font-scaled child is measured at natural width, switches to a vertical layout if needed, and retains at least the existing touch height. No fixed-height translated cloud answer buttons or squeezed local reply weights.
- Existing local buttons and starter cards receive a theme-neutral bounded touch ripple without replacing their palette/background.
- Rich answers have rounded sections, compact responsive headings and copy controls near the top, and differentiated assistant/provider labels. Code, tables, math, and original raw copies are preserved. Rich-block copy now uses the same sensitive clipboard marker as whole-answer copy (not an encryption or clipboard-access guarantee).
- Eight additional pure Java policy fixtures cover exact fit, narrow windows, expanded translated labels, no/single child and overflow. Existing CI compile, locale and regression gates retained. Physical visual, accessibility, screen-reader, keyboard and touch timing validation NOT_RUN.
- Exact-commit CI and ten-perspective review are required before further stage progression; no score increase or release approval claimed based solely on source changes.

## Subsequent stages (not implemented here)
12-B: token-aware long/multi-document coverage and source citations; 12-C: semantic workflow gates and traceability; 12-D: device voice UX; 12-E: benchmark/engine routing; 12-F: real-device and commercial acceptance. No production release or main merge.
