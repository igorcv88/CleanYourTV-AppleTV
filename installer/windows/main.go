// CleanYourTV-Setup.exe is a small native Windows console entrypoint. It launches
// the adjacent, inspectable PowerShell installer and never requires elevation.
package main

import (
	"bufio"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
)

func installerScript(exePath string) string {
	return filepath.Join(filepath.Dir(exePath), "installer", "interactive.ps1")
}

func main() {
	exePath, err := os.Executable()
	if err != nil {
		fmt.Fprintln(os.Stderr, "Cannot locate Setup.exe:", err)
		finish(1)
	}
	script := installerScript(exePath)
	if _, err := os.Stat(script); err != nil {
		fmt.Fprintln(os.Stderr, "Extract the complete Setup ZIP before running this executable.")
		finish(1)
	}
	args := []string{"-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script}
	args = append(args, os.Args[1:]...)
	cmd := exec.Command("powershell.exe", args...)
	cmd.Dir = filepath.Dir(exePath)
	cmd.Stdin, cmd.Stdout, cmd.Stderr = os.Stdin, os.Stdout, os.Stderr
	if err := cmd.Run(); err != nil {
		fmt.Fprintln(os.Stderr, "Setup failed:", err)
		finish(1)
	}
	finish(0)
}

func finish(code int) {
	fmt.Println("\nPressione Enter para fechar esta janela.")
	_, _ = bufio.NewReader(os.Stdin).ReadString('\n')
	os.Exit(code)
}
