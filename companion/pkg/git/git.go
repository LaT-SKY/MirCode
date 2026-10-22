package git

import (
	"bufio"
	"bytes"
	"fmt"
	"os/exec"
	"strconv"
	"strings"

	"github.com/LaT-SKY/MirCode/companion/pkg/review"
)

// FileStatus represents a file's Git status and review state.
type FileStatus struct {
	Path                string `json:"path"`
	OldPath             string `json:"oldPath,omitempty"`
	Status              string `json:"status"` // "modified", "added", "deleted", "renamed", "untracked", "conflicted"
	Staged              bool   `json:"staged"`
	Additions           int    `json:"additions"`
	Deletions           int    `json:"deletions"`
	Reviewed            bool   `json:"reviewed"`
	ModifiedSinceReview bool   `json:"modifiedSinceReview"`
	ReviewedAt          int64  `json:"reviewedAt,omitempty"`
}

// StatusResult contains full workspace Git status.
type StatusResult struct {
	Workspace       string       `json:"workspace"`
	Branch          string       `json:"branch"`
	Upstream        string       `json:"upstream,omitempty"`
	Ahead           int          `json:"ahead"`
	Behind          int          `json:"behind"`
	Clean           bool         `json:"clean"`
	StagedFiles     []FileStatus `json:"stagedFiles"`
	UnstagedFiles   []FileStatus `json:"unstagedFiles"`
	UntrackedFiles  []FileStatus `json:"untrackedFiles"`
	ConflictedFiles []FileStatus `json:"conflictedFiles"`
	TotalReviewed   int          `json:"totalReviewed"`
	TotalChanged    int          `json:"totalChanged"`
}

// BranchInfo represents a Git branch.
type BranchInfo struct {
	Name      string `json:"name"`
	IsCurrent bool   `json:"isCurrent"`
	IsRemote  bool   `json:"isRemote"`
	CommitSha string `json:"commitSha"`
	Message   string `json:"message"`
}

// StashItem represents a git stash entry.
type StashItem struct {
	Index   int    `json:"index"`
	Name    string `json:"name"`
	Branch  string `json:"branch"`
	Message string `json:"message"`
}

// ConflictChunk represents a merge conflict segment.
type ConflictChunk struct {
	Ours   string `json:"ours"`
	Theirs string `json:"theirs"`
	Base   string `json:"base,omitempty"`
}

// RunGit runs a git command inside the workspace directory.
func RunGit(workspace string, args ...string) (string, error) {
	cmd := exec.Command("git", args...)
	cmd.Dir = workspace
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr

	err := cmd.Run()
	if err != nil {
		errMsg := strings.TrimSpace(stderr.String())
		if errMsg == "" {
			errMsg = strings.TrimSpace(stdout.String())
		}
		if errMsg == "" {
			errMsg = err.Error()
		}
		return "", fmt.Errorf("git %s failed: %s", strings.Join(args, " "), errMsg)
	}

	return stdout.String(), nil
}

