package usecase

import (
	"context"
	"errors"
	"fmt"
	"net/netip"
	"sort"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/inventory/dto"
)

// MaxMappedAddresses bounds the address map to a /22 of IPv4 (RFC-027 §2).
const MaxMappedAddresses = 1024

// Address states of a subnet address map.
const (
	AddressFree     = "free"
	AddressInUse    = "in_use"
	AddressReserved = "reserved"
	AddressConflict = "conflict"
)

// ErrSubnetTooLargeToMap reports a subnet with more host addresses than an address map holds,
// or an IPv6 subnet, which is not mapped.
var ErrSubnetTooLargeToMap = errors.New("subnet is too large to map")

// GetSubnetAddressMap lists every host address of a subnet with the live devices holding it.
func (uc *DefaultInventoryUseCase) GetSubnetAddressMap(ctx context.Context, idStr string) (*dto.SubnetAddressMapResponse, error) {
	subnetID, err := uuid.Parse(idStr)
	if err != nil {
		return nil, ErrInvalidUUID
	}
	subnet, err := uc.repo.GetSubnetByID(ctx, subnetID)
	if err != nil {
		return nil, err
	}
	devices, _, err := uc.repo.ListDevices(ctx, "", "", 10000, 0, false)
	if err != nil {
		return nil, err
	}

	resp, err := BuildSubnetAddressMap(subnet.Cidr, subnet.GatewayIp, devices)
	if err != nil {
		return nil, err
	}
	resp.SubnetID = subnet.ID.String()
	return resp, nil
}

// BuildSubnetAddressMap places the live devices of devices on the host addresses of prefix.
// An IPv4 prefix up to /30 leaves out its network and broadcast addresses.
func BuildSubnetAddressMap(prefix netip.Prefix, gateway *netip.Addr, devices []db.Device) (*dto.SubnetAddressMapResponse, error) {
	prefix = prefix.Masked()
	if !prefix.Addr().Is4() {
		return nil, fmt.Errorf("%w: IPv6 subnets are not mapped", ErrSubnetTooLargeToMap)
	}
	hostBits := 32 - prefix.Bits()
	if hostBits > 10 {
		return nil, fmt.Errorf("%w: %s holds more than %d addresses", ErrSubnetTooLargeToMap, prefix, MaxMappedAddresses)
	}

	holders := make(map[netip.Addr][]dto.SubnetAddressDevice)
	for i := range devices {
		dev := &devices[i]
		if dev.DeletedAt.Valid || dev.IpAddress == nil || !prefix.Contains(*dev.IpAddress) {
			continue
		}
		holders[*dev.IpAddress] = append(holders[*dev.IpAddress], dto.SubnetAddressDevice{
			ID:           dev.ID.String(),
			Hostname:     dev.Hostname,
			DeviceType:   dev.DeviceType,
			IPAssignment: dev.IpAssignment,
		})
	}

	resp := &dto.SubnetAddressMapResponse{CIDR: prefix.String(), Addresses: []dto.SubnetAddress{}}
	first, last := hostRange(prefix)
	for addr := first; ; addr = addr.Next() {
		held := holders[addr]
		sort.Slice(held, func(i, j int) bool { return held[i].Hostname < held[j].Hostname })
		entry := dto.SubnetAddress{
			Address:   addr.String(),
			State:     addressState(held),
			IsGateway: gateway != nil && *gateway == addr,
			Devices:   append([]dto.SubnetAddressDevice{}, held...),
		}
		resp.Addresses = append(resp.Addresses, entry)
		countState(resp, entry.State)
		if addr == last {
			break
		}
	}
	resp.Total = len(resp.Addresses)
	return resp, nil
}

// hostRange returns the first and last host addresses of an IPv4 prefix.
func hostRange(prefix netip.Prefix) (netip.Addr, netip.Addr) {
	first := prefix.Addr()
	raw := first.As4()
	hostMask := uint32(1)<<(32-prefix.Bits()) - 1
	n := uint32(raw[0])<<24 | uint32(raw[1])<<16 | uint32(raw[2])<<8 | uint32(raw[3])
	n |= hostMask
	last := netip.AddrFrom4([4]byte{byte(n >> 24), byte(n >> 16), byte(n >> 8), byte(n)})
	if prefix.Bits() <= 30 {
		return first.Next(), last.Prev()
	}
	return first, last
}

func addressState(held []dto.SubnetAddressDevice) string {
	switch {
	case len(held) == 0:
		return AddressFree
	case len(held) > 1:
		return AddressConflict
	case held[0].IPAssignment == "reserved" || held[0].IPAssignment == "static":
		return AddressReserved
	default:
		return AddressInUse
	}
}

func countState(resp *dto.SubnetAddressMapResponse, state string) {
	switch state {
	case AddressFree:
		resp.Free++
	case AddressInUse:
		resp.InUse++
	case AddressReserved:
		resp.Reserved++
	case AddressConflict:
		resp.Conflict++
	}
}
