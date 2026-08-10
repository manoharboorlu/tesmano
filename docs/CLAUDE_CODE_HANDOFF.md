# TesMano Claude Code Handoff

## 1. Current repository state

- Repository: `/Users/boorlu/Developer/tesmano`
- Branch: `develop`
- Local HEAD: `7fbab55783b36474102cec1d06638ec5078825b0`
- Local Phase 7A commit: `feat: add TesMano recurring routes and advanced tags`
- `origin/develop`: `ae8fcc15713380f9f6cc67ec0494688d450b0a42`
- Phase 7A is local and unpushed; `develop` is one commit ahead of `origin/develop`.
- Worktree was clean when this handoff was written.
- `origin` is the TesMano fork; `upstream` is `vide/matedroid`.

Treat `git status`, `git log`, and current source as authoritative if this document becomes stale.

## 2. Product vision

TesMano is a personal Tesla analytics app with a premium dark, data-dense interface. It is a MateDroid derivative, remains read-only, and favors transparent measurements over flashy or invented estimates. The primary real product target is the Samsung Galaxy Z Fold 8 Ultra: cover is intentionally compact; unfolded screens should use deliberate two-pane layouts where they improve the task.

The configured vehicle is **Night Fury**, a legacy/pre-Juniper Model Y Performance with PN01 Stealth Grey paint, 19-inch dark Gemini wheels without aero covers, red Performance calipers, and an OEM Performance spoiler. The user flows are progressive disclosure: **GLANCE → SCROLL → TAP**.

## 3. Architecture

The Android app uses Kotlin, Jetpack Compose/Material 3, Hilt, Room, Retrofit/OkHttp/Moshi, DataStore, WorkManager, Glance widgets, OSMDroid, and AndroidX Window adaptive-layout support.

```text
TeslaMate → TeslaMateApi → Retrofit/repository/domain → Room/local caches
         → transparent derived analytics → Compose UI/widgets
```

`data/api/TeslamateApi.kt` and `data/repository/` own network reads. `data/local/StatsDatabase.kt` owns Room summaries, derived aggregates, and user-owned local records. `data/sync/DataSyncWorker.kt` performs bounded summary sync. Screens load detail only through established detail flows; details can be large and must not be bulk-fetched merely to fill an analytic card.

### Summary versus detail

Drive and charge summaries are the scalable history layer. Existing detail views may fetch one selected detail and cache only compact derived results/endpoints. This distinction protects startup, storage, and backend load:

- `DriveSummary` / `ChargeSummary`: history, lists, period analytics.
- `DriveDetailAggregate`, `ChargeDetailAggregate`, `DriveEndpointCache`: compact local enrichment from an already-opened detail.
- Never schedule a history-wide detail download for a new feature.

### Local feature locations

- Smart Places and commute: `domain/SmartPlaces.kt`, `data/local/entity/SmartPlace.kt`, `SmartPlacesDao`.
- Charging rates/costs: `domain/ChargingCostEngine.kt`, `domain/ChargingCostAnalytics.kt`, `ChargingCostDao`.
- Battery capacity/range: `domain/BatteryAnalytics.kt`, `domain/RealWorldRange.kt`, `ui/screens/battery/`.
- Recurring routes/tags: `domain/RecurringRoutes.kt`, `domain/RouteTagsRepository.kt`, `RouteTagsDao`, `ui/screens/routes/`.

## 4. Non-negotiable backend boundary

Recent phases are local to TesMano. No work has changed TeslaMate, TeslaMateApi, PostgreSQL, Oracle, Tesla Fleet API, or backend authentication/security. Do not use history-wide detail fetches to populate routes, maps, costs, or battery analytics. Incremental local enrichment after a user opens a detail is intentional.

## 5. Completed phase history

