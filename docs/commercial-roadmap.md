# Sentinel AV Commercial Product Roadmap

## Product direction

The intended first product is a paid antivirus for home users on Windows 11 and
on Windows 10 devices only while they remain eligible for and receive security
updates under Microsoft's applicable program. Cloud services are allowed for
signed intelligence updates and optional reputation lookups. The protection
target includes real-time blocking backed by a Windows service and a
Microsoft-signed kernel component. The current repository is a prototype and
is not suitable for protecting or marketing as a commercial antivirus.

This roadmap is a gated product-development plan, not a claim of certification,
security, or feature completeness. The original 30-item wishlist is now mapped
one-to-one below; measurable thresholds and some product-scope decisions
remain open.

## Current coverage baseline

The following inventory preserves all 30 capabilities from the original
wishlist. `Prototype` means limited behavior exists in this repository;
`Absent` means no implementation was identified. No row is commercially
verified. The release wave is a proposed order, not a promise that a capability
is safe to ship by itself. The baseline is **0 commercially verified, 17
partially represented by prototype behavior, and 13 not implemented**.

| ID | Capability and commercial acceptance outcome | Current prototype coverage | Proposed wave |
|---|---|---|---|
| REQ-001 | **Real-time protection:** newly created, changed, downloaded, executed, removable-media, script, DLL, and process activity is observed and policy-checked before harmful activity proceeds; protection state and failures are visible. | **Prototype:** best-effort user-mode file notifications and process polling. No execution/file blocking, DLL/script/browser/USB coverage, or guarantee against missed events. | P0 — Stage 3 |
| REQ-002 | **Multi-layer detection:** known signatures, authenticated hash reputation, static heuristics, and behavioral evidence produce sourced, explainable, versioned verdicts with confidence and safe offline behavior. | **Prototype:** local SHA-256 database, bounded static indicators, and rule-based file risk score. No authenticated intelligence, hash-reputation service, or integrated behavior verdict. | P0 — Stage 2 |
| REQ-003 | **Behavioral detection:** suspicious process chains and actions are correlated to a process identity and result in a tested, policy-controlled response with forensic evidence. | **Prototype:** a coarse file-change burst alert; no reliable process attribution or enforcement. | P0 — Stage 3 |
| REQ-004 | **Ransomware protection:** destructive file activity is interrupted, the responsible process is handled, evidence is retained, and optional recovery restores only verified affected files. | **Prototype:** alert-only burst heuristic. No file-access blocking, process stop, isolation, or rollback. | P0 — Stage 3 |
| REQ-005 | **Anti-spyware:** credential theft, keylogging, screen capture, browser credential access, suspicious persistence, and remote-access threats are detected with evidence and safe response. | **Absent** as a dedicated detection or response capability. | P1 — Stages 2–4 |
| REQ-006 | **Threat-family classification:** detections are classified into supported families/types (for example ransomware, trojan, worm, RAT, infostealer, and PUA) with evidence-backed confidence rather than unsupported labels. | **Prototype:** signature labels and generic static indicators; no validated threat taxonomy or classification quality. | P1 — Stage 2 |
| REQ-007 | **Network protection:** process-attributed DNS/IP/domain connections are reputation-checked and suspicious connections can be blocked with a visible reason. | **Absent.** | P1 — Stage 4 |
| REQ-008 | **Anti-phishing:** supported browser navigation and URLs are checked for phishing indicators before credential submission, with a clear warning and safe override policy. | **Absent.** | P1 — Stage 4 |
| REQ-009 | **Download protection:** downloads are identified, scanned using local and optional cloud intelligence, and prevented from execution when policy requires it. | **Prototype:** Quick Scan covers Downloads during an on-demand scan only; it does not observe downloads or block execution. | P0 — Stage 3 |
| REQ-010 | **USB protection:** removable volumes are identified, scanned according to policy, protected from autorun abuse, and report detections before user access where supported. | **Absent.** | P1 — Stage 3 |
| REQ-011 | **Full, Quick, and Custom Scan:** users can run clearly scoped scans of common locations, processes/startup, selected paths, removable media, and full volumes, with progress, cancellation, errors, and a shell scan action. | **Prototype:** recursive path scan and Quick Scan for current-user Downloads, Temp, and Startup. No process-image inclusion in Quick Scan, full commercial scan controls, or shell integration. | P0 — Stage 2 |
| REQ-012 | **Startup protection:** startup folders, registry run entries, services, scheduled tasks, and supported boot persistence are inventoried, risk-assessed, and safely remediated. | **Prototype:** Quick Scan scans the current user's Startup folder only. | P1 — Stage 3 |
| REQ-013 | **Process monitor:** users can inspect process identity, parent, CPU/memory, publisher, network connections, children, and evidence-based risk with refresh and error states. | **Prototype:** process-start polling and executable signature checks; no complete live process inventory or requested resource/network fields. | P1 — Stage 3 |
| REQ-014 | **Process tree:** ancestry and relevant file/network/security events are correlated into a navigable, time-aware attack chain. | **Prototype:** point-in-time PID/parent snapshot only. | P1 — Stage 3 |
| REQ-015 | **Quarantine system:** policy-authorized threats are isolated from execution, with durable metadata, history, integrity checks, safe restore, and explicit failure/recovery handling. | **Prototype:** opt-in move of exact hash matches, local metadata, hash-checked restore, and no-overwrite behavior. No general behavior-based isolation or full crash/tamper recovery. | P0 — Stage 2 |
| REQ-016 | **Automatic remediation:** supported policy actions (quarantine, block, terminate, disable persistence, block network, restore) are authorized, auditable, reversible where possible, and fail safely. | **Prototype:** optional quarantine of exact signature matches only. Other actions are absent. | P0 — Stages 2–3 |
| REQ-017 | **Tamper protection:** unauthorized users/processes cannot disable protection, alter policy/intelligence, or delete evidence; authorized update/recovery paths remain available and auditable. | **Prototype:** restrictive ACL handling for some newly created quarantine/log files. No protected service, comprehensive tamper defense, or recovery workflow. | P0 — Stages 1–3 |
| REQ-018 | **Cloud threat intelligence:** authenticated, privacy-minimized reputation/update services return versioned, auditable results and degrade safely when offline. | **Absent.** Cloud use was approved as a direction, not implemented. | P1 — Stage 4 |
| REQ-019 | **AI / Machine Learning:** models use validated file/behavior features, produce calibrated and monitored results, and remain one explainable detection layer rather than an unreviewable sole authority. | **Absent.** | P2 — Stage 4, only after validated data and evaluation |
| REQ-020 | **Exploit protection:** supported exploitation indicators such as injection, hollowing, and suspicious memory operations are detected and policy-handled with bounded false positives. | **Absent.** | P1 — Stage 3 |
| REQ-021 | **Memory protection:** fileless and in-memory threats are monitored and mitigated with process-attributed evidence and safe recovery. | **Absent.** | P1 — Stage 3 |
| REQ-022 | **Security dashboard:** the user sees truthful protection state, scan results, threats, processes, and network state without an unsupported “safe” claim. | **Prototype:** JavaFX dashboard and local engine controls. It does not represent real-time enforcement or the absent protection modules. | P0 — Stage 5 |
| REQ-023 | **Security Center:** each protection module reports actual enabled/degraded/failed state and offers safe recovery without claiming unavailable capabilities are active. | **Absent** as an operational status center; the existing UI is a prototype dashboard. | P0 — Stage 5 |
| REQ-024 | **Threat timeline:** timestamped download, scan, behavior, block, quarantine, and network events are correlated into an explainable incident sequence. | **Prototype:** short in-memory UI event history and local engine logs; no correlated incident timeline. | P1 — Stage 5 |
| REQ-025 | **Security score:** any aggregate score is derived from transparent, verifiable protection posture, explains its components, and never presents an unscanned device as safe. | **Prototype:** file-level heuristic risk score only; no device security score. | P2 — Stage 5 |
| REQ-026 | **Vulnerability scanner:** supported application versions, missing security updates, and insecure configurations are identified from trusted data and linked to safe remediation guidance. | **Absent.** | P2 — Stage 5 or a separately scoped product module |
| REQ-027 | **Privacy protection:** supported camera/microphone access and browser privacy signals are surfaced with clear OS limitations, consent, and minimal collection. | **Absent.** | P2 — Stage 5, subject to Windows capability/privacy review |
| REQ-028 | **Gamer Mode:** resource-heavy scheduled work and noncritical notifications are reduced while real-time protection remains enabled and visible. | **Absent.** | P2 — Stage 5 |
| REQ-029 | **Performance monitor:** CPU, memory, disk, and network impact are measured accurately, with representative workloads meeting agreed budgets. | **Absent** as a product measurement feature; no commercial impact benchmarks exist. | P1 — Stage 5 and all release gates |
| REQ-030 | **Reports / forensics:** each incident report records the detection source, file/hash, confidence, time, process ancestry, network evidence, and actions actually completed, with export and integrity controls. | **Prototype:** local JSONL engine events and basic quarantine metadata; no correlated, comprehensive incident reports. | P1 — Stage 5 |

