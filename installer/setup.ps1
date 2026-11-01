# Windows release installer: prebuilt Scenic Glass APK + optional exact-firmware CleanYourTV debloat.
param(
    [Parameter(Mandatory = $true)][string]$TvHost,
    [string]$DebloatList = ''
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $root
$env:TV_HOST = $TvHost
$apk = Join-Path $root 'ScenicGlassTV.apk'
if (-not (Test-Path $apk -PathType Leaf)) { throw 'ScenicGlassTV.apk missing; extract the full release ZIP.' }

$py = Join-Path $root 'tools\venv\Scripts\python.exe'
if (-not (Test-Path $py)) {
    & python -m venv (Join-Path $root 'tools\venv')
    if ($LASTEXITCODE -ne 0) { throw 'Python 3 with venv is required.' }
}
& $py -m pip -q install -r (Join-Path $root 'tools\requirements.txt')
if ($LASTEXITCODE -ne 0) { throw 'Could not install Python ADB dependencies.' }

Write-Host 'TV identity (read-only):'
& $py -I tools\tv.py --probe
if ($LASTEXITCODE -ne 0) { throw 'TV probe failed; no changes made.' }
$fingerprint = ((& $py -I tools\tv.py 'getprop ro.build.fingerprint') | Select-Object -Last 1).Trim()
if ($LASTEXITCODE -ne 0 -or -not $fingerprint) { throw 'Cannot read firmware fingerprint; no changes made.' }

function ProfileFingerprint([string]$path) {
    $line = Get-Content $path | Where-Object { $_ -match '^# Fingerprint: ' } | Select-Object -First 1
    if (-not $line) { return '' }
    return ($line -replace '^# Fingerprint: ', '').Trim()
}

$profile = ''
if ($DebloatList) {
    $profile = (Resolve-Path $DebloatList).Path
} else {
    $candidates = @(Get-ChildItem (Join-Path $root 'examples') -Filter debloat.txt -Recurse -File |
        Where-Object { (ProfileFingerprint $_.FullName) -ceq $fingerprint })
    if ($candidates.Count -eq 1) {
        $profile = $candidates[0].FullName
        Write-Host "Found one matching optimization profile: $profile"
    } elseif ($candidates.Count -gt 1) {
        Write-Warning 'Several exact-firmware profiles exist. Read their comments and choose one with -DebloatList:'
        $candidates | ForEach-Object { Write-Host "  $($_.FullName)" }
    } else {
        Write-Warning 'No exact-firmware optimization profile; will install Home without debloating.'
    }
}

if ($profile) {
    $reference = ProfileFingerprint $profile
    if (-not $reference -or $reference -cne $fingerprint) {
        throw "Refusing profile from another firmware. TV: $fingerprint ; profile: $reference"
    }
    & $py -I tools\recovery.py --save $profile
    if ($LASTEXITCODE -ne 0) { throw 'Could not save pre-install state; no changes made.' }
}

$home = ((& $py -I tools\tv.py 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME') | Select-Object -Last 1).Trim()
$homePkg = ($home -split '/')[0]
if ($homePkg -match '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$') {
    $baseline = Join-Path $root 'recovery-baseline.txt'
    Set-Content -Path $baseline -Value $homePkg -Encoding ascii
    & $py -I tools\recovery.py --save $baseline
    if ($LASTEXITCODE -ne 0) { throw 'Could not save original Home state; no changes made.' }
} else {
    Write-Warning "Home '$home' could not be recorded. The installer retains the stock Home if handoff fails."
}

Write-Host 'Installing signed APK, optimizing its compilation and setting default Home...'
try {
    & (Join-Path $root 'launcher\build-and-install.ps1') -TvHost $TvHost -ApkPath $apk
} finally {
    Set-Location $root
}

if ($profile) {
    Write-Host "Matched profile: $profile"
    Write-Host 'Read its comments; disabling services can affect Chromecast, tuner, HDMI and Ambilight.'
    $answer = Read-Host 'Type OPTIMIZAR to apply, or press Enter to skip'
    if ($answer -ceq 'OPTIMIZAR') {
        & (Join-Path $root 'tools\disable.ps1') -TvHost $TvHost -List $profile
        if ($LASTEXITCODE -ne 0) { throw 'Debloat did not finish; restore using tools\restore.ps1.' }
        Write-Host "Undo: tools\restore.ps1 -TvHost $TvHost -List '$profile'"
    } else {
        Write-Host 'Debloat skipped; Home installed.'
    }
}
Write-Host 'Keep this folder and the recovery records under your user profile (\.cleanyourtv).'
Write-Host 'After checking the TV, turn ADB debugging OFF and reboot it.'
