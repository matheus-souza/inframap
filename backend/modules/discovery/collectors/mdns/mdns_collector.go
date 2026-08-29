// Package mdns implements mDNS / Bonjour service discovery collector for local subnets.
package mdns

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"os"
	"strings"
	"time"

	"github.com/matheussouza/inframap/modules/discovery/collectors"
	"golang.org/x/net/dns/dnsmessage"
)

// MDNSMulticastIPv4 is the standard mDNS IPv4 multicast address (224.0.0.251:5353).
const MDNSMulticastIPv4 = "224.0.0.251:5353"

// DefaultQueryTimeout is the default timeout for waiting for mDNS multicast responses.
const DefaultQueryTimeout = 2500 * time.Millisecond

// DefaultMDNSServices are the standard Bonjour/mDNS service types queried during discovery.
var DefaultMDNSServices = []string{
	"_services._dns-sd._udp.local.",
	"_workstation._tcp.local.",
	"_http._tcp.local.",
	"_printer._tcp.local.",
	"_ipp._tcp.local.",
	"_googlecast._tcp.local.",
	"_airplay._tcp.local.",
	"_smb._tcp.local.",
	"_ssh._tcp.local.",
	"_device-info._tcp.local.",
	"_companion-link._tcp.local.",
	"_scanner._tcp.local.",
	"_sftp-ssh._tcp.local.",
	"_homekit._tcp.local.",
	"_raop._tcp.local.",
	"_appletv-v2._tcp.local.",
	"_sonos._tcp.local.",
	"_spotify-connect._tcp.local.",
}

// MDNSClient abstracts mDNS multicast query and response reception for testability.
type MDNSClient interface {
	Query(ctx context.Context, services []string, timeout time.Duration) ([]collectors.RawObservation, error)
}

// MDNSCollector discovers hosts and services on the local subnet via mDNS/Bonjour.
type MDNSCollector struct {
	client   MDNSClient
	timeout  time.Duration
	services []string
}

// NewMDNSCollector creates a new MDNSCollector with the given client.
// If client is nil, NewDefaultMDNSClient() is used.
func NewMDNSCollector(client MDNSClient) *MDNSCollector {
	if client == nil {
		client = NewDefaultMDNSClient()
	}
	return &MDNSCollector{
		client:   client,
		timeout:  DefaultQueryTimeout,
		services: DefaultMDNSServices,
	}
}

// ID returns the unique collector identifier.
func (c *MDNSCollector) ID() string { return "mdns" }

// Name returns the human-readable collector name.
func (c *MDNSCollector) Name() string { return "mDNS / Bonjour Service Discovery" }

// Collect performs mDNS service discovery on the target network subnet.
// Only responses originating from IP addresses within the target CIDR are retained.
func (c *MDNSCollector) Collect(ctx context.Context, target collectors.DiscoveryTarget) ([]collectors.RawObservation, error) {
	if err := ctx.Err(); err != nil {
		return nil, fmt.Errorf("mdns collector: %w", err)
	}

	if c.client == nil {
		return []collectors.RawObservation{}, nil
	}

	// Validate CIDR format and prefix size (/16 max per Guideline #81)
	if _, err := collectors.ParseTargetPrefix(target.CIDR); err != nil {
		return nil, fmt.Errorf("mdns collector: %w", err)
	}

	_, ipNet, err := net.ParseCIDR(target.CIDR)
	if err != nil {
		return nil, fmt.Errorf("mdns collector: invalid target CIDR %q: %w", target.CIDR, err)
	}

	rawObservations, err := c.client.Query(ctx, c.services, c.timeout)
	if err != nil {
		if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
			return nil, fmt.Errorf("mdns collector: %w", err)
		}
		// If query fails due to local network restriction (e.g. multicast unsupported), log warning and return empty
		slog.Warn("mDNS discovery query failed", slog.Any("error", err))
		return []collectors.RawObservation{}, nil
	}

	// Filter observations by target CIDR subnet and deduplicate per IP
	now := time.Now()
	byIP := make(map[string]*collectors.RawObservation)

	for _, obs := range rawObservations {
		if err := ctx.Err(); err != nil {
			return nil, fmt.Errorf("mdns collector: %w", err)
		}

		ip := net.ParseIP(obs.IPAddress)
		if ip == nil || !ipNet.Contains(ip) {
			continue // ignore IP outside target CIDR
		}

		existing, found := byIP[obs.IPAddress]
		if !found {
			entry := obs
			entry.ProtocolSource = "mdns"
			entry.ConfidenceScore = 70
			entry.ObservedAt = now
			if entry.RawMetadata == nil {
				entry.RawMetadata = make(map[string]interface{})
			}
			byIP[obs.IPAddress] = &entry
		} else {
			// Merge additive metadata and pick best hostname
			mergeObservations(existing, obs)
		}
	}

	result := make([]collectors.RawObservation, 0, len(byIP))
	for _, obs := range byIP {
		result = append(result, *obs)
	}

	return result, nil
}

