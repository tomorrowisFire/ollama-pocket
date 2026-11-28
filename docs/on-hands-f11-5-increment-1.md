# F11-5 increment 1 — durable workflow deadline control

Check wall deadline and clock rollback before dispatch and after generation; never accept late output; retain durable cancelled settlement. Retry only if the exact backoff fits the remaining execution window. KPI: zero expired dispatch/accepted late results in fixtures; previous durable runs readable without schema migration. Three single-assistant perspectives: agent execution, transaction safety, user control. Native and independent review pending.
