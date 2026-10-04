# Sentinel AV Threat Model

## Scope and security claim

This threat model covers the local Windows prototype in this repository. It is
an educational endpoint-security demonstration, not a production antivirus or
an enforcement boundary. Detection is best-effort and must not be relied on to
protect a system.

## Assets

- User files being scanned
- Files explicitly moved to quarantine
- Local SHA-256 signature database
- Engine configuration and event log
- Integrity and availability of the C++ engine and Java management process

## Trust boundaries

- **JavaFX UI to C++ child process:** Local `ProcessBuilder` command arguments
  and captured standard-output/error pipes. Arguments are passed directly,
  without a command shell. Only the launched child is intended to receive the
  inherited output handles.
- **Engine to filesystem:** The engine reads file contents and metadata and can
  rename signature-matched files only when quarantine is explicitly enabled.
- **Configuration/signatures to engine:** Local user-controlled files. Invalid
  entries are rejected, but these inputs are not cryptographically signed.
- **Engine to event log:** Local append-only JSON Lines output. Log write
  failures are surfaced. The active log rotates at 10 MiB with one backup.
- **Monitoring APIs:** Windows filesystem notifications and process snapshots
  are untrusted, incomplete observations, not authoritative audit streams.

## Threats and mitigations

| Threat | Current mitigation | Remaining limitation |
|---|---|---|
| Malformed signatures or configuration cause unexpected parsing | Strict field/hash format, duplicate and unknown keys rejected | Configuration and signature authenticity are not verified |
| A signature match is silently moved | Quarantine is opt-in; only exact SHA-256 matches trigger it | Same-volume rename can fail; no encrypted storage or restore workflow |
| Other local users read newly quarantined files | New quarantine directories and moved files receive protected ACLs for the current user, SYSTEM, and Administrators | Existing quarantine directory ACLs are not changed; verify the warning and filesystem support |
| Heuristic false positives are treated as confirmed malware | Heuristic output uses separate `SUSPICIOUS` indicators and never auto-quarantines | Entropy, file extensions, and PE flags can produce benign findings |
| IPC exposes a network service | UI launches a local child and uses process streams; no listener is created | Local administrators or the same user can still inspect/control their processes |
| Event data is lost or log growth is unbounded | Synchronous writes surface failures; one 10 MiB log and one backup are kept | No tamper-evident chain, remote collector, or guaranteed durability after power loss |
| Real-time event is missed | Watcher reports buffer overflow and asks for a full scan | Filesystem watcher is best-effort; process polling misses short-lived processes |
| Crafted PE headers cause out-of-bounds parsing | Parser bounds-checks offsets and reads metadata only | PE parser has not received external fuzzing or broad corpus validation |

## Out of scope

- Kernel-level protection or blocking execution
- Credential theft, privilege escalation, or persistence detection
- Network monitoring or remote management
- Authenticated signature updates and rollback protection
- A hostile administrator or compromise of the current user's account

## Safe validation

Use only the harmless `abc` signature test vector and generated synthetic PE
metadata fixtures from the automated tests. Do not use live malware samples.
