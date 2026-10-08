# Sentinel AV: usage and technical reference

## Scanner examples

The C++ engine uses Windows CNG (`bcrypt.dll`) for SHA-256; no third-party
runtime packages are required.

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

Quick Scan checks the current user's Downloads folder, temporary directory,
and Startup folder:

```powershell
.\build\Release\sentinel-av.exe quick-scan `
  --signatures .\signatures.example.txt
```

Missing optional locations are reported as skipped; discovery or access errors
are reported in the summary and produce a nonzero exit status. Quick Scan does
not scan all of AppData or active process images. As with other scans, it only
reports detections unless exact-signature quarantine is explicitly enabled.

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

To inspect the current process ancestry as a point-in-time snapshot:

```powershell
.\build\Release\sentinel-av.exe process-tree `
  --config .\config.example.ini
```

The process tree shows PID, parent PID, and executable name. It is an
unprivileged snapshot, not continuous telemetry; it does not inspect memory,
network activity, loaded DLLs, or terminate processes.

Quarantine uses a same-volume rename and does not execute or inspect files
behaviorally. On Windows, newly-created quarantine directories and quarantined
files receive protected ACLs for the current user, LocalSystem, and local
Administrators. Existing quarantine-directory ACLs are not modified and trigger
a warning; verify them before use. The directory must not be the scan target or
one of its parents. Use a quarantine directory on the same volume as the file
being moved. Each moved item receives local metadata containing its SHA-256,
signature label, original path, and UTC timestamp. The Java dashboard can list
these entries and restore a selected item. Restore refuses to overwrite an
existing path and requires the original parent directory to remain available.
The content hash is rechecked before restore. The metadata is local and is not
cryptographically authenticated; protect the quarantine directory and review
the source before restoring.

Quarantine history can also be inspected or restored from the CLI:

```powershell
.\build\Release\sentinel-av.exe quarantine list `
  --config .\config.example.ini
.\build\Release\sentinel-av.exe quarantine restore <id> `
  --config .\config.example.ini
```

During manual folder monitoring, Sentinel reports a ransomware-like burst
alert when 32 distinct observed file paths change within 10 seconds. This is a
coarse, alert-only heuristic: it cannot attribute writes to a process, may
produce false positives, and does not stop the process or roll back files.
Filesystem notifications can be missed or overflow; this is not ransomware
prevention.

## JavaFX management dashboard

The Java desktop dashboard launches the C++ executable as a child process and
communicates through its command-line arguments and private parent/child
standard-output pipes (`ProcessBuilder`). It does not use a TCP listener, named
pipe server, or network port; command arguments are passed directly without a
shell.

Requirements: JDK 25+. Build the C++ engine first using the instructions above,
then run the Java tests or launch the dashboard:

```powershell
Set-Location .\management
.\mvnw.cmd test
.\mvnw.cmd javafx:run
```

The checked-in Maven Wrapper downloads the pinned Apache Maven distribution on
first use; no system-wide Maven installation is required.

The dashboard includes a branded startup screen, animated cybersecurity
background, and a dark graphite interface with teal accents. Its pages are:

- **Dashboard:** scan status, recent activity, and quick actions.
- **Scanner:** start a Quick Scan, select a folder or file, and follow live
  engine output. Quick Scan checks the current user's Downloads, temporary,
  and Startup folders. Missing locations are skipped and reported;
  inaccessible locations produce errors. It does not scan all of AppData or
  active process images.
- **Monitoring:** start and stop the optional folder and process monitoring.
  Monitoring is best-effort and only runs after you start it.
- **Activity:** review engine output and events for the current app session;
  the Java dashboard does not persist a threat database.
- **Quarantine:** configures opt-in movement of exact signature matches. The
  dashboard provides local history and a restore action; it does not delete
  quarantined files.
- **Settings:** configure engine, signatures, and optional configuration paths.

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

### Launch without a terminal (Windows)

To create a double-clickable app image with a bundled Java runtime, JDK 25+
(including `jpackage`) is required. First build the C++ engine. The packaging
script expects it at `build\sentinel-av.exe`; if a multi-configuration CMake
generator placed it at `build\Release\sentinel-av.exe`, copy it into that
expected location:

```powershell
Copy-Item .\build\Release\sentinel-av.exe .\build\sentinel-av.exe
.\scripts\package-app.ps1
```

The launcher is created at `dist\SentinelAV\SentinelAV.exe`. Open that file or
create a Windows shortcut to it; keep the complete `dist\SentinelAV` folder
together because it contains the application runtime and bundled engine. The
generated `dist` directory is ignored by Git. Re-run the packaging script after
changing the dashboard or rebuilding the engine. This creates an app image,
not a Setup installer.

## Docker

