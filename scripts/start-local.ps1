$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backendRoot = Join-Path $projectRoot 'backend'
$frontendRoot = Join-Path $projectRoot 'frontend'
$runtimeRoot = Join-Path $projectRoot '.run'
New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null

foreach ($name in @('backend', 'frontend')) {
    if (Test-Path -LiteralPath (Join-Path $runtimeRoot "$name.json")) {
        throw "检测到 $name 的运行记录，请先执行 scripts/stop-local.ps1。"
    }
}
foreach ($port in @(8081, 5173)) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        throw "端口 $port 已被占用，请先停止占用程序。"
    }
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw '需要 Java 17 或更高版本。' }
if (-not (Get-Command npm -ErrorAction SilentlyContinue)) { throw '需要 Node.js 22 或更高版本与 npm。' }

Push-Location $frontendRoot
try {
    npm ci
    if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败' }
    npm run build
    if ($LASTEXITCODE -ne 0) { throw '前端构建失败' }
} finally { Pop-Location }

Push-Location $backendRoot
try {
    & (Join-Path $backendRoot 'mvnw.cmd') -s .mvn/settings.xml package
    if ($LASTEXITCODE -ne 0) { throw '后端构建或测试失败' }
} finally { Pop-Location }

$jarPath = Join-Path $backendRoot 'target/chengjing-server-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jarPath)) { throw '后端 JAR 未生成' }
$javaPath = (Get-Command java).Source
$nodePath = (Get-Command node -ErrorAction Stop).Source
$vitePath = Join-Path $frontendRoot 'node_modules/vite/bin/vite.js'
$backendProcess = Start-Process -FilePath $javaPath -ArgumentList @('-jar', ('"' + $jarPath + '"')) `
    -WorkingDirectory $backendRoot -RedirectStandardOutput (Join-Path $runtimeRoot 'backend.out.log') `
    -RedirectStandardError (Join-Path $runtimeRoot 'backend.err.log') -WindowStyle Hidden -PassThru
$backendProcess | Select-Object Id, @{Name='StartedUtc';Expression={$_.StartTime.ToUniversalTime().ToString('o')}} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtimeRoot 'backend.json') -Encoding UTF8

try {
    $healthy = $false
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        Start-Sleep -Milliseconds 500
        try {
            $response = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/system/health' -TimeoutSec 2
            if ($response.success -and $response.data.status -eq 'UP') { $healthy = $true; break }
        } catch { }
    }
    if (-not $healthy) { throw '后端健康检查失败，请查看 .run/backend.err.log' }
    if (-not (Get-Process -Id $backendProcess.Id -ErrorAction SilentlyContinue)) { throw '后端进程已退出，请查看 .run/backend.err.log' }
    $frontendProcess = Start-Process -FilePath $nodePath -ArgumentList @(('"' + $vitePath + '"'), '--host', '127.0.0.1', '--port', '5173') `
        -WorkingDirectory $frontendRoot -RedirectStandardOutput (Join-Path $runtimeRoot 'frontend.out.log') `
        -RedirectStandardError (Join-Path $runtimeRoot 'frontend.err.log') -WindowStyle Hidden -PassThru
    $frontendProcess | Select-Object Id, @{Name='StartedUtc';Expression={$_.StartTime.ToUniversalTime().ToString('o')}} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtimeRoot 'frontend.json') -Encoding UTF8
    $webReady = $false
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        Start-Sleep -Milliseconds 500
        try { if ((Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:5173/' -TimeoutSec 2).StatusCode -eq 200) { $webReady = $true; break } }
        catch { }
    }
    if (-not $webReady) { throw '前端健康检查失败，请查看 .run/frontend.err.log' }
    if (-not (Get-Process -Id $frontendProcess.Id -ErrorAction SilentlyContinue)) { throw '前端进程已退出，请查看 .run/frontend.err.log' }
    Write-Host '澄镜团队版已启动：http://127.0.0.1:5173/'
    Write-Host '健康检查：http://127.0.0.1:8081/api/v1/system/health'
} catch {
    & (Join-Path $PSScriptRoot 'stop-local.ps1')
    throw
}
