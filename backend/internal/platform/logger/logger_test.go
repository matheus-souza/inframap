package logger_test

import (
	"testing"

	"github.com/matheussouza/inframap/internal/platform/logger"
)

func TestNewLogger(t *testing.T) {
	log := logger.New()
	if log == nil {
		t.Fatal("expected non-nil logger")
	}
}
