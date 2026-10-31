// Package dto provides request and response structures for DNS record operations.
package dto

import (
	"net/netip"
	"strings"
	"time"

	"github.com/google/uuid"
)

// DNSRecord represents an operator-owned DNS record (A or CNAME) in InfraMap.
type DNSRecord struct {
	ID        uuid.UUID   `json:"id"`
	Name      string      `json:"name"`
	RecordType string     `json:"record_type"`
	Address   *netip.Addr `json:"address,omitempty"`
	Target    string      `json:"target,omitempty"`
	DeviceID  *uuid.UUID  `json:"device_id,omitempty"`
	Notes     string      `json:"notes,omitempty"`
	Status    string      `json:"status"` // ok, address_mismatch, orphan, dangling
	CreatedAt time.Time   `json:"created_at"`
	UpdatedAt time.Time   `json:"updated_at"`
}

// CreateDNSRecordRequest represents payload for creating a DNS record.
type CreateDNSRecordRequest struct {
	Name      string  `json:"name"`
	RecordType string `json:"record_type"`
	Address   string  `json:"address,omitempty"`
	Target    string  `json:"target,omitempty"`
	DeviceID  *string `json:"device_id,omitempty"`
	Notes     string  `json:"notes,omitempty"`
}

// Normalize applies defaults and trims whitespace before validation.
func (r *CreateDNSRecordRequest) Normalize() {
	r.Name = strings.ToLower(strings.TrimSuffix(strings.TrimSpace(r.Name), "."))
	r.RecordType = strings.ToUpper(strings.TrimSpace(r.RecordType))
	r.Address = strings.TrimSpace(r.Address)
	r.Target = strings.TrimSpace(r.Target)
	r.Notes = strings.TrimSpace(r.Notes)
}

// Validate validates CreateDNSRecordRequest attributes.
func (r *CreateDNSRecordRequest) Validate() []FieldError {
	var errs []FieldError
	if r.Name == "" {
		errs = append(errs, FieldError{Field: "name", Issue: "name is required"})
	}
	rt := strings.TrimSpace(r.RecordType)
	if rt != "A" && rt != "CNAME" {
		errs = append(errs, FieldError{Field: "record_type", Issue: "record_type must be A or CNAME"})
	}
	if rt == "A" && r.Address == "" {
		errs = append(errs, FieldError{Field: "address", Issue: "address is required for A records"})
	}
	if rt == "CNAME" && r.Target == "" {
		errs = append(errs, FieldError{Field: "target", Issue: "target is required for CNAME records"})
	}
	return errs
}

// UpdateDNSRecordRequest represents payload for updating an existing DNS record.
type UpdateDNSRecordRequest struct {
	Name      *string `json:"name,omitempty"`
	RecordType *string `json:"record_type,omitempty"`
	Address   *string `json:"address,omitempty"`
	Target    *string `json:"target,omitempty"`
	DeviceID  *string `json:"device_id,omitempty"`
	Notes     *string `json:"notes,omitempty"`
}

// Validate validates UpdateDNSRecordRequest attributes.
func (r *UpdateDNSRecordRequest) Validate() []FieldError {
	var errs []FieldError
	if r.RecordType != nil {
		rt := strings.TrimSpace(*r.RecordType)
		if rt != "A" && rt != "CNAME" {
			errs = append(errs, FieldError{Field: "record_type", Issue: "record_type must be A or CNAME"})
		}
	}
	return errs
}

// DNSRecordResponse represents a DNS record in API responses.
type DNSRecordResponse struct {
	ID        string    `json:"id"`
	Name      string    `json:"name"`
	RecordType string   `json:"record_type"`
	Address   string    `json:"address,omitempty"`
	Target    string    `json:"target,omitempty"`
	DeviceID  *string   `json:"device_id,omitempty"`
	Notes     string    `json:"notes,omitempty"`
	Status    string    `json:"status"` // ok, address_mismatch, orphan, dangling
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}

// FieldError represents a validation issue on a request attribute.
type FieldError struct {
	Field string `json:"field"`
	Issue string `json:"issue"`
}
