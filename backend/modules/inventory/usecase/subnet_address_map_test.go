package usecase_test

import (
	"context"
	"errors"
	"net/netip"
	"testing"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/inventory/dto"
	"github.com/matheussouza/inframap/modules/inventory/repository"
	"github.com/matheussouza/inframap/modules/inventory/usecase"
)

func device(hostname, ip, assignment string) db.Device {
	addr := netip.MustParseAddr(ip)
	return db.Device{ID: uuid.New(), Hostname: hostname, IpAddress: &addr, IpAssignment: assignment, DeviceType: "host"}
}

func entry(t *testing.T, resp *dto.SubnetAddressMapResponse, address string) dto.SubnetAddress {
	t.Helper()
	for _, a := range resp.Addresses {
		if a.Address == address {
			return a
		}
	}
	t.Fatalf("address %s is not in the map", address)
	return dto.SubnetAddress{}
}

func TestBuildSubnetAddressMap(t *testing.T) {
	prefix := netip.MustParsePrefix("192.0.2.0/24")
	gateway := netip.MustParseAddr("192.0.2.1")
	deleted := device("old-nas", "192.0.2.30", "reserved")
	deleted.DeletedAt = pgtype.Timestamptz{Valid: true}
	devices := []db.Device{
		device("router", "192.0.2.1", "static"),
		device("laptop", "192.0.2.10", "dynamic"),
		device("nas", "192.0.2.20", "reserved"),
		device("tv", "192.0.2.40", "unknown"),
		device("phone", "192.0.2.40", "dynamic"),
		deleted,
		device("elsewhere", "198.51.100.7", "static"),
	}

	resp, err := usecase.BuildSubnetAddressMap(prefix, &gateway, devices)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	t.Run("lists every host address and leaves out network and broadcast", func(t *testing.T) {
		if resp.Total != 254 || resp.Addresses[0].Address != "192.0.2.1" || resp.Addresses[253].Address != "192.0.2.254" {
			t.Fatalf("total %d from %s to %s, want 254 from .1 to .254", resp.Total, resp.Addresses[0].Address, resp.Addresses[resp.Total-1].Address)
		}
	})

	t.Run("states follow who holds each address", func(t *testing.T) {
		want := map[string]string{
			"192.0.2.1":  usecase.AddressReserved, // static counts as reserved
			"192.0.2.10": usecase.AddressInUse,
			"192.0.2.20": usecase.AddressReserved,
			"192.0.2.40": usecase.AddressConflict,
			"192.0.2.30": usecase.AddressFree, // the device holding it was deleted
			"192.0.2.99": usecase.AddressFree,
		}
		for address, state := range want {
			if got := entry(t, resp, address).State; got != state {
				t.Errorf("%s state = %s, want %s", address, got, state)
			}
		}
		if got := entry(t, resp, "192.0.2.40").Devices; len(got) != 2 || got[0].Hostname != "phone" {
			t.Errorf("conflict holders = %+v, want phone and tv sorted by name", got)
		}
	})

	t.Run("counts add up and the gateway is flagged", func(t *testing.T) {
		if resp.Reserved != 2 || resp.InUse != 1 || resp.Conflict != 1 || resp.Free != 250 {
			t.Errorf("counts reserved=%d in_use=%d conflict=%d free=%d", resp.Reserved, resp.InUse, resp.Conflict, resp.Free)
		}
		if !entry(t, resp, "192.0.2.1").IsGateway || entry(t, resp, "192.0.2.10").IsGateway {
			t.Error("only the subnet's gateway is flagged")
		}
	})
}

func TestBuildSubnetAddressMap_Bounds(t *testing.T) {
	t.Run("a /31 and a /32 keep every address", func(t *testing.T) {
		for prefix, total := range map[string]int{"192.0.2.0/31": 2, "192.0.2.5/32": 1} {
			resp, err := usecase.BuildSubnetAddressMap(netip.MustParsePrefix(prefix), nil, nil)
			if err != nil || resp.Total != total {
				t.Errorf("%s: total %v (%v), want %d", prefix, resp, err, total)
			}
		}
	})

	t.Run("a /22 is mapped and anything larger is refused", func(t *testing.T) {
		if resp, err := usecase.BuildSubnetAddressMap(netip.MustParsePrefix("10.0.0.0/22"), nil, nil); err != nil || resp.Total != 1022 {
			t.Fatalf("/22: %v, want 1022 addresses", err)
		}
		if _, err := usecase.BuildSubnetAddressMap(netip.MustParsePrefix("10.0.0.0/21"), nil, nil); !errors.Is(err, usecase.ErrSubnetTooLargeToMap) {
			t.Errorf("/21: err = %v, want ErrSubnetTooLargeToMap", err)
		}
	})

	t.Run("an IPv6 subnet is not mapped", func(t *testing.T) {
		if _, err := usecase.BuildSubnetAddressMap(netip.MustParsePrefix("2001:db8::/120"), nil, nil); !errors.Is(err, usecase.ErrSubnetTooLargeToMap) {
			t.Errorf("err = %v, want ErrSubnetTooLargeToMap", err)
		}
	})
}

func TestInventoryUseCase_GetSubnetAddressMap(t *testing.T) {
	repo := newMockInventoryRepository()
	uc := usecase.NewDefaultInventoryUseCase(repo, nil, nil)
	ctx := context.Background()

	subnet, err := uc.CreateSubnet(ctx, dto.CreateSubnetRequest{Name: "Home LAN", CIDR: "192.0.2.0/24", GatewayIP: "192.0.2.1"})
	if err != nil {
		t.Fatalf("create subnet: %v", err)
	}
	nas, _ := uc.CreateDevice(ctx, dto.CreateDeviceRequest{Hostname: "nas", DeviceType: "server", IPAddress: "192.0.2.20"})
	reserved := "reserved"
	if _, err := uc.UpdateDevice(ctx, nas.ID, dto.UpdateDeviceRequest{IPAssignment: &reserved}); err != nil {
		t.Fatalf("reserve nas: %v", err)
	}

	t.Run("maps the subnet's addresses", func(t *testing.T) {
		resp, err := uc.GetSubnetAddressMap(ctx, subnet.ID)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if resp.SubnetID != subnet.ID || resp.Reserved != 1 || entry(t, resp, "192.0.2.20").Devices[0].Hostname != "nas" {
			t.Errorf("map = %+v, want the reserved nas in subnet %s", resp, subnet.ID)
		}
		if !entry(t, resp, "192.0.2.1").IsGateway {
			t.Error("the gateway is flagged")
		}
	})

	t.Run("rejects a malformed id", func(t *testing.T) {
		if _, err := uc.GetSubnetAddressMap(ctx, "not-a-uuid"); !errors.Is(err, usecase.ErrInvalidUUID) {
			t.Errorf("err = %v, want ErrInvalidUUID", err)
		}
	})

	t.Run("reports a missing subnet", func(t *testing.T) {
		if _, err := uc.GetSubnetAddressMap(ctx, uuid.NewString()); !errors.Is(err, repository.ErrSubnetNotFound) {
			t.Errorf("err = %v, want ErrSubnetNotFound", err)
		}
	})
}
