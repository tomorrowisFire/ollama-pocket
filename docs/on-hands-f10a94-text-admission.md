# F10A94 — conversation metadata and cloud text admission

Base F10A93 b00016dc0a5b646164d551d8fa284e0a5363638f. FAST 46133058253 passed; FULL 46133058132 was pending when preparing isolated candidate. Do not update development ref until mandatory predecessor gates pass.

Prevent invisible-only or malformed/control-containing new conversation titles; preserve existing title layout conversion, multilingual characters and joining characters, existing stored metadata and message contents. Reject illegal controls anywhere in cloud messages, before request, history commit or stream preview, while preserving normal line breaks, tabs and Unicode. A rejected result leaves draft/history/revision intact. No migration, permissions, routing, provider, ads, billing or user-data changes.

Exact parent ChatOrganization and CloudHistory source blobs checked locally. Both independent regression groups reproduced on parent and passed after repair (15 title + 42 cloud = 57 assertions with javac --release 17). Verified expected candidate blob hashes 283047a5a3a2f4b8bb29d33279dd627047e44f50, 79bf9a8e842f176d03ec86d3ea5678af8b5a99a6 and test 1108e9e725587df1d81a570445e6edf9a42fef93. CloudApi integration and provider stream fixtures prepared for CI, not locally run. Actual device, full native verification and independent quality scores remain separate. No score uplift or release claim.