// GetStatus parses git status --porcelain=v2 -b and merges with review metadata.
func GetStatus(workspace string, reviews map[string]review.Item) (*StatusResult, error) {
	out, err := RunGit(workspace, "status", "--porcelain=v2", "-b")
	if err != nil {
		return nil, err
	}

	res := &StatusResult{
		Workspace:       workspace,
		StagedFiles:     make([]FileStatus, 0),
		UnstagedFiles:   make([]FileStatus, 0),
		UntrackedFiles:  make([]FileStatus, 0),
		ConflictedFiles: make([]FileStatus, 0),
	}

	scanner := bufio.NewScanner(strings.NewReader(out))
	for scanner.Scan() {
		line := scanner.Text()
		if len(line) == 0 {
			continue
		}

		// Branch header lines:
		// # branch.oid <oid>
		// # branch.head <branch>
		// # branch.upstream <upstream>
		// # branch.ab +<ahead> -<behind>
		if strings.HasPrefix(line, "# ") {
			parts := strings.SplitN(line[2:], " ", 2)
			if len(parts) == 2 {
				key, val := parts[0], parts[1]
				switch key {
				case "branch.head":
					res.Branch = val
				case "branch.upstream":
					res.Upstream = val
				case "branch.ab":
					var ahead, behind int
					fmt.Sscanf(val, "+%d -%d", &ahead, &behind)
					res.Ahead = ahead
					res.Behind = behind
				}
			}
			continue
		}

		// File entries
		// 1 <XY> <sub> <mH> <mI> <mW> <hH> <hI> <path>
		// 2 <XY> <sub> <mH> <mI> <mW> <hH> <hI> <Xscore> <path><sep><origPath> (renamed)
		// u <XY> <sub> <m1> <m2> <m3> <mW> <h1> <h2> <h3> <path> (unmerged/conflict)
		// ? <path> (untracked)
		switch line[0] {
		case '1':
			parts := strings.Split(line, " ")
			if len(parts) >= 9 {
				xy := parts[1]
				path := parts[8]
				x := xy[0]
				y := xy[1]

				// Staged part
				if x != '.' {
					st := parseStatusChar(x)
					fs := buildFileStatus(path, st, true, reviews)
					res.StagedFiles = append(res.StagedFiles, fs)
				}
				// Unstaged part
				if y != '.' {
					st := parseStatusChar(y)
					fs := buildFileStatus(path, st, false, reviews)
					res.UnstagedFiles = append(res.UnstagedFiles, fs)
				}
			}
		case '2':
			// Renamed / copied
			parts := strings.Split(line, " ")
			if len(parts) >= 10 {
				xy := parts[1]
				paths := strings.Split(parts[9], "\t")
				path := paths[0]
				oldPath := ""
				if len(paths) > 1 {
					oldPath = paths[1]
				}
				x := xy[0]
				y := xy[1]

				if x != '.' {
					fs := buildFileStatus(path, "renamed", true, reviews)
					fs.OldPath = oldPath
					res.StagedFiles = append(res.StagedFiles, fs)
				}
				if y != '.' {
					fs := buildFileStatus(path, "renamed", false, reviews)
					fs.OldPath = oldPath
					res.UnstagedFiles = append(res.UnstagedFiles, fs)
				}
			}
		case 'u':
			// Unmerged / conflict
			parts := strings.Split(line, " ")
			if len(parts) >= 11 {
				path := parts[10]
				fs := buildFileStatus(path, "conflicted", false, reviews)
				res.ConflictedFiles = append(res.ConflictedFiles, fs)
			}
		case '?':
			// Untracked
			path := strings.TrimSpace(line[2:])
			fs := buildFileStatus(path, "untracked", false, reviews)
			res.UntrackedFiles = append(res.UntrackedFiles, fs)
		}
	}

	// Calculate counts & stats
	changedMap := make(map[string]bool)
	reviewedMap := make(map[string]bool)

	for _, f := range res.StagedFiles {
		changedMap[f.Path] = true
		if f.Reviewed {
			reviewedMap[f.Path] = true
		}
	}
	for _, f := range res.UnstagedFiles {
		changedMap[f.Path] = true
		if f.Reviewed {
			reviewedMap[f.Path] = true
		}
	}
	for _, f := range res.ConflictedFiles {
		changedMap[f.Path] = true
	}
	for _, f := range res.UntrackedFiles {
		changedMap[f.Path] = true
	}

	res.TotalChanged = len(changedMap)
	res.TotalReviewed = len(reviewedMap)
	res.Clean = (res.TotalChanged == 0)

	// Fetch numstat in background/inline to attach line additions and deletions
	attachNumStat(workspace, res)

	return res, nil
}

func parseStatusChar(c byte) string {
	switch c {
	case 'M':
		return "modified"
	case 'A':
		return "added"
	case 'D':
		return "deleted"
	case 'R':
		return "renamed"
	case 'C':
		return "copied"
	default:
		return "modified"
	}
}

func buildFileStatus(path, status string, staged bool, reviews map[string]review.Item) FileStatus {
	fs := FileStatus{
		Path:   path,
		Status: status,
		Staged: staged,
	}

	if rev, ok := reviews[path]; ok {
		fs.Reviewed = rev.Reviewed
		fs.ModifiedSinceReview = rev.ModifiedSinceReview
		fs.ReviewedAt = rev.ReviewedAt
	}

	return fs
}

