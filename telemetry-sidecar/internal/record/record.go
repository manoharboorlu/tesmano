// Package record decodes Tesla Fleet Telemetry logger-dispatcher JSON lines into a
// normalized, generic shape the store package can persist without a schema change
// per signal.
//
// The logger dispatcher (teslamotors/fleet-telemetry, "logger" datastore) emits one
// JSON object per line, shaped like:
//
//	{
//	  "Vin": "XP7YGCEK9PB00000",
//	  "CreatedAt": "2025-01-16T09:08:39Z",
//	  "ModuleTempMin": {"doubleValue": 24.5},
//	  "ModuleTempMax": {"doubleValue": 29.1},
//	  "Location": {"locationValue": {"latitude": 37.7, "longitude": -122.4}}
//	}
//
// Every key other than Vin/CreatedAt/IsResend is a signal name mapped to a typed
// value object with exactly one populated variant. Tesla decides the variant per
// field per firmware build (e.g. some numeric fields arrive as stringValue), so the
// decoder trusts whichever variant is present rather than guessing from the field
// name.
package record

import (
	"encoding/json"
	"fmt"
	"time"
)

// ValueType is the normalized storage type for a Signal.
type ValueType string

const (
	Num  ValueType = "NUM"
	Str  ValueType = "STR"
	Bool ValueType = "BOOL"
)

// Signal is one decoded, typed telemetry field observation.
type Signal struct {
	VIN       string
	Timestamp time.Time
	Name      string
	Type      ValueType
	NumValue  float64
	StrValue  string
	BoolValue bool
}

// Location is one decoded location observation, kept separate from Signal because
// TesMano route/map queries always need latitude and longitude together.
type Location struct {
	VIN       string
	Timestamp time.Time
	Lat       float64
	Lon       float64
}

// Batch is everything decoded from a single JSON line (one Tesla Payload record).
type Batch struct {
	Signals   []Signal
	Locations []Location
}

type teslaValue struct {
	StringValue   *string  `json:"stringValue"`
	DoubleValue   *float64 `json:"doubleValue"`
	FloatValue    *float64 `json:"floatValue"`
	IntValue      *int64   `json:"intValue"`
	LongValue     *int64   `json:"longValue"`
	BooleanValue  *bool    `json:"booleanValue"`
	Invalid       *bool    `json:"invalid"`
	LocationValue *struct {
		Latitude  float64 `json:"latitude"`
		Longitude float64 `json:"longitude"`
	} `json:"locationValue"`
}

// reservedKeys are top-level record keys that are metadata, not signals.
var reservedKeys = map[string]bool{
	"Vin":       true,
	"CreatedAt": true,
	"IsResend":  true,
}

// Decode parses one JSONL line into a Batch. A record with no usable VIN/CreatedAt
// is rejected; individual fields marked "invalid" by Tesla are silently skipped
// (unavailable is not zero) rather than stored as a fabricated value.
func Decode(line []byte) (Batch, error) {
	var raw map[string]json.RawMessage
	if err := json.Unmarshal(line, &raw); err != nil {
		return Batch{}, fmt.Errorf("record: invalid JSON line: %w", err)
	}

	var vin string
	if v, ok := raw["Vin"]; ok {
		if err := json.Unmarshal(v, &vin); err != nil {
			return Batch{}, fmt.Errorf("record: invalid Vin: %w", err)
		}
	}
	if vin == "" {
		return Batch{}, fmt.Errorf("record: missing Vin")
	}

	var createdAtRaw string
	if v, ok := raw["CreatedAt"]; ok {
		if err := json.Unmarshal(v, &createdAtRaw); err != nil {
			return Batch{}, fmt.Errorf("record: invalid CreatedAt: %w", err)
		}
	}
	if createdAtRaw == "" {
		return Batch{}, fmt.Errorf("record: missing CreatedAt")
	}
	ts, err := time.Parse(time.RFC3339, createdAtRaw)
	if err != nil {
		return Batch{}, fmt.Errorf("record: unparseable CreatedAt %q: %w", createdAtRaw, err)
	}

	var batch Batch
	for key, rawValue := range raw {
		if reservedKeys[key] {
			continue
		}
		var tv teslaValue
		if err := json.Unmarshal(rawValue, &tv); err != nil {
			// A field this decoder doesn't understand yet; skip it rather than
			// fail the whole record so new Tesla fields never block ingest.
			continue
		}
		if tv.Invalid != nil && *tv.Invalid {
			continue
		}
		switch {
		case tv.LocationValue != nil:
			batch.Locations = append(batch.Locations, Location{
				VIN: vin, Timestamp: ts,
				Lat: tv.LocationValue.Latitude, Lon: tv.LocationValue.Longitude,
			})
		case tv.DoubleValue != nil:
			batch.Signals = append(batch.Signals, Signal{VIN: vin, Timestamp: ts, Name: key, Type: Num, NumValue: *tv.DoubleValue})
		case tv.FloatValue != nil:
			batch.Signals = append(batch.Signals, Signal{VIN: vin, Timestamp: ts, Name: key, Type: Num, NumValue: *tv.FloatValue})
		case tv.IntValue != nil:
			batch.Signals = append(batch.Signals, Signal{VIN: vin, Timestamp: ts, Name: key, Type: Num, NumValue: float64(*tv.IntValue)})
		case tv.LongValue != nil:
			batch.Signals = append(batch.Signals, Signal{VIN: vin, Timestamp: ts, Name: key, Type: Num, NumValue: float64(*tv.LongValue)})
		case tv.BooleanValue != nil:
			batch.Signals = append(batch.Signals, Signal{VIN: vin, Timestamp: ts, Name: key, Type: Bool, BoolValue: *tv.BooleanValue})
		case tv.StringValue != nil:
			batch.Signals = append(batch.Signals, Signal{VIN: vin, Timestamp: ts, Name: key, Type: Str, StrValue: *tv.StringValue})
		}
	}
	return batch, nil
}
