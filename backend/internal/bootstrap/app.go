// Package bootstrap wires all InfraMap dependencies and starts the application.
package bootstrap

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"os"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/internal/platform/httputil"
	"github.com/matheussouza/inframap/internal/platform/logger"
	"github.com/matheussouza/inframap/modules/audit"
	"github.com/matheussouza/inframap/modules/configuration"
	"github.com/matheussouza/inframap/modules/configuration/controller"
	"github.com/matheussouza/inframap/modules/configuration/repository"
	"github.com/matheussouza/inframap/modules/configuration/usecase"
)

// App holds all application-wide dependencies.
type App struct {
	Logger   *slog.Logger
	DB       *pgxpool.Pool
	EventBus eventbus.EventBus
	Router   http.Handler
}

// Config holds bootstrap configuration parameters.
type Config struct {
	DatabaseURL string
}

// NewConfigFromEnv loads configuration parameters from environment variables.
func NewConfigFromEnv() Config {
	dbURL := os.Getenv("DATABASE_URL")
	if dbURL == "" {
		dbURL = "postgres://inframap:inframap_dev_pass@localhost:5432/inframap?sslmode=disable"
	}
	return Config{DatabaseURL: dbURL}
}

// New initializes and wires all application components.
func New(ctx context.Context, cfg Config) (*App, error) {
	log := logger.New()

	// 1. Database Connection Pool
	poolConfig, err := pgxpool.ParseConfig(cfg.DatabaseURL)
	if err != nil {
		return nil, fmt.Errorf("invalid database URL: %w", err)
	}

	pool, err := pgxpool.NewWithConfig(ctx, poolConfig)
	if err != nil {
		return nil, fmt.Errorf("failed to create db pool: %w", err)
	}

	if err := pool.Ping(ctx); err != nil {
		log.Warn("database ping failed on bootstrap (continuing for offline/testing mode)", slog.Any("error", err))
	}

	// 2. In-Memory Event Bus
	bus := eventbus.NewInMemoryEventBus(eventbus.DefaultWorkers, 1000)

	// 3. Register Audit Logger Subscriber
	auditSubscriber := audit.NewSubscriber(pool)
	if err := auditSubscriber.Register(bus); err != nil {
		return nil, fmt.Errorf("failed to register audit subscriber: %w", err)
	}

	// 4. Initialize Configuration Module
	setupRepo := repository.NewPgSetupRepository(pool)
	setupUseCase := usecase.NewDefaultSetupUseCase(setupRepo, bus, log)
	setupCtrl := controller.NewSetupController(setupUseCase)

	// 5. Setup Router & Register Endpoints
	mux := http.NewServeMux()

	// Health endpoint
	mux.HandleFunc("GET /api/v1/health", func(w http.ResponseWriter, r *http.Request) {
		httputil.WriteJSON(w, r, http.StatusOK, map[string]string{
			"status":  "ok",
			"version": usecase.AppVersion,
		})
	})

	configuration.RegisterRoutes(mux, setupCtrl)

	// 6. Middleware Stack: RequestID -> SecurityHeaders -> Recovery -> Mux
	handler := httputil.RequestID(
		httputil.SecurityHeaders(
			httputil.Recovery(log)(mux),
		),
	)

	return &App{
		Logger:   log,
		DB:       pool,
		EventBus: bus,
		Router:   handler,
	}, nil
}

// Close gracefully releases application resources.
func (a *App) Close() {
	if a.EventBus != nil {
		_ = a.EventBus.Close()
	}
	if a.DB != nil {
		a.DB.Close()
	}
}
