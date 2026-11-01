# Windows: safe signed APK handover. Keep stock Home available and optimize only after profile review.
param([string]$TvHost = $env:TV_HOST)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'Pass -TvHost <TV IP address> or set TV_HOST.' }
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$apk = Join-Path $root 'CleanYourTV-Scenic.apk'
if (-not (Test-Path $apk)) { throw 'Extract the full Setup ZIP before running.' }
$env:TV_HOST = $TvHost
Push-Location $root
try {
    $py = 'tools\venv\Scripts\python.exe'
    if (-not (Test-Path $py)) { python -m venv tools\venv }
    & $py -m pip -q install -r tools\requirements.txt
    & $py -I tools\tv.py --probe
    $fingerprint = (& $py -I tools\tv.py 'getprop ro.build.fingerprint' | Select-Object -Last 1).Trim()
    if (-not $fingerprint) { throw 'Firmware fingerprint unavailable; no changes made.' }
    $home = (& $py -I tools\tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | Select-Object -Last 1).Trim()
    New-Item -ItemType Directory -Force recovery | Out-Null
    $home | Set-Content recovery\home-before-install.txt
    & $py -I tools\tv.py 'pm list packages -d' | Set-Content recovery\packages-disabled-before-install.txt
    Write-Host "TV firmware: $fingerprint; original Home: $home"
    Write-Host 'Installing signed launcher; leaving stock Home enabled.'
    & launcher\build-and-install.ps1 -TvHost $TvHost -StockLauncher none -PrebuiltApk $apk
    if (-not $?) { throw 'Home install failed; original stock Home was not disabled.' }
    Write-Host 'The optional upstream optimizations require an exact firmware profile and separate confirmation.'
    Write-Host 'Review examples\<model>\debloat.txt, then tools\disable.ps1 or tools\restore.ps1.'
    Write-Host 'Turn off ADB debugging after validation.'
} finally { Pop-Location }
