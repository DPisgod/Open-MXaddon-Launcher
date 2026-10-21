# build_shell.ps1 —— 构建自解压 exe 壳子
#
# 输入：dist\ 目录（jar / Libraries / jna*.jar / runtime / 使用说明.txt）
# 输出：dist-shell\MXCloud启动器.exe  （单文件，内嵌全部运行时）
#
# 原理：
#   1) 把 dist\ 内容打成 payload.zip（不含 .min）
#   2) 生成 payload.rc（把 zip 作为 RCDATA 资源编译进 exe）
#   3) 编译 MXCloudLauncher.cpp + payload.rc -> exe
#
# 注意：exe 内嵌的是**启动器运行时**，不含任何 mod 本体。

param(
    [string]$DistDir = "",      # 输入：运行时目录（默认 ..\dist）
    [string]$OutDir  = "",      # 输出目录（默认 ..\dist-shell）
    [switch]$KeepTemp
)

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
if (-not $Root) { $Root = (Get-Location).Path }
$Parent = Split-Path $Root -Parent
$versionFile = Join-Path $Parent 'src\main\resources\mxcloud-version.properties'
$releaseVersion = ((Get-Content -LiteralPath $versionFile | Where-Object { $_ -match '^version=' }) -replace '^version=','').Trim()
if ($releaseVersion -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid shared launcher version' }
$numericVersion = ($releaseVersion -replace '\.',',') + ',0'

if (-not $DistDir) { $DistDir = Join-Path $Parent "dist" }
if (-not $OutDir)  { $OutDir  = Join-Path $Parent "dist-shell" }
$iconFile = Join-Path $Parent "src\main\resources\icons\launcher.ico"
if (-not (Test-Path -LiteralPath $iconFile -PathType Leaf)) { throw "Application icon missing: $iconFile" }

Write-Host "=== [1/6] 检查输入 ===" -ForegroundColor Cyan
if (-not (Test-Path (Join-Path $DistDir "mxaddon-launcher.jar"))) {
    throw "找不到 $DistDir\mxaddon-launcher.jar（请先运行 package.ps1）"
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$versionArchive = [IO.Compression.ZipFile]::OpenRead((Join-Path $DistDir 'mxaddon-launcher.jar'))
try {
    $versionEntry = $versionArchive.GetEntry('mxcloud-version.properties')
    if (-not $versionEntry) { throw 'Launcher JAR has no shared release version; rebuild Java first' }
    $versionReader = [IO.StreamReader]::new($versionEntry.Open())
    try { $jarVersionText = $versionReader.ReadToEnd() } finally { $versionReader.Dispose() }
    $jarReleaseVersion = (($jarVersionText -split '\r?\n' | Where-Object { $_ -match '^version=' }) -replace '^version=','').Trim()
    if ($jarReleaseVersion -ne $releaseVersion) { throw 'Launcher JAR version differs from native EXE version; rebuild Java first' }
} finally { $versionArchive.Dispose() }
if (-not (Test-Path (Join-Path $DistDir "runtime\bin\javaw.exe"))) {
    throw "找不到 $DistDir\runtime（自带 JRE 缺失）"
}
Write-Host "输入目录: $DistDir"

# ---------- 2. 打 payload.zip ----------
Write-Host "`n=== [2/6] 打包运行时 ===" -ForegroundColor Cyan

$stage = Join-Path $Root "_stage"
$resolvedStage = [System.IO.Path]::GetFullPath($stage)
if (-not $resolvedStage.StartsWith([System.IO.Path]::GetFullPath($Root) + '\', [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe staging path' }
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Force -Path $stage | Out-Null

# 复制 dist 内容，排除运行期产物
$skipNames = @("session.properties", "accounts.dat", "launcher.properties", "appearance.properties", "activation.properties", "device-session.dat", "payload.zip")
$skipDirs  = @("skins", "wallpapers", "build", ".min", "_stage")

function Copy-Tree($from, $to) {
    New-Item -ItemType Directory -Force -Path $to | Out-Null
    foreach ($item in Get-ChildItem -LiteralPath $from -Force) {
        if ($item.PSIsContainer) {
            if ($skipDirs -contains $item.Name) { continue }
            Copy-Tree $item.FullName (Join-Path $to $item.Name)
        } else {
            if ($skipNames -contains $item.Name) { continue }
            Copy-Item -LiteralPath $item.FullName -Destination (Join-Path $to $item.Name) -Force
        }
    }
}
Copy-Tree $DistDir $stage

$payloadZip = Join-Path $Root "payload.zip"
if (Test-Path $payloadZip) { Remove-Item $payloadZip -Force }

Add-Type -AssemblyName System.IO.Compression.FileSystem
# 用最快压缩（JRE 里大量已是压缩过的 class，再压收益低还慢）
[System.IO.Compression.ZipFile]::CreateFromDirectory(
    $stage, $payloadZip, [System.IO.Compression.CompressionLevel]::Fastest, $false)

$zipMb = [math]::Round((Get-Item $payloadZip).Length / 1MB, 1)
Write-Host "payload.zip 已生成: $zipMb MB"

# 计算内容版本（用于 %TEMP% 缓存目录名）：基于 zip 的哈希前 12 位
$sha = [System.Security.Cryptography.SHA256]::Create()
$fs = [System.IO.File]::OpenRead($payloadZip)
$hash = $sha.ComputeHash($fs)
$fs.Close()
$buildId = ($hash[0..5] | ForEach-Object { $_.ToString("x2") }) -join ""
Write-Host "内容版本: $buildId"

# ---------- 3. 生成资源脚本 ----------
Write-Host "`n=== [3/6] 生成资源脚本 ===" -ForegroundColor Cyan

# 资源 ID
$IDR_PAYLOAD_ZIP = 1001

# payload_ids.h
# 注意：必须以换行结尾 —— rc.exe 对文件末尾缺换行会报 RC1004。
$ids = @"
// 自动生成 —— 请勿手改
#pragma once
#define MXCLOUD_BUILD_ID "$buildId"
#define IDR_PAYLOAD_ZIP  $IDR_PAYLOAD_ZIP

"@
[System.IO.File]::WriteAllText((Join-Path $Root "payload_ids.h"), $ids,
    (New-Object System.Text.UTF8Encoding($false)))

# payload.rc
# 关键：必须 include payload_ids.h，否则 IDR_PAYLOAD_ZIP 是未定义宏，
# rc 会把它当字符串资源名，运行时 FindResource 就找不到。
$rc = "// 自动生成 —— 内嵌启动器运行时（不含 mod）`r`n"
$rc += "#include <windows.h>`r`n"
$rc += "#include `"payload_ids.h`"`r`n"
$rc += "1 ICON `"../src/main/resources/icons/launcher.ico`"`r`n"
# A second identical group gives shortcuts a fresh, valid cache key. NSIS
# truncates negative icon indices, so use the supported positive ordinal 1.
$rc += "2 ICON `"../src/main/resources/icons/launcher.ico`"`r`n"
$rc += "IDR_PAYLOAD_ZIP RCDATA `"payload.zip`"`r`n"
$rc += "1 VERSIONINFO`r`n"
$rc += "FILEVERSION $numericVersion`r`n"
$rc += "PRODUCTVERSION $numericVersion`r`n"
$rc += "FILEOS 0x40004L`r`n"
$rc += "FILETYPE 0x1L`r`n"
$rc += "BEGIN`r`n"
$rc += "  BLOCK `"StringFileInfo`"`r`n"
$rc += "  BEGIN`r`n"
$rc += "    BLOCK `"080404b0`"`r`n"
$rc += "    BEGIN`r`n"
$rc += "      VALUE `"FileDescription`", `"MXCloud 启动器`"`r`n"
$rc += "      VALUE `"ProductName`", `"MXCloud Launcher`"`r`n"
$rc += "      VALUE `"CompanyName`", `"MXCloud`"`r`n"
$rc += "      VALUE `"FileVersion`", `"$releaseVersion.0`"`r`n"
$rc += "      VALUE `"ProductVersion`", `"$releaseVersion`"`r`n"
$rc += "    END`r`n"
$rc += "  END`r`n"
$rc += "  BLOCK `"VarFileInfo`"`r`n"
$rc += "  BEGIN`r`n"
$rc += "    VALUE `"Translation`", 0x804, 1200`r`n"
$rc += "  END`r`n"
$rc += "END`r`n"

# rc 文件需与 payload.zip 同目录，或指定相对路径
[System.IO.File]::WriteAllText((Join-Path $Root "payload.rc"), $rc,
    (New-Object System.Text.UnicodeEncoding($false, $true)))

Write-Host "payload.rc / payload_ids.h 已生成"

# ---------- 4. 定位 MSVC ----------
Write-Host "`n=== [4/6] 定位 MSVC ===" -ForegroundColor Cyan

function Find-VcRoot {
    $vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
    if (Test-Path $vswhere) {
        $p = & $vswhere -latest -products * `
            -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 `
            -property installationPath 2>$null
        if ($p) {
            $t = Get-ChildItem (Join-Path $p "VC\Tools\MSVC") -Directory -EA SilentlyContinue |
                 Sort-Object Name -Descending | Select-Object -First 1
            if ($t) { return $t.FullName }
        }
    }
    # 回退：直接扫常见安装位置（vswhere 有时探测不到 BuildTools）
    foreach ($base in @("${env:ProgramFiles(x86)}\Microsoft Visual Studio",
                        "${env:ProgramFiles}\Microsoft Visual Studio")) {
        if (-not (Test-Path $base)) { continue }
        $hit = Get-ChildItem $base -Recurse -Depth 5 -Directory -Filter "MSVC" -EA SilentlyContinue |
               ForEach-Object { Get-ChildItem $_.FullName -Directory -EA SilentlyContinue } |
               Where-Object { $_.FullName -match '\\VC\\Tools\\MSVC\\' } |
               Sort-Object Name -Descending | Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }
    return $null
}

function Find-SdkRoots {
    $base = "${env:ProgramFiles(x86)}\Windows Kits\10"
    if (-not (Test-Path $base)) { return $null }
    $ver = Get-ChildItem (Join-Path $base "Include") -Directory -EA SilentlyContinue |
           Where-Object { $_.Name -match '^\d+\.' } |
           Sort-Object Name -Descending | Select-Object -First 1
    if (-not $ver) { return $null }
    return @{ Inc = Join-Path $base "Include\$($ver.Name)"; Lib = Join-Path $base "Lib\$($ver.Name)" }
}

$vcRoot = Find-VcRoot
if (-not $vcRoot) { throw "未找到 MSVC（请安装 VS Build Tools 的 C++ 组件）" }
$sdk = Find-SdkRoots
if (-not $sdk) { throw "未找到 Windows SDK" }

Write-Host "VC : $vcRoot"
Write-Host "SDK: $($sdk.Lib)"

# 建立编译环境
# vcRoot = <VS根>\VC\Tools\MSVC\<版本>
# vcvars 在 <VS根>\VC\Auxiliary\Build\vcvars64.bat
# 上溯 2 层得到 <VS根>\VC，再拼 Auxiliary\Build
$vcDir = Split-Path (Split-Path $vcRoot -Parent) -Parent      # ...\<VS根>\VC
$vcvars = Join-Path $vcDir "Auxiliary\Build\vcvars64.bat"
if (-not (Test-Path $vcvars)) {
    # 回退：在 VS 根下搜索
    $found = Get-ChildItem (Split-Path $vcDir -Parent) -Recurse -Depth 6 `
                -Filter "vcvars64.bat" -EA SilentlyContinue | Select-Object -First 1
    if ($found) { $vcvars = $found.FullName } else { throw "未找到 vcvars64.bat" }
}

$envOut = cmd /c "call `"$vcvars`" >nul 2>&1 && set"
foreach ($line in $envOut) {
    if ($line -match "^([^=]+)=(.*)$") {
        $n = $matches[1]
        if ($n -in @("PATH","LIB","INCLUDE","LIBPATH","VCINSTALLDIR","WindowsSdkDir",
                     "WinSDKVer","VCToolsInstallDir","UniversalCRTSdkDir","UCRTVersion",
                     "WindowsSDKVersion","VCToolsVersion")) {
            Set-Item -Path "env:$n" -Value $matches[2] -EA SilentlyContinue
        }
    }
}

# ---------- 5. 编译 ----------
Write-Host "`n=== [5/6] 编译 exe ===" -ForegroundColor Cyan

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$src = Join-Path $Root "MXCloudLauncher.cpp"
if (-not (Test-Path $src)) { throw "缺少源码: $src" }

$exeOut = Join-Path $OutDir "MXCloud启动器.exe"
$buildLog = Join-Path $env:TEMP "mxcloud_shell_build.log"

# 直接用 PowerShell 调用工具链（环境变量已从 vcvars 继承）。
# .rc 必须先用 rc.exe 编译成 .res，cl 不能直接吃 .rc。
Push-Location $Root
try {
    $prevEA = $ErrorActionPreference
    $ErrorActionPreference = "Continue"

    # 1) 编译资源（-I 指向 payload_ids.h 所在目录）
    & rc.exe /nologo "/I$Root" payload.rc 2>&1 | Tee-Object -FilePath $buildLog
    $rcExit = $LASTEXITCODE
    if ($rcExit -ne 0) {
        $ErrorActionPreference = $prevEA
        throw "资源编译失败（exit=$rcExit）"
    }

    # 2) 编译 + 链接
    & cl.exe /nologo /utf-8 /O2 /MT /EHsc /std:c++17 /DUNICODE /D_UNICODE `
        "/I$Root" MXCloudLauncher.cpp payload.res `
        /link /SUBSYSTEM:WINDOWS "/OUT:$exeOut" `
        user32.lib shlwapi.lib shell32.lib 2>&1 |
        Tee-Object -FilePath $buildLog -Append
    $rc = $LASTEXITCODE
    $ErrorActionPreference = $prevEA
} finally {
    Pop-Location
}

if ($rc -ne 0 -or -not (Test-Path $exeOut)) {
    throw "编译失败（exit=$rc），日志: $buildLog"
}

$exeMb = [math]::Round((Get-Item $exeOut).Length / 1MB, 1)
Write-Host "exe 已生成: $exeMb MB"

# ---------- 6. 组装交付目录 + 清理 ----------
Write-Host "`n=== [6/6] 组装交付物 ===" -ForegroundColor Cyan

# 6a. 单文件版目录（exe + 说明 + 空 .min）
$SingleDir = Join-Path (Split-Path $Root -Parent) "..\MXCloud单文件版"
$SingleDir = [System.IO.Path]::GetFullPath($SingleDir)
$deliveryRoot = [System.IO.Path]::GetFullPath((Join-Path $Parent '..'))
if (-not $SingleDir.StartsWith($deliveryRoot + '\', [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe delivery path' }
# Preserve any game files a user already placed beside a previous build.
New-Item -ItemType Directory -Force -Path $SingleDir | Out-Null
Copy-Item $exeOut (Join-Path $SingleDir "MXCloud启动器.exe") -Force
New-Item -ItemType Directory -Force -Path (Join-Path $SingleDir ".min\versions") | Out-Null
if (Test-Path (Join-Path $DistDir "使用说明.txt")) {
    Copy-Item (Join-Path $DistDir "使用说明.txt") (Join-Path $SingleDir "使用说明.txt") -Force
}
Write-Host "单文件版目录: $SingleDir"

# 6b. zip（便于分发）
$SingleZip = Join-Path (Split-Path (Split-Path $Root -Parent) -Parent) "MXCloud单文件版.zip"
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (Test-Path $SingleZip) { Remove-Item $SingleZip -Force }
$archive = [System.IO.Compression.ZipFile]::Open($SingleZip, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($name in @('MXCloud启动器.exe','使用说明.txt')) {
        $file = Join-Path $SingleDir $name
        if (Test-Path -LiteralPath $file) { [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file,$name) | Out-Null }
    }
    $archive.CreateEntry('.min/versions/') | Out-Null
} finally { $archive.Dispose() }
$zipMb = [math]::Round((Get-Item $SingleZip).Length / 1MB, 1)
Write-Host "分发包: $SingleZip ($zipMb MB)"

# 6c. 清理编译中间产物（保留 payload_ids.h）
foreach ($f in @("payload.zip", "payload.rc", "MXCloudLauncher.obj", "payload.res",
                 "MXCloudLauncher.res", "vc140.pdb", "MXCloudLauncher.pdb")) {
    $p = Join-Path $Root $f
    if (Test-Path $p) { Remove-Item $p -Force -EA SilentlyContinue }
}
if (-not $KeepTemp -and (Test-Path $stage)) {
    Remove-Item $stage -Recurse -Force -EA SilentlyContinue
}

Write-Host ""
Write-Host "构建完成！" -ForegroundColor Green
Write-Host "  单文件版（可直接测试）: $SingleDir"
Write-Host "  分发包                : $SingleZip  ($zipMb MB)"
Write-Host "  内容版本              : $buildId"
Write-Host ""
Write-Host "用户目录里只有：MXCloud启动器.exe + .min\ + 使用说明.txt"
Write-Host "运行时会自动释放到 %TEMP%\MXCloud\$buildId\（用户看不到）。"
