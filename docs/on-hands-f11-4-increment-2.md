# F11-4 r002 — provider-isolated model search

One worker and at most one queued request per provider replaces the shared two-worker queue. A cancellation-resistant request cannot consume the other provider's worker. Latest-query cancellation and bounded queue cleanup remain intact. Bidirectional blocking-provider regression fixtures added.

Review perspectives (single assistant): concurrency, responsiveness, resource limits. KPI: unaffected provider completes while the other remains blocked; maximum two total queued jobs; stale queued work never runs. Live network latency and Android performance pending.
