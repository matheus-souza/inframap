// Package docker implements the Docker Engine integration provider for InfraMap.
package docker

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"fmt"
	"log/slog"
	"net"
	"net/http"
	"net/url"
	"sort"
	"strings"
	"time"

	"github.com/matheussouza/inframap/internal/platform/sdk"
)

// DefaultSocketPath defines the standard Docker UNIX domain socket path.
const DefaultSocketPath = "/var/run/docker.sock"

// Canonical ProviderRef segments for the entities this provider discovers (ADR-013).
const (
	providerID    = "docker"
	kindEngine    = "engine"
	kindContainer = "container"
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
		Description: "Discovers Docker host and containers via Docker Socket or HTTP/TLS API",
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
				Key:         "socket_path",
				Label:       "Docker Socket Path",
				Type:        "text",
				Required:    false,
				Default:     DefaultSocketPath,
				Description: "Path to Docker daemon UNIX domain socket (e.g. /var/run/docker.sock)",
			},
			{
				Key:         "tcp_url",
				Label:       "Docker TCP URL",
				Type:        "text",
				Required:    false,
				Description: "Remote Docker daemon TCP/HTTP endpoint (e.g. tcp://192.168.1.100:2376 or http://192.168.1.100:2375)",
			},
			{
				Key:         "tls_cert",
				Label:       "TLS Certificate",
				Type:        "password",
				Required:    false,
				Description: "Client TLS certificate in PEM format for mutual TLS authentication",
			},
			{
				Key:         "tls_key",
				Label:       "TLS Private Key",
				Type:        "password",
				Required:    false,
				Description: "Client TLS private key in PEM format for mutual TLS authentication",
			},
			{
				Key:         "tls_ca",
				Label:       "TLS CA Certificate",
				Type:        "password",
				Required:    false,
				Description: "CA certificate in PEM format to verify Docker daemon TLS identity",
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

// HealthCheck tests connectivity against Docker Engine /_ping endpoint with /info fallback.
func (p *Provider) HealthCheck(ctx context.Context, config sdk.ProviderConfig) error {
	client, baseURL, err := p.buildClient(config)
	if err != nil {
		return err
	}

	// 1. Attempt GET /_ping
	pingURL := baseURL.JoinPath("_ping")
	// lgtm[go/ssrf] - Target URL is validated by buildClient with strict scheme and host checks
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, pingURL.String(), nil)
	if err != nil {
		return fmt.Errorf("failed to create ping request: %w", err)
	}

	resp, err := client.Do(req)
	if err == nil {
		defer func() {
			_ = resp.Body.Close()
		}()
		if resp.StatusCode == http.StatusOK {
			return nil
		}
	}

	// 2. Fallback to GET /info if /_ping fails or is unavailable
	infoURL := baseURL.JoinPath("info")
	// lgtm[go/ssrf] - Target URL is validated by buildClient with strict scheme and host checks
	infoReq, infoErr := http.NewRequestWithContext(ctx, http.MethodGet, infoURL.String(), nil)
	if infoErr != nil {
		return fmt.Errorf("failed to create info request: %w", infoErr)
	}

	infoResp, infoErr := client.Do(infoReq)
	if infoErr != nil {
		if err != nil {
			return fmt.Errorf("failed to reach Docker Engine API: %w", err)
		}
		return fmt.Errorf("failed to reach Docker Engine API: %w", infoErr)
	}
	defer func() {
		_ = infoResp.Body.Close()
	}()

	if infoResp.StatusCode != http.StatusOK {
		return fmt.Errorf("docker health check returned HTTP %d", infoResp.StatusCode)
	}
	return nil
}

type dockerInfoResponse struct {
	ID                string `json:"ID"`
	Name              string `json:"Name"`
	OSType            string `json:"OSType"`
	OperatingSystem   string `json:"OperatingSystem"`
	Architecture      string `json:"Architecture"`
	ServerVersion     string `json:"ServerVersion"`
	NCPU              int    `json:"NCPU"`
	MemTotal          int64  `json:"MemTotal"`
	Containers        int    `json:"Containers"`
	ContainersRunning int    `json:"ContainersRunning"`
	ContainersPaused  int    `json:"ContainersPaused"`
	ContainersStopped int    `json:"ContainersStopped"`
	Images            int    `json:"Images"`
}

type dockerPortResponse struct {
	IP          string `json:"IP,omitempty"`
	PrivatePort int    `json:"PrivatePort"`
	PublicPort  int    `json:"PublicPort,omitempty"`
	Type        string `json:"Type"`
}

