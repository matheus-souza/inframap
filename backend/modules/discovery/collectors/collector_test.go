package collectors_test

import (
	"testing"
	"time"

	"github.com/matheussouza/inframap/internal/platform/sdk"
	"github.com/matheussouza/inframap/modules/discovery/collectors"
)

// TestProviderRef_AliasesSDKType guards the alias that lets sdk.NormalizedDevice carry a
// typed identity without the provider SDK and the discovery pipeline importing each other.
// The identity semantics themselves are covered by the sdk package tests.
func TestProviderRef_AliasesSDKType(t *testing.T) {
	ref := collectors.ProviderRef{Provider: "proxmox", Scope: "pve-cluster", Kind: "qemu", NativeID: "101"}

	var asSDK sdk.ProviderRef = ref
	if asSDK.Key() != "proxmox:pve-cluster:qemu:101" {
		t.Errorf("Key() = %q, want %q", asSDK.Key(), "proxmox:pve-cluster:qemu:101")
	}
	if ref.IsZero() {
		t.Error("expected a fully populated reference not to be zero")
	}
}

func TestRawObservation_WithProviderRef(t *testing.T) {
	parentRef := &collectors.ProviderRef{
		Provider: "proxmox",
		Scope:    "cluster",
		Kind:     "node",
		NativeID: "pve1",
	}

	childRef := &collectors.ProviderRef{
		Provider: "proxmox",
		Scope:    "cluster",
		Kind:     "qemu",
		NativeID: "100",
	}

	obs := collectors.RawObservation{
		Hostname:          "vm-100",
		ObservedAt:        time.Now(),
		ProviderRef:       childRef,
		ParentProviderRef: parentRef,
	}

	if obs.ProviderRef == nil || obs.ProviderRef.Key() != "proxmox:cluster:qemu:100" {
		t.Fatalf("unexpected ProviderRef: %+v", obs.ProviderRef)
	}
	if obs.ParentProviderRef == nil || obs.ParentProviderRef.Key() != "proxmox:cluster:node:pve1" {
		t.Fatalf("unexpected ParentProviderRef: %+v", obs.ParentProviderRef)
	}
}
