package config

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"math/big"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

// Config represents persistent desktop settings.
type Config struct {
	Host             string `json:"host"`
	Port             int    `json:"port"`
	Token            string `json:"token"`
	PairCode         string `json:"pairCode"`
	DefaultWorkspace string `json:"defaultWorkspace"`
	RelayURL         string `json:"relayUrl"`
	AutoStart        bool   `json:"autoStart"`
	TailcatEnabled   bool   `json:"tailcatEnabled"`
}

// GeneratePairCode generates a random 6-digit pair code.
func GeneratePairCode() string {
	n, err := rand.Int(rand.Reader, big.NewInt(900000))
	if err != nil {
		return "839102"
	}
	return fmt.Sprintf("%06d", n.Int64()+100000)
}

// DefaultConfig returns default configuration values.
func DefaultConfig() *Config {
	home, _ := os.UserHomeDir()
	return &Config{
		Host:             "0.0.0.0",
		Port:             18980,
		Token:            generateToken(16),
		PairCode:         GeneratePairCode(),
		DefaultWorkspace: home,
		RelayURL:         "https://relay.mircode.dev",
		AutoStart:        false,
		TailcatEnabled:   true,
	}
}

// ConfigDir returns the path to the configuration directory.
func ConfigDir() string {
	var dir string
	if runtime.GOOS == "windows" {
		dir = filepath.Join(os.Getenv("APPDATA"), "mircode")
	} else {
		home, _ := os.UserHomeDir()
		dir = filepath.Join(home, ".config", "mircode")
	}
	_ = os.MkdirAll(dir, 0755)
	return dir
}

// ConfigFilePath returns the path to config.json.
func ConfigFilePath() string {
	return filepath.Join(ConfigDir(), "config.json")
}

// Load reads the config file or returns defaults if not present.
func Load() (*Config, error) {
	path := ConfigFilePath()
	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			cfg := DefaultConfig()
			_ = Save(cfg)
			return cfg, nil
		}
		return nil, err
	}

	cfg := DefaultConfig()
	if err := json.Unmarshal(data, cfg); err != nil {
		return nil, err
	}

	needsSave := false
	if cfg.Token == "" {
		cfg.Token = generateToken(16)
		needsSave = true
	}
	if cfg.PairCode == "" {
		cfg.PairCode = GeneratePairCode()
		needsSave = true
	}
	if needsSave {
		_ = Save(cfg)
	}

	// Sync AutoStart state with OS
	cfg.AutoStart = IsAutoStartEnabled()
	return cfg, nil
}

// ResetCredentials manually resets the token and pair code.
func ResetCredentials() (*Config, error) {
	cfg, err := Load()
	if err != nil {
		cfg = DefaultConfig()
	}
	cfg.Token = generateToken(16)
	cfg.PairCode = GeneratePairCode()
	if err := Save(cfg); err != nil {
		return nil, err
	}
	return cfg, nil
}

// Save writes the config to disk atomically.
func Save(cfg *Config) error {
	path := ConfigFilePath()
	dir := filepath.Dir(path)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return err
	}

	data, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return err
	}

	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, data, 0644); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}

// IsAutoStartEnabled checks if mircode is configured to start on boot.
func IsAutoStartEnabled() bool {
	if runtime.GOOS == "linux" {
		out, err := exec.Command("systemctl", "--user", "is-enabled", "mircode").Output()
		if err == nil && strings.TrimSpace(string(out)) == "enabled" {
			return true
		}
		return false
	} else if runtime.GOOS == "windows" {
		appData := os.Getenv("APPDATA")
		shortcut := filepath.Join(appData, "Microsoft", "Windows", "Start Menu", "Programs", "Startup", "mircode.cmd")
		_, err := os.Stat(shortcut)
		return err == nil
	}
	return false
}

// SetAutoStart enables or disables systemd or windows startup.
func SetAutoStart(enable bool) error {
	execPath, err := os.Executable()
	if err != nil {
		execPath = "mircode"
	}

	if runtime.GOOS == "linux" {
		home, _ := os.UserHomeDir()
		userSystemdDir := filepath.Join(home, ".config", "systemd", "user")
		_ = os.MkdirAll(userSystemdDir, 0755)
		servicePath := filepath.Join(userSystemdDir, "mircode.service")

		if enable {
			serviceContent := fmt.Sprintf(`[Unit]
Description=MirCode Companion Daemon
After=network.target

[Service]
Type=simple
ExecStart=%s
Restart=on-failure
RestartSec=3s
Environment=PATH=/usr/local/bin:/usr/bin:/bin

[Install]
WantedBy=default.target
`, execPath)
			if err := os.WriteFile(servicePath, []byte(serviceContent), 0644); err != nil {
				return err
			}
			_ = exec.Command("systemctl", "--user", "daemon-reload").Run()
			return exec.Command("systemctl", "--user", "enable", "mircode").Run()
		} else {
			_ = exec.Command("systemctl", "--user", "disable", "mircode").Run()
			_ = os.Remove(servicePath)
			_ = exec.Command("systemctl", "--user", "daemon-reload").Run()
			return nil
		}
	} else if runtime.GOOS == "windows" {
		appData := os.Getenv("APPDATA")
		startupDir := filepath.Join(appData, "Microsoft", "Windows", "Start Menu", "Programs", "Startup")
		_ = os.MkdirAll(startupDir, 0755)
		shortcut := filepath.Join(startupDir, "mircode.cmd")

		if enable {
			cmdContent := fmt.Sprintf("@echo off\r\nstart \"\" \"%s\"\r\n", execPath)
			return os.WriteFile(shortcut, []byte(cmdContent), 0644)
		} else {
			return os.Remove(shortcut)
		}
	}
	return fmt.Errorf("auto-start not supported on %s", runtime.GOOS)
}

// IsServiceRunning checks if mircode companion is currently active.
func IsServiceRunning(port int) bool {
	if runtime.GOOS == "linux" {
		out, err := exec.Command("systemctl", "--user", "is-active", "mircode").Output()
		if err == nil && strings.TrimSpace(string(out)) == "active" {
			return true
		}
	}

	// Also check port connectivity directly
	addr := fmt.Sprintf("127.0.0.1:%d", port)
	conn, err := net.DialTimeout("tcp", addr, 200*time.Millisecond)
	if err == nil {
		_ = conn.Close()
		return true
	}
	return false
}

// ServiceAction controls the background service (start, stop, restart).
func ServiceAction(action string) error {
	if runtime.GOOS == "linux" {
		switch action {
		case "start":
			return exec.Command("systemctl", "--user", "start", "mircode").Run()
		case "stop":
			return exec.Command("systemctl", "--user", "stop", "mircode").Run()
		case "restart":
			return exec.Command("systemctl", "--user", "restart", "mircode").Run()
		}
	}
	return fmt.Errorf("service action '%s' not implemented on %s", action, runtime.GOOS)
}

func generateToken(byteLen int) string {
	b := make([]byte, byteLen)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}
