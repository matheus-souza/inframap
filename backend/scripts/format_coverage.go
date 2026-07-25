// Command format_coverage formats Go coverage.out profiles into clean, readable GitHub Markdown tables
// and optionally enforces minimum coverage thresholds per InfraMap Coverage Policy.
package main

import (
	"bufio"
	"flag"
	"fmt"
	"math"
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

func isExcludedPath(cleanPkg, filePath string) bool {
	// Policy exclusions:
	// 1. Auto-generated code (internal/platform/db, *.sql.go)
	// 2. Configuration & scripts (scripts)
	// 3. Bootstrap code (internal/bootstrap)
	// 4. Entrypoints (cmd)
	// 5. Migrations (migrations)
	// 6. Mocks / Stubs (*_mock.go, mock_*.go)
	base := filepath.Base(filePath)
	if strings.HasPrefix(cleanPkg, "scripts") ||
		strings.HasPrefix(cleanPkg, "cmd") ||
		strings.HasPrefix(cleanPkg, "internal/bootstrap") ||
		strings.HasPrefix(cleanPkg, "migrations") ||
		cleanPkg == "internal/platform/db" {
		return true
	}

	if strings.HasSuffix(base, ".sql.go") ||
		strings.HasSuffix(base, "_mock.go") ||
		strings.HasPrefix(base, "mock_") {
		return true
	}

	return false
}

func main() {
	enforceThreshold := flag.Float64("enforce", 0, "Enforce minimum total coverage percentage (0..100). Exits 1 if violated.")
	flag.Parse()

	if *enforceThreshold < 0 || *enforceThreshold > 100 || math.IsNaN(*enforceThreshold) || math.IsInf(*enforceThreshold, 0) {
		fmt.Fprintf(os.Stderr, "Error: --enforce threshold must be a finite number between 0 and 100\n")
		os.Exit(1)
	}

	args := flag.Args()
	if len(args) < 1 {
		fmt.Println("Usage: go run format_coverage.go [--enforce 85] <coverage.out>")
		os.Exit(1)
	}

	filePath := filepath.Clean(args[0])
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

		// Apply policy exclusion rules
		if isExcludedPath(cleanPkg, fullFilePath) {
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
	sb.WriteString(fmt.Sprintf("> **Total Filtered Application Coverage:** **%.1f%%** (%d / %d lines) %s\n", totalPct, totalCovered, totalStmt, totalStatus))

	fmt.Print(sb.String())

	// Enforce threshold if requested
	if *enforceThreshold > 0 && totalPct < *enforceThreshold {
		fmt.Fprintf(os.Stderr, "\n❌ COVERAGE POLICY VIOLATION: Total application coverage is %.1f%%, which is below required threshold of %.1f%%\n", totalPct, *enforceThreshold)
		os.Exit(1)
	}
}

func getStatusEmoji(pct float64) string {
	if pct >= 85.0 {
		return "🟢"
	} else if pct >= 50.0 {
		return "🟡"
	}
	return "🔴"
}
