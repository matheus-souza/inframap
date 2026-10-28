package dto_test

import (
	"encoding/json"
	"errors"
	"fmt"
	"testing"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/modules/discovery/dto"
)

// targetCode returns the RFC-026 error code carried by err, or "" when err is not a
// target validation error.
func targetCode(t *testing.T, err error) string {
	t.Helper()
	var targetErr *dto.TargetValidationError
	if !errors.As(err, &targetErr) {
		return ""
	}
	return targetErr.Code
}

func TestParseSourceTargets(t *testing.T) {
	subnetID := uuid.New()

	t.Run("legacy cidr becomes one target", func(t *testing.T) {
		targets, present, err := dto.ParseSourceTargets(map[string]interface{}{"cidr": " 10.0.10.0/24 "})
		if err != nil || !present {
			t.Fatalf("expected a present target list, got present=%v err=%v", present, err)
		}
		if len(targets) != 1 || targets[0].CIDR != "10.0.10.0/24" || targets[0].SubnetID != nil {
			t.Fatalf("expected one free-form target 10.0.10.0/24, got %+v", targets)
		}
	})

	t.Run("targets are read as is, in order", func(t *testing.T) {
		config := map[string]interface{}{
			"targets": []interface{}{
				map[string]interface{}{"cidr": "10.0.10.0/24", "subnet_id": subnetID.String()},
				map[string]interface{}{"cidr": "192.0.2.0/24"},
			},
		}
		targets, present, err := dto.ParseSourceTargets(config)
		if err != nil || !present {
			t.Fatalf("expected a present target list, got present=%v err=%v", present, err)
		}
		if len(targets) != 2 {
			t.Fatalf("expected 2 targets, got %d", len(targets))
		}
		if targets[0].CIDR != "10.0.10.0/24" || targets[0].SubnetID == nil || *targets[0].SubnetID != subnetID {
			t.Errorf("unexpected linked target: %+v", targets[0])
		}
		if targets[1].CIDR != "192.0.2.0/24" || targets[1].SubnetID != nil {
			t.Errorf("unexpected free-form target: %+v", targets[1])
		}
	})

	t.Run("targets win over a legacy cidr", func(t *testing.T) {
		targets, _, err := dto.ParseSourceTargets(map[string]interface{}{
			"cidr":    "10.9.9.0/24",
			"targets": []dto.SourceTarget{{CIDR: "192.0.2.0/24"}},
		})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(targets) != 1 || targets[0].CIDR != "192.0.2.0/24" {
			t.Fatalf("expected the targets list to win, got %+v", targets)
		}
	})

	t.Run("neither key means no targets in the request", func(t *testing.T) {
		targets, present, err := dto.ParseSourceTargets(map[string]interface{}{"other": "value"})
		if err != nil || present || targets != nil {
			t.Fatalf("expected absent targets, got %+v present=%v err=%v", targets, present, err)
		}
	})

	t.Run("an empty list or cidr clears the targets", func(t *testing.T) {
		for _, config := range []map[string]interface{}{
			{"targets": []interface{}{}},
			{"targets": nil},
			{"cidr": ""},
		} {
			targets, present, err := dto.ParseSourceTargets(config)
			if err != nil || !present || len(targets) != 0 {
				t.Errorf("config %v: expected an empty present list, got %+v present=%v err=%v", config, targets, present, err)
			}
		}
	})

	t.Run("malformed payloads are invalid targets", func(t *testing.T) {
		for _, config := range []map[string]interface{}{
			{"targets": "10.0.0.0/24"},
			{"targets": []interface{}{map[string]interface{}{"cidr": 10}}},
			{"cidr": 42},
		} {
			_, _, err := dto.ParseSourceTargets(config)
			if code := targetCode(t, err); code != dto.ErrCodeTargetInvalid {
				t.Errorf("config %v: expected %s, got %v", config, dto.ErrCodeTargetInvalid, err)
			}
		}
	})

	t.Run("a malformed subnet id is an unknown subnet", func(t *testing.T) {
		_, _, err := dto.ParseSourceTargets(map[string]interface{}{
			"targets": []interface{}{map[string]interface{}{"cidr": "10.0.0.0/24", "subnet_id": "not-a-uuid"}},
		})
		if code := targetCode(t, err); code != dto.ErrCodeTargetSubnetNotFound {
			t.Fatalf("expected %s, got %v", dto.ErrCodeTargetSubnetNotFound, err)
		}
	})
}

