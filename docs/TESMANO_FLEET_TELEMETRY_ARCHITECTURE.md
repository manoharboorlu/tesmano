# TesMano Fleet Telemetry Sidecar Architecture (T0 spike)

Status: **design only, not deployed, not authorized to run against a real vehicle.**
This document proposes the architecture for a future, separately-authorized project to
historically retain Tesla Fleet Telemetry data for TesMano's one vehicle (Night Fury). It does
not change TeslaMate, TeslaMateApi, PostgreSQL, Oracle infrastructure, or any Android production
code, and does not use the Tesla Fleet API today.

### T0.1 implementation note

A minimal, fixture-tested skeleton of the ingest → SQLite → read API path now exists locally at
`telemetry-sidecar/` (Go, `modernc.org/sqlite`, no cgo). It proves the data path only — no real
Tesla credentials, no Vehicle Command Proxy, no deployment. Two deltas from the schema/API sketch
below, kept because they simplify the skeleton without losing anything:

- `telemetry_signal`/`telemetry_location` key on **`vin TEXT`**, not `vehicle_id INTEGER` — the
  sidecar has no vehicle-registry table yet, and VIN is already the natural identifier Tesla
  sends on every record. Add an integer surrogate key later only if a real need (e.g. joining
  against a multi-vehicle registry) appears.
- Both tables also carry **`received_at`**, distinct from the telemetry `ts`, per T0.1's minimum
  raw model (`timestamp`, `vehicle/VIN`, `signal`, `typed value`, `received_at`).
- The read API's actual shape is `GET /api/v1/signals/latest?signal=...&vin=...` and
  `GET /api/v1/signals/history?signal=...&vin=...&from=...&to=...&limit=...` (bounded, default
  500 rows, hard cap 5000) rather than the `/v1/vehicles/{id}/signals` sketch — same intent,
  VIN-scoped instead of vehicle-id-scoped for the reason above.

See `telemetry-sidecar/README.md` for exact run commands.

## Why this exists

`docs/TESLAMATE_API_CONTRACT.md` establishes that the current TeslaMate/TeslaMateApi contract has
no authoritative historical FSD-active signal, no battery module temperature, no pack
voltage/current, no navigation-prediction data, and no high-frequency performance data. Those all
require Tesla's Fleet Telemetry streaming service directly from the vehicle, which is out of
scope for TeslaMate. This spike answers *how* TesMano could start collecting that data
prospectively, without touching the existing backend.

## Recommended architecture

```text
Vehicle (Fleet Telemetry client, firmware-side)
        │  WebSocket, mTLS, protobuf
        ▼
teslamotors/fleet-telemetry  (official Tesla open-source server, unmodified)
        │  "logger" dispatcher → newline-delimited JSON, one line per record
        ▼
tesmano-telemetry-collector  (new, small, single-purpose process)
        │  parses JSON records → typed rows
        ▼
SQLite  (single file, WAL mode)
        ▲
        │  read-only local HTTP/JSON
tesmano-telemetry-api  (new, tiny, single-purpose process)
        ▲
        │  HTTPS, bearer token (same auth model as TeslaMateApi in the app today)
TesMano Android app  (new read-only client, added in a later phase)
```

Three new, independent processes; zero changes to TeslaMate/TeslaMateApi/PostgreSQL. All three
new pieces are small enough to run on one small host and back up as a single directory (one
SQLite file + two static binaries/scripts + config).

## Decisions

### 1. Receiver

Run Tesla's official **`teslamotors/fleet-telemetry`** server directly, unmodified, as a Docker
container. It already implements the WebSocket protocol, mTLS, protobuf decoding, and the
`fleet_telemetry_config` request/response contract — reimplementing any of that would be pure
risk for a personal one-vehicle project. Use its built-in **`logger` dispatcher**, which
serializes each incoming record to a JSON line (stdout or file). No Kafka/Kinesis/Redis/ZMQ
broker — those exist in the official project for fleet-scale deployments and are unnecessary
complexity for one car.

### 2. Storage