func mergeObservations(dst *collectors.RawObservation, src collectors.RawObservation) {
	if dst.Hostname == "" && src.Hostname != "" {
		dst.Hostname = src.Hostname
	}
	if dst.Vendor == "" && src.Vendor != "" {
		dst.Vendor = src.Vendor
	}
	if dst.OS == "" && src.OS != "" {
		dst.OS = src.OS
	}

	if dst.RawMetadata == nil {
		dst.RawMetadata = make(map[string]interface{})
	}

	// Merge services list
	dstServices, _ := dst.RawMetadata["services"].([]string)
	if srcServices, ok := src.RawMetadata["services"].([]string); ok {
		seen := make(map[string]bool)
		for _, s := range dstServices {
			seen[s] = true
		}
		for _, s := range srcServices {
			if !seen[s] {
				seen[s] = true
				dstServices = append(dstServices, s)
			}
		}
		dst.RawMetadata["services"] = dstServices
	}

	// Merge TXT records map
	dstTXT, _ := dst.RawMetadata["txt"].(map[string]string)
	if dstTXT == nil {
		dstTXT = make(map[string]string)
	}
	if srcTXT, ok := src.RawMetadata["txt"].(map[string]string); ok {
		for k, v := range srcTXT {
			if _, exists := dstTXT[k]; !exists {
				dstTXT[k] = v
			}
		}
	}
	dst.RawMetadata["txt"] = dstTXT
}

// DefaultMDNSClient implements MDNSClient with pure Go UDP multicast socket.
type DefaultMDNSClient struct {
	listenUDPFunc func(network string, laddr *net.UDPAddr) (PacketConn, error)
}

// PacketConn abstracts UDP socket operations for unit test mocking.
type PacketConn interface {
	SetReadDeadline(t time.Time) error
	SetWriteDeadline(t time.Time) error
	WriteTo(b []byte, dst net.Addr) (int, error)
	ReadFrom(b []byte) (int, net.Addr, error)
	Close() error
}

// NewDefaultMDNSClient creates a new DefaultMDNSClient.
func NewDefaultMDNSClient() *DefaultMDNSClient {
	return &DefaultMDNSClient{
		listenUDPFunc: func(network string, laddr *net.UDPAddr) (PacketConn, error) {
			return net.ListenUDP(network, laddr)
		},
	}
}

// NewDefaultMDNSClientWithConn creates a DefaultMDNSClient with a custom socket listener function.
func NewDefaultMDNSClientWithConn(listenFunc func(network string, laddr *net.UDPAddr) (PacketConn, error)) *DefaultMDNSClient {
	return &DefaultMDNSClient{
		listenUDPFunc: listenFunc,
	}
}

// Query broadcasts mDNS PTR queries and listens for replies until the deadline.
func (c *DefaultMDNSClient) Query(ctx context.Context, services []string, timeout time.Duration) ([]collectors.RawObservation, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}

	if c.listenUDPFunc == nil {
		return nil, errors.New("listenUDPFunc not set")
	}

	dstAddr, err := net.ResolveUDPAddr("udp4", MDNSMulticastIPv4)
	if err != nil {
		return nil, fmt.Errorf("failed to resolve mDNS multicast address: %w", err)
	}

	conn, err := c.listenUDPFunc("udp4", &net.UDPAddr{IP: net.IPv4zero, Port: 0})
	if err != nil {
		return nil, fmt.Errorf("failed to bind mDNS UDP socket: %w", err)
	}
	defer func() { _ = conn.Close() }()

	// Send multicast queries for each service
	for _, svc := range services {
		if err := ctx.Err(); err != nil {
			return nil, err
		}

		queryPacket, packErr := BuildMDNSQuery(svc)
		if packErr != nil {
			continue
		}

		_ = conn.SetWriteDeadline(time.Now().Add(500 * time.Millisecond))
		_, _ = conn.WriteTo(queryPacket, dstAddr)
	}

	// Set socket read deadline based on context or query timeout
	deadline := time.Now().Add(timeout)
	if ctxDeadline, ok := ctx.Deadline(); ok && ctxDeadline.Before(deadline) {
		deadline = ctxDeadline
	}
	_ = conn.SetReadDeadline(deadline)

	var observations []collectors.RawObservation
	buf := make([]byte, 65535)

	for {
		if err := ctx.Err(); err != nil {
			return observations, err
		}

		n, remoteAddr, readErr := conn.ReadFrom(buf)
		if readErr != nil {
			if errors.Is(readErr, os.ErrDeadlineExceeded) || isTimeout(readErr) {
				break // Read deadline reached, finish collecting without hanging
			}
			if ctx.Err() != nil {
				return observations, ctx.Err()
			}
			break // other I/O error
		}

		var senderIP net.IP
		if udpAddr, ok := remoteAddr.(*net.UDPAddr); ok {
			senderIP = udpAddr.IP
		}

		obsList := ParseMDNSPacket(buf[:n], senderIP)
		observations = append(observations, obsList...)
	}

	return observations, nil
}

