# F10A75 — independent focused improvements, integration checkpoint

Baseline F10A74 233318ee870b0c1d244ef3a4e4aaa8e56600edaf. Exact baseline FAST run 46103464689 and FULL run 46103464707 succeeded.

| Independent module | Target | Evidence gate |
|---|---|---|
| P1B | Avoid serial 20-second resident unload waits and validate full snapshot before unloading. 15-second monotonic aggregate budget, maximum 3-second request read timeout; checked after I/O. | EngineTransitionPolicyTest, Java/native CI. Best-effort deadline, not a hard bound on OS/network stalls. |
| P2 | Unknown, non-finite, inconsistent RAM/storage must not appear as a fitting recommendation; historical speed cannot override current memory caution. | ModelVariantCatalogTest, ModelPerformanceAdvisorTest. |
| P3 | Preserve chosen family/quantization during asynchronous partial results, same-query refresh and source changes; show empty results when appropriate, reset only on changed query. | ModelListFilterTest, existing sidebar and responsive contracts. |
| P4 | Classify interrupted workflows as recoverable paused checkpoints, distinguish timeout transport failure, restore actual InterruptedException flag after durable checkpoint. No automatic retry of unknown transport. | WorkflowRunJournalTest, existing workflow checks. |
| P5 | Stream private abandoned-PDF directory scan instead of allocating all paths before 10,000-entry bound. Existing age, name, regular-file and symlink protections retained. | PrivacyPolicyTest. |

Integrated in one tree on the verified baseline. Exact candidate FAST/FULL not yet observed. Do not claim actual independent agents, physical device tests, improved throughput, security certification, or 90+ quality. Maintain original release gates, ads/billing locks, and package/data compatibility.
