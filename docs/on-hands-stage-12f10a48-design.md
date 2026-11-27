# F10A48 — workbench dialog action states

Baseline db751d03c1bc8166414b2e0685d66963580d0747: Java run 46011552763 and native run 46011552696 succeeded. Native job actually ran APK, unsigned QA AAB and ARM64 ELF/16KiB audits; stable-signed update publication skipped. Ad, locale, host and regression checks succeeded. F10A45 failure was the stale action spacing expectation repaired by F10A46, not an unresolved defect.

## Scoped implementation
Owner: popup style / palette policy / pure Java tests / brand source assertions. Existing WorkbenchWindow.fit opt-in only. A/B/C role-based review (one AI): avoid replacing platform dialogs and treating every positive button as a safe filled CTA; use consistent text actions with brand ripple, high contrast disabled fill, keyboard focus outline and 48dp minimum touch dimensions. Preserve existing padding, labels, callbacks, visibility, enabled state, window layout and confirmation semantics. No ad, engine, language, permission, launcher or data mutation. Exact candidate CI pending; actual dialog rendering / long translated labels / TalkBack / physical devices NOT_RUN.

## Whole-candidate review (one AI, five simulated perspectives, not independent)
UI: compact one-column starters and shared tokens structurally verified; actual screen composition N/A.
UX: existing actions retained; actual task success, warning comprehension and error recovery N/A.
Brand: artwork hash verified by previous CI, both adaptive XMLs use 26dp inset (56dp foreground extent out of 108dp); no monochrome layer or v33 override present. Actual icon legibility and preference N/A.
Responsive: policy and regression tests pass on baseline; actual phone/tablet, IME and locale fit N/A.
Technical/accessibility: exact baseline Java/native/ads/locale/secure relay gates pass; new code and TalkBack not yet accepted. No whole or icon score/mean defensible without render and independent evidence; stagnation delta N/A, not an invented increase.

Icon-only static audit: both masks refer to same blob, SHA-256 pinned by baseline CI, no monochrome override; ten legacy density/mask PNGs present but visual equivalence not verified. No candidate published, no asset replaced. Prior claims of private candidate renders cannot be verified from repository. Next icon queue: independent silhouette/monochrome prototype and comparative raster checks in private workspace, no final sharing until actual gates.
