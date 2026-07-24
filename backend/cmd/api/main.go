// Package main is the entry point for the InfraMap API server.
package main

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/matheussouza/inframap/internal/bootstrap"
)

func getPort() string {
	port := os.Getenv("INFRAMAP_PORT")
	if port == "" {
		port = "8055"
	}
	return port
}

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	cfg := bootstrap.NewConfigFromEnv()
	app, err := bootstrap.New(ctx, cfg)
	if err != nil {
		log.Fatalf("failed to bootstrap InfraMap application: %v", err)
	}
	defer app.Close()

	port := getPort()
	addr := fmt.Sprintf(":%s", port)

	server := &http.Server{
		Addr:              addr,
		Handler:           app.Router,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      10 * time.Second,
		IdleTimeout:       30 * time.Second,
	}

	go func() {
		log.Printf("InfraMap API server starting on http://localhost%s", addr)
		// nosemgrep: go.lang.security.audit.net.use-tls.use-tls
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("server exited unexpectedly: %v", err)
		}
	}()

	<-ctx.Done()
	log.Println("Shutting down InfraMap API server gracefully...")

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := server.Shutdown(shutdownCtx); err != nil {
		log.Printf("server forced to shutdown: %v", err)
	}

	log.Println("InfraMap API server stopped.")
}
