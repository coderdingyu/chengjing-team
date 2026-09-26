$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = Join-Path $projectRoot '.run'
foreach ($name in @('frontend', 'backend')) {
    $statePath = Join-Path $runtimeRoot "$name.json"
    if (-not (Test-Path -LiteralPath $statePath)) { continue }
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $process = Get-Process -Id $state.Id -ErrorAction SilentlyContinue
    if ($process -and $process.StartTime.ToUniversalTime().ToString('o') -eq $state.StartedUtc) {
        Stop-Process -Id $state.Id -Force
        Write-Host "已停止 $name (PID $($state.Id))"
    }
    Remove-Item -LiteralPath $statePath -Force
}
