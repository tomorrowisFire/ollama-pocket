# F11-5 r003 — cancellable retry backoff

Split existing bounded 2/4-second retry backoff into at most 100 ms sleep slices. Check user stop or interrupt between slices and return the latest durable checkpoint without reserving another attempt. Preserve interruption and existing wall-clock revalidation. Resume uses the original persisted not-before value; no schema or retry limit change. Fixtures check pause at 100 ms, exact resume and interruption before dispatch. KPI: stop polling every 100 ms during backoff; no extra reservation or duplicated dispatch. Actual Android responsiveness pending.
