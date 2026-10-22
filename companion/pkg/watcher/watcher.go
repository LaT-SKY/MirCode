package watcher

import (
	"log"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/fsnotify/fsnotify"
	"github.com/LaT-SKY/MirCode/companion/pkg/review"
)

// EventCallback defines listener for file watcher events.
type EventCallback func(eventType string, payload any)

// Watcher manages recursive filesystem watching and debounced notifications.
type Watcher struct {
	mu         sync.RWMutex
	fsWatcher  *fsnotify.Watcher
	workspace  string
	reviewMgr  *review.Manager
	onEvent    EventCallback
	stopCh     chan struct{}
	debounceMu sync.Mutex
	timers     map[string]*time.Timer
}

// New creates a new recursive watcher for the workspace.
func New(workspace string, reviewMgr *review.Manager, callback EventCallback) (*Watcher, error) {
	fsWatcher, err := fsnotify.NewWatcher()
	if err != nil {
		return nil, err
	}

	w := &Watcher{
		fsWatcher: fsWatcher,
		workspace: workspace,
		reviewMgr: reviewMgr,
		onEvent:   callback,
		stopCh:    make(chan struct{}),
		timers:    make(map[string]*time.Timer),
	}

	if err := w.watchRecursive(workspace); err != nil {
		fsWatcher.Close()
		return nil, err
	}

	go w.eventLoop()

	return w, nil
}

// SwitchWorkspace re-binds watcher to a new directory.
func (w *Watcher) SwitchWorkspace(newWorkspace string) error {
	w.mu.Lock()
	defer w.mu.Unlock()

	// Clear old watches
	for _, dir := range w.fsWatcher.WatchList() {
		_ = w.fsWatcher.Remove(dir)
	}

	w.workspace = newWorkspace
	return w.watchRecursive(newWorkspace)
}

// Close stops the watcher.
func (w *Watcher) Close() error {
	close(w.stopCh)
	return w.fsWatcher.Close()
}

func (w *Watcher) eventLoop() {
	for {
		select {
		case <-w.stopCh:
			return
		case event, ok := <-w.fsWatcher.Events:
			if !ok {
				return
			}
			w.handleFsEvent(event)
		case err, ok := <-w.fsWatcher.Errors:
			if !ok {
				return
			}
			log.Printf("[Watcher Error] %v", err)
		}
	}
}

func (w *Watcher) handleFsEvent(event fsnotify.Event) {
	path := event.Name

	// Ignore internal and ignored directories
	if isIgnored(path) {
		return
	}

	// If directory was created, watch it
	if event.Has(fsnotify.Create) {
		if fi, err := os.Stat(path); err == nil && fi.IsDir() {
			_ = w.watchRecursive(path)
		}
	}

	// Debounce file changes per path
	w.debounce(path, func() {
		w.processChange(path)
	})
}

func (w *Watcher) debounce(key string, action func()) {
	w.debounceMu.Lock()
	defer w.debounceMu.Unlock()

	if timer, exists := w.timers[key]; exists {
		timer.Stop()
	}

	w.timers[key] = time.AfterFunc(200*time.Millisecond, func() {
		w.debounceMu.Lock()
		delete(w.timers, key)
		w.debounceMu.Unlock()

		action()
	})
}

func (w *Watcher) processChange(absPath string) {
	w.mu.RLock()
	ws := w.workspace
	w.mu.RUnlock()

	relPath, err := filepath.Rel(ws, absPath)
	if err != nil {
		return
	}

	// Check review invalidation
	if w.reviewMgr != nil {
		invalidated, item, err := w.reviewMgr.CheckAndInvalidate(relPath)
		if err == nil && invalidated && item != nil {
			log.Printf("[Review Invalidation] File modified after review: %s", relPath)
			if w.onEvent != nil {
				w.onEvent("REVIEW_INVALIDATED", map[string]any{
					"filePath":            relPath,
					"modifiedSinceReview": true,
					"reviewed":            false,
					"item":                item,
				})
			}
		}
	}

	// Emit FILE_CHANGED
	if w.onEvent != nil {
		w.onEvent("FILE_CHANGED", map[string]any{
			"filePath":  relPath,
			"timestamp": time.Now().UnixMilli(),
		})
		// Also notify git status update
		w.onEvent("GIT_STATUS_UPDATED", map[string]any{
			"workspace": ws,
			"timestamp": time.Now().UnixMilli(),
		})
	}
}

func (w *Watcher) watchRecursive(root string) error {
	return filepath.Walk(root, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return nil
		}
		if info.IsDir() {
			if isIgnored(path) {
				return filepath.SkipDir
			}
			return w.fsWatcher.Add(path)
		}
		return nil
	})
}

func isIgnored(path string) bool {
	base := filepath.Base(path)
	if base == ".git" || base == ".mircode" || base == "node_modules" || base == ".gradle" || base == "build" || base == ".idea" {
		return true
	}
	return false
}
