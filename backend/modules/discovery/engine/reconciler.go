package engine

import (
	"encoding/json"
	"net"
	"net/netip"
	"reflect"
	"strings"

	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/discovery/dto"
)

// SourceConfidenceMatrix maps discovery collector types to their default confidence score.
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

// Reconcile evaluates field updates using per-field confidence scores and user-lock immunity.
func (r *DefaultFieldReconciler) Reconcile(existing *db.Device, incoming *dto.NormalizedDeviceDTO, sourceType string) (*db.Device, bool) {
	if existing == nil || incoming == nil {
		return existing, false
	}
	changed := false
	updated := *existing

	var meta map[string]interface{}
	if len(existing.Metadata) > 0 {
		_ = json.Unmarshal(existing.Metadata, &meta)
	}
	if meta == nil {
		meta = make(map[string]interface{})
	}

	fieldScores := extractFieldScores(meta)
	lockedFields := extractUserLockedFields(meta)
	incomingScore, exists := SourceConfidenceMatrix[sourceType]
	if !exists {
		incomingScore = 20
	}

	canUpdate := func(fieldName string) bool {
		if lockedFields[fieldName] {
			return false
		}
		currentScore := fieldScores[fieldName]
		return incomingScore >= currentScore
	}

	if canUpdate("hostname") && incoming.Hostname != "" && incoming.Hostname != updated.Hostname {
		updated.Hostname = incoming.Hostname
		fieldScores["hostname"] = incomingScore
		changed = true
	}

	if canUpdate("ip_address") && incoming.IPAddress != "" {
		if addr, err := netip.ParseAddr(incoming.IPAddress); err == nil {
			if updated.IpAddress == nil || *updated.IpAddress != addr {
				updated.IpAddress = &addr
				fieldScores["ip_address"] = incomingScore
				changed = true
			}
		}
	}

	if canUpdate("mac_address") && incoming.MACAddress != "" {
		if hw, err := net.ParseMAC(incoming.MACAddress); err == nil {
			if len(updated.MacAddress) == 0 || !strings.EqualFold(updated.MacAddress.String(), hw.String()) {
				updated.MacAddress = hw
				fieldScores["mac_address"] = incomingScore
				changed = true
			}
		}
	}

	if canUpdate("manufacturer") && incoming.Manufacturer != "" && (!updated.Manufacturer.Valid || updated.Manufacturer.String != incoming.Manufacturer) {
		updated.Manufacturer.String = incoming.Manufacturer
		updated.Manufacturer.Valid = true
		fieldScores["manufacturer"] = incomingScore
		changed = true
	}

	if canUpdate("model") && incoming.Model != "" && (!updated.Model.Valid || updated.Model.String != incoming.Model) {
		updated.Model.String = incoming.Model
		updated.Model.Valid = true
		fieldScores["model"] = incomingScore
		changed = true
	}

	if canUpdate("serial_number") && incoming.SerialNumber != "" && (!updated.SerialNumber.Valid || updated.SerialNumber.String != incoming.SerialNumber) {
		updated.SerialNumber.String = incoming.SerialNumber
		updated.SerialNumber.Valid = true
		fieldScores["serial_number"] = incomingScore
		changed = true
	}

	if canUpdate("device_type") && incoming.DeviceType != "" && incoming.DeviceType != "unknown" && incoming.DeviceType != updated.DeviceType {
		updated.DeviceType = incoming.DeviceType
		fieldScores["device_type"] = incomingScore
		changed = true
	}

	meta["field_confidence_scores"] = fieldScores

	// Deep-merge additive raw payload metadata only when keys actually differ
	if len(incoming.RawPayload) > 0 && sourceType != "" {
		existingNs, _ := meta[sourceType].(map[string]interface{})
		if existingNs == nil {
			existingNs = make(map[string]interface{})
		}
		nsChanged := false
		for k, v := range incoming.RawPayload {
			if oldV, exists := existingNs[k]; !exists || !reflect.DeepEqual(oldV, v) {
				existingNs[k] = v
				nsChanged = true
			}
		}
		if nsChanged {
			meta[sourceType] = existingNs
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

func extractFieldScores(metadata map[string]interface{}) map[string]int {
	scores := make(map[string]int)
	if rawScores, ok := metadata["field_confidence_scores"].(map[string]interface{}); ok {
		for k, val := range rawScores {
			switch v := val.(type) {
			case int:
				scores[k] = v
			case float64:
				scores[k] = int(v)
			}
		}
	}
	return scores
}
