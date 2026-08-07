package engine_test

import (
	"testing"

	"github.com/matheussouza/inframap/modules/discovery/collectors"
	"github.com/matheussouza/inframap/modules/discovery/engine"
)

func TestValidator_ValidateObservation(t *testing.T) {
	tests := []struct {
		name    string
		obs     collectors.RawObservation
		wantErr bool
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
			name: "invalid IP syntax",
			obs: collectors.RawObservation{
				IPAddress:  "999.999.1.1",
				MACAddress: "aa:bb:cc:11:22:33",
			},
			wantErr: true,
		},
		{
			name: "zero MAC rejected",
			obs: collectors.RawObservation{
				IPAddress:  "192.168.1.10",
				MACAddress: "00:00:00:00:00:00",
			},
			wantErr: true,
		},
		{
			name: "broadcast MAC rejected",
			obs: collectors.RawObservation{
				IPAddress:  "192.168.1.10",
				MACAddress: "ff:ff:ff:ff:ff:ff",
			},
			wantErr: true,
		},
		{
			name: "invalid MAC syntax",
			obs: collectors.RawObservation{
				IPAddress:  "192.168.1.10",
				MACAddress: "invalid-mac",
			},
			wantErr: true,
		},
		{
			name: "hostname containing unprintable control char rejected",
			obs: collectors.RawObservation{
				IPAddress:  "192.168.1.10",
				MACAddress: "aa:bb:cc:11:22:33",
				Hostname:   "bad\x00host",
			},
			wantErr: true,
		},
		{
			name: "empty IP and empty MAC rejected",
			obs: collectors.RawObservation{
				IPAddress:  "",
				MACAddress: "",
			},
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := engine.ValidateObservation(tt.obs)
			if (err != nil) != tt.wantErr {
				t.Errorf("ValidateObservation() error = %v, wantErr %v", err, tt.wantErr)
			}
		})
	}
}
