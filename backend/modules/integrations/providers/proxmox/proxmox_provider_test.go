package proxmox_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"github.com/matheussouza/inframap/internal/platform/sdk"
	"github.com/matheussouza/inframap/modules/integrations/providers/proxmox"
)

func TestProxmoxProvider_MetadataAndSchema(t *testing.T) {
	provider := proxmox.NewProvider()

	if provider.ID() != "proxmox" {
		t.Errorf("expected ID proxmox, got %s", provider.ID())
	}
	if provider.Name() != "Proxmox VE" {
		t.Errorf("expected Name Proxmox VE, got %s", provider.Name())
	}
	meta := provider.Metadata()
	if meta.Name != "Proxmox VE" {
		t.Errorf("expected Metadata Name Proxmox VE, got %s", meta.Name)
	}
	if meta.Category != "hypervisor" {
		t.Errorf("expected category hypervisor, got %s", meta.Category)
	}
	if meta.Icon != "server" {
		t.Errorf("expected icon server, got %s", meta.Icon)
	}

	schema := provider.ConfigSchema()
	if len(schema.Fields) != 4 {
		t.Fatalf("expected 4 config schema fields, got %d", len(schema.Fields))
	}

	keys := make(map[string]bool)
	for _, f := range schema.Fields {
		keys[f.Key] = true
	}
	for _, expectedKey := range []string{"api_url", "token_id", "token_secret", "tls_verify"} {
		if !keys[expectedKey] {
			t.Errorf("expected config schema to contain key %q", expectedKey)
		}
	}
}

func TestProxmoxProvider_HealthCheck(t *testing.T) {
	provider := proxmox.NewProvider()

	t.Run("Success with Valid Token Header", func(t *testing.T) {
		var receivedAuth string
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Path == "/api2/json/version" {
				receivedAuth = r.Header.Get("Authorization")
				w.WriteHeader(http.StatusOK)
				_, _ = w.Write([]byte(`{"data":{"version":"8.1.3","release":"8.1"}}`))
				return
			}
			w.WriteHeader(http.StatusNotFound)
		}))
		defer ts.Close()

		validCfg := sdk.ProviderConfig{
			"api_url":      ts.URL,
			"token_id":     "root@pam!token",
			"token_secret": "uuid-secret-1234",
			"tls_verify":   false,
		}

		err := provider.HealthCheck(context.Background(), validCfg)
		if err != nil {
			t.Fatalf("expected nil error on HealthCheck, got %v", err)
		}
		expectedAuth := "PVEAPIToken=root@pam!token=uuid-secret-1234" //gitleaks:allow — fixture asserting the header shape, not a credential
		if receivedAuth != expectedAuth {
			t.Errorf("expected Authorization header %q, got %q", expectedAuth, receivedAuth)
		}
	})

	t.Run("Non-200 Status", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusUnauthorized)
		}))
		defer ts.Close()

		cfg := sdk.ProviderConfig{
			"api_url":      ts.URL,
			"token_id":     "root@pam!token",
			"token_secret": "bad-secret",
		}
		if err := provider.HealthCheck(context.Background(), cfg); err == nil {
			t.Error("expected error when HealthCheck returns HTTP 401")
		}
	})

	t.Run("Missing Configuration Fields", func(t *testing.T) {
		cases := []sdk.ProviderConfig{
			{},
			{"api_url": "http://localhost:8006"},
			{"api_url": "http://localhost:8006", "token_id": "root@pam!tok"},
		}
		for i, cfg := range cases {
			if err := provider.HealthCheck(context.Background(), cfg); err == nil {
				t.Errorf("case %d: expected error for missing config field", i)
			}
		}
	})

	t.Run("Invalid API URL", func(t *testing.T) {
		cases := []string{
			"invalid-url",
			"ftp://pve.local:8006",
			"http://",
			"http://invalid host:8006",
		}
		for _, u := range cases {
			cfg := sdk.ProviderConfig{
				"api_url":      u,
				"token_id":     "root@pam!token",
				"token_secret": "secret",
			}
			if err := provider.HealthCheck(context.Background(), cfg); err == nil {
				t.Errorf("expected error for invalid URL %q", u)
			}
		}
	})

	t.Run("Connection Refused / Network Error", func(t *testing.T) {
		cfg := sdk.ProviderConfig{
			"api_url":      "http://127.0.0.1:59999",
			"token_id":     "root@pam!token",
			"token_secret": "secret",
		}
		if err := provider.HealthCheck(context.Background(), cfg); err == nil {
			t.Error("expected error when connection refused")
		}
	})
}

