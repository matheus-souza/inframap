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

func TestTriggerScanRequest_NormalizeAndValidate(t *testing.T) {
	t.Run("Valid request with whitespace and optional fields", func(t *testing.T) {
		credID := "  cred-123  "
		req := &dto.TriggerScanRequest{
			CIDR:            "  192.168.1.0/24  ",
			SubnetID:        "  subnet-456  ",
			CredentialSetID: &credID,
		}
		req.Normalize()
		if req.CIDR != "192.168.1.0/24" {
			t.Errorf("expected trimmed CIDR, got %q", req.CIDR)
		}
		if req.SubnetID != "subnet-456" {
			t.Errorf("expected trimmed SubnetID, got %q", req.SubnetID)
		}
		if req.CredentialSetID == nil || *req.CredentialSetID != "cred-123" {
			t.Errorf("expected trimmed CredentialSetID 'cred-123', got %v", req.CredentialSetID)
		}
		if err := req.Validate(); err != nil {
			t.Fatalf("expected valid request, got %v", err)
		}
	})

	t.Run("Empty CredentialSetID normalizes to nil", func(t *testing.T) {
		emptyCred := "   "
		req := &dto.TriggerScanRequest{
			CIDR:            "10.0.0.0/24",
			CredentialSetID: &emptyCred,
		}
		req.Normalize()
		if req.CredentialSetID != nil {
			t.Errorf("expected nil CredentialSetID, got %v", req.CredentialSetID)
		}
	})

	t.Run("Empty CIDR fails validation", func(t *testing.T) {
		req := &dto.TriggerScanRequest{CIDR: "   "}
		req.Normalize()
		if err := req.Validate(); err == nil {
			t.Error("expected error for empty CIDR, got nil")
		}
	})
}

