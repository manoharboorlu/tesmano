# TesMano Phase 0 Engineering Audit

Audit date: 2026-08-09. Audited source: MateDroid current upstream `9333a14521e232147df4f47e72cccff68531ad10`, plus TesMano setup commit `a7c81df53451478aef1d3be32a58de25a64cf263`.

## Executive conclusion

MateDroid is a viable TesMano foundation, and current upstream is a better starting point than the 1.10.0 tag. Its Compose/Hilt/Retrofit/Room/WorkManager structure is understandable, its v1 backend surface is read-only, and its post-release performance fixes are material.

The foundation is not release-ready for TesMano yet. Phase 1 must establish correct instant/timezone handling, independent vehicle appearance dimensions, bounded/paged historical sync, explicit API compatibility/error handling, secure credential/TLS policy, and regression fixtures. The Galaxy Z Fold 8 Ultra requirement is also a real architectural gap: the code has no fold posture tracking, WindowManager/adaptive-layout dependency, canonical window-size policy, or two-pane navigation. Current unfolded behavior is predominantly a stretched single pane.

No Kotlin/Compose application source was changed in Phase 0.

### Phase 0 verification

| Gate | Result |
|---|---|
| `./gradlew test` | PASS — 77 tests, 0 failures/errors/skips |
| `./gradlew lint` | PASS — existing Gradle deprecation notice only |
| `./gradlew assembleDebug` | PASS |

The shell required the already-approved JDK 17/Android SDK environment recorded in `.vscode/tasks.json`; the first bare test command stopped before Gradle because `JAVA_HOME` was absent. It made no repository change and is not a test failure.

## 1. Architecture map

### Technology snapshot

- Android application, min SDK 28, compile/target SDK 36, Java/Kotlin JVM 17.
- Kotlin 2.3.20, AGP 9.1.1, Compose BOM 2026.03.01 and Material 3.
- Hilt for application graph/ViewModels/workers; typed Navigation Compose routes.
- Retrofit 3, OkHttp 5 and Moshi codegen for network data.
- Preferences DataStore for settings and small state; Room v12 for history-derived data.
- WorkManager for historical sync, geocoding, TPMS, charging and widgets.
- OSMDroid for maps, Nominatim for reverse geocoding, Open-Meteo for trip weather.
- Glance for the home-screen widget.

### Layer and ownership map

| Layer | Current responsibility | Audit finding |
|---|---|---|
| Compose UI | 20 typed destinations, charts/maps, dashboard and widgets | Feature-rich but large screen files; no foldable/adaptive pane system |
| ViewModels | Fetch/transform state and prepare chart/list models | Generally clear; several paths own in-memory full-history lists and independent network calls |
| Domain | trips, comparisons, route simplification, car images, units and day boundaries | Valuable separation, but time is modeled as local wall time and vehicle dimensions are coupled |
| Repositories | TeslaMate reads, stats, geocoding, weather, sentry and TPMS | `TeslamateRepository` centralizes transport/fallback; error/version contract needs hardening |
| Local data | Room summaries/aggregates/trips/caches; multiple DataStores | Mixes disposable cache with irreplaceable user state in one backed-up DB |
| Sync | launch-time full summary sync, per-record detail sync, geocode queue | Resumable at aggregate level but unbounded for years of history and not true incremental sync |
| Network | Dynamic cached clients for primary/secondary URL and auth | Read-only API, redacted debug headers; plaintext secrets and release-insecure options remain |
| External backend | TeslaMateApi PostgreSQL/MQTT adapter | Timestamps are RFC 3339 in API `TZ`; history endpoints page/filter, detail arrays are unpaged |

Source distribution is UI-heavy: 81 Kotlin UI files, 32 local-data files, 14 domain files, eight API/model files, six repositories, plus sync, widget, notification and service code.

### Current car status flow

