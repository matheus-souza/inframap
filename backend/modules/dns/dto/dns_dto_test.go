// Package dto provides request and response structures for DNS record operations.
package dto

import (
	"net/netip"
	"testing"

	"github.com/google/uuid"
)

func TestCreateDNSRecordRequest_Normalize(t *testing.T) {
	req := CreateDNSRecordRequest{
		Name:      "  Nas.Home.arpa. ",
		RecordType: " A ",
		Address:   "  192.0.2.20  ",
		Target:    "  nas  ",
		Notes:     "  NAS server  ",
	}
	req.Normalize()
	if req.Name != "nas.home.arpa" {
		t.Errorf("expected name 'nas.home.arpa', got %q", req.Name)
	}
	if req.RecordType != "A" {
		t.Errorf("expected record_type 'A', got %q", req.RecordType)
	}
	if req.Address != "192.0.2.20" {
		t.Errorf("expected address '192.0.2.20', got %q", req.Address)
	}
	if req.Target != "nas" {
		t.Errorf("expected target 'nas', got %q", req.Target)
	}
	if req.Notes != "NAS server" {
		t.Errorf("expected notes 'NAS server', got %q", req.Notes)
	}
}

func TestCreateDNSRecordRequest_Validate(t *testing.T) {
	tests := []struct {
		name string
		req  CreateDNSRecordRequest
		want int // expected number of validation errors
	}{
		{
			name: "valid A record",
			req:  CreateDNSRecordRequest{Name: "nas", RecordType: "A", Address: "192.0.2.20"},
			want: 0,
		},
		{
			name: "valid CNAME record",
			req:  CreateDNSRecordRequest{Name: "jellyfin", RecordType: "CNAME", Target: "nas.home.arpa"},
			want: 0,
		},
		{
			name: "empty name",
			req:  CreateDNSRecordRequest{RecordType: "A", Address: "192.0.2.20"},
			want: 1,
		},
		{
			name: "invalid record type",
			req:  CreateDNSRecordRequest{Name: "nas", RecordType: "MX", Address: "192.0.2.20"},
			want: 1,
		},
		{
			name: "A record missing address",
			req:  CreateDNSRecordRequest{Name: "nas", RecordType: "A"},
			want: 1,
		},
		{
			name: "CNAME missing target",
			req:  CreateDNSRecordRequest{Name: "jellyfin", RecordType: "CNAME"},
			want: 1,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			req := tt.req
			req.Normalize()
			errs := req.Validate()
			if len(errs) != tt.want {
				t.Errorf("expected %d errors, got %d: %v", tt.want, len(errs), errs)
			}
		})
	}
}

func TestDNSRecordResponse(t *testing.T) {
	id := uuid.New()
	addr := netip.MustParseAddr("192.0.2.20")
	deviceIDStr := id.String()
	resp := DNSRecordResponse{
		ID:         id.String(),
		Name:       "nas.home.arpa",
		RecordType: "A",
		Address:    addr.String(),
		DeviceID:   &deviceIDStr,
		Notes:      "NAS server",
	}

	if resp.ID != id.String() {
		t.Errorf("expected ID %s, got %s", id.String(), resp.ID)
	}
	if resp.Name != "nas.home.arpa" {
		t.Errorf("expected name 'nas.home.arpa', got %q", resp.Name)
	}
	if resp.RecordType != "A" {
		t.Errorf("expected record_type 'A', got %q", resp.RecordType)
	}
	if resp.Address != "192.0.2.20" {
		t.Errorf("expected address '192.0.2.20', got %q", resp.Address)
	}
}
