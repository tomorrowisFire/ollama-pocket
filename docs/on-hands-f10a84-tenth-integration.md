# F10A84 — tenth focused integration

Parent F10A83 5f3d1718d0845edd8d4ab688630184bf77b91e0a: FAST 46109087096 and FULL 46109087061 both succeeded.

- Cloud: identical bounded context validation in offline estimate and network request, including individual prompt/reply limits. For Responses completed payloads containing output, validate final text and compare to received deltas; accept valid terminal-only text, reject contradictory text and action outputs. Legacy completed events omitting output remain supported. Invalid JSON is incomplete, not a saved answer.
- Cost: decimal arithmetic for manually priced estimates and budget boundaries, account completed/failed responses to their reviewed route before checking history ownership. Unknown charges remain unknown, LAN remains unpriced. These are not provider billing or a spending guarantee.
- Voice: one nonrecursive silent-chunk traversal for neural and Android speech, preserve exact text offsets and skip all-whitespace trailing chunks. Recognize Unicode spaces at chunk boundaries and saturate barge frame counts.
- Knowledge: recognize multiple bracketed citations and full-width brackets, flag malformed/case-mismatched IDs as unknown. Literal retrieval and reviewed source boundaries remain unchanged; citations are not factual verification.

Separate implementation scopes integrated in one candidate. Updated existing FAST fixtures and wiring contracts. No actual service, device performance, independent expert or release claim; physical validation remains final.
