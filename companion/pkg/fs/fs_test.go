package fs

import (
	"os"
	"path/filepath"
	"testing"
)

func TestBrowseAndFileReadWrite(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "mircode-fs-test-*")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(tempDir)

	subDir := filepath.Join(tempDir, "subproject")
	_ = os.MkdirAll(filepath.Join(subDir, ".git"), 0755)

	testFile := filepath.Join(tempDir, "hello.txt")
	_ = os.WriteFile(testFile, []byte("Hello MirCode!"), 0644)

	// Browse test
	res, err := Browse(tempDir)
	if err != nil {
		t.Fatalf("Browse failed: %v", err)
	}

	if len(res.Entries) != 2 {
		t.Errorf("expected 2 entries, got %d", len(res.Entries))
	}

	var foundSub, foundFile bool
	for _, e := range res.Entries {
		if e.Name == "subproject" {
			foundSub = true
			if !e.IsDir || !e.IsGitRepo {
				t.Errorf("subproject should be dir and git repo")
			}
		}
		if e.Name == "hello.txt" {
			foundFile = true
			if e.IsDir {
				t.Errorf("hello.txt should not be dir")
			}
		}
	}
	if !foundSub || !foundFile {
		t.Errorf("missing expected entries in browse result")
	}

	// Read and Write test
	content, err := ReadFile(tempDir, "hello.txt")
	if err != nil || content != "Hello MirCode!" {
		t.Errorf("ReadFile mismatch: %v, got %q", err, content)
	}

	err = WriteFile(tempDir, "nested/path/code.go", "package main\n")
	if err != nil {
		t.Fatalf("WriteFile failed: %v", err)
	}

	written, err := ReadFile(tempDir, "nested/path/code.go")
	if err != nil || written != "package main\n" {
		t.Errorf("written content mismatch: %v, got %q", err, written)
	}
}
