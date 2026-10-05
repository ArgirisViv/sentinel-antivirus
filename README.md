# Sentinel AV

Sentinel AV is a Windows-only portfolio project for learning endpoint-security
concepts. It provides a C++17 file scanner and recursive real-time filesystem
monitor with SHA-256 signatures and an explicit, opt-in quarantine action. It
is a demonstration, not a replacement for commercial antivirus software.

## Build

Requirements: Windows, CMake 3.16+, and a C++17 compiler. SHA-256 is provided by
Windows CNG (`bcrypt.dll`); no third-party packages are required.

```powershell
cmake -S . -B build
cmake --build build --config Release
ctest --test-dir build -C Release --output-on-failure
```

## Try the harmless test vector

The example database contains only the SHA-256 of the harmless text `abc`.
It is included to verify the scanner pipeline and is **not a malware signature**.

```powershell
[System.IO.File]::WriteAllText("$PWD\safe-test.txt", "abc")
.\build\Release\sentinel-av.exe scan .\safe-test.txt --signatures .\signatures.example.txt
```

For a single-config generator, the executable may be at `build\sentinel-av.exe`.

An optional key/value configuration file supplies defaults:

```powershell
.\build\Release\sentinel-av.exe scan .\safe-test.txt `
  --config .\config.example.ini
```

Supported keys are `signatures`, `log_file`, and `quarantine_directory`.
Relative values resolve from the configuration file's directory. Unknown or
duplicate keys are errors. Command-line `--signatures` and `--log` values
override the configuration. `--quarantine [directory]` remains opt-in; without
a directory argument it uses `quarantine_directory` from the config.

When `log_file` is configured, the engine appends structured JSON Lines events
with UTC timestamps, severity, event name, message, and path. The active log is
rotated at 10 MiB, keeping one `.1` backup. Log write failures are reported
instead of silently ignored.

To move a detected test file into quarantine, opt in explicitly:

```powershell
.\build\Release\sentinel-av.exe scan .\safe-test.txt `
  --signatures .\signatures.example.txt --quarantine .\quarantine
```

To monitor a directory recursively, start the watcher and create or modify a
file in the watched directory from another terminal:

```powershell
.\build\Release\sentinel-av.exe watch .\watched `
  --signatures .\signatures.example.txt
```

The monitor processes added, modified, and renamed-in files after their size
and last-write time remain stable. It skips symbolic links and directories.
It does not scan files that already existed when monitoring started; run `scan`
first for a baseline. Press Ctrl+C to stop. If Windows reports
notification-buffer overflow, the monitor exits with an error; run a full
`scan` to cover any missed changes. Real-time monitoring is best-effort and is
not a security boundary.

To monitor new processes:

```powershell
.\build\Release\sentinel-av.exe processes `
  --signatures .\signatures.example.txt
```

The process monitor polls Windows process snapshots once per second. It records
the currently running processes as a baseline and reports later process starts,
then scans each accessible executable image against the local signatures.
Processes already running at startup are not scanned. Windows may deny access
to protected or elevated processes; these are reported and their image scan is
skipped. This polling approach can miss very short-lived processes. It reports
detections only and does not terminate processes.

Quarantine uses a same-volume rename and does not execute or inspect files
behaviorally. On Windows, newly-created quarantine directories and quarantined
files receive protected ACLs for the current user, LocalSystem, and local
Administrators. Existing quarantine-directory ACLs are not modified and trigger
a warning; verify them before use. The directory must not be the scan target or
one of its parents. Use a quarantine directory on the same volume as the file
being moved. Review its contents before restoring or deleting files.

## JavaFX management dashboard

The Java desktop dashboard launches the C++ executable as a child process and
communicates through its command-line arguments and private parent/child
standard-output pipes (`ProcessBuilder`). It does not use a TCP listener, named
pipe server, or network port; command arguments are passed directly without a
shell.

Requirements: JDK 25+. Build the C++ engine first using the instructions above,
then run the Java tests or launch the dashboard from the repository root:

```powershell
cd management
.\mvnw.cmd test
.\mvnw.cmd javafx:run
```

The checked-in Maven Wrapper downloads the pinned Apache Maven distribution on
first use; no system-wide Maven installation is required.

