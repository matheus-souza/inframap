// Package repository provides PostgreSQL data access implementations for the Topology Engine.
package repository

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/topology/dto"
)

var (
	// ErrLinkNotFound indicates that a topology link was not found.
	ErrLinkNotFound = errors.New("topology link not found")
)

// TopologyRepository defines data access methods for managing topology links and network graph queries.
type TopologyRepository interface {
	CreateLink(ctx context.Context, req *dto.CreateTopologyLinkRequest) (*dto.TopologyLinkResponse, error)
	GetLinkByID(ctx context.Context, id uuid.UUID) (*dto.TopologyLinkResponse, error)
	ListLinks(ctx context.Context, linkType string, sourceDeviceID, targetDeviceID *uuid.UUID) ([]*dto.TopologyLinkResponse, error)
	DeleteLink(ctx context.Context, id uuid.UUID) error
	GetGraphData(ctx context.Context) (*dto.TopologyGraphResponse, error)
}

// PgTopologyRepository implements TopologyRepository using sqlc-generated db.Queries.
type PgTopologyRepository struct {
	queries *db.Queries
}

// NewPgTopologyRepository constructs a new PgTopologyRepository.
func NewPgTopologyRepository(queries *db.Queries) *PgTopologyRepository {
	return &PgTopologyRepository{queries: queries}
}

// CreateLink inserts a new topology link in PostgreSQL.
func (r *PgTopologyRepository) CreateLink(ctx context.Context, req *dto.CreateTopologyLinkRequest) (*dto.TopologyLinkResponse, error) {
	linkID := uuid.New()
	metaBytes, err := json.Marshal(req.Metadata)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal link metadata: %w", err)
	}

	confidence := 1.00
	if req.ConfidenceScore != nil {
		confidence = *req.ConfidenceScore
	}
	confidenceNum := pgtype.Numeric{}
	if err := confidenceNum.Scan(fmt.Sprintf("%.2f", confidence)); err != nil {
		return nil, fmt.Errorf("failed to scan confidence score: %w", err)
	}

	var sourceIf, targetIf pgtype.UUID
	if req.SourceInterfaceID != nil {
		sourceIf = pgtype.UUID{Bytes: *req.SourceInterfaceID, Valid: true}
	}
	if req.TargetInterfaceID != nil {
		targetIf = pgtype.UUID{Bytes: *req.TargetInterfaceID, Valid: true}
	}

	row, err := r.queries.CreateTopologyLink(ctx, db.CreateTopologyLinkParams{
		ID:                linkID,
		SourceDeviceID:    req.SourceDeviceID,
		TargetDeviceID:    req.TargetDeviceID,
		SourceInterfaceID: sourceIf,
		TargetInterfaceID: targetIf,
		LinkType:          req.LinkType,
		ConfidenceScore:   confidenceNum,
		DiscoveredBy:      req.DiscoveredBy,
		Metadata:          metaBytes,
	})
	if err != nil {
		return nil, fmt.Errorf("failed to insert topology link: %w", err)
	}

	return mapRowToLinkResponse(row), nil
}

// GetLinkByID fetches a topology link by UUID.
func (r *PgTopologyRepository) GetLinkByID(ctx context.Context, id uuid.UUID) (*dto.TopologyLinkResponse, error) {
	row, err := r.queries.GetTopologyLinkByID(ctx, id)
	if err != nil {
		return nil, ErrLinkNotFound
	}
	return mapRowToLinkResponse(row), nil
}

// ListLinks lists topology links filtering by type or device IDs.
func (r *PgTopologyRepository) ListLinks(ctx context.Context, linkType string, sourceDeviceID, targetDeviceID *uuid.UUID) ([]*dto.TopologyLinkResponse, error) {
	var lt pgtype.Text
	if linkType != "" {
		lt = pgtype.Text{String: linkType, Valid: true}
	}
	var srcID, tgtID pgtype.UUID
	if sourceDeviceID != nil {
		srcID = pgtype.UUID{Bytes: *sourceDeviceID, Valid: true}
	}
	if targetDeviceID != nil {
		tgtID = pgtype.UUID{Bytes: *targetDeviceID, Valid: true}
	}

	rows, err := r.queries.ListTopologyLinks(ctx, db.ListTopologyLinksParams{
		LinkType:       lt,
		SourceDeviceID: srcID,
		TargetDeviceID: tgtID,
	})
	if err != nil {
		return nil, fmt.Errorf("failed to list topology links: %w", err)
	}

	result := make([]*dto.TopologyLinkResponse, 0, len(rows))
	for i := range rows {
		result = append(result, mapRowToLinkResponse(rows[i]))
	}
	return result, nil
}

// DeleteLink removes a topology link by UUID.
func (r *PgTopologyRepository) DeleteLink(ctx context.Context, id uuid.UUID) error {
	return r.queries.DeleteTopologyLink(ctx, id)
}

// GetGraphData builds the complete topology graph response.
func (r *PgTopologyRepository) GetGraphData(ctx context.Context) (*dto.TopologyGraphResponse, error) {
	rows, err := r.queries.ListAllActiveNodesAndLinks(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch active nodes and links: %w", err)
	}

	nodesMap := make(map[uuid.UUID]dto.DeviceNode)
	edgesMap := make(map[uuid.UUID]dto.LinkEdge)

	for _, row := range rows {
		if _, exists := nodesMap[row.DeviceID]; !exists {
			ipStr := ""
			if row.IpAddress != nil {
				ipStr = row.IpAddress.String()
			}
			macStr := ""
			if len(row.MacAddress) > 0 {
				macStr = row.MacAddress.String()
			}

			var nodeMeta map[string]interface{}
			if len(row.DeviceMetadata) > 0 {
				_ = json.Unmarshal(row.DeviceMetadata, &nodeMeta)
			}

			nodesMap[row.DeviceID] = dto.DeviceNode{
				ID:         row.DeviceID,
				Hostname:   row.Hostname,
				IPAddress:  ipStr,
				MACAddress: macStr,
				DeviceType: row.DeviceType,
				Status:     row.Status,
				Metadata:   nodeMeta,
			}
		}

		if row.LinkID.Valid {
			linkID := row.LinkID.Bytes
			if _, exists := edgesMap[linkID]; !exists {
				confFloat, _ := row.ConfidenceScore.Float64Value()
				var srcIf, tgtIf *uuid.UUID
				if row.SourceInterfaceID.Valid {
					id := uuid.UUID(row.SourceInterfaceID.Bytes)
					srcIf = &id
				}
				if row.TargetInterfaceID.Valid {
					id := uuid.UUID(row.TargetInterfaceID.Bytes)
					tgtIf = &id
				}

				edgesMap[linkID] = dto.LinkEdge{
					ID:                linkID,
					SourceDeviceID:    row.SourceDeviceID.Bytes,
					TargetDeviceID:    row.TargetDeviceID.Bytes,
					SourceInterfaceID: srcIf,
					TargetInterfaceID: tgtIf,
					LinkType:          row.LinkType.String,
					ConfidenceScore:   confFloat.Float64,
					DiscoveredBy:      row.DiscoveredBy.String,
				}
			}
		}
	}

	nodes := make([]dto.DeviceNode, 0, len(nodesMap))
	for _, n := range nodesMap {
		nodes = append(nodes, n)
	}
	edges := make([]dto.LinkEdge, 0, len(edgesMap))
	for _, e := range edgesMap {
		edges = append(edges, e)
	}

	graphMeta := map[string]interface{}{
		"total_nodes": len(nodes),
		"total_edges": len(edges),
		"generated_at": time.Now().Format(time.RFC3339),
	}

	return &dto.TopologyGraphResponse{
		Nodes:    nodes,
		Edges:    edges,
		Metadata: graphMeta,
	}, nil
}

func mapRowToLinkResponse(row db.TopologyLink) *dto.TopologyLinkResponse {
	confFloat, _ := row.ConfidenceScore.Float64Value()
	var srcIf, tgtIf *uuid.UUID
	if row.SourceInterfaceID.Valid {
		id := uuid.UUID(row.SourceInterfaceID.Bytes)
		srcIf = &id
	}
	if row.TargetInterfaceID.Valid {
		id := uuid.UUID(row.TargetInterfaceID.Bytes)
		tgtIf = &id
	}

	var meta map[string]interface{}
	if len(row.Metadata) > 0 {
		_ = json.Unmarshal(row.Metadata, &meta)
	}

	return &dto.TopologyLinkResponse{
		ID:                row.ID,
		SourceDeviceID:    row.SourceDeviceID,
		TargetDeviceID:    row.TargetDeviceID,
		SourceInterfaceID: srcIf,
		TargetInterfaceID: tgtIf,
		LinkType:          row.LinkType,
		ConfidenceScore:   confFloat.Float64,
		DiscoveredBy:      row.DiscoveredBy,
		Metadata:          meta,
		CreatedAt:         row.CreatedAt.Time.Format(time.RFC3339),
		UpdatedAt:         row.UpdatedAt.Time.Format(time.RFC3339),
	}
}
