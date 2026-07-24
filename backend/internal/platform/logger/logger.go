// Package logger provides a structured slog-based logger for InfraMap.
package logger

import (
	"log/slog"
	"os"
)

// New creates a new structured JSON logger for production use.
func New() *slog.Logger {
	return slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))
}
