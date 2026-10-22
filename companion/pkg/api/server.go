package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"runtime"
	"sync"

	"github.com/LaT-SKY/MirCode/companion/pkg/auth"
	"github.com/LaT-SKY/MirCode/companion/pkg/fs"
	"github.com/LaT-SKY/MirCode/companion/pkg/git"
	"github.com/LaT-SKY/MirCode/companion/pkg/review"
	"github.com/LaT-SKY/MirCode/companion/pkg/watcher"
	"github.com/LaT-SKY/MirCode/companion/pkg/workspace"
)

// Server coordinates HTTP API, WebSockets, Git operations, and file watchers.
type Server struct {
	mu           sync.RWMutex
	workspace    string
	authMgr      *auth.Manager
	reviewMgr    *review.Manager
	workspaceMgr *workspace.Manager
	watcher      *watcher.Watcher
	hub          *Hub
	recentSpaces []string
}

// NewServer creates a new API Server instance.
func NewServer(initialWorkspace string, authMgr *auth.Manager) (*Server, error) {
	revMgr, err := review.NewManager(initialWorkspace)
	if err != nil {
		return nil, fmt.Errorf("failed to init review manager: %w", err)
	}

	wsMgr, err := workspace.NewManager("")
	if err != nil {
		return nil, fmt.Errorf("failed to init workspace manager: %w", err)
	}
	_, _ = wsMgr.AddOrUpdate(initialWorkspace)

	hub := NewHub()

	s := &Server{
		workspace:    initialWorkspace,
		authMgr:      authMgr,
		reviewMgr:    revMgr,
		workspaceMgr: wsMgr,
		hub:          hub,
		recentSpaces: []string{initialWorkspace},
	}

	// Init watcher
	w, err := watcher.New(initialWorkspace, revMgr, func(eventType string, payload any) {
		hub.Broadcast(eventType, payload)
	})
	if err != nil {
		return nil, fmt.Errorf("failed to init file watcher: %w", err)
	}
	s.watcher = w

	return s, nil
}

// Close gracefully closes background resources.
func (s *Server) Close() {
	if s.watcher != nil {
		_ = s.watcher.Close()
	}
}

// Handler returns the HTTP handler with all registered routes and middleware.
func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()

	// WebSocket endpoint
	mux.HandleFunc("/api/v1/ws", s.handleWS)

	// Auth check
	mux.HandleFunc("/api/v1/auth/verify", s.handleAuthVerify)

	// System & Workspace
	mux.HandleFunc("/api/v1/system/info", s.handleSystemInfo)
	mux.HandleFunc("/api/v1/fs/browse", s.handleFsBrowse)
	mux.HandleFunc("/api/v1/workspaces", s.handleWorkspaces)
	mux.HandleFunc("/api/v1/workspaces/activate", s.handleWorkspaceActivate)
	mux.HandleFunc("/api/v1/workspaces/recent", s.handleWorkspacesRecent)

	// Git operations
	mux.HandleFunc("/api/v1/git/status", s.handleGitStatus)
	mux.HandleFunc("/api/v1/git/diff", s.handleGitDiff)
	mux.HandleFunc("/api/v1/git/stage", s.handleGitStage)
	mux.HandleFunc("/api/v1/git/unstage", s.handleGitUnstage)
	mux.HandleFunc("/api/v1/git/discard", s.handleGitDiscard)
	mux.HandleFunc("/api/v1/git/commit", s.handleGitCommit)
	mux.HandleFunc("/api/v1/git/branches", s.handleGitBranches)
	mux.HandleFunc("/api/v1/git/branch", s.handleGitBranchAction)
	mux.HandleFunc("/api/v1/git/sync", s.handleGitSync)
	mux.HandleFunc("/api/v1/git/stashes", s.handleGitStashes)
	mux.HandleFunc("/api/v1/git/stash", s.handleGitStashAction)
	mux.HandleFunc("/api/v1/git/resolve", s.handleGitResolveConflict)

	// File reading & writing
	mux.HandleFunc("/api/v1/file/content", s.handleFileContent)

	// Code Review
	mux.HandleFunc("/api/v1/review/status", s.handleReviewStatus)
	mux.HandleFunc("/api/v1/review/toggle", s.handleReviewToggle)

	// Wrap in auth middleware
	return s.authMgr.Middleware(mux)
}

