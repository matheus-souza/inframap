package dto_test

import (
	"errors"
	"testing"

	"github.com/matheussouza/inframap/modules/discovery/dto"
)

func TestCreateDiscoverySourceRequest_NormalizeAndValidate(t *testing.T) {
	t.Run("Valid request passes normalization and validation", func(t *testing.T) {
		req := &dto.CreateDiscoverySourceRequest{
			Name: "  Proxmox VE Cluster  ",
			Type: "  PROXMOX  ",
		}
		req.Normalize()
		if err := req.Validate(); err != nil {
			t.Fatalf("expected valid request, got %v", err)
		}
		if req.Name != "Proxmox VE Cluster" {
			t.Errorf("expected trimmed name, got %q", req.Name)
		}
		if req.Type != "proxmox" {
			t.Errorf("expected lowercase type, got %q", req.Type)
		}
		if req.Enabled == nil || !*req.Enabled {
			t.Error("expected default enabled = true")
		}
	})

	t.Run("Empty name returns ErrEmptySourceName", func(t *testing.T) {
		req := &dto.CreateDiscoverySourceRequest{
			Name: "   ",
			Type: "icmp_sweep",
		}
		req.Normalize()
		err := req.Validate()
		if !errors.Is(err, dto.ErrEmptySourceName) {
			t.Errorf("expected ErrEmptySourceName, got %v", err)
		}
	})

	t.Run("Invalid type returns ErrInvalidDiscoveryType", func(t *testing.T) {
		req := &dto.CreateDiscoverySourceRequest{
			Name: "Unknown Collector",
			Type: "unsupported_collector_type",
		}
		req.Normalize()
		err := req.Validate()
		if !errors.Is(err, dto.ErrInvalidDiscoveryType) {
			t.Errorf("expected ErrInvalidDiscoveryType, got %v", err)
		}
	})
}