```text
DashboardScreen
  → DashboardViewModel
  → TeslamateRepository
  → TeslamateApiFactory (Settings DataStore → URL/auth/TLS)
  → GET /api/v1/cars + GET /cars/{id}/status
  → Moshi CarsResponse/CarStatusResponse
  → StateFlow<DashboardUiState>
  → Compose dashboard
```

Status is not persisted in Room. The initial load also reads image overrides/selected car, fetches global settings, uses car-response counts, gets a local trip count/latest trip, and may reverse-geocode the current position. While Dashboard is visible it polls status every five seconds. Lifecycle pause/resume is present in current upstream. Widget, TPMS and charging workers run their own `cars`/`status` requests on separate schedules, so there is no shared status snapshot or request coalescing.

### Historical drive flow

There are two distinct paths:

1. Drives list/detail UI reads TeslaMateApi directly. A selected date range becomes device-local RFC 3339 boundaries, `GET /drives` returns an in-memory list, and the ViewModel filters, summarizes and bins charts locally. Detail calls `GET /drives/{id}` and holds its complete position array for maps/charts.
2. Stats/trips background sync calls `GET /drives?page=1&show=50000`, maps every item into `drives_summary`, then calls one unpaged detail endpoint for every record lacking the current aggregate schema. It stores compact aggregates and route data, not the raw position array. Stats DAOs, trip detection and comparisons then read those tables.

The direct UI path does not benefit from the Room summary cache. The background cache therefore duplicates summary acquisition while enabling stats, country/region, mileage, trips and record calculations.

### Historical charge flow

Charges mirrors drives: the list/detail/comparison UI reads API data directly, while sync fetches up to 50,000 charge summaries into `charges_summary` and fetches each detail array to compute AC/DC, power, temperature, connector and country aggregates. Charge costs are server-backed. “Edit cost” opens the TeslaMate web UI; the app performs no write.

## 2. Local data and migration decision

### Cache / recreatable

| Store | Contents | Rebuild source |
|---|---|---|
| `drives_summary`, `charges_summary` | Completed-history list records | TeslaMateApi list endpoints |
| drive/charge detail aggregates | elevation, temperatures, power, AC/DC and point counts | TeslaMateApi detail endpoints; charger brand/connector are intended but currently lost by a JSON nesting mismatch |
| `sync_state` | progress/schema flags | Reset/recomputed |
| geocode cache/queue/progress | 0.01-degree reverse-geocode results and work state | Detail coordinates + Nominatim |
| trip route/country caches | compressed route segments/country sequences | Drive details + geocoding |
| auto-detected saved trips | persisted detector output | Drive/charge summaries plus detector rules, if no edits consumed their fingerprints |
| trip count DataStore | dashboard shortcut | Trips repository |
| Glance widget state | most recent display snapshot | Cars/status APIs and settings |
| OSMDroid tile cache | 100 MB max, trim to 80 MB, seven-day expiration | Map tile provider |
| Open-Meteo weather results | in-memory/file cache behavior in repository | Open-Meteo archive API |

Raw drive positions and raw charge points are not stored in Room. Recreating aggregates requires downloading all relevant details again.

### User-created / non-recreatable

| Store | Contents | Loss impact |
|---|---|---|
| saved trips/legs/fingerprints | manual trips, membership edits, merges, renamed/edited auto trips | User intent and names cannot be derived from TeslaMate |
| sentry alert log | locally detected sessions, times, coordinates and address | TeslaMateApi has no equivalent history endpoint |
| Settings DataStore | URLs, credentials, currency, short-entry preference, selected car, image overrides and notification prompt | Requires reconfiguration; image choice is user intent |
| charge-session state | whether an active/completed session was DC | Transient notification behavior |
| sentry and TPMS DataStores | counters/session/warning/last-check state | Local notification history/state |

`fullResetSync(carId)` deletes summaries and detail aggregates and resets sync state. It does not delete saved trips, geocode/route/country caches, sentry history or settings. A clean install/clear-data is broader.

### User migration decision

