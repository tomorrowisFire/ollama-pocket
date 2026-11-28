# F12 wide-batch report file export

Only REPORT_JSON and REPORT_TEXT receive a 1 MiB UTF-8 export budget. Retain the existing 320,000-character bound, strict Unicode encoder, immutable reviewed payload, file-destination consent and non-report byte limits. Supports a multilingual report approaching the 280,000-character JSON cap without the previous 640,000-byte rejection. Project library storage and version limits remain unchanged. Regression fixtures cover exact round trips, report-specific admission, project/code rejection and over-character cap.
