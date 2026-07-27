// Package docker implements the Docker Engine integration provider for InfraMap.
package docker

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strings"

	"github.com/matheussouza/inframap/internal/platform/sdk"
)

// Provider implements sdk.Provider for Docker Engine API.
type Provider struct{}

// NewProvider constructs a new Docker Provider.
func NewProvider() *Provider {
	return &Provider{}
}

// ID returns the unique provider identifier.
func (p *Provider) ID() string {
	return "docker"
}

// Metadata returns human-readable details for UI rendering.
func (p *Provider) Metadata() sdk.ProviderMetadata {
	return sdk.ProviderMetadata{
		ID:          "docker",
		Name:        "Docker Engine",
		Description: "Discovers Docker host and active containers via Docker Socket or HTTP API",
		Version:     "1.0.0",
		Icon:        "container",
		Category:    "container",
	}
}

// ConfigSchema defines input parameters required for Docker Engine API.
func (p *Provider) ConfigSchema() sdk.ConfigSchema {
	return sdk.ConfigSchema{
		Fields: []sdk.ConfigField{
			{
				Key:         "api_url",
				Label:       "Docker API URL",
				Type:        "text",
				Required:    true,
				Default:     "http://localhost:2375",
				Description: "HTTP base URL of Docker Engine daemon API (e.g. http://127.0.0.1:2375)",
			},
			{
				Key:         "verify_ssl",
				Label:       "Verify SSL Certificate",
				Type:        "boolean",
				Required:    false,
				Default:     false,
				Description: "Whether to verify target TLS certificate",
			},
		},
	}
}

// HealthCheck tests connectivity against Docker Engine /_ping endpoint.
func (p *Provider) HealthCheck(ctx context.Context, config sdk.ProviderConfig) error {
	client, baseURL, err := p.buildClient(config)
	if err != nil {
		return err
	}

	reqURL := baseURL.JoinPath("_ping")
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, reqURL.String(), nil)
	if err != nil {
		return fmt.Errorf("failed to create ping request: %w", err)
	}

	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("failed to reach Docker Engine API: %w", err)
	}
	defer func() {
		_ = resp.Body.Close()
	}()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("docker health check returned HTTP %d", resp.StatusCode)
	}
	return nil
}

// Discover fetches running containers from Docker Engine API `/containers/json`.
func (p *Provider) Discover(ctx context.Context, config sdk.ProviderConfig) ([]sdk.NormalizedDevice, error) {
	client, baseURL, err := p.buildClient(config)
	if err != nil {
		return nil, err
	}

	// 1. Fetch info for host device
	var devices []sdk.NormalizedDevice

	infoURL := baseURL.JoinPath("info")
	infoReq, err := http.NewRequestWithContext(ctx, http.MethodGet, infoURL.String(), nil)
	if err == nil {
		if infoResp, err := client.Do(infoReq); err == nil && infoResp.StatusCode == http.StatusOK {
			var info struct {
				Name      string `json:"Name"`
				OSType    string `json:"OSType"`
				OSVersion string `json:"OperatingSystem"`
			}
			if err := json.NewDecoder(infoResp.Body).Decode(&info); err == nil && info.Name != "" {
				devices = append(devices, sdk.NormalizedDevice{
					Hostname:   info.Name,
					DeviceType: "server",
					Vendor:     "Docker",
					Model:      "Docker Engine Host",
					OSName:     info.OSType,
					OSVersion:  info.OSVersion,
					Metadata: map[string]interface{}{
						"docker": map[string]interface{}{
							"is_host": true,
						},
					},
				})
			}
			_ = infoResp.Body.Close()
		}
	}

	// 2. Fetch running containers
	containersURL := baseURL.JoinPath("containers/json")
	containersReq, err := http.NewRequestWithContext(ctx, http.MethodGet, containersURL.String(), nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create containers request: %w", err)
	}

	resp, err := client.Do(containersReq)
	if err != nil {
		return nil, fmt.Errorf("failed to list docker containers: %w", err)
	}
	defer func() {
		_ = resp.Body.Close()
	}()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("failed to list docker containers: HTTP %d", resp.StatusCode)
	}

	var containers []struct {
		ID     string   `json:"Id"`
		Names  []string `json:"Names"`
		Image  string   `json:"Image"`
		State  string   `json:"State"`
		Status string   `json:"Status"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&containers); err != nil {
		return nil, fmt.Errorf("failed to decode docker containers payload: %w", err)
	}

	for _, c := range containers {
		name := c.ID[:12]
		if len(c.Names) > 0 {
			name = strings.TrimPrefix(c.Names[0], "/")
		}

		containerDevice := sdk.NormalizedDevice{
			Hostname:   name,
			DeviceType: "container",
			Vendor:     "Docker",
			Model:      c.Image,
			Metadata: map[string]interface{}{
				"docker": map[string]interface{}{
					"container_id": c.ID,
					"image":        c.Image,
					"state":        c.State,
					"status":       c.Status,
				},
			},
		}
		devices = append(devices, containerDevice)
	}

	return devices, nil
}

func (p *Provider) buildClient(config sdk.ProviderConfig) (*http.Client, *url.URL, error) {
	apiURL, err := config.GetString("api_url")
	if err != nil {
		apiURL = "http://localhost:2375"
	}

	parsedURL, err := url.Parse(apiURL)
	if err != nil || (parsedURL.Scheme != "http" && parsedURL.Scheme != "https") || parsedURL.Host == "" {
		return nil, nil, fmt.Errorf("invalid api_url format: must start with http:// or https://")
	}
	cleanURL := &url.URL{
		Scheme: parsedURL.Scheme,
		Host:   parsedURL.Host,
	}

	verifySSL := false
	if v, ok := config["verify_ssl"].(bool); ok {
		verifySSL = v
	}

	tr := &http.Transport{
		TLSClientConfig: &tls.Config{
			InsecureSkipVerify: !verifySSL, // #nosec G402
			MinVersion:         tls.VersionTLS12,
		},
	}
	client := &http.Client{Transport: tr}

	return client, cleanURL, nil
}
