// Command format_coverage formats Go coverage.out profiles into clean, readable GitHub Markdown tables.
package main

import (
	"bufio"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
)

type pkgStats struct {
	numStmt     int
	coveredStmt int
}

func main() {
	if len(os.Args) < 2 {
		fmt.Println("Usage: go run format_coverage.go <coverage.out>")
		os.Exit(1)
	}

	filePath := filepath.Clean(os.Args[1])
	file, err := os.Open(filePath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error opening coverage file: %v\n", err)
		os.Exit(1)
	}
	defer func() { _ = file.Close() }()

	packages := make(map[string]*pkgStats)
	var totalStmt, totalCovered int

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "mode:") {
			continue
		}

		fields := strings.Fields(line)
		if len(fields) < 3 {
			continue
		}

		stmtCount, err1 := strconv.Atoi(fields[1])
		execCount, err2 := strconv.Atoi(fields[2])
		if err1 != nil || err2 != nil {
			continue
		}

		filePathWithLines := fields[0]
		colonIdx := strings.Index(filePathWithLines, ":")
		if colonIdx == -1 {
			continue
		}

		fullFilePath := filePathWithLines[:colonIdx]
		dirPath := filepath.Dir(fullFilePath)

		// Simplify package path (strip module prefix)
		cleanPkg := dirPath
		if idx := strings.Index(dirPath, "inframap/"); idx != -1 {
			cleanPkg = dirPath[idx+len("inframap/"):]
		}

		// Skip generated code or scripts directory
		if strings.HasPrefix(cleanPkg, "scripts") || cleanPkg == "internal/platform/db" {
			continue
		}

		stats, exists := packages[cleanPkg]
		if !exists {
			stats = &pkgStats{}
			packages[cleanPkg] = stats
		}

		stats.numStmt += stmtCount
		if execCount > 0 {
			stats.coveredStmt += stmtCount
		}

		totalStmt += stmtCount
		if execCount > 0 {
			totalCovered += stmtCount
		}
	}

	if err := scanner.Err(); err != nil {
		fmt.Fprintf(os.Stderr, "Error reading coverage file: %v\n", err)
		os.Exit(1)
	}

	// Sort package names
	var pkgNames []string
	for k, st := range packages {
		if st.numStmt > 0 {
			pkgNames = append(pkgNames, k)
		}
	}
	sort.Strings(pkgNames)

	// Build Markdown Output
	var sb strings.Builder
	sb.WriteString("### 📊 Code Coverage Summary Report\n\n")
	sb.WriteString("| Package / Module | Covered Lines | Total Lines | Coverage | Status |\n")
	sb.WriteString("|---|:---:|:---:|:---:|:---:|\n")

	for _, name := range pkgNames {
		st := packages[name]
		pct := 0.0
		if st.numStmt > 0 {
			pct = (float64(st.coveredStmt) / float64(st.numStmt)) * 100.0
		}

		status := getStatusEmoji(pct)
		sb.WriteString(fmt.Sprintf("| `%s` | %d | %d | **%.1f%%** | %s |\n", name, st.coveredStmt, st.numStmt, pct, status))
	}

	totalPct := 0.0
	if totalStmt > 0 {
		totalPct = (float64(totalCovered) / float64(totalStmt)) * 100.0
	}
	totalStatus := getStatusEmoji(totalPct)

	sb.WriteString("\n> [!NOTE]\n")
	sb.WriteString(fmt.Sprintf("> **Total Application Coverage:** **%.1f%%** (%d / %d lines) %s\n", totalPct, totalCovered, totalStmt, totalStatus))

	fmt.Print(sb.String())
}

func getStatusEmoji(pct float64) string {
	if pct >= 80.0 {
		return "🟢"
	} else if pct >= 50.0 {
		return "🟡"
	}
	return "🔴"
}
