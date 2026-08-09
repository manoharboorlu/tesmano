# TesMano Time Model

## Phase 1A status

TeslaMateApi RFC3339 timestamps carry an absolute instant and offset. MateDroid currently reduces those values to local wall time in shared parsing, then uses that representation across display, trip gaps, charts, weather interpolation, and cached-history queries.

This remains deferred in Phase 1A. A safe correction must preserve `Instant` through network, storage, and domain layers; migrate or rebuild timestamp-backed cache where required; use named `ZoneId` rules only for display/calendar operations; and apply day ranges as `[local start, next local start)`. A partial parser-only change risks inconsistent Room comparisons and DST calculations.

The intended primary-zone test matrix is `America/New_York`: winter EST, summer EDT, both DST transitions, and dates near UTC midnight. No fixed `EST` offset is acceptable.
