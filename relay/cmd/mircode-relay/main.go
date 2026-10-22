package main

import (
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"log"
	"math/big"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

var upgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool {
		return true
	},
	ReadBufferSize:  8192,
	WriteBufferSize: 8192,
}

// TunnelRequest is an HTTP request forwarded across the WebSocket tunnel.
type TunnelRequest struct {
	ID      string              `json:"id"`
	Method  string              `json:"method"`
	URL     string              `json:"url"`
	Headers map[string][]string `json:"headers"`
	Body    string              `json:"body"` // base64 encoded
}

// TunnelResponse is the HTTP response returned from the host companion.
type TunnelResponse struct {
	ID      string              `json:"id"`
	Status  int                 `json:"status"`
	Headers map[string][]string `json:"headers"`
	Body    string              `json:"body"` // base64 encoded
}

// Session represents an active paired session between a host and clients.
type Session struct {
	Code      string
	Token     string
	HostConn  *websocket.Conn
	mu        sync.Mutex
	pending   map[string]chan TunnelResponse
	pendingMu sync.RWMutex
	CreatedAt time.Time
}

func newSession(code, token string) *Session {
	return &Session{
		Code:      code,
		Token:     token,
		pending:   make(map[string]chan TunnelResponse),
		CreatedAt: time.Now(),
	}
}

// RelayServer coordinates sessions and reverse proxying.
type RelayServer struct {
	mu       sync.RWMutex
	sessions map[string]*Session
}

func NewRelayServer() *RelayServer {
	return &RelayServer{
		sessions: make(map[string]*Session),
	}
}

func (s *RelayServer) generateUniqueCode() string {
	s.mu.Lock()
	defer s.mu.Unlock()

	for {
		n, _ := rand.Int(rand.Reader, big.NewInt(900000))
		code := fmt.Sprintf("%06d", n.Int64()+100000)
		if _, exists := s.sessions[code]; !exists {
			return code
		}
	}
}

func (s *RelayServer) Register(token string) string {
	code := s.generateUniqueCode()
	sess := newSession(code, token)

	s.mu.Lock()
	s.sessions[code] = sess
	s.mu.Unlock()

	return code
}

func (s *RelayServer) GetSession(code string) (*Session, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	sess, ok := s.sessions[code]
	return sess, ok
}

func (s *RelayServer) RemoveSession(code string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	delete(s.sessions, code)
}

