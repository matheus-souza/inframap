package dto_test

import (
	"testing"

	"github.com/matheussouza/inframap/modules/realtime/dto"
)

func TestStreamQueryParams_NormalizeAndValidate(t *testing.T) {
	params := &dto.StreamQueryParams{
		LastEventID: "evt-123",
		Token:       "ims_testtoken",
	}

	params.Normalize()
	if err := params.Validate(); err != nil {
		t.Errorf("expected nil error on Validate, got %v", err)
	}
}
