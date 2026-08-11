package collectors

import (
	"context"
	"net"
)

// NetDNSResolver wraps net.Resolver to implement the DNSResolver interface.
type NetDNSResolver struct {
	resolver *net.Resolver
}

// NewNetDNSResolver creates a DNSResolver using the stdlib net.Resolver.
func NewNetDNSResolver() *NetDNSResolver {
	return &NetDNSResolver{resolver: net.DefaultResolver}
}

// LookupAddr performs a reverse DNS lookup for the given address.
func (r *NetDNSResolver) LookupAddr(ctx context.Context, addr string) ([]string, error) {
	return r.resolver.LookupAddr(ctx, addr)
}