func TestProxmoxProvider_Discover_Full(t *testing.T) {
	provider := proxmox.NewProvider()

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/api2/json/nodes":
			w.WriteHeader(http.StatusOK)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{
				"data": []map[string]interface{}{
					{
						"node":    "pve-01",
						"status":  "online",
						"maxcpu":  16,
						"maxmem":  68719476736,
						"maxdisk": 1000000000000,
						// volatile metrics that should be ignored:
						"cpu":    0.05,
						"mem":    8589934592,
						"disk":   10737418240,
						"uptime": 123456,
					},
					{
						"node":    "pve-offline",
						"status":  "offline",
						"maxcpu":  8,
						"maxmem":  34359738368,
						"maxdisk": 500000000000,
					},
				},
			})
		case "/api2/json/nodes/pve-01/qemu":
			w.WriteHeader(http.StatusOK)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{
				"data": []map[string]interface{}{
					{
						"vmid":    101,
						"name":    "ubuntu-server",
						"status":  "running",
						"cpus":    4,
						"maxmem":  8589934592,
						"maxdisk": 107374182400,
						"cpu":     0.02,
						"uptime":  3600,
					},
					{
						"vmid":    102,
						"name":    "win-vm",
						"status":  "stopped",
						"cpus":    2,
						"maxmem":  4294967296,
						"maxdisk": 53687091200,
					},
					{
						"vmid":    103,
						"name":    "",
						"status":  "paused",
						"cpus":    1,
						"maxmem":  2147483648,
						"maxdisk": 21474836480,
					},
				},
			})
		case "/api2/json/nodes/pve-01/qemu/101/agent/network-get-interfaces":
			// Standard guest agent nested format with loopback and eth0
			w.WriteHeader(http.StatusOK)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{
				"data": map[string]interface{}{
					"result": []map[string]interface{}{
						{
							"name":             "lo",
							"hardware-address": "00:00:00:00:00:00",
							"ip-addresses": []map[string]interface{}{
								{"ip-address": "127.0.0.1", "ip-address-type": "ipv4"},
							},
						},
						{
							"name":             "eth0",
							"hardware-address": "bc:24:11:22:33:44",
							"ip-addresses": []map[string]interface{}{
								{"ip-address": "192.168.1.150", "ip-address-type": "ipv4", "prefix": 24},
								{"ip-address": "fe80::be24:11ff:fe22:3344", "ip-address-type": "ipv6"},
							},
						},
					},
				},
			})
		case "/api2/json/nodes/pve-01/qemu/102/agent/network-get-interfaces":
			// Guest agent not running (returns HTTP 500)
			w.WriteHeader(http.StatusInternalServerError)
			_, _ = w.Write([]byte(`{"data":null,"errors":"QEMU guest agent is not running"}`))
		case "/api2/json/nodes/pve-01/qemu/103/agent/network-get-interfaces":
			// Direct array format
			w.WriteHeader(http.StatusOK)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{
				"data": []map[string]interface{}{
					{
						"name":             "ens18",
						"hardware-address": "52:54:00:aa:bb:cc",
						"ip-addresses": []map[string]interface{}{
							{"ip-address": "192.168.1.151", "ip-address-type": "ipv4"},
						},
					},
				},
			})
		case "/api2/json/nodes/pve-01/lxc":
			w.WriteHeader(http.StatusOK)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{
				"data": []map[string]interface{}{
					{
						"vmid":    201,
						"name":    "pihole-ct",
						"status":  "running",
						"cpus":    2,
						"maxmem":  1073741824,
						"maxdisk": 10737418240,
					},
					{
						"vmid":    202,
						"name":    "",
						"status":  "stopped",
						"cpus":    4,
						"maxmem":  4294967296,
						"maxdisk": 42949672960,
					},
				},
			})
		case "/api2/json/nodes/pve-offline/qemu":
			w.WriteHeader(http.StatusOK)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{"data": []interface{}{}})
		case "/api2/json/nodes/pve-offline/lxc":
			w.WriteHeader(http.StatusOK)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{"data": []interface{}{}})
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer ts.Close()

	u, _ := url.Parse(ts.URL)
	// The provider strips the port: a colon inside the scope would break the canonical
	// four-segment ProviderRef key.
	scope := sdk.SanitizeRefSegment(u.Hostname())
	if strings.Contains(scope, ":") {
		t.Fatalf("scope %q must not contain a colon, it would break the canonical key", scope)
	}

	cfg := sdk.ProviderConfig{
		"api_url":      ts.URL,
		"token_id":     "root@pam!token",
		"token_secret": "uuid-secret-1234",
		"verify_ssl":   false,
	}

	devices, err := provider.Discover(context.Background(), cfg)
	if err != nil {
		t.Fatalf("expected nil error on Discover, got %v", err)
	}

	// 2 nodes + 3 VMs + 2 LXCs = 7 devices
	if len(devices) != 7 {
		t.Fatalf("expected 7 devices, got %d", len(devices))
	}

	// Helper to find device by hostname
	findDevice := func(hostname string) *sdk.NormalizedDevice {
		for i := range devices {
			if devices[i].Hostname == hostname {
				return &devices[i]
			}
		}
		return nil
	}

	// 1. Check Node pve-01
	node := findDevice("pve-01")
	if node == nil {
		t.Fatal("node pve-01 not found")
	}
	if node.DeviceType != "server" {
		t.Errorf("expected DeviceType server, got %s", node.DeviceType)
	}
	nodeRef := "proxmox:" + scope + ":node:pve-01"
	if node.ProviderRef == nil || node.ProviderRef.Key() != nodeRef {
		t.Errorf("expected provider_ref %s, got %v", nodeRef, node.ProviderRef)
	}
	if node.ParentProviderRef != nil {
		t.Errorf("expected cluster node to have no parent, got %v", node.ParentProviderRef)
	}
	proxmoxMeta, ok := node.Metadata["proxmox"].(map[string]interface{})
	if !ok {
		t.Fatal("expected proxmox metadata map")
	}
	if proxmoxMeta["is_host"] != true {
		t.Errorf("expected is_host true, got %v", proxmoxMeta["is_host"])
	}
	if proxmoxMeta["power_state"] != "running" {
		t.Errorf("expected power_state running, got %v", proxmoxMeta["power_state"])
	}
	if proxmoxMeta["cores"] != int64(16) {
		t.Errorf("expected cores 16, got %v", proxmoxMeta["cores"])
	}
	if proxmoxMeta["memory_bytes"] != int64(68719476736) {
		t.Errorf("expected memory_bytes 68719476736, got %v", proxmoxMeta["memory_bytes"])
	}
	if proxmoxMeta["disk_bytes"] != int64(1000000000000) {
		t.Errorf("expected disk_bytes 1000000000000, got %v", proxmoxMeta["disk_bytes"])
	}
	// Assert no volatile metrics
	if _, exists := proxmoxMeta["cpu"]; exists {
		t.Error("volatile metric cpu must not be in metadata")
	}
	if _, exists := proxmoxMeta["uptime"]; exists {
		t.Error("volatile metric uptime must not be in metadata")
	}

	// 2. Check Node pve-offline
	offlineNode := findDevice("pve-offline")
	if offlineNode == nil {
		t.Fatal("node pve-offline not found")
	}
	offlineMeta := offlineNode.Metadata["proxmox"].(map[string]interface{})
	if offlineMeta["power_state"] != "stopped" {
		t.Errorf("expected power_state stopped for offline node, got %v", offlineMeta["power_state"])
	}

	// 3. Check QEMU VM 101 with guest agent IP & MAC
	vm101 := findDevice("ubuntu-server")
	if vm101 == nil {
		t.Fatal("vm ubuntu-server not found")
	}
	if vm101.DeviceType != "vm" {
		t.Errorf("expected DeviceType vm, got %s", vm101.DeviceType)
	}
	if vm101.IPAddress != "192.168.1.150" {
		t.Errorf("expected IPAddress 192.168.1.150, got %s", vm101.IPAddress)
	}
	if vm101.MACAddress != "bc:24:11:22:33:44" {
		t.Errorf("expected MACAddress bc:24:11:22:33:44, got %s", vm101.MACAddress)
	}
	vm101Ref := "proxmox:" + scope + ":qemu:101"
	if vm101.ProviderRef == nil || vm101.ProviderRef.Key() != vm101Ref {
		t.Errorf("expected provider_ref %s, got %v", vm101Ref, vm101.ProviderRef)
	}
	if vm101.ParentProviderRef == nil || vm101.ParentProviderRef.Key() != nodeRef {
		t.Errorf("expected parent_provider_ref %s, got %v", nodeRef, vm101.ParentProviderRef)
	}
	vm101Meta := vm101.Metadata["proxmox"].(map[string]interface{})
	if vm101Meta["power_state"] != "running" {
		t.Errorf("expected power_state running, got %v", vm101Meta["power_state"])
	}
	if vm101Meta["cores"] != int64(4) {
		t.Errorf("expected cores 4, got %v", vm101Meta["cores"])
	}
	if vm101Meta["type"] != "qemu" {
		t.Errorf("expected type qemu, got %v", vm101Meta["type"])
	}

	// 4. Check QEMU VM 102 (stopped, guest agent failed)
	vm102 := findDevice("win-vm")
	if vm102 == nil {
		t.Fatal("vm win-vm not found")
	}
	if vm102.IPAddress != "" || vm102.MACAddress != "" {
		t.Errorf("expected empty IP/MAC for VM without agent, got ip=%s mac=%s", vm102.IPAddress, vm102.MACAddress)
	}
	vm102Meta := vm102.Metadata["proxmox"].(map[string]interface{})
	if vm102Meta["power_state"] != "stopped" {
		t.Errorf("expected power_state stopped, got %v", vm102Meta["power_state"])
	}

	// 5. Check QEMU VM 103 (fallback hostname vm-103, paused)
	vm103 := findDevice("vm-103")
	if vm103 == nil {
		t.Fatal("vm-103 fallback hostname not found")
	}
	if vm103.IPAddress != "192.168.1.151" || vm103.MACAddress != "52:54:00:aa:bb:cc" {
		t.Errorf("expected resolved ip/mac on direct array format, got %s / %s", vm103.IPAddress, vm103.MACAddress)
	}
	vm103Meta := vm103.Metadata["proxmox"].(map[string]interface{})
	if vm103Meta["power_state"] != "paused" {
		t.Errorf("expected power_state paused, got %v", vm103Meta["power_state"])
	}

	// 6. Check LXC Container 201 (pihole-ct)
	lxc201 := findDevice("pihole-ct")
	if lxc201 == nil {
		t.Fatal("lxc pihole-ct not found")
	}
	if lxc201.DeviceType != "container" {
		t.Errorf("expected DeviceType container, got %s", lxc201.DeviceType)
	}
	lxc201Ref := "proxmox:" + scope + ":lxc:201"
	if lxc201.ProviderRef == nil || lxc201.ProviderRef.Key() != lxc201Ref {
		t.Errorf("expected provider_ref %s, got %v", lxc201Ref, lxc201.ProviderRef)
	}
	if lxc201.ParentProviderRef == nil || lxc201.ParentProviderRef.Key() != nodeRef {
		t.Errorf("expected parent_provider_ref %s, got %v", nodeRef, lxc201.ParentProviderRef)
	}
	lxc201Meta := lxc201.Metadata["proxmox"].(map[string]interface{})
	if lxc201Meta["power_state"] != "running" {
		t.Errorf("expected power_state running, got %v", lxc201Meta["power_state"])
	}
	if lxc201Meta["type"] != "lxc" {
		t.Errorf("expected type lxc, got %v", lxc201Meta["type"])
	}

	// 7. Check LXC Container 202 (fallback hostname ct-202)
	lxc202 := findDevice("ct-202")
	if lxc202 == nil {
		t.Fatal("ct-202 fallback hostname not found")
	}
	lxc202Meta := lxc202.Metadata["proxmox"].(map[string]interface{})
	if lxc202Meta["power_state"] != "stopped" {
		t.Errorf("expected power_state stopped, got %v", lxc202Meta["power_state"])
	}
}

