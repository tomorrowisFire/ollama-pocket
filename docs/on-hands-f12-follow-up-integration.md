# F12 r006 integrated follow-up candidate

Base 1580cd41d2936ec9248c67274091b3b1491706eb officially promoted after FAST 46151435828 / FULL 46151435812. Independent module commits:
- O-follow-policy: fa542b7d03d94b84d9ff15e1cf963db38b1991b9
- P-follow-runner: 984f7f9eab3e3c46c526d82182a3a86ab1c323e9
- Q-follow-ui: ee6a1966e1e197ad836571e8a2b04a64edf71a9a
- R-reward-recovery: 5413e3c0d8018ab626d70d5650775fed532e1c25
- S-locales-focus: a1a1bc5d8075e54f5f4ddf9908556303e0273e29

Source scope extends only for the user-requested follow-up module and fixtures/docs. Existing normal file limits, 20-file/2 GiB deep limit, 30-minute rewarded grant, native/manifest/dependency/data schema and production advertisement lock retained. Known limitations: rolling in-memory dialogue, bounded extracted source ranges only, one 90-second local request per follow-up, no persistent multi-turn deep history, no renewal inside the analysis dialog, live/device tests and independent reviews pending. The actual request count is not capped by the retained-turn count. No promotion before exact candidate FAST/FULL success.
