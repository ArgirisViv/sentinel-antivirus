# Sentinel AV Architecture

Sentinel AV is a Windows-only learning project. The C++ engine owns file
scanning and local monitoring. The JavaFX application is a local management
client that starts the engine as a child process.

```mermaid
flowchart TB
    User[Local user]
    UI[JavaFX management dashboard]
    CLI[C++ Sentinel AV CLI]
    Hash[Windows CNG SHA-256]
    Signatures[Local hash signature file]
    Static[Static indicators<br/>entropy, extensions, PE metadata]
    Files[Local filesystem]
    Watcher[ReadDirectoryChangesW watcher]
    Processes[Windows process snapshot monitor]
    Quarantine[Quarantine directory<br/>restricted Windows ACL]
    EventLog[JSON Lines event log<br/>10 MiB rotation]

    User --> UI
    UI -->|ProcessBuilder arguments| CLI
    CLI -->|Captured stdout/stderr pipe| UI
    CLI --> Hash
    Hash --> Files
    CLI --> Signatures
    CLI --> Static
    CLI --> Files
    CLI --> Watcher
    CLI --> Processes
    CLI -->|Opt-in signature match only| Quarantine
    CLI --> EventLog
```

## Components

- **C++ CLI:** `scan`, `watch`, and `processes` commands. File signature
  matches are definitive detections; static heuristics are separate indicators.
- **Hash and signatures:** SHA-256 through Windows CNG, compared against a
  strict local text database.
- **Static analysis:** Samples at most 1 MiB for entropy and reads bounded PE
  headers/section flags without loading or executing a file.
- **Monitoring:** Recursive filesystem notifications and periodic process
  snapshots. Both are best-effort and can miss events.
- **Quarantine:** Explicit opt-in, same-volume rename. Newly-created
  quarantine directories and moved files receive protected ACLs for the
  current user, LocalSystem, and local Administrators.
- **Configuration and events:** Strict key/value config; append-only JSONL
  events with one 10 MiB active log and one rotated backup.
- **JavaFX client:** Starts a local engine process with `ProcessBuilder`, reads
  its output pipe, and provides controls and event display. No TCP server,
  remote API, or network listener is implemented.

## Data flow

1. A user starts a CLI operation directly or through the JavaFX client.
2. The engine validates configuration and loads the local signature database.
3. A scan gathers static indicators, hashes each regular file, and reports
   matching signatures separately from heuristic context.
4. Quarantine, when explicitly enabled, moves only hash-matched files and
   applies a restricted ACL.
5. Structured event records are appended to the configured local log.

## Operational limitations

The engine is not a Windows service, does not prevent file execution, and does
not provide a kernel driver, cloud reputation, signature updates, or guaranteed
real-time coverage. Process monitoring polls snapshots and may miss short-lived
processes. Run a full scan for a baseline and after watcher overflow.