func (s *Server) getActiveWorkspace() string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.workspace
}

func (s *Server) setActiveWorkspace(ws string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if err := s.reviewMgr.SetWorkspace(ws); err != nil {
		return err
	}
	if err := s.watcher.SwitchWorkspace(ws); err != nil {
		return err
	}

	s.workspace = ws

	if s.workspaceMgr != nil {
		_, _ = s.workspaceMgr.AddOrUpdate(ws)
	}

	// Track in recent
	found := false
	for _, p := range s.recentSpaces {
		if p == ws {
			found = true
			break
		}
	}
	if !found {
		s.recentSpaces = append([]string{ws}, s.recentSpaces...)
	}

	// Broadcast workspace switch
	s.hub.Broadcast("WORKSPACE_SWITCHED", map[string]string{"workspace": ws})
	return nil
}

// Handlers

func (s *Server) handleWS(w http.ResponseWriter, r *http.Request) {
	s.hub.HandleWS(w, r)
}

func (s *Server) handleAuthVerify(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{"valid": true, "token": s.authMgr.GetToken()})
}

func (s *Server) handleSystemInfo(w http.ResponseWriter, r *http.Request) {
	hostname, _ := os.Hostname()
	gitVer, _ := git.RunGit(".", "--version")

	writeJSON(w, http.StatusOK, map[string]any{
		"hostname":        hostname,
		"os":              runtime.GOOS,
		"arch":            runtime.GOARCH,
		"gitVersion":      gitVer,
		"activeWorkspace": s.getActiveWorkspace(),
	})
}

func (s *Server) handleFsBrowse(w http.ResponseWriter, r *http.Request) {
	target := r.URL.Query().Get("path")
	res, err := fs.Browse(target)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, res)
}

func (s *Server) handleWorkspaces(w http.ResponseWriter, r *http.Request) {
	if s.workspaceMgr == nil {
		writeError(w, http.StatusInternalServerError, "workspace manager not initialized")
		return
	}

	switch r.Method {
	case http.MethodGet:
		writeJSON(w, http.StatusOK, s.workspaceMgr.List())
	case http.MethodPost:
		var body struct {
			Path string `json:"path"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body.Path == "" {
			writeError(w, http.StatusBadRequest, "Invalid request: path required")
			return
		}
		entry, err := s.workspaceMgr.AddOrUpdate(body.Path)
		if err != nil {
			writeError(w, http.StatusBadRequest, err.Error())
			return
		}
		writeJSON(w, http.StatusOK, entry)
	case http.MethodDelete:
		target := r.URL.Query().Get("path")
		if target == "" {
			var body struct {
				Path string `json:"path"`
			}
			_ = json.NewDecoder(r.Body).Decode(&body)
			target = body.Path
		}
		if target == "" {
			writeError(w, http.StatusBadRequest, "Invalid request: path required")
			return
		}
		if err := s.workspaceMgr.Remove(target); err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		writeJSON(w, http.StatusOK, map[string]bool{"success": true})
	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

func (s *Server) handleWorkspaceActivate(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Path string `json:"path"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body.Path == "" {
		writeError(w, http.StatusBadRequest, "Invalid request body: path required")
		return
	}

	if err := s.setActiveWorkspace(body.Path); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"success":   true,
		"workspace": body.Path,
	})
}

func (s *Server) handleWorkspacesRecent(w http.ResponseWriter, r *http.Request) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	writeJSON(w, http.StatusOK, s.recentSpaces)
}

