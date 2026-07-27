// Package dto defines data transfer objects and validation logic for the Topology Engine.
package dto

import (
	"errors"
	"strings"

	"github.com/google/uuid"
)

var (
	// ErrInvalidDeviceID indicates that a device ID is invalid or missing.
	ErrInvalidDeviceID = errors.New("source_device_id and target_device_id are required and must be distinct valid UUIDs")

	// ErrInvalidLinkType indicates that a link_type is unsupported.
	ErrInvalidLinkType = errors.New("invalid link_type: must be one of layer2_physical, layer3_routed, virtual_hypervisor, container_veth, manual")
)

// Supported link types
const (
	LinkTypeLayer2Physical   = "layer2_physical"
	LinkTypeLayer3Routed     = "layer3_routed"
	LinkTypeVirtualHypervisor = "virtual_hypervisor"
	LinkTypeContainerVeth    = "container_veth"
	LinkTypeManual           = "manual"
)

// CreateTopologyLinkRequest represents input payload for manual or automated topology link creation.
type CreateTopologyLinkRequest struct {
	SourceDeviceID    uuid.UUID              `json:"source_device_id"`
	TargetDeviceID    uuid.UUID              `json:"target_device_id"`
	SourceInterfaceID *uuid.UUID             `json:"source_interface_id,omitempty"`
	TargetInterfaceID *uuid.UUID             `json:"target_interface_id,omitempty"`
	LinkType          string                 `json:"link_type"`
	ConfidenceScore   *float64               `json:"confidence_score,omitempty"`
	DiscoveredBy      string                 `json:"discovered_by,omitempty"`
	Metadata          map[string]interface{} `json:"metadata,omitempty"`
}

// Normalize trims string fields and sets defaults.
func (r *CreateTopologyLinkRequest) Normalize() {
	r.LinkType = strings.ToLower(strings.TrimSpace(r.LinkType))
	if r.LinkType == "" {
		r.LinkType = LinkTypeManual
	}
	r.DiscoveredBy = strings.TrimSpace(r.DiscoveredBy)
	if r.DiscoveredBy == "" {
		r.DiscoveredBy = "manual"
	}
	if r.ConfidenceScore == nil {
		defaultScore := 1.00
		if r.LinkType == LinkTypeVirtualHypervisor {
			defaultScore = 0.95
		} else if r.LinkType == LinkTypeContainerVeth {
			defaultScore = 0.90
		} else if r.LinkType == LinkTypeLayer3Routed {
			defaultScore = 0.85
		}
		r.ConfidenceScore = &defaultScore
	}
	if r.Metadata == nil {
		r.Metadata = make(map[string]interface{})
	}
}

// Validate verifies input constraints without mutating state.
func (r *CreateTopologyLinkRequest) Validate() error {
	if r.SourceDeviceID == uuid.Nil || r.TargetDeviceID == uuid.Nil || r.SourceDeviceID == r.TargetDeviceID {
		return ErrInvalidDeviceID
	}
	switch r.LinkType {
	case LinkTypeLayer2Physical, LinkTypeLayer3Routed, LinkTypeVirtualHypervisor, LinkTypeContainerVeth, LinkTypeManual:
		return nil
	default:
		return ErrInvalidLinkType
	}
}

// TopologyLinkResponse represents output payload for a single link.
type TopologyLinkResponse struct {
	ID                uuid.UUID              `json:"id"`
	SourceDeviceID    uuid.UUID              `json:"source_device_id"`
	TargetDeviceID    uuid.UUID              `json:"target_device_id"`
	SourceInterfaceID *uuid.UUID             `json:"source_interface_id,omitempty"`
	TargetInterfaceID *uuid.UUID             `json:"target_interface_id,omitempty"`
	LinkType          string                 `json:"link_type"`
	ConfidenceScore   float64                `json:"confidence_score"`
	DiscoveredBy      string                 `json:"discovered_by"`
	Metadata          map[string]interface{} `json:"metadata"`
	CreatedAt         string                 `json:"created_at"`
	UpdatedAt         string                 `json:"updated_at"`
}

// DeviceNode represents a single vertex in the topology graph.
type DeviceNode struct {
	ID         uuid.UUID              `json:"id"`
	Hostname   string                 `json:"hostname"`
	IPAddress  string                 `json:"ip_address,omitempty"`
	MACAddress string                 `json:"mac_address,omitempty"`
	DeviceType string                 `json:"device_type"`
	Status     string                 `json:"status"`
	SubnetID   *uuid.UUID             `json:"subnet_id,omitempty"`
	Metadata   map[string]interface{} `json:"metadata,omitempty"`
}

// LinkEdge represents a directed or undirected edge in the topology graph.
type LinkEdge struct {
	ID                uuid.UUID `json:"id"`
	SourceDeviceID    uuid.UUID `json:"source_device_id"`
	TargetDeviceID    uuid.UUID `json:"target_device_id"`
	SourceInterfaceID *uuid.UUID `json:"source_interface_id,omitempty"`
	TargetInterfaceID *uuid.UUID `json:"target_interface_id,omitempty"`
	LinkType          string    `json:"link_type"`
	ConfidenceScore   float64   `json:"confidence_score"`
	DiscoveredBy      string    `json:"discovered_by"`
}

// TopologyGraphResponse represents the full graph visualization response.
type TopologyGraphResponse struct {
	Nodes    []DeviceNode           `json:"nodes"`
	Edges    []LinkEdge             `json:"edges"`
	Metadata map[string]interface{} `json:"metadata"`
}