func TestValidateSourceTargets(t *testing.T) {
	free := func(cidrs ...string) []dto.SourceTarget {
		targets := make([]dto.SourceTarget, len(cidrs))
		for i, cidr := range cidrs {
			targets[i] = dto.SourceTarget{CIDR: cidr}
		}
		return targets
	}

	tooMany := make([]string, dto.MaxSourceTargets+1)
	for i := range tooMany {
		tooMany[i] = fmt.Sprintf("10.0.%d.0/24", i)
	}
	maxCount := tooMany[:dto.MaxSourceTargets]

	cases := []struct {
		name         string
		targets      []dto.SourceTarget
		requiresCIDR bool
		wantCode     string
		wantField    string
	}{
		{name: "sweep plan without targets", targets: nil, requiresCIDR: true, wantCode: dto.ErrCodeTargetsRequired, wantField: "config.targets"},
		{name: "provider-only plan without targets", targets: nil, requiresCIDR: false},
		{name: "provider-only plan may still carry targets", targets: free("10.0.0.0/24"), requiresCIDR: false},
		{name: "single valid target", targets: free("10.0.10.0/24"), requiresCIDR: true},
		{name: "disjoint targets", targets: free("10.0.10.0/24", "10.0.20.0/24", "192.0.2.0/24"), requiresCIDR: true},
		{name: "adjacent targets do not overlap", targets: free("10.0.0.0/24", "10.0.1.0/24"), requiresCIDR: true},
		{name: "malformed cidr", targets: free("10.0.0.0/24", "not-a-cidr"), requiresCIDR: true, wantCode: dto.ErrCodeTargetInvalid, wantField: "config.targets[1].cidr"},
		{name: "empty cidr", targets: free(""), requiresCIDR: true, wantCode: dto.ErrCodeTargetInvalid, wantField: "config.targets[0].cidr"},
		{name: "prefix broader than /16", targets: free("10.0.0.0/15"), requiresCIDR: true, wantCode: dto.ErrCodeTargetInvalid, wantField: "config.targets[0].cidr"},
		{name: "duplicate targets", targets: free("10.0.10.0/24", "10.0.10.0/24"), requiresCIDR: true, wantCode: dto.ErrCodeTargetsOverlap, wantField: "config.targets[1].cidr"},
		{name: "duplicate written with host bits", targets: free("10.0.10.0/24", "10.0.10.7/24"), requiresCIDR: true, wantCode: dto.ErrCodeTargetsOverlap},
		{name: "partial overlap", targets: free("10.0.0.0/23", "10.0.1.0/24"), requiresCIDR: true, wantCode: dto.ErrCodeTargetsOverlap, wantField: "config.targets[1].cidr"},
		{name: "contained target listed first", targets: free("10.0.1.0/24", "10.0.0.0/23"), requiresCIDR: true, wantCode: dto.ErrCodeTargetsOverlap},
		{name: "one /16 is exactly at the address limit", targets: free("10.0.0.0/16"), requiresCIDR: true},
		{name: "address sum over the limit", targets: free("10.0.0.0/16", "192.0.2.0/24"), requiresCIDR: true, wantCode: dto.ErrCodeTargetsTooLarge, wantField: "config.targets"},
		{name: "two /17 reach the limit exactly", targets: free("10.0.0.0/17", "10.1.0.0/17"), requiresCIDR: true},
		{name: "maximum number of targets", targets: free(maxCount...), requiresCIDR: true},
		{name: "too many targets", targets: free(tooMany...), requiresCIDR: true, wantCode: dto.ErrCodeTargetsTooMany, wantField: "config.targets"},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			err := dto.ValidateSourceTargets(tc.targets, tc.requiresCIDR)
			if tc.wantCode == "" {
				if err != nil {
					t.Fatalf("expected valid targets, got %v", err)
				}
				return
			}
			var targetErr *dto.TargetValidationError
			if !errors.As(err, &targetErr) {
				t.Fatalf("expected a TargetValidationError with %s, got %v", tc.wantCode, err)
			}
			if targetErr.Code != tc.wantCode {
				t.Errorf("code = %s, want %s", targetErr.Code, tc.wantCode)
			}
			if tc.wantField != "" && targetErr.Field != tc.wantField {
				t.Errorf("field = %s, want %s", targetErr.Field, tc.wantField)
			}
			if targetErr.Error() == "" {
				t.Error("expected a non-empty error message")
			}
		})
	}
}

