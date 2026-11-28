# F10A83 — ninth focused integration

Parent F10A82 dff23a1e9b66442863150e8dbe2750c21f389599: FAST 46108088598 and FULL 46108088606 completed successfully.

- Confirm every Ollama pull and both GGUF import routes against a valid local /api/tags snapshot with the exact requested tag (or implicit latest), a positive reported byte count and manifest digest. This is registration readback, not independent weight hashing. An unconfirmed install is reported explicitly, not automatically deleted or retried.
- Share a process-wide identity-owned lease across actual pull, HF import and local import workers; keep it through cleanup. UI busy guard checks it across Activity replacement. Cancel owned HF and local registration on destroy; retain cancellation and late-callback checks. This is not a global lock against other external clients or a guarantee of interruptible OS I/O.
- A pull dialog from an older generation cannot overwrite a newer pull's ready state. Refresh parsing uses the same validated inventory identity policy. Five localized unconfirmed messages.
- Integrate legacy ModelTileCatalogTest, ModelListFilterTest, ModelVariantCatalogTest and ModelExperienceTest into the FAST test pipeline; previously these specific suites were not listed in that workflow. New inventory and lease fixtures are wired into real CI.

Preserve all approved UI, licensing, ads, billing, data and release gates. Exact candidate FAST/FULL pending; no actual device, throughput, independent expert evaluation or production approval claimed.
