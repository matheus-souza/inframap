package engine

import (
	"encoding/json"
	"net"
	"net/netip"
	"strings"

	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/discovery/dto"
)

// SourceConfidenceMatrix maps discovery collector types to their confidence score.
var SourceConfidenceMatrix = map[string]int{
	"user_override": 100,
	"proxmox":       80,
	"docker":        80,
	"unifi":         80,
	"mdns":          50,
	"lldp":          50,
	"snmp":          50,
	"icmp_sweep":    20,
	"arp_sweep":     20,
}

// FieldReconciler handles merging incoming scan data into active inventory records.
type FieldReconciler interface {
	Reconcile(existing *db.Device, incoming *dto.NormalizedDeviceDTO, sourceType string) (updated *db.Device, changed bool)
}

// DefaultFieldReconciler implements field-level confidence score merging and user-lock protection.
type DefaultFieldReconciler struct{}

// NewDefaultFieldReconciler creates a new DefaultFieldReconciler.
func NewDefaultFieldReconciler() *DefaultFieldReconciler {
	return &DefaultFieldReconciler{}
}

// Reconcile evaluates field updates using confidence scores and user-lock immunity.
func (r *DefaultFieldReconciler) Reconcile(existing *db.Device, incoming *dto.NormalizedDeviceDTO, sourceType string) (*db.Device, bool) {
	changed := false
	updated := *existing

	var meta map[string]interface{}
	if len(existing.Metadata) > 0 {
		_ = json.Unmarshal(existing.Metadata, &meta)
	}
	if meta == nil {
		meta = make(map[string]interface{})
	}

	lockedFields := extractUserLockedFields(meta)
	incomingScore, exists := SourceConfidenceMatrix[sourceType]
	if !exists {
		incomingScore = 20
	}

	existingScore := extractFieldScore(meta, "source_confidence_score")

	// Apply field updates if incoming score >= existing score and field is not locked by user
	if incomingScore >= existingScore {
		if !lockedFields["hostname"] && incoming.Hostname != "" && incoming.Hostname != updated.Hostname {
			updated.Hostname = incoming.Hostname
			changed = true
		}
		if !lockedFields["ip_address"] && incoming.IPAddress != "" {
			if addr, err := netip.ParseAddr(incoming.IPAddress); err == nil {
				if updated.IpAddress == nil || *updated.IpAddress != addr {
					updated.IpAddress = &addr
					changed = true
				}
			}
		}
		if !lockedFields["mac_address"] && incoming.MACAddress != "" {
			if hw, err := net.ParseMAC(incoming.MACAddress); err == nil {
				if len(updated.MacAddress) == 0 || !strings.EqualFold(updated.MacAddress.String(), hw.String()) {
					updated.MacAddress = hw
					changed = true
				}
			}
		}
		if !lockedFields["manufacturer"] && incoming.Manufacturer != "" && (!updated.Manufacturer.Valid || updated.Manufacturer.String != incoming.Manufacturer) {
			updated.Manufacturer.String = incoming.Manufacturer
			updated.Manufacturer.Valid = true
			changed = true
		}
		if !lockedFields["model"] && incoming.Model != "" && (!updated.Model.Valid || updated.Model.String != incoming.Model) {
			updated.Model.String = incoming.Model
			updated.Model.Valid = true
			changed = true
		}
		if !lockedFields["serial_number"] && incoming.SerialNumber != "" && (!updated.SerialNumber.Valid || updated.SerialNumber.String != incoming.SerialNumber) {
			updated.SerialNumber.String = incoming.SerialNumber
			updated.SerialNumber.Valid = true
			changed = true
		}
		if !lockedFields["device_type"] && incoming.DeviceType != "" && incoming.DeviceType != "unknown" && incoming.DeviceType != updated.DeviceType {
			updated.DeviceType = incoming.DeviceType
			changed = true
		}

		if changed {
			meta["source_confidence_score"] = incomingScore
		}
	}

	// Additive raw payload metadata merge
	if len(incoming.RawPayload) > 0 {
		providerNamespace := sourceType
		if providerNamespace != "" {
			meta[providerNamespace] = incoming.RawPayload
			changed = true
		}
	}

	if changed {
		metaBytes, _ := json.Marshal(meta)
		updated.Metadata = metaBytes
	}

	return &updated, changed
}

func extractUserLockedFields(metadata map[string]interface{}) map[string]bool {
	locked := make(map[string]bool)
	if fields, ok := metadata["user_locked_fields"].([]interface{}); ok {
		for _, f := range fields {
			if s, isStr := f.(string); isStr {
				locked[s] = true
			}
		}
	}
	return locked
}

func extractFieldScore(metadata map[string]interface{}, key string) int {
	if val, ok := metadata[key]; ok {
		switch v := val.(type) {
		case int:
			return v
		case float64:
			return int(v)
		}
	}
	return 0
}
