// Package httputil provides HTTP response envelope helpers per RFC-008.
package httputil

import (
	"encoding/json"
	"net/http"

	"github.com/google/uuid"
)

// ContextKey is the custom type for HTTP context keys.
type ContextKey string

// RequestIDHeader is the HTTP header key for tracing requests.
const RequestIDHeader = "X-Request-ID"

// RequestIDContextKey is the context key storing the request_id string.
const RequestIDContextKey ContextKey = "request_id"

// SuccessEnvelope represents the RFC-008 success response envelope.
type SuccessEnvelope struct {
	Data any  `json:"data"`
	Meta Meta `json:"meta"`
}

// ErrorEnvelope represents the RFC-008 error response envelope.
type ErrorEnvelope struct {
	Error ErrorBody `json:"error"`
	Meta  Meta      `json:"meta"`
}

// Meta contains request tracing metadata.
type Meta struct {
	RequestID string `json:"request_id"`
}

// ErrorBody contains structured error details.
type ErrorBody struct {
	Code    string       `json:"code"`
	Message string       `json:"message"`
	Details []FieldError `json:"details,omitempty"`
}

// FieldError represents a single field validation error.
type FieldError struct {
	Field string `json:"field"`
	Issue string `json:"issue"`
}

// WriteJSON encodes data into a JSON response with status code and RFC-008 envelope.
func WriteJSON(w http.ResponseWriter, r *http.Request, statusCode int, data any) {
	reqID := GetRequestID(r)
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)

	resp := SuccessEnvelope{
		Data: data,
		Meta: Meta{RequestID: reqID},
	}

	_ = json.NewEncoder(w).Encode(resp)
}

// WriteError encodes an error payload into an RFC-008 error envelope.
func WriteError(w http.ResponseWriter, r *http.Request, statusCode int, code, message string, details []FieldError) {
	reqID := GetRequestID(r)
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)

	resp := ErrorEnvelope{
		Error: ErrorBody{
			Code:    code,
			Message: message,
			Details: details,
		},
		Meta: Meta{RequestID: reqID},
	}

	_ = json.NewEncoder(w).Encode(resp)
}

// GetRequestID retrieves the request ID from the context or generates a new UUIDv7.
func GetRequestID(r *http.Request) string {
	if r != nil {
		if id, ok := r.Context().Value(RequestIDContextKey).(string); ok && id != "" {
			return id
		}
		if id := r.Header.Get(RequestIDHeader); id != "" {
			return id
		}
	}
	id, err := uuid.NewV7()
	if err != nil {
		return uuid.New().String()
	}
	return id.String()
}