func isTimeout(err error) bool {
	var netErr net.Error
	if errors.As(err, &netErr) && netErr.Timeout() {
		return true
	}
	return false
}

// BuildMDNSQuery crafts a DNS query message for the given service type.
func BuildMDNSQuery(serviceName string) ([]byte, error) {
	nameStr := serviceName
	if !strings.HasSuffix(nameStr, ".") {
		nameStr += "."
	}
	name, err := dnsmessage.NewName(nameStr)
	if err != nil {
		return nil, fmt.Errorf("invalid service name %q: %w", serviceName, err)
	}

	msg := dnsmessage.Message{
		Header: dnsmessage.Header{
			ID:               0,
			Response:         false,
			OpCode:           0,
			RecursionDesired: false,
		},
		Questions: []dnsmessage.Question{
			{
				Name:  name,
				Type:  dnsmessage.TypePTR,
				Class: dnsmessage.ClassINET,
			},
		},
	}

	return msg.Pack()
}

// ParseMDNSPacket parses a raw DNS message and extracts RawObservation items.
func ParseMDNSPacket(data []byte, senderIP net.IP) []collectors.RawObservation {
	var msg dnsmessage.Message
	if err := msg.Unpack(data); err != nil {
		return nil
	}

	// Extract records
	aRecords := make(map[string][]string)            // name -> [ip1, ip2]
	srvRecords := make(map[string]srvInfo)           // name -> {target, port}
	ptrRecords := make(map[string][]string)          // service -> [instance1, instance2]
	txtRecords := make(map[string]map[string]string) // name -> txt map

	allRecords := append(msg.Answers, msg.Additionals...)

	for _, r := range allRecords {
		rName := r.Header.Name.String()
		switch r.Header.Type {
		case dnsmessage.TypeA:
			if a, ok := r.Body.(*dnsmessage.AResource); ok {
				ipStr := net.IP(a.A[:]).String()
				aRecords[rName] = append(aRecords[rName], ipStr)
			}
		case dnsmessage.TypeAAAA:
			if aaaa, ok := r.Body.(*dnsmessage.AAAAResource); ok {
				ipStr := net.IP(aaaa.AAAA[:]).String()
				aRecords[rName] = append(aRecords[rName], ipStr)
			}
		case dnsmessage.TypePTR:
			if ptr, ok := r.Body.(*dnsmessage.PTRResource); ok {
				ptrRecords[rName] = append(ptrRecords[rName], ptr.PTR.String())
			}
		case dnsmessage.TypeSRV:
			if srv, ok := r.Body.(*dnsmessage.SRVResource); ok {
				srvRecords[rName] = srvInfo{
					Target: srv.Target.String(),
					Port:   srv.Port,
				}
			}
		case dnsmessage.TypeTXT:
			if txt, ok := r.Body.(*dnsmessage.TXTResource); ok {
				txtRecords[rName] = ParseTXTStrings(txt.TXT)
			}
		}
	}

	var results []collectors.RawObservation
	seenIPs := make(map[string]*collectors.RawObservation)

	// 1. Process PTR instance records
	for svcName, instances := range ptrRecords {
		for _, inst := range instances {
			var targetHost string
			var port uint16
			if srv, ok := srvRecords[inst]; ok {
				targetHost = srv.Target
				port = srv.Port
			}

			txtMap := txtRecords[inst]
			if txtMap == nil {
				txtMap = make(map[string]string)
			}

			// Find IP addresses associated with targetHost, inst, or fallback to senderIP
			var ips []string
			if targetHost != "" {
				ips = append(ips, aRecords[targetHost]...)
			}
			ips = append(ips, aRecords[inst]...)

			if len(ips) == 0 && senderIP != nil && !senderIP.IsUnspecified() && !senderIP.IsMulticast() {
				ips = append(ips, senderIP.String())
			}

			hostname := CleanMDNSHostname(targetHost)
			if hostname == "" {
				hostname = CleanMDNSHostname(inst)
			}

			vendor, osName := ExtractVendorAndOS(svcName, txtMap)

			for _, ip := range ips {
				if ip == "" {
					continue
				}
				cleanSvc := CleanServiceName(svcName)
				if existing, exists := seenIPs[ip]; exists {
					if existing.Hostname == "" && hostname != "" {
						existing.Hostname = hostname
					}
					if existing.Vendor == "" && vendor != "" {
						existing.Vendor = vendor
					}
					if existing.OS == "" && osName != "" {
						existing.OS = osName
					}
					if cleanSvc != "" {
						servicesList, _ := existing.RawMetadata["services"].([]string)
						existing.RawMetadata["services"] = appendUnique(servicesList, cleanSvc)
					}
				} else {
					obs := collectors.RawObservation{
						IPAddress:       ip,
						Hostname:        hostname,
						Vendor:          vendor,
						OS:              osName,
						ProtocolSource:  "mdns",
						ConfidenceScore: 70,
						RawMetadata: map[string]interface{}{
							"services": []string{},
							"txt":      txtMap,
						},
						ObservedAt: time.Now(),
					}
					if port > 0 {
						obs.RawMetadata["port"] = int(port)
					}
					if cleanSvc != "" {
						obs.RawMetadata["services"] = []string{cleanSvc}
					}
					seenIPs[ip] = &obs
				}
			}
		}
	}

	// 2. Process standalone A/AAAA records that may not have PTR links
	for hostName, ips := range aRecords {
		cleanedName := CleanMDNSHostname(hostName)
		txtMap := txtRecords[hostName]
		if txtMap == nil {
			txtMap = make(map[string]string)
		}
		vendor, osName := ExtractVendorAndOS("", txtMap)

		for _, ip := range ips {
			if _, exists := seenIPs[ip]; !exists {
				obs := collectors.RawObservation{
					IPAddress:       ip,
					Hostname:        cleanedName,
					Vendor:          vendor,
					OS:              osName,
					ProtocolSource:  "mdns",
					ConfidenceScore: 70,
					RawMetadata: map[string]interface{}{
						"services": []string{},
						"txt":      txtMap,
					},
					ObservedAt: time.Now(),
				}
				seenIPs[ip] = &obs
			}
		}
	}

	// 3. Fallback: if senderIP provided and not yet added but we saw host info
	if len(seenIPs) == 0 && senderIP != nil && !senderIP.IsUnspecified() && !senderIP.IsMulticast() {
		for inst, srv := range srvRecords {
			h := CleanMDNSHostname(srv.Target)
			if h == "" {
				h = CleanMDNSHostname(inst)
			}
			txtMap := txtRecords[inst]
			if txtMap == nil {
				txtMap = make(map[string]string)
			}
			vendor, osName := ExtractVendorAndOS("", txtMap)

			obs := collectors.RawObservation{
				IPAddress:       senderIP.String(),
				Hostname:        h,
				Vendor:          vendor,
				OS:              osName,
				ProtocolSource:  "mdns",
				ConfidenceScore: 70,
				RawMetadata: map[string]interface{}{
					"services": []string{},
					"txt":      txtMap,
				},
				ObservedAt: time.Now(),
			}
			if srv.Port > 0 {
				obs.RawMetadata["port"] = int(srv.Port)
			}
			seenIPs[senderIP.String()] = &obs
			break
		}
	}

	for _, obs := range seenIPs {
		results = append(results, *obs)
	}

	return results
}

