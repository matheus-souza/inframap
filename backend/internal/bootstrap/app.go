// Package bootstrap wires all InfraMap dependencies and starts the application.
package bootstrap

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/matheussouza/inframap/internal/platform/crypto"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/internal/platform/httputil"
	"github.com/matheussouza/inframap/internal/platform/logger"
	"github.com/matheussouza/inframap/modules/audit"
	"github.com/matheussouza/inframap/modules/configuration"
	configctrl "github.com/matheussouza/inframap/modules/configuration/controller"
	configrepo "github.com/matheussouza/inframap/modules/configuration/repository"
	configuc "github.com/matheussouza/inframap/modules/configuration/usecase"
	"github.com/matheussouza/inframap/modules/discovery"
	discctrl "github.com/matheussouza/inframap/modules/discovery/controller"
	discrepo "github.com/matheussouza/inframap/modules/discovery/repository"
	discuc "github.com/matheussouza/inframap/modules/discovery/usecase"
	"github.com/matheussouza/inframap/modules/identity"
	identityctrl "github.com/matheussouza/inframap/modules/identity/controller"
	identityrepo "github.com/matheussouza/inframap/modules/identity/repository"
	identityuc "github.com/matheussouza/inframap/modules/identity/usecase"
	"github.com/matheussouza/inframap/modules/inventory"
	invctrl "github.com/matheussouza/inframap/modules/inventory/controller"
	invrepo "github.com/matheussouza/inframap/modules/inventory/repository"
	invuc "github.com/matheussouza/inframap/modules/inventory/usecase"
	"github.com/matheussouza/inframap/modules/integrations"
	integrationsctrl "github.com/matheussouza/inframap/modules/integrations/controller"
	dockerprovider "github.com/matheussouza/inframap/modules/integrations/providers/docker"
	proxmoxprovider "github.com/matheussouza/inframap/modules/integrations/providers/proxmox"
	integrationsreg "github.com/matheussouza/inframap/modules/integrations/registry"
	"github.com/matheussouza/inframap/modules/topology"
	topoctrl "github.com/matheussouza/inframap/modules/topology/controller"
	toporepo "github.com/matheussouza/inframap/modules/topology/repository"
	topouc "github.com/matheussouza/inframap/modules/topology/usecase"
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
	MasterKey   string
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
	return Config{
		DatabaseURL: dbURL,
		MasterKey:   os.Getenv("INFRAMAP_MASTER_KEY"),
	}
}

// New initializes and wires all application components.
func New(ctx context.Context, cfg Config) (*App, error) {
	log := logger.New()

	// 1. Validate & initialize encryptor (before any resource allocation)
	var encryptor crypto.Encryptor
	if cfg.MasterKey != "" {
		enc, encErr := crypto.NewAESGCMEncryptor(cfg.MasterKey)
		if encErr != nil {
			return nil, fmt.Errorf("failed to initialize encryptor: %w", encErr)
		}
		encryptor = enc
	} else {
		log.Warn("INFRAMAP_MASTER_KEY not set: discovery source config encryption unavailable (set this variable in production)")
	}

	// 2. Database Connection Pool
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

	// 3. In-Memory Event Bus
	bus := eventbus.NewInMemoryEventBus(eventbus.DefaultWorkers, 1000)

	// 4. Register Audit Logger Subscriber
	auditSubscriber := audit.NewSubscriber(pool)
	if err := auditSubscriber.Register(bus); err != nil {
		return nil, fmt.Errorf("failed to register audit subscriber: %w", err)
	}

	// 5. Initialize Configuration Module
	setupRepo := configrepo.NewPgSetupRepository(pool)
	setupUseCase := configuc.NewDefaultSetupUseCase(setupRepo, bus, log)
	setupCtrl := configctrl.NewSetupController(setupUseCase)

	// 6. Initialize Identity Module
	sessionRepo := identityrepo.NewPgSessionRepository(pool)
	identityUseCase := identityuc.NewDefaultIdentityUseCase(ctx, sessionRepo, bus, log)
	identityCtrl := identityctrl.NewIdentityController(identityUseCase)

	// 7. Initialize Inventory Module
	invRepo := invrepo.NewPgInventoryRepository(pool)
	invUseCase := invuc.NewDefaultInventoryUseCase(invRepo, bus, log)
	invCtrl := invctrl.NewInventoryController(invUseCase)

	// 8. Initialize Discovery Module
	discRepo := discrepo.NewPgDiscoveryRepository(pool, encryptor)
	discUseCase := discuc.NewDefaultDiscoveryUseCase(discRepo, invRepo, bus, log)
	discCtrl := discctrl.NewDiscoveryController(discUseCase)

	// 9. Initialize Topology Module
	queries := db.New(pool)
	topoRepo := toporepo.NewPgTopologyRepository(queries)
	topoUseCase := topouc.NewDefaultTopologyUseCase(topoRepo, invRepo, bus, log)
	topoCtrl := topoctrl.NewTopologyController(topoUseCase)

	_ = bus.Subscribe("device.created", topoUseCase.HandleDeviceEvent)
	_ = bus.Subscribe("device.updated", topoUseCase.HandleDeviceEvent)
	_ = bus.Subscribe("device.deleted", topoUseCase.HandleDeviceEvent)

	// 10. Setup Router & Register Endpoints
	mux := http.NewServeMux()

	// Health endpoint
	mux.HandleFunc("GET /api/v1/health", func(w http.ResponseWriter, r *http.Request) {
		status := "ok"
		pingCtx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
		defer cancel()
		if err := pool.Ping(pingCtx); err != nil {
			status = "degraded"
		}
		httputil.WriteJSON(w, r, http.StatusOK, map[string]string{
			"status":  status,
			"version": configuc.AppVersion,
		})
	})

	// Wire Integrations Registry & Native Providers
	integRegistry := integrationsreg.NewRegistry()
	_ = integRegistry.Register(proxmoxprovider.NewProvider())
	_ = integRegistry.Register(dockerprovider.NewProvider())
	integCtrl := integrationsctrl.NewIntegrationsController(integRegistry)

	configuration.RegisterRoutes(mux, setupCtrl)
	identity.RegisterRoutes(mux, identityCtrl)
	inventory.RegisterRoutes(mux, invCtrl)
	discovery.RegisterRoutes(mux, discCtrl)
	topology.RegisterRoutes(mux, topoCtrl)
	integrations.RegisterRoutes(mux, integCtrl)

	validator := &sessionValidatorAdapter{repo: sessionRepo}

	// 10. Middleware Stack: RequestID -> SecurityHeaders -> LimitBody -> Recovery -> AuthMiddleware -> Mux
	handler := httputil.RequestID(
		httputil.SecurityHeaders(
			httputil.LimitBody(
				httputil.Recovery(log)(
					httputil.AuthMiddleware(validator)(mux),
				),
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