The user has not manually created, renamed, merged, or edited trip membership. Therefore there is no trip intent requiring migration. Auto-detected trips can be recreated from a clean, correctly normalized historical resync. Phase 1 may intentionally rebuild history cache instead of migrating the current Room summaries/aggregates.

That conclusion does not make all app data disposable. Preserve or explicitly export/re-enter settings, credentials, per-car image overrides, and any desired local sentry history. A cache rebuild should target cache tables, not indiscriminately clear application data.

The Room database has explicit migrations 1→12, but the provider also enables `fallbackToDestructiveMigration(dropAllTables=false)`. That fallback is unsuitable for release reliability, particularly because cache and user-created tables share the database. There are no Room migration tests.

## 3. Timezone root cause (release-critical)

### End-to-end trace

1. TeslaMate stores PostgreSQL timestamps without a zone using a UTC convention.
2. TeslaMateApi parses the database string with a literal-`Z` UTC layout, converts the instant into its container `TZ` (default `Europe/Berlin`), and emits RFC 3339 with `Z` or an explicit numeric offset.
3. Moshi retains that timestamp as a `String`.
4. `TeslamateRepository` passes the string unchanged.
5. Sync stores the same string in Room `TEXT` columns. Direct drive/charge UI skips Room but receives the same value.
6. `parseIsoDateTime` calls `OffsetDateTime.parse(value).toLocalDateTime()`. This discards the offset without converting the instant to the Android/device zone.
7. Domain calculations and Compose format that offset-free `LocalDateTime` as if it were already local.

The exact defect is step 6. Offset presence is correctly recognized and then intentionally erased. Correct behavior is to preserve `Instant` (or epoch milliseconds), and only call `instant.atZone(displayZone)` at presentation/calendar-boundary time.

If TeslaMateApi `TZ=America/New_York` and the device is also in America/New_York, current output appears correct by accident because the server already supplied New York wall time. If the API emits UTC or another zone, the app displays that zone's wall clock as local. The same loss corrupts dates, grouping, trip gaps, weather interpolation, chart bins and ambiguous fall-back times—not only text formatting.

Room introduces additional hazards: raw offset strings are sorted/range-compared lexically in several queries, while SQLite `strftime`/`julianday` apply their own offset behavior. Stats boundaries are often timezone-free strings. Those representations are not a single documented time model.

### America/New_York cases

| Case | Correct local result | Current result if API emits UTC | Consequence |
|---|---|---|---|
| Winter: `2026-01-15T01:30:00Z` | Jan 14, 8:30 PM (`-05:00`) | Jan 15, 1:30 AM | Five hours late and wrong day |
| Summer: `2026-07-04T03:30:00Z` | Jul 3, 11:30 PM (`-04:00`) | Jul 4, 3:30 AM | Four hours late and wrong day |
| Spring: `06:59Z` → `07:00Z` on Mar 8 | 1:59 AM EST → 3:00 AM EDT | 6:59 → 7:00 | Invents a local 2 AM hour and wrong duration context |
| Fall: `05:30Z` and `06:30Z` on Nov 1 | Two different 1:30 AM instants (`-04`, `-05`) | 5:30 and 6:30; or both collapse to 1:30 if API emits NY offsets | Ordering/duration cannot be safely reconstructed after offset loss |
| Near UTC midnight: `00:30Z` in summer | Prior local day, 8:30 PM | Current UTC date | Wrong daily bucket |

Use `America/New_York`, never fixed `EST`; the named zone supplies winter/summer and transition rules.

### “Today” filtering is a separate concern

`LocalDayBoundaries` uses `LocalDate.now()` and the device `ZoneId`, computing the offset independently at midnight and 23:59:59. TeslaMateApi parses those RFC 3339 boundaries to UTC before SQL. This is directionally correct and DST-aware; the existing Madrid spring-forward test verifies different start/end offsets.

Remaining defects/gaps:

