package collectors

import (
	"bufio"
	"bytes"
	"context"
	"fmt"
	"os/exec"
	"strings"
)

// ProcNetARPReader reads the ARP table from /proc/net/arp (Linux)
// or falls back to parsing `arp -an` output on other platforms.
type ProcNetARPReader struct {
	readFile func(name string) ([]byte, error)
	arpPath  string
}

// NewProcNetARPReader creates an ARPTableReader that works on Linux and macOS.
func NewProcNetARPReader(readFile func(string) ([]byte, error)) *ProcNetARPReader {
	arpPath, _ := exec.LookPath("arp")
	return &ProcNetARPReader{readFile: readFile, arpPath: arpPath}
}

// ReadEntries returns ARP entries from the OS. On Linux, reads /proc/net/arp.
// On other platforms, falls back to `arp -an`.
func (r *ProcNetARPReader) ReadEntries(ctx context.Context) ([]ARPEntry, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}

	if r.readFile != nil {
		data, err := r.readFile("/proc/net/arp")
		if err == nil {
			return parseProcNetARP(data), nil
		}
	}

	if r.arpPath != "" {
		return r.readARPCommand(ctx)
	}

	return nil, nil
}

// parseProcNetARP parses /proc/net/arp format:
// IP address       HW type     Flags       HW address            Mask     Device
// 192.168.1.1      0x1         0x2         aa:bb:cc:dd:ee:ff     *        eth0
func parseProcNetARP(data []byte) []ARPEntry {
	var entries []ARPEntry
	scanner := bufio.NewScanner(bytes.NewReader(data))
	first := true
	for scanner.Scan() {
		if first {
			first = false
			continue
		}
		fields := strings.Fields(scanner.Text())
		if len(fields) < 6 {
			continue
		}
		mac := fields[3]
		if mac == "00:00:00:00:00:00" || mac == "ff:ff:ff:ff:ff:ff" {
			continue
		}
		entries = append(entries, ARPEntry{
			IPAddress:  fields[0],
			MACAddress: mac,
			Flags:      fields[2],
			Interface:  fields[5],
		})
	}
	return entries
}

// readARPCommand runs `arp -an` and parses the output.
// Output format: ? (192.168.1.1) at aa:bb:cc:dd:ee:ff on en0 ifscope [ethernet]
func (r *ProcNetARPReader) readARPCommand(ctx context.Context) ([]ARPEntry, error) {
	//nolint:gosec // arpPath is resolved via exec.LookPath("arp")
	cmd := exec.CommandContext(ctx, r.arpPath, "-an")
	var out bytes.Buffer
	cmd.Stdout = &out
	if err := cmd.Run(); err != nil {
		return nil, fmt.Errorf("arp command failed: %w", err)
	}

	var entries []ARPEntry
	scanner := bufio.NewScanner(&out)
	for scanner.Scan() {
		entry, ok := parseARPLine(scanner.Text())
		if ok {
			entries = append(entries, entry)
		}
	}
	return entries, nil
}

// parseARPLine parses a single line of `arp -an` output.
// Linux:  ? (192.168.1.1) at aa:bb:cc:dd:ee:ff [ether] on eth0
// macOS:  ? (192.168.1.1) at aa:bb:cc:dd:ee:ff on en0 ifscope [ethernet]
func parseARPLine(line string) (ARPEntry, bool) {
	fields := strings.Fields(line)
	if len(fields) < 4 {
		return ARPEntry{}, false
	}

	atIdx := -1
	for i, f := range fields {
		if f == "at" {
			atIdx = i
			break
		}
	}
	if atIdx < 1 || atIdx+1 >= len(fields) {
		return ARPEntry{}, false
	}

	ipRaw := fields[atIdx-1]
	ip := strings.Trim(ipRaw, "()")
	mac := fields[atIdx+1]

	if mac == "(incomplete)" || mac == "00:00:00:00:00:00" || mac == "ff:ff:ff:ff:ff:ff" {
		return ARPEntry{}, false
	}

	var iface string
	for i, f := range fields {
		if f == "on" && i+1 < len(fields) {
			iface = fields[i+1]
			break
		}
	}

	return ARPEntry{
		IPAddress:  ip,
		MACAddress: mac,
		Interface:  iface,
	}, true
}
