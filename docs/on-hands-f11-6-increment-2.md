# F11-6 r002 — temporary cloud request buffer ownership

Both ordinary and streamed cloud requests now use one cancellation-aware fixed-length write routine. The owned temporary UTF-8 array is overwritten in a finally block after successful transmission, cancellation, output-open failure or write failure. Request text and transport copies may remain; this is best-effort memory hygiene, not full secure erasure or a change to credential encryption. Offline connection fixtures check exact multilingual bytes, closure, rejection and wiping.

Review perspectives (single assistant): privacy, transport correctness, regression safety. KPI: all four exit paths clear the owned buffer; valid requests preserve exact bytes; cancelled requests do not open the output stream. CI and live provider validation pending.