type dockerNetworkResponse struct {
	NetworkID           string `json:"NetworkID,omitempty"`
	EndpointID          string `json:"EndpointID,omitempty"`
	Gateway             string `json:"Gateway,omitempty"`
	IPAddress           string `json:"IPAddress,omitempty"`
	IPPrefixLen         int    `json:"IPPrefixLen,omitempty"`
	IPv6Gateway         string `json:"IPv6Gateway,omitempty"`
	GlobalIPv6Address   string `json:"GlobalIPv6Address,omitempty"`
	GlobalIPv6PrefixLen int    `json:"GlobalIPv6PrefixLen,omitempty"`
	MacAddress          string `json:"MacAddress,omitempty"`
}

type dockerContainerResponse struct {
	ID              string               `json:"Id"`
	Names           []string             `json:"Names"`
	Image           string               `json:"Image"`
	ImageID         string               `json:"ImageID"`
	Command         string               `json:"Command"`
	Created         int64                `json:"Created"`
	State           string               `json:"State"`
	Status          string               `json:"Status"`
	Ports           []dockerPortResponse `json:"Ports"`
	Labels          map[string]string    `json:"Labels"`
	NetworkSettings struct {
		Networks map[string]dockerNetworkResponse `json:"Networks"`
	} `json:"NetworkSettings"`
}

