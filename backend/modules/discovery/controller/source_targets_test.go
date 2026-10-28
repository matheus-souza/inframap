package controller_test

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/modules/discovery/controller"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/matheussouza/inframap/modules/discovery/usecase"
)

// targetsUseCase answers source writes with a fixed error, or echoes the request's targets.
// Methods the tests do not reach are left to the embedded interface.
type targetsUseCase struct {
	usecase.DiscoveryUseCase
	err      error
	received map[string]interface{}
}

func (u *targetsUseCase) respond(config map[string]interface{}) (*dto.DiscoverySourceResponse, error) {
	u.received = config
	if u.err != nil {
		return nil, u.err
	}
	targets, _, err := dto.ParseSourceTargets(config)
	if err != nil {
		return nil, err
	}
	resp := &dto.DiscoverySourceResponse{
		ID:         uuid.New(),
		Name:       "Lab",
		Targets:    dto.NewSourceTargetResponses(targets),
		Collectors: []dto.CollectorResponse{},
	}
	if len(targets) > 0 {
		resp.ConfigCIDR = targets[0].CIDR
	}
	return resp, nil
}

func (u *targetsUseCase) CreateSource(_ context.Context, req *dto.CreateDiscoverySourceRequest) (*dto.DiscoverySourceResponse, error) {
	return u.respond(req.Config)
}

func (u *targetsUseCase) UpdateSource(_ context.Context, _ string, req *dto.UpdateDiscoverySourceRequest) (*dto.DiscoverySourceResponse, error) {
	return u.respond(req.Config)
}

type errorEnvelope struct {
	Error struct {
		Code    string `json:"code"`
		Message string `json:"message"`
		Details []struct {
			Field string `json:"field"`
			Issue string `json:"issue"`
		} `json:"details"`
	} `json:"error"`
}

