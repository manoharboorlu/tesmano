# TesMano Engineering Phase Plan

Audit date: 2026-08-09

## Delivery principles

- Reliability and data correctness precede visual redesign.
- TeslaMate/TeslaMateApi access remains read-only for TesMano v1.
- The real Samsung Galaxy Z Fold 8 Ultra is the final authority. Folded and unfolded layouts are separate experiences, not one layout at two widths.
- Preserve user-created data; freely rebuild only explicitly classified cache.
- Every phase has a test/performance/security exit gate and a rollback-sized commit series.
- Do not automatically merge a moving MateDroid upstream or blindly upgrade dependencies.

The requested phase sequence is retained, with one safety adjustment: Phase 1 establishes the adaptive-window contract and every feature phase designs both compact and unfolded layouts. Phase 7 completes fold-specific integration rather than discovering foldability after screens have already been redesigned.

## Phase 1 — Correctness foundation

### Phase 1A technical debt

- Timestamp normalization remains deferred: API RFC3339 offsets are still reduced to local wall time in several UI, domain, weather, and Room-adjacent paths. Correcting this safely requires an end-to-end `Instant`/storage migration and range-query review, so it is not being partially changed in Phase 1A.

### Scope

- Lock the TesMano base to MateDroid `9333a145…`; record future upstream intake explicitly.
- Add current TeslaMateApi v1.25 contract fixtures, error-envelope parsing, API-version/capability handling and read-only API guards.
- Introduce canonical `Instant` storage/domain semantics and explicit display `ZoneId`; normalize Today/range filtering and rebuild recreatable cache.
- Separate cache reset/migration from user-created trips/settings/sentry data; remove destructive release migration fallback and add Room migration tests.
- Implement bounded pagination/incremental summary sync and controlled deep-sync concurrency/progress.
- Introduce independent vehicle generation/trim/wheel/brake/spoiler resolution, authoritative overrides, legacy PN01/dark-Gemini assets, and Dashboard/widget parity.
- Define a window/posture state abstraction and adaptive navigation contract without redesigning screens yet.
- Establish HTTPS/release, keystore-backed credential and signing-fail-closed foundations.

### Exit gate

- New York winter/summer/DST/midnight tests pass end to end.
- All six vehicle decision-table cases and asset-existence tests pass.
- API fixtures cover all consumed endpoints, null/error/empty/version/pagination cases.
- Room migrations and cache-only rebuild preserve non-recreatable records.
- Multi-year sync is paged, resumable and bounded; no silent 50,000-record truncation.
- Folded/unfolded/posture state can be injected/tested and survives configuration changes.
- Unit, lint, debug assembly and targeted device tests pass.

## Phase 2 — TesMano identity and responsive design system

### Scope

- Rename product-facing identity and assets; defer application ID migration until signing/data implications are settled.
- Create typography, color, spacing, shape, motion, elevation and chart/map token systems.
- Define compact/unfolded pane widths, touch targets, safe/hinge insets and density rules.
- Build reusable loading/error/empty/offline/sync components and screenshot catalog.
- Meet contrast, dynamic type, screen reader, reduced-motion and localization requirements.

### Exit gate

Design tokens render consistently on the real Fold in both postures; no feature screen owns hardcoded device breakpoints or TesMano identity values.

## Phase 3 — Home/dashboard

### Scope

- Rebuild current-status presentation with a shared freshness-aware status source.
- Treat folded dashboard as focused, one-hand compact UI.
- Treat unfolded dashboard as a composed overview with independently useful secondary panes, not stretched cards.
- Improve multi-car selection, vehicle appearance, charging/TPMS/sentry messaging and offline/stale states.
- Align widget data/image logic with dashboard and validate actual launcher grids.

### Exit gate

Five-second live updates do not duplicate worker requests unnecessarily; posture transitions retain selected car/scroll/dialog state; real-device touch, type, animation and widget review passes.

## Phase 4 — Activity: drives, charges and trips

### Scope

