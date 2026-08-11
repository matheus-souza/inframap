package collectors

import "testing"

func TestParseARPLine(t *testing.T) {
	tests := []struct {
		name    string
		line    string
		wantOK  bool
		wantIP  string
		wantMAC string
		wantIF  string
	}{
		{
			name:    "macOS format",
			line:    "? (192.168.1.1) at aa:bb:cc:dd:ee:01 on en0 ifscope [ethernet]",
			wantOK:  true,
			wantIP:  "192.168.1.1",
			wantMAC: "aa:bb:cc:dd:ee:01",
			wantIF:  "en0",
		},
		{
			name:    "Linux format",
			line:    "? (10.0.0.1) at 10:22:33:44:55:66 [ether] on eth0",
			wantOK:  true,
			wantIP:  "10.0.0.1",
			wantMAC: "10:22:33:44:55:66",
			wantIF:  "eth0",
		},
		{
			name:   "incomplete entry",
			line:   "? (192.168.1.2) at (incomplete) on en0",
			wantOK: false,
		},
		{
			name:   "too few fields",
			line:   "foo bar",
			wantOK: false,
		},
		{
			name:   "no at keyword",
			line:   "? (10.0.0.1) xx aa:bb:cc:dd:ee:01 on eth0",
			wantOK: false,
		},
		{
			name:   "multicast MAC rejected",
			line:   "? (192.168.1.3) at 01:00:5e:00:00:01 on en0",
			wantOK: false,
		},
		{
			name:   "broadcast MAC rejected",
			line:   "? (192.168.1.4) at ff:ff:ff:ff:ff:ff on en0",
			wantOK: false,
		},
		{
			name:    "no interface field",
			line:    "? (192.168.1.5) at aa:bb:cc:dd:ee:02",
			wantOK:  true,
			wantIP:  "192.168.1.5",
			wantMAC: "aa:bb:cc:dd:ee:02",
			wantIF:  "",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			entry, ok := parseARPLine(tt.line)
			if ok != tt.wantOK {
				t.Fatalf("parseARPLine() ok = %v, want %v", ok, tt.wantOK)
			}
			if !ok {
				return
			}
			if entry.IPAddress != tt.wantIP {
				t.Errorf("IP = %q, want %q", entry.IPAddress, tt.wantIP)
			}
			if entry.MACAddress != tt.wantMAC {
				t.Errorf("MAC = %q, want %q", entry.MACAddress, tt.wantMAC)
			}
			if entry.Interface != tt.wantIF {
				t.Errorf("Interface = %q, want %q", entry.Interface, tt.wantIF)
			}
		})
	}
}

func TestIsValidUnicastMAC(t *testing.T) {
	tests := []struct {
		mac  string
		want bool
	}{
		{"aa:bb:cc:dd:ee:ff", true},
		{"00:00:00:00:00:00", false},
		{"ff:ff:ff:ff:ff:ff", false},
		{"01:00:5e:00:00:01", false},
		{"33:33:00:00:00:01", false},
		{"INVALID", false},
		{"", false},
	}

	for _, tt := range tests {
		t.Run(tt.mac, func(t *testing.T) {
			if got := isValidUnicastMAC(tt.mac); got != tt.want {
				t.Errorf("isValidUnicastMAC(%q) = %v, want %v", tt.mac, got, tt.want)
			}
		})
	}
}
