package review

import (
	"crypto/sha1"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

// Item holds review metadata for a single file.
type Item struct {
	FilePath            string `json:"filePath"`
	BlobSha             string `json:"blobSha"`
	ContentSha256       string `json:"contentSha256"`
	Reviewed            bool   `json:"reviewed"`
	ModifiedSinceReview bool   `json:"modifiedSinceReview"`
	ReviewedAt          int64  `json:"reviewedAt"`
	ReviewedBy          string `json:"reviewedBy"`
}

// State represents the persisted review configuration.
type State struct {
	Version   int              `json:"version"`
	Workspace string           `json:"workspace"`
	Branch    string           `json:"branch"`
	Reviews   map[string]*Item `json:"reviews"`
}

// Manager manages review statuses for a workspace.
type Manager struct {
	mu        sync.RWMutex
	workspace string
	state     *State
}

// NewManager creates a review manager for a specific workspace.
func NewManager(workspace string) (*Manager, error) {
	absWorkspace, err := filepath.Abs(workspace)
	if err != nil {
		absWorkspace = workspace
	}

	m := &Manager{
		workspace: absWorkspace,
		state: &State{
			Version:   1,
			Workspace: absWorkspace,
			Reviews:   make(map[string]*Item),
		},
	}

	if err := m.load(); err != nil {
		// If file doesn't exist, ignore error and start with empty state
		if !os.IsNotExist(err) {
			return nil, err
		}
	}

	// Ensure .mircode is excluded from git tracking
	m.ensureGitExclude()

	return m, nil
}

// GetWorkspace returns the workspace path.
func (m *Manager) GetWorkspace() string {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.workspace
}

// SetWorkspace switches to another workspace.
func (m *Manager) SetWorkspace(workspace string) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	absWorkspace, err := filepath.Abs(workspace)
	if err != nil {
		absWorkspace = workspace
	}

	m.workspace = absWorkspace
	m.state = &State{
		Version:   1,
		Workspace: absWorkspace,
		Reviews:   make(map[string]*Item),
	}

	if err := m.loadUnlocked(); err != nil && !os.IsNotExist(err) {
		return err
	}

	m.ensureGitExcludeUnlocked()
	return nil
}

// GetAll returns a snapshot of all review states.
func (m *Manager) GetAll() map[string]Item {
	m.mu.RLock()
	defer m.mu.RUnlock()

	result := make(map[string]Item, len(m.state.Reviews))
	for k, v := range m.state.Reviews {
		if v != nil {
			result[k] = *v
		}
	}
	return result
}

// GetItem returns review state for a single relative path.
func (m *Manager) GetItem(relPath string) (*Item, bool) {
	m.mu.RLock()
	defer m.mu.RUnlock()

	relPath = normalizeRelPath(relPath)
	item, ok := m.state.Reviews[relPath]
	if !ok || item == nil {
		return nil, false
	}
	copy := *item
	return &copy, true
}

// ToggleReview marks a file as reviewed or unreviewed.
func (m *Manager) ToggleReview(relPath string, reviewed bool, reviewer string) (*Item, error) {
	m.mu.Lock()
	defer m.mu.Unlock()

	relPath = normalizeRelPath(relPath)
	absPath := filepath.Join(m.workspace, relPath)

	item, exists := m.state.Reviews[relPath]
	if !exists || item == nil {
		item = &Item{
			FilePath: relPath,
		}
		m.state.Reviews[relPath] = item
	}

	if reviewed {
		blobSha, sha256Hash, err := computeHashes(absPath)
		if err != nil {
			// If file was deleted or unreadable, still allow marking
			blobSha = "deleted"
			sha256Hash = "deleted"
		}
		item.Reviewed = true
		item.ModifiedSinceReview = false
		item.BlobSha = blobSha
		item.ContentSha256 = sha256Hash
		item.ReviewedAt = time.Now().UnixMilli()
		if reviewer == "" {
			reviewer = "reviewer"
		}
		item.ReviewedBy = reviewer
	} else {
		item.Reviewed = false
		item.ModifiedSinceReview = false
	}

	if err := m.saveUnlocked(); err != nil {
		return nil, fmt.Errorf("failed to save review state: %w", err)
	}

	ret := *item
	return &ret, nil
}

