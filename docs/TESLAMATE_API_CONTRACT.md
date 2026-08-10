# TesMano Phase 0 — TeslaMateApi Contract

Audit date: 2026-08-09

## Inspected source

This contract is based on the current public [TeslaMateApi repository at `3e158df077165237130072c82d07878ab448f4f8`](https://github.com/tobiasehlert/teslamateapi/tree/3e158df077165237130072c82d07878ab448f4f8), not only on MateDroid's Moshi models. The latest release is [v1.25.0](https://github.com/tobiasehlert/teslamateapi/releases/tag/v1.25.0), published 2026-07-07; the inspected `main` differs by a CI dependency commit dated 2026-07-08.

MateDroid/TesMano defines 12 GET calls in `TeslamateApi.kt`. It defines no POST, PUT, PATCH, or DELETE request. TeslaMateApi itself exposes optional command and logging write endpoints, but the app neither models nor calls them. TesMano v1 must keep that read-only boundary.

## Common behavior

### Envelope and errors

Successful reads normally return HTTP 200 with `{"data": ...}`. TeslaMateApi's common error helper also returns HTTP 200, but with `{"error":"..."}`. That means HTTP success alone is insufficient. MateDroid explicitly handles the current-charge error envelope but most other repositories reduce a missing payload to “No … returned”; list endpoints may turn a missing array into an empty list.

Every response includes an `API-Version` header. MateDroid does not read or validate it, so compatibility is discovered only when parsing or calling an endpoint.

Moshi ignores unknown fields. Most app fields are nullable and default to null, but `car_id`, `drive_id`, and `charge_id` are required in their principal records; missing/wrong-typed IDs fail the complete response parse.

### Authentication

Current TeslaMateApi read endpoints have no native token middleware. `API_TOKEN` protects command/logging mutations only (and commands are disabled unless `ENABLE_COMMANDS=true`). In real installations:

- HTTP Basic credentials in MateDroid normally authenticate a reverse proxy.
- MateDroid's Bearer token is sent to every read when configured. Current TeslaMateApi ignores it on reads, but a reverse proxy may enforce it.
- If Basic and Bearer are both configured, MateDroid adds two `Authorization` headers. Proxy behavior is ambiguous; TesMano should support one unambiguous authorization policy or a separately named proxy-token header.
- v1.25 adds configurable `API_TOKEN_HEADER`, but that still concerns TeslaMateApi's mutation authentication, not its read routes.

### Timestamps and timezone

TeslaMate/PostgreSQL date columns are treated by TeslaMateApi as UTC values represented by the literal layout `2006-01-02T15:04:05Z`. The API converts them to the container's `TZ` location (default `Europe/Berlin`) and serializes RFC 3339, such as `2026-01-14T20:30:00-05:00` or `2026-07-03T21:30:00-04:00`. Thus every timestamp carries an offset, but not a named zone.

For `startDate`/`endDate`, RFC 3339 input is converted to UTC before SQL filtering. A timezone-free input is interpreted in the API container's `TZ`, then converted to UTC. Clients should always send RFC 3339 with an explicit offset or `Z`.

MateDroid currently discards the returned offset when it calls `OffsetDateTime.parse(value).toLocalDateTime()`. See `TESMANO_AUDIT.md` for the root cause and remediation.

### Units

TeslaMateApi reads TeslaMate settings and converts numeric values before returning them:

- distance, odometer, speed, and range are `km`/`km/h` or `mi`/`mph` according to `unit_of_length`;
- temperature is Celsius or Fahrenheit according to `unit_of_temperature`;
- pressure is included by status when available;
- energy is kWh, power is kW, voltage is V, current is A, and battery values are percent;
- cost is a number in the TeslaMate installation's configured monetary context; the API supplies no currency code per record.

The app must not convert server-converted distance/speed a second time. Upstream commit `8d56f2f` fixes that exact defect.

### Pagination, filtering, and payload size

Drive, charge, and update list defaults are `page=1`, `show=100`; page values below one behave as page one. Responses contain no total count or next-page token. MateDroid overrides both list history calls and updates to `page=1&show=50000` and never loops pages, so records beyond 50,000 are silently absent.

Current server filters:

- drives: `startDate`, `endDate`, `page`, `show`, `minDistance`, `maxDistance`, `location`;
- charges: `startDate`, `endDate`, `page`, `show`, `location`;
- updates: `page`, `show`.

MateDroid only exposes date/page/show in its Retrofit interface and performs distance/location filtering in memory. Detail endpoints have no paging and return complete point arrays. A drive detail can exceed 15 MB; charge arrays can also be large.

## Consumed endpoint matrix

All paths are relative to the configured base URL.

| Method/path | Query | Response consumed by app | Time/units/nullability/size | Compatibility notes |
|---|---|---|---|---|
| `GET api/ping` | none | Status only; model expects optional `ping` | Server actually returns `{"message":"pong"}`; body mismatch is harmless because connection test checks HTTP success | Suitable only as reachability check; it does not prove endpoint schema or database/MQTT health |
| `GET api/v1/cars` | none | `data.cars[]`: required `car_id`; optional name, model, trim, VIN, efficiency, exterior color, spoiler, wheel, free-supercharging, drive/charge counts | Car inserted/updated timestamps exist server-side but are ignored. Exterior/model values may be null in v1.25 | v1.25 fixes null exterior fields and old null model handling; prefer v1.25 |
| `GET api/v1/cars/{carId}` | path ID | Same cars envelope; app takes first item | Same as cars | Server implements this by filtering the cars result; absent ID can yield an empty array rather than 404 |
| `GET api/v1/cars/{carId}/status` | none | Status snapshot plus units: state/state_since, odometer, lock/sentry/display, geodata, software, driving, climate, battery, charging and TPMS | `state_since` and scheduled-charge time are RFC 3339 in API `TZ`. MQTT fields are highly nullable/stale-capable. Values are pre-converted | Requires MQTT for full live data. Server declares charger current/power as floating point while the app models integers; non-integral JSON values may fail parsing. No ETag/freshness contract; app polls every 5 s while Dashboard is visible |
| `GET api/v1/cars/{carId}/drives` | dates/page/show; server also distance/location | `data.drives[]`: IDs, dates, addresses, odometer/distance, duration, speed/power, battery/range, temperatures, energy and consumption | RFC 3339 in API `TZ`; unit bundle included. Old temperature values can be null. One page only | MateDroid requests 50,000. v1.25 fixes nullable old temperatures and supports location; app does not use server distance/location filters |
| `GET api/v1/cars/{carId}/drives/{driveId}` | none | Drive summary plus `drive_details[]`: timestamp, lat/lon, speed, power, odometer, battery, elevation, climate, range/heater data | Complete unpaged telemetry array; dates RFC 3339; many detail fields nullable; potentially 15 MB+ | Primary sync/performance risk. App downloads one complete response per unsynced drive and stores only derived aggregates/route cache |
| `GET api/v1/cars/{carId}/charges` | dates/page/show; server also location | `data.charges[]`: IDs, dates, address, energy added/used, cost, duration, battery/range, temperature, odometer, lat/lon | RFC 3339; units included. Server coalesces several values to zero; list contains completed sessions only | MateDroid requests 50,000 and filters location in memory. v1.25 location filter is unused |
| `GET api/v1/cars/{carId}/charges/{chargeId}` | none | Charge summary plus `charge_details[]`: timestamp, battery/energy, `charger_details`, sibling `fast_charger_info`, battery data and connector cable | Complete unpaged array; fields may be null/zero depending on TeslaMate history and sensor availability | App models fast-charger brand/type/presence inside `charger_details` instead of sibling `fast_charger_info`, so brand/connector aggregates are lost. App also expects battery range keys with `_km` suffix although server JSON omits it |

### Local charging-cost semantics

TesMano derives charging-cost estimates locally from the completed charge-list/detail fields. It prefers coherent `charge_energy_used` (reported grid/wall energy, only when positive and no smaller than battery energy) and otherwise uses positive `charge_energy_added` as a battery-energy estimate that may exclude charging losses. Missing or incoherent energy yields no estimate. Charge-list latitude/longitude permits immediate Smart Place/default-rate matching; no historical detail fetch is scheduled for pricing. Automatic estimates remain derived from local effective-dated rate rules, while manual/free session overrides remain local and take precedence.
| `GET api/v1/cars/{carId}/charges/current` | none | Similar detail envelope, with `is_charging`, current battery and current/added rated range | In-progress unpaged point array. No session may be HTTP 200 `error` (and app tolerates 204) | Introduced in v1.24. Server calls the range object `rated_range` with `current_range`/`added_range`; app reuses a `range_rated` model with `end_range`, so current range data is ignored. Availability is session-cached |
| `GET api/v1/cars/{carId}/battery-health` | none | max/current range and capacity, rated efficiency, health percentage | No timestamp. Derived by server from historical positions/charges; zeros may mean insufficient data. Range pre-converted | Potentially expensive SQL over history; app treats fields as nullable but server generally coalesces to zero |
| `GET api/v1/cars/{carId}/updates` | page/show | update ID, version, start/end dates | RFC 3339 in API `TZ`; completed/non-null updates only in v1.24+ | App requests only first 50,000 and ignores units/car wrapper |
| `GET api/v1/globalsettings` | none | TeslaMate base/Grafana URLs and length/temperature units | Account timestamps exist but app ignores them; most app model fields nullable | Optional in Dashboard; used to cache the TeslaMate web base URL for charge-cost editing |

## Data ownership

### Server-backed

Cars/configuration reported by TeslaMate, current status, completed drives, complete drive position samples, completed charges, charge samples, battery-health derivation, software updates, TeslaMate URLs/units, addresses/geofences present in those records, and charge costs are server-backed.

TeslaMateApi does not provide a trip resource. It also has no standalone location-history endpoint used by the app; locations arrive inside status, charge summaries, and drive detail samples.

### Autopilot / FSD route attribution

The consumed contract exposes no `SelfDrivingMilesSinceReset`, Autopilot engagement state, FSD state, or equivalent per-sample usage field. Drive detail samples have timestamped position/telemetry, but no automation flag; the app stores only derived drive aggregates historically. Accurate FSD route highlighting is therefore not possible from the current backend. A future backend capability would need a timestamped, historically retained Autopilot/FSD engagement signal at or finer than the drive-position sampling cadence, with the same UTC/offset-preserving timestamps as the position samples. TesMano must not infer usage from speed, steering, or other telemetry.

### Created locally by MateDroid

Stats summary tables, detail aggregates, reverse-geocode metadata, route/country caches, sentry alert history, auto-detected/saved/manual/edited/merged trips, trip names and membership, image overrides, notification state, selected car, credentials, and sync progress are local. Weather comes from Open-Meteo; reverse geocoding comes from Nominatim; neither is TeslaMateApi data.

### Server writes

MateDroid performs none. “Edit charge cost” launches the TeslaMate web page at `/charge-cost/{id}`; any user action occurs in that web application, not through the app's Retrofit API. TesMano should retain a compile-time/read-only API surface and should not add TeslaMateApi command routes or Tesla Fleet API controls in v1.

## Contract risks and Phase 1 requirements

### Phase 1A implementation

- All consumed API envelopes model the optional HTTP-200 `error` field. Repository mapping converts it to a typed `SERVER_ENVELOPE` failure rather than treating it as missing data.
- `API-Version` is captured internally from response headers when supplied.
- Read authentication is a single explicit mode: `NONE`, `BASIC`, or `BEARER`. When legacy settings contain both Basic credentials and a Bearer token, Bearer takes precedence and exactly one `Authorization` header is sent.

1. Preserve timestamps as instants; do not reduce RFC 3339 to `LocalDateTime` at ingestion.
2. Align models to the actual nested schema and numeric types: `fast_charger_info`, `rated_range`, current/added range, range names without `_km`, usable battery fields, and floating-point status current/power. Today, charger brand/connector aggregates and current-charge range are silently absent.
3. Read and record `API-Version`; define a supported minimum (v1.24 for current charge, v1.25 preferred for old/null data) and capability-test optional endpoints.
4. Parse the `error` envelope consistently for every endpoint, even on HTTP 200.
5. Replace `show=50000` with bounded pagination and cancellation/progress, and use server filters where their semantics match.
6. Stream or bound detail processing; never require a whole multi-megabyte response and all decoded points to coexist longer than necessary.
7. Keep read authentication separate from mutation-token semantics and reject ambiguous Basic+Bearer configuration.
8. Add fixture/contract tests from current v1.25 JSON, including null old records, empty arrays, error envelopes, current-charge unavailable/no-active states, offset timestamps, decimal status values and very large arrays.
