# F11-4 r005 — bounded exact catalog aggregation

Both model providers share aggregation that deduplicates by exact provider/model/file identity, preserves insertion order, prefers a known size over an unknown size and limits retained variants to 4096. Malformed or truncated lookups count as incomplete and remain ineligible for complete-result caching. Fixtures verify file/case identity, ordering, size preference and overflow. Design perspectives: resource safety, download UX, catalog accuracy. Live performance and device tests pending.
