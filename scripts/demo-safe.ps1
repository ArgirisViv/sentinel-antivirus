param(
    [string]$Engine = ".\build\Release\sentinel-av.exe",
    [string]$Signatures = ".\signatures.example.txt"
)

$ErrorActionPreference = "Stop"

$enginePath = (Resolve-Path $Engine).Path
$signaturePath = (Resolve-Path $Signatures).Path
$demoRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
    "sentinel-safe-demo-" + [System.Guid]::NewGuid().ToString("N"))
$scanTarget = Join-Path $demoRoot "scan-target"
$quarantine = Join-Path $demoRoot "quarantine"

New-Item -ItemType Directory -Path $scanTarget -Force | Out-Null
try {
    [System.IO.File]::WriteAllText(
        (Join-Path $scanTarget "safe-test-vector.txt"),
        "abc")
    [System.IO.File]::WriteAllText(
        (Join-Path $scanTarget "ordinary-note.txt"),
        "Harmless Sentinel AV demo content.")

    Write-Output "Report-only scan using harmless text fixtures:"
    & $enginePath scan $scanTarget --signatures $signaturePath
    if ($LASTEXITCODE -ne 0) {
        throw "Report-only scan failed with exit code $LASTEXITCODE."
    }

    Write-Output "Explicit quarantine demonstration using the same harmless fixture:"
    & $enginePath scan $scanTarget --signatures $signaturePath --quarantine $quarantine
    if ($LASTEXITCODE -ne 0) {
        throw "Quarantine demonstration failed with exit code $LASTEXITCODE."
    }
    if (-not (Test-Path $quarantine)) {
        throw "The expected quarantine directory was not created."
    }
    Write-Output "The quarantine contains only the harmless test-vector text file."
} finally {
    if (Test-Path $demoRoot) {
        Remove-Item -LiteralPath $demoRoot -Recurse -Force
    }
}
