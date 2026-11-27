# F10A47 — compact icon-led home starters

Baseline: f415eb63f3bbceaef27ad99be3ec7e2ebd6f0cd3. Exact Java and secure relay PASS; native packaging for F10A46 skipped (test-only delta). F10A45 full packaging succeeded; require new FULL checkpoint before accepting F10A47.

## Scoped design hypotheses and acceptance
- UI: a narrow phone needs six compact, leading-icon rows rather than six tall vertical tiles. Minimum card height 72dp instead of 104dp, a reduction of 32dp per minimum-height card; this is a source-layout bound, not a measured rendered height. Retain full wrapping for translated content and larger fonts.
- UX: keep all six original actions, visible title and detail, existing draft/consent checks and callbacks. Preserve 48dp touch minimum and grouped accessibility name. No new behavior or automatic transmission.
- Responsive: retain the two-column arrangement and established breakpoint policy. Margins use logical end for the leading icon. Actual phone/tablet and font-scale screenshots remain NOT_RUN.
- Technical: pure Java boundary and 28 width/font combinations, brand source assertions; new exact CI required. Existing launcher is unchanged. Public repository must not contain unpublished icon candidates; icon approval/release remains blocked until independent evaluation and actual renders. No independent score claimed.

A/B/C simulated design/UX/implementation review: choose compact one-column card over shrinking labels or removing useful captions. These are one agent's role-based hypotheses, not three independent reviewers. Reassess full UI and icon separately once visual/device evidence and true independent sessions are available. No app icon change, no production/ad/data/engine/signing changes.
