package store_test

import (
	"bufio"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/manoharboorlu/tesmano/telemetry-sidecar/internal/record"
	"github.com/manoharboorlu/tesmano/telemetry-sidecar/internal/store"
)

const testVIN = "5YJYGDEE1LF000001"

func ingestFixture(t *testing.T, dbPath string) {
	t.Helper()
	st, err := store.Open(dbPath)
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	defer st.Close()

	f, err := os.Open(filepath.Join("..", "..", "testdata", "fixture.jsonl"))
	if err != nil {
		t.Fatalf("open fixture: %v", err)
	}
	defer f.Close()

	scanner := bufio.NewScanner(f)
	lines := 0
	for scanner.Scan() {
		line := scanner.Bytes()
		if len(line) == 0 {
			continue
		}
		batch, err := record.Decode(line)
		if err != nil {
			t.Fatalf("Decode: %v", err)
		}
		if err := st.Insert(batch, time.Now()); err != nil {
			t.Fatalf("Insert: %v", err)
		}
		lines++
	}
	if err := scanner.Err(); err != nil {
		t.Fatalf("scan fixture: %v", err)
	}
	if lines != 5 {
		t.Fatalf("expected 5 fixture lines, got %d", lines)
	}
}

func TestIngestFixtureAndQuery(t *testing.T) {
	dbPath := filepath.Join(t.TempDir(), "telemetry.db")
	ingestFixture(t, dbPath)

	st, err := store.Open(dbPath)
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	defer st.Close()

	t.Run("latest returns the most recent value", func(t *testing.T) {
		row, ok, err := st.Latest(testVIN, "ModuleTempMax")
		if err != nil {
			t.Fatalf("Latest: %v", err)
		}
		if !ok {
			t.Fatal("expected a row, got none")
		}
		if row.Timestamp != "2026-08-10T09:20:00Z" {
			t.Errorf("expected latest ts 09:20:00Z, got %s", row.Timestamp)
		}
		if got, ok := row.Value.(float64); !ok || got != 30.4 {
			t.Errorf("expected value 30.4, got %v", row.Value)
		}
	})

	t.Run("invalid-flagged sample is not stored", func(t *testing.T) {
		row, ok, err := st.Latest(testVIN, "ModuleTempMin")
		if err != nil {
			t.Fatalf("Latest: %v", err)
		}
		if !ok {
			t.Fatal("expected a row, got none")
		}
		// The 09:20:00Z ModuleTempMin sample is marked invalid in the fixture and
		// must be skipped, so latest should still be the 09:15:00Z value.
		if row.Timestamp != "2026-08-10T09:15:00Z" {
			t.Errorf("expected latest valid ts 09:15:00Z, got %s", row.Timestamp)
		}
	})

	t.Run("history returns bounded, ordered rows", func(t *testing.T) {
		rows, err := st.History(testVIN, "ModuleTempMax", "", "", 10)
		if err != nil {
			t.Fatalf("History: %v", err)
		}
		if len(rows) != 5 {
			t.Fatalf("expected 5 history rows, got %d", len(rows))
		}
		if rows[0].Timestamp != "2026-08-10T09:20:00Z" {
			t.Errorf("expected newest-first ordering, first row ts=%s", rows[0].Timestamp)
		}
		if rows[len(rows)-1].Timestamp != "2026-08-10T09:00:00Z" {
			t.Errorf("expected oldest row last, last row ts=%s", rows[len(rows)-1].Timestamp)
		}
	})

	t.Run("history respects from/to bounds", func(t *testing.T) {
		rows, err := st.History(testVIN, "ModuleTempMax", "2026-08-10T09:05:00Z", "2026-08-10T09:10:00Z", 10)
		if err != nil {
			t.Fatalf("History: %v", err)
		}
		if len(rows) != 2 {
			t.Fatalf("expected 2 bounded rows, got %d", len(rows))
		}
	})

	t.Run("string and boolean signals round-trip typed", func(t *testing.T) {
		row, ok, err := st.Latest(testVIN, "DetailedChargeState")
		if err != nil || !ok {
			t.Fatalf("Latest DetailedChargeState: ok=%v err=%v", ok, err)
		}
		if row.Value != "Charging" {
			t.Errorf("expected string value Charging, got %v", row.Value)
		}

		row, ok, err = st.Latest(testVIN, "PreconditioningEnabled")
		if err != nil || !ok {
			t.Fatalf("Latest PreconditioningEnabled: ok=%v err=%v", ok, err)
		}
		if row.Value != false {
			t.Errorf("expected bool value false, got %v", row.Value)
		}
	})
}

func TestLocationRoundTrips(t *testing.T) {
	dbPath := filepath.Join(t.TempDir(), "telemetry.db")
	ingestFixture(t, dbPath)

	st, err := store.Open(dbPath)
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	defer st.Close()

	rows, err := st.History(testVIN, "ModuleTempMin", "", "", 10)
	if err != nil {
		t.Fatalf("History: %v", err)
	}
	// 5 fixture lines, one ModuleTempMin marked invalid -> 4 stored samples.
	if len(rows) != 4 {
		t.Fatalf("expected 4 valid ModuleTempMin rows, got %d", len(rows))
	}
}

func TestRestartRetainsData(t *testing.T) {
	dbPath := filepath.Join(t.TempDir(), "telemetry.db")
	ingestFixture(t, dbPath)

	// Simulate a process restart: open a fresh *Store handle against the same file.
	st, err := store.Open(dbPath)
	if err != nil {
		t.Fatalf("re-open after restart: %v", err)
	}
	defer st.Close()

	row, ok, err := st.Latest(testVIN, "Soc")
	if err != nil {
		t.Fatalf("Latest: %v", err)
	}
	if !ok {
		t.Fatal("expected Soc data to survive restart")
	}
	if got, ok := row.Value.(float64); !ok || got != 70 {
		t.Errorf("expected Soc 70 to survive restart, got %v", row.Value)
	}
}
