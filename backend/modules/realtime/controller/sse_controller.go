// Package controller provides HTTP REST & SSE handlers for the Realtime module.
package controller

import (
	"fmt"
	"net/http"
	"time"

	"github.com/matheussouza/inframap/internal/platform/httputil"
	"github.com/matheussouza/inframap/modules/realtime/gateway"
)

// DefaultHeartbeatInterval is the duration between SSE keep-alive ping comments.
const DefaultHeartbeatInterval = 15 * time.Second

// SSEController manages Server-Sent Event connections.
type SSEController struct {
	gw *gateway.Gateway
}

// NewSSEController constructs a new SSEController.
func NewSSEController(gw *gateway.Gateway) *SSEController {
	return &SSEController{gw: gw}
}

// StreamEvents handles GET /api/v1/events/stream
func (c *SSEController) StreamEvents(w http.ResponseWriter, r *http.Request) {
	flusher, ok := w.(http.Flusher)
	if !ok {
		httputil.WriteError(w, r, http.StatusBadRequest, "STREAMING_UNSUPPORTED", "Server-Sent Events streaming is not supported by connection", nil)
		return
	}

	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	w.Header().Set("X-Accel-Buffering", "no")

	// 1. Register subscription FIRST to prevent missing concurrent live events during replay query
	subChan, unsub := c.gw.Subscribe()
	defer unsub()

	// Initial connection ack
	_, _ = fmt.Fprint(w, ": connected\n\n")
	flusher.Flush()

	// 2. Inspect Last-Event-ID for replay
	lastEventID := r.Header.Get("Last-Event-ID")
	if lastEventID == "" {
		lastEventID = r.URL.Query().Get("last_event_id")
	}

	if lastEventID != "" {
		missedEvents := c.gw.GetEventsAfter(lastEventID)
		for _, evt := range missedEvents {
			_, _ = fmt.Fprint(w, evt.FormatSSE())
		}
		if len(missedEvents) > 0 {
			flusher.Flush()
		}
	}

	ticker := time.NewTicker(DefaultHeartbeatInterval)
	defer ticker.Stop()

	for {
		select {
		case <-r.Context().Done():
			return
		case <-ticker.C:
			_, _ = fmt.Fprint(w, ": ping\n\n")
			flusher.Flush()
		case msg, open := <-subChan:
			if !open {
				return
			}
			_, _ = fmt.Fprint(w, msg.FormatSSE())
			flusher.Flush()
		}
	}
}
