package dto_test

import (
	"errors"
	"testing"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/modules/topology/dto"
)

func TestCreateTopologyLinkRequest_NormalizeAndValidate(t *testing.T) {
	dev1 := uuid.New()
	dev2 := uuid.New()

	t.Run("Valid Request Normalization and Validation", func(t *testing.T) {
		req := &dto.CreateTopologyLinkRequest{
			SourceDeviceID: dev1,
			TargetDeviceID: dev2,
			LinkType:       " MANUAL ",
		}
		req.Normalize()

		if req.LinkType != dto.LinkTypeManual {
			t.Errorf("expected linkType manual, got %s", req.LinkType)
		}
		if *req.ConfidenceScore != 1.00 {
			t.Errorf("expected confidence 1.00, got %f", *req.ConfidenceScore)
		}
		if err := req.Validate(); err != nil {
			t.Errorf("expected nil validation error, got %v", err)
		}
	})

	t.Run("Virtual Hypervisor Default Confidence Score", func(t *testing.T) {
		req := &dto.CreateTopologyLinkRequest{
			SourceDeviceID: dev1,
			TargetDeviceID: dev2,
			LinkType:       dto.LinkTypeVirtualHypervisor,
		}
		req.Normalize()
		if *req.ConfidenceScore != 0.95 {
			t.Errorf("expected confidence 0.95, got %f", *req.ConfidenceScore)
		}
	})

	t.Run("Container Veth Default Confidence Score", func(t *testing.T) {
		req := &dto.CreateTopologyLinkRequest{
			SourceDeviceID: dev1,
			TargetDeviceID: dev2,
			LinkType:       dto.LinkTypeContainerVeth,
		}
		req.Normalize()
		if *req.ConfidenceScore != 0.90 {
			t.Errorf("expected confidence 0.90, got %f", *req.ConfidenceScore)
		}
	})

	t.Run("Invalid Device IDs Failure", func(t *testing.T) {
		req := &dto.CreateTopologyLinkRequest{
			SourceDeviceID: dev1,
			TargetDeviceID: dev1, // Identical IDs
		}
		req.Normalize()
		if !errors.Is(req.Validate(), dto.ErrInvalidDeviceID) {
			t.Errorf("expected ErrInvalidDeviceID, got %v", req.Validate())
		}
	})

	t.Run("Invalid Link Type Failure", func(t *testing.T) {
		req := &dto.CreateTopologyLinkRequest{
			SourceDeviceID: dev1,
			TargetDeviceID: dev2,
			LinkType:       "invalid_link_type",
		}
		if !errors.Is(req.Validate(), dto.ErrInvalidLinkType) {
			t.Errorf("expected ErrInvalidLinkType, got %v", req.Validate())
		}
	})
}