- no New York winter, summer, spring, fall, midnight or API round-trip tests;
- API drive filtering requires `start_date >= start` and `end_date <= end`, so a drive crossing midnight belongs to neither day's result;
- end-of-day uses an inclusive last second rather than the safer next-day exclusive boundary;
- displayed/grouped dates still use the broken offset-dropping parser;
- Room year/day range queries do not share this boundary helper.

### Recommended Phase 1 time model

- Parse every RFC 3339 field as `OffsetDateTime`, immediately convert to `Instant`, and preserve raw text only for diagnostics.
- Store epoch milliseconds (indexed integer) or a documented UTC string in Room; never compare mixed offset/local strings.
- Convert to `ZonedDateTime` only at display/calendar operations, with an explicit policy: system zone by default and `America/New_York` in the user's current deployment/test matrix.
- Build day ranges as `[date.atStartOfDay(zone).toInstant(), nextDate.atStartOfDay(zone).toInstant())`; query a sufficiently broad server range if needed and apply the final start/end membership rule locally.
- Rebuild recreatable history cache after schema normalization, while retaining user-created tables/settings.
- Test durations/order as instants and labels/buckets as zoned calendar values.

## 4. Vehicle/image root cause (release-critical)

### User configuration

Legacy/pre-Juniper Model Y Performance; PN01 Stealth Grey; P74D; 19-inch dark Gemini replacement wheels; red calipers and Performance spoiler. Trim does not imply current wheels.

### Current coupling defects

`CarImageResolver` first maps color, trim and wheel into one variant (`my`, `myjs`, `myj`, `myjp`), then chooses only wheels/colors considered legal for that variant.

- PN01 is in `HIGHLAND_JUNIPER_COLORS` and absent from `LEGACY_MY_COLORS`; therefore PN01 alone asserts Juniper.
- P74D only means Performance in code, but in combination with any asserted Juniper signal it selects `myjp`. P74D is not generation-unique and cannot prove Juniper.
- `Uberturbine21`/any `21...` is treated as a Juniper Performance wheel. Legacy Model Y Performance also used 21-inch Überturbine, so it is not generation-unique.
- Legacy resolver maps `Uberturbine21` to the generic `WY20P` fallback. The asset-fetch script mentions legacy `WY1S`, but no `my_*_WY1S.png` is currently bundled and the picker does not expose it.
- Performance variant selection forces the Juniper 21-inch asset even when the actual Performance car wears Gemini wheels.
- PN01 has no bundled legacy `my_PN01_*` asset. The script and `ASSETS.md` incorrectly define it as new-generation-only for Model Y.
- Dashboard validates an override against heuristic-filtered picker variants. For PN01 it rejects `my` before rendering. The widget does not perform the same validation, but `getAssetPathForOverride` still validates PN01 against legacy colors and silently changes it to white. Thus manual override does not reliably override automatic inference, and Dashboard/widget behavior differs.
- `CarImageOverride` stores only `variant` and `wheelCode`; it cannot independently preserve generation, trim appearance, brake/spoiler appearance and wheel choice.

### Required decision table

“Legacy”/“Juniper” in the scenario column represents known real-world truth; the current function does not receive generation.

| Scenario/input | Current resolved asset | Correctness |
|---|---|---|
| Legacy MY + PN01 + P74D + Gemini19 | `myjp_PN01_WY21A.png` | Wrong generation and wheels; PN01 asserts Juniper, P badge asserts Performance, Gemini cannot map to `myjp` and defaults to 21-inch |
| Legacy MY + PN01 + P74D + Uberturbine21 | `myjp_PN01_WY21A.png` | Wrong generation; all three heuristics reinforce Juniper Performance even though trim/wheel are legacy-compatible |
| Legacy MY + PPSW + P74D + Uberturbine21 | `myjp_PPSW_WY21A.png` | Wrong generation; `Uberturbine21` alone is treated as Juniper evidence and combines with P74D |
| Legacy MY + DeepBlue + Gemini19 | `my_PPSB_WY19B.png` | Correct legacy inference because PPSB and Gemini are classified legacy |
| Juniper MY + PN01 + Crossflow19 + `74` | `myj_PN01_WY19P.png` | Correct Premium Juniper for these signals; generation remains heuristic |
| Juniper Performance + PB02 and/or Juniper-exclusive 21-inch signal + P trim | `myjp_PB02_WY21A.png` (color depending on input) | Correct when the signal is truly Juniper-exclusive |