func TestProxmoxProvider_Discover_ErrorResilience(t *testing.T) {
	provider := proxmox.NewProvider()

	t.Run("Nodes Endpoint Failure Returns Error", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusInternalServerError)
		}))
		defer ts.Close()

		cfg := sdk.ProviderConfig{"api_url": ts.URL, "token_id": "a", "token_secret": "b"}
		if _, err := provider.Discover(context.Background(), cfg); err == nil {
			t.Error("expected error when nodes listing returns HTTP 500")
		}
	})

	t.Run("Nodes Malformed JSON Returns Error", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write([]byte("not-json"))
		}))
		defer ts.Close()

		cfg := sdk.ProviderConfig{"api_url": ts.URL, "token_id": "a", "token_secret": "b"}
		if _, err := provider.Discover(context.Background(), cfg); err == nil {
			t.Error("expected error when nodes payload is invalid json")
		}
	})

	t.Run("QEMU and LXC Failures Do Not Halt Discovery (Error Isolation)", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			switch r.URL.Path {
			case "/api2/json/nodes":
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode(map[string]interface{}{
					"data": []map[string]interface{}{
						{"node": "pve-isolated", "status": "online"},
					},
				})
			case "/api2/json/nodes/pve-isolated/qemu":
				w.WriteHeader(http.StatusInternalServerError)
			case "/api2/json/nodes/pve-isolated/lxc":
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode(map[string]interface{}{
					"data": []map[string]interface{}{
						{"vmid": 301, "name": "lxc-ok", "status": "running"},
					},
				})
			default:
				w.WriteHeader(http.StatusNotFound)
			}
		}))
		defer ts.Close()

		cfg := sdk.ProviderConfig{"api_url": ts.URL, "token_id": "a", "token_secret": "b"}
		devices, err := provider.Discover(context.Background(), cfg)
		if err != nil {
			t.Fatalf("expected nil error on Discover, got %v", err)
		}
		// Expect node + LXC = 2 devices
		if len(devices) != 2 {
			t.Fatalf("expected 2 devices, got %d", len(devices))
		}
		if devices[0].Hostname != "pve-isolated" || devices[1].Hostname != "lxc-ok" {
			t.Errorf("unexpected devices returned: %v", devices)
		}
	})

	t.Run("LXC Failure Does Not Halt QEMU Discovery", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			switch r.URL.Path {
			case "/api2/json/nodes":
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode(map[string]interface{}{
					"data": []map[string]interface{}{
						{"node": "pve-lxc-fail", "status": "online"},
					},
				})
			case "/api2/json/nodes/pve-lxc-fail/qemu":
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode(map[string]interface{}{
					"data": []map[string]interface{}{
						{"vmid": 401, "name": "qemu-ok", "status": "running"},
					},
				})
			case "/api2/json/nodes/pve-lxc-fail/lxc":
				w.WriteHeader(http.StatusInternalServerError)
			default:
				w.WriteHeader(http.StatusNotFound)
			}
		}))
		defer ts.Close()

		cfg := sdk.ProviderConfig{"api_url": ts.URL, "token_id": "a", "token_secret": "b"}
		devices, err := provider.Discover(context.Background(), cfg)
		if err != nil {
			t.Fatalf("expected nil error on Discover, got %v", err)
		}
		if len(devices) != 2 {
			t.Fatalf("expected 2 devices (node + qemu), got %d", len(devices))
		}
		if devices[0].Hostname != "pve-lxc-fail" || devices[1].Hostname != "qemu-ok" {
			t.Errorf("unexpected devices returned: %v", devices)
		}
	})

	t.Run("Context Cancellation Aborts Discover", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusOK)
			_ = json.NewEncoder(w).Encode(map[string]interface{}{
				"data": []map[string]interface{}{
					{"node": "pve-cancel", "status": "online"},
				},
			})
		}))
		defer ts.Close()

		ctx, cancel := context.WithCancel(context.Background())
		cancel() // cancel immediately

		cfg := sdk.ProviderConfig{"api_url": ts.URL, "token_id": "a", "token_secret": "b"}
		_, err := provider.Discover(ctx, cfg)
		if err == nil {
			t.Error("expected error when context is cancelled")
		}
	})

	t.Run("Malformed JSON on QEMU and LXC endpoints", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			switch r.URL.Path {
			case "/api2/json/nodes":
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode(map[string]interface{}{
					"data": []map[string]interface{}{
						{"node": "pve-badjson", "status": "online"},
					},
				})
			case "/api2/json/nodes/pve-badjson/qemu":
				w.WriteHeader(http.StatusOK)
				_, _ = w.Write([]byte("bad-qemu-json"))
			case "/api2/json/nodes/pve-badjson/lxc":
				w.WriteHeader(http.StatusOK)
				_, _ = w.Write([]byte("bad-lxc-json"))
			default:
				w.WriteHeader(http.StatusNotFound)
			}
		}))
		defer ts.Close()

		cfg := sdk.ProviderConfig{"api_url": ts.URL, "token_id": "a", "token_secret": "b"}
		devices, err := provider.Discover(context.Background(), cfg)
		if err != nil {
			t.Fatalf("expected nil error on Discover, got %v", err)
		}
		if len(devices) != 1 || devices[0].Hostname != "pve-badjson" {
			t.Errorf("expected 1 host device only, got %v", devices)
		}
	})

	t.Run("Guest Agent Interface Edge Cases", func(t *testing.T) {
		ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			switch r.URL.Path {
			case "/api2/json/nodes":
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode(map[string]interface{}{
					"data": []map[string]interface{}{
						{"node": "pve-agent-edge", "status": "online"},
					},
				})
			case "/api2/json/nodes/pve-agent-edge/qemu":
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode(map[string]interface{}{
					"data": []map[string]interface{}{
						{"vmid": 501, "name": "vm-docker-only", "status": "running"},
						{"vmid": 502, "name": "vm-invalid-ip", "status": "running"},
						{"vmid": 503, "name": "vm-empty-agent", "status": "running"},
					},
				})
			case "/api2/json/nodes/pve-agent-edge/qemu/501/agent/network-get-interfaces":
				// docker0 and veth interface ignored in primary loop, fallback handles other non-lo IP
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode(map[string]interface{}{
					"data": map[string]interface{}{
						"result": []map[string]interface{}{
							{
								"name":             "docker0",
								"hardware-address": "02:42:1a:2b:3c:4d",
								"ip-addresses": []map[string]interface{}{
									{"ip-address": "172.17.0.1", "ip-address-type": "ipv4"},
								},
							},
							{
								"name":             "veth1234",
								"hardware-address": "02:42:1a:2b:3c:4e",
								"ip-addresses": []map[string]interface{}{
									{"ip-address": "172.18.0.1", "ip-address-type": "ipv4"},
								},
							},
						},
					},
				})
			case "/api2/json/nodes/pve-agent-edge/qemu/502/agent/network-get-interfaces":
				// Invalid IP strings, link-local only, empty strings
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode(map[string]interface{}{
					"data": map[string]interface{}{
						"result": []map[string]interface{}{
							{
								"name":             "eth0",
								"hardware-address": "bc:24:11:22:33:55",
								"ip-addresses": []map[string]interface{}{
									{"ip-address": "", "ip-address-type": "ipv4"},
									{"ip-address": "invalid-ip", "ip-address-type": "ipv4"},
									{"ip-address": "169.254.1.1", "ip-address-type": "ipv4"},
								},
							},
						},
					},
				})
			case "/api2/json/nodes/pve-agent-edge/qemu/503/agent/network-get-interfaces":
				// Malformed agent JSON
				w.WriteHeader(http.StatusOK)
				_, _ = w.Write([]byte("not-json"))
			case "/api2/json/nodes/pve-agent-edge/lxc":
				w.WriteHeader(http.StatusOK)
				_ = json.NewEncoder(w).Encode(map[string]interface{}{"data": []interface{}{}})
			default:
				w.WriteHeader(http.StatusNotFound)
			}
		}))
		defer ts.Close()

		cfg := sdk.ProviderConfig{"api_url": ts.URL, "token_id": "a", "token_secret": "b", "tls_verify": true}
		devices, err := provider.Discover(context.Background(), cfg)
		if err != nil {
			t.Fatalf("expected nil error on Discover, got %v", err)
		}
		// 1 node + 3 VMs = 4 devices
		if len(devices) != 4 {
			t.Fatalf("expected 4 devices, got %d", len(devices))
		}

		vm501 := devices[1]
		if vm501.Hostname != "vm-docker-only" || vm501.IPAddress != "172.17.0.1" {
			t.Errorf("expected vm-docker-only with fallback IP 172.17.0.1, got hostname=%s ip=%s", vm501.Hostname, vm501.IPAddress)
		}

		vm502 := devices[2]
		if vm502.Hostname != "vm-invalid-ip" || vm502.IPAddress != "" {
			t.Errorf("expected empty IP for invalid IP addresses, got %s", vm502.IPAddress)
		}

		vm503 := devices[3]
		if vm503.Hostname != "vm-empty-agent" || vm503.IPAddress != "" {
			t.Errorf("expected empty IP for malformed agent json, got %s", vm503.IPAddress)
		}
	})
}

func TestProxmoxProvider_TLSVerificationIsOnByDefault(t *testing.T) {
	// An absent or malformed flag must never be the reason InfraMap talks to an unverified
	// endpoint: the schema default and the runtime default have to agree on "verify".
	provider := proxmox.NewProvider()

	var field *sdk.ConfigField
	for i, f := range provider.ConfigSchema().Fields {
		if f.Key == "tls_verify" {
			field = &provider.ConfigSchema().Fields[i]
			break
		}
	}
	if field == nil {
		t.Fatal("expected a tls_verify field in the config schema")
	}
	if field.Default != true {
		t.Errorf("tls_verify default = %v, want true", field.Default)
	}

	// A config that omits the flag entirely must still reach a TLS-verifying client.
	ts := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer ts.Close()

	cfg := sdk.ProviderConfig{"api_url": ts.URL, "token_id": "a", "token_secret": "b"}
	if err := provider.HealthCheck(context.Background(), cfg); err == nil {
		t.Error("expected the self-signed certificate to be rejected when tls_verify is unset")
	}
}
