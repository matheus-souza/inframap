package controller_test

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/matheussouza/inframap/internal/platform/httputil"
	"github.com/matheussouza/inframap/modules/configuration/controller"
	"github.com/matheussouza/inframap/modules/configuration/dto"
	"github.com/matheussouza/inframap/modules/configuration/usecase"
)

type mockSetupUseCase struct {
	statusResp *dto.StatusResponse
	statusErr  error
	onboardResp *dto.OnboardResponse
	onboardErr  error
}

func (m *mockSetupUseCase) GetStatus(_ context.Context) (*dto.StatusResponse, error) {
	if m.statusErr != nil {
		return nil, m.statusErr
	}
	return m.statusResp, nil
}

func (m *mockSetupUseCase) Onboard(_ context.Context, _ dto.OnboardRequest) (*dto.OnboardResponse, error) {
	if m.onboardErr != nil {
		return nil, m.onboardErr
	}
	return m.onboardResp, nil
}

func TestSetupController_GetStatus(t *testing.T) {
	uc := &mockSetupUseCase{
		statusResp: &dto.StatusResponse{
			OnboardingCompleted: false,
			SystemInstanceID:    "0198a000-0000-7000-8000-000000000001",
		},
	}

	ctrl := controller.NewSetupController(uc)

	w := httptest.NewRecorder()
	r := httptest.NewRequest(http.MethodGet, "/api/v1/setup/status", nil)

	ctrl.GetStatus(w, r)

	if w.Code != http.StatusOK {
		t.Errorf("expected status 200, got %d", w.Code)
	}

	var env httputil.SuccessEnvelope
	if err := json.NewDecoder(w.Body).Decode(&env); err != nil {
		t.Fatalf("failed to decode response: %v", err)
	}
}

func TestSetupController_Onboard(t *testing.T) {
	uc := &mockSetupUseCase{
		onboardResp: &dto.OnboardResponse{
			OnboardingCompleted: true,
			SystemInstanceID:    "0198a000-0000-7000-8000-000000000001",
			AdminUserID:         "0198a000-0000-7000-8000-000000000002",
		},
	}

	ctrl := controller.NewSetupController(uc)

	reqPayload := dto.OnboardRequest{
		AdminUsername:    "admin",
		AdminEmail:       "admin@example.com",
		AdminPassword:    "correct-horse-battery-staple-passphrase",
		AdminFullName:    "System Admin",
		TelemetryEnabled: false,
	}
	body, _ := json.Marshal(reqPayload)

	w := httptest.NewRecorder()
	r := httptest.NewRequest(http.MethodPost, "/api/v1/setup/onboard", bytes.NewReader(body))

	ctrl.Onboard(w, r)

	if w.Code != http.StatusCreated {
		t.Errorf("expected status 201 Created, got %d", w.Code)
	}
}

func TestSetupController_OnboardAlreadyCompleted(t *testing.T) {
	uc := &mockSetupUseCase{
		onboardErr: usecase.ErrAlreadyOnboarded,
	}

	ctrl := controller.NewSetupController(uc)

	reqPayload := dto.OnboardRequest{
		AdminUsername:    "admin",
		AdminEmail:       "admin@example.com",
		AdminPassword:    "correct-horse-battery-staple-passphrase",
		AdminFullName:    "System Admin",
		TelemetryEnabled: false,
	}
	body, _ := json.Marshal(reqPayload)

	w := httptest.NewRecorder()
	r := httptest.NewRequest(http.MethodPost, "/api/v1/setup/onboard", bytes.NewReader(body))

	ctrl.Onboard(w, r)

	if w.Code != http.StatusConflict {
		t.Errorf("expected status 409 Conflict on retry, got %d", w.Code)
	}
}
