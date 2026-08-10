# TesMano Fleet Telemetry Sidecar (T0.1 skeleton)

Minimal, isolated proof of the data path described in
`docs/TESMANO_FLEET_TELEMETRY_ARCHITECTURE.md`:

```text
Tesla Fleet Telemetry logger JSONL  →  cmd/ingest  →  SQLite  →  cmd/api  →  (future) TesMano app
```

This directory is a standalone Go module. It does not touch TeslaMate, TeslaMateApi, PostgreSQL,
Oracle infrastructure, or the Android app. It does not talk to Tesla; it only knows how to parse
the JSON shape the official `teslamotors/fleet-telemetry` server's `logger` dispatcher emits.

## Requirements

Go 1.26+ (installed locally via `brew install go`). No cgo, no C compiler, no Docker needed for
this skeleton.

## Build

```sh
cd telemetry-sidecar
go build -o /tmp/ts-ingest ./cmd/ingest
go build -o /tmp/ts-api ./cmd/api
```

## Run: ingest the fixture

```sh
mkdir -p data
/tmp/ts-ingest -db ./data/telemetry.db < testdata/fixture.jsonl
# ingest: accepted=5 skipped=0 db=./data/telemetry.db
```

`testdata/fixture.jsonl` is five representative decoded Tesla records (`ModuleTempMin/Max`,
`Soc`, `PackVoltage`, `PackCurrent`, one `Location`, one `DetailedChargeState` string, one
`PreconditioningEnabled` bool, and one field deliberately marked `"invalid": true` to prove
unavailable data is skipped, not stored as zero).

Later, the official server's logger output can be piped straight in:

```sh
fleet-telemetry ... | /tmp/ts-ingest -db ./data/telemetry.db
```

## Run: start the read API (dev mode, no auth, loopback-only)

```sh
/tmp/ts-api -db ./data/telemetry.db -addr 127.0.0.1:8081
```

`-auth none` (the default) refuses to start unless `-addr` is a loopback address, so it can never
be accidentally exposed without a token. For a non-local deployment, use:

```sh
/tmp/ts-api -db ./data/telemetry.db -addr 0.0.0.0:8081 -auth bearer -token "$API_TOKEN"
```

## Query

```sh
curl -s http://127.0.0.1:8081/health

curl -s "http://127.0.0.1:8081/api/v1/signals/latest?signal=ModuleTempMin"

curl -s "http://127.0.0.1:8081/api/v1/signals/history?signal=ModuleTempMin&limit=100"
# optional: &vin=...&from=2026-08-10T09:00:00Z&to=2026-08-10T09:10:00Z
```

## Test

```sh
go test ./...
```

Covers: fixture ingest, latest/history queries, that an `"invalid": true` field is skipped rather
than stored, typed string/bool/location round-tripping, and that data survives closing and
re-opening the SQLite file (simulating a process restart).

## Not in this skeleton

Retention/downsampling worker, daily aggregates, Docker/deployment, OAuth, Vehicle Command Proxy,
Tesla application registration, Android integration, and any analytics UI. See
`docs/TESMANO_FLEET_TELEMETRY_ARCHITECTURE.md` for where those fit later.
