package usecase

import (
	"context"
	"encoding/json"
	"testing"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/discovery/engine"
)

// deviceWithRef builds an inventory row carrying a canonical provider identity, which is
// what the lifecycle engine matches observations against.
func deviceWithRef(t *testing.T, ref string) db.Device {
	t.Helper()

	metadata, err := json.Marshal(map[string]interface{}{engine.DeviceMetadataProviderRefKey: ref})
	if err != nil {
		t.Fatalf("failed to build device metadata: %v", err)
	}
	return db.Device{ID: uuid.New(), Status: "active", Metadata: metadata}
}

func TestApplyLifecycleHysteresis(t *testing.T) {
	const scope = "lab-cluster"

	t.Run("first absence takes a workload offline, second archives it", func(t *testing.T) {
		present := deviceWithRef(t, "docker:lab-cluster:container:present")
		gone := deviceWithRef(t, "docker:lab-cluster:container:gone")

		inv := &stubInvRepo{
			devicesByProviderScope: map[string][]db.Device{scope: {present, gone}},
		}
		uc := &DefaultDiscoveryUseCase{invRepo: inv}

		scan := &engine.ScanResult{
			Collectors: []engine.CollectorRunDetail{{
				CollectorType: "docker",
				Status:        "success",
				Scopes: []engine.ProviderScopeObservation{{
					Scope:        scope,
					ObservedRefs: []string{"docker:lab-cluster:container:present"},
				}},
			}},
		}

		uc.applyLifecycleHysteresis(context.Background(), scan)

		if len(inv.absenceCalls) != 1 || inv.absenceCalls[0] != gone.ID {
			t.Fatalf("expected only the absent workload to be retired, got %v", inv.absenceCalls)
		}
		if got := inv.absenceCounts[gone.ID]; got != 1 {
			t.Errorf("absence_count = %d, want 1 after the first miss", got)
		}

		// A second complete run without it must archive it.
		uc.applyLifecycleHysteresis(context.Background(), scan)

		if got := inv.absenceCounts[gone.ID]; got != 2 {
			t.Errorf("absence_count = %d, want 2 after the second miss", got)
		}
		if _, retired := inv.absenceCounts[present.ID]; retired {
			t.Error("a workload the provider still reports must never be retired")
		}
	})

	t.Run("freezes state when the collector run failed", func(t *testing.T) {
		gone := deviceWithRef(t, "docker:lab-cluster:container:gone")
		inv := &stubInvRepo{devicesByProviderScope: map[string][]db.Device{scope: {gone}}}
		uc := &DefaultDiscoveryUseCase{invRepo: inv}

		// A cluster outage or an expired token makes every workload look gone, so a failed
		// run must not retire anything.
		uc.applyLifecycleHysteresis(context.Background(), &engine.ScanResult{
			Collectors: []engine.CollectorRunDetail{{
				CollectorType: "docker",
				Status:        "error",
				Scopes:        []engine.ProviderScopeObservation{{Scope: scope}},
			}},
		})

		if len(inv.absenceCalls) != 0 {
			t.Errorf("expected a failed run to retire nothing, got %v", inv.absenceCalls)
		}
	})

	t.Run("freezes state when a successful run reported nothing", func(t *testing.T) {
		gone := deviceWithRef(t, "docker:lab-cluster:container:gone")
		inv := &stubInvRepo{devicesByProviderScope: map[string][]db.Device{scope: {gone}}}
		uc := &DefaultDiscoveryUseCase{invRepo: inv}

		// An empty scope is indistinguishable from a provider that failed to enumerate.
		uc.applyLifecycleHysteresis(context.Background(), &engine.ScanResult{
			Collectors: []engine.CollectorRunDetail{{
				CollectorType: "docker",
				Status:        "success",
				Scopes:        []engine.ProviderScopeObservation{{Scope: scope, ObservedRefs: nil}},
			}},
		})

		if len(inv.absenceCalls) != 0 {
			t.Errorf("expected an empty run to retire nothing, got %v", inv.absenceCalls)
		}
	})

	t.Run("never retires a device from another scope", func(t *testing.T) {
		proxmoxVM := deviceWithRef(t, "proxmox:pve-cluster:qemu:101")
		inv := &stubInvRepo{
			devicesByProviderScope: map[string][]db.Device{"pve-cluster": {proxmoxVM}},
		}
		uc := &DefaultDiscoveryUseCase{invRepo: inv}

		// A Docker run is authoritative only over its own scope.
		uc.applyLifecycleHysteresis(context.Background(), &engine.ScanResult{
			Collectors: []engine.CollectorRunDetail{{
				CollectorType: "docker",
				Status:        "success",
				Scopes: []engine.ProviderScopeObservation{{
					Scope:        scope,
					ObservedRefs: []string{"docker:lab-cluster:container:present"},
				}},
			}},
		})

		if len(inv.absenceCalls) != 0 {
			t.Errorf("expected Proxmox workloads to survive a Docker run, got %v", inv.absenceCalls)
		}
	})

	t.Run("ignores devices without a provider identity", func(t *testing.T) {
		sweepDevice := db.Device{ID: uuid.New(), Status: "active"}
		inv := &stubInvRepo{devicesByProviderScope: map[string][]db.Device{scope: {sweepDevice}}}
		uc := &DefaultDiscoveryUseCase{invRepo: inv}

		uc.applyLifecycleHysteresis(context.Background(), &engine.ScanResult{
			Collectors: []engine.CollectorRunDetail{{
				CollectorType: "docker",
				Status:        "success",
				Scopes: []engine.ProviderScopeObservation{{
					Scope:        scope,
					ObservedRefs: []string{"docker:lab-cluster:container:present"},
				}},
			}},
		})

		if len(inv.absenceCalls) != 0 {
			t.Errorf("expected sweep-discovered devices to be left alone, got %v", inv.absenceCalls)
		}
	})
}
