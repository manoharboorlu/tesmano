# TesMano feature roadmap

This roadmap describes future product work only. It does not authorize implementation ahead of its approved phase.

## Phase 2 — Visual transformation

Complete the TesMano visual identity and the responsive Fold8 Ultra experience, beginning with Home.

## Phase 3 — Activity and Drive/Charge detail redesign

Redesign Activity plus Drive and Charge detail experiences while preserving the established read-only data paths.

## Phase 4 — Smart Places, tags and commute intelligence — complete

- Smart Places and geofences.
- Editable commute rules based on start location, end location, and a configurable radius.
- Manual drive-tag override.
- Generic drive and charge tags.
- Configurable geofence radius.

## Phase 5 — Charging Cost Engine — complete

- Rates by Home, Work, Supercharger, and custom location.
- $/kWh pricing initially, with future flat, session, time-based, and time-of-use pricing.
- Monthly, yearly, and location charging-cost aggregation.

## Phase 6 — Battery Analytics — complete

- Deep Battery Lab with measured, derived, and estimated metric distinctions.
- Degradation, capacity, and range trends.
- AC/DC charging analysis, charge curves, SOC bands, and temperature analysis.
- Since Last Charge metrics.
- Parking and vampire-drain analysis.

## Phase 7 — Advanced Analytics and Lab

### Phase 7A — Recurring routes and advanced tags — complete

- Endpoint-based recurring-route discovery, local user route metadata, and manual multi-tags.
- Smart Place priority, conservative unmatched-endpoint clustering, and Activity filtering.
- Current physical history has limited endpoint coverage; see `RECURRING_ROUTE_INTELLIGENCE.md`.

### Phase 8A — Data quality and confidence — implemented locally, pending approval/push

- Shared MEASURED/DERIVED/ESTIMATED, availability, coverage, and structured-reason model
  reused across Battery, Real-World Range, Recurring Routes, and Charging Cost; see
  `docs/DATA_QUALITY_MODEL.md`.
- Data Quality Center (Settings → Data quality) summarizing local drive/charging/battery/range
  coverage and confidence for the selected car.
- Distinct Recurring Routes empty states (no endpoints / partial coverage / no repeated
  pattern), a compact charging cost unavailable-reason breakdown, and a per-reason battery
  capacity exclusion breakdown.

- Trip maps with charge stops.
- CSV export.
- Advanced Analytics and Lab experiences.
