package relay

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

// TunnelRequest matches the relay server definition.
type TunnelRequest struct {
	ID      string              `json:"id"`
	Method  string              `json:"method"`
	URL     string              `json:"url"`
	Headers map[string][]string `json:"headers"`
	Body    string              `json:"body"`
}

// TunnelResponse matches the relay server definition.
type TunnelResponse struct {
	ID      string              `json:"id"`
	Status  int                 `json:"status"`
	Headers map[string][]string `json:"headers"`
	Body    string              `json:"body"`
}

// Client manages connection to the zero-config relay server.
type Client struct {
	mu           sync.RWMutex
	relayURL     string
	token        string
	desiredCode  string
	pairCode     string
	localHandler http.Handler
	stopCh       chan struct{}
	connected    bool
	onConnected  func(code string)
}

// NewClient creates a new relay client with an optional desired pair code.
func NewClient(relayURL, token, desiredCode string, localHandler http.Handler, onConnected func(code string)) *Client {
	if relayURL == "" {
		relayURL = "http://127.0.0.1:18990" // Default to relay port or public fallback
	}
	return &Client{
		relayURL:     strings.TrimSuffix(relayURL, "/"),
		token:        token,
		desiredCode:  desiredCode,
		localHandler: localHandler,
		stopCh:       make(chan struct{}),
		onConnected:  onConnected,
	}
}

// Start begins the registration and connection loop in background.
func (c *Client) Start() {
	go c.runLoop()
}

// Stop terminates the relay client.
func (c *Client) Stop() {
	close(c.stopCh)
}

// GetPairCode returns the active 6-digit pair code.
func (c *Client) GetPairCode() string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.pairCode
}

// IsConnected returns connection status.
func (c *Client) IsConnected() bool {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.connected
}

func (c *Client) runLoop() {
	for {
		select {
		case <-c.stopCh:
			return
		default:
			code, err := c.register()
			if err != nil {
				// Relay might be starting up or unavailable, retry after 3 seconds
				time.Sleep(3 * time.Second)
				continue
			}

			c.mu.Lock()
			c.pairCode = code
			c.mu.Unlock()

			if c.onConnected != nil {
				c.onConnected(code)
			}

			if err := c.connectTunnel(code); err != nil {
				c.mu.Lock()
				c.connected = false
				c.mu.Unlock()
				time.Sleep(2 * time.Second)
			}
		}
	}
}

func (c *Client) register() (string, error) {
	reqURL := fmt.Sprintf("%s/api/v1/relay/register?token=%s&code=%s", c.relayURL, url.QueryEscape(c.token), url.QueryEscape(c.desiredCode))
	resp, err := http.Post(reqURL, "application/json", nil)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("relay register failed with status: %d", resp.StatusCode)
	}

	var res struct {
		Code string `json:"code"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&res); err != nil {
		return "", err
	}
	return res.Code, nil
}

func (c *Client) connectTunnel(code string) error {
	wsScheme := "ws"
	if strings.HasPrefix(c.relayURL, "https://") {
		wsScheme = "wss"
	}
	host := strings.TrimPrefix(c.relayURL, "http://")
	host = strings.TrimPrefix(host, "https://")

	wsURL := fmt.Sprintf("%s://%s/api/v1/relay/host?code=%s&token=%s", wsScheme, host, code, url.QueryEscape(c.token))
	conn, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		return err
	}
	defer conn.Close()

	c.mu.Lock()
	c.connected = true
	c.mu.Unlock()

	log.Printf("[Relay Client] Connected to relay at %s with Pair Code: %s", c.relayURL, code)

	writeMu := sync.Mutex{}

	for {
		_, msg, err := conn.ReadMessage()
		if err != nil {
			return err
		}

		var req TunnelRequest
		if err := json.Unmarshal(msg, &req); err != nil {
			continue
		}

		// Process request concurrently
		go func(tReq TunnelRequest) {
			resp := c.handleLocalRequest(tReq)
			data, err := json.Marshal(resp)
			if err != nil {
				return
			}

			writeMu.Lock()
			_ = conn.WriteMessage(websocket.TextMessage, data)
			writeMu.Unlock()
		}(req)
	}
}

func (c *Client) handleLocalRequest(tReq TunnelRequest) TunnelResponse {
	decodedBody, _ := base64.StdEncoding.DecodeString(tReq.Body)
	httpReq, err := http.NewRequest(tReq.Method, tReq.URL, bytes.NewReader(decodedBody))
	if err != nil {
		return TunnelResponse{
			ID:     tReq.ID,
			Status: http.StatusInternalServerError,
		}
	}

	for k, vals := range tReq.Headers {
		for _, v := range vals {
			httpReq.Header.Add(k, v)
		}
	}

	rec := httptest.NewRecorder()
	c.localHandler.ServeHTTP(rec, httpReq)

	res := rec.Result()
	respBody, _ := io.ReadAll(res.Body)

	return TunnelResponse{
		ID:      tReq.ID,
		Status:  res.StatusCode,
		Headers: res.Header,
		Body:    base64.StdEncoding.EncodeToString(respBody),
	}
}