For PN01/P74D/Gemini, the picker offers Standard and Premium based on color and may initialize the hidden `myjp` detected default; it never offers legacy. A stale legacy override is ignored. This proves the override is subordinate to the heuristic rather than authoritative.

### Proposed generic model (do not implement in Phase 0)

```text
VehicleAppearance
├── VehicleGeneration: LEGACY | HIGHLAND | JUNIPER | UNKNOWN
├── VehicleTrim: STANDARD | LONG_RANGE | PERFORMANCE | UNKNOWN
├── WheelConfiguration: family/code/diameter/color, independent of trim
├── BrakeConfiguration: STANDARD | RED_PERFORMANCE | UNKNOWN
├── SpoilerConfiguration: NONE | FACTORY_PERFORMANCE | CUSTOM | UNKNOWN
└── AppearanceOverride
    ├── optional generation
    ├── optional trim/body treatment
    ├── optional wheel
    ├── optional brakes
    └── optional spoiler
```

Resolution should keep evidence provenance/confidence per dimension. Model, VIN/model year or explicit user choice can establish generation. Trim badging establishes trim, not generation or wheels. Wheel telemetry establishes the current wheel only when trustworthy; the user may override it independently. A manual value must win for its dimension until explicitly reset, and both Dashboard and widget must consume the same resolved immutable appearance.

Phase 1 also needs a real legacy PN01 asset strategy and a dark-Gemini representation. Asset generation/validation must detect placeholders visually/hash-wise, not only by size, and resolver tests must verify every supported path actually exists.

## 5. Performance audit

### HIGH

| Finding | Current behavior / why it matters | Likely remediation | v1? |
|---|---|---|---|
| Full-history summary refresh | Every app launch enqueues sync; every car fetches page 1 with `show=50000`, maps and upserts the complete drive and charge history. It is not incremental and silently truncates beyond 50k | Page in bounded batches, persist high-water marks, reconcile deletes separately, use server date filters, expose cancellable progress | Yes |
| N+1 deep sync and huge decoding | One unpaged detail request per unsynced drive/charge, batches of 10 concurrent with only 10 ms between batches. Drive payloads can exceed 15 MB; years can mean thousands of requests and heavy API/Postgres/RAM use | Backpressure with small adaptive concurrency, checkpoint every record/batch, stream/limit fields if server permits, defer deep stats, prioritize recent/on-demand data | Yes |
| Direct list screens bypass cache/paging | Drives/charges/updates load complete requested results into memory; all-time can be 50k, then filter/summarize/bin and render from lists | Room-backed paging or bounded API paging, use server filters, incremental chart aggregation and stable item keys/content types | Yes |
| Whole-history trip/comparison/stat work | Trip repository loads all drives, all charges and DC aggregates, runs detector/fingerprint maps, and may auto-persist. Comparisons load/prepare full curves. Current upstream moves CPU off main but does not bound memory/CPU | Indexed range queries, incremental trip materialization, precomputed normalized series, suspend/cancel on screen changes | Yes for trip/count launch path |
| Full-resolution chart/map point paths | Detail screens decode all telemetry; charts/maps prepare/draw large arrays. Route simplification and paint memoization help, but detail payload and point count remain unbounded | Downsample by viewport/metric, simplify off main with error tolerance, cache levels of detail, retain exact points only for inspection | Yes for Galaxy Fold chart/map usability |

### MEDIUM

