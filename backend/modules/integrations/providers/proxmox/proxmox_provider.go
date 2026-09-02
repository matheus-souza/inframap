// Package proxmox implements the Proxmox VE integration provider for InfraMap.
package proxmox

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/matheussouza/inframap/internal/platform/sdk"
)

// Canonical ProviderRef segments for the entities this provider discovers (ADR-013).
const (
	providerID = "proxmox"
	kindNode   = "node"
	kindQemu   = "qemu"
	kindLXC    = "lxc"
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

// Name returns the human-readable provider name.
func (p *Provider) Name() string {
	return "Proxmox VE"
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
				Key:         "tls_verify",
				Label:       "Verify TLS Certificate",
				Type:        "boolean",
				Required:    false,
				Default:     true,
				Description: "Whether to verify target TLS certificate",
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

	reqURL := baseURL.JoinPath("api2/json/version")
	// lgtm[go/ssrf] - Target URL is validated by buildClient with strict scheme and host checks
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, reqURL.String(), nil)
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

	// The scope must stay a single canonical segment, so derive it from the bare hostname:
	// baseURL.Host carries the port and its colon would split ProviderRef.Key() apart.
	scope := sdk.SanitizeRefSegment(baseURL.Hostname())

	// 1. Fetch nodes
	nodesURL := baseURL.JoinPath("api2/json/nodes")
	// lgtm[go/ssrf] - Target URL is validated by buildClient with strict scheme and host checks
	nodesReq, err := http.NewRequestWithContext(ctx, http.MethodGet, nodesURL.String(), nil)
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
			Node    string `json:"node"`
			Status  string `json:"status"`
			MaxCPU  int64  `json:"maxcpu"`
			MaxMem  int64  `json:"maxmem"`
			MaxDisk int64  `json:"maxdisk"`
		} `json:"data"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&nodesResp); err != nil {
		return nil, fmt.Errorf("failed to decode proxmox nodes payload: %w", err)
	}

	var devices []sdk.NormalizedDevice

	for _, n := range nodesResp.Data {
		if err := ctx.Err(); err != nil {
			return nil, err
		}

		nodePowerState := "running"
		if strings.EqualFold(n.Status, "offline") {
			nodePowerState = "stopped"
		}

		hostDevice := sdk.NormalizedDevice{
			Hostname:   n.Node,
			DeviceType: "server",
			Vendor:     "Proxmox",
			Model:      "PVE Node",
			// Cluster nodes sit at the top of the containment hierarchy: no parent.
			ProviderRef: &sdk.ProviderRef{Provider: providerID, Scope: scope, Kind: kindNode, NativeID: n.Node},
			Metadata: map[string]interface{}{
				"proxmox": map[string]interface{}{
					"is_host":      true,
					"node":         n.Node,
					"status":       n.Status,
					"power_state":  nodePowerState,
					"cores":        n.MaxCPU,
					"memory_bytes": n.MaxMem,
					"disk_bytes":   n.MaxDisk,
				},
			},
		}
		devices = append(devices, hostDevice)

		// 2. Fetch QEMU VMs for node
		vmDevices, err := p.fetchQemuVMs(ctx, client, baseURL, tokenHeader, n.Node, scope)
		if err != nil {
			return nil, err
		}
		devices = append(devices, vmDevices...)

		// 3. Fetch LXC Containers for node
		lxcDevices, err := p.fetchLXCContainers(ctx, client, baseURL, tokenHeader, n.Node, scope)
		if err != nil {
			return nil, err
		}
		devices = append(devices, lxcDevices...)
	}

	return devices, nil
}

func (p *Provider) fetchQemuVMs(ctx context.Context, client *http.Client, baseURL *url.URL, tokenHeader, node, scope string) ([]sdk.NormalizedDevice, error) {
	qemuURL := baseURL.JoinPath("api2/json/nodes", node, "qemu")
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, qemuURL.String(), nil)
	if err != nil {
		slog.Warn("proxmox: failed to create QEMU request", "node", node, "error", err)
		return nil, nil
	}
	req.Header.Set("Authorization", tokenHeader)

	resp, err := client.Do(req)
	if err != nil {
		slog.Warn("proxmox: failed to list QEMU VMs", "node", node, "error", err)
		return nil, nil
	}
	defer func() {
		_ = resp.Body.Close()
	}()

	if resp.StatusCode != http.StatusOK {
		slog.Warn("proxmox: QEMU VMs returned non-200 status", "node", node, "status", resp.StatusCode)
		return nil, nil
	}

	var vmsResp struct {
		Data []struct {
			VMID    int64  `json:"vmid"`
			Name    string `json:"name"`
			Status  string `json:"status"`
			CPUs    int64  `json:"cpus"`
			MaxMem  int64  `json:"maxmem"`
			MaxDisk int64  `json:"maxdisk"`
		} `json:"data"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&vmsResp); err != nil {
		slog.Warn("proxmox: failed to decode QEMU response", "node", node, "error", err)
		return nil, nil
	}

	devices := make([]sdk.NormalizedDevice, 0, len(vmsResp.Data))
	for _, vm := range vmsResp.Data {
		if err := ctx.Err(); err != nil {
			return nil, err
		}

		ipAddr, macAddr := p.fetchGuestAgentNetwork(ctx, client, baseURL, tokenHeader, node, vm.VMID)

		powerState := "stopped"
		switch strings.ToLower(vm.Status) {
		case "running":
			powerState = "running"
		case "paused":
			powerState = "paused"
		case "stopped":
			powerState = "stopped"
		default:
			if vm.Status != "" {
				powerState = strings.ToLower(vm.Status)
			}
		}

		vmName := vm.Name
		if vmName == "" {
			vmName = fmt.Sprintf("vm-%d", vm.VMID)
		}
		devices = append(devices, sdk.NormalizedDevice{
			Hostname:          vmName,
			IPAddress:         ipAddr,
			MACAddress:        macAddr,
			DeviceType:        "vm",
			Vendor:            "QEMU",
			Model:             "Virtual Machine",
			ProviderRef:       &sdk.ProviderRef{Provider: providerID, Scope: scope, Kind: kindQemu, NativeID: strconv.FormatInt(vm.VMID, 10)},
			ParentProviderRef: &sdk.ProviderRef{Provider: providerID, Scope: scope, Kind: kindNode, NativeID: node},
			Metadata: map[string]interface{}{
				"proxmox": map[string]interface{}{
					"vmid":         vm.VMID,
					"node":         node,
					"type":         "qemu",
					"cores":        vm.CPUs,
					"memory_bytes": vm.MaxMem,
					"disk_bytes":   vm.MaxDisk,
					"power_state":  powerState,
				},
			},
		})
	}

	return devices, nil
}

