package engine_test

import (
	"errors"
	"testing"

	"github.com/matheussouza/inframap/modules/discovery/collectors"
	"github.com/matheussouza/inframap/modules/discovery/engine"
)

func TestValidator_ValidateObservation(t *testing.T) {
	tests := []struct {
		name      string
		obs       collectors.RawObservation
		wantErr   bool
		targetErr error
	}{
		{
			name: "valid IPv4 and unicast MAC",
			obs: collectors.RawObservation{
				IPAddress:  "192.168.1.10",
				MACAddress: "aa:bb:cc:11:22:33",
				Hostname:   "my-switch.local",
			},
			wantErr: false,
		},
		{
			name: "valid IPv6",
			obs: collectors.RawObservation{
				IPAddress:  "fe80::1",
				MACAddress: "02:42:ac:11:00:02",
			},
			wantErr: false,
		},
		{
			name: "valid ProviderRef without IP or MAC",
			obs: collectors.RawObservation{
				Hostname: "docker-container-web",
				ProviderRef: &collectors.ProviderRef{
					Provider: "docker",
					Scope:    "local",
					Kind:     "container",
					NativeID: "container-999",
				},
			},
			wantErr: false,
		},
		{
			name: "zero ProviderRef without IP or MAC rejected",
			obs: collectors.RawObservation{
				Hostname:    "unidentified-host",
				ProviderRef: &collectors.ProviderRef{},
			},
			wantErr:   true,
			targetErr: engine.ErrMissingIdentity,
		},
		{
			name: "invalid IP syntax",
			obs: collectors.RawObservation{
				IPAddress:  "999.999.1.1",
				MACAddress: "aa:bb:cc:11:22:33",
			},
			wantErr:   true,
			targetErr: engine.ErrInvalidIP,
		},
		{
			name: "zero MAC rejected",
			obs: collectors.RawObservation{
				IPAddress:  "192.168.1.10",
				MACAddress: "00:00:00:00:00:00",
			},
			wantErr:   true,
			targetErr: engine.ErrZeroMAC,
		},
		{
			name: "broadcast MAC rejected",
			obs: collectors.RawObservation{
				IPAddress:  "192.168.1.10",
				MACAddress: "ff:ff:ff:ff:ff:ff",
			},
			wantErr:   true,
			targetErr: engine.ErrBroadcastMAC,
		},
		{
			name: "invalid MAC syntax",
			obs: collectors.RawObservation{
				IPAddress:  "192.168.1.10",
				MACAddress: "invalid-mac",
			},
			wantErr:   true,
			targetErr: engine.ErrInvalidMAC,
		},
		{
			name: "hostname containing unprintable control char rejected",
			obs: collectors.RawObservation{
				IPAddress:  "192.168.1.10",
				MACAddress: "aa:bb:cc:11:22:33",
				Hostname:   "bad\x00host",
			},
			wantErr:   true,
			targetErr: engine.ErrInvalidHostname,
		},
		{
			name: "empty IP, MAC and nil ProviderRef rejected with ErrMissingIdentity",
			obs: collectors.RawObservation{
				IPAddress:  "",
				MACAddress: "",
			},
			wantErr:   true,
			targetErr: engine.ErrMissingIdentity,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := engine.ValidateObservation(tt.obs)
			if (err != nil) != tt.wantErr {
				t.Errorf("ValidateObservation() error = %v, wantErr %v", err, tt.wantErr)
			}
			if tt.targetErr != nil && !errors.Is(err, tt.targetErr) {
				t.Errorf("ValidateObservation() error = %v, want targetErr %v", err, tt.targetErr)
			}
		})
	}

	t.Run("ErrMissingAddress is backwards compatible alias for ErrMissingIdentity", func(t *testing.T) {
		err := engine.ValidateObservation(collectors.RawObservation{})
		if !errors.Is(err, engine.ErrMissingAddress) {
			t.Errorf("expected errors.Is(err, ErrMissingAddress) to be true, got %v", err)
		}
		if !errors.Is(err, engine.ErrMissingIdentity) {
			t.Errorf("expected errors.Is(err, ErrMissingIdentity) to be true, got %v", err)
		}
	})
}
