package auth

import (
	"crypto/rand"
	"encoding/hex"
	"net/http"
	"strings"
	"sync"
)

// Manager handles auth token generation and validation.
type Manager struct {
	mu    sync.RWMutex
	token string
}

// NewManager creates an Auth Manager with a generated or specified token.
func NewManager(initialToken string) *Manager {
	if initialToken == "" {
		initialToken = generateRandomToken(16)
	}
	return &Manager{
		token: initialToken,
	}
}

// GetToken returns the current valid token.
func (m *Manager) GetToken() string {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.token
}

// Validate checks if the provided token matches.
func (m *Manager) Validate(token string) bool {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return token != "" && m.token == token
}

// Middleware creates an HTTP middleware that enforces token auth.
func (m *Manager) Middleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Allow CORS preflight requests
		if r.Method == http.MethodOptions {
			w.Header().Set("Access-Control-Allow-Origin", "*")
			w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
			w.Header().Set("Access-Control-Allow-Headers", "Authorization, Content-Type, X-MirCode-Token")
			w.WriteHeader(http.StatusOK)
			return
		}

		token := extractToken(r)
		if !m.Validate(token) {
			w.Header().Set("Content-Type", "application/json")
			w.Header().Set("Access-Control-Allow-Origin", "*")
			w.WriteHeader(http.StatusUnauthorized)
			w.Write([]byte(`{"success":false,"error":"Unauthorized: invalid or missing auth token"}`))
			return
		}

		next.ServeHTTP(w, r)
	})
}

// extractToken extracts the token from Authorization header or query parameter.
func extractToken(r *http.Request) string {
	// Check Authorization header: Bearer <token>
	authHeader := r.Header.Get("Authorization")
	if authHeader != "" {
		parts := strings.SplitN(authHeader, " ", 2)
		if len(parts) == 2 && strings.EqualFold(parts[0], "Bearer") {
			return strings.TrimSpace(parts[1])
		}
		return strings.TrimSpace(authHeader)
	}

	// Check custom header
	if customHeader := r.Header.Get("X-MirCode-Token"); customHeader != "" {
		return strings.TrimSpace(customHeader)
	}

	// Fallback to query param (e.g. for WebSockets)
	return r.URL.Query().Get("token")
}

func generateRandomToken(byteLength int) string {
	b := make([]byte, byteLength)
	if _, err := rand.Read(b); err != nil {
		return "mircode-secret-default-token"
	}
	return hex.EncodeToString(b)
}
