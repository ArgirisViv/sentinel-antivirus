param(
    [string]$Version = '0.1.0'
)

# Builds a double-clickable Sentinel AV app (no terminal needed) into dist\SentinelAV\.
# Requires: JDK 25 (jpackage), the C++ engine already built at build\sentinel-av.exe.
$ErrorActionPreference = 'Continue'
if ($Version -notmatch '^\d+(\.\d+){1,3}$') { throw "Invalid app version: $Version" }
$root = Split-Path -Parent $PSScriptRoot
$mgmt = Join-Path $root 'management'
$dist = Join-Path $root 'dist'
$stage = Join-Path $dist 'input'
$engine = Join-Path $root 'build\sentinel-av.exe'
if (-not (Test-Path $engine)) { throw "Engine not found: $engine. Build the C++ engine first." }

Push-Location $mgmt
try {
    & .\mvnw.cmd -q package -DskipTests
    if (-not (Get-ChildItem target\sentinel-management-*.jar)) { throw 'Maven package failed' }
    & .\mvnw.cmd -q dependency:copy-dependencies "-DoutputDirectory=target\libs" -DincludeScope=runtime
    if (-not (Test-Path target\libs)) { throw 'Could not copy dependencies' }
} finally { Pop-Location }

Remove-Item $dist -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory $stage | Out-Null
Copy-Item (Join-Path $mgmt 'target\sentinel-management-*.jar') $stage
Copy-Item (Join-Path $mgmt 'target\libs\*.jar') $stage

$content = Join-Path $dist 'content'
New-Item -ItemType Directory $content | Out-Null
Copy-Item $engine $content
foreach ($f in 'signatures.example.txt', 'config.example.ini') {
    $p = Join-Path $root $f
    if (Test-Path $p) { Copy-Item $p $content }
}

$icon = Join-Path $PSScriptRoot 'sentinel.ico'
$mainJar = (Get-ChildItem $stage -Filter 'sentinel-management-*.jar' | Select-Object -First 1).Name

& jpackage --type app-image --name SentinelAV --app-version $Version `
    --input $stage --main-jar $mainJar --main-class com.sentinelav.desktop.SentinelDashboard `
    --java-options '--module-path $APPDIR --add-modules javafx.controls' `
    --icon $icon --app-content $content --dest $dist --vendor 'Sentinel AV'
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed' }
Write-Host "Done: $dist\SentinelAV\SentinelAV.exe"