The priority and wave labels are initial planning proposals. Every requirement
needs measurable thresholds and a test design during Stage 0. `Prototype` is
not equivalent to shipped, certified, or complete.

## Delivery stages and release gates

## Agreed product gates and policies

The following initial requirements were explicitly agreed during product
planning. They are internal release gates, not independent-lab ratings or
guarantees of real-world detection:

- **Release sequencing:** the first commercial release is staged around the
  core antivirus product (trusted updates, scanning/detection, quarantine,
  real-time blocking, tamper protection, and a truthful status dashboard).
  Network/web features, ML, privacy extras, Gamer Mode, and vulnerability
  scanning are planned for later releases rather than forced into v1.
- **Known-threat detection:** at least 95% detection on a documented,
  legally-obtained, held-out test corpus, with results reported separately by
  threat family and detection layer. Corpus composition, sample freshness,
  repetitions, and confidence intervals must be independently reviewed before
  release claims are made.
- **False positives:** fewer than 1 false positive per 100,000 benign files in
  the agreed clean corpus, and zero false positives against the explicitly
  defined critical Windows/system-file set. Detection and false-positive
  thresholds must be measured together at the same policy/settings.
- **Idle performance:** p95 CPU usage below 1% and working set below 200 MB on
  a representative home-PC baseline of Windows 11, a 4-core CPU, 8 GB RAM,
  and SSD storage, while real-time protection is enabled and the device is
  idle. A lower-spec supported system must also be included in compatibility
  tests. Active-scan impact, boot impact, and interactive latency require
  separate workload benchmarks.
