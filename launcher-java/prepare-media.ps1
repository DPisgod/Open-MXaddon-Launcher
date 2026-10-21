param([Parameter(Mandatory=$true)][string]$RuntimeDirectory)
$ErrorActionPreference = 'Stop'

# Pin an LGPL shared build and verify it before extracting executable code.
$url = 'https://github.com/BtbN/FFmpeg-Builds/releases/download/autobuild-2026-10-21-13-06/ffmpeg-n9.0.2-31-gc89d6ee1ca-win64-lgpl-shared-9.0.zip'
$expected = '3c645c674a592f3eb9493617710839c0fc6f824033aaf850874c050616ac554f'
$cache = Join-Path $PSScriptRoot '.media-cache'
New-Item -ItemType Directory -Force -Path $cache | Out-Null
$download = Join-Path $cache 'ffmpeg-lgpl.zip'
if (-not (Test-Path -LiteralPath $download)) {
    $partial = Join-Path $cache 'ffmpeg-lgpl.download'
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $partial
    if ((Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {
        throw 'FFmpeg checksum mismatch'
    }
    Move-Item -LiteralPath $partial -Destination $download -Force
}
if ((Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {
    throw 'Cached FFmpeg checksum mismatch'
}
$media = Join-Path $RuntimeDirectory 'Media'
New-Item -ItemType Directory -Force -Path $media | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($download)
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '/bin/(ffmpeg\.exe|[^/]+\.dll)$') {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $media $entry.Name),$true)
        } elseif ($entry.FullName -match '/LICENSE\.txt$') {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $media 'FFmpeg-LICENSE.txt'),$true)
        }
    }
} finally { $archive.Dispose() }
$wallpapers = Join-Path $media 'BuiltInWallpapers'
New-Item -ItemType Directory -Force -Path $wallpapers | Out-Null
Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'assets/builtin-wallpapers') -File |
    Copy-Item -Destination $wallpapers -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'assets/FFmpeg-NOTICE.txt') -Destination $media -Force
$ffmpegVersion = & (Join-Path $media 'ffmpeg.exe') -version
if ($LASTEXITCODE -ne 0) { throw 'Bundled FFmpeg could not start' }
$ffmpegVersion | Select-Object -First 1 | Write-Host
Write-Host 'Bundled media decoder and wallpapers are ready.'
