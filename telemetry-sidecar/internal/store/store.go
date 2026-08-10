// Package store persists decoded telemetry into SQLite with a generic, per-signal
// event schema (see docs/TESMANO_FLEET_TELEMETRY_ARCHITECTURE.md) so a new Tesla
// field never requires a migration.
package store

import (
	"database/sql"
	"fmt"
	"time"

	_ "modernc.org/sqlite"

	"github.com/manoharboorlu/tesmano/telemetry-sidecar/internal/record"
)

const schema = `
CREATE TABLE IF NOT EXISTS telemetry_signal (
    vin         TEXT NOT NULL,
    ts          TEXT NOT NULL,
    signal      TEXT NOT NULL,
    value_type  TEXT NOT NULL,
    value_num   REAL,
    value_str   TEXT,
    value_bool  INTEGER,
    received_at TEXT NOT NULL,
    PRIMARY KEY (vin, signal, ts)
);
CREATE INDEX IF NOT EXISTS idx_signal_lookup ON telemetry_signal (vin, signal, ts DESC);

CREATE TABLE IF NOT EXISTS telemetry_location (
    vin         TEXT NOT NULL,
    ts          TEXT NOT NULL,
    lat         REAL NOT NULL,
    lon         REAL NOT NULL,
    received_at TEXT NOT NULL,
    PRIMARY KEY (vin, ts)
);
`

// Store wraps a SQLite connection configured for the sidecar's single-writer,
// possibly-concurrent-readers usage pattern.
type Store struct {
	db *sql.DB
}

// Open opens (creating if needed) the SQLite database at path, sets WAL mode, and
// ensures the schema exists.
func Open(path string) (*Store, error) {
	db, err := sql.Open("sqlite", path+"?_pragma=journal_mode(WAL)&_pragma=synchronous(NORMAL)&_pragma=busy_timeout(5000)")
	if err != nil {
		return nil, fmt.Errorf("store: open: %w", err)
	}
	if _, err := db.Exec(schema); err != nil {
		db.Close()
		return nil, fmt.Errorf("store: schema: %w", err)
	}
	return &Store{db: db}, nil
}

func (s *Store) Close() error { return s.db.Close() }

// Insert persists one decoded batch. Signals/locations are upserted on the
// (vin, signal|--, ts) primary key so a Tesla resend safely overwrites rather than
// erroring or duplicating.
func (s *Store) Insert(b record.Batch, receivedAt time.Time) error {
	tx, err := s.db.Begin()
	if err != nil {
		return fmt.Errorf("store: begin: %w", err)
	}
	defer tx.Rollback()

	sigStmt, err := tx.Prepare(`
		INSERT INTO telemetry_signal (vin, ts, signal, value_type, value_num, value_str, value_bool, received_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?)
		ON CONFLICT (vin, signal, ts) DO UPDATE SET
			value_type = excluded.value_type,
			value_num = excluded.value_num,
			value_str = excluded.value_str,
			value_bool = excluded.value_bool,
			received_at = excluded.received_at
	`)
	if err != nil {
		return fmt.Errorf("store: prepare signal: %w", err)
	}
	defer sigStmt.Close()

	locStmt, err := tx.Prepare(`
		INSERT INTO telemetry_location (vin, ts, lat, lon, received_at)
		VALUES (?, ?, ?, ?, ?)
		ON CONFLICT (vin, ts) DO UPDATE SET lat = excluded.lat, lon = excluded.lon, received_at = excluded.received_at
	`)
	if err != nil {
		return fmt.Errorf("store: prepare location: %w", err)
	}
	defer locStmt.Close()

	receivedAtStr := receivedAt.UTC().Format(time.RFC3339Nano)
	for _, sig := range b.Signals {
		var num sql.NullFloat64
		var str sql.NullString
		var bl sql.NullInt64
		switch sig.Type {
		case record.Num:
			num = sql.NullFloat64{Float64: sig.NumValue, Valid: true}
		case record.Str:
			str = sql.NullString{String: sig.StrValue, Valid: true}
		case record.Bool:
			v := int64(0)
			if sig.BoolValue {
				v = 1
			}
			bl = sql.NullInt64{Int64: v, Valid: true}
		}
		if _, err := sigStmt.Exec(sig.VIN, sig.Timestamp.UTC().Format(time.RFC3339Nano), sig.Name, string(sig.Type), num, str, bl, receivedAtStr); err != nil {
			return fmt.Errorf("store: insert signal %s: %w", sig.Name, err)
		}
	}
	for _, loc := range b.Locations {
		if _, err := locStmt.Exec(loc.VIN, loc.Timestamp.UTC().Format(time.RFC3339Nano), loc.Lat, loc.Lon, receivedAtStr); err != nil {
			return fmt.Errorf("store: insert location: %w", err)
		}
	}
	return tx.Commit()
}