func TestDiscoveryController_SourceTargets(t *testing.T) {
	uc := &targetsUseCase{}
	ctrl := controller.NewDiscoveryController(uc)
	mux := http.NewServeMux()
	mux.HandleFunc("POST /api/v1/discovery/sources", ctrl.CreateSource)
	mux.HandleFunc("PUT /api/v1/discovery/sources/{id}", ctrl.UpdateSource)

	send := func(method, path string, body map[string]interface{}) *httptest.ResponseRecorder {
		data, _ := json.Marshal(body)
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, httptest.NewRequest(method, path, bytes.NewReader(data)))
		return rec
	}

	codes := []string{
		dto.ErrCodeTargetsRequired,
		dto.ErrCodeTargetInvalid,
		dto.ErrCodeTargetsOverlap,
		dto.ErrCodeTargetsTooLarge,
		dto.ErrCodeTargetsTooMany,
		dto.ErrCodeTargetSubnetNotFound,
	}
	routes := []struct{ method, path string }{
		{http.MethodPost, "/api/v1/discovery/sources"},
		{http.MethodPut, "/api/v1/discovery/sources/" + uuid.New().String()},
	}

	for _, route := range routes {
		for _, code := range codes {
			t.Run(fmt.Sprintf("%s answers %s with 422", route.method, code), func(t *testing.T) {
				uc.err = fmt.Errorf("wrapped: %w", &dto.TargetValidationError{
					Code:    code,
					Field:   "config.targets[1].cidr",
					Message: "rule broken",
				})
				defer func() { uc.err = nil }()

				rec := send(route.method, route.path, map[string]interface{}{"name": "Lab", "type": "icmp_sweep"})
				if rec.Code != http.StatusUnprocessableEntity {
					t.Fatalf("expected 422, got %d: %s", rec.Code, rec.Body.String())
				}
				var env errorEnvelope
				if err := json.NewDecoder(rec.Body).Decode(&env); err != nil {
					t.Fatalf("failed to decode error: %v", err)
				}
				if env.Error.Code != code {
					t.Errorf("error code = %q, want %q", env.Error.Code, code)
				}
				if len(env.Error.Details) != 1 || env.Error.Details[0].Field != "config.targets[1].cidr" {
					t.Errorf("expected the offending field in details, got %+v", env.Error.Details)
				}
			})
		}
	}

	t.Run("a list-wide rule has no field details", func(t *testing.T) {
		uc.err = &dto.TargetValidationError{Code: dto.ErrCodeTargetsRequired, Message: "needs a target"}
		defer func() { uc.err = nil }()

		rec := send(http.MethodPost, "/api/v1/discovery/sources", map[string]interface{}{"name": "Lab"})
		var env errorEnvelope
		_ = json.NewDecoder(rec.Body).Decode(&env)
		if rec.Code != http.StatusUnprocessableEntity || env.Error.Code != dto.ErrCodeTargetsRequired || len(env.Error.Details) != 0 {
			t.Errorf("unexpected response %d %+v", rec.Code, env)
		}
	})

	t.Run("adding targets without the credential answers 422 secret_required_on_target_change", func(t *testing.T) {
		uc.err = &dto.ErrSecretRequiredOnTargetChange{Fields: []string{dto.ConfigKeyTargets}}
		defer func() { uc.err = nil }()

		rec := send(http.MethodPut, "/api/v1/discovery/sources/"+uuid.New().String(), map[string]interface{}{
			"name":       "SNMP sweep",
			"collectors": []map[string]interface{}{{"type": "snmp", "config": map[string]interface{}{"community": ""}}},
			"config":     map[string]interface{}{"targets": []map[string]interface{}{{"cidr": "192.0.2.0/24"}}},
		})
		if rec.Code != http.StatusUnprocessableEntity {
			t.Fatalf("expected 422, got %d: %s", rec.Code, rec.Body.String())
		}
		var env errorEnvelope
		if err := json.NewDecoder(rec.Body).Decode(&env); err != nil {
			t.Fatalf("failed to decode error: %v", err)
		}
		if env.Error.Code != "secret_required_on_target_change" {
			t.Errorf("error code = %q, want secret_required_on_target_change", env.Error.Code)
		}
		if len(env.Error.Details) != 1 || env.Error.Details[0].Field != dto.ConfigKeyTargets {
			t.Errorf("expected the targets field in details, got %+v", env.Error.Details)
		}
	})

	t.Run("legacy config.cidr request returns targets and config_cidr", func(t *testing.T) {
		rec := send(http.MethodPost, "/api/v1/discovery/sources", map[string]interface{}{
			"name":   "Legacy",
			"type":   "icmp_sweep",
			"config": map[string]interface{}{"cidr": "10.0.10.0/24"},
		})
		if rec.Code != http.StatusCreated {
			t.Fatalf("expected 201, got %d: %s", rec.Code, rec.Body.String())
		}
		var env struct {
			Data struct {
				ConfigCIDR string `json:"config_cidr"`
				Targets    []struct {
					CIDR     string  `json:"cidr"`
					SubnetID *string `json:"subnet_id"`
				} `json:"targets"`
			} `json:"data"`
		}
		if err := json.NewDecoder(rec.Body).Decode(&env); err != nil {
			t.Fatalf("failed to decode response: %v", err)
		}
		if env.Data.ConfigCIDR != "10.0.10.0/24" || len(env.Data.Targets) != 1 || env.Data.Targets[0].CIDR != "10.0.10.0/24" {
			t.Errorf("unexpected response body: %+v", env.Data)
		}
	})

	t.Run("config.targets reaches the use case untouched", func(t *testing.T) {
		subnetID := uuid.New().String()
		rec := send(http.MethodPut, "/api/v1/discovery/sources/"+uuid.New().String(), map[string]interface{}{
			"name": "Two blocks",
			"type": "icmp_sweep",
			"config": map[string]interface{}{"targets": []map[string]interface{}{
				{"cidr": "10.0.10.0/24", "subnet_id": subnetID},
				{"cidr": "192.0.2.0/24"},
			}},
		})
		if rec.Code != http.StatusOK {
			t.Fatalf("expected 200, got %d: %s", rec.Code, rec.Body.String())
		}
		targets, present, err := dto.ParseSourceTargets(uc.received)
		if err != nil || !present || len(targets) != 2 || targets[0].SubnetID == nil || targets[0].SubnetID.String() != subnetID {
			t.Errorf("unexpected targets handed to the use case: %+v present=%v err=%v", targets, present, err)
		}
	})
}
