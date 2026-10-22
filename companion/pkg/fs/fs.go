package fs

import (
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"
)

// Entry represents a file or directory item.
type Entry struct {
	Name      string    `json:"name"`
	Path      string    `json:"path"`
	IsDir     bool      `json:"isDir"`
	IsGitRepo bool      `json:"isGitRepo"`
	Size      int64     `json:"size"`
	ModTime   time.Time `json:"modTime"`
}

// BrowseResult contains list of entries and directory navigation metadata.
type BrowseResult struct {
	CurrentPath string  `json:"currentPath"`
	ParentPath  string  `json:"parentPath"`
	HomePath    string  `json:"homePath"`
	Entries     []Entry `json:"entries"`
}

// Browse lists entries in the given target directory.
// If target is empty, it defaults to the user's home directory.
func Browse(target string) (*BrowseResult, error) {
	homeDir, err := os.UserHomeDir()
	if err != nil {
		homeDir = "/"
	}

	if target == "" || target == "~" {
		target = homeDir
	}

	target = filepath.Clean(target)
	info, err := os.Stat(target)
	if err != nil {
		return nil, fmt.Errorf("path error: %w", err)
	}

	if !info.IsDir() {
		return nil, fmt.Errorf("target is not a directory: %s", target)
	}

	dirEntries, err := os.ReadDir(target)
	if err != nil {
		return nil, fmt.Errorf("read dir error: %w", err)
	}

	var entries []Entry
	for _, de := range dirEntries {
		name := de.Name()
		// Skip hidden files/directories except .git (for detection)
		if strings.HasPrefix(name, ".") && name != ".git" {
			continue
		}

		fullPath := filepath.Join(target, name)
		fi, err := de.Info()
		if err != nil {
			continue
		}

		isDir := de.IsDir()
		isGit := false
		if isDir {
			// Check if this directory is a git repository
			gitDir := filepath.Join(fullPath, ".git")
			if _, err := os.Stat(gitDir); err == nil {
				isGit = true
			}
		}

		entries = append(entries, Entry{
			Name:      name,
			Path:      fullPath,
			IsDir:     isDir,
			IsGitRepo: isGit,
			Size:      fi.Size(),
			ModTime:   fi.ModTime(),
		})
	}

	// Sort directories first, then alphabetically
	sort.Slice(entries, func(i, j int) bool {
		if entries[i].IsDir != entries[j].IsDir {
			return entries[i].IsDir
		}
		return strings.ToLower(entries[i].Name) < strings.ToLower(entries[j].Name)
	})

	parent := filepath.Dir(target)
	if parent == target {
		parent = ""
	}

	return &BrowseResult{
		CurrentPath: target,
		ParentPath:  parent,
		HomePath:    homeDir,
		Entries:     entries,
	}, nil
}

// ReadFile reads the full text of a file within a workspace.
func ReadFile(workspace, relPath string) (string, error) {
	absPath, err := safeResolve(workspace, relPath)
	if err != nil {
		return "", err
	}

	data, err := os.ReadFile(absPath)
	if err != nil {
		return "", err
	}

	return string(data), nil
}

// WriteFile writes full text content to a file safely.
func WriteFile(workspace, relPath, content string) error {
	absPath, err := safeResolve(workspace, relPath)
	if err != nil {
		return err
	}

	dir := filepath.Dir(absPath)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return err
	}

	tmpFile := absPath + ".tmp"
	if err := os.WriteFile(tmpFile, []byte(content), 0644); err != nil {
		return err
	}
	return os.Rename(tmpFile, absPath)
}

func safeResolve(workspace, relPath string) (string, error) {
	cleanRel := filepath.Clean(relPath)
	if strings.HasPrefix(cleanRel, "..") {
		return "", fmt.Errorf("illegal relative path traversing outside workspace")
	}

	absPath := filepath.Join(workspace, cleanRel)
	return absPath, nil
}