- Dashboard polls status every five seconds while visible, while widget (self-rescheduling plus 15-minute fallback), charging and TPMS workers independently fetch cars/status. Coalesce into a process-level status repository with freshness/active-consumer policy.
- Launch sync uses unique work `REPLACE`, despite the comment claiming `KEEP`; reopening can replace queued/running work and repeat setup. Define explicit resume semantics.
- Nominatim processes one location per second on a 0.01-degree grid; first multi-year sync can take hours and competes with sync. Coarser/deduplicated/server-address-first and user-visible deferred geocoding are preferable.
- Stats performs many separate aggregate queries per load. Off-main execution prevents jank but SQL round trips and scans remain; consolidate measured hot groups and add query-plan/large-fixture benchmarks.
- UI list date changes launch network work without a documented cancellation/latest-wins policy in all screens; stale work can waste bandwidth or race state.
- Current-charge polling concurrently requests current detail and status; the detail array grows during charging. Prefer a lightweight current summary/cursor if the backend can support it, or throttle/adapt.

### LOW / observe

- Several very large Compose files increase maintenance/recomposition risk, but size alone is not a runtime defect.
- Image picker decodes asset bitmaps per visible option with `remember`; the list is small. Optimize only after profiling.
- API-client cache keys hold credential strings in memory. The cache is capped at four; security design matters more than micro-optimization.

Current upstream already improved lifecycle collection, moved stats/comparison/trip CPU off main, hoisted chart paints, added list content types, simplified routes, and removed a per-charge DAO lookup. Keep those changes, then measure on the real folded and unfolded Galaxy Z Fold 8 Ultra with a production-scale anonymized fixture.

## 6. Foldable readiness

The repository has no Jetpack WindowManager/adaptive dependency or use of `FoldingFeature`, window size classes, canonical adaptive layouts, list-detail panes, hinge occlusion or posture transitions. A few components use `BoxWithConstraints` for chart geometry and detail screens read `LocalConfiguration` for fullscreen orientation; that is component sizing, not a foldable experience.

Consequences:

- Folded and unfolded are not distinct information architectures.
- Unfolded screen width generally stretches single-column scaffolds and charts.
- Navigation cannot present list/detail or map/metrics simultaneously.
- Hinge state/transitions are not modeled or preserved.
- No tests cover window-size changes, posture, state retention, touch targets or widget appearance on the primary device.

Phase 1 should define reusable window/posture state and keep screen logic independent from pane placement. Phase 7 should implement and validate first-class compact and unfolded experiences. The real Galaxy Z Fold 8 Ultra remains final authority for layouts, transitions, charts, maps, performance, widget, typography, animation and touch targets; emulators are regression aids only.

## 7. Feature inventory and disposition

