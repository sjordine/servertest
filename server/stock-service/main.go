package main

import (
	"context"
	"encoding/json"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
)

type helloResponse struct {
	Service  string    `json:"service"`
	Language string    `json:"language"`
	Message  string    `json:"message"`
	DBTime   time.Time `json:"dbTime"`
}

func main() {
	dsn := os.Getenv("DATABASE_URL")
	if dsn == "" {
		log.Fatal("DATABASE_URL is not set")
	}

	pool, err := pgxpool.New(context.Background(), dsn)
	if err != nil {
		log.Fatalf("invalid DATABASE_URL: %v", err)
	}
	defer pool.Close()

	mux := http.NewServeMux()

	mux.HandleFunc("GET /api/stock/hello", func(w http.ResponseWriter, r *http.Request) {
		resp := helloResponse{Service: "stock", Language: "go"}
		err := pool.QueryRow(r.Context(),
			"SELECT message, now() FROM greeting ORDER BY id LIMIT 1",
		).Scan(&resp.Message, &resp.DBTime)
		if err != nil {
			log.Printf("hello query failed: %v", err)
			writeJSON(w, http.StatusInternalServerError, map[string]string{"error": err.Error()})
			return
		}
		writeJSON(w, http.StatusOK, resp)
	})

	mux.HandleFunc("GET /api/stock/health", func(w http.ResponseWriter, r *http.Request) {
		if err := pool.Ping(r.Context()); err != nil {
			writeJSON(w, http.StatusServiceUnavailable, map[string]string{"status": "DOWN", "error": err.Error()})
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"status": "UP"})
	})

	addr := ":8080"
	log.Printf("stock-service listening on %s", addr)
	log.Fatal(http.ListenAndServe(addr, mux))
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
