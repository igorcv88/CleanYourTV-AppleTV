$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\devices.ps1')

function Assert-Ctv($Condition, [string]$Message) {
    if (-not $Condition) { throw "Test failed: $Message" }
}

# All PowerShell sources must parse on Windows PowerShell 5.1, which is included in Windows 11.
foreach ($script in @('devices.ps1', 'interactive.ps1', 'setup.ps1')) {
    $path = Join-Path $PSScriptRoot "..\$script"
    $tokens = $null
    $errors = $null
    $null = [System.Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors)
    Assert-Ctv ($errors.Count -eq 0) "PowerShell syntax: $script ($errors)"
}

$temporary = Join-Path ([IO.Path]::GetTempPath()) ('cleanyourtv-test-' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $temporary
try {
    $path = Join-Path $temporary 'devices.json'
    $store = Read-CtvStore $path
    Assert-Ctv ($store.devices.Count -eq 0) 'empty store'

    foreach ($invalid in @('bad host', '192.168.1.256', '192.168.1.70:0', '192.168.1.70:70000')) {
        $rejected = $false
        try { $null = ConvertTo-CtvEndpoint $invalid } catch { $rejected = $true }
        Assert-Ctv $rejected "reject invalid address $invalid"
    }
    Assert-Ctv ((ConvertTo-CtvEndpoint '192.168.001.070:5555') -eq '192.168.1.70') 'canonical IP'

    $one = Save-CtvDevice $path '192.168.1.70' 'Philips Android TV'
    Assert-Ctv ($one -eq 1) 'first ID'
    $two = Save-CtvDevice $path '192.168.1.80:5556' 'Living room'
    Assert-Ctv ($two -eq 2) 'second ID'
    $again = Save-CtvDevice $path '192.168.1.70' 'Duplicate'
    Assert-Ctv ($again -eq 1) 'existing address does not duplicate'
    $store = Read-CtvStore $path
    Assert-Ctv ($store.devices.Count -eq 2) 'two devices'
    Assert-Ctv ($store.defaultId -eq 1) 'last selected becomes default'
    Assert-Ctv ($store.devices[0].name -eq 'Philips Android TV') 'existing name retained'

    $updated = Save-CtvDevice $path '192.168.1.71' 'Ignored rename' 1
    Assert-Ctv ($updated -eq 1) 'edit reuses identity'
    $store = Read-CtvStore $path
    Assert-Ctv ($store.devices[0].address -eq '192.168.1.71') 'IP updated'
    Assert-Ctv ($store.devices[0].name -eq 'Philips Android TV') 'edit keeps TV name'

    $snapshot = Get-Content $path -Raw
    $rejected = $false
    try { $null = Save-CtvDevice $path '192.168.1.80:5556' 'collision' 1 } catch { $rejected = $true }
    Assert-Ctv $rejected 'duplicate IP update rejected'
    Assert-Ctv ((Get-Content $path -Raw) -eq $snapshot) 'failed update leaves file unchanged'

    [IO.File]::WriteAllText($path, '{broken JSON')
    $rejected = $false
    try { $null = Read-CtvStore $path } catch { $rejected = $true }
    Assert-Ctv $rejected 'damaged JSON rejected'
    Assert-Ctv ((Get-Content $path -Raw) -eq '{broken JSON') 'corrupted file preserved'

    Write-Host 'CleanYourTV Windows device-store tests passed.' -ForegroundColor Green
} finally {
    Remove-Item -LiteralPath $temporary -Recurse -Force
}
