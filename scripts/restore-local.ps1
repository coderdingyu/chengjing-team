param([Parameter(Mandatory=$true)][string]$Source, [switch]$Force)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runtimeRoot = Join-Path $projectRoot '.run'
if (Test-Path -LiteralPath (Join-Path $runtimeRoot 'backend.json')) { throw '请先停止后端，再恢复数据库。' }
$sourceRoot = (Resolve-Path -LiteralPath $Source).Path
$database = Join-Path $sourceRoot 'chengjing.mv.db'
$masterKey = Join-Path $sourceRoot 'master.key'
$manifestPath = Join-Path $sourceRoot 'manifest.json'
if (-not (Test-Path -LiteralPath $database) -or -not (Test-Path -LiteralPath $masterKey) -or -not (Test-Path -LiteralPath $manifestPath)) { throw '备份缺少数据库、主密钥或校验清单。' }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ((Get-FileHash -LiteralPath $database -Algorithm SHA256).Hash -ne $manifest.databaseSha256 -or (Get-FileHash -LiteralPath $masterKey -Algorithm SHA256).Hash -ne $manifest.keySha256) { throw '备份文件校验失败。' }
$dataRoot = Join-Path $projectRoot 'backend/data'
if (((Test-Path -LiteralPath (Join-Path $dataRoot 'chengjing.mv.db')) -or (Test-Path -LiteralPath (Join-Path $dataRoot 'master.key'))) -and -not $Force) { throw '当前数据库或主密钥已存在。确认覆盖时请加 -Force，或先单独备份。' }
New-Item -ItemType Directory -Path $dataRoot -Force | Out-Null
Copy-Item -LiteralPath $database -Destination (Join-Path $dataRoot 'chengjing.mv.db') -Force
Copy-Item -LiteralPath $masterKey -Destination (Join-Path $dataRoot 'master.key') -Force
Write-Host '恢复完成。启动服务后会自动校验和执行后续数据库迁移。'