- **Phase 1A** (`a89f40c`): API/data correctness foundation, timestamps/units and model correctness.
- **Phase 1B** (`8ec66fe`): bounded resumable summary pagination, secure secret storage/migration, release transport/signing hardening.
- **Phase 1C** (`c1832b4`, `014411c`): TesMano identity, adaptive foundation, existing-server connection flow, Fold policy.
- **Phase 2** (`da76bf1`, `00e9298`, `848406f`): approved Home transformation, shared custom Night Fury vehicle asset and polished hierarchy.
- **Phase 3** (`15ea979`, `45239fb`): Activity redesign; Drive/Charge Detail redesign; more prominent route map treatment. Current contract cannot support accurate historical FSD highlighting.
- **Phase 4** (`0da5c50`): local Smart Places, map-first place editing, Home/Work/Custom geofences, deterministic commute and initial tag override foundation.
- **Phase 5** (`3492f0b`, `5e68f95`): local effective-dated Charging Cost Engine, manual/free overrides, and cost analytics.
- **Phase 6** (`15a8ee9`, `ae8fcc1`): battery analytics, robust usable-capacity estimate/baseline/confidence, and dynamic Real-World Range.
- **Phase 7A** (`7fbab55`, local/unpushed): endpoint-based recurring routes and reusable manual drive tags.

## 6. Current Phase 7A state

Phase 7A is implemented at `7fbab55783b36474102cec1d06638ec5078825b0`.

### Routes

- Smart Place stable IDs have endpoint-identity priority.
- Unmatched endpoints use a dedicated 200 m cluster radius (`EndpointClusterer`), not potentially broad Smart Place radii.
- Route identity is directional: `A → B` differs from `B → A`.
- Discovery threshold is three completed observations (`RecurringRouteDetector.MIN_OCCURRENCES`).
- Route stats use summaries only: occurrence count, total/average distance, average duration, most recent date, and weighted efficiency (`sum energy / sum valid distance`).
- Route names, confirmations, and dismissals are local metadata. Coordinates anchor that intent so ordinary place re-derivation does not silently discard it.

### Commute and tags

- Home ↔ Work is still the only automatic Commute rule; frequency, timing, distance, and shape never infer Commute.
- Existing manual Commute assign/remove overrides remain authoritative.
- `COMMUTE` is a system-derived tag. `UserDriveTag` and `DriveManualTag` provide reusable manual multi-tags without overwriting system provenance.
- Drive Detail supports add existing/create/remove manual tags. Activity shows compact tags and filters by Commute or a selected user tag.
- A physical tag-filter duplicate-page crash was found and fixed with a synchronous page-request guard in `ActivityTimelineViewModel`.

### Current coverage limitation

The physical history currently has endpoints for only **6 / 101** drives. No recurring-route suggestions on that device is therefore correct. Do not lower the threshold or fabricate routes for presentation. Route coverage will naturally improve as users open existing Drive Details. The cover empty/coverage state was physically reviewed; a populated recurring-route detail and its unfolded two-pane state remain pending enough locally cached endpoints.

## 7. Critical data semantics

### Charging energy and costs

For automatic cost, prefer coherent reported grid/wall energy when `charge_energy_used >= charge_energy_added`; otherwise use battery energy added with a charging-loss caveat. Missing/incoherent energy is unavailable. Automatic FREE works even when energy is unavailable. Manual override always wins.

The first-ever place/default rate applies back to the open historical beginning. A later change applies forward from its creation/change timestamp. Do not apply today’s rate to all history. Cost analytics use local summaries/rates/overrides and label the denominator as **cost-basis energy**, not grid energy.

### Battery capacity

The approved capacity estimator is battery energy added divided by SOC delta. It requires at least a 12 percentage-point SOC span, applies robust median/MAD filtering, and reports an **ESTIMATED** result, not an OEM diagnostic. Current verified result: 71.7 kWh, 9 accepted samples, HIGH confidence, MAD about 0.609 kWh. Integer SOC quantization contributes uncertainty; display precision is intentionally limited. The baseline is observed early-sample data only, not assumed pack capacity.

### Dynamic Real-World Range

Real-world range uses estimated usable capacity × current SOC ÷ weighted recent driving efficiency. Windows: 25 mi, 50 mi, 100 mi, 7d, and 30d; default 50 mi when sufficient. A distance window includes the entire boundary drive, then stops—never partial-drive prorating.

Verified example: SOC 56%, capacity 71.6757 kWh, 50-mi target, 65.4046 represented mi, 17.50096 kWh, 267.58 Wh/mi, about 150 mi to 0% and 123 mi to 10%. Tesla Rated Range, Tesla Estimated Range, and TesMano Real-World Range are separate. Tesla Estimated Range zero/missing is unavailable.

## 8. Smart Places

