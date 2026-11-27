# F10A22 / P1A6 simulated A/B/C review
A product: correct misleading GPU/accelerator classification and retain clear distinction between detection and real inference support.
B engineering: versioned JNI, NDK-checked device constants, fail-closed partial enumeration and bounded Java parser; exact candidate diagnostic module rebuilt and symbol checks added to cooperative CI. Other native inference payload and release policies retained.
C QA: 1952 host C and 1213 Java checks PASS; tested blobs match. Android NDK build, APK and QA AAB symbol verification and all exact candidate CI gates remain pending. Actual hardware detection, NPU model execution, performance and independent reviewer scores remain unverified. Three roles simulated by one AI; provisional mean 80.3/min 75 unchanged.
