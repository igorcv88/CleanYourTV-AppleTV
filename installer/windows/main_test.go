package main

import (
	"path/filepath"
	"testing"
)

func TestInstallerScriptNextToExecutable(t *testing.T) {
	got := installerScript(filepath.Join("C:", "Users", "Igor", "Setup with spaces", "CleanYourTV-Setup.exe"))
	want := filepath.Join("C:", "Users", "Igor", "Setup with spaces", "installer", "interactive.ps1")
	if got != want {
		t.Fatalf("script path = %q, want %q", got, want)
	}
}
