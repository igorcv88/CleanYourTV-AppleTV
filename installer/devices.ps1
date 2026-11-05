# Device preferences are stored outside release ZIPs, under the current Windows user's profile.
# These functions deliberately do not touch the TV or require administrator privileges.
function Get-CtvStorePath {
    if (-not $env:LOCALAPPDATA) { throw 'LOCALAPPDATA is unavailable; cannot persist TV settings.' }
    Join-Path (Join-Path $env:LOCALAPPDATA 'CleanYourTV') 'devices.json'
}

function ConvertTo-CtvEndpoint([string]$Address) {
    $value = "$Address".Trim()
    if ($value -notmatch '\A((?:[0-9]{1,3}\.){3}[0-9]{1,3})(?::([0-9]{1,5}))?\z') {
        throw "Invalid TV IP address: '$value'. Use 192.168.1.70 or 192.168.1.70:5555."
    }
    $octets = @($Matches[1].Split('.') | ForEach-Object { [int]$_ })
    if (@($octets | Where-Object { $_ -gt 255 }).Count -gt 0) {
        throw "Invalid TV IP address: '$value'."
    }
    $port = if ($Matches[2]) { [int]$Matches[2] } else { 5555 }
    if ($port -lt 1 -or $port -gt 65535) { throw "Invalid TV port: '$value'." }
    $ip = $octets -join '.'
    if ($port -eq 5555) { return $ip }
    return "${ip}:$port"
}

function Read-CtvStore([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        return [pscustomobject]@{ version = 1; defaultId = 0; devices = @() }
    }
    try {
        $saved = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
    } catch {
        throw "Cannot read $Path. Existing TV settings were not overwritten: $_"
    }
    if ($null -eq $saved -or $saved.version -ne 1 -or $null -eq $saved.devices -or
        $saved.devices -isnot [array]) {
        throw "Unsupported or damaged TV settings in $Path. Existing settings were not overwritten."
    }
    $ids = @{}
    foreach ($device in $saved.devices) {
        $parsedId = 0
        if ($null -eq $device -or -not [int]::TryParse("$($device.id)", [ref]$parsedId) -or $parsedId -lt 1 -or
            -not $device.name -or -not $device.address -or $ids.ContainsKey([int]$device.id)) {
            throw "Invalid TV entry in $Path. Existing settings were not overwritten."
        }
        $ids[[int]$device.id] = $true
        try { $null = ConvertTo-CtvEndpoint ([string]$device.address) }
        catch { throw "Invalid TV address in $Path. Existing settings were not overwritten." }
    }
    return $saved
}

function Write-CtvStore([string]$Path, $Store) {
    $folder = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $folder)) {
        $null = New-Item -ItemType Directory -Path $folder -Force
    }
    $temporary = Join-Path $folder ('.devices-' + [guid]::NewGuid().ToString('N') + '.tmp')
    try {
        $json = ConvertTo-Json -InputObject $Store -Depth 6
        [System.IO.File]::WriteAllText($temporary, $json + "`n", [System.Text.UTF8Encoding]::new($false))
        Move-Item -LiteralPath $temporary -Destination $Path -Force
    } finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
    }
}

function Save-CtvDevice([string]$Path, [string]$Address, [string]$Name, [int]$ExistingId = 0) {
    $addressValue = ConvertTo-CtvEndpoint $Address
    $store = Read-CtvStore $Path
    $devices = @($store.devices)
    $existing = @($devices | Where-Object { $_.address -eq $addressValue } | Select-Object -First 1)
    if ($existing.Count -gt 0 -and $ExistingId -gt 0 -and $existing[0].id -ne $ExistingId) {
        throw "Address $addressValue is already assigned to TV #$($existing[0].id)."
    }
    $id = $ExistingId
    if ($id -eq 0 -and $existing.Count -gt 0) { $id = [int]$existing[0].id }
    if ($id -eq 0) {
        $maxId = 0
        foreach ($device in $devices) { $maxId = [Math]::Max($maxId, [int]$device.id) }
        $id = $maxId + 1
    }
    $match = @($devices | Where-Object { $_.id -eq $id } | Select-Object -First 1)
    if ($ExistingId -gt 0 -and $match.Count -eq 0) { throw "TV #$ExistingId no longer exists." }
    $displayName = "$Name".Trim()
    if (-not $displayName) { $displayName = "Android TV $id" }
    if ($match.Count -gt 0) {
        $match[0].address = $addressValue
    } else {
        $devices += [pscustomobject]@{ id = $id; name = $displayName; address = $addressValue }
    }
    $store.devices = @($devices)
    $store.defaultId = $id
    Write-CtvStore $Path $store
    return $id
}
