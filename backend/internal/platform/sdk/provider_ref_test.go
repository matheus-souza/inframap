package sdk_test

import (
	"errors"
	"testing"

	"github.com/matheussouza/inframap/internal/platform/sdk"
)

func TestProviderRef_IsZero(t *testing.T) {
	tests := []struct {
		name     string
		ref      sdk.ProviderRef
		expected bool
	}{
		{
			name:     "empty struct is zero",
			ref:      sdk.ProviderRef{},
			expected: true,
		},
		{
			name:     "provider without native id is zero",
			ref:      sdk.ProviderRef{Provider: "docker"},
			expected: true,
		},
		{
			name:     "native id without provider is zero",
			ref:      sdk.ProviderRef{NativeID: "12345"},
			expected: true,
		},
		{
			name:     "scope and kind alone are zero",
			ref:      sdk.ProviderRef{Scope: "local", Kind: "container"},
			expected: true,
		},
		{
			name:     "provider and native id are enough",
			ref:      sdk.ProviderRef{Provider: "docker", NativeID: "abc"},
			expected: false,
		},
		{
			name: "all fields populated is not zero",
			ref: sdk.ProviderRef{
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
			if got := tt.ref.IsZero(); got != tt.expected {
				t.Errorf("ProviderRef.IsZero() = %v, want %v", got, tt.expected)
			}
		})
	}
}

func TestProviderRef_Key(t *testing.T) {
	tests := []struct {
		name     string
		ref      sdk.ProviderRef
		expected string
	}{
		{
			name:     "proxmox vm key",
			ref:      sdk.ProviderRef{Provider: "proxmox", Scope: "pve-cluster", Kind: "qemu", NativeID: "101"},
			expected: "proxmox:pve-cluster:qemu:101",
		},
		{
			name:     "scope with host and port is sanitized to keep four segments",
			ref:      sdk.ProviderRef{Provider: "proxmox", Scope: "pve.local:8006", Kind: "lxc", NativeID: "102"},
			expected: "proxmox:pve.local-8006:lxc:102",
		},
		{
			name:     "scope with socket url is sanitized",
			ref:      sdk.ProviderRef{Provider: "docker", Scope: "unix:///var/run/docker.sock", Kind: "container", NativeID: "abc123"},
			expected: "docker:unix-var-run-docker.sock:container:abc123",
		},
		{
			name:     "native id keeps colons as the trailing segment",
			ref:      sdk.ProviderRef{Provider: "unifi", Scope: "site-default", Kind: "client", NativeID: "aa:bb:cc:dd:ee:ff"},
			expected: "unifi:site-default:client:aa:bb:cc:dd:ee:ff",
		},
		{
			name:     "zero ref yields empty key",
			ref:      sdk.ProviderRef{Provider: "docker"},
			expected: "",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := tt.ref.Key(); got != tt.expected {
				t.Errorf("ProviderRef.Key() = %q, want %q", got, tt.expected)
			}
		})
	}
}

func TestParseProviderRef(t *testing.T) {
	t.Run("round trips a canonical key", func(t *testing.T) {
		original := sdk.ProviderRef{Provider: "proxmox", Scope: "pve-cluster", Kind: "qemu", NativeID: "101"}

		parsed, err := sdk.ParseProviderRef(original.Key())
		if err != nil {
			t.Fatalf("ParseProviderRef() unexpected error: %v", err)
		}
		if parsed != original {
			t.Errorf("ParseProviderRef() = %+v, want %+v", parsed, original)
		}
	})

	t.Run("keeps colons inside the native id", func(t *testing.T) {
		parsed, err := sdk.ParseProviderRef("docker:engine-01:image:sha256:deadbeef")
		if err != nil {
			t.Fatalf("ParseProviderRef() unexpected error: %v", err)
		}
		if parsed.NativeID != "sha256:deadbeef" {
			t.Errorf("NativeID = %q, want %q", parsed.NativeID, "sha256:deadbeef")
		}
	})

	t.Run("rejects keys with fewer than four segments", func(t *testing.T) {
		if _, err := sdk.ParseProviderRef("proxmox:pve:qemu"); !errors.Is(err, sdk.ErrMalformedProviderRef) {
			t.Errorf("expected ErrMalformedProviderRef, got %v", err)
		}
	})

	t.Run("rejects keys missing provider or native id", func(t *testing.T) {
		for _, key := range []string{":pve:qemu:101", "proxmox:pve:qemu:"} {
			if _, err := sdk.ParseProviderRef(key); !errors.Is(err, sdk.ErrMalformedProviderRef) {
				t.Errorf("ParseProviderRef(%q): expected ErrMalformedProviderRef, got %v", key, err)
			}
		}
	})
}

func TestSanitizeRefSegment(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{input: "pve-cluster", expected: "pve-cluster"},
		{input: "pve.local:8006", expected: "pve.local-8006"},
		{input: "  spaced  scope  ", expected: "spaced-scope"},
		{input: "unix:///var/run/docker.sock", expected: "unix-var-run-docker.sock"},
		{input: "", expected: ""},
	}

	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			if got := sdk.SanitizeRefSegment(tt.input); got != tt.expected {
				t.Errorf("SanitizeRefSegment(%q) = %q, want %q", tt.input, got, tt.expected)
			}
		})
	}
}
