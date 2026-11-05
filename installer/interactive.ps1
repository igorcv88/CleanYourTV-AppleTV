# Interactive entrypoint used by CleanYourTV-Setup.exe. Nothing is persisted until a TV install succeeds.
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'devices.ps1')

function Read-TvAddress {
    while ($true) {
        $typed = Read-Host 'IP da TV (ex.: 192.168.1.70)'
        try { return ConvertTo-CtvEndpoint $typed }
        catch { Write-Warning $_.Exception.Message }
    }
}

function Choose-Tv([string]$StorePath) {
    while ($true) {
        $store = Read-CtvStore $StorePath
        $devices = @($store.devices)
        Write-Host ''
        Write-Host '=== CleanYourTV Scenic — Setup ===' -ForegroundColor Cyan
        if ($devices.Count -eq 0) {
            Write-Host 'Primeira instalacao. Informe o IP da TV.'
            return [pscustomobject]@{ address = (Read-TvAddress); id = 0; name = '' }
        }
        for ($index = 0; $index -lt $devices.Count; $index++) {
            $suffix = if ($devices[$index].id -eq $store.defaultId) { ' (padrao)' } else { '' }
            Write-Host "[$($index + 1)] $($devices[$index].name) - $($devices[$index].address)$suffix"
        }
        Write-Host '[A] Adicionar TV    [E] Alterar IP    [Q] Sair'
        $prompt = if ($devices.Count -eq 1) { 'Enter para atualizar a TV salva, ou escolha uma opcao' }
                  else { 'Numero da TV (Enter usa a padrao), ou escolha uma opcao' }
        $answer = (Read-Host $prompt).Trim()
        if ($answer -ieq 'Q') { throw 'Instalacao cancelada.' }
        if ($answer -ieq 'A') {
            return [pscustomobject]@{ address = (Read-TvAddress); id = 0; name = '' }
        }
        if ($answer -ieq 'E') {
            $selected = $null
            if ($devices.Count -eq 1) { $selected = $devices[0] }
            else {
                $number = 0
                $typedNumber = Read-Host 'Numero da TV que tera o IP alterado'
                if (-not [int]::TryParse($typedNumber, [ref]$number) -or
                    $number -lt 1 -or $number -gt $devices.Count) {
                    Write-Warning 'Numero invalido.'
                    continue
                }
                $selected = $devices[$number - 1]
            }
            return [pscustomobject]@{ address = (Read-TvAddress); id = [int]$selected.id; name = $selected.name }
        }
        if (-not $answer) {
            $default = @($devices | Where-Object { $_.id -eq $store.defaultId } | Select-Object -First 1)
            $selected = if ($default.Count) { $default[0] } else { $devices[0] }
        } else {
            $number = 0
            if (-not [int]::TryParse($answer, [ref]$number) -or
                $number -lt 1 -or $number -gt $devices.Count) {
                Write-Warning 'Opcao invalida.'
                continue
            }
            $selected = $devices[$number - 1]
        }
        return [pscustomobject]@{ address = $selected.address; id = [int]$selected.id; name = $selected.name }
    }
}

try {
    $storePath = Get-CtvStorePath
    $selection = Choose-Tv $storePath
    if ($selection.id -gt 0) {
        $conflicts = @((Read-CtvStore $storePath).devices | Where-Object {
            $_.address -eq $selection.address -and $_.id -ne $selection.id
        })
        if ($conflicts.Count -gt 0) {
            throw "IP $($selection.address) ja pertence a outra TV cadastrada. Nenhuma instalacao foi iniciada."
        }
    }
    $env:TV_HOST = $selection.address
    Write-Host "TV selecionada: $($selection.address)"
    & (Join-Path $PSScriptRoot 'setup.ps1') -TvHost $selection.address
    if (-not $?) { throw 'A instalacao falhou; a lista de TVs nao foi alterada.' }

    $name = $selection.name
    if (-not $name) {
        $root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
        $pythonPath = Join-Path $root 'tools\venv\Scripts\python.exe'
        try {
            $model = @(& $pythonPath -I (Join-Path $root 'tools\tv.py') 'getprop ro.product.model')
            if ($LASTEXITCODE -eq 0 -and $model.Count -gt 0) { $name = "$($model[-1])".Trim() }
        } catch { Write-Warning 'Could not read TV model; using a generic name.' }
    }
    $id = Save-CtvDevice $storePath $selection.address $name $selection.id
    Write-Host "TV #$id salva em $storePath" -ForegroundColor Green
    Write-Host 'Instalacao concluida. Nas proximas versoes, execute o Setup.exe novamente.' -ForegroundColor Green
} catch {
    Write-Host "ERRO: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