// Discover fetches Docker host metadata and all containers from Docker Engine API.
func (p *Provider) Discover(ctx context.Context, config sdk.ProviderConfig) ([]sdk.NormalizedDevice, error) {
	client, baseURL, err := p.buildClient(config)
	if err != nil {
		return nil, err
	}

	// The scope must stay a single canonical segment: a raw socket path or TCP URL would
	// otherwise split ProviderRef.Key() apart on its colons and slashes.
	scope := sdk.SanitizeRefSegment(config.GetStringOrDefault("scope", "default"))
	var devices []sdk.NormalizedDevice
	daemonID := "docker-engine"

	// 1. Fetch info for host device
	infoURL := baseURL.JoinPath("info")
	// lgtm[go/ssrf] - Target URL is validated by buildClient with strict scheme and host checks
	infoReq, err := http.NewRequestWithContext(ctx, http.MethodGet, infoURL.String(), nil)
	if err == nil {
		infoResp, err := client.Do(infoReq)
		if err == nil {
			if infoResp.StatusCode == http.StatusOK {
				var info dockerInfoResponse
				if err := json.NewDecoder(infoResp.Body).Decode(&info); err != nil {
					slog.Warn("docker: failed to decode /info response", "error", err)
				} else {
					if info.ID != "" {
						daemonID = info.ID
					} else if info.Name != "" {
						daemonID = info.Name
					}

					hostName := info.Name
					if hostName == "" {
						hostName = "docker-host"
					}

					hostDevice := sdk.NormalizedDevice{
						Hostname:   hostName,
						DeviceType: "server",
						Vendor:     "Docker",
						Model:      "Docker Engine Host",
						OSName:     info.OSType,
						OSVersion:  info.OperatingSystem,
						// The engine is the root of the containment hierarchy: no parent.
						ProviderRef: &sdk.ProviderRef{Provider: providerID, Scope: scope, Kind: kindEngine, NativeID: daemonID},
						Metadata: map[string]interface{}{
							"docker": map[string]interface{}{
								"is_host":            true,
								"daemon_id":          daemonID,
								"server_version":     info.ServerVersion,
								"os_type":            info.OSType,
								"operating_system":   info.OperatingSystem,
								"architecture":       info.Architecture,
								"ncpu":               info.NCPU,
								"mem_total":          info.MemTotal,
								"containers_running": info.ContainersRunning,
								"containers_total":   info.Containers,
								"images_count":       info.Images,
							},
						},
					}
					devices = append(devices, hostDevice)
				}
			} else {
				slog.Warn("docker: /info returned non-200 status", "status", infoResp.StatusCode)
			}
			_ = infoResp.Body.Close()
		} else {
			slog.Warn("docker: failed to request /info", "error", err)
		}
	}

	// 2. Fetch all containers across all states (all=true)
	containersURL := baseURL.JoinPath("containers/json")
	q := containersURL.Query()
	q.Set("all", "true")
	containersURL.RawQuery = q.Encode()

	// lgtm[go/ssrf] - Target URL is validated by buildClient with strict scheme and host checks
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

	var containers []dockerContainerResponse
	if err := json.NewDecoder(resp.Body).Decode(&containers); err != nil {
		return nil, fmt.Errorf("failed to decode docker containers payload: %w", err)
	}

	for _, c := range containers {
		name := c.ID
		if len(c.ID) >= 12 {
			name = c.ID[:12]
		}
		if len(c.Names) > 0 {
			name = strings.TrimPrefix(c.Names[0], "/")
		}

		// Extract networks, IP, MAC
		primaryIP := ""
		primaryMAC := ""
		networkNames := make([]string, 0, len(c.NetworkSettings.Networks))
		networkDetails := make(map[string]interface{}, len(c.NetworkSettings.Networks))

		for netName := range c.NetworkSettings.Networks {
			networkNames = append(networkNames, netName)
		}
		// Go randomizes map iteration, so the primary address has to be elected from a
		// stable order or a multi-homed container would change identity between runs.
		sort.Strings(networkNames)

		for _, netName := range networkNames {
			netConf := c.NetworkSettings.Networks[netName]
			networkDetails[netName] = map[string]interface{}{
				"network_id":             netConf.NetworkID,
				"endpoint_id":            netConf.EndpointID,
				"gateway":                netConf.Gateway,
				"ip_address":             netConf.IPAddress,
				"ip_prefix_len":          netConf.IPPrefixLen,
				"ipv6_gateway":           netConf.IPv6Gateway,
				"global_ipv6_address":    netConf.GlobalIPv6Address,
				"global_ipv6_prefix_len": netConf.GlobalIPv6PrefixLen,
				"mac_address":            netConf.MacAddress,
			}

			if primaryIP == "" && netConf.IPAddress != "" {
				primaryIP = netConf.IPAddress
			}
			if primaryMAC == "" && netConf.MacAddress != "" {
				primaryMAC = netConf.MacAddress
			}
		}

		// Extract port mappings
		portMappings := make([]map[string]interface{}, 0, len(c.Ports))
		for _, p := range c.Ports {
			mapping := map[string]interface{}{
				"container_port": p.PrivatePort,
				"protocol":       p.Type,
			}
			if p.PublicPort > 0 {
				mapping["host_port"] = p.PublicPort
			}
			if p.IP != "" {
				mapping["host_ip"] = p.IP
			}
			portMappings = append(portMappings, mapping)
		}

		// Parse image repo, tag, digest
		imageRepo, imageTag, imageDigest := parseImageRef(c.Image, c.ImageID)
		powerState := mapPowerState(c.State)

		containerDevice := sdk.NormalizedDevice{
			Hostname:          name,
			IPAddress:         primaryIP,
			MACAddress:        primaryMAC,
			DeviceType:        "container",
			Vendor:            "Docker",
			Model:             c.Image,
			ProviderRef:       &sdk.ProviderRef{Provider: providerID, Scope: scope, Kind: kindContainer, NativeID: c.ID},
			ParentProviderRef: &sdk.ProviderRef{Provider: providerID, Scope: scope, Kind: kindEngine, NativeID: daemonID},
			Metadata: map[string]interface{}{
				"docker": map[string]interface{}{
					"container_id":    c.ID,
					"container_name":  name,
					"names":           c.Names,
					"image":           c.Image,
					"image_id":        c.ImageID,
					"image_repo":      imageRepo,
					"image_tag":       imageTag,
					"image_digest":    imageDigest,
					"command":         c.Command,
					"created_at":      c.Created,
					"state":           c.State,
					"status":          c.Status,
					"power_state":     powerState,
					"port_mappings":   portMappings,
					"networks":        networkNames,
					"network_details": networkDetails,
					"labels":          c.Labels,
				},
			},
		}
		devices = append(devices, containerDevice)
	}

	return devices, nil
}

