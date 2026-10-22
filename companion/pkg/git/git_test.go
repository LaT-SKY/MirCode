package git

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/LaT-SKY/MirCode/companion/pkg/review"
)

func TestGitLifecycleOperations(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "mircode-git-test-*")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(tempDir)

	// Init git repo
	if _, err := RunGit(tempDir, "init", "-b", "main"); err != nil {
		t.Fatalf("git init failed: %v", err)
	}
	_, _ = RunGit(tempDir, "config", "user.name", "Tester")
	_, _ = RunGit(tempDir, "config", "user.email", "test@mircode.dev")

	// Create a file
	filePath := filepath.Join(tempDir, "file1.txt")
	_ = os.WriteFile(filePath, []byte("line 1\nline 2\n"), 0644)

	// 1. Status: untracked
	st, err := GetStatus(tempDir, nil)
	if err != nil {
		t.Fatalf("GetStatus failed: %v", err)
	}
	if len(st.UntrackedFiles) != 1 || st.UntrackedFiles[0].Path != "file1.txt" {
		t.Errorf("expected 1 untracked file, got %+v", st.UntrackedFiles)
	}

	// 2. Stage file
	if err := StageFiles(tempDir, []string{"file1.txt"}); err != nil {
		t.Fatalf("StageFiles failed: %v", err)
	}
	st, _ = GetStatus(tempDir, nil)
	if len(st.StagedFiles) != 1 || st.StagedFiles[0].Path != "file1.txt" {
		t.Errorf("expected 1 staged file, got %+v", st.StagedFiles)
	}

	// 3. Commit
	if _, err := Commit(tempDir, "Initial commit", false); err != nil {
		t.Fatalf("Commit failed: %v", err)
	}
	st, _ = GetStatus(tempDir, nil)
	if !st.Clean {
		t.Errorf("expected clean status after commit")
	}

	// 4. Modify file & check diff
	_ = os.WriteFile(filePath, []byte("line 1\nline 2 modified\nline 3 added\n"), 0644)
	diff, err := GetDiff(tempDir, "file1.txt", false)
	if err != nil {
		t.Fatalf("GetDiff failed: %v", err)
	}
	if diff == "" {
		t.Errorf("expected non-empty diff")
	}

	// 5. Test review integration with status
	reviews := map[string]review.Item{
		"file1.txt": {
			FilePath: "file1.txt",
			Reviewed: true,
		},
	}
	st, _ = GetStatus(tempDir, reviews)
	if len(st.UnstagedFiles) != 1 || !st.UnstagedFiles[0].Reviewed {
		t.Errorf("expected review status to be mapped to file1.txt")
	}

	// 6. Branch operations
	if _, err := CheckoutBranch(tempDir, "feature/demo", true); err != nil {
		t.Fatalf("create branch failed: %v", err)
	}
	branches, err := ListBranches(tempDir)
	if err != nil {
		t.Fatalf("ListBranches failed: %v", err)
	}
	if len(branches) < 2 {
		t.Errorf("expected at least 2 branches, got %d", len(branches))
	}
}
