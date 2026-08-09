# Sync model

TesMano treats TeslaMateApi history as read-only, recreatable cache data. Drive and charge summaries are stored in Room; settings, appearance overrides, credentials, saved trips, and local sentry history are user-owned and are not cache-reset targets.

Initial summary sync uses the documented `page` and `show` list parameters with 250-record pages. Each successfully stored page advances a Room checkpoint. A restart resumes from that page; Room upserts make inclusive boundary records and duplicate records idempotent. A short page completes the traversal. A repeated full page or API failure is an incomplete sync, never a successful truncated history.

After initial completion, the stored latest raw API `startDate` is sent as an inclusive `startDate` filter and only that delta is paged. This avoids downloading years of cached history at every launch. TeslaMateApi exposes no deletion/change cursor, so deletion reconciliation is intentionally deferred rather than guessed.

Ordinary sync fetches summaries only. Detail endpoints can contain large position/telemetry arrays and are reserved for existing explicit detail/analysis paths; they are not bulk-fetched on launch. All Room and network work remains off the main thread.

`fullResetSync` is a cache reset: summaries and derived detail aggregates are deleted, while settings and user-owned records are preserved. The shared database has explicit migrations and no destructive fallback.
