package dto_test

import (
	"testing"

	"github.com/matheussouza/inframap/modules/inventory/dto"
)

func TestCreateDeviceRequest_Normalize(t *testing.T) {
	req := dto.CreateDeviceRequest{
		Hostname:   "  server-01  ",
		DeviceType: "",
	}
	req.Normalize()

	if req.Hostname != "server-01" {
		t.Errorf("expected trimmed hostname 'server-01', got %q", req.Hostname)
	}
	if req.DeviceType != "unknown" {
		t.Errorf("expected default device_type 'unknown', got %q", req.DeviceType)
	}
}

func TestCreateDeviceRequest_Validate(t *testing.T) {
	t.Run("valid request", func(t *testing.T) {
		req := dto.CreateDeviceRequest{Hostname: "switch-01"}
		if errs := req.Validate(); len(errs) != 0 {
			t.Errorf("expected no errors, got %v", errs)
		}
	})

	t.Run("empty hostname", func(t *testing.T) {
		req := dto.CreateDeviceRequest{Hostname: ""}
		errs := req.Validate()
		if len(errs) != 1 || errs[0].Field != "hostname" {
			t.Errorf("expected hostname error, got %v", errs)
		}
	})
}

func TestCreateSubnetRequest_Validate(t *testing.T) {
	t.Run("valid request", func(t *testing.T) {
		req := dto.CreateSubnetRequest{Name: "LAN", CIDR: "192.168.1.0/24"}
		if errs := req.Validate(); len(errs) != 0 {
			t.Errorf("expected no errors, got %v", errs)
		}
	})

	t.Run("empty name", func(t *testing.T) {
		req := dto.CreateSubnetRequest{Name: "", CIDR: "10.0.0.0/8"}
		errs := req.Validate()
		if len(errs) != 1 || errs[0].Field != "name" {
			t.Errorf("expected name error, got %v", errs)
		}
	})

	t.Run("empty CIDR", func(t *testing.T) {
		req := dto.CreateSubnetRequest{Name: "LAN", CIDR: ""}
		errs := req.Validate()
		if len(errs) != 1 || errs[0].Field != "cidr" {
			t.Errorf("expected cidr error, got %v", errs)
		}
	})

	t.Run("invalid CIDR", func(t *testing.T) {
		req := dto.CreateSubnetRequest{Name: "LAN", CIDR: "not-a-cidr"}
		errs := req.Validate()
		if len(errs) != 1 || errs[0].Field != "cidr" {
			t.Errorf("expected cidr error, got %v", errs)
		}
	})

	t.Run("both empty", func(t *testing.T) {
		req := dto.CreateSubnetRequest{Name: "", CIDR: ""}
		errs := req.Validate()
		if len(errs) != 2 {
			t.Errorf("expected 2 errors, got %d", len(errs))
		}
	})
}
