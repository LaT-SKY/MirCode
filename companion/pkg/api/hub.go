package api

import (
	"encoding/json"
	"log"
	"net/http"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

var upgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool {
		return true // Allow all connections
	},
	ReadBufferSize:  4096,
	WriteBufferSize: 4096,
}

// WSMessage represents a message sent to or from WebSocket clients.
type WSMessage struct {
	Type      string `json:"type"`
	Payload   any    `json:"payload,omitempty"`
	Timestamp int64  `json:"timestamp"`
}

// Hub manages active WebSocket connections and broadcasting.
type Hub struct {
	mu      sync.RWMutex
	clients map[*websocket.Conn]bool
}

// NewHub creates a new WebSocket Hub.
func NewHub() *Hub {
	return &Hub{
		clients: make(map[*websocket.Conn]bool),
	}
}

// HandleWS handles an incoming WebSocket upgrade and connection.
func (h *Hub) HandleWS(w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("[WS Upgrade Error] %v", err)
		return
	}

	h.register(conn)

	// Send initial WELCOME message
	_ = h.send(conn, WSMessage{
		Type:      "CONNECTED",
		Payload:   map[string]string{"message": "MirCode Companion WebSocket Connected"},
		Timestamp: time.Now().UnixMilli(),
	})

	// Read loop to detect disconnection & handle ping/pong
	go func() {
		defer func() {
			h.unregister(conn)
			_ = conn.Close()
		}()

		conn.SetReadLimit(32768)
		_ = conn.SetReadDeadline(time.Now().Add(60 * time.Second))
		conn.SetPongHandler(func(string) error {
			_ = conn.SetReadDeadline(time.Now().Add(60 * time.Second))
			return nil
		})

		for {
			_, msg, err := conn.ReadMessage()
			if err != nil {
				break
			}
			// Handle client ping
			var parsed map[string]any
			if err := json.Unmarshal(msg, &parsed); err == nil {
				if t, ok := parsed["type"].(string); ok && t == "PING" {
					_ = h.send(conn, WSMessage{
						Type:      "PONG",
						Timestamp: time.Now().UnixMilli(),
					})
				}
			}
		}
	}()
}

func (h *Hub) register(conn *websocket.Conn) {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.clients[conn] = true
	log.Printf("[WS] Client connected, total clients: %d", len(h.clients))
}

func (h *Hub) unregister(conn *websocket.Conn) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if _, ok := h.clients[conn]; ok {
		delete(h.clients, conn)
		log.Printf("[WS] Client disconnected, remaining clients: %d", len(h.clients))
	}
}

// Broadcast sends a message to all connected clients.
func (h *Hub) Broadcast(eventType string, payload any) {
	h.mu.RLock()
	defer h.mu.RUnlock()

	msg := WSMessage{
		Type:      eventType,
		Payload:   payload,
		Timestamp: time.Now().UnixMilli(),
	}

	data, err := json.Marshal(msg)
	if err != nil {
		return
	}

	for conn := range h.clients {
		_ = conn.SetWriteDeadline(time.Now().Add(5 * time.Second))
		if err := conn.WriteMessage(websocket.TextMessage, data); err != nil {
			// Failed write will be cleaned up in read loop
			log.Printf("[WS Write Error] %v", err)
		}
	}
}

func (h *Hub) send(conn *websocket.Conn, msg WSMessage) error {
	data, err := json.Marshal(msg)
	if err != nil {
		return err
	}
	_ = conn.SetWriteDeadline(time.Now().Add(5 * time.Second))
	return conn.WriteMessage(websocket.TextMessage, data)
}