**SQLite**, not PostgreSQL. One vehicle at a bounded, on-change telemetry cadence (see §6/§7) is
low enough volume that SQLite in WAL mode comfortably handles both the collector's writes and the
read API's queries on the same host. It is one file: trivial to back up, inspect with any SQLite
tool, and delete/rebuild during experimentation. Standing up PostgreSQL for this would duplicate
infrastructure the project already deliberately avoids exposing to Android (per
`docs/TESLAMATE_API_CONTRACT.md`'s existing PostgreSQL boundary) for no real benefit at this
scale.

### 3. Tiny read API

A small **read-only HTTP/JSON service** (one process, same host, same language as the collector)
exposing bounded, paginated queries over SQLite — e.g. `GET /v1/vehicles/{id}/signals?name=...&since=...&until=...&page=...`
and a `/v1/vehicles/{id}/signals/latest`. Auth is a single bearer token, mirroring the
`BASIC`/`BEARER` read-auth model TesMano's `TeslamateApi.kt` already uses — Android gets only this
narrow token, never any Tesla developer credential. No endpoint returns unbounded history; the
API enforces the same "summary first, detail on demand" discipline as the rest of the app.

### 4. Schema (no migration per new signal)

A narrow, generic event table, not one column/table per Tesla field:

```sql
CREATE TABLE telemetry_signal (
    vehicle_id   INTEGER NOT NULL,
    ts           TEXT    NOT NULL,   -- RFC 3339 UTC, e.g. 2026-08-10T14:03:11.482Z
    signal       TEXT    NOT NULL,   -- exact Tesla field name, e.g. "PackVoltage"
    value_type   TEXT    NOT NULL,   -- 'NUM' | 'STR' | 'BOOL'
    value_num    REAL,
    value_str    TEXT,
    value_bool   INTEGER,
    PRIMARY KEY (vehicle_id, signal, ts)
);
CREATE INDEX idx_signal_recent ON telemetry_signal (vehicle_id, signal, ts DESC);

CREATE TABLE telemetry_location (
    vehicle_id   INTEGER NOT NULL,
    ts           TEXT    NOT NULL,
    lat          REAL    NOT NULL,
    lon          REAL    NOT NULL,
    PRIMARY KEY (vehicle_id, ts)
);
```

A new Tesla field (e.g. a future `PackTempMax`) is just new rows with a new `signal` string —
never a schema migration. `location` is the one deliberate exception, kept as its own narrow
table because route/map queries need lat+lon together constantly; forcing it through the generic
table would mean pairing two separate rows on every read.

Downsampled long-term aggregates (§6) get one more narrow table, same philosophy — not a
feature-specific table per Battery/Charging/FSD:

```sql
CREATE TABLE telemetry_signal_daily (
    vehicle_id INTEGER NOT NULL,
    day        TEXT    NOT NULL,   -- local calendar date
    signal     TEXT    NOT NULL,
    min_num    REAL, max_num REAL, avg_num REAL, sample_count INTEGER,
    PRIMARY KEY (vehicle_id, day, signal)
);
```

### 5. Typed value handling

`value_type` says which of `value_num`/`value_str`/`value_bool` is populated. Numeric fields
(`PackVoltage`, `Soc`, `EnergyRemaining`, …) → `value_num`. Enum/string fields
(`DetailedChargeState`, `ChargerPhases` if reported as a label) → `value_str`, stored as the exact
Tesla-reported string — TesMano never invents its own enum mapping ahead of seeing real values.
Boolean fields (`PreconditioningEnabled`, `LocatedAtHome`) → `value_bool` as 0/1. Location is
handled structurally, not as two independent generic rows (§4), so a route query never has to
join two async signal streams back together.

### 6. Retention and downsampling

- **State-transition / discrete signals** (`DetailedChargeState` changes, connectivity
  online/offline, any future FSD-engagement-style discrete field): retained indefinitely as raw
  rows. Low volume, high analytic value — never downsample these.
- **Normal continuous telemetry** (SOC, pack voltage/current, speed, temperatures, …): full
  resolution retained for a rolling recent window — **90 days** — which comfortably covers
  Battery Lab/Charging Lab/Range work that looks at recent sessions and trends.
- **Beyond 90 days**: collapsed into `telemetry_signal_daily` (min/max/avg/count per signal per
  day) and the raw rows deleted. Long-term degradation/efficiency trend work reads the daily
  table; nothing needs millisecond telemetry from a year ago.
- **Performance Mode raw data** (future, §7): short retention only, **14 days**, then deleted —
  it exists for near-term analysis of a specific capture session, not long-term trending.

### 7. Cadence: Normal vs. Performance Mode

**Normal / always-on** relies on Fleet Telemetry's per-field `interval_seconds` (floor on how
often a field resends even unchanged) and `minimum_delta` (how much a numeric field must move
before it's considered "changed" and sent). Target a practical effective cadence in the **10–60
second** range per field, matching the philosophy already stated: useful long-term history, not
millisecond telemetry. Noisy fields (`ChargerVoltage`, `PackCurrent`, `InsideTemp`) get a
deliberately generous `minimum_delta` so small sensor jitter doesn't multiply signal volume.

**Performance Mode** (future, explicitly opt-in, not implemented now) would subscribe a small
additional field set (motor torque, accel, pack current/voltage, inverter/stator temps) at much
tighter `minimum_delta`/`interval_seconds`, only while a user-triggered capture session is active,
writing to the same schema with a session-scoped short retention (§6). It is a configuration
change to the vehicle's telemetry subscription, not a different pipeline.

### 8. Cost control

Fleet Telemetry billing is per streaming signal Tesla sends to the receiver (confirmed via
Tesla's Fleet API billing/limits documentation and corroborated by third-party telemetry
resellers who pass this exact cost through). The controls available, in order of impact:

1. **Field selection** — subscribe only to the prioritized Normal-profile fields (§ below), not
   every field Tesla exposes. This is the single biggest lever.
2. **`minimum_delta`** — the biggest volume reducer for continuously-varying numeric fields; set
   generously for noisy fields, tightly only for fields where small changes actually matter
   (e.g. `Soc`).
3. **`interval_seconds`** — a floor under (2) so an unchanging field doesn't resend needlessly
   often, but still resends often enough to prove the vehicle is online.
4. **Performance Mode stays opt-in and time-boxed** — never subscribed by default; it is the one
   profile capable of generating real volume.
5. One Tesla developer **account gets a monthly discount** intended to cover light multi-vehicle
   personal use; for one vehicle at Normal-profile cadence this is expected to comfortably absorb
   the cost, but actual signal counts should be observed before ever enabling Performance Mode
   for extended periods.

### 9. Secrets and where they live

| Secret | Purpose | Lives where | Never goes |
|---|---|---|---|
| Tesla application **private key** | Signs `fleet_telemetry_config` requests via the Vehicle Command Proxy | Collector host only (ideally a KMS/HSM-backed path; a plain file with strict permissions is the minimum acceptable for a personal spike) | Android, Git, docs, logs |
| Corresponding **public key** | Hosted at the app domain's well-known URI to prove domain ownership | Public by design — served over HTTPS at the registered path | N/A (not secret) |
| Fleet API **OAuth client ID/secret + tokens** | Authenticates the collector's application to Tesla | Collector host only | Android, Git, docs, logs |
| Fleet Telemetry server **TLS certificate + private key** | Terminates the vehicle's WebSocket connection | Same host as the fleet-telemetry container | Android, Git, docs, logs |
| Tiny read API **bearer token** | The *only* credential Android ever receives for this system | Android secure storage (same pattern TesMano already uses for TeslaMateApi credentials) | Never the Tesla private key, OAuth secret, or TLS key |

Document only secret **names/locations** in the repo (as above); values are never committed,
logged, or placed in this document.

## Signal profiles

Every field name below is taken from current Tesla/Teslemetry Fleet Telemetry field
documentation; nothing here is invented. Exact field availability should be re-verified against
`api.teslemetry.com/fields.json` (the community-maintained mirror of Tesla's authoritative field
list) and the vehicle's actual firmware/hardware support at implementation time, since Tesla adds
fields over time (e.g. `MilesSinceReset`/`SelfDrivingMilesSinceReset` require HW4 + a firmware
version released in late 2025).

### Normal / always-on

- **Battery**: `Soc`, `BatteryLevel`, `EnergyRemaining`, `PackVoltage`, `PackCurrent`,
  `ModuleTempMin`, `ModuleTempMax`, `PreconditioningEnabled`.
- **Efficiency/driving**: `VehicleSpeed`, `Location` (→ `telemetry_location`), `OutsideTemp`,
  `Odometer`, `RatedRange`, `EstBatteryRange`, `IdealBatteryRange`.
- **FSD**: `MilesSinceReset`, `SelfDrivingMilesSinceReset` (HW4-only; absent on non-HW4 hardware —
  treated as `UNAVAILABLE`, never inferred).
- **Navigation**: `DestinationLocation`, `DestinationName`, `MilesToArrival`, `MinutesToArrival`,
  `ExpectedEnergyPercentAtTripArrival`.
- **Charging**: `DetailedChargeState`, `ChargeState`, `ACChargingPower`, `ACChargingEnergyIn`,
  `DCChargingPower`, `DCChargingEnergyIn`, `ChargerVoltage`, `ChargeAmps`, `ChargerPhases`,
  `ChargeLimitSoc`, `TimeToFullCharge`.

### Performance Mode (future concept only)

Higher-frequency subset: `LongitudinalAcceleration`, `LateralAcceleration`, `VehicleSpeed`,
`Location`, `PackCurrent`, `PackVoltage`, `DiTorqueActualF`/`R`/`REL`/`RER`,
`DiStatorTempF`/`R`/`REL`/`RER`, `DiHeatsinkTF`/`R`/`REL`/`RER`. Not implemented; not configured
on the vehicle; explicitly future and explicitly opt-in.

## FSD analytics: what's possible, and what stays uncertain

`MilesSinceReset` and `SelfDrivingMilesSinceReset` are **monotonic odometer-style counters since
the last FSD-build reset**, not per-instant booleans. That means:

- Approximate FSD-engaged distance for a drive/segment is the **delta** of
  `SelfDrivingMilesSinceReset` between the samples bounding that segment.
- If a sample cadence means the true engage/disengage instant falls *between* two samples, the
  attributed segment boundary is **approximate by construction** — the delta is exact, but exactly
  *where within the sampled interval* the transition happened is not recoverable from these two
  fields alone.
- Any future segment-level FSD attribution must carry that uncertainty forward (label boundary
  segments as approximate) rather than presenting a false-precision exact split — consistent with
  `docs/TESLAMATE_API_CONTRACT.md`'s existing conclusion that historical FSD state cannot be
  reconstructed, and that this sidecar only starts collecting **prospectively** from whenever it
  is deployed.

## Hosting

Compared options, smallest set that's realistic for one vehicle:

1. **Same Oracle Cloud VM that already hosts TeslaMate**, in a separate directory/container
   network, with its own SQLite file — never touching TeslaMate's PostgreSQL or containers.
   Simplest to operate long-term (one host to maintain, already has a public IP and TLS
   infrastructure pattern this user has proven out). **Recommended for eventual deployment.**
2. A second small VM/cloud instance dedicated to this sidecar. Cleaner isolation, but a second
   host to patch/monitor/back up for a single vehicle — not justified yet.
3. Local machine, for architecture experimentation only — not viable long-term since Fleet
   Telemetry requires a public FQDN with a stable TLS certificate for the vehicle to connect to.

**Decision: (1), but not deployed in this phase.** This document records the recommendation only;
no changes are made to the Oracle VM now.

## Implementation steps (future, in order, each separately authorized)

1. Register a Tesla developer application, generate the key pair, host the public key at the
   well-known URI on the TesMano domain, and confirm domain verification — no vehicle
   interaction yet.
2. Stand up `teslamotors/fleet-telemetry` in Docker on the target host (initially can be tested
   against a throwaway host before touching the Oracle VM) with the `logger` dispatcher and the
   Normal-profile field config from this document; issue the `fleet_telemetry_config` request
   through the Vehicle Command Proxy for Night Fury only.
3. Build the minimal `tesmano-telemetry-collector` (parses logger JSON lines → `telemetry_signal`
   / `telemetry_location` rows) and `tesmano-telemetry-api` (bounded read-only queries), verify
   against a few hours of real streamed data, and only then consider an Android-side read client —
   which is its own future phase, not part of this spike.

## Explicitly out of scope for this document

No collector/API/Docker config was implemented. No Oracle VM change was made. No Android code was
touched. No secret was created or requested. Tire Intelligence was intentionally not scoped.
