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
    # Keep the Python environment and ADB identity between newly extracted release ZIPs.
    if ($env:LOCALAPPDATA) {
        $stateDir = Join-Path $env:LOCALAPPDATA 'CleanYourTV'
        $venvDir = Join-Path $stateDir 'venv'
        $env:CLEANYOURTV_ADB_KEYDIR = Join-Path $stateDir 'adbkey'
    } else {
        $venvDir = 'tools\venv'
    }
    $py = Join-Path $venvDir 'Scripts\python.exe'
    if (-not (Test-Path $py)) {
        if (Get-Command py.exe -ErrorAction SilentlyContinue) { & py.exe -3 -m venv $venvDir }
        elseif (Get-Command python.exe -ErrorAction SilentlyContinue) { & python.exe -m venv $venvDir }
        else { throw 'Python 3.10+ not found. Install Python, then run Setup again.' }
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path $py)) { throw 'Python environment setup failed; TV unchanged.' }
    }
    & $py -m pip -q install -r tools\requirements.txt
    if ($LASTEXITCODE -ne 0) { throw 'Python dependencies could not be installed; TV unchanged.' }
    $env:CLEANYOURTV_PYTHON = (Resolve-Path $py).Path
    & $py -I tools\tv.py --probe
    if ($LASTEXITCODE -ne 0) { throw "TV $TvHost unreachable or ADB not authorized; TV unchanged." }
    $fingerprintResult = @(& $py -I tools\tv.py 'getprop ro.build.fingerprint')
    if ($LASTEXITCODE -ne 0 -or $fingerprintResult.Count -eq 0) {
        throw 'Firmware fingerprint unavailable; no changes made.'
    }
    $fingerprint = "$($fingerprintResult[-1])".Trim()
    if (-not $fingerprint) { throw 'Firmware fingerprint unavailable; no changes made.' }
    $homeResult = @(& $py -I tools\tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME')
    if ($LASTEXITCODE -ne 0 -or $homeResult.Count -eq 0) {
        throw 'Original Home unavailable; no changes made.'
    }
    $originalHome = "$($homeResult[-1])".Trim()
    if (-not $originalHome) { throw 'Original Home unavailable; no changes made.' }
    New-Item -ItemType Directory -Force recovery | Out-Null
    $originalHome | Set-Content recovery\home-before-install.txt
    & $py -I tools\tv.py 'pm list packages -d' | Set-Content recovery\packages-disabled-before-install.txt
    if ($LASTEXITCODE -ne 0) { throw 'Could not record disabled packages; TV unchanged.' }
    if ($env:LOCALAPPDATA) {
        $archive = Join-Path $stateDir ('recovery\' + ($TvHost -replace '[^A-Za-z0-9.-]', '_') + '_' + (Get-Date -Format 'yyyyMMdd_HHmmss'))
        New-Item -ItemType Directory -Path $archive -Force | Out-Null
        Copy-Item recovery\home-before-install.txt,recovery\packages-disabled-before-install.txt -Destination $archive
        Write-Host "Recovery copy: $archive"
    }
    Write-Host "TV firmware: $fingerprint; original Home: $originalHome"
    Write-Host 'Installing signed launcher; leaving stock Home enabled.'
    & launcher\build-and-install.ps1 -TvHost $TvHost -StockLauncher none -PrebuiltApk $apk
    if (-not $?) { throw 'Home install failed; original stock Home was not disabled.' }
    Write-Host 'The optional upstream optimizations require an exact firmware profile and separate confirmation.'
    Write-Host 'Review examples\<model>\debloat.txt, then tools\disable.ps1 or tools\restore.ps1.'
    Write-Host 'Turn off ADB debugging after validation.'
} finally {
    Remove-Item Env:\CLEANYOURTV_PYTHON -ErrorAction SilentlyContinue
    Pop-Location
}
