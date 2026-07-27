// Package topology_test tests route registration for the Topology module.
package topology_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/topology"
	topoctrl "github.com/matheussouza/inframap/modules/topology/controller"
	"github.com/matheussouza/inframap/modules/topology/dto"
)

type dummyUseCase struct{}

func (d *dummyUseCase) CreateLink(_ context.Context, _ *dto.CreateTopologyLinkRequest) (*dto.TopologyLinkResponse, error) {
	return &dto.TopologyLinkResponse{ID: uuid.New()}, nil
}

func (d *dummyUseCase) GetLinkByID(_ context.Context, _ string) (*dto.TopologyLinkResponse, error) {
	return &dto.TopologyLinkResponse{ID: uuid.New()}, nil
}

func (d *dummyUseCase) ListLinks(_ context.Context, _, _, _ string, _, _ int32) ([]*dto.TopologyLinkResponse, error) {
	return []*dto.TopologyLinkResponse{}, nil
}

func (d *dummyUseCase) DeleteLink(_ context.Context, _ string) error {
	return nil
}

func (d *dummyUseCase) GetGraph(_ context.Context) (*dto.TopologyGraphResponse, error) {
	return &dto.TopologyGraphResponse{Nodes: []dto.DeviceNode{}, Edges: []dto.LinkEdge{}}, nil
}

func (d *dummyUseCase) HandleDeviceEvent(_ context.Context, _ eventbus.DomainEvent) error {
	return nil
}

func TestRegisterRoutes(t *testing.T) {
	ctrl := topoctrl.NewTopologyController(&dummyUseCase{})
	mux := http.NewServeMux()

	topology.RegisterRoutes(mux, ctrl)

	ts := httptest.NewServer(mux)
	defer ts.Close()

	req, _ := http.NewRequest(http.MethodGet, ts.URL+"/api/v1/topology/graph", nil)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("failed to make request: %v", err)
	}
	defer func() {
		_ = resp.Body.Close()
	}()

	if resp.StatusCode != http.StatusOK {
		t.Errorf("expected 200 OK, got %d", resp.StatusCode)
	}
}
