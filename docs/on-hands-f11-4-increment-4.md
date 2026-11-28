# F11-4 r004 — faster empty search result expiry

Cache complete empty provider results for at most 30 seconds, or the configured TTL if shorter. Preserve existing positive-result lifetime, bounded memory, LRU, query ownership and cancellation; incomplete results still do not enter the cache. Tests exercise exact TTL boundary, pruning before eviction and clock rollback. KPI: a subsequent search after the negative TTL is eligible for refresh while a valid positive result remains reusable. No automatic network polling. Design review perspectives: responsiveness, resource use, data consistency.
