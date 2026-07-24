package e2e

import (
	"bytes"
	"encoding/json"
	"net/http"
	"testing"

	"github.com/matheussouza/inframap/internal/platform/httputil"
	"github.com/matheussouza/inframap/modules/configuration/dto"
	identitydto "github.com/matheussouza/inframap/modules/identity/dto"
)

func TestE2E_IdentityAuthFlow(t *testing.T) {
	app, ts := setupTestApp(t)
	defer app.Close()
	defer ts.Close()

	client := ts.Client()

	// 1. Perform Onboarding or ensure user exists
	t.Run("Step 1: Ensure System Onboarded", func(t *testing.T) {
		payload := dto.OnboardRequest{
			AdminUsername:    "admin",
			AdminEmail:       "admin@example.com",
			AdminPassword:    "correct-horse-battery-staple-passphrase",
			AdminFullName:    "System Administrator",
			TelemetryEnabled: false,
		}
		body, _ := json.Marshal(payload)

		resp, err := client.Post(ts.URL+"/api/v1/setup/onboard", "application/json", bytes.NewReader(body))
		if err != nil {
			t.Fatalf("onboard request failed: %v", err)
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != http.StatusCreated && resp.StatusCode != http.StatusConflict {
			t.Errorf("expected 201 Created or 409 Conflict, got %d", resp.StatusCode)
		}
	})

	// 2. Failed Login Attempt
	t.Run("Step 2: Login Failure on Bad Password", func(t *testing.T) {
		loginPayload := identitydto.LoginRequest{
			Username: "admin",
			Password: "wrong-password",
		}
		body, _ := json.Marshal(loginPayload)

		resp, err := client.Post(ts.URL+"/api/v1/auth/login", "application/json", bytes.NewReader(body))
		if err != nil {
			t.Fatalf("login request failed: %v", err)
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != http.StatusUnauthorized {
			t.Errorf("expected 401 Unauthorized, got %d", resp.StatusCode)
		}
	})

	// 3. Successful Login Attempt
	var token string
	var sessionCookie *http.Cookie

	t.Run("Step 3: Successful Login", func(t *testing.T) {
		loginPayload := identitydto.LoginRequest{
			Username: "admin",
			Password: "correct-horse-battery-staple-passphrase",
		}
		body, _ := json.Marshal(loginPayload)

		resp, err := client.Post(ts.URL+"/api/v1/auth/login", "application/json", bytes.NewReader(body))
		if err != nil {
			t.Fatalf("login request failed: %v", err)
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", resp.StatusCode)
		}

		// Verify cookie header
		cookies := resp.Cookies()
		for _, c := range cookies {
			if c.Name == "inframap_session" {
				sessionCookie = c
				break
			}
		}

		if sessionCookie == nil {
			t.Error("expected inframap_session cookie in login response")
		}

		var env httputil.SuccessEnvelope
		if err := json.NewDecoder(resp.Body).Decode(&env); err != nil {
			t.Fatalf("failed to decode login response: %v", err)
		}

		dataMap, ok := env.Data.(map[string]any)
		if !ok {
			t.Fatalf("expected data map in login response")
		}

		tokenStr, ok := dataMap["token"].(string)
		if !ok || tokenStr == "" {
			t.Error("expected token string in login response data")
		}
		token = tokenStr
	})

	// 4. Authenticated Request GET /auth/me with Bearer Header
	t.Run("Step 4: GET /auth/me with Bearer Header", func(t *testing.T) {
		req, _ := http.NewRequest(http.MethodGet, ts.URL+"/api/v1/auth/me", nil)
		req.Header.Set("Authorization", "Bearer "+token)

		resp, err := client.Do(req)
		if err != nil {
			t.Fatalf("get me request failed: %v", err)
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != http.StatusOK {
			t.Errorf("expected 200 OK for authenticated user, got %d", resp.StatusCode)
		}

		var env httputil.SuccessEnvelope
		if err := json.NewDecoder(resp.Body).Decode(&env); err != nil {
			t.Fatalf("failed to decode me response: %v", err)
		}
	})

	// 5. Logout POST /auth/logout
	t.Run("Step 5: Logout", func(t *testing.T) {
		req, _ := http.NewRequest(http.MethodPost, ts.URL+"/api/v1/auth/logout", nil)
		req.Header.Set("Authorization", "Bearer "+token)

		resp, err := client.Do(req)
		if err != nil {
			t.Fatalf("logout request failed: %v", err)
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != http.StatusOK {
			t.Errorf("expected 200 OK on logout, got %d", resp.StatusCode)
		}
	})

	// 6. Unauthenticated Request after Logout returns 401
	t.Run("Step 6: GET /auth/me after Logout returns 401", func(t *testing.T) {
		req, _ := http.NewRequest(http.MethodGet, ts.URL+"/api/v1/auth/me", nil)
		req.Header.Set("Authorization", "Bearer "+token)

		resp, err := client.Do(req)
		if err != nil {
			t.Fatalf("get me request failed: %v", err)
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != http.StatusUnauthorized {
			t.Errorf("expected 401 Unauthorized after session revocation, got %d", resp.StatusCode)
		}
	})
}
