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
	configctrl "github.com/matheussouza/inframap/modules/configuration/controller"
	configrepo "github.com/matheussouza/inframap/modules/configuration/repository"
	configuc "github.com/matheussouza/inframap/modules/configuration/usecase"
	"github.com/matheussouza/inframap/modules/identity"
	identityctrl "github.com/matheussouza/inframap/modules/identity/controller"
	identityrepo "github.com/matheussouza/inframap/modules/identity/repository"
	identityuc "github.com/matheussouza/inframap/modules/identity/usecase"
	"github.com/matheussouza/inframap/modules/inventory"
	invctrl "github.com/matheussouza/inframap/modules/inventory/controller"
	invrepo "github.com/matheussouza/inframap/modules/inventory/repository"
	invuc "github.com/matheussouza/inframap/modules/inventory/usecase"
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

type sessionValidatorAdapter struct {
	repo identityrepo.SessionRepository
}

func (s *sessionValidatorAdapter) ValidateSession(ctx context.Context, token string) (string, []string, error) {
	session, err := s.repo.GetSessionByToken(ctx, token)
	if err != nil {
		return "", nil, err
	}
	perms, _ := s.repo.GetUserPermissions(ctx, session.UserID)
	return session.UserID.String(), perms, nil
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
	setupRepo := configrepo.NewPgSetupRepository(pool)
	setupUseCase := configuc.NewDefaultSetupUseCase(setupRepo, bus, log)
	setupCtrl := configctrl.NewSetupController(setupUseCase)

	// 5. Initialize Identity Module
	sessionRepo := identityrepo.NewPgSessionRepository(pool)
	identityUseCase := identityuc.NewDefaultIdentityUseCase(pool, sessionRepo, bus, log)
	identityCtrl := identityctrl.NewIdentityController(identityUseCase)

	// 6. Initialize Inventory Module
	invRepo := invrepo.NewPgInventoryRepository(pool)
	invUseCase := invuc.NewDefaultInventoryUseCase(invRepo, bus, log)
	invCtrl := invctrl.NewInventoryController(invUseCase)

	// 7. Setup Router & Register Endpoints
	mux := http.NewServeMux()

	// Health endpoint
	mux.HandleFunc("GET /api/v1/health", func(w http.ResponseWriter, r *http.Request) {
		httputil.WriteJSON(w, r, http.StatusOK, map[string]string{
			"status":  "ok",
			"version": configuc.AppVersion,
		})
	})

	configuration.RegisterRoutes(mux, setupCtrl)
	identity.RegisterRoutes(mux, identityCtrl)
	inventory.RegisterRoutes(mux, invCtrl)

	// 7. Middleware Stack: RequestID -> SecurityHeaders -> Recovery -> AuthMiddleware -> Mux
	validator := &sessionValidatorAdapter{repo: sessionRepo}
	handler := httputil.RequestID(
		httputil.SecurityHeaders(
			httputil.Recovery(log)(
				httputil.AuthMiddleware(validator)(mux),
			),
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