func (p *Provider) buildClient(config sdk.ProviderConfig) (*http.Client, *url.URL, error) {
	tcpURL := config.GetStringOrDefault("tcp_url", "")
	if tcpURL == "" {
		tcpURL = config.GetStringOrDefault("api_url", "")
	}
	socketPath := config.GetStringOrDefault("socket_path", "")

	// 1. If explicit TCP / HTTP / HTTPS endpoint configured
	if tcpURL != "" && (strings.HasPrefix(tcpURL, "http://") || strings.HasPrefix(tcpURL, "https://") || strings.HasPrefix(tcpURL, "tcp://")) {
		return p.buildTCPClient(tcpURL, config)
	}

	// 2. Unix socket transport
	if socketPath == "" {
		if strings.HasPrefix(tcpURL, "unix://") || strings.HasPrefix(tcpURL, "/") {
			socketPath = tcpURL
		} else {
			socketPath = DefaultSocketPath
		}
	}
	socketPath = strings.TrimPrefix(socketPath, "unix://")

	// Every request is redirected to the daemon socket, so the network and address the
	// http client derives from the placeholder base URL are deliberately ignored.
	tr := &http.Transport{
		DialContext: func(ctx context.Context, _, _ string) (net.Conn, error) {
			return (&net.Dialer{}).DialContext(ctx, "unix", socketPath)
		},
	}
	client := &http.Client{Transport: tr, Timeout: 30 * time.Second}
	baseURL, _ := url.Parse("http://localhost")
	return client, baseURL, nil
}

func (p *Provider) buildTCPClient(rawURL string, config sdk.ProviderConfig) (*http.Client, *url.URL, error) {
	tlsCert := config.GetStringOrDefault("tls_cert", "")
	tlsKey := config.GetStringOrDefault("tls_key", "")
	tlsCA := config.GetStringOrDefault("tls_ca", "")

	targetURL := rawURL
	if strings.HasPrefix(targetURL, "tcp://") {
		if tlsCert != "" || tlsKey != "" || tlsCA != "" {
			targetURL = "https://" + strings.TrimPrefix(targetURL, "tcp://")
		} else {
			targetURL = "http://" + strings.TrimPrefix(targetURL, "tcp://")
		}
	}

	parsedURL, err := url.Parse(targetURL)
	if err != nil || (parsedURL.Scheme != "http" && parsedURL.Scheme != "https") || parsedURL.Host == "" {
		return nil, nil, fmt.Errorf("invalid api_url format: must start with http://, https://, or tcp://")
	}
	if strings.ContainsAny(parsedURL.Host, "\r\n\t ") {
		return nil, nil, fmt.Errorf("invalid host format")
	}

	cleanURL := &url.URL{
		Scheme: parsedURL.Scheme,
		Host:   parsedURL.Host,
	}

	verifySSL := false
	if v, ok := config["verify_ssl"].(bool); ok {
		verifySSL = v
	}

	tlsConfig := &tls.Config{
		MinVersion: tls.VersionTLS12,
	}

	if tlsCA != "" {
		caPool := x509.NewCertPool()
		if caPool.AppendCertsFromPEM([]byte(tlsCA)) {
			tlsConfig.RootCAs = caPool
		}
	}

	if tlsCert != "" && tlsKey != "" {
		cert, err := tls.X509KeyPair([]byte(tlsCert), []byte(tlsKey))
		if err != nil {
			return nil, nil, fmt.Errorf("failed to load TLS client key pair: %w", err)
		}
		tlsConfig.Certificates = []tls.Certificate{cert}
	}

	if !verifySSL && tlsCA == "" {
		tlsConfig.InsecureSkipVerify = true // #nosec G402
	}

	tr := &http.Transport{
		TLSClientConfig: tlsConfig,
	}
	client := &http.Client{Transport: tr, Timeout: 30 * time.Second}

	return client, cleanURL, nil
}

func parseImageRef(image, imageID string) (repo, tag, digest string) {
	s := strings.TrimSpace(image)
	if s == "" {
		return "", "", imageID
	}

	if atIdx := strings.Index(s, "@"); atIdx != -1 {
		digest = s[atIdx+1:]
		s = s[:atIdx]
	} else if strings.HasPrefix(imageID, "sha256:") {
		digest = imageID
	}

	lastSlash := strings.LastIndex(s, "/")
	var hostAndPath, remainder string
	if lastSlash != -1 {
		hostAndPath = s[:lastSlash+1]
		remainder = s[lastSlash+1:]
	} else {
		remainder = s
	}

	if colonIdx := strings.LastIndex(remainder, ":"); colonIdx != -1 {
		repo = hostAndPath + remainder[:colonIdx]
		tag = remainder[colonIdx+1:]
	} else {
		repo = s
		tag = "latest"
	}

	return repo, tag, digest
}

func mapPowerState(state string) string {
	switch strings.ToLower(strings.TrimSpace(state)) {
	case "running":
		return "running"
	case "paused":
		return "paused"
	case "exited", "dead":
		return "exited"
	case "created":
		return "stopped"
	case "restarting":
		return "running"
	default:
		return strings.ToLower(strings.TrimSpace(state))
	}
}
