# F10A28 / P2A6 acceptance

Exact app a0fc71224bfa2eea1bb52aab41d0b7344a971f08. [Java 45954723066](https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45954723066) SUCCESS: compile job 140358437042 passed app Java and typed AAR compilation, voice storage, ad lock, five locale and regression checks. Embedded voice host job 140358436789 passed real Whisper (11 core and 18 JNI checks), Korean/English/Japanese Supertonic fixtures and actual Chinese MeloTTS VITS synthesis. Chinese fixtures returned 99372 and 80896 finite nonzero frames at 44100 Hz; cancellation suppression, wrong model-family language rejection and text bounds passed. Exact pinned fixture revision a0d5c6a264c0ef92d70d8661d8cc502d79627cd6, converted MIT LICENSE and all seven selected file sizes/digests observed in the host log.

| File | Bytes | SHA-256 |
|---|---:|---|
| LICENSE | 1053 | 88a50e5a02bbc2a5c2f084dc19da751aa97b1690f5fda76cd8005c8634d1ca70 |
| tokens.txt | 655 | d18664a7e12bd7ea1022ddaf951e534e136815016c5a809d6b64156bffb4369d |
| lexicon.txt | 6837671 | 7236884b02435ac5d10cf69b4be40a61b45aa676b5300f0e412f185748fee528 |
| date.fst | 59154 | eb8aa079ae3cb81d8f4404992f39d61a0cb990947512b5b8d1e54d1f6980e718 |
| number.fst | 64482 | 743f402181fcfebf76cc2f0546b71fa26476e626fbe4e460fb7b4c3a7a8bd5bd |
| phone.fst | 88630 | 1ac2b6fa56b1442320c4de7db08353bab8963a2b57f365eebcdd3a2d3562f8d7 |
| model.onnx | 170429550 | bf30582eb1b012250a35b1a4a80e7dfbcf8485e7bb9de0d95efbbeef0e4ad86d |
Total 177481195 bytes.

[Native 45954723048](https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45954723048) SUCCESS: jobs 140358436792 and 140358556725 passed secure-relay regressions, native RPC and voice runtime builds, cooperative APK/payload, sidebar, unsigned QA AAB, NNAPI and 16 KiB audits. Fixed-signed publication skipped. Exact nonexpired artifacts:
- APK 15178491861: sha256 7a2548c7bc7837535d4e28f43dbaa8f440d176e1e440f665dc5bf3c23873e7d8
- QA AAB 15178372897: sha256 cb2ac52e752b37a8fea547b1fa518f64460dcf40a30e17d585d61766e01bd037
- Compatibility audit 15178143690: sha256 5de0e8735ee5cef04eac269b795ea5ae015adf9b9eff84c110518e6d710823f7

Host qualification accepted. Chinese installation, Android player activation, real device tests and independent reviewer scores not inferred.