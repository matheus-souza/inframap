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
	ListLinks(ctx context.Context, linkType string, sourceDeviceID, targetDeviceID *uuid.UUID, page, limit int32) ([]*dto.TopologyLinkResponse, error)
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

// ListLinks lists topology links filtering by type or device IDs with pagination.
func (r *PgTopologyRepository) ListLinks(ctx context.Context, linkType string, sourceDeviceID, targetDeviceID *uuid.UUID, page, limit int32) ([]*dto.TopologyLinkResponse, error) {
	if page < 1 {
		page = 1
	}
	if limit < 1 || limit > 1000 {
		limit = 100
	}
	offset := (page - 1) * limit

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
		Limit:          limit,
		Offset:         offset,
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

// DeleteLink removes a topology link by UUID and errors if 0 rows were deleted.
func (r *PgTopologyRepository) DeleteLink(ctx context.Context, id uuid.UUID) error {
	rowsAffected, err := r.queries.DeleteTopologyLink(ctx, id)
	if err != nil {
		return fmt.Errorf("failed to delete topology link: %w", err)
	}
	if rowsAffected == 0 {
		return ErrLinkNotFound
	}
	return nil
}

// GetGraphData builds the complete topology graph response using paginated queries.
func (r *PgTopologyRepository) GetGraphData(ctx context.Context) (*dto.TopologyGraphResponse, error) {
	var nodes []dto.DeviceNode
	var devPage int32 = 1
	var limit int32 = 1000

	// 1. Paginate active devices
	for {
		devRows, err := r.queries.ListActiveDevicesForGraph(ctx, db.ListActiveDevicesForGraphParams{
			Limit:  limit,
			Offset: (devPage - 1) * limit,
		})
		if err != nil {
			return nil, fmt.Errorf("failed to fetch active devices for graph: %w", err)
		}
		if len(devRows) == 0 {
			break
		}

		for _, d := range devRows {
			ipStr := ""
			if d.IpAddress != nil {
				ipStr = d.IpAddress.String()
			}
			macStr := ""
			if len(d.MacAddress) > 0 {
				macStr = d.MacAddress.String()
			}

			var nodeMeta map[string]interface{}
			if len(d.Metadata) > 0 {
				_ = json.Unmarshal(d.Metadata, &nodeMeta)
			}

			nodes = append(nodes, dto.DeviceNode{
				ID:         d.ID,
				Hostname:   d.Hostname,
				IPAddress:  ipStr,
				MACAddress: macStr,
				DeviceType: d.DeviceType,
				Status:     d.Status,
				Metadata:   nodeMeta,
			})
		}

		if len(devRows) < int(limit) {
			break
		}
		devPage++
	}

	// 2. Paginate topology links
	var edges []dto.LinkEdge
	var linkPage int32 = 1

	for {
		linkRows, err := r.queries.ListActiveTopologyLinksForGraph(ctx, db.ListActiveTopologyLinksForGraphParams{
			Limit:  limit,
			Offset: (linkPage - 1) * limit,
		})
		if err != nil {
			return nil, fmt.Errorf("failed to fetch active topology links for graph: %w", err)
		}
		if len(linkRows) == 0 {
			break
		}

		for _, l := range linkRows {
			confFloat, _ := l.ConfidenceScore.Float64Value()
			var srcIf, tgtIf *uuid.UUID
			if l.SourceInterfaceID.Valid {
				id := uuid.UUID(l.SourceInterfaceID.Bytes)
				srcIf = &id
			}
			if l.TargetInterfaceID.Valid {
				id := uuid.UUID(l.TargetInterfaceID.Bytes)
				tgtIf = &id
			}

			edges = append(edges, dto.LinkEdge{
				ID:                l.ID,
				SourceDeviceID:    l.SourceDeviceID,
				TargetDeviceID:    l.TargetDeviceID,
				SourceInterfaceID: srcIf,
				TargetInterfaceID: tgtIf,
				LinkType:          l.LinkType,
				ConfidenceScore:   confFloat.Float64,
				DiscoveredBy:      l.DiscoveredBy,
			})
		}

		if len(linkRows) < int(limit) {
			break
		}
		linkPage++
	}

	graphMeta := map[string]interface{}{
		"total_nodes":  len(nodes),
		"total_edges":  len(edges),
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
