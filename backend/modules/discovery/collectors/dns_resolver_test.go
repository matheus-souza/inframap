package collectors_test

import (
	"context"
	"testing"

	"github.com/matheussouza/inframap/modules/discovery/collectors"
)

func TestNetDNSResolver_LookupAddr(t *testing.T) {
	resolver := collectors.NewNetDNSResolver()
	if resolver == nil {
		t.Fatal("expected non-nil resolver")
	}

	// Lookup localhost — should resolve on any platform
	names, err := resolver.LookupAddr(context.Background(), "127.0.0.1")
	if err != nil {
		t.Skipf("DNS resolution not available in test environment: %v", err)
	}
	if len(names) == 0 {
		t.Skip("no PTR records for 127.0.0.1 in this environment")
	}
}
