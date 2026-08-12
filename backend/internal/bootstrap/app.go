// Package bootstrap wires all InfraMap dependencies and starts the application.
package bootstrap

import (
	"context"
	"crypto/rand"
	"fmt"
	"io/fs"
	"log/slog"
	"net/http"
	"net/url"
	"os"

	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/matheussouza/inframap/internal/platform/crypto"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/internal/platform/httputil"
	"github.com/matheussouza/inframap/internal/platform/netinfo"
	"github.com/matheussouza/inframap/internal/platform/spa"
	"github.com/matheussouza/inframap/internal/platform/logger"
	"github.com/matheussouza/inframap/modules/audit"
	"github.com/matheussouza/inframap/modules/configuration"
	configctrl "github.com/matheussouza/inframap/modules/configuration/controller"
	configrepo "github.com/matheussouza/inframap/modules/configuration/repository"
	configuc "github.com/matheussouza/inframap/modules/configuration/usecase"
	"github.com/matheussouza/inframap/modules/credentials"
	credentialsctrl "github.com/matheussouza/inframap/modules/credentials/controller"
	credentialsrepo "github.com/matheussouza/inframap/modules/credentials/repository"
	credentialsuc "github.com/matheussouza/inframap/modules/credentials/usecase"
	"github.com/matheussouza/inframap/modules/discovery"
	discctrl "github.com/matheussouza/inframap/modules/discovery/controller"
	discrepo "github.com/matheussouza/inframap/modules/discovery/repository"
	"github.com/matheussouza/inframap/modules/discovery/scheduler"
	discuc "github.com/matheussouza/inframap/modules/discovery/usecase"
	"github.com/matheussouza/inframap/modules/identity"
	identityctrl "github.com/matheussouza/inframap/modules/identity/controller"
	identityrepo "github.com/matheussouza/inframap/modules/identity/repository"
	identityuc "github.com/matheussouza/inframap/modules/identity/usecase"
	"github.com/matheussouza/inframap/modules/integrations"
	integrationsctrl "github.com/matheussouza/inframap/modules/integrations/controller"
	dockerprovider "github.com/matheussouza/inframap/modules/integrations/providers/docker"
	proxmoxprovider "github.com/matheussouza/inframap/modules/integrations/providers/proxmox"
	integrationsreg "github.com/matheussouza/inframap/modules/integrations/registry"
	"github.com/matheussouza/inframap/modules/inventory"
	invctrl "github.com/matheussouza/inframap/modules/inventory/controller"
	invrepo "github.com/matheussouza/inframap/modules/inventory/repository"
	invuc "github.com/matheussouza/inframap/modules/inventory/usecase"
	"github.com/matheussouza/inframap/modules/realtime"
	realtimectrl "github.com/matheussouza/inframap/modules/realtime/controller"
	realtimegw "github.com/matheussouza/inframap/modules/realtime/gateway"
	"github.com/matheussouza/inframap/modules/topology"
	topoctrl "github.com/matheussouza/inframap/modules/topology/controller"
	toporepo "github.com/matheussouza/inframap/modules/topology/repository"
	topouc "github.com/matheussouza/inframap/modules/topology/usecase"
)

// App holds all application-wide dependencies.
type App struct {
	Logger    *slog.Logger
	DB        *pgxpool.Pool
	EventBus  eventbus.EventBus
	Router    http.Handler
	scheduler *scheduler.Scheduler
}

