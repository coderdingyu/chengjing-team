param([string]$Destination = "")
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = Join-Path $projectRoot '.run'
if (Test-Path -LiteralPath (Join-Path $runtimeRoot 'backend.json')) { throw '请先停止后端，再备份数据库。' }
$dataRoot = Join-Path $projectRoot 'backend/data'
$database = Join-Path $dataRoot 'chengjing.mv.db'
$masterKey = Join-Path $dataRoot 'master.key'
if (-not (Test-Path -LiteralPath $database) -or -not (Test-Path -LiteralPath $masterKey)) { throw '数据库或加密主密钥不存在。' }
if (-not $Destination) { $Destination = Join-Path $projectRoot ('backups/' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
New-Item -ItemType Directory -Path $Destination -Force | Out-Null
Copy-Item -LiteralPath $database -Destination (Join-Path $Destination 'chengjing.mv.db')
Copy-Item -LiteralPath $masterKey -Destination (Join-Path $Destination 'master.key')
$manifest = @{ createdAt = (Get-Date).ToUniversalTime().ToString('o'); databaseSha256 = (Get-FileHash -LiteralPath $database -Algorithm SHA256).Hash; keySha256 = (Get-FileHash -LiteralPath $masterKey -Algorithm SHA256).Hash }
$manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $Destination 'manifest.json') -Encoding UTF8
Write-Host "备份完成：$Destination（其中包含可解密模型密钥的主密钥，请妥善保存）"
