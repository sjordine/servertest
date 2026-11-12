package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"servertest/stock-service/auth"
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

	authCfg, err := auth.ConfigFromEnv()
	if err != nil {
		log.Fatal(err)
	}
	verifier := auth.NewVerifier(context.Background(), authCfg)

	// Calls to other services authenticate as stock-service (client credentials).
	serviceClient, err := auth.ServiceClientFromEnv(context.Background())
	if err != nil {
		log.Fatal(err)
	}
	productsURL := os.Getenv("PRODUCTS_URL")
	if productsURL == "" {
		productsURL = "http://products:8080"
	}

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

	// Protected example: needs a user token with stock:read, then calls the products service
	// with stock's own service token. Proves both user -> stock and stock -> products auth.
	mux.Handle("GET /api/stock/auth-check", verifier.Middleware("stock:read")(http.HandlerFunc(
		func(w http.ResponseWriter, r *http.Request) {
			claims, _ := auth.ClaimsFrom(r.Context())
			resp := map[string]any{
				"service": "stock",
				"caller": map[string]any{
					"subject":  claims.Subject,
					"username": claims.PreferredUsername,
					"client":   claims.AuthorizedParty,
					"scopes":   claims.Scopes,
				},
			}
			units, err := countProductUnits(r.Context(), serviceClient, productsURL)
			if err != nil {
				log.Printf("auth-check: calling products failed: %v", err)
				resp["productsCall"] = map[string]any{"ok": false, "error": err.Error()}
				writeJSON(w, http.StatusBadGateway, resp)
				return
			}
			resp["productsCall"] = map[string]any{"ok": true, "as": "stock-service", "unitCount": units}
			writeJSON(w, http.StatusOK, resp)
		})))

	addr := ":8080"
	log.Printf("stock-service listening on %s", addr)
	log.Fatal(http.ListenAndServe(addr, mux))
}

// countProductUnits calls GET /api/products/units on the products service with stock's service token.
func countProductUnits(ctx context.Context, client *http.Client, productsURL string) (int, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, productsURL+"/api/products/units", nil)
	if err != nil {
		return 0, err
	}
	res, err := client.Do(req)
	if err != nil {
		return 0, err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return 0, fmt.Errorf("products answered HTTP %d", res.StatusCode)
	}
	var units []json.RawMessage
	if err := json.NewDecoder(res.Body).Decode(&units); err != nil {
		return 0, err
	}
	return len(units), nil
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
