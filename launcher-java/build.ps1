# ============================================================
#  打包.ps1 —— MXCloud 启动器一键打包
# ------------------------------------------------------------
#  产物：
#    dist\                        运行目录（jar + JNA + 自带 JRE）
#    dist-shell\MXCloud启动器.exe  单文件版
#    上级目录\MXCloud单文件版\      可直接分发的目录
#    上级目录\MXCloud单文件版.zip
#
#  用法：
#    pwsh -File 打包.ps1
#    pwsh -File 打包.ps1 -SkipJre      （不生成自带 JRE）
#    pwsh -File 打包.ps1 -SkipShell    （只做到 dist\）
# ============================================================

param(
    [switch]$SkipJre,
    [switch]$SkipShell
)

$ErrorActionPreference = "Stop"
$Here = $PSScriptRoot
if (-not $Here) { $Here = (Get-Location).Path }
$Root = Split-Path $Here -Parent

function Step($n, $total, $msg) {
    Write-Host ("`n=== [{0}/{1}] {2} ===" -f $n, $total, $msg) -ForegroundColor Cyan
}

# ---------- 1. 定位 JDK ----------
Step 1 4 "定位 JDK"
$jdkCandidates = @(
    "D:\JDK\jdk-21.0.12.1+1",
    "D:\JDK\jdk-22.0.2+9",
    "D:\JAVA",
    "C:\Program Files\Java\jdk-21"
)
if ($env:JAVA_HOME) { $jdkCandidates += $env:JAVA_HOME }
$JDK = $null
foreach ($c in $jdkCandidates) {
    if ($c -and (Test-Path (Join-Path $c "bin\javac.exe"))) { $JDK = $c; break }
}
if (-not $JDK) { throw "找不到 JDK 21+（需要 javac / jar / jlink）" }
Write-Host "  JDK: $JDK"
& "$JDK\bin\javac.exe" -version

# ---------- 2. 依赖（JNA） ----------
Step 2 4 "准备依赖"
$lib = Join-Path $Here "lib"
New-Item -ItemType Directory -Force -Path $lib | Out-Null
$jars = @{
    "jna-5.14.0.jar"          = "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.14.0/jna-5.14.0.jar"
    "jna-platform-5.14.0.jar" = "https://repo1.maven.org/maven2/net/java/dev/jna/jna-platform/5.14.0/jna-platform-5.14.0.jar"
}
foreach ($name in $jars.Keys) {
    $target = Join-Path $lib $name
    if (Test-Path $target) { Write-Host "  OK  $name"; continue }
    Write-Host "  下载 $name ..."
    curl.exe -sSL --max-time 180 -o $target $jars[$name]
    if (-not (Test-Path $target)) { throw "依赖下载失败: $name" }
    Write-Host "  OK  $name"
}

# ---------- 3. 编译 + 打包 jar ----------
Step 3 4 "编译 Java 源码"
$classes = Join-Path $Here "build\classes"
if (Test-Path $classes) { Remove-Item $classes -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classes | Out-Null

$cp = "$lib\jna-5.14.0.jar;$lib\jna-platform-5.14.0.jar"
$srcs = Get-ChildItem (Join-Path $Here "src\main\java\dev\mx\launcher") -Filter "*.java" |
        ForEach-Object { $_.FullName }
Write-Host "  源文件数: $($srcs.Count)"

$argf = Join-Path $Here "build\javac_args.txt"
$argLines = @("-encoding", "UTF-8", "-nowarn", "-d", $classes, "-cp", $cp) + $srcs
[System.IO.File]::WriteAllLines($argf, $argLines, [System.Text.UTF8Encoding]::new($false))

$prevEA = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$out = & "$JDK\bin\javac.exe" "@$argf" 2>&1
$javacRc = $LASTEXITCODE
$ErrorActionPreference = $prevEA
if ($javacRc -ne 0) {
    $out | Select-Object -First 30 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    throw "编译失败（exit=$javacRc）"
}
Write-Host "  编译成功，$((Get-ChildItem $classes -Recurse -Filter *.class | Measure-Object).Count) 个 class"

# 资源（版本号、图标、默认壁纸）一并打进 jar
$resources = Join-Path $Here "src\main\resources"
if (Test-Path $resources) {
    Copy-Item (Join-Path $resources "*") $classes -Recurse -Force
    Write-Host "  资源已合并: src\main\resources"
}

$mf = Join-Path $Here "build\MANIFEST.MF"
@"
Manifest-Version: 1.0
Main-Class: dev.mx.launcher.Main
Class-Path: jna-5.14.0.jar jna-platform-5.14.0.jar
Implementation-Title: MXCloud Launcher
Implementation-Version: 1.0.0

"@ | Set-Content -Path $mf -Encoding ASCII

$dist = Join-Path $Here "dist"
if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
New-Item -ItemType Directory -Force -Path $dist | Out-Null

Push-Location $classes
$ErrorActionPreference = "Continue"
& "$JDK\bin\jar.exe" cfm "$dist\mxaddon-launcher.jar" $mf . 2>&1 | Out-Null
$jarRc = $LASTEXITCODE
$ErrorActionPreference = $prevEA
Pop-Location
if ($jarRc -ne 0) { throw "jar 打包失败（exit=$jarRc）" }
Write-Host "  jar 已生成: $([math]::Round((Get-Item "$dist\mxaddon-launcher.jar").Length/1KB,1)) KB"

Copy-Item "$lib\jna-5.14.0.jar" $dist -Force
Copy-Item "$lib\jna-platform-5.14.0.jar" $dist -Force
foreach ($f in @("使用说明.txt", "启动.bat")) {
    if (Test-Path (Join-Path $Here $f)) { Copy-Item (Join-Path $Here $f) $dist -Force }
}
if (Test-Path (Join-Path $Here "shell\使用说明.txt")) {
    Copy-Item (Join-Path $Here "shell\使用说明.txt") $dist -Force
}

if (-not $SkipJre) {
    Write-Host "  生成精简 JRE ..."
    & "$JDK\bin\jlink.exe" `
        --add-modules java.base,java.desktop,java.logging,java.prefs,jdk.unsupported,java.management,jdk.crypto.ec `
        --strip-debug --no-header-files --no-man-pages --compress=zip-6 `
        --output "$dist\runtime" 2>&1 | Out-Null
    if (Test-Path "$dist\runtime\bin\javaw.exe") {
        $jreMb = [math]::Round((Get-ChildItem "$dist\runtime" -Recurse -File |
                 Measure-Object -Property Length -Sum).Sum / 1MB, 1)
        Write-Host "  自带 JRE 就绪: $jreMb MB"
    } else {
        throw "jlink 失败：未生成自带 JRE"
    }
}
Write-Host "  运行目录: $dist"

# ---------- 4. 单文件 exe ----------
Step 4 4 "生成单文件版"
if ($SkipShell) {
    Write-Host "  （-SkipShell 已跳过）"
} else {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Here "shell\build_shell.ps1")
    if ($LASTEXITCODE -ne 0) { throw "单文件版构建失败" }
}

Write-Host ""
Write-Host "打包完成" -ForegroundColor Green
Write-Host "  运行目录 : $dist"
if (-not $SkipShell) { Write-Host "  单文件版 : $(Join-Path $Root 'MXCloud单文件版\MXCloud启动器.exe')" }