- **Cloud and privacy:** local protection continues offline. Default reputation
  checks send only file hashes and the minimum metadata required for the
  service; file contents are never uploaded without separate, explicit
  consent. Hashes and user identifiers are not persistently retained by the
  cloud service; only anonymous aggregate metrics may be retained. Any
  temporary identifiable logging requires separate, explicit consent.
- **Protection failure:** service/driver failure uses fail-open for home-user
  availability, immediately reports a degraded protection state, and attempts
  documented recovery. Any exceptions to this policy must be approved and
  tested before release; failure must never be shown as fully protected.
- **Supported systems:** Windows 11, plus Windows 10 only while the device
  remains eligible for and receives Microsoft's applicable security updates.

The exact lower-spec test system, named corpora, measurement tooling, exact
Windows builds, and statistical acceptance procedure remain Stage 0 work.
Internal gates do not substitute for independent security assessment,
Microsoft driver signing, or external product testing.

### Stage 0 — Product requirements and acceptance baseline

**Outcome:** Every item from the original 30-item wishlist has a stable ID,
user-facing behavior, priority, explicit in-scope/out-of-scope boundary, and
testable acceptance criteria.

**Work:**

- Convert the original list into a traceable requirements matrix without
  merging distinct capabilities.
- Mark each item as `not started`, `prototype`, `implemented`, or `commercially
  verified`; do not use one status to imply another.
- Define supported Windows editions/builds, user privileges, offline behavior,
  data collection, retention, update cadence, recovery expectations, and
  performance budgets.
- Identify all security-critical assets, trust boundaries, abuse cases, and
  failure modes.

**Exit gate:** All 30 source items map to requirements and measurable
acceptance tests; the agreed gates above are tied to named test corpora,
reference hardware, and supported OS builds; remaining product assumptions
and unresolved legal/privacy decisions are recorded and assigned.

### Stage 1 — Secure product foundation

**Outcome:** A supportable, updateable application architecture with explicit
trust boundaries.

**Work:**

- Define component boundaries for UI, privileged Windows service, scanning
  engine, kernel component, update/reputation services, and installer.
- Design authenticated IPC, least privilege, service recovery, upgrade and
  rollback behavior, key custody, secrets handling, and signed release
  artifacts.
- Define privacy-by-design rules for cloud lookups, including data
  minimization, consent, retention, transport security, and failure behavior.
- Establish reproducible CI builds, dependency governance, threat modeling,
  fuzzing, static analysis, and security review as release controls.

**Exit gate:** Architecture and threat model receive independent security
review; privileged interfaces, update trust, privacy behavior, and recovery
paths have reviewed specifications and tests.

### Stage 2 — Detection and response engine

**Outcome:** Scanning and response are reliable, measurable, and recoverable.

**Work:**

- Replace the demonstration-only signature source with authenticated,
  versioned intelligence and rollback protection.