func (p *Provider) fetchLXCContainers(ctx context.Context, client *http.Client, baseURL *url.URL, tokenHeader, node, scope string) ([]sdk.NormalizedDevice, error) {
	lxcURL := baseURL.JoinPath("api2/json/nodes", node, "lxc")
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, lxcURL.String(), nil)
	if err != nil {
		slog.Warn("proxmox: failed to create LXC request", "node", node, "error", err)
		return nil, nil
	}
	req.Header.Set("Authorization", tokenHeader)

	resp, err := client.Do(req)
	if err != nil {
		slog.Warn("proxmox: failed to list LXC containers", "node", node, "error", err)
		return nil, nil
	}
	defer func() {
		_ = resp.Body.Close()
	}()

	if resp.StatusCode != http.StatusOK {
		slog.Warn("proxmox: LXC containers returned non-200 status", "node", node, "status", resp.StatusCode)
		return nil, nil
	}

	var lxcResp struct {
		Data []struct {
			VMID    int64  `json:"vmid"`
			Name    string `json:"name"`
			Status  string `json:"status"`
			CPUs    int64  `json:"cpus"`
			MaxMem  int64  `json:"maxmem"`
			MaxDisk int64  `json:"maxdisk"`
		} `json:"data"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&lxcResp); err != nil {
		slog.Warn("proxmox: failed to decode LXC response", "node", node, "error", err)
		return nil, nil
	}

	devices := make([]sdk.NormalizedDevice, 0, len(lxcResp.Data))
	for _, lxc := range lxcResp.Data {
		if err := ctx.Err(); err != nil {
			return nil, err
		}

		powerState := "stopped"
		if strings.EqualFold(lxc.Status, "running") {
			powerState = "running"
		} else if lxc.Status != "" {
			powerState = strings.ToLower(lxc.Status)
		}

		lxcName := lxc.Name
		if lxcName == "" {
			lxcName = fmt.Sprintf("ct-%d", lxc.VMID)
		}
		devices = append(devices, sdk.NormalizedDevice{
			Hostname:          lxcName,
			DeviceType:        "container",
			Vendor:            "LXC",
			Model:             "LXC Container",
			ProviderRef:       &sdk.ProviderRef{Provider: providerID, Scope: scope, Kind: kindLXC, NativeID: strconv.FormatInt(lxc.VMID, 10)},
			ParentProviderRef: &sdk.ProviderRef{Provider: providerID, Scope: scope, Kind: kindNode, NativeID: node},
			Metadata: map[string]interface{}{
				"proxmox": map[string]interface{}{
					"vmid":         lxc.VMID,
					"node":         node,
					"type":         "lxc",
					"cores":        lxc.CPUs,
					"memory_bytes": lxc.MaxMem,
					"disk_bytes":   lxc.MaxDisk,
					"power_state":  powerState,
				},
			},
		})
	}

	return devices, nil
}

type agentNetworkResponse struct {
	Data struct {
		Result []agentInterface `json:"result"`
	} `json:"data"`
}

type agentInterface struct {
	Name            string           `json:"name"`
	HardwareAddress string           `json:"hardware-address"`
	IPAddresses     []agentIPAddress `json:"ip-addresses"`
}

type agentIPAddress struct {
	IPAddress     string `json:"ip-address"`
	IPAddressType string `json:"ip-address-type"`
}

func (p *Provider) fetchGuestAgentNetwork(ctx context.Context, client *http.Client, baseURL *url.URL, tokenHeader, node string, vmid int64) (string, string) {
	agentURL := baseURL.JoinPath("api2/json/nodes", node, "qemu", fmt.Sprintf("%d", vmid), "agent/network-get-interfaces")
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, agentURL.String(), nil)
	if err != nil {
		slog.Warn("proxmox: failed to create guest agent request", "node", node, "vmid", vmid, "error", err)
		return "", ""
	}
	req.Header.Set("Authorization", tokenHeader)

	resp, err := client.Do(req)
	if err != nil {
		slog.Warn("proxmox: guest agent request failed", "node", node, "vmid", vmid, "error", err)
		return "", ""
	}
	defer func() {
		_ = resp.Body.Close()
	}()

	if resp.StatusCode != http.StatusOK {
		slog.Warn("proxmox: guest agent returned non-200 status", "node", node, "vmid", vmid, "status", resp.StatusCode)
		return "", ""
	}

	bodyBytes, err := io.ReadAll(io.LimitReader(resp.Body, 1024*1024))
	if err != nil {
		slog.Warn("proxmox: failed to read guest agent response", "node", node, "vmid", vmid, "error", err)
		return "", ""
	}

	return parseAgentInterfaces(bodyBytes)
}

func parseAgentInterfaces(body []byte) (string, string) {
	var resp agentNetworkResponse
	if err := json.Unmarshal(body, &resp); err == nil && len(resp.Data.Result) > 0 {
		return extractIPAndMAC(resp.Data.Result)
	}

	var directResp struct {
		Data []agentInterface `json:"data"`
	}
	if err := json.Unmarshal(body, &directResp); err == nil && len(directResp.Data) > 0 {
		return extractIPAndMAC(directResp.Data)
	}

	return "", ""
}

func extractIPAndMAC(ifaces []agentInterface) (string, string) {
	for _, iface := range ifaces {
		name := strings.ToLower(strings.TrimSpace(iface.Name))
		if name == "lo" || strings.HasPrefix(name, "lo:") || strings.HasPrefix(name, "docker") || strings.HasPrefix(name, "veth") {
			continue
		}
		for _, addr := range iface.IPAddresses {
			if ip := parseRoutableIPv4(addr.IPAddress); ip != "" {
				return ip, strings.TrimSpace(iface.HardwareAddress)
			}
		}
	}

	// Fallback to any routable IPv4 if the named interfaces didn't match. The same address
	// filter applies here: an APIPA address such as 169.254.0.0/16 identifies nothing.
	for _, iface := range ifaces {
		for _, addr := range iface.IPAddresses {
			if ip := parseRoutableIPv4(addr.IPAddress); ip != "" {
				return ip, strings.TrimSpace(iface.HardwareAddress)
			}
		}
	}

	return "", ""
}

func (p *Provider) buildClient(config sdk.ProviderConfig) (*http.Client, *url.URL, string, error) {
	apiURL, err := config.GetString("api_url")
	if err != nil {
		return nil, nil, "", err
	}
	tokenID, err := config.GetString("token_id")
	if err != nil {
		return nil, nil, "", err
	}
	tokenSecret, err := config.GetString("token_secret")
	if err != nil {
		return nil, nil, "", err
	}

	parsedURL, err := url.Parse(apiURL)
	if err != nil || (parsedURL.Scheme != "http" && parsedURL.Scheme != "https") || parsedURL.Host == "" {
		return nil, nil, "", fmt.Errorf("invalid api_url format: must start with http:// or https://")
	}
	if strings.ContainsAny(parsedURL.Host, "\r\n\t ") {
		return nil, nil, "", fmt.Errorf("invalid host format")
	}

	cleanURL := &url.URL{
		Scheme: parsedURL.Scheme,
		Host:   parsedURL.Host,
	}

	// Certificate validation stays on unless the operator explicitly turns it off. A missing
	// or malformed flag must never be the reason InfraMap talks to an unverified endpoint.
	verifyTLS := true
	if v, ok := config["tls_verify"].(bool); ok {
		verifyTLS = v
	} else if v, ok := config["verify_ssl"].(bool); ok {
		verifyTLS = v
	}

	tr := &http.Transport{
		TLSClientConfig: &tls.Config{
			InsecureSkipVerify: !verifyTLS, // #nosec G402
			MinVersion:         tls.VersionTLS12,
		},
	}
	client := &http.Client{Transport: tr, Timeout: 30 * time.Second, CheckRedirect: refuseRedirects}
	tokenHeader := fmt.Sprintf("PVEAPIToken=%s=%s", tokenID, tokenSecret)

	return client, cleanURL, tokenHeader, nil
}

// parseRoutableIPv4 returns the canonical form of a routable IPv4 address, or an empty
// string when the value is unparseable, loopback, link-local or not IPv4. Guest agents
// report every address a guest holds, and only routable ones identify a workload.
func parseRoutableIPv4(raw string) string {
	ip := net.ParseIP(strings.TrimSpace(raw))
	if ip == nil || ip.IsLoopback() || ip.IsLinkLocalUnicast() || ip.To4() == nil {
		return ""
	}
	return ip.String()
}

// refuseRedirects stops the http.Client from following redirects.
//
// Go's default policy follows up to ten of them and only strips credentials when the
// destination is neither the same domain nor a subdomain of it. That is too permissive for
// a provider transport: the Proxmox API token would travel to any subdomain of the
// configured host, and the Docker client certificate lives on the transport, so it is
// presented to every redirect destination regardless of domain. A management API has no
// legitimate reason to redirect, so the response is surfaced as-is and the caller treats
// the non-200 status as a failure (CONTEXT.md guideline #189).
func refuseRedirects(_ *http.Request, _ []*http.Request) error {
	return http.ErrUseLastResponse
}
