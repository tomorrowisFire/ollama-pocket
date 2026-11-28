# F11-3–6 r006 candidate

- F11-3: 558d802b2ea2d9ba7affd0e3d378916bc72056d7
- F11-4: 0f8e039b5d1cf818fc736000f529c1b3ef58125e
- F11-5: 1fb9d455ee97bd1223413af78114a98530e52b4d
- F11-6: 3c0020f96bbae7984239231206f5cf922d73a70a

Exact module blobs and disjoint paths. No changes to native engines, stored schemas, ads, billing or model files and permissions. Deadline guards are not measured latency or hard interruption guarantees. CI, real-device tests and independent review pending.

## F11-7 entry criteria

1. Resolve all predecessor and exact candidate FAST/FULL failures. Verify branch ancestry and exact run head SHAs; failed older candidates are not release evidence.
2. Confirm regression fixtures for deadline/late callbacks, catalog read cancellation/UTF-8, durable workflow cancellation/evidence, cloud stream terminal/usage admission. Confirm five-locale resource parity, existing billing/first-run ads, native host checks and Android compile using existing gates.
3. Review cumulative F11 delta against F11-2F for unapproved native/dependency/manifest/billing/data changes and confirm test coverage. Record unresolved cases rather than treating synthetic fixtures as device or live provider evidence.
4. Only after verified source integration begin final device testing: voice interruption and model latency on actual hardware, both catalog networks, automation recovery, cloud provider interoperability and UI/UX, payments/ads in appropriate test environment. Independent evaluation requires real reviewers; do not report simulated scores as external results.