// CheckAndInvalidate checks if the file has changed since it was reviewed.
// If it was reviewed and changed, it resets Reviewed to false, sets ModifiedSinceReview to true.
// Returns (invalidated, item, error).
func (m *Manager) CheckAndInvalidate(relPath string) (bool, *Item, error) {
	m.mu.Lock()
	defer m.mu.Unlock()

	relPath = normalizeRelPath(relPath)
	item, exists := m.state.Reviews[relPath]
	if !exists || item == nil || !item.Reviewed {
		return false, nil, nil
	}

	absPath := filepath.Join(m.workspace, relPath)
	blobSha, sha256Hash, err := computeHashes(absPath)
	if err != nil {
		// File might be deleted or unreadable, invalidate review
		item.Reviewed = false
		item.ModifiedSinceReview = true
		_ = m.saveUnlocked()
		copy := *item
		return true, &copy, nil
	}

	// Compare with recorded hashes
	if item.ContentSha256 != "" && item.ContentSha256 != sha256Hash {
		item.Reviewed = false
		item.ModifiedSinceReview = true
		item.BlobSha = blobSha
		item.ContentSha256 = sha256Hash
		if err := m.saveUnlocked(); err != nil {
			return true, item, err
		}
		copy := *item
		return true, &copy, nil
	}

	return false, item, nil
}

// UpdateBranch updates the recorded branch name.
func (m *Manager) UpdateBranch(branch string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.state.Branch = branch
	_ = m.saveUnlocked()
}

func (m *Manager) stateFilePath() string {
	return filepath.Join(m.workspace, ".mircode", "review_state.json")
}

func (m *Manager) load() error {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.loadUnlocked()
}

func (m *Manager) loadUnlocked() error {
	path := m.stateFilePath()
	data, err := os.ReadFile(path)
	if err != nil {
		return err
	}

	var state State
	if err := json.Unmarshal(data, &state); err != nil {
		return err
	}

	if state.Reviews == nil {
		state.Reviews = make(map[string]*Item)
	}
	m.state = &state
	return nil
}

func (m *Manager) saveUnlocked() error {
	path := m.stateFilePath()
	dir := filepath.Dir(path)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return err
	}

	data, err := json.MarshalIndent(m.state, "", "  ")
	if err != nil {
		return err
	}

	// Atomic write
	tmpFile := path + ".tmp"
	if err := os.WriteFile(tmpFile, data, 0644); err != nil {
		return err
	}
	return os.Rename(tmpFile, path)
}

func (m *Manager) ensureGitExclude() {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.ensureGitExcludeUnlocked()
}

func (m *Manager) ensureGitExcludeUnlocked() {
	excludePath := filepath.Join(m.workspace, ".git", "info", "exclude")
	content, err := os.ReadFile(excludePath)
	if err != nil {
		return
	}

	if !strings.Contains(string(content), ".mircode") {
		f, err := os.OpenFile(excludePath, os.O_APPEND|os.O_WRONLY, 0644)
		if err == nil {
			defer f.Close()
			_, _ = f.WriteString("\n# MirCode metadata\n.mircode/\n")
		}
	}
}

func computeHashes(filePath string) (blobSha string, sha256Hex string, err error) {
	f, err := os.Open(filePath)
	if err != nil {
		return "", "", err
	}
	defer f.Close()

	stat, err := f.Stat()
	if err != nil {
		return "", "", err
	}

	// 1. Git Blob SHA-1: "blob <size>\0<content>"
	gitHeader := fmt.Sprintf("blob %d\x00", stat.Size())
	hGit := sha1.New()
	_, _ = hGit.Write([]byte(gitHeader))

	// 2. Standard SHA-256
	h256 := sha256.New()

	mw := io.MultiWriter(hGit, h256)
	if _, err := io.Copy(mw, f); err != nil {
		return "", "", err
	}

	blobSha = hex.EncodeToString(hGit.Sum(nil))
	sha256Hex = hex.EncodeToString(h256.Sum(nil))
	return blobSha, sha256Hex, nil
}

func normalizeRelPath(p string) string {
	p = filepath.Clean(p)
	p = strings.TrimPrefix(p, "/")
	p = strings.TrimPrefix(p, "\\")
	return filepath.ToSlash(p)
}