func TestWithSourceTargets(t *testing.T) {
	subnetID := uuid.New()
	input := map[string]interface{}{"cidr": "10.0.10.0/24", "credential_id": "kept"}
	targets := []dto.SourceTarget{{CIDR: "10.0.10.0/24", SubnetID: &subnetID}, {CIDR: "192.0.2.0/24"}}

	out := dto.WithSourceTargets(input, targets)

	if _, hasCIDR := out["cidr"]; hasCIDR {
		t.Error("expected the legacy cidr key to be dropped on write")
	}
	if out["credential_id"] != "kept" {
		t.Errorf("expected unrelated keys to be kept, got %v", out["credential_id"])
	}
	if _, hasCIDR := input["cidr"]; !hasCIDR || input["targets"] != nil {
		t.Error("expected the input map to be left unmodified")
	}

	data, err := json.Marshal(out)
	if err != nil {
		t.Fatalf("marshal failed: %v", err)
	}
	var stored map[string]interface{}
	if err := json.Unmarshal(data, &stored); err != nil {
		t.Fatalf("unmarshal failed: %v", err)
	}
	list, ok := stored["targets"].([]interface{})
	if !ok || len(list) != 2 {
		t.Fatalf("expected 2 stored targets, got %v", stored["targets"])
	}
	first := list[0].(map[string]interface{})
	if first["cidr"] != "10.0.10.0/24" || first["subnet_id"] != subnetID.String() {
		t.Errorf("unexpected stored linked target: %v", first)
	}
	if _, hasSubnet := list[1].(map[string]interface{})["subnet_id"]; hasSubnet {
		t.Error("expected a free-form target to be stored without subnet_id")
	}

	// What was written must read back as the same list.
	readBack, present, err := dto.ParseSourceTargets(stored)
	if err != nil || !present || len(readBack) != 2 || *readBack[0].SubnetID != subnetID {
		t.Fatalf("expected the stored targets to read back, got %+v present=%v err=%v", readBack, present, err)
	}

	empty := dto.WithSourceTargets(nil, nil)
	if list, ok := empty["targets"].([]dto.SourceTarget); !ok || len(list) != 0 {
		t.Errorf("expected an empty targets list on a nil config, got %v", empty["targets"])
	}
}

func TestSourceTargetResponses(t *testing.T) {
	subnetID := uuid.New()
	stored := []dto.SourceTarget{{CIDR: "10.0.10.0/24", SubnetID: &subnetID}, {CIDR: "192.0.2.0/24"}}

	responses := dto.NewSourceTargetResponses(stored)
	resp := dto.DiscoverySourceResponse{ID: uuid.New(), Targets: responses, ConfigCIDR: responses[0].CIDR}

	data, err := json.Marshal(resp)
	if err != nil {
		t.Fatalf("marshal failed: %v", err)
	}
	var raw map[string]interface{}
	if err := json.Unmarshal(data, &raw); err != nil {
		t.Fatalf("unmarshal failed: %v", err)
	}
	if raw["config_cidr"] != "10.0.10.0/24" {
		t.Errorf("expected config_cidr to hold the first target, got %v", raw["config_cidr"])
	}
	list, ok := raw["targets"].([]interface{})
	if !ok || len(list) != 2 {
		t.Fatalf("expected 2 targets in the response, got %v", raw["targets"])
	}
	second := list[1].(map[string]interface{})
	if value, present := second["subnet_id"]; !present || value != nil {
		t.Errorf("expected subnet_id to be null for a free-form target, got %v (present=%v)", value, present)
	}
	if value, present := second["subnet_name"]; !present || value != nil {
		t.Errorf("expected subnet_name to be null for a free-form target, got %v (present=%v)", value, present)
	}
	if value, present := second["subnet_deleted"]; !present || value != false {
		t.Errorf("expected subnet_deleted to be false for a free-form target, got %v (present=%v)", value, present)
	}

	name := "Lab"
	responses[0].SubnetName = &name
	responses[0].SubnetDeleted = true
	roundTrip := dto.SourceTargetsFromResponses(responses)
	if len(roundTrip) != 2 || roundTrip[0].CIDR != "10.0.10.0/24" || *roundTrip[0].SubnetID != subnetID || roundTrip[1].SubnetID != nil {
		t.Errorf("unexpected round trip: %+v", roundTrip)
	}
}