- Define supported file types, archive/decompression limits, error handling,
  exclusions, scan cancellation, and performance targets.
- Harden quarantine with durable metadata, crash consistency, tamper detection,
  recovery, and user-confirmed restore workflows.
- Measure detection quality and false-positive rates against licensed,
  controlled test corpora; never validate with live malware on ordinary
  development systems.

**Exit gate:** Reproducible quality and performance reports meet approved
thresholds; interrupted scans, updates, and quarantine operations recover
without silent data loss.

### Stage 3 — Real-time enforcement

**Outcome:** Supported file operations can be inspected and blocked according
to explicit policy.

**Work:**

- Design the kernel/user-mode boundary and fail-open/fail-closed behavior
  before implementing a driver.
- Implement the service/driver as a separate security-critical component with
  strict input validation, bounded communication, safe unload/update behavior,
  and diagnostics.
- Test compatibility, boot and upgrade recovery, performance, race conditions,
  tampering, and coexistence with Windows security products.
- Complete applicable Microsoft driver signing and compatibility processes
  before general distribution.

**Exit gate:** Independent driver security review and supported Windows
compatibility, reliability, performance, and recovery results pass the release
criteria. A user-mode alert or test-only block does not satisfy this gate.

### Stage 4 — Cloud, network, and advanced detection

**Outcome:** Optional online intelligence and additional protections add
measurable value without making local protection unsafe when unavailable.

**Work:**

- Add reputation lookups and update services only after privacy, abuse
  resistance, authentication, availability, and incident-response controls are
  approved.
- Specify network/web protection boundaries and safe degraded/offline
  behavior.
- Treat behavioral and machine-learning detection as independently testable
  signals with explainability, false-positive controls, drift monitoring, and
  human-reviewed response policy.
- Define whether vulnerability scanning belongs in the home antivirus product
  or a separate product tier.

**Exit gate:** Each capability has independent accuracy, privacy, availability,
and failure-mode evidence; no cloud or model failure silently disables core
local protection.

### Stage 5 — Commercial release readiness

**Outcome:** A product that can be installed, updated, supported, and safely
removed by ordinary users.

**Work:**

- Produce a signed installer, upgrade/uninstall path, rollback, diagnostics,
  accessibility and localization review, support process, and documented
  recovery instructions.
- Run supported-Windows compatibility, load, long-duration, upgrade,
  rollback, and failure-injection suites.
- Complete independent security assessment, privacy/legal review, release
  signing, vulnerability response process, and customer-support readiness.
- Publish accurate product claims tied to evidence and supported configurations.

**Exit gate:** Product, security, privacy, compatibility, support, and release
owners all approve the same build and evidence package. No marketing claim may
exceed a verified behavior.

## Product-wide quality gates

These gates apply throughout delivery. The initial quantitative targets are
listed in **Agreed product gates and policies**; Stage 0 must operationalize
them with named corpora, reference hardware, and repeatable measurement
procedures before using them to make release decisions.

- **Protection quality:** jointly meet the agreed detection and false-positive
  targets on documented, representative, legally obtained test corpora.
- **Safety:** no silent file deletion; every remediation is auditable and has a
  documented recovery path.
- **Reliability:** upgrades, service restarts, power interruption, and
  unavailable cloud services do not leave the product in an unknown state.
- **Performance:** meet the agreed idle CPU/memory gate and define separate
  scan-time, disk-impact, boot-impact, and interactive-latency budgets on
  representative hardware.
- **Security:** privileged interfaces and update/release trust receive
  independent review; critical findings block release.
- **Privacy:** data collection is minimized, documented, controllable, and
  protected in transit and at rest.
- **Compatibility:** only explicitly tested Windows versions and configurations
  are claimed as supported.
- **Honest claims:** prototype, alert-only, and best-effort behavior is never
  represented as prevention or complete coverage.

## Immediate next step

Close Stage 0 by selecting the named test corpora, defining the lower-spec
compatibility system and exact supported Windows builds, setting cloud
metadata retention, and agreeing release priorities against the matrix above.
The representative performance baseline is Windows 11, 4-core CPU, 8 GB RAM,
and SSD. Windows 10 version 22H2 Home/Pro
reached general Microsoft end of support on October 14, 2025; eligibility for
any extended updates must be checked rather than assumed. See
[Microsoft's Windows 10 lifecycle notice](https://learn.microsoft.com/en-us/lifecycle/announcements/windows-10-end-of-support).
Do not begin kernel-driver or cloud-service implementation before their
architecture, threat model, and recovery behavior pass review.
