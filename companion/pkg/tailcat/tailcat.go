package tailcat

import (
	"bufio"
	"fmt"
	"os/exec"
	"strings"
	"sync"
)

// Manager manages the Tailcat P2P process.
type Manager struct {
	mu      sync.Mutex
	cmd     *exec.Cmd
	address string
	running bool
}

// NewManager creates a Tailcat manager.
func NewManager() *Manager {
	return &Manager{}
}

// IsAvailable checks if tailcat is installed in the system PATH.
func (m *Manager) IsAvailable() bool {
	_, err := exec.LookPath("tailcat")
	return err == nil
}

// Start launches a tailcat listener or forwarder for localPort.
// Returns the generated Tailcat address (e.g. tc...).
func (m *Manager) Start(localPort int) (string, error) {
	m.mu.Lock()
	defer m.mu.Unlock()

	if !m.IsAvailable() {
		return "", fmt.Errorf("tailcat is not installed in PATH")
	}

	cmd := exec.Command("tailcat", "forward", fmt.Sprintf("%d", localPort))
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return "", err
	}

	if err := cmd.Start(); err != nil {
		return "", err
	}

	m.cmd = cmd
	m.running = true

	// Read first output lines to capture tc... token
	addrCh := make(chan string, 1)
	go func() {
		scanner := bufio.NewScanner(stdout)
		for scanner.Scan() {
			line := scanner.Text()
			// Tailcat addresses typically start with "tc"
			for _, word := range strings.Fields(line) {
				if strings.HasPrefix(word, "tc") && len(word) > 10 {
					addrCh <- word
					return
				}
			}
		}
		close(addrCh)
	}()

	select {
	case addr := <-addrCh:
		m.address = addr
		return addr, nil
	default:
		// Fallback address format
		return "tc-ready", nil
	}
}

// Stop terminates the tailcat process.
func (m *Manager) Stop() {
	m.mu.Lock()
	defer m.mu.Unlock()

	if m.cmd != nil && m.cmd.Process != nil {
		_ = m.cmd.Process.Kill()
		m.running = false
	}
}

// GetAddress returns the active Tailcat address.
func (m *Manager) GetAddress() string {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.address
}
