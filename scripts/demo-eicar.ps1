# This opt-in demo creates a standard antivirus test file that security
# products may automatically quarantine or remove.
param(
    [switch]$RunEicarTest,
    [string]$Engine = ".\build\Release\sentinel-av.exe"
)

$ErrorActionPreference = "Stop"

if (-not $RunEicarTest) {
    throw "This demo creates the standard EICAR antivirus test file, which security products may quarantine. Review the script, then rerun with -RunEicarTest to opt in."
}

$enginePath = (Resolve-Path $Engine).Path
$demoRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
    "sentinel-eicar-demo-" + [System.Guid]::NewGuid().ToString("N"))
$fixture = Join-Path $demoRoot "eicar-test.txt"
$signatures = Join-Path $demoRoot "signatures.txt"

New-Item -ItemType Directory -Path $demoRoot | Out-Null
try {
    $payload = 'X5O!P%@AP[4\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*'
    [System.IO.File]::WriteAllBytes($fixture, [System.Text.Encoding]::ASCII.GetBytes($payload))
    if (-not (Test-Path -LiteralPath $fixture -PathType Leaf)) {
        throw "The EICAR test file is no longer present. Windows Security or another antivirus may have quarantined it. Run this opt-in demo only in a disposable environment intended for antivirus testing; do not disable protection on your everyday device."
    }
    [System.IO.File]::WriteAllText($signatures, '')

    Write-Output "Scanning the harmless EICAR test string with an empty signature database:"
    $output = & $enginePath scan $fixture --signatures $signatures 2>&1
    $exitCode = $LASTEXITCODE
    $output | ForEach-Object { Write-Output $_ }
    if (-not (Test-Path -LiteralPath $fixture -PathType Leaf)) {
        throw "The EICAR test file disappeared during scanning. Windows Security or another antivirus may have quarantined it, so the demo could not complete. Use a disposable environment intended for antivirus testing; do not disable protection on your everyday device."
    }
    if ($exitCode -ne 0) {
        throw "Scanner exited with code $exitCode."
    }
    if (($output -join "`n") -notmatch '\[DETECTED\].*EICAR-Test-File \(standard AV test string, not malware\)') {
        throw "The scanner did not report the EICAR test-file detection."
    }
    Write-Output "EICAR content detection succeeded without a matching signature."
    Write-Output "The temporary test file and signature will now be removed."
} finally {
    if (Test-Path -LiteralPath $demoRoot) {
        Remove-Item -LiteralPath $demoRoot -Recurse -Force
    }
}
