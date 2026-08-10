// Command ingest reads Tesla Fleet Telemetry logger-dispatcher JSONL records from
// stdin, one record per line, and persists them into the sidecar's SQLite store.
// It is designed so the official fleet-telemetry server's logger output can later
// be piped straight into this process's stdin.
package main

import (
	"bufio"
	"flag"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/manoharboorlu/tesmano/telemetry-sidecar/internal/record"
	"github.com/manoharboorlu/tesmano/telemetry-sidecar/internal/store"
)

func main() {
	dbPath := flag.String("db", "./data/telemetry.db", "path to the SQLite database file")
	flag.Parse()

	st, err := store.Open(*dbPath)
	if err != nil {
		log.Fatalf("ingest: %v", err)
	}
	defer st.Close()

	scanner := bufio.NewScanner(os.Stdin)
	scanner.Buffer(make([]byte, 0, 64*1024), 1024*1024)

	var accepted, skipped int
	for scanner.Scan() {
		line := scanner.Bytes()
		if len(line) == 0 {
			continue
		}
		batch, err := record.Decode(line)
		if err != nil {
			log.Printf("ingest: skipping unparseable line: %v", err)
			skipped++
			continue
		}
		if err := st.Insert(batch, time.Now()); err != nil {
			log.Printf("ingest: skipping line, insert failed: %v", err)
			skipped++
			continue
		}
		accepted++
	}
	if err := scanner.Err(); err != nil {
		log.Fatalf("ingest: reading stdin: %v", err)
	}

	fmt.Fprintf(os.Stderr, "ingest: accepted=%d skipped=%d db=%s\n", accepted, skipped, *dbPath)
}