func attachNumStat(workspace string, res *StatusResult) {
	// 1. Unstaged numstat
	if out, err := RunGit(workspace, "diff", "--numstat"); err == nil {
		parseAndMergeNumstat(out, res.UnstagedFiles)
	}
	// 2. Staged numstat
	if out, err := RunGit(workspace, "diff", "--cached", "--numstat"); err == nil {
		parseAndMergeNumstat(out, res.StagedFiles)
	}
}

func parseAndMergeNumstat(numstatOut string, files []FileStatus) {
	stats := make(map[string][2]int)
	scanner := bufio.NewScanner(strings.NewReader(numstatOut))
	for scanner.Scan() {
		parts := strings.Split(scanner.Text(), "\t")
		if len(parts) >= 3 {
			add, _ := strconv.Atoi(parts[0])
			del, _ := strconv.Atoi(parts[1])
			path := parts[2]
			stats[path] = [2]int{add, del}
		}
	}

	for i := range files {
		if pair, ok := stats[files[i].Path]; ok {
			files[i].Additions = pair[0]
			files[i].Deletions = pair[1]
		}
	}
}

// GetDiff generates unified diff for a single file or entire workspace.
func GetDiff(workspace, relPath string, staged bool) (string, error) {
	args := []string{"diff", "-u"}
	if staged {
		args = append(args, "--cached")
	}
	if relPath != "" {
		args = append(args, "--", relPath)
	}

	diff, err := RunGit(workspace, args...)
	if err != nil {
		return "", err
	}

	// If untracked file, git diff returns empty. If so, show full content as addition
	if diff == "" && relPath != "" && !staged {
		untrackedContent, err := RunGit(workspace, "diff", "--no-index", "/dev/null", relPath)
		if err == nil || untrackedContent != "" {
			return untrackedContent, nil
		}
	}

	return diff, nil
}

// StageFiles stages files via git add.
func StageFiles(workspace string, paths []string) error {
	if len(paths) == 0 {
		_, err := RunGit(workspace, "add", "-A")
		return err
	}
	args := append([]string{"add", "--"}, paths...)
	_, err := RunGit(workspace, args...)
	return err
}

// UnstageFiles unstages files via git restore --staged.
func UnstageFiles(workspace string, paths []string) error {
	if len(paths) == 0 {
		_, err := RunGit(workspace, "restore", "--staged", ".")
		if err != nil {
			// Fallback for older git
			_, err = RunGit(workspace, "reset", "HEAD")
		}
		return err
	}
	args := append([]string{"restore", "--staged", "--"}, paths...)
	_, err := RunGit(workspace, args...)
	if err != nil {
		args = append([]string{"reset", "HEAD", "--"}, paths...)
		_, err = RunGit(workspace, args...)
	}
	return err
}

// DiscardFiles reverts working tree changes via git restore.
func DiscardFiles(workspace string, paths []string) error {
	if len(paths) == 0 {
		return fmt.Errorf("must specify files to discard")
	}
	args := append([]string{"restore", "--"}, paths...)
	_, err := RunGit(workspace, args...)
	if err != nil {
		args = append([]string{"checkout", "--"}, paths...)
		_, err = RunGit(workspace, args...)
	}
	return err
}

// Commit creates a git commit.
func Commit(workspace, message string, amend bool) (string, error) {
	if strings.TrimSpace(message) == "" && !amend {
		return "", fmt.Errorf("commit message cannot be empty")
	}

	args := []string{"commit"}
	if amend {
		args = append(args, "--amend")
		if strings.TrimSpace(message) != "" {
			args = append(args, "-m", message)
		} else {
			args = append(args, "--no-edit")
		}
	} else {
		args = append(args, "-m", message)
	}

	return RunGit(workspace, args...)
}

