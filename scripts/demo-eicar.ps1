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
    [System.IO.File]::WriteAllText($fixture, $payload)
    $hash = (Get-FileHash -LiteralPath $fixture -Algorithm SHA256).Hash.ToLowerInvariant()
    [System.IO.File]::WriteAllText($signatures, "$hash`teicar-test-vector")

    Write-Output "Scanning the harmless EICAR test string against a generated local SHA-256 signature:"
    & $enginePath scan $fixture --signatures $signatures
    Write-Output "Scanner exit code: $LASTEXITCODE"
    Write-Output "The temporary test file and signature will now be removed."
} finally {
    if (Test-Path -LiteralPath $demoRoot) {
        Remove-Item -LiteralPath $demoRoot -Recurse -Force
    }
}