type srvInfo struct {
	Target string
	Port   uint16
}

// CleanMDNSHostname sanitizes raw mDNS domain names into clean hostnames.
// Strips .local, trailing dots, and service identifiers.
func CleanMDNSHostname(raw string) string {
	s := strings.TrimSpace(raw)
	s = strings.TrimSuffix(s, ".")

	if strings.HasSuffix(strings.ToLower(s), ".local") {
		s = s[:len(s)-len(".local")]
	}

	// Strip common service suffixes if part of instance name
	for _, suffix := range []string{
		"._workstation._tcp",
		"._http._tcp",
		"._printer._tcp",
		"._ipp._tcp",
		"._googlecast._tcp",
		"._airplay._tcp",
		"._smb._tcp",
		"._ssh._tcp",
		"._device-info._tcp",
		"._companion-link._tcp",
		"._scanner._tcp",
		"._sftp-ssh._tcp",
		"._homekit._tcp",
		"._raop._tcp",
		"._appletv-v2._tcp",
		"._sonos._tcp",
		"._spotify-connect._tcp",
		"._services._dns-sd._udp",
	} {
		if idx := strings.Index(strings.ToLower(s), suffix); idx != -1 {
			s = s[:idx]
		}
	}

	s = strings.Trim(s, ". ")
	return s
}

