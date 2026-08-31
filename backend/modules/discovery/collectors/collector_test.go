package collectors_test

import (
	"testing"
	"time"

	"github.com/matheussouza/inframap/modules/discovery/collectors"
)

func TestProviderRef_IsZero(t *testing.T) {
	tests := []struct {
		name     string
		ref      collectors.ProviderRef
		expected bool
	}{
		{
			name:     "empty struct is zero",
			ref:      collectors.ProviderRef{},
			expected: true,
		},
		{
			name:     "only provider populated is not zero",
			ref:      collectors.ProviderRef{Provider: "docker"},
			expected: false,
		},
		{
			name:     "only scope populated is not zero",
			ref:      collectors.ProviderRef{Scope: "local"},
			expected: false,
		},
		{
			name:     "only kind populated is not zero",
			ref:      collectors.ProviderRef{Kind: "container"},
			expected: false,
		},
		{
			name:     "only native_id populated is not zero",
			ref:      collectors.ProviderRef{NativeID: "12345"},
			expected: false,
		},
		{
			name: "all fields populated is not zero",
			ref: collectors.ProviderRef{
				Provider: "proxmox",
				Scope:    "node-01",
				Kind:     "qemu",
				NativeID: "100",
			},
			expected: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := tt.ref.IsZero()
			if got != tt.expected {
				t.Errorf("ProviderRef.IsZero() = %v, want %v", got, tt.expected)
			}
		})
	}
}

func TestProviderRef_Key(t *testing.T) {
	tests := []struct {
		name     string
		ref      collectors.ProviderRef
		expected string
	}{
		{
			name: "proxmox vm key",
			ref: collectors.ProviderRef{
				Provider: "proxmox",
				Scope:    "pve-cluster",
				Kind:     "qemu",
				NativeID: "101",
			},
			expected: "proxmox:pve-cluster:qemu:101",
		},
		{
			name: "docker container key",
			ref: collectors.ProviderRef{
				Provider: "docker",
				Scope:    "unix:///var/run/docker.sock",
				Kind:     "container",
				NativeID: "container-abc",
			},
			expected: "docker:unix:///var/run/docker.sock:container:container-abc",
		},
		{
			name: "unifi client key",
			ref: collectors.ProviderRef{
				Provider: "unifi",
				Scope:    "site-default",
				Kind:     "client",
				NativeID: "aa:bb:cc:dd:ee:ff",
			},
			expected: "unifi:site-default:client:aa:bb:cc:dd:ee:ff",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := tt.ref.Key()
			if got != tt.expected {
				t.Errorf("ProviderRef.Key() = %q, want %q", got, tt.expected)
			}
		})
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