Types are `HOME`, `WORK`, and `CUSTOM`. The editor is map-first: tap moves the center, the geofence circle updates with the 100–2000 m radius slider, and a previously known address may be shown. Raw latitude/longitude is internal and not normal UX. Matching is deterministic Haversine against enabled places, using the closest match. Disabled places are ignored. Do not request phone GPS permission or ask users to type coordinates.

## 9. Vehicle and Home invariants

Generation, trim, paint, wheels, brakes, and spoiler are independent vehicle dimensions. Performance does not imply Performance wheels; PN01/P74D/Uberturbine21 do not imply Juniper. The shared resolver uses the custom legacy asset at:

`app/src/main/assets/car_images/custom/vehicle_custom_legacy_model_y_pn01_dark_gemini.png`

`CarImageResolver` uses the stable `legacy_model_y_performance_dark_gemini` key and falls back to `car_images/my_PMNG_WY19B.png`; it never substitutes Juniper. Dashboard and widgets share this path. The approved Home hierarchy should not be casually redesigned. Phase 6B intentionally deferred Real-World Range integration on Home.

## 10. Maps and FSD

Drive Detail uses the existing OSMDroid stack with stronger route emphasis, markers, and fit bounds. Do not replace it just for styling.

The current TeslaMate/TeslaMateApi contract has no authoritative historical FSD/autopilot-active signal aligned with stored drive positions. Do not infer FSD from speed, steering, road type, or smoothness. Accurate historical highlighting cannot be recovered retroactively. A future, separately authorized architecture could be Tesla Fleet Telemetry → isolated TesMano sidecar collector/store → TesMano, without changing TeslaMate/PostgreSQL. Even authoritative cumulative counters such as `SelfDrivingMilesSinceReset` would make route transition boundaries estimated. Do not begin this work without a dedicated design prompt.

## 11. Important limitations and do-not-reopen items

- Keep named timezone/DST handling; do not revive fixed `EST` without a reproducible defect.
- Never eager-fetch historical details.
- Unknown stays unavailable: Tesla estimated range zero, battery temperature, historical rated-range trend, broad charge curves, and uncached AC/DC data are not fabricated.
- Short drives are valid; do not restore an arbitrary under-6-mile exclusion.
- Recurring route is not Commute.
- Other/DC is not Supercharger without authoritative classification.

## 12. Fold and physical device rules

Target device: Samsung Galaxy Z Fold 8 Ultra (`SM-F976U1` when connected). Cover target is 1080 × 2520; unfolded target is 2256 × 2504. Use the Fold8 cover/main emulators only as approximations. The Pixel foldable is posture-only and is not a product target.

Physical folding may not be controllable over ADB. Do not claim cover/unfolded validation unless it was actually observed. Never uninstall or clear app data. Build/install in place with `./gradlew installDebug`.

## 13. Build and validation

Verified local environment:

```sh
env JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ANDROID_HOME="/Users/boorlu/Library/Android/sdk" \
  ANDROID_SDK_ROOT="/Users/boorlu/Library/Android/sdk" \
  PATH="/Users/boorlu/Library/Android/sdk/platform-tools:$PATH" \
  ./gradlew test lint assembleDebug installDebug
```

Run focused tests during implementation. Before completing a behavior change, run `./gradlew test`, `./gradlew lint`, and `./gradlew assembleDebug`; install/smoke-test the Fold when relevant. A docs-only change needs diff/path/secret review, not a full Gradle suite.

## 14. Roadmap and next work

Phases 1–6B are complete and pushed. Phase 7A is implemented locally and awaits review/push. After explicit approval, possible next choices are: (A) Phase 7B recurring-route/pattern refinement, or (B) a separately planned FSD telemetry-sidecar architecture. Other future work includes explicit route auto-tag rules, richer route analytics, contextual range prediction, charging TOU/session fees, and additional records/data-quality tools. Do not choose or begin a phase autonomously.

## 15. Tomorrow startup procedure

Before modifying anything:

1. Read `CLAUDE.md`, `AGENTS.md`, this handoff, `docs/TESMANO_FEATURE_ROADMAP.md`, and `docs/TESLAMATE_API_CONTRACT.md`.
2. Run:
   ```sh
   git status --short
   git branch --show-current
   git log --oneline --decorate -10
   git rev-parse HEAD
   git rev-parse origin/develop
   ```
3. Inspect only the source area required by the next ChatGPT prompt.
4. Treat source/tests as truth if any handoff text is stale.
5. Do not push unless explicitly instructed.