// CleanServiceName trims trailing dots and .local domain from service names.
func CleanServiceName(raw string) string {
	s := strings.TrimSpace(raw)
	s = strings.TrimSuffix(s, ".")
	if strings.HasSuffix(strings.ToLower(s), ".local") {
		s = s[:len(s)-len(".local")]
	}
	return s
}

// ExtractVendorAndOS extracts hardware vendor and OS names from TXT records and service types.
func ExtractVendorAndOS(service string, txt map[string]string) (vendor string, osName string) {
	for k, v := range txt {
		kLower := strings.ToLower(k)
		vLower := strings.ToLower(v)

		switch kLower {
		case "model", "modelname", "md", "am", "product":
			switch {
			case strings.Contains(vLower, "macbook") || strings.Contains(vLower, "macmini") || strings.Contains(vLower, "imac") || strings.Contains(vLower, "macpro") || strings.Contains(vLower, "iphone") || strings.Contains(vLower, "ipad") || strings.Contains(vLower, "appletv") || strings.Contains(vLower, "airplay"):
				vendor = "Apple"
			case strings.Contains(vLower, "chromecast") || strings.Contains(vLower, "google"):
				vendor = "Google"
			case strings.Contains(vLower, "synology") || strings.Contains(vLower, "diskstation"):
				vendor = "Synology"
			case strings.Contains(vLower, "canon"):
				vendor = "Canon"
			case strings.Contains(vLower, "hp") || strings.Contains(vLower, "laserjet") || strings.Contains(vLower, "officejet"):
				vendor = "HP"
			case strings.Contains(vLower, "epson"):
				vendor = "Epson"
			case strings.Contains(vLower, "brother"):
				vendor = "Brother"
			case strings.Contains(vLower, "raspberry"):
				vendor = "Raspberry Pi Foundation"
			case strings.Contains(vLower, "sonos"):
				vendor = "Sonos"
			case strings.Contains(vLower, "unifi") || strings.Contains(vLower, "edgerouter"):
				vendor = "Ubiquiti"
			}
		case "vendor", "manufacturer", "mfg":
			if vendor == "" {
				vendor = v
			}
		case "osx":
			osName = "macOS"
		case "os", "sys", "osversion":
			switch {
			case strings.Contains(vLower, "osx") || strings.Contains(vLower, "macos") || strings.Contains(vLower, "darwin"):
				osName = "macOS"
			case strings.Contains(vLower, "linux"):
				osName = "Linux"
			case strings.Contains(vLower, "windows"):
				osName = "Windows"
			case strings.Contains(vLower, "android"):
				osName = "Android"
			default:
				if osName == "" {
					osName = v
				}
			}
		}
	}

	// Service type heuristic fallbacks
	svcLower := strings.ToLower(service)
	if vendor == "" {
		switch {
		case strings.Contains(svcLower, "airplay") || strings.Contains(svcLower, "raop") || strings.Contains(svcLower, "companion-link") || strings.Contains(svcLower, "apple"):
			vendor = "Apple"
		case strings.Contains(svcLower, "googlecast"):
			vendor = "Google"
		case strings.Contains(svcLower, "sonos"):
			vendor = "Sonos"
		case strings.Contains(svcLower, "spotify"):
			vendor = "Spotify"
		}
	}

	return vendor, osName
}

// ParseTXTStrings parses DNS TXT character-strings into key-value map.
func ParseTXTStrings(entries []string) map[string]string {
	res := make(map[string]string)
	for _, entry := range entries {
		parts := strings.SplitN(entry, "=", 2)
		if len(parts) == 2 {
			res[parts[0]] = parts[1]
		} else if len(parts) == 1 && parts[0] != "" {
			res[parts[0]] = "true"
		}
	}
	return res
}

func appendUnique(slice []string, item string) []string {
	for _, s := range slice {
		if s == item {
			return slice
		}
	}
	return append(slice, item)
}
