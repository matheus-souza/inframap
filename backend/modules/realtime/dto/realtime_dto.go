// Package dto defines data transfer objects for the Realtime module.
package dto

import "strings"

// StreamQueryParams holds parsed query parameters for SSE connections.
type StreamQueryParams struct {
	LastEventID string `json:"last_event_id,omitempty"`
	Token       string `json:"token,omitempty"`
}

// Normalize sanitizes string fields.
func (q *StreamQueryParams) Normalize() {
	q.LastEventID = strings.TrimSpace(q.LastEventID)
	q.Token = strings.TrimSpace(q.Token)
}

// Validate checks parameter validity.
func (q *StreamQueryParams) Validate() error {
	return nil
}
