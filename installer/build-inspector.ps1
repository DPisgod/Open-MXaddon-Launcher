param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'

# Locate vcvars. vswhere may return nothing on some BuildTools installs, so scan the disk too.
function Find-Vcvars([string]$name) {
    $vswhere="${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
    if (Test-Path $vswhere) {
        foreach ($argumentList in @(
            @('-latest','-products','*','-requires','Microsoft.VisualStudio.Component.VC.Tools.x86.x64','-property','installationPath'),
            @('-latest','-products','*','-property','installationPath'))) {
            $installation = & $vswhere @argumentList 2>$null
            if ($installation) {
                $candidate = Join-Path $installation "VC\Auxiliary\Build\$name"
                if (Test-Path $candidate) { return $candidate }
            }
        }
    }
    foreach ($base in @("${env:ProgramFiles(x86)}\Microsoft Visual Studio", "${env:ProgramFiles}\Microsoft Visual Studio")) {
        if (-not (Test-Path $base)) { continue }
        $hit = Get-ChildItem $base -Recurse -Depth 6 -Filter $name -File -ErrorAction SilentlyContinue |
               Sort-Object FullName -Descending | Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }
    return $null
}

$vcvars = Find-Vcvars 'vcvars32.bat'
if (-not $vcvars) { throw 'MSVC Build Tools not found' }

# Build inside an ASCII-only temp directory: cl.exe cannot open Chinese paths here.
$work = Join-Path $env:TEMP 'mx-inspector-build'
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
New-Item -ItemType Directory -Force -Path $work | Out-Null
Copy-Item (Join-Path $PSScriptRoot 'inspect-install.cpp') (Join-Path $work 'inspect-install.cpp') -Force
$helper = Join-Path $work 'mx-inspect.exe'
$batch = Join-Path $work 'build.cmd'
$commands = "@echo off`r`ncall `"$vcvars`" >nul 2>&1`r`ncd /d `"$work`"`r`ncl /nologo /O2 /MT /EHsc /utf-8 /DUNICODE /D_UNICODE inspect-install.cpp /Fe:mx-inspect.exe /link bcrypt.lib version.lib`r`n"
[IO.File]::WriteAllText($batch, $commands, [Text.Encoding]::ASCII)
& cmd /c $batch | Out-Null
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $helper)) { throw 'Installation inspector compilation failed' }

$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
Copy-Item $helper (Join-Path $OutputDirectory 'mx-inspect.exe') -Force
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
