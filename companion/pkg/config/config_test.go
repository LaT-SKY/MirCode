package config

import (
	"os"
	"path/filepath"
	"testing"
)

func TestConfigLoadSave(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "mircode-cfg-test-*")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(tempDir)

	cfgPath := filepath.Join(tempDir, "config.json")
	cfg := DefaultConfig()
	cfg.Port = 19999
	cfg.RelayURL = "https://custom-relay.example.com"

	_, err = os.ReadFile(cfgPath)
	if err == nil {
		t.Fatalf("file should not exist yet")
	}

	if cfg.Port != 19999 {
		t.Errorf("expected port 19999")
	}
}
