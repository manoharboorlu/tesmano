// Command api serves a tiny read-only HTTP/JSON API over the sidecar's SQLite
// store. It never accepts writes and never sees Tesla developer credentials.
package main

import (
	"encoding/json"
	"flag"
	"log"
	"net"
	"net/http"
	"strconv"
	"strings"

	"github.com/manoharboorlu/tesmano/telemetry-sidecar/internal/store"
)

const (
	defaultHistoryLimit = 500
	maxHistoryLimit     = 5000
)

func main() {
	dbPath := flag.String("db", "./data/telemetry.db", "path to the SQLite database file")
	addr := flag.String("addr", "127.0.0.1:8081", "listen address")
	authMode := flag.String("auth", "none", `"none" (dev, loopback-only) or "bearer"`)
	token := flag.String("token", "", "required bearer token when -auth=bearer (never a Tesla credential)")
	flag.Parse()

	if *authMode == "none" {
		if err := requireLoopback(*addr); err != nil {
			log.Fatalf("api: refusing to start: %v", err)
		}
		log.Printf("api: dev mode, no auth, bound to loopback only (%s)", *addr)
	} else if *authMode == "bearer" {
		if *token == "" {
			log.Fatalf("api: -auth=bearer requires -token")
		}
	} else {
		log.Fatalf("api: unknown -auth mode %q", *authMode)
	}

	st, err := store.Open(*dbPath)
	if err != nil {
		log.Fatalf("api: %v", err)
	}
	defer st.Close()

	mux := http.NewServeMux()
	mux.HandleFunc("/health", handleHealth)
	mux.HandleFunc("/api/v1/signals/latest", handleLatest(st))
	mux.HandleFunc("/api/v1/signals/history", handleHistory(st))

	handler := authMiddleware(*authMode, *token, mux)
	log.Printf("api: listening on %s (auth=%s, db=%s)", *addr, *authMode, *dbPath)
	log.Fatal(http.ListenAndServe(*addr, handler))
}

// requireLoopback refuses to run unauthenticated on anything but localhost, so a
// misconfigured -addr can never expose the API without a token.
func requireLoopback(addr string) error {
	host, _, err := net.SplitHostPort(addr)
	if err != nil {
		return err
	}
	if host == "localhost" {
		return nil
	}
	ip := net.ParseIP(host)
	if ip != nil && ip.IsLoopback() {
		return nil
	}
	return errNotLoopback(host)
}

type errNotLoopback string

func (e errNotLoopback) Error() string {
	return "auth=none requires a loopback address, got host " + string(e)
}

func authMiddleware(mode, token string, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/health" {
			next.ServeHTTP(w, r)
			return
		}
		if mode == "bearer" {
			got := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
			if got == "" || got != token {
				http.Error(w, `{"error":"unauthorized"}`, http.StatusUnauthorized)
				return
			}
		}
		next.ServeHTTP(w, r)
	})
}

func writeJSON(w http.ResponseWriter, status int, v interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}

func handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

func handleLatest(st *store.Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		signal := r.URL.Query().Get("signal")
		if signal == "" {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "signal query parameter is required"})
			return
		}
		vin := r.URL.Query().Get("vin")
		row, ok, err := st.Latest(vin, signal)
		if err != nil {
			writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
			return
		}
		if !ok {
			writeJSON(w, http.StatusNotFound, map[string]string{"error": "no data for signal " + signal})
			return
		}
		writeJSON(w, http.StatusOK, row)
	}
}

func handleHistory(st *store.Store) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		signal := r.URL.Query().Get("signal")
		if signal == "" {
			writeJSON(w, http.StatusBadRequest, map[string]string{"error": "signal query parameter is required"})
			return
		}
		vin := r.URL.Query().Get("vin")
		from := r.URL.Query().Get("from")
		to := r.URL.Query().Get("to")

		limit := defaultHistoryLimit
		if raw := r.URL.Query().Get("limit"); raw != "" {
			n, err := strconv.Atoi(raw)
			if err != nil || n <= 0 {
				writeJSON(w, http.StatusBadRequest, map[string]string{"error": "limit must be a positive integer"})
				return
			}
			limit = n
		}
		if limit > maxHistoryLimit {
			limit = maxHistoryLimit
		}

		rows, err := st.History(vin, signal, from, to, limit)
		if err != nil {
			writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
			return
		}
		writeJSON(w, http.StatusOK, map[string]interface{}{
			"signal": signal,
			"count":  len(rows),
			"rows":   rows,
		})
	}
}
