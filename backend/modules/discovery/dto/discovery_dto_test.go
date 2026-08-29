package dto_test

import (
	"encoding/json"
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

	t.Run("All ValidDiscoveryTypes pass validation", func(t *testing.T) {
		for discType := range dto.ValidDiscoveryTypes {
			req := &dto.CreateDiscoverySourceRequest{
				Name: "Source for " + discType,
				Type: discType,
			}
			req.Normalize()
			if err := req.Validate(); err != nil {
				t.Errorf("expected type %q to be valid, got %v", discType, err)
			}
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

	t.Run("Collectors field trims, lowercases, deduplicates and removes blanks", func(t *testing.T) {
		req := &dto.TriggerScanRequest{
			CIDR: "192.168.1.0/24",
			Collectors: []string{
				"  ICMP_SWEEP  ",
				"arp_sweep",
				"  ",
				"icmp_sweep",
				"  SNMP ",
			},
		}
		req.Normalize()
		if len(req.Collectors) != 3 {
			t.Fatalf("expected 3 normalized collectors, got %d: %v", len(req.Collectors), req.Collectors)
		}
		expected := []string{"icmp_sweep", "arp_sweep", "snmp"}
		for i, exp := range expected {
			if req.Collectors[i] != exp {
				t.Errorf("at index %d, expected %q, got %q", i, exp, req.Collectors[i])
			}
		}

		if err := req.Validate(); err != nil {
			t.Fatalf("expected valid collectors, got %v", err)
		}
	})

	t.Run("Invalid collector in list fails validation", func(t *testing.T) {
		req := &dto.TriggerScanRequest{
			CIDR: "192.168.1.0/24",
			Collectors: []string{
				"icmp_sweep",
				"invalid_collector",
			},
		}
		req.Normalize()
		err := req.Validate()
		if err == nil {
			t.Fatal("expected validation error for invalid collector, got nil")
		}
		if !errors.Is(err, dto.ErrInvalidDiscoveryType) {
			t.Errorf("expected ErrInvalidDiscoveryType, got %v", err)
		}
	})
}

func TestScanResultResponse_Serialization(t *testing.T) {
	resp := dto.ScanResultResponse{
		CIDR:            "192.168.1.0/24",
		TotalCollected:  5,
		TotalValid:      4,
		TotalDiscovered: 2,
		TotalUpdated:    2,
		DurationMs:      123,
		Collectors: []dto.CollectorRunDetail{
			{
				CollectorType: "icmp",
				Status:        "success",
				DevicesFound:  2,
				DurationMs:    45,
			},
			{
				CollectorType: "proxmox",
				Status:        "error",
				DevicesFound:  0,
				DurationMs:    0,
				ErrorMessage:  "collector not implemented",
			},
		},
	}

	data, err := json.Marshal(resp)
	if err != nil {
		t.Fatalf("failed to marshal ScanResultResponse: %v", err)
	}

	var parsed map[string]interface{}
	if err := json.Unmarshal(data, &parsed); err != nil {
		t.Fatalf("failed to unmarshal JSON: %v", err)
	}

	if parsed["cidr"] != "192.168.1.0/24" {
		t.Errorf("expected cidr 192.168.1.0/24, got %v", parsed["cidr"])
	}

	cols, ok := parsed["collectors"].([]interface{})
	if !ok || len(cols) != 2 {
		t.Fatalf("expected 2 collector run details, got %v", parsed["collectors"])
	}
}