| Feature | Disposition | Evidence-based direction |
|---|---|---|
| Dashboard/current state | IMPROVE | Keep live battery/climate/lock/location/TPMS value; reorganize requests and build compact/unfolded compositions |
| Multiple vehicles/car picker | KEEP | Preserve selected car and counts; improve adaptive selector and per-car state |
| Vehicle image + picker | IMPROVE | Replace coupled heuristics with independent appearance model and authoritative overrides |
| Current charge + notifications | KEEP | Valuable v1.24+ feature; improve capability/error handling and growing-detail polling |
| Drives/list/detail | KEEP | Core; add paging, canonical time, point downsampling and fold-aware list/detail |
| Drive comparison | KEEP | Valuable; preserve current interaction fixes and bound curve work |
| Charges/list/detail | KEEP | Core; add paging/time correctness and clearer server/local cost ownership |
| Charge comparison | KEEP | Valuable; add calculation/fixture tests and bounded curves |
| Trips/auto detection | IMPROVE | Preserve; make incremental, timezone-correct and explicit about local ownership |
| Manual trip creation/edit/merge/rename | OPTIONAL | Powerful but local-only and migration-sensitive; retain after correctness/core activity, with export/backup design |
| Battery health | IMPROVE | Keep but explain server-derived estimate/zero-insufficient-data semantics; add math/contract tests |
| Stats/records/countries/regions | REORGANIZE | Valuable analytics; reduce query fan-out and organize for folded/unfolded exploration |
| Mileage hierarchy | KEEP | Strong existing drill-down; memoize/paginate and use canonical dates |
| Software history | KEEP | Simple server-backed feature; paginate and normalize time |
| Where Was I? | OPTIONAL | Useful history lookup; privacy-sensitive and dependent on canonical time/routes |
| Sentry history | OPTIONAL | Local-only and notification-derived, not authoritative TeslaMate history; clearly label limitations |
| Maps/routes/geocoding | IMPROVE | Keep OSMDroid/open services; two-finger behavior is good, add fold panes/cache/privacy/limits |
| Home widget | KEEP | Primary-device requirement; unify image/status resolver and test actual launcher sizes |
| Settings, units, localization | KEEP | Six resource locales (default, Catalan, German, Spanish, Italian, Chinese); fix secure storage/auth ambiguity and unit contracts |
| Historical export | IMPROVE | No general user export found. Add later for user-created trips/settings portability, with credential exclusion |
| Sync/debug UI | REORGANIZE | Debug sync logs/palette and endpoint receiver are useful development tools; keep strictly debug-only and create user-safe sync status |
| Vehicle controls/Fleet API | DO NOT CARRY FORWARD (v1) | Violates the read-only v1 boundary and expands security/safety scope |

No other existing feature has evidence strong enough for permanent removal. “Optional” means schedule after the reliability/core experience, not delete automatically.

## 8. Security audit

### Credential trace

| Value | Storage | Backup | Network/log/build behavior |
|---|---|---|---|
| primary/secondary server URL | plaintext Preferences DataStore in app sandbox | entire settings file excluded from cloud backup and device transfer | URL and network exception messages may appear in debug logs; HTTP is globally allowed |
| Basic username/password | same plaintext DataStore | excluded | encoded into `Authorization`; header redacted in debug HTTP logs; two auth headers possible |
| API token | same plaintext DataStore | excluded | sent as Bearer to all reads; header redacted; not in BuildConfig |
| invalid-cert flag | same DataStore | excluded | when enabled, trusts every certificate and hostname in debug and release |

`BuildConfig` contains version information and Git SHA, not credentials. `.env`, `local.properties`, `*.jks` and `*.keystore` are ignored. Phase 0 setup tracks no private files.

### Findings

- `android:usesCleartextTraffic="true"` applies to every build and host; there is no network-security XML.
- Trust-all TLS/hostname verification is available in release.
- Credentials are not encrypted or Android-keystore-backed. App sandbox is the only at-rest protection.
- Backup rules correctly exclude `datastore/matedroid_settings.preferences_pb` for legacy cloud backup and API 31+ cloud/device transfer. Other Room/DataStore files, including local history/sentry/trips, remain eligible.
- Debug OkHttp logging is HEADERS only and redacts `Authorization`; response bodies are deliberately omitted. Release installs no HTTP logger.
- Repository logs exception text and server URLs but does not explicitly log credentials. TeslaMateApi's own debug mode serializes successful response data server-side and logs request URI; location/history can therefore enter server logs. Query tokens would enter URI logs, although MateDroid uses headers.
- Adding both Basic and Bearer `Authorization` headers is ambiguous and may expose unexpected behavior at a proxy.
- GitHub workflows source signing/service-account material from secrets, but Gradle and workflows fall back to the debug keystore when release secrets are absent. A release artifact can therefore be debug-signed instead of failing closed.
- WorkManager minimum log level is DEBUG for all builds; not currently a credential leak, but release logging should be deliberate.

### Required release posture

Use Android-keystore-backed encrypted credential persistence; exclude all credential material from every backup/transfer path; enforce HTTPS in release with a narrow debug-only local HTTP policy; remove trust-all TLS from release; make auth schemes explicit; retain header redaction and add structured URL/query redaction; and fail release signing when production secrets are absent.

