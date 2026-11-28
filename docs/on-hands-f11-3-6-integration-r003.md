# F11-3–6 r003 corrected candidate

- F11-3: 8dea9707f37a7c5622f0fe13826f20c2930da818
- F11-4: 4cd8c4b6d981eee313111273bf4ef108ae42ab5b
- F11-5: 6c85d9e4e5f16d038a1d72ef9443cb4ceb24b037
- F11-6: 1d3e906d5f7bc818fa7cc652c8803cec72953276

r002 FAST 46146091734 failed at the old shared-worker structural assertion. r003 supersedes that candidate and preserves the two-worker/two-queued-job bound, all other guards and provider cancellation tests. Exact isolated blobs and disjoint paths. No skipped CI or direct official promotion. Corrected exact FAST/FULL and verified baseline required. Device tests and actual independent evaluation remain NOT_RUN. Voice and cloud changes do not imply full duplex or guaranteed end-to-end secure erasure.
