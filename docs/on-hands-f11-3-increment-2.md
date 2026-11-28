# F11-3 r002 — multilingual speech chunk boundaries

Prefer breaks outside CRLF, combining marks, common joined emoji and regional-indicator pairs while respecting existing UTF-16 backend limits and preserving exact source offsets. A cluster larger than the backend limit falls back to code-point boundaries. This is not a full Unicode grapheme segmenter or proof of natural prosody. Fixtures cover reconstruction and boundaries; device speech quality pending.

Review perspectives (single assistant): multilingual UX, playback continuity, bounded resource use. KPI: no surrogate breaks, exact reconstruction, limit honored, common joined sequences kept together when possible.
