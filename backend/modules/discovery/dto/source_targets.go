package dto

import (
	"encoding/json"
	"fmt"
	"net/netip"
	"strings"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/modules/discovery/collectors"
)

// Keys of the encrypted discovery source configuration that hold its targets (RFC-026 §1).
const (
	// ConfigKeyTargets holds the ordered list of targets a source scans.
	ConfigKeyTargets = "targets"

	// ConfigKeyCIDR is the single-target form used before targets existed. It is still
	// accepted on requests and read from stored configs, and every write drops it.
	ConfigKeyCIDR = "cidr"
)

const (
	// MaxSourceTargets is the largest number of targets one source may hold.
	MaxSourceTargets = 32

	// MaxSourceTargetAddresses caps the sum of addresses across all targets of a source: the
	// size of one /16, the per-target limit enforced by collectors.ParseTargetPrefix.
	MaxSourceTargetAddresses = 65536
)

// Error codes returned with 422 when a source's targets break a rule of RFC-026 §2.
const (
	ErrCodeTargetsRequired      = "error_targets_required"
	ErrCodeTargetInvalid        = "error_target_invalid"
	ErrCodeTargetsOverlap       = "error_targets_overlap"
	ErrCodeTargetsTooLarge      = "error_targets_too_large"
	ErrCodeTargetsTooMany       = "error_targets_too_many"
	ErrCodeTargetSubnetNotFound = "error_target_subnet_not_found"
)

// SourceTarget is one address block a discovery source scans. A target with SubnetID is
// linked to a registered Subnet and CIDR is a snapshot of the Subnet's block; without it,
// CIDR is a free-form target.
type SourceTarget struct {
	CIDR     string     `json:"cidr"`
	SubnetID *uuid.UUID `json:"subnet_id,omitempty"`
}

// SourceTargetResponse is a target of a discovery source returned to API clients.
// SubnetID is null for a free-form target.
type SourceTargetResponse struct {
	CIDR     string     `json:"cidr"`
	SubnetID *uuid.UUID `json:"subnet_id"`
}

// TargetValidationError reports a source target list that breaks a rule of RFC-026 §2.
// Code is one of the ErrCodeTarget* constants and is returned verbatim as the API error code.
type TargetValidationError struct {
	Code    string
	Field   string
	Message string
}

func (e *TargetValidationError) Error() string {
	if e.Message == "" {
		return e.Code
	}
	return e.Code + ": " + e.Message
}

func targetError(code, field, format string, args ...interface{}) *TargetValidationError {
	return &TargetValidationError{Code: code, Field: field, Message: fmt.Sprintf(format, args...)}
}

func targetField(index int, key string) string {
	return fmt.Sprintf("config.%s[%d].%s", ConfigKeyTargets, index, key)
}

// NewTargetSubnetNotFoundError reports that the target at index links to a Subnet that does
// not exist or was soft-deleted.
func NewTargetSubnetNotFoundError(index int, subnetID string) *TargetValidationError {
	return targetError(ErrCodeTargetSubnetNotFound, targetField(index, "subnet_id"), "subnet %q not found", subnetID)
}

// ParseSourceTargets reads the targets carried by a source configuration. config.targets
// wins; otherwise a legacy config.cidr becomes a single target. present is false when the
// configuration carries neither key, so an update can keep the stored targets.
func ParseSourceTargets(config map[string]interface{}) (targets []SourceTarget, present bool, err error) {
	if raw, ok := config[ConfigKeyTargets]; ok {
		targets, err = decodeSourceTargets(raw)
		return targets, true, err
	}

	raw, ok := config[ConfigKeyCIDR]
	if !ok {
		return nil, false, nil
	}
	if raw == nil {
		return []SourceTarget{}, true, nil
	}
	cidr, isString := raw.(string)
	if !isString {
		return nil, true, targetError(ErrCodeTargetInvalid, "config."+ConfigKeyCIDR, "cidr must be a string")
	}
	cidr = strings.TrimSpace(cidr)
	if cidr == "" {
		return []SourceTarget{}, true, nil
	}
	return []SourceTarget{{CIDR: cidr}}, true, nil
}