func (s *Server) handleGitStatus(w http.ResponseWriter, r *http.Request) {
	ws := s.getActiveWorkspace()
	reviews := s.reviewMgr.GetAll()

	st, err := git.GetStatus(ws, reviews)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	// Update branch in review manager
	s.reviewMgr.UpdateBranch(st.Branch)

	writeJSON(w, http.StatusOK, st)
}

func (s *Server) handleGitDiff(w http.ResponseWriter, r *http.Request) {
	ws := s.getActiveWorkspace()
	file := r.URL.Query().Get("file")
	staged := r.URL.Query().Get("staged") == "true"

	diff, err := git.GetDiff(ws, file, staged)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"file":   file,
		"staged": staged,
		"diff":   diff,
	})
}

func (s *Server) handleGitStage(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Paths []string `json:"paths"`
	}
	_ = json.NewDecoder(r.Body).Decode(&body)

	ws := s.getActiveWorkspace()
	if err := git.StageFiles(ws, body.Paths); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	s.hub.Broadcast("GIT_STATUS_UPDATED", map[string]any{"action": "stage"})
	writeJSON(w, http.StatusOK, map[string]bool{"success": true})
}

func (s *Server) handleGitUnstage(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Paths []string `json:"paths"`
	}
	_ = json.NewDecoder(r.Body).Decode(&body)

	ws := s.getActiveWorkspace()
	if err := git.UnstageFiles(ws, body.Paths); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	s.hub.Broadcast("GIT_STATUS_UPDATED", map[string]any{"action": "unstage"})
	writeJSON(w, http.StatusOK, map[string]bool{"success": true})
}

func (s *Server) handleGitDiscard(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Paths []string `json:"paths"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil || len(body.Paths) == 0 {
		writeError(w, http.StatusBadRequest, "Paths required")
		return
	}

	ws := s.getActiveWorkspace()
	if err := git.DiscardFiles(ws, body.Paths); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	s.hub.Broadcast("GIT_STATUS_UPDATED", map[string]any{"action": "discard"})
	writeJSON(w, http.StatusOK, map[string]bool{"success": true})
}

func (s *Server) handleGitCommit(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Message string `json:"message"`
		Amend   bool   `json:"amend"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		writeError(w, http.StatusBadRequest, "Invalid request body")
		return
	}

	ws := s.getActiveWorkspace()
	out, err := git.Commit(ws, body.Message, body.Amend)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	s.hub.Broadcast("GIT_STATUS_UPDATED", map[string]any{"action": "commit"})
	writeJSON(w, http.StatusOK, map[string]any{"success": true, "output": out})
}

func (s *Server) handleGitBranches(w http.ResponseWriter, r *http.Request) {
	ws := s.getActiveWorkspace()
	branches, err := git.ListBranches(ws)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, branches)
}

