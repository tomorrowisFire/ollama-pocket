# F11-4 r003 — model search regression repair

FAST run 46146091734 failed at scripts/test_sidebar_contract.py because the structural test still required the retired shared two-worker pool. Replace only that obsolete expectation with explicit two independent single-worker bounded lanes, provider routing, cancellation and shutdown assertions. Keep all other sidebar/search checks. Strengthen bidirectional fixture to track cancellation of the newest blocked-lane request, not a previously exhausted latch. Exact corrected candidate must pass FAST/FULL; no test skip or weakened queue capacity.
