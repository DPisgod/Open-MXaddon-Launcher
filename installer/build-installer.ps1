param(
    [string]$LauncherExe = '',
    [string]$ReadmeFile = '',
    [string]$NsisCompiler = '',
    [string]$OutputDirectory = '',
    [string]$Version = ''
)
$ErrorActionPreference = 'Stop'
$installerRoot = $PSScriptRoot
$repoRoot = Split-Path $installerRoot -Parent
$versionFile = Join-Path $repoRoot 'launcher-java\src\main\resources\OML-version.properties'
$sharedVersion = ((Get-Content -LiteralPath $versionFile | Where-Object { $_ -match '^version=' }) -replace '^version=','').Trim()
if (-not $Version) { $Version = $sharedVersion }
if ($Version -ne $sharedVersion) { throw '安装包版本必须与启动器共享版本一致' }
$launcherIcon = Join-Path $repoRoot 'launcher-java\src\main\resources\icons\launcher.ico'
$launcherIconSvg = Join-Path $repoRoot 'launcher-java\assets\launcher-icon.svg'
if (-not $LauncherExe) { $LauncherExe = Join-Path $repoRoot 'artifacts\single\OML启动器.exe' }
if (-not $ReadmeFile) { $ReadmeFile = Join-Path $repoRoot 'launcher-java\shell\使用说明.txt' }
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $repoRoot 'artifacts\installer' }
if (-not $NsisCompiler) {
    $compilerCommand = Get-Command makensis -ErrorAction SilentlyContinue
    if ($compilerCommand) { $NsisCompiler = $compilerCommand.Source }
    else {
        $candidates = @(
            (Join-Path ${env:ProgramFiles(x86)} 'NSIS\makensis.exe'),
            (Join-Path $env:USERPROFILE '.codex\tools\nsis-3.13\makensis.exe')
        )
        $NsisCompiler = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    }
}
foreach ($required in @($LauncherExe, $ReadmeFile, $NsisCompiler, $launcherIcon, $launcherIconSvg)) {
    if (-not $required -or -not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "找不到输入文件或 NSIS 编译器：$required" }
}
if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw 'Version 必须为三段数字，例如 1.0.0' }
$launcherVersion = (Get-Item -LiteralPath $LauncherExe).VersionInfo.ProductVersion
if ($launcherVersion -ne $Version) { throw "启动器 EXE 版本 $launcherVersion 与安装包 $Version 不一致，请先重新构建启动器" }
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
$payloadDirectory = Join-Path $OutputDirectory 'payload'
New-Item -ItemType Directory -Force -Path $OutputDirectory,$payloadDirectory | Out-Null
& (Join-Path $installerRoot 'build-inspector.ps1') -OutputDirectory (Join-Path $OutputDirectory 'inspector')
$inspector = Join-Path $OutputDirectory 'inspector\mx-inspect.exe'
# Explicit allowlist: never include accounts, device credentials, games or wallpapers.
Copy-Item -LiteralPath $LauncherExe -Destination (Join-Path $payloadDirectory 'OML启动器.exe') -Force
Copy-Item -LiteralPath $ReadmeFile -Destination (Join-Path $payloadDirectory '使用说明.txt') -Force
# Use the same OML monogram for setup, installed shortcuts and the launcher.
Copy-Item -LiteralPath $launcherIcon -Destination (Join-Path $installerRoot 'assets\OML.ico') -Force
Copy-Item -LiteralPath $launcherIconSvg -Destination (Join-Path $installerRoot 'assets\OML.svg') -Force
$iconHash = (Get-FileHash -LiteralPath $launcherIcon -Algorithm SHA256).Hash.ToLowerInvariant()
$outputFile = Join-Path $OutputDirectory ("OML-Setup-$Version.exe")
Push-Location $installerRoot
try {
    & $NsisCompiler '/V3' '/INPUTCHARSET' 'UTF8' "/DPAYLOAD_DIR=$payloadDirectory" "/DOUTPUT_FILE=$outputFile" "/DAPP_VERSION=$Version" "/DINSPECTOR_FILE=$inspector" (Join-Path $installerRoot 'OML.nsi')
    if ($LASTEXITCODE -ne 0) { throw "NSIS 编译失败：$LASTEXITCODE" }
} finally { Pop-Location }
$sourceInstaller = Join-Path $repoRoot 'OML安装包.exe'
if ([IO.Path]::GetFullPath($outputFile) -ne [IO.Path]::GetFullPath($sourceInstaller)) {
    Copy-Item -LiteralPath $outputFile -Destination $sourceInstaller -Force
}
$manifest = [ordered]@{
    version = $Version
    defaultDirectory = 'D:\Open-MXaddon-Launcher'
    desktopShortcutOptional = $true
    sourceCommit = (& git -C $repoRoot rev-parse HEAD)
    launcherSha256 = (Get-FileHash -LiteralPath $LauncherExe -Algorithm SHA256).Hash.ToLowerInvariant()
    installerSha256 = (Get-FileHash -LiteralPath $outputFile -Algorithm SHA256).Hash.ToLowerInvariant()
    installerBytes = (Get-Item -LiteralPath $outputFile).Length
    iconSha256 = $iconHash
    externalIconFile = $false
    uninstallerFile = '卸载 OML.exe'
    uninstallRemovesInstallationDirectory = $true
    uninstallClearsCloudAndRuntimeCache = $true
    sourceInstaller = $sourceInstaller
    payloadFiles = @('OML启动器.exe','使用说明.txt')
    generatedIntegrityManifest = '.mx-install.ini'
    inspection = 'SHA-256 of launcher, instructions and uninstaller; PE version matches manifest'
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $OutputDirectory 'build-manifest.json') -Encoding UTF8
Write-Output "安装包已生成：$outputFile"