func main() {
	var (
		portFlag int
		hostFlag string
	)
	flag.IntVar(&portFlag, "port", 18990, "Relay server port to listen on")
	flag.StringVar(&hostFlag, "host", "0.0.0.0", "Host address to bind")
	flag.Parse()

	relay := NewRelayServer()
	mux := http.NewServeMux()

	// Register a new host room
	mux.HandleFunc("/api/v1/relay/register", func(w http.ResponseWriter, r *http.Request) {
		enableCORS(w)
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusOK)
			return
		}

		token := r.URL.Query().Get("token")
		code := relay.Register(token)
		log.Printf("[Relay] New room registered with code: %s", code)

		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]any{
			"code":      code,
			"expiresIn": 86400,
		})
	})

	// Host establishes persistent WebSocket tunnel
	mux.HandleFunc("/api/v1/relay/host", func(w http.ResponseWriter, r *http.Request) {
		code := r.URL.Query().Get("code")
		token := r.URL.Query().Get("token")

		sess, ok := relay.GetSession(code)
		if !ok || sess.Token != token {
			http.Error(w, "Invalid session code or token", http.StatusUnauthorized)
			return
		}

		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			log.Printf("[Relay Host Upgrade Error] %v", err)
			return
		}

		sess.mu.Lock()
		sess.HostConn = conn
		sess.mu.Unlock()

		log.Printf("[Relay Host Connected] Room %s is now online", code)

		// Listen for responses from host
		defer func() {
			sess.mu.Lock()
			sess.HostConn = nil
			sess.mu.Unlock()
			conn.Close()
			relay.RemoveSession(code)
			log.Printf("[Relay Host Disconnected] Room %s closed", code)
		}()

		for {
			_, msg, err := conn.ReadMessage()
			if err != nil {
				break
			}

			var resp TunnelResponse
			if err := json.Unmarshal(msg, &resp); err == nil && resp.ID != "" {
				sess.pendingMu.RLock()
				ch, exists := sess.pending[resp.ID]
				sess.pendingMu.RUnlock()
				if exists {
					select {
					case ch <- resp:
					default:
					}
				}
			}
		}
	})

	// Reverse proxy endpoint: /relay/{code}/api/v1/...
	mux.HandleFunc("/relay/", func(w http.ResponseWriter, r *http.Request) {
		enableCORS(w)
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusOK)
			return
		}

		path := strings.TrimPrefix(r.URL.Path, "/relay/")
		parts := strings.SplitN(path, "/", 2)
		if len(parts) == 0 {
			http.Error(w, "Invalid relay path format: expected /relay/{code}/...", http.StatusBadRequest)
			return
		}

		code := parts[0]
		targetURL := "/"
		if len(parts) > 1 {
			targetURL = "/" + parts[1]
		}
		if r.URL.RawQuery != "" {
			targetURL += "?" + r.URL.RawQuery
		}

		sess, ok := relay.GetSession(code)
		if !ok {
			http.Error(w, "Session code not found or offline", http.StatusNotFound)
			return
		}

		sess.mu.Lock()
		hostConn := sess.HostConn
		sess.mu.Unlock()

		if hostConn == nil {
			http.Error(w, "Companion host is not connected", http.StatusBadGateway)
			return
		}

		// Read request body
		bodyBytes, _ := io.ReadAll(r.Body)
		reqID := fmt.Sprintf("%d-%s", time.Now().UnixNano(), code)

		tunnelReq := TunnelRequest{
			ID:      reqID,
			Method:  r.Method,
			URL:     targetURL,
			Headers: r.Header,
			Body:    base64.StdEncoding.EncodeToString(bodyBytes),
		}

		reqJSON, err := json.Marshal(tunnelReq)
		if err != nil {
			http.Error(w, "Failed to serialize tunnel request", http.StatusInternalServerError)
			return
		}

		respCh := make(chan TunnelResponse, 1)
		sess.pendingMu.Lock()
		sess.pending[reqID] = respCh
		sess.pendingMu.Unlock()

		defer func() {
			sess.pendingMu.Lock()
			delete(sess.pending, reqID)
			sess.pendingMu.Unlock()
		}()

		sess.mu.Lock()
		err = hostConn.WriteMessage(websocket.TextMessage, reqJSON)
		sess.mu.Unlock()
		if err != nil {
			http.Error(w, "Failed to send request to host tunnel", http.StatusBadGateway)
			return
		}

		// Wait for response with 30s timeout
		select {
		case resp := <-respCh:
			for k, vals := range resp.Headers {
				for _, v := range vals {
					w.Header().Add(k, v)
				}
			}
			enableCORS(w)
			w.WriteHeader(resp.Status)
			decodedBody, _ := base64.StdEncoding.DecodeString(resp.Body)
			_, _ = w.Write(decodedBody)
		case <-time.After(30 * time.Second):
			http.Error(w, "Gateway Timeout: host took too long to respond", http.StatusGatewayTimeout)
		}
	})

	addr := fmt.Sprintf("%s:%d", hostFlag, portFlag)
	log.Printf("==================================================================")
	log.Printf("   MirCode Zero-Config Public Relay Server")
	log.Printf("   Listening on %s", addr)
	log.Printf("==================================================================")

	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Fatalf("Relay server exited: %v", err)
	}
}

func enableCORS(w http.ResponseWriter) {
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
	w.Header().Set("Access-Control-Allow-Headers", "Authorization, Content-Type, X-MirCode-Token")
}