The [Dockerfile](../Dockerfile) works with the default Linux-container mode of
Docker Desktop. Because the engine uses Windows APIs, the image cross-compiles
it to `sentinel-av.exe` with MinGW-w64 and runs it under Wine. The C++ tests run
during the image build, also under Wine. Wine is an emulation layer, so use the
image to build, test, and demonstrate the scanner; native Windows remains the
reference platform. The JavaFX window is a desktop application and is not run in
a container. Use the packaged Windows app above for the graphical dashboard;
Docker is for the scanner engine and its mounted-volume demonstrations.

```powershell
# Build the image (cross-compiles the engine and runs the C++ tests)
docker build -t sentinel-av .

# Scan a host folder, mounted read-only, with the example signatures
docker run --rm -v C:\path\to\scan:/data:ro sentinel-av `
  scan /data --signatures /sentinel/signatures.example.txt

# Run the Java management unit tests (JDK 25)
docker build --target management-test .
```

The container scans only files that are mounted into it. It does not see the
host's files or processes, so process monitoring there is not meaningful.

### Always-running monitor container

`docker compose up -d --build` starts a `sentinel-monitor` container that stays
visible under **Containers** in Docker Desktop and runs the engine's `watch`
command on a Docker volume (`/data`). Monitoring is best-effort and covers only
that volume. Drop a harmless test file in and read the result in the container
logs:

```powershell
Set-Content -NoNewline -Path $env:TEMP\safe.txt -Value abc
docker cp $env:TEMP\safe.txt sentinel-monitor:/data/safe.txt
docker logs sentinel-monitor
docker compose down        # stop it (add -v to also delete the volume)
```

A named volume is used because Docker Desktop does not forward file-change
events from Windows bind mounts into Linux containers, so files created
directly in a host folder would not be noticed.

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

- [Architecture overview](architecture.md)
- [Threat model](threat-model.md)
- [Safe demo script](../scripts/demo-safe.ps1)
- [Opt-in EICAR demo](../scripts/demo-eicar.ps1)

The demo script creates only harmless text files in a temporary directory,
scans them, demonstrates explicit quarantine of the `abc` test vector, and
removes its temporary files afterward.

[GitHub Actions CI](../.github/workflows/ci.yml) builds and runs the C++ tests
on Windows and runs the Java tests.

The optional EICAR script creates the standard harmless test string and a
matching temporary local hash signature, runs a report-only scan, and removes
its temporary files. Run it only after reviewing it and explicitly opt in:

```powershell
.\scripts\demo-eicar.ps1 -RunEicarTest
```

Windows Security or another antivirus may alert on or quarantine this test
file; that is expected behavior. The string is not bundled in the example
signature database, and the scanner has no EICAR-specific detection rule.

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

## Explainable risk classification

Files with configured indicators emit a `[RISK]` record containing an integer
score, severity, broad evidence category, confidence basis, and the reasons
that contributed. The score is a **rule-based prioritization value, not a
probability or a claim that a file is malware**. Correlated findings in the
same category contribute only their strongest weight; distinct categories can
combine. Current weights are: active-content extension (5), high entropy (15),
executable masquerading (25), suspicious PE structure (up to 30), a
PE-like extension on a non-PE file (35), and malformed PE headers (45),
capped at 99.

Heuristic severities are `LOW` (1–19), `MEDIUM` (20–39), `HIGH` (40–69), and
`CRITICAL` (70–99). A score of 0 means only that no configured indicator fired;
it is not a safety verdict. An exact hash hit is separately reported as
`SIGNATURE_MATCH` with score 100. It means only that the hash matches the
configured local database; database contents and labels are not independently
authenticated, and are not verified malware-family or cloud-reputation data.

## Current scope

- Recursive file traversal without following symbolic links
- SHA-256 through Windows CNG
- Strict local hash-signature database
- Detection reporting and opt-in quarantine by rename
- Recursive real-time monitoring of added, modified, and renamed-in files
- Alert-only ransomware-like burst heuristic for 32 distinct observed paths
  within 10 seconds (no process attribution, blocking, or rollback)
- Process-start monitoring and executable signature scans
- Point-in-time process ancestry snapshot
- Local quarantine metadata, history listing, and no-overwrite restore
- Static entropy, extension, and PE metadata indicators
- Strict key/value engine configuration with config-relative paths
- Structured, rotating JSONL event logging
- Restricted ACLs on event logs and quarantined files; new quarantine
  directories are ACL-hardened
- Local parent/child pipe communication without a network listener
- Non-zero exit status on scan errors or invalid invocation

A Windows service, kernel minifilter, pre-execution blocking, authenticated
signature updates, process-attributed behavior, network/web protection,
cloud reputation, ML, and rollback are not implemented. Sentinel remains an
educational local prototype, not a replacement for Windows Security or a
production antivirus.
