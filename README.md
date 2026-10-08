# Sentinel AV

<p align="center">
  <img src="management/src/main/resources/com/sentinelav/desktop/logo.png" alt="Sentinel AV shield logo" width="112">
</p>

<p align="center">
  <strong>A Windows endpoint-security prototype built with C++17 and JavaFX.</strong><br>
  Local scanning, explainable static indicators, best-effort monitoring, and a native desktop console.
</p>

<p align="center">
  <a href="https://github.com/ArgirisViv/sentinel-antivirus/actions/workflows/ci.yml">
    <img src="https://github.com/ArgirisViv/sentinel-antivirus/actions/workflows/ci.yml/badge.svg?branch=main" alt="Windows CI">
  </a>
  <img src="https://img.shields.io/badge/C%2B%2B-17-00599C?logo=cplusplus" alt="C++17">
  <img src="https://img.shields.io/badge/UI-JavaFX-4A90D9" alt="JavaFX">
  <img src="https://img.shields.io/badge/platform-Windows-0078D4?logo=windows" alt="Windows">
</p>

> **Honest scope:** Sentinel AV is an educational prototype, not production
> antivirus software. Scanning and monitoring are best-effort; it does not
> block threats or provide always-on protection.

## Preview

<p align="center">
  <img src="docs/images/demo.gif" alt="Sentinel AV animated desktop preview" width="88%">
</p>

## Highlights

- Native Windows C++17 scanner using Windows CNG SHA-256 and a strict, local
  signature database.
- Separates exact signature matches from explainable static indicators; risk
  scores are prioritization signals, not probabilities.
- Optional quarantine for exact matches, with restricted ACLs on newly created
  locations, local history, and hash-checked restore.
- Best-effort recursive file monitoring, process-start snapshots, and
  alert-only file-change burst heuristic.
- JavaFX desktop console supervises the local engine process and streams its
  output; no network listener or cloud service.
- Windows CI, automated C++/Java tests, a threat model, and architecture docs.

## Try it on Windows

Requirements: CMake 3.16+ and a C++17 Windows compiler (for example Visual
Studio Build Tools).

```powershell
cmake -S . -B build
cmake --build build --config Release
ctest --test-dir build -C Release --output-on-failure
.\scripts\demo-safe.ps1
```

The demo uses harmless text fixtures, including the well-known `abc` hash
vector, in a temporary directory. It demonstrates report-only scanning and
explicit quarantine, then removes its files. The EICAR test string is used
only by the separate, opt-in [EICAR demo](scripts/demo-eicar.ps1); it is not
bundled as a signature or specially detected by the scanner. Other antivirus
products may flag or quarantine that standard test file.

## Run the desktop app

Download the **Windows x64 ZIP** from [GitHub Releases](https://github.com/ArgirisViv/sentinel-antivirus/releases/latest),
extract the complete folder, and launch `SentinelAV.exe`. No separate Java
installation is needed for the packaged app.

To build it yourself, install JDK 25+ with `jpackage`, build the C++ engine,
then run:

```powershell
Copy-Item .\build\Release\sentinel-av.exe .\build\sentinel-av.exe
.\scripts\package-app.ps1
```

The app image is written to `dist\SentinelAV`. Keep its folder contents
together.

## Documentation

- [Usage and CLI reference](docs/usage.md)
- [Architecture](docs/architecture.md)
- [Threat model and limitations](docs/threat-model.md)

## Future work

Potential engineering follow-ups include broader benign-corpus validation,
parser fuzzing, measured scan-performance baselines, and stronger UI-level
automated tests. These are proposed improvements, not implemented product
claims.