// SignalRow is one row read back from telemetry_signal, with the typed value
// resolved into a single interface{} for JSON encoding.
type SignalRow struct {
	VIN        string      `json:"vin"`
	Timestamp  string      `json:"ts"`
	Signal     string      `json:"signal"`
	ValueType  string      `json:"value_type"`
	Value      interface{} `json:"value"`
	ReceivedAt string      `json:"received_at"`
}

func scanSignalRow(rows *sql.Rows) (SignalRow, error) {
	var r SignalRow
	var num sql.NullFloat64
	var str sql.NullString
	var bl sql.NullInt64
	if err := rows.Scan(&r.VIN, &r.Timestamp, &r.Signal, &r.ValueType, &num, &str, &bl, &r.ReceivedAt); err != nil {
		return r, err
	}
	switch r.ValueType {
	case string(record.Num):
		r.Value = num.Float64
	case string(record.Str):
		r.Value = str.String
	case string(record.Bool):
		r.Value = bl.Int64 == 1
	}
	return r, nil
}

// Latest returns the single most recent row for a signal, optionally scoped to a
// VIN. ok is false if no matching row exists.
func (s *Store) Latest(vin, signal string) (row SignalRow, ok bool, err error) {
	query := `SELECT vin, ts, signal, value_type, value_num, value_str, value_bool, received_at
		FROM telemetry_signal WHERE signal = ?`
	args := []interface{}{signal}
	if vin != "" {
		query += " AND vin = ?"
		args = append(args, vin)
	}
	query += " ORDER BY ts DESC LIMIT 1"

	rows, err := s.db.Query(query, args...)
	if err != nil {
		return SignalRow{}, false, fmt.Errorf("store: latest query: %w", err)
	}
	defer rows.Close()
	if !rows.Next() {
		return SignalRow{}, false, nil
	}
	row, err = scanSignalRow(rows)
	if err != nil {
		return SignalRow{}, false, fmt.Errorf("store: latest scan: %w", err)
	}
	return row, true, nil
}

// History returns rows for a signal within [from, to] (RFC3339, inclusive),
// newest first, bounded by limit (callers must supply a sane cap).
func (s *Store) History(vin, signal, from, to string, limit int) ([]SignalRow, error) {
	query := `SELECT vin, ts, signal, value_type, value_num, value_str, value_bool, received_at
		FROM telemetry_signal WHERE signal = ?`
	args := []interface{}{signal}
	if vin != "" {
		query += " AND vin = ?"
		args = append(args, vin)
	}
	if from != "" {
		query += " AND ts >= ?"
		args = append(args, from)
	}
	if to != "" {
		query += " AND ts <= ?"
		args = append(args, to)
	}
	query += " ORDER BY ts DESC LIMIT ?"
	args = append(args, limit)

	rows, err := s.db.Query(query, args...)
	if err != nil {
		return nil, fmt.Errorf("store: history query: %w", err)
	}
	defer rows.Close()

	var out []SignalRow
	for rows.Next() {
		row, err := scanSignalRow(rows)
		if err != nil {
			return nil, fmt.Errorf("store: history scan: %w", err)
		}
		out = append(out, row)
	}
	return out, rows.Err()
}
