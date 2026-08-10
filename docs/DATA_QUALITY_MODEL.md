# Data Quality Model

Phase 8A adds a thin, shared presentation layer over TesMano's existing derived and estimated
analytics. It never recomputes a metric and never changes backend, sync, or detail-fetch
behavior; it only describes the provenance, availability, and (where a domain-specific model
already exists) confidence of a result already produced by `BatteryAnalyticsCalculator`,
`RealWorldRangeCalculator`, `RecurringRouteDetector`, or `ChargingCostAnalyticsCalculator`.

All shared types live in `domain/DataQuality.kt`.

## Vocabulary

- `MetricSemantic` — `MEASURED` (reported directly by the vehicle/backend), `DERIVED`
  (calculated directly from recorded values), or `ESTIMATED` (modeled from historical or
  partial data). Promoted from the Battery-only `BatteryMetricKind` enum introduced in Phase 6.
- `MetricAvailability` — `AVAILABLE`, `PARTIAL`, or `UNAVAILABLE`. Unknown is never zero.
- `QualityReason` — a structured, localizable reason a metric is partial or unavailable
  (`INSUFFICIENT_HISTORY`, `MISSING_ENDPOINTS`, `MISSING_RATE`, `MISSING_ENERGY`,
  `INCOHERENT_ENERGY`, `INSUFFICIENT_SOC_SPAN`, `MISSING_CAPACITY_ESTIMATE`,
  `INSUFFICIENT_DRIVE_COVERAGE`, `SOURCE_NOT_RETAINED`, `SOURCE_MISSING_OR_ZERO`,
  `MIXED_CURRENCY`, `DETAIL_NOT_CACHED`, `NO_MATCHING_SMART_PLACE`, `NO_VALID_SAMPLES`).
  Enum names are never shown to users; `ui/components/QualityPresentation.kt` maps each to a
  localized string.
- `MetricCoverage(numerator, denominator)` — an optional `X of Y` count with a derived
  `percent`. Used wherever coverage, not confidence, is the correct concept.
- `MetricQuality(semantic, availability, confidence, coverage, reason)` — the composed result
  attached to a metric. Not every field applies to every metric.

## Confidence versus coverage

TesMano deliberately does **not** have a universal 0–100 confidence score. `BatteryConfidence`
(`LOW`/`MEDIUM`/`HIGH`, `domain/BatteryAnalytics.kt`) is the one cross-feature confidence model,
reused as-is by Real-World Range (`RealWorldRange.quality()` combines capacity confidence and
driving-efficiency confidence via `minOf`). Recurring routes and charging cost intentionally use
**coverage** instead of confidence — occurrence-count/session-count thresholds are a different
evidence model than sample dispersion, and inventing a confidence score for them would be
misleading. Smart Place matching stays a plain deterministic match; no confidence badge is
shown because `SmartPlaceMatcher` does not currently detect overlapping-radius ambiguity.

## Availability and reason

`quality()` extension functions on the existing result types compute `MetricAvailability` and
`QualityReason` purely from fields those types already expose — no new computation:

- `BatteryCapacityEstimate.quality()` — `UNAVAILABLE` with a reason drawn from whichever
  `CapacityExclusion` dominates (`INSUFFICIENT_SOC_SPAN`, `MISSING_ENERGY`, or
  `NO_VALID_SAMPLES`) when no estimate exists; otherwise `AVAILABLE` with coverage
  `accepted / (accepted + excluded)`.
- `RealWorldRange.quality(efficiency, capacityAvailable)` — `MISSING_CAPACITY_ESTIMATE` when
  there is no usable-capacity estimate, `INSUFFICIENT_DRIVE_COVERAGE` when the selected window
  lacks efficiency confidence, otherwise `AVAILABLE`.
- `RouteCoverage.quality()` (and `RouteCoverage.state(): RouteCoverageState`) — distinguishes
  `NO_ENDPOINTS` (nothing cached yet), `PARTIAL_COVERAGE` (some but not all drives have known
  endpoints), and `FULL_COVERAGE_NO_PATTERN` (complete coverage, genuinely no repeated route).
  This is what lets Recurring Routes show three different empty states instead of one generic
  one.
- `ChargingAnalyticsSnapshot.quality()` / `unavailableReasonBreakdown()` — `PARTIAL` when any
  session is unavailable or currencies are mixed; the dominant per-session reason
  (`MISSING_RATE` or `MISSING_ENERGY`) is derived from `ChargeCostPresentation` without
  re-deriving cost.

## Data Quality Center

`ui/screens/dataquality/DataQualityScreen.kt`, reached from Settings → Data quality, summarizes
Drives, Charging, Battery, and Range health for the selected car. `DataQualityRepository`
assembles the summary purely from cached summaries and the same calculators listed above (one
`RouteTagsRepository.recurringRoutes()` call reused for both the endpoint-coverage number and
the drive-eligibility number, so this screen and the Recurring Routes screen always agree). It
is read for a car via `Screen.DataQuality` (no `carId` route argument — the ViewModel resolves
the current car the same way `DashboardViewModel` does: last-selected car falling back to the
first car). Cover renders as a single column of compact cards; unfolded renders as two panes
(Drives/Charging on the left, Battery/Range/Limitations on the right).

## No bulk backfill

Nothing added in Phase 8A fetches a Drive or Charge detail, lowers the recurring-route
occurrence threshold, or fabricates a route/estimate to make coverage look better. Partial
coverage is intentional, architecture-driven, and stays visible rather than being "fixed" by
history-wide detail crawling. See `RECURRING_ROUTE_INTELLIGENCE.md` for why endpoint coverage is
naturally sparse until drives are opened individually.
