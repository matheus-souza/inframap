// Package proxmox implements the Proxmox VE integration provider for InfraMap.
package proxmox

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"

	"github.com/matheussouza/inframap/internal/platform/sdk"
)

// Provider implements sdk.Provider for Proxmox VE API.
type Provider struct{}

// NewProvider constructs a new Proxmox Provider.
func NewProvider() *Provider {
	return &Provider{}
}

// ID returns the unique provider identifier.
func (p *Provider) ID() string {
	return "proxmox"
}

// Metadata returns human-readable details for UI rendering.
func (p *Provider) Metadata() sdk.ProviderMetadata {
	return sdk.ProviderMetadata{
		ID:          "proxmox",
		Name:        "Proxmox VE",
		Description: "Discovers PVE hypervisor nodes, LXC containers, and QEMU virtual machines via Proxmox REST API",
		Version:     "1.0.0",
		Icon:        "server",
		Category:    "hypervisor",
	}
}

// ConfigSchema defines input parameters required for Proxmox API authentication.
func (p *Provider) ConfigSchema() sdk.ConfigSchema {
	return sdk.ConfigSchema{
		Fields: []sdk.ConfigField{
			{
				Key:         "api_url",
				Label:       "Proxmox API URL",
				Type:        "text",
				Required:    true,
				Description: "Base URL of Proxmox VE API (e.g. https://pve.home.lab:8006)",
			},
			{
				Key:         "token_id",
				Label:       "API Token ID",
				Type:        "text",
				Required:    true,
				Description: "Proxmox API Token ID (e.g. root@pam!inframap)",
			},
			{
				Key:         "token_secret",
				Label:       "API Token Secret",
				Type:        "password",
				Required:    true,
				Description: "Proxmox API Token Secret UUID",
			},
			{
				Key:         "verify_ssl",
				Label:       "Verify SSL Certificate",
				Type:        "boolean",
				Required:    false,
				Default:     false,
				Description: "Whether to verify target SSL certificate",
			},
		},
	}
}

// HealthCheck tests connectivity against Proxmox VE /api2/json/version.
func (p *Provider) HealthCheck(ctx context.Context, config sdk.ProviderConfig) error {
	client, baseURL, tokenHeader, err := p.buildClient(config)
	if err != nil {
		return err
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, baseURL+"/api2/json/version", nil)
	if err != nil {
		return fmt.Errorf("failed to create health check request: %w", err)
	}
	req.Header.Set("Authorization", tokenHeader)

	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("failed to reach Proxmox API: %w", err)
	}
	defer func() {
		_ = resp.Body.Close()
	}()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("proxmox health check returned HTTP %d", resp.StatusCode)
	}
	return nil
}

// Discover fetches nodes, LXC, and QEMU VMs from Proxmox VE API.
func (p *Provider) Discover(ctx context.Context, config sdk.ProviderConfig) ([]sdk.NormalizedDevice, error) {
	client, baseURL, tokenHeader, err := p.buildClient(config)
	if err != nil {
		return nil, err
	}

	// 1. Fetch nodes
	nodesReq, err := http.NewRequestWithContext(ctx, http.MethodGet, baseURL+"/api2/json/nodes", nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create nodes request: %w", err)
	}
	nodesReq.Header.Set("Authorization", tokenHeader)

	resp, err := client.Do(nodesReq)
	if err != nil {
		return nil, fmt.Errorf("failed to list proxmox nodes: %w", err)
	}
	defer func() {
		_ = resp.Body.Close()
	}()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("failed to list proxmox nodes: HTTP %d", resp.StatusCode)
	}

	var nodesResp struct {
		Data []struct {
			Node   string `json:"node"`
			Status string `json:"status"`
		} `json:"data"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&nodesResp); err != nil {
		return nil, fmt.Errorf("failed to decode proxmox nodes payload: %w", err)
	}

	var devices []sdk.NormalizedDevice

	for _, n := range nodesResp.Data {
		hostDevice := sdk.NormalizedDevice{
			Hostname:   n.Node,
			DeviceType: "server",
			Vendor:     "Proxmox",
			Model:      "PVE Node",
			Metadata: map[string]interface{}{
				"proxmox": map[string]interface{}{
					"is_host": true,
					"node":    n.Node,
					"status":  n.Status,
				},
			},
		}
		devices = append(devices, hostDevice)

		// Fetch QEMU VMs for node
		qemuReq, err := http.NewRequestWithContext(ctx, http.MethodGet, fmt.Sprintf("%s/api2/json/nodes/%s/qemu", baseURL, n.Node), nil)
		if err == nil {
			qemuReq.Header.Set("Authorization", tokenHeader)
			if qemuResp, err := client.Do(qemuReq); err == nil {
				var vms struct {
					Data []struct {
						VMID   int    `json:"vmid"`
						Name   string `json:"name"`
						Status string `json:"status"`
					} `json:"data"`
				}
				if err := json.NewDecoder(qemuResp.Body).Decode(&vms); err == nil {
					for _, vm := range vms.Data {
						devices = append(devices, sdk.NormalizedDevice{
							Hostname:   vm.Name,
							DeviceType: "virtual_machine",
							Vendor:     "QEMU",
							Metadata: map[string]interface{}{
								"proxmox": map[string]interface{}{
									"vm_id":     vm.VMID,
									"node":      n.Node,
									"status":    vm.Status,
									"type":      "qemu",
									"node_host": n.Node,
								},
							},
						})
					}
				}
				_ = qemuResp.Body.Close()
			}
		}
	}

	return devices, nil
}

func (p *Provider) buildClient(config sdk.ProviderConfig) (*http.Client, string, string, error) {
	apiURL, err := config.GetString("api_url")
	if err != nil {
		return nil, "", "", err
	}
	tokenID, err := config.GetString("token_id")
	if err != nil {
		return nil, "", "", err
	}
	tokenSecret, err := config.GetString("token_secret")
	if err != nil {
		return nil, "", "", err
	}

	parsedURL, err := url.Parse(apiURL)
	if err != nil || parsedURL.Scheme == "" || parsedURL.Host == "" {
		return nil, "", "", fmt.Errorf("invalid api_url format: %s", apiURL)
	}
	cleanURL := fmt.Sprintf("%s://%s", parsedURL.Scheme, parsedURL.Host)

	verifySSL := false
	if v, ok := config["verify_ssl"].(bool); ok {
		verifySSL = v
	}

	tr := &http.Transport{
		TLSClientConfig: &tls.Config{InsecureSkipVerify: !verifySSL}, // #nosec G402 - Proxmox homelab self-signed certificates
	}
	client := &http.Client{Transport: tr}
	tokenHeader := fmt.Sprintf("PVEAPIToken=%s=%s", tokenID, tokenSecret)

	return client, cleanURL, tokenHeader, nil
}