## 9. Test coverage audit

### Existing local unit tests (77 tests in 11 classes)

| Area | Tests | What is actually covered |
|---|---:|---|
| Settings ViewModel | 14 | initial settings, URL/token state, URL validation, primary/secondary connection outcomes, save and invalid-cert state |
| Trip country sequence | 12 | ordered/deduplicated country paths including loops and a Germany–France–Spain example |
| Widget layout | 10 | which facts appear at 1×1 through 3×2, charging vs non-charging |
| Dashboard ViewModel | 10 | car loading/selection/status refresh/error/retry behavior |
| Trip detector | 9 | drive/DC-charge thresholds, gaps, minimum 300 km, micro-drive filtering |
| Current charge ViewModel | 5 | startup/no-active transitions, network/status failures and exit behavior |
| Widget display mapping | 5 | battery/status null handling and AC/DC phase interpretation |
| Boot receiver | 4 | scheduling critical charging/TPMS workers and ignoring other intents |
| Local day boundaries | 4 | positive/negative/UTC offsets and Madrid spring-forward boundary offsets |
| Car image resolver | 3 | Diamond Black aliases and one Juniper Premium black asset path |
| Hardcoded unit labels | 1 | static scan preventing hardcoded unit text in UI sources |

### Existing instrumented/Compose tests

- `CarWidgetClickActionTest`: two Glance unit/instrumentation tests for root tap with/without car ID.
- `WidgetPreviewGeneratorTest`: one device-side bitmap generation utility/test.
- There are no broad screen Compose tests, end-to-end navigation tests, API integration tests, Room database/migration tests, benchmark tests or baseline profiles.

The requested `./gradlew test` gate runs local unit tests, not the two instrumented classes; those require a device/emulator task.

### Priority gaps

1. Time: RFC 3339→instant→New York display, winter/summer, both DST transitions, local/UTC midnight, trip gaps, chart bins, Room ordering/ranges, and Today server semantics.
2. Vehicle: the six decision-table cases, authoritative override, Dashboard/widget parity, asset-exists matrix, legacy PN01/P74D/replacement wheels, independent brake/spoiler behavior.
3. API: v1.25 fixtures for all endpoints, HTTP-200 error envelopes, null old data, empty responses, API version/capabilities, pagination and large arrays.
4. Room: every 1→12 migration path, foreign keys/user-created trip preservation, cache reset boundaries and destructive-fallback prevention.
5. Sync: pagination, restart/idempotency, per-record failure, cancellation/REPLACE semantics, progress accuracy, >50k and large payload backpressure.
6. Analytics: charge/drive comparison calculations, battery-health interpretation, stats queries and unit correctness.
7. Foldable/UI: folded/unfolded/posture state retention, list-detail navigation, hinge avoidance, charts/maps gesture arbitration, touch targets and screenshots on real hardware.
8. Performance: macrobenchmarks, startup, scroll/chart frames, memory and baseline profiles using multi-year fixtures.

## 10. Top Phase 1 actions

1. Lock the baseline at `9333a145…` and add API/time/vehicle fixtures before behavioral changes.
2. Introduce canonical instant and display-zone boundaries; migrate/rebuild only recreatable history.
3. Replace car variant heuristics with independent appearance evidence and an authoritative per-dimension override; add the required legacy PN01/dark-Gemini assets.
4. Introduce bounded API pagination and incremental sync checkpoints; fix error envelopes/version capability handling.
5. Separate disposable cache from user-created data and remove destructive release fallback.
6. Establish secure credential/TLS/release-signing policy early enough that later screens do not depend on insecure settings APIs.
7. Define foldable window/posture abstractions and test contracts in Phase 1, while reserving full adaptive UI implementation for Phase 7.

See `BASELINE.md`, `TESLAMATE_API_CONTRACT.md`, and `TESMANO_PHASE_PLAN.md` for the commit decision, endpoint contract and sequenced delivery plan.
