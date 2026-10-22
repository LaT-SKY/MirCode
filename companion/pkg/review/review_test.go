package review

import (
	"os"
	"path/filepath"
	"testing"
)

func TestReviewCycleAndInvalidation(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "mircode-test-*")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(tempDir)

	// Create test file
	testFile := filepath.Join(tempDir, "test.txt")
	if err := os.WriteFile(testFile, []byte("version 1 content\n"), 0644); err != nil {
		t.Fatal(err)
	}

	mgr, err := NewManager(tempDir)
	if err != nil {
		t.Fatalf("failed to init review manager: %v", err)
	}

	// 1. Initial state: not reviewed
	item, ok := mgr.GetItem("test.txt")
	if ok && item.Reviewed {
		t.Errorf("expected not reviewed initially")
	}

	// 2. Mark as reviewed
	item, err = mgr.ToggleReview("test.txt", true, "tester")
	if err != nil {
		t.Fatalf("failed to toggle review: %v", err)
	}
	if !item.Reviewed {
		t.Errorf("expected item.Reviewed to be true")
	}
	if item.ModifiedSinceReview {
		t.Errorf("expected item.ModifiedSinceReview to be false right after review")
	}
	if item.ContentSha256 == "" {
		t.Errorf("expected ContentSha256 to be computed")
	}

	// 3. Test check when content hasn't changed
	invalidated, item, err := mgr.CheckAndInvalidate("test.txt")
	if err != nil {
		t.Fatalf("check failed: %v", err)
	}
	if invalidated {
		t.Errorf("should not invalidate when content is identical")
	}
	if !item.Reviewed {
		t.Errorf("item should still be reviewed")
	}

	// 4. Modify file
	if err := os.WriteFile(testFile, []byte("version 2 content - modified!\n"), 0644); err != nil {
		t.Fatal(err)
	}

	// 5. Invalidation test
	invalidated, item, err = mgr.CheckAndInvalidate("test.txt")
	if err != nil {
		t.Fatalf("check after edit failed: %v", err)
	}
	if !invalidated {
		t.Errorf("expected review to be invalidated after file edit")
	}
	if item.Reviewed {
		t.Errorf("expected item.Reviewed to be false after edit")
	}
	if !item.ModifiedSinceReview {
		t.Errorf("expected item.ModifiedSinceReview to be true")
	}

	// 6. Persistence check: reload manager from disk
	mgr2, err := NewManager(tempDir)
	if err != nil {
		t.Fatalf("failed to reload manager: %v", err)
	}
	item2, ok := mgr2.GetItem("test.txt")
	if !ok {
		t.Fatalf("expected to find item after reload")
	}
	if item2.Reviewed {
		t.Errorf("reloaded item should not be reviewed")
	}
	if !item2.ModifiedSinceReview {
		t.Errorf("reloaded item should have ModifiedSinceReview = true")
	}
}
