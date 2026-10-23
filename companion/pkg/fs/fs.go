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
// If target is empty, it defaults to defaultDir (e.g. active workspace) or user home.
func Browse(target, defaultDir string) (*BrowseResult, error) {
	homeDir, err := os.UserHomeDir()
	if err != nil {
		homeDir = "/"
	}

	if target == "" || target == "~" || target == "." {
		if defaultDir != "" {
			target = defaultDir
		} else {
			target = homeDir
		}
	} else if !filepath.IsAbs(target) && defaultDir != "" {
		target = filepath.Join(defaultDir, target)
	}

	target = filepath.Clean(target)
	info, err := os.Stat(target)
	if err != nil {
		if defaultDir != "" && target != defaultDir {
			target = defaultDir
			info, err = os.Stat(target)
		}
		if err != nil {
			return nil, fmt.Errorf("path error: %w", err)
		}
	}

	if !info.IsDir() {
		return nil, fmt.Errorf("target is not a directory: %s", target)
	}

	dirEntries, err := os.ReadDir(target)
	if err != nil {
		return nil, fmt.Errorf("read dir error: %w", err)
	}

	entries := make([]Entry, 0)
	for _, de := range dirEntries {
		name := de.Name()
		// Only skip internal .git metadata directory; allow developer files like .github, .gitignore, .env
		if name == ".git" {
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

func safeResolve(workspace, pathStr string) (string, error) {
	if strings.TrimSpace(pathStr) == "" {
		return "", fmt.Errorf("empty path")
	}

	cleanPath := filepath.Clean(pathStr)

	// Case 1: Already an absolute path
	if filepath.IsAbs(cleanPath) {
		rel, err := filepath.Rel(workspace, cleanPath)
		if err == nil && !strings.HasPrefix(rel, "..") {
			return cleanPath, nil
		}
		evalWs, err1 := filepath.EvalSymlinks(workspace)
		evalTarget, err2 := filepath.EvalSymlinks(cleanPath)
		if err1 == nil && err2 == nil {
			rel2, err := filepath.Rel(evalWs, evalTarget)
			if err == nil && !strings.HasPrefix(rel2, "..") {
				return evalTarget, nil
			}
		}
		if fi, err := os.Stat(cleanPath); err == nil && !fi.IsDir() {
			return cleanPath, nil
		}
		return cleanPath, nil
	}

	// Case 2: Relative path within workspace
	relPath := strings.TrimLeft(cleanPath, "/\\")
	if strings.HasPrefix(relPath, "..") {
		return "", fmt.Errorf("illegal relative path traversing outside workspace")
	}

	absPath := filepath.Join(workspace, relPath)
	return absPath, nil
}
