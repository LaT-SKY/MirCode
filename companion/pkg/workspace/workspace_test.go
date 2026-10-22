package workspace

import (
	"os"
	"path/filepath"
	"testing"
)

func TestWorkspaceManager(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "mircode-ws-test")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

	mgr, err := NewManager(tempDir)
	if err != nil {
		t.Fatalf("NewManager failed: %v", err)
	}

	ws1 := filepath.Join(tempDir, "project-a")
	if err := os.MkdirAll(ws1, 0755); err != nil {
		t.Fatal(err)
	}

	entry, err := mgr.AddOrUpdate(ws1)
	if err != nil {
		t.Fatalf("AddOrUpdate failed: %v", err)
	}
	if entry.Name != "project-a" {
		t.Fatalf("Expected name 'project-a', got '%s'", entry.Name)
	}

	list := mgr.List()
	if len(list) != 1 {
		t.Fatalf("Expected 1 entry, got %d", len(list))
	}

	// Remove
	if err := mgr.Remove(ws1); err != nil {
		t.Fatalf("Remove failed: %v", err)
	}
	if len(mgr.List()) != 0 {
		t.Fatalf("Expected 0 entries after remove")
	}
}