- Paged unified activity navigation with server/cache-backed filters.
- Folded list→detail flows and unfolded list-detail/map-detail panes.
- Drive/charge detail charts and maps with viewport downsampling and gesture arbitration.
- Preserve comparisons and charge-cost web handoff.
- Rebuild timezone-correct incremental auto trips; reintroduce optional manual/edit/merge/rename with explicit local-data portability.

### Exit gate

Years of history scroll smoothly; crossing-midnight/DST records group correctly; charts/maps remain usable in both postures; no raw detail payload is retained unnecessarily.

## Phase 5 — Battery and analytics

### Scope

- Explain battery-health source, confidence and insufficient-data/zero states.
- Reorganize stats, records, mileage, countries/regions and software history around user questions.
- Consolidate measured hot queries and introduce durable aggregate/version policies.
- Create compact summaries and unfolded analytical workspaces with accessible chart alternatives.

### Exit gate

Metrics have definitions, unit/time tests and source provenance; large-history query and chart budgets pass on the primary device.

## Phase 6 — Lab

### Scope

- Introduce an explicitly experimental, read-only Lab behind clear feature flags.
- Prototype non-destructive analytics, visualization and diagnostics only.
- Keep Lab models/components isolated from core correctness and release navigation.
- Prohibit Tesla/Fleet controls, backend writes and production-credential experiments.

### Exit gate

Lab can be disabled without affecting core binaries/data flows; experiments have privacy, performance and deletion rules.

## Phase 7 — Foldable adaptive UI integration

### Scope

- Complete posture-aware navigation/pane integration across every retained feature.
- Add hinge avoidance, tabletop/half-open decisions if reported by actual hardware, transition choreography and state restoration.
- Tune charts, maps, dialogs, pickers and comparison screens as distinct folded/unfolded experiences.
- Validate widget appearance and launcher behavior alongside app layouts.
- Build screenshot/posture regression coverage; use emulators only as supplements.

### Exit gate

No unfolded destination is merely a stretched compact layout. The real Galaxy Z Fold 8 Ultra passes the full layout, hinge transition, chart/map, touch, typography, animation and widget checklist.

## Phase 8 — Cache, sync and performance hardening

### Scope

- Profile and tune incremental sync, deep-stat scheduling, request coalescing, geocoding and cache eviction.
- Add offline/freshness policies and user-facing cache/sync controls.
- Measure Room query plans, network bytes/calls, memory, battery and Compose recomposition/frame time.
- Add bounded retry/backoff and observability with credential/location redaction.

### Exit gate

Production-scale history meets documented startup, sync, memory, network and scroll budgets in both device postures; interruption/restart is idempotent.

## Phase 9 — Benchmarks and baseline profiles

### Scope

- Add Macrobenchmark startup, activity scroll, charts, maps, posture transition and widget update scenarios.
- Generate/verify baseline profiles and release minification behavior.
- Maintain small/typical/multi-year anonymized datasets and performance regression thresholds.

### Exit gate

Benchmarks are reproducible in CI where practical and confirmed on the primary device; profiles are packaged and improve measured critical paths.

## Phase 10 — Release hardening, signing and APK/AAB

### Scope

- Enforce HTTPS-only release traffic, trusted certificates, keystore-backed secrets, backup exclusions and redacted logs.
- Fail closed on missing production signing material; verify key ownership/rotation/recovery.
- Finalize application ID/data migration decision, ProGuard/R8 rules, SBOM/dependency/license/privacy review.
- Run unit, lint, instrumented, Compose, migration, contract, benchmark and real-device acceptance matrices.
- Produce reproducible signed APK/AAB, checksum/provenance, release notes and rollback plan.

### Exit gate

No debug signing/insecure TLS/cleartext/diagnostic endpoints in release; artifacts install/update correctly and pass the full real-Fold acceptance checklist.

## Cross-phase release gates

Every phase should report:

1. exact baseline and upstream intake;
2. changed data/API/security contracts;
3. test/lint/build results and known baseline warnings;
4. folded and unfolded verification evidence where UI changed;
5. performance impact with a realistic history fixture;
6. every changed file and whether the commit was pushed.