func decodeSourceTargets(raw interface{}) ([]SourceTarget, error) {
	if raw == nil {
		return []SourceTarget{}, nil
	}

	// A round-trip through JSON accepts both a decoded request body and a typed slice.
	data, err := json.Marshal(raw)
	if err != nil {
		return nil, targetError(ErrCodeTargetInvalid, "config."+ConfigKeyTargets, "targets must be a list of objects")
	}
	var entries []struct {
		CIDR     string `json:"cidr"`
		SubnetID string `json:"subnet_id"`
	}
	if err := json.Unmarshal(data, &entries); err != nil {
		return nil, targetError(ErrCodeTargetInvalid, "config."+ConfigKeyTargets, "targets must be a list of {cidr, subnet_id} objects")
	}

	targets := make([]SourceTarget, len(entries))
	for i, entry := range entries {
		targets[i].CIDR = strings.TrimSpace(entry.CIDR)
		subnetID := strings.TrimSpace(entry.SubnetID)
		if subnetID == "" {
			continue
		}
		id, parseErr := uuid.Parse(subnetID)
		if parseErr != nil {
			return nil, NewTargetSubnetNotFoundError(i, subnetID)
		}
		targets[i].SubnetID = &id
	}
	return targets, nil
}

// ValidateSourceTargets applies the target rules of RFC-026 §2 that need no lookup.
// requiresCIDR is collectors.RequiresCIDR of the plan: a plan made only of providers is
// valid with no targets. Subnet existence is checked by the caller.
func ValidateSourceTargets(targets []SourceTarget, requiresCIDR bool) error {
	if len(targets) == 0 {
		if requiresCIDR {
			return targetError(ErrCodeTargetsRequired, "config."+ConfigKeyTargets, "the discovery plan contains a network sweep and needs at least one target")
		}
		return nil
	}
	if len(targets) > MaxSourceTargets {
		return targetError(ErrCodeTargetsTooMany, "config."+ConfigKeyTargets, "a source holds at most %d targets, got %d", MaxSourceTargets, len(targets))
	}

	prefixes := make([]netip.Prefix, 0, len(targets))
	addresses := 0
	for i, target := range targets {
		prefix, err := collectors.ParseTargetPrefix(target.CIDR)
		if err != nil {
			return targetError(ErrCodeTargetInvalid, targetField(i, "cidr"), "%v", err)
		}
		prefix = prefix.Masked()

		// Overlaps are rejected rather than merged so host counts and per-target run
		// history stay unambiguous.
		for j, previous := range prefixes {
			if previous.Overlaps(prefix) {
				return targetError(ErrCodeTargetsOverlap, targetField(i, "cidr"), "%s overlaps target %d (%s)", target.CIDR, j, targets[j].CIDR)
			}
		}
		prefixes = append(prefixes, prefix)

		// ParseTargetPrefix bounds every target to 16 host bits, so the shift cannot overflow.
		addresses += 1 << (prefix.Addr().BitLen() - prefix.Bits())
	}

	if addresses > MaxSourceTargetAddresses {
		return targetError(ErrCodeTargetsTooLarge, "config."+ConfigKeyTargets, "targets cover %d addresses, the limit is %d", addresses, MaxSourceTargetAddresses)
	}
	return nil
}

// WithSourceTargets returns a copy of config that stores targets under config.targets and
// drops the legacy config.cidr, migrating the payload on its next save. The input map is not
// modified: request normalization may share it with a collector's config.
func WithSourceTargets(config map[string]interface{}, targets []SourceTarget) map[string]interface{} {
	out := make(map[string]interface{}, len(config)+1)
	for key, value := range config {
		if key == ConfigKeyCIDR {
			continue
		}
		out[key] = value
	}
	stored := make([]SourceTarget, len(targets))
	copy(stored, targets)
	out[ConfigKeyTargets] = stored
	return out
}

// NewSourceTargetResponses maps stored targets to their API representation.
func NewSourceTargetResponses(targets []SourceTarget) []SourceTargetResponse {
	out := make([]SourceTargetResponse, len(targets))
	for i, target := range targets {
		out[i] = SourceTargetResponse(target)
	}
	return out
}

// SourceTargetsFromResponses maps the targets of a source response back to the stored form.
func SourceTargetsFromResponses(targets []SourceTargetResponse) []SourceTarget {
	out := make([]SourceTarget, len(targets))
	for i, target := range targets {
		out[i] = SourceTarget(target)
	}
	return out
}
