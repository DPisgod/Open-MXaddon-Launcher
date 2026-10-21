# ============================================================
#  build-all.ps1 —— 一键构建：启动器 + 单文件 exe + 安装包
# ------------------------------------------------------------
#  用法：  pwsh -File build-all.ps1
#          （或双击根目录的 一键构建.cmd）
# ============================================================
param(
    [switch]$SkipShell,     # 不打单文件 exe
    [switch]$SkipInstaller  # 不打安装包
)

$ErrorActionPreference = 'Stop'
$Root = $PSScriptRoot
if (-not $Root) { $Root = (Get-Location).Path }

Write-Host "`n=== [1/2] 构建启动器（jar / JRE / 单文件 exe） ===" -ForegroundColor Cyan
$buildArgs = @('-NoProfile','-ExecutionPolicy','Bypass','-File', (Join-Path $Root 'launcher-java\build.ps1'))
if ($SkipShell) { $buildArgs += '-SkipShell' }
& powershell @buildArgs
if ($LASTEXITCODE -ne 0) { throw '启动器构建失败' }

$launcher = Join-Path $Root 'OML单文件版\OML启动器.exe'

if ($SkipInstaller) {
    Write-Host "`n（-SkipInstaller 已跳过安装包）"
    return
}
if (-not (Test-Path $launcher)) {
    Write-Warning "未找到 $launcher，跳过安装包"
    return
}

Write-Host "`n=== [2/2] 构建安装包 ===" -ForegroundColor Cyan
$makensis = (Get-Command makensis -ErrorAction SilentlyContinue).Source
if (-not $makensis) {
    foreach ($candidate in @(
        (Join-Path ${env:ProgramFiles(x86)} 'NSIS\makensis.exe'),
        (Join-Path $env:ProgramFiles 'NSIS\makensis.exe'),
        (Join-Path (Split-Path $Root -Parent) '_tools\nsis-3.11\makensis.exe'))) {
        if (Test-Path -LiteralPath $candidate) { $makensis = $candidate; break }
    }
}
if (-not $makensis) {
    Write-Warning '未找到 makensis.exe（安装 NSIS 3 后可生成安装包）。单文件 exe 已生成：'
    Write-Host "  $launcher"
    return
}

& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Root 'installer\build-installer.ps1') `
    -LauncherExe $launcher -NsisCompiler $makensis
if ($LASTEXITCODE -ne 0) { throw '安装包构建失败' }

Write-Host "`n构建完成：" -ForegroundColor Green
Write-Host "  单文件版 : $launcher"
Write-Host "  安装包   : $(Join-Path $Root 'artifacts\installer')"
