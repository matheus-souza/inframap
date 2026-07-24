// Package main is the entry point for the InfraMap API server.
package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"time"
)

// HealthResponse represents the health check endpoint response.
type HealthResponse struct {
	Status  string `json:"status"`
	Version string `json:"version"`
}

func healthHandler(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)

	resp := HealthResponse{
		Status:  "ok",
		Version: "v1.0.0-rc.1",
	}

	if err := json.NewEncoder(w).Encode(resp); err != nil {
		log.Printf("failed to encode health response: %v", err)
	}
}

func setupRouter() *http.ServeMux {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /api/v1/health", healthHandler)
	return mux
}

func getPort() string {
	port := os.Getenv("INFRAMAP_PORT")
	if port == "" {
		port = "8055"
	}
	return port
}

func main() {
	port := getPort()
	addr := fmt.Sprintf(":%s", port)
	router := setupRouter()

	server := &http.Server{
		Addr:              addr,
		Handler:           router,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      10 * time.Second,
		IdleTimeout:       30 * time.Second,
	}

	log.Printf("InfraMap API server starting on http://localhost%s", addr)
	// nosemgrep: go.lang.security.audit.net.use-tls.use-tls
	if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatalf("server exited unexpectedly: %v", err)
	}
}