The dashboard uses a rounded, dark plum-and-navy security-center layout with
magenta accents, a fixed sidebar, compact top bar, status banner, and six action
cards. The cards open:

- **Quick scan:** scans the current user's Downloads folder only.
- **Full scan:** asks you to choose a drive or folder, confirms the selection,
  then recursively scans that location. Runtime and coverage depend on the
  selected location and Windows file permissions; this does not promise a
  complete scan of every volume.
- **Real-time monitoring:** opens manual folder and process monitoring controls.
  Monitoring is best-effort and only runs after you start it.
- **Threat history:** shows engine output and events for the current app
  session; the Java dashboard does not persist a threat database.
- **Quarantine:** configures opt-in movement of exact signature matches. The
  dashboard does not yet provide a quarantine browser, restore, or delete UI.
- **Process monitor:** opens the process-monitor controls.

Summary counters reflect the current scan/session, not lifetime totals. The
status banner says the device is ready or shows the current scan/engine state;
it does not claim that the device is safe or continuously protected. There is
no always-on protection, signature updater, VPN, firewall, privacy module, or
support service in this prototype. Engine paths, signatures, and optional
configuration are in Settings. The engine path defaults to
`build\Release\sentinel-av.exe` or `build\sentinel-av.exe` when present.

The dashboard can run one engine command at a time, show live output, and keep
a short in-memory event history for the current session. Monitoring is
user-started and best-effort; it is not always-on protection. Stop or closing
the dashboard stops the Sentinel AV child process and does not terminate
processes being monitored.

The Java EngineClient tests cover child-process output capture, exit codes,
exclusive engine operation, stop requests, and missing-executable errors.

## Signature database

The default database path is `signatures.txt`; create it from the example or
specify another file with `--signatures`. Each non-comment line must have this
format:

```text
<64 hexadecimal SHA-256 characters><TAB><non-empty label>
```

Blank lines and lines beginning with `#` are ignored. Invalid and duplicate
hashes are reported as errors; matching is exact and case-insensitive.

## Portfolio materials

- [Architecture overview](docs/architecture.md)
- [Threat model](docs/threat-model.md)
- [Safe demo script](scripts/demo-safe.ps1)

The demo script creates only harmless text files in a temporary directory,
scans them, demonstrates explicit quarantine of the `abc` test vector, and
removes its temporary files afterward.

GitHub Actions CI is defined in `.github/workflows/ci.yml`; it builds and runs
the C++ tests on Windows and runs the Java tests.

## Static analysis indicators

The scanner reports SHA-256 matches as `[DETECTED]`. Separate `[SUSPICIOUS]`
indicators are heuristic context only and are never treated as signature
detections or automatically quarantined:

- Active/script-oriented extensions and executable names with selected
  document/image double extensions
- Shannon byte entropy of the first 1 MiB, only for samples at least 4 KiB,
  with an indicator threshold of 7.2 bits per byte
- PE header metadata (architecture and section count), malformed/truncated PE
  headers, unusual section counts, unknown architecture, and sections marked
  both writable and executable
- Process executable images located beneath the current user's temporary,
  Downloads, or local AppData directories

These signals are intentionally conservative and can flag legitimate software,
scripts, installers, compressed, or encrypted files. PE parsing reads metadata
only; files are never loaded or executed by the analyzer. This is not behavioral
analysis and does not establish that a file is malicious.

## Current scope

- Recursive file traversal without following symbolic links
- SHA-256 through Windows CNG
- Strict local hash-signature database
- Detection reporting and opt-in quarantine by rename
- Recursive real-time monitoring of added, modified, and renamed-in files
- Process-start monitoring and executable signature scans
- Static entropy, extension, and PE metadata indicators
- Strict key/value engine configuration with config-relative paths
- Structured, rotating JSONL event logging
- Restricted ACLs on event logs and quarantined files; new quarantine
  directories are ACL-hardened
- Local parent/child pipe communication without a network listener
- Non-zero exit status on scan errors or invalid invocation

A Windows service, signature updates, stronger process telemetry, and deeper
behavioral analysis are planned for later phases and are not implemented.
