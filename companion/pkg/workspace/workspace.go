package workspace

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"sync"
	"time"
)

// WorkspaceEntry represents a saved remote workspace.
type WorkspaceEntry struct {
	Path       string `json:"path"`
	Name       string `json:"name"`
	LastOpened int64  `json:"lastOpened"`
	IsGit      bool   `json:"isGit"`
}

// Manager manages persistent workspaces stored in workspaces.json.
type Manager struct {
	mu       sync.RWMutex
	filePath string
	entries  []WorkspaceEntry
}

// NewManager initializes the workspace manager given the config directory.
func NewManager(configDir string) (*Manager, error) {
	if configDir == "" {
		home, _ := os.UserHomeDir()
		configDir = filepath.Join(home, ".config", "mircode")
	}
	_ = os.MkdirAll(configDir, 0755)

	filePath := filepath.Join(configDir, "workspaces.json")
	mgr := &Manager{
		filePath: filePath,
		entries:  make([]WorkspaceEntry, 0),
	}

	_ = mgr.Load()
	return mgr, nil
}

// Load reads workspaces from workspaces.json.
func (m *Manager) Load() error {
	m.mu.Lock()
	defer m.mu.Unlock()

	data, err := os.ReadFile(m.filePath)
	if err != nil {
		if os.IsNotExist(err) {
			m.entries = make([]WorkspaceEntry, 0)
			return nil
		}
		return err
	}

	var list []WorkspaceEntry
	if err := json.Unmarshal(data, &list); err != nil {
		return err
	}

	m.entries = list
	m.sortEntries()
	return nil
}

// Save writes workspaces to workspaces.json atomically.
func (m *Manager) Save() error {
	m.mu.RLock()
	data, err := json.MarshalIndent(m.entries, "", "  ")
	m.mu.RUnlock()
	if err != nil {
		return err
	}

	tmp := m.filePath + ".tmp"
	if err := os.WriteFile(tmp, data, 0644); err != nil {
		return err
	}
	return os.Rename(tmp, m.filePath)
}

// List returns all saved workspaces sorted by last opened descending.
func (m *Manager) List() []WorkspaceEntry {
	m.mu.RLock()
	defer m.mu.RUnlock()

	result := make([]WorkspaceEntry, len(m.entries))
	copy(result, m.entries)
	return result
}

// AddOrUpdate records a workspace as active/opened and persists it.
func (m *Manager) AddOrUpdate(absPath string) (*WorkspaceEntry, error) {
	cleanPath := filepath.Clean(absPath)
	fi, err := os.Stat(cleanPath)
	if err != nil || !fi.IsDir() {
		return nil, fmt.Errorf("invalid directory: %s", cleanPath)
	}

	isGit := false
	if _, err := os.Stat(filepath.Join(cleanPath, ".git")); err == nil {
		isGit = true
	}

	name := filepath.Base(cleanPath)
	now := time.Now().UnixMilli()

	entry := WorkspaceEntry{
		Path:       cleanPath,
		Name:       name,
		LastOpened: now,
		IsGit:      isGit,
	}

	m.mu.Lock()
	found := false
	for i := range m.entries {
		if m.entries[i].Path == cleanPath {
			m.entries[i].LastOpened = now
			m.entries[i].IsGit = isGit
			entry = m.entries[i]
			found = true
			break
		}
	}
	if !found {
		m.entries = append(m.entries, entry)
	}
	m.sortEntries()
	m.mu.Unlock()

	_ = m.Save()
	return &entry, nil
}

// Remove deletes a workspace from the saved list.
func (m *Manager) Remove(targetPath string) error {
	cleanPath := filepath.Clean(targetPath)

	m.mu.Lock()
	filtered := make([]WorkspaceEntry, 0, len(m.entries))
	for _, e := range m.entries {
		if e.Path != cleanPath {
			filtered = append(filtered, e)
		}
	}
	m.entries = filtered
	m.mu.Unlock()

	return m.Save()
}

func (m *Manager) sortEntries() {
	sort.Slice(m.entries, func(i, j int) bool {
		return m.entries[i].LastOpened > m.entries[j].LastOpened
	})
}