func (s *Server) handleGitBranchAction(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Action string `json:"action"` // "checkout", "create", "delete"
		Branch string `json:"branch"`
		Force  bool   `json:"force"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body.Branch == "" {
		writeError(w, http.StatusBadRequest, "Branch name and action required")
		return
	}

	ws := s.getActiveWorkspace()
	var out string
	var err error

	switch body.Action {
	case "checkout":
		out, err = git.CheckoutBranch(ws, body.Branch, false)
	case "create":
		out, err = git.CheckoutBranch(ws, body.Branch, true)
	case "delete":
		out, err = git.DeleteBranch(ws, body.Branch, body.Force)
	default:
		writeError(w, http.StatusBadRequest, "Invalid branch action")
		return
	}

	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	s.hub.Broadcast("GIT_STATUS_UPDATED", map[string]any{"action": "branch_" + body.Action})
	writeJSON(w, http.StatusOK, map[string]any{"success": true, "output": out})
}

func (s *Server) handleGitSync(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Action string `json:"action"` // "fetch", "pull", "push"
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body.Action == "" {
		writeError(w, http.StatusBadRequest, "Action required")
		return
	}

	ws := s.getActiveWorkspace()
	out, err := git.Sync(ws, body.Action)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	s.hub.Broadcast("GIT_STATUS_UPDATED", map[string]any{"action": body.Action})
	writeJSON(w, http.StatusOK, map[string]any{"success": true, "output": out})
}

func (s *Server) handleGitStashes(w http.ResponseWriter, r *http.Request) {
	ws := s.getActiveWorkspace()
	stashes, err := git.StashList(ws)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, stashes)
}

func (s *Server) handleGitStashAction(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Action  string `json:"action"` // "push", "pop"
		Message string `json:"message"`
		Index   int    `json:"index"`
	}
	_ = json.NewDecoder(r.Body).Decode(&body)

	ws := s.getActiveWorkspace()
	var out string
	var err error

	switch body.Action {
	case "push":
		out, err = git.StashPush(ws, body.Message)
	case "pop":
		out, err = git.StashPop(ws, body.Index)
	default:
		writeError(w, http.StatusBadRequest, "Invalid stash action")
		return
	}

	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	s.hub.Broadcast("GIT_STATUS_UPDATED", map[string]any{"action": "stash_" + body.Action})
	writeJSON(w, http.StatusOK, map[string]any{"success": true, "output": out})
}

func (s *Server) handleGitResolveConflict(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Path     string `json:"path"`
		Strategy string `json:"strategy"` // "ours", "theirs", "custom"
		Content  string `json:"content"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body.Path == "" {
		writeError(w, http.StatusBadRequest, "Path required")
		return
	}

	ws := s.getActiveWorkspace()

	if body.Strategy == "custom" {
		if err := fs.WriteFile(ws, body.Path, body.Content); err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
	}

	if err := git.ResolveConflict(ws, body.Path, body.Strategy, body.Content); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	s.hub.Broadcast("GIT_STATUS_UPDATED", map[string]any{"action": "conflict_resolved", "path": body.Path})
	writeJSON(w, http.StatusOK, map[string]bool{"success": true})
}

func (s *Server) handleFileContent(w http.ResponseWriter, r *http.Request) {
	ws := s.getActiveWorkspace()

	if r.Method == http.MethodGet {
		relPath := r.URL.Query().Get("path")
		if relPath == "" {
			writeError(w, http.StatusBadRequest, "Path required")
			return
		}

		content, err := fs.ReadFile(ws, relPath)
		if err != nil {
			writeError(w, http.StatusNotFound, err.Error())
			return
		}

		writeJSON(w, http.StatusOK, map[string]any{
			"path":    relPath,
			"content": content,
		})
		return
	}

	if r.Method == http.MethodPut || r.Method == http.MethodPost {
		var body struct {
			Path    string `json:"path"`
			Content string `json:"content"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body.Path == "" {
			writeError(w, http.StatusBadRequest, "Path and content required")
			return
		}

		if err := fs.WriteFile(ws, body.Path, body.Content); err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}

		writeJSON(w, http.StatusOK, map[string]bool{"success": true})
		return
	}

	writeError(w, http.StatusMethodNotAllowed, "Method not allowed")
}

func (s *Server) handleReviewStatus(w http.ResponseWriter, r *http.Request) {
	reviews := s.reviewMgr.GetAll()
	writeJSON(w, http.StatusOK, reviews)
}

func (s *Server) handleReviewToggle(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Path     string `json:"path"`
		Reviewed bool   `json:"reviewed"`
		Reviewer string `json:"reviewer"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body.Path == "" {
		writeError(w, http.StatusBadRequest, "Path required")
		return
	}

	item, err := s.reviewMgr.ToggleReview(body.Path, body.Reviewed, body.Reviewer)
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}

	s.hub.Broadcast("REVIEW_UPDATED", map[string]any{
		"item":      item,
		"filePath":  body.Path,
		"reviewed":  body.Reviewed,
		"timestamp": item.ReviewedAt,
	})

	writeJSON(w, http.StatusOK, item)
}

// Response helpers

func writeJSON(w http.ResponseWriter, status int, data any) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(data)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]any{
		"success": false,
		"error":   message,
	})
}
