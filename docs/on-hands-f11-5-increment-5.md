# F11-5 r005 — exact automation output evidence

Validate the reviewed local result before evidence hashing or workflow admission. Reject unpaired UTF-16 surrogates and unexpected ISO controls; preserve valid multilingual text, normal whitespace, empty/truncated outputs and exact evidence. A malformed generated result settles as uncertain without an evidence digest, accepted output or retry. Tests cover Unicode, limits, exact digest and durable settlement. Design perspectives: workflow evidence, Unicode integrity, fail-closed execution. Device and external independent review pending.