// Config holds bootstrap configuration parameters.
type Config struct {
	DatabaseURL        string
	MasterKey          string
	CORSAllowedOrigins string
	StaticFS           fs.FS
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
		host := os.Getenv("PGHOST")
		user := os.Getenv("PGUSER")
		pass := os.Getenv("PGPASSWORD")
		dbname := os.Getenv("PGDATABASE")
		port := os.Getenv("PGPORT")
		sslmode := os.Getenv("PGSSLMODE")

		if host != "" || user != "" || pass != "" || dbname != "" {
			if host == "" {
				host = "localhost"
			}
			if user == "" {
				user = "inframap"
			}
			if dbname == "" {
				dbname = "inframap"
			}
			if port == "" {
				port = "5432"
			}
			if sslmode == "" {
				sslmode = "disable"
			}
			dbURL = fmt.Sprintf("postgres://%s:%s@%s:%s/%s?sslmode=%s",
				url.QueryEscape(user),
				url.QueryEscape(pass),
				host,
				port,
				dbname,
				sslmode,
			)
		} else {
			dbURL = "postgres://inframap:inframap_dev_pass@localhost:5432/inframap?sslmode=disable"
		}
	}
	return Config{
		DatabaseURL:        dbURL,
		MasterKey:          os.Getenv("INFRAMAP_MASTER_KEY"),
		CORSAllowedOrigins: os.Getenv("INFRAMAP_CORS_ALLOWED_ORIGINS"),
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
		randomKey := make([]byte, 32)
		if _, err := rand.Read(randomKey); err != nil {
			return nil, fmt.Errorf("failed to generate random dev key: %w", err)
		}
		enc, encErr := crypto.NewAESGCMEncryptor(string(randomKey))
		if encErr != nil {
			return nil, fmt.Errorf("failed to initialize dev encryptor: %w", encErr)
		}
		encryptor = enc
		log.Warn("INFRAMAP_MASTER_KEY not set: using ephemeral random key — encrypted data will NOT survive restarts (set this variable in production)")
	}

	// 2. Database Migrations (auto-apply on startup)
	if cfg.DatabaseURL != "" {
		if err := db.RunMigrations(ctx, cfg.DatabaseURL); err != nil {
			return nil, fmt.Errorf("run database migrations: %w", err)
		}
	}

	// 3. Database Connection Pool
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
	discScheduler := scheduler.New(discUseCase, discRepo, bus, log)

	// 9. Initialize Topology Module
	queries := db.New(pool)
	topoRepo := toporepo.NewPgTopologyRepository(queries)
	topoUseCase := topouc.NewDefaultTopologyUseCase(topoRepo, invRepo, bus, log)
	topoCtrl := topoctrl.NewTopologyController(topoUseCase)

	_ = bus.Subscribe("device.created", topoUseCase.HandleDeviceEvent)
	_ = bus.Subscribe("device.updated", topoUseCase.HandleDeviceEvent)
	_ = bus.Subscribe("device.deleted", topoUseCase.HandleDeviceEvent)

	// 10. Initialize Credentials Module
	credRepo, err := credentialsrepo.NewPgxRepository(queries, encryptor)
	if err != nil {
		return nil, fmt.Errorf("failed to initialize credentials repository: %w", err)
	}
	credUseCase, err := credentialsuc.NewCredentialsUseCase(credRepo, bus)
	if err != nil {
		return nil, fmt.Errorf("failed to initialize credentials usecase: %w", err)
	}
	credCtrl := credentialsctrl.NewCredentialsController(credUseCase)

	// 11. Setup Router & Register Endpoints
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

	// Network interface auto-detection endpoint
	mux.HandleFunc("GET /api/v1/network/interfaces", func(w http.ResponseWriter, r *http.Request) {
		ifaces, ifErr := netinfo.DetectInterfaces()
		if ifErr != nil {
			httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to detect network interfaces", nil)
			return
		}
		httputil.WriteJSON(w, r, http.StatusOK, map[string]interface{}{"data": ifaces})
	})

	// Wire Integrations Registry & Native Providers
	integRegistry := integrationsreg.NewRegistry()
	_ = integRegistry.Register(proxmoxprovider.NewProvider())
	_ = integRegistry.Register(dockerprovider.NewProvider())
	integCtrl := integrationsctrl.NewIntegrationsController(integRegistry)

	// Wire Realtime Gateway & SSE Controller
	rtGateway := realtimegw.NewGateway(bus)
	if err := rtGateway.Start(ctx); err != nil {
		return nil, fmt.Errorf("failed to start realtime gateway: %w", err)
	}
	rtCtrl := realtimectrl.NewSSEController(rtGateway)

	configuration.RegisterRoutes(mux, setupCtrl)
	identity.RegisterRoutes(mux, identityCtrl)
	inventory.RegisterRoutes(mux, invCtrl)
	discovery.RegisterRoutes(mux, discCtrl)
	topology.RegisterRoutes(mux, topoCtrl)
	integrations.RegisterRoutes(mux, integCtrl)
	realtime.RegisterRoutes(mux, rtCtrl)
	credentials.RegisterRoutes(mux, credCtrl)

	if cfg.StaticFS != nil {
		spaHandler := spa.NewSPAHandler(cfg.StaticFS)
		mux.Handle("/", spaHandler)
	}

	validator := &sessionValidatorAdapter{repo: sessionRepo}

	// Middleware Stack: CORS -> RequestID -> SecurityHeaders -> LimitBody -> Recovery -> AuthMiddleware -> Mux
	handler := httputil.CORS(cfg.CORSAllowedOrigins)(
		httputil.RequestID(
			httputil.SecurityHeaders(
				httputil.LimitBody(
					httputil.Recovery(log)(
						httputil.AuthMiddleware(validator)(mux),
					),
				),
			),
		),
	)

	return &App{
		Logger:    log,
		DB:        pool,
		EventBus:  bus,
		Router:    handler,
		scheduler: discScheduler,
	}, nil
}

// Start performs post-wiring startup of background services.
func (a *App) Start(ctx context.Context) error {
	if a.scheduler != nil {
		if err := a.scheduler.Start(ctx); err != nil {
			return fmt.Errorf("failed to start discovery scheduler: %w", err)
		}
	}
	return nil
}

// Close gracefully releases application resources.
// Order matters: scheduler → event bus → database.
func (a *App) Close() {
	if a.scheduler != nil {
		a.scheduler.Stop()
	}
	if a.EventBus != nil {
		_ = a.EventBus.Close()
	}
	if a.DB != nil {
		a.DB.Close()
	}
}
