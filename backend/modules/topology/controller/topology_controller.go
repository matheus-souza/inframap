// Package controller provides HTTP REST handlers for the Topology Engine.
package controller

import (
	"encoding/json"
	"errors"
	"net/http"

	"github.com/matheussouza/inframap/internal/platform/httputil"
	inventoryUC "github.com/matheussouza/inframap/modules/inventory/usecase"
	"github.com/matheussouza/inframap/modules/topology/dto"
	topoRepo "github.com/matheussouza/inframap/modules/topology/repository"
	"github.com/matheussouza/inframap/modules/topology/usecase"
)

// TopologyController exposes REST endpoints for network topology.
type TopologyController struct {
	useCase usecase.TopologyUseCase
}

// NewTopologyController constructs a TopologyController.
func NewTopologyController(useCase usecase.TopologyUseCase) *TopologyController {
	return &TopologyController{useCase: useCase}
}

// CreateLink handles POST /api/v1/topology/links
func (c *TopologyController) CreateLink(w http.ResponseWriter, r *http.Request) {
	var req dto.CreateTopologyLinkRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_JSON", "Invalid JSON payload", nil)
		return
	}

	resp, err := c.useCase.CreateLink(r.Context(), &req)
	if err != nil {
		if errors.Is(err, inventoryUC.ErrInvalidInput) {
			httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_INPUT", err.Error(), nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to create topology link", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusCreated, resp)
}

// GetLinkByID handles GET /api/v1/topology/links/{id}
func (c *TopologyController) GetLinkByID(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")
	resp, err := c.useCase.GetLinkByID(r.Context(), idStr)
	if err != nil {
		if errors.Is(err, inventoryUC.ErrInvalidUUID) {
			httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_UUID", "Invalid link UUID format", nil)
			return
		}
		if errors.Is(err, topoRepo.ErrLinkNotFound) {
			httputil.WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "Topology link not found", nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to fetch topology link", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, resp)
}

// ListLinks handles GET /api/v1/topology/links
func (c *TopologyController) ListLinks(w http.ResponseWriter, r *http.Request) {
	linkType := r.URL.Query().Get("link_type")
	sourceID := r.URL.Query().Get("source_device_id")
	targetID := r.URL.Query().Get("target_device_id")

	links, err := c.useCase.ListLinks(r.Context(), linkType, sourceID, targetID)
	if err != nil {
		if errors.Is(err, inventoryUC.ErrInvalidUUID) {
			httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_UUID", "Invalid device UUID format in query parameter", nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to list topology links", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, links)
}

// DeleteLink handles DELETE /api/v1/topology/links/{id}
func (c *TopologyController) DeleteLink(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")
	if err := c.useCase.DeleteLink(r.Context(), idStr); err != nil {
		if errors.Is(err, inventoryUC.ErrInvalidUUID) {
			httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_UUID", "Invalid link UUID format", nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to delete topology link", nil)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// GetGraph handles GET /api/v1/topology/graph
func (c *TopologyController) GetGraph(w http.ResponseWriter, r *http.Request) {
	graph, err := c.useCase.GetGraph(r.Context())
	if err != nil {
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to fetch topology graph", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, graph)
}