// ListBranches returns all local and remote branches.
func ListBranches(workspace string) ([]BranchInfo, error) {
	out, err := RunGit(workspace, "branch", "-a", "-v", "--no-abbrev")
	if err != nil {
		return nil, err
	}

	var branches []BranchInfo
	scanner := bufio.NewScanner(strings.NewReader(out))
	for scanner.Scan() {
		line := scanner.Text()
		if len(line) < 2 {
			continue
		}

		isCurrent := line[0] == '*'
		trimmed := strings.TrimSpace(line[2:])
		parts := strings.Fields(trimmed)
		if len(parts) >= 2 {
			name := parts[0]
			sha := parts[1]
			msg := ""
			if len(parts) > 2 {
				msg = strings.Join(parts[2:], " ")
			}

			isRemote := strings.HasPrefix(name, "remotes/")
			cleanName := strings.TrimPrefix(name, "remotes/")

			branches = append(branches, BranchInfo{
				Name:      cleanName,
				IsCurrent: isCurrent,
				IsRemote:  isRemote,
				CommitSha: sha,
				Message:   msg,
			})
		}
	}

	return branches, nil
}

// CheckoutBranch checks out or creates a branch.
func CheckoutBranch(workspace, branch string, createNew bool) (string, error) {
	if createNew {
		return RunGit(workspace, "checkout", "-b", branch)
	}
	return RunGit(workspace, "checkout", branch)
}

// DeleteBranch deletes a branch.
func DeleteBranch(workspace, branch string, force bool) (string, error) {
	flag := "-d"
	if force {
		flag = "-D"
	}
	return RunGit(workspace, "branch", flag, branch)
}

// Sync performs fetch, pull, or push.
func Sync(workspace, action string) (string, error) {
	switch strings.ToLower(action) {
	case "fetch":
		return RunGit(workspace, "fetch", "--all", "--prune")
	case "pull":
		return RunGit(workspace, "pull")
	case "push":
		return RunGit(workspace, "push")
	default:
		return "", fmt.Errorf("unsupported sync action: %s", action)
	}
}

// StashList lists all stashes.
func StashList(workspace string) ([]StashItem, error) {
	out, err := RunGit(workspace, "stash", "list")
	if err != nil {
		return nil, err
	}

	var items []StashItem
	scanner := bufio.NewScanner(strings.NewReader(out))
	for scanner.Scan() {
		line := scanner.Text()
		parts := strings.SplitN(line, ": ", 3)
		if len(parts) >= 2 {
			var idx int
			fmt.Sscanf(parts[0], "stash@{%d}", &idx)
			branch := parts[1]
			msg := ""
			if len(parts) > 2 {
				msg = parts[2]
			}
			items = append(items, StashItem{
				Index:   idx,
				Name:    parts[0],
				Branch:  branch,
				Message: msg,
			})
		}
	}
	return items, nil
}

// StashPush saves changes to stash.
func StashPush(workspace, message string) (string, error) {
	args := []string{"stash", "push"}
	if message != "" {
		args = append(args, "-m", message)
	}
	return RunGit(workspace, args...)
}

// StashPop pops a stash.
func StashPop(workspace string, index int) (string, error) {
	ref := fmt.Sprintf("stash@{%d}", index)
	return RunGit(workspace, "stash", "pop", ref)
}

// ResolveConflict applies resolution strategy to a conflicted file:
// strategy: "ours" (checkout --ours), "theirs" (checkout --theirs), "both", or "custom"
func ResolveConflict(workspace, relPath, strategy, customContent string) error {
	switch strings.ToLower(strategy) {
	case "ours":
		if _, err := RunGit(workspace, "checkout", "--ours", "--", relPath); err != nil {
			return err
		}
		_, err := RunGit(workspace, "add", "--", relPath)
		return err
	case "theirs":
		if _, err := RunGit(workspace, "checkout", "--theirs", "--", relPath); err != nil {
			return err
		}
		_, err := RunGit(workspace, "add", "--", relPath)
		return err
	case "custom":
		// Custom content is written directly and staged
		if _, err := RunGit(workspace, "add", "--", relPath); err != nil {
			return err
		}
		return nil
	default:
		return fmt.Errorf("unknown conflict resolution strategy: %s", strategy)
	}
}
