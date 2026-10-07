# Sentinel AV Commercial Product Roadmap

## Product direction

The intended first product is a paid antivirus for home users in Greece and the
EU/EEA on Windows 11 Home/Pro, with cloud services allowed for signed
intelligence updates and optional reputation lookups. The protection target
includes real-time blocking backed by a Windows service and a Microsoft-signed
kernel component. The current repository is a prototype and is not suitable
for protecting or marketing as a commercial antivirus.

The initial OS support scope is Windows 11 Home/Pro x64, versions 25H2 and 26H2
on generally available servicing releases, with the latest applicable security
updates installed. Exclude ARM64, version 26H1, version 24H2, Windows 10, and
Insider or preview releases from the initial release. Microsoft lists Windows
26H1 as device-scoped rather than an in-place feature update for existing
devices, and Windows 24H2 Home/Pro reaches end of servicing on October 13, 2026.
Reconsider excluded platforms and versions only through a separate compatibility
and release review.

As a dated reference baseline on October 7, 2026, the latest listed builds are
26200.9550 for 25H2 and 26300.9550 for 26H2. These are test baselines, not
permanent build pins: support requires the latest applicable cumulative security
update for each supported version. See [Microsoft Windows 11 release
information](https://learn.microsoft.com/en-us/windows/release-health/windows11-release-information).

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
  legally obtained, held-out test corpus, plus a pre-agreed minimum detection
  floor for every in-scope threat family; strong results in one family cannot
  compensate for a weak family. The primary malware gate covers ransomware,
  trojans, worms, RATs, and infostealers. Report PUA separately; do not include
  PUA results in the primary malware aggregate or use them to offset a malware
  family result. The lower bound of a one-sided 95% confidence interval must
  meet the overall and every per-family detection threshold. Report results
  separately by family and detection layer. The independent laboratory
  proposes family floors, corpus sample size, weighting, repetitions, and
  confidence-interval methodology for approval before testing. A qualified
  independent laboratory handles the licensed threat corpus; development and
  CI use only EICAR/AMTSO test artifacts and synthetic fixtures. Before final
  evaluation, freeze the exact product build, configuration, policies, rules,
  and definition set; record cryptographic hashes and build provenance. Any
  material change after a failed evaluation requires retesting on a fresh,
  previously unused held-out split. Run the primary detection and false-positive
  gates on the final release candidate with the shipping default settings;
  report optional modes and individual detection layers separately, without
  using them to substitute for the default-settings gate.
- **False positives:** the upper bound of a one-sided 95% confidence interval
  for the false-positive rate must be strictly below 1 per 100,000 files in the
  agreed stratified benign corpus covering Windows files, widely used
  applications and installers, and common personal file types, with legally
  obtained samples. The independent laboratory proposes sources, stratum
  proportions, sample size, and statistical method. Require zero false
  positives in the explicitly defined critical Windows/system-file subset.
  Evaluate detection and false-positive thresholds together at the same
  policy/settings.
- **Idle performance:** p95 CPU usage below 1% and p95 aggregate working set
  below 200 MB across all Sentinel user-mode processes on a representative
  Windows 11 PC with a 4-core CPU, 8 GB RAM, and SSD, with protection enabled
  while idle. Apply these targets to the baseline hardware on both supported OS
  versions. Normalize aggregate Sentinel user-mode CPU time to total host
  capacity across all logical processors for each sample; report driver CPU
  separately and capture whole-system CPU impact. Record peak working set as a
  diagnostic. A separate Windows 11
  compatibility profile uses a 2-core CPU, 4 GB RAM, and HDD; this is a test
  target, not yet a support promise or a minimum system requirement. Set
  minimum supported hardware only after measuring scan time, responsiveness,
  and resource use. Active-scan, boot-impact, and interactive-latency budgets
  remain separate.
- **Compatibility matrix:** test all four combinations of Windows 11 Home/Pro
  x64 25H2 and 26H2 with both the baseline profile (4 cores, 8 GB RAM, SSD) and
  the low-spec test profile (2 cores, 4 GB RAM, HDD). The low-spec cells assess
  functional compatibility, stability, data integrity, and truthful protection
  state, but do not gate performance targets or establish a supported minimum.
  Use baseline-hardware cells for release performance gates. Review low-spec
  measurements before setting/publishing minimum requirements. The independent
  lab proposes exact physical reference systems for both profiles; approve the
  models before testing. Record CPU SKU/microcode, memory configuration,
  storage model/firmware, BIOS/UEFI, power/thermal settings, drivers, OS build,
  and installed updates. Virtual machines may support preliminary checks but
  cannot replace physical-system release measurements.
- **Idle measurement protocol:** for each of the four cells, warm up for
  15 minutes, then measure for 8 hours and repeat in three independent runs.
  Keep protection enabled, close the main UI, allow scheduled background tasks
  and definition checks, and do not run a user-triggered scan. Aggregate CPU
  and working set across all Sentinel user-mode processes; report kernel-driver
  CPU and paged/nonpaged pool use separately, with driver resource limits set
  before release. Normalize aggregate user-mode CPU time across all Sentinel
  processes to total host capacity across logical processors for each sample;
  calculate p95 CPU and aggregate working set over measured samples, and record
  peak working set and active-scan resource impact separately. Keep Microsoft
  Defender enabled and unmodified, preserve the same state and controlled
  Windows background workload across all runs, and record both
  Sentinel-attributed and whole-system resource impact.
- **Manual/full scans:** use adaptive background CPU and disk-I/O priority,
  throttle under interactive load, and support pause/resume. Set quantitative
  resource and completion-time budgets from representative-device measurements
  rather than promising an unmeasured scan duration. Benchmark with a
  deterministic synthetic benign corpus and a repeatable concurrent interactive
  workload. The independent lab proposes reproducible scripts for common app
  launch/use, opening and saving benign files, and file copy/search; approve the
  scripts before benchmarking. Include two separately reported concurrent-load
  scenarios: steady everyday activity and short bursts of overlapping app
  launches/file operations. The lab proposes the workload rates; approve them
  before acceptance runs. Version the synthetic corpus generator; fix its seed
  and preserve a manifest plus SHA-256 per generated file. The lab approves
  file-type, size, archive-depth distributions and benign template sources
  before tests. Any generator or corpus change creates a new version and
  baseline. Measure scan completion/throughput, CPU, disk I/O, p95 foreground
  latency, and pause/resume behavior separately for each load scenario. The lab
  proposes numerical scan/latency budgets from baseline runs for approval
  before acceptance. Run three cold-start acceptance scans per matrix cell from
  the same clean system image after reboot, with no pre-populated Sentinel scan
  cache; record and hold the OS filesystem-cache procedure consistent. Report
  warm-cache diagnostics separately, not mixed into cold-start acceptance. Keep
  live malware out of development/CI.
- **Cloud privacy:** local protection continues offline. Default reputation
  requests send only hashes and minimum necessary metadata; file contents are
  never uploaded without separate explicit consent. Hashes and user
  identifiers are not persistently retained; anonymous aggregates are allowed.
  Temporary identifiable logging requires separate explicit consent.
- **Threat-definition freshness:** check for updates every 4 hours. If
  definitions are more than 24 hours old while internet connectivity is
  available, show a degraded warning. Offline local protection continues; mark
  definitions stale after 30 days without a successful update while offline,
  without silently treating unknown files as clean or disabling local scanning.
- **Update trust:** use an offline root signing key protected by an HSM and a
  separate online delegated signing key in a managed HSM/KMS. The client must
  validate the signature chain and product/channel/version/expiry policy,
  reject disallowed rollback, and activate packages only after validation.
  Rotation, revocation, and compromise recovery require a reviewed lifecycle.
- **Protection failure:** service/driver failure is fail-open for home-user
  availability, with a visible degraded status within 5 seconds and a first
  recovery attempt within 30 seconds. Show healthy only after end-to-end health
  verification. Allow file operations across operation types during the
  enforcement outage, keep Microsoft Defender and its settings untouched, and
  do not report Sentinel protection while enforcement is unavailable. Measure
  and document the exposure window and residual risk.
- **Supported systems:** Windows 11 Home/Pro x64 versions 25H2 and 26H2 only
  for the initial release, on generally available builds with the latest
  applicable security updates. Exclude ARM64, version 26H1, version 24H2,
  Windows 10, and Insider or preview releases; reconsider excluded platforms
  and versions only through a separate compatibility and release review.

The independent evaluation laboratory, named corpora, measurement tooling,
minimum supported hardware, behavior and performance acceptance on the
2-core/4-GB/HDD compatibility test profile, statistical acceptance procedure,
quantitative active-scan budgets, and detailed failure behavior remain Stage 0
work. The adaptive background scan policy and degraded/recovery timing
objectives above are agreed but still require measurement. Internal gates do
not substitute for independent security assessment, Microsoft driver signing,
or external product testing.

### Stage 0 — Product requirements and acceptance baseline

**Outcome:** Every item from the original 30-item wishlist has a stable ID,
user-facing behavior, priority, explicit in-scope/out-of-scope boundary, and
testable acceptance criteria.

**Work:**

- Convert the original list into a traceable requirements matrix without
  merging distinct capabilities.
- Mark each item as `not started`, `prototype`, `implemented`, or `commercially
  verified`; do not use one status to imply another.
- Verify Windows 11 Home/Pro x64 25H2 and 26H2 against the dated reference
  builds and define the monthly security-update compatibility process, user
  privileges, offline behavior, data collection, retention, update cadence,
  recovery expectations, and performance budgets. ARM64 requires a separate
  driver-signing, compatibility, and performance release review.
- Run the complete four-cell OS-version/hardware-profile compatibility matrix;
  record exact hardware, firmware, OS build, installed updates, product build,
  and configuration for every result. Have the independent lab propose exact
  physical baseline and low-spec systems for approval before release testing;
  VMs are not substitutes for physical performance evidence.
- Apply quantitative release performance gates on the 4-core/8-GB/SSD baseline
  for both supported OS versions. On the 2-core/4-GB/HDD profile, require
  functional operation, stability, data integrity, and truthful protection
  status; characterize performance and decide support eligibility before
  publishing minimum hardware requirements.
- For idle performance in each cell, use a 15-minute warm-up followed by an
  8-hour measurement, repeated in three independent runs; calculate p95 CPU
  and aggregate working set across Sentinel user-mode processes and report peak
  working set, with protection on, UI closed, scheduled background tasks
  enabled, and no user-triggered scan.
  Normalize aggregate user-mode CPU time to total host capacity across all
  logical processors per sample. Measure kernel-driver CPU and paged/nonpaged
  pool separately, set driver limits before release, and report active-scan
  resources separately. Keep Microsoft Defender enabled and unmodified with
  consistent settings and controlled Windows background workload across runs;
  capture whole-system impact as well as Sentinel-attributed resource use.
- For manual/full-scan performance in each cell, use a deterministic synthetic
  benign corpus whose file-type, size, and archive distributions are defined
  before the benchmark. Version the generator, use a fixed seed, preserve a
  manifest and per-file SHA-256 hashes, and approve distributions and benign
  template sources with the lab. A corpus/generator change requires a new
  version and baseline. Use lab-proposed, pre-approved workload scripts for
  common app launch/use, benign-file open/save, and file copy/search, with
  separately measured steady everyday and short burst load scenarios. Approve
  workload rates before acceptance. Measure completion time/throughput, CPU,
  disk I/O, p95 foreground latency, and pause/resume separately per scenario.
  Approve numerical scan/latency budgets based on baseline runs before
  acceptance runs. Perform three cold-start scans per cell from the same clean
  image after reboot with no pre-populated Sentinel scan cache; report warm-cache
  diagnostics separately. Do not run live malware in development or CI.
- Measure the 2-core/4-GB/HDD compatibility profile but do not treat it as a
  support commitment. Set and publish minimum supported hardware requirements
  from measured scan-time, responsiveness, and resource-use results.
- Record the agreed offline-root/online-delegated update-signing key hierarchy;
  specify key rotation, revocation, emergency recovery, and compromise-response
  procedures before implementation.
- Specify signed-definition freshness states, the 4-hour update check, the
  24-hour online degraded warning, and the 30-day offline stale warning.
- Define and test the 5-second degraded-state and 30-second recovery-attempt
  objectives, including the end-to-end health checks required to clear degraded
  state.
- Benchmark manual/full scans in adaptive background mode on representative
  hardware; set CPU, disk-I/O, responsiveness, pause/resume, and completion-time
  acceptance budgets from measured results.
- Define the safe synthetic scan corpus distributions and repeatable interactive
  workload; benchmark each of the four OS/hardware cells and use the results to
  set active-scan acceptance budgets.
- Shortlist an independent evaluation laboratory only if it demonstrates all
  of the following: relevant Windows antivirus testing experience; legal
  authority to access and handle the proposed threat corpus in a controlled
  environment; a documented, reproducible methodology with pre-agreed sample
  attribution, statistical analysis, and repeatability controls; reporting of
  results and limitations by threat family and detection layer; and disclosure
  of conflicts of interest, funding, and relevant commercial relationships.
  Require the laboratory to remain free to report unfavorable findings; payment
  must not depend on passing or on a favorable result.
- Desk-researched candidate shortlist as of 2026-10-07; this is not a
  qualification, endorsement, or selection. Invite proposals only after
  confirming that each candidate can pass every mandatory gate above:

  | Candidate | Publicly documented fit | Important limitation to resolve before scoring |
  |---|---|---|
  | [AV-Comparatives](https://av-comparatives.org/services/cybersecurity-vendors/) | Offers confidential private assessments for vendors and publishes a Windows consumer antivirus test series. | Confirm whether it will accept a new, small consumer-AV vendor for the full custom scope, and obtain evidence for corpus custody, per-family/layer reporting, the four physical test cells, retest rules, and publication terms. |
  | [SE Labs](https://selabs.uk/services/cyber-security-vendor/) | Describes vendor testing levels including evaluation/advanced work and lists antivirus testing among its services; it publishes endpoint anti-malware methodologies and reports. | Confirm fit for the specified consumer Windows 11 product and bespoke statistical gates, licensed-corpus access/custody, repeatability, the four physical test cells, and unfavorable-result publication rights. |
  | [Virus Bulletin / VB100](https://www.virusbulletin.com/testing/vb1001/vb100-vendors/) | Explicitly offers private, one-off, and custom tests, including altered or fully custom methodologies. | The standard VB100 scope focuses on static detection of common Windows PE malware. Confirm that a custom engagement can cover behavior/blocking layers, all required families, the benign strata, active-scan performance, and the complete acceptance protocol; do not treat a standard VB100 result as sufficient for this product gate. |
  | [AV-TEST](https://www.av-test.org/en/antivirus/home-windows/) | Publishes recurring Windows consumer antivirus evaluations, including protection, performance, and usability results. | Public material reviewed confirms relevant comparative testing, but not a bespoke private engagement meeting this scope. Keep as a reserve candidate unless it confirms the commissioning model, full methodology, corpus controls, reporting, and publication terms in writing. |

  Public service descriptions establish only a reason to send an information
  request. They do not prove independence for this engagement, legal corpus
  authority, security controls, pricing, availability, or acceptance of our
  contractual requirements. Record the dated source and the candidate's written
  response for each mandatory gate; mark any unverified item as pending, never
  as a pass. The initial outreach set should prioritize AV-Comparatives, SE Labs,
  and Virus Bulletin, with AV-TEST retained as a reserve pending confirmation.
- Evaluate each proposal in two stages. First apply mandatory pass/fail gates:
  every capability and independence criterion above, lawful corpus access and
  handling, adequate security/custody controls, acceptance of the no-contingent-
  payment and unfavorable-reporting terms, and agreement to the required
  deliverables and retest protections. Reject any proposal that fails a gate;
  do not let a high price/quality score compensate for a failed mandatory gate.
- Score only proposals that pass all mandatory gates using the same documented
  100-point rubric: protocol/statistical rigor and reproducibility (30 points);
  threat and benign corpus provenance, representativeness, and family coverage
  (20); reporting completeness, evidence quality, and repeatability (20);
  corpus custody/security and incident handling (15); independence,
  transparency, and conflict management (10); and total cost and delivery
  schedule (5). Record evidence and rationale for every score; set a minimum
  qualifying score of 80/100 before reviewing proposals, and document any
  tie-break without weakening a mandatory gate.
- Before contract signature, approve a written statement of work defining
  corpus provenance and legal scope, lab custody and access controls, the
  physical four-cell hardware matrix, the synthetic corpus and interactive
  workload protocol, test configurations and frozen build inputs, statistical
  methods, deliverables, retest rules, data retention/destruction, and report
  publication/claims restrictions.
- Require deliverables that include the approved protocol, test environment and
  product-build provenance, corpus versions and non-sensitive sample
  identifiers/hashes, per-family/per-layer and PUA-separate results, false
  positives by benign stratum, statistical bounds, deviations, failed runs,
  limitations, and repeatability evidence. Keep licensed threat samples and
  their contents under the laboratory's legal and security controls; do not
  place them in developer or CI environments.
- Approve the detection acceptance protocol before testing: overall 95% gate
  plus a minimum per-family floor for ransomware, trojans, worms, RATs, and
  infostealers; report PUA separately from the primary malware gate. Agree on
  taxonomy, sample attribution, corpus composition, sufficient sample size,
  family weighting, repetitions, and one-sided 95% confidence-interval
  methodology with the independent laboratory before testing. Require the
  lower confidence bound to meet each detection threshold.
- Approve the false-positive protocol before testing: require the upper
  one-sided 95% confidence bound to be strictly below 1/100,000 on a stratified,
  legally sourced benign corpus spanning Windows files, widely used
  applications/installers, and common personal file types. Have the independent
  laboratory propose sources, stratum proportions, sample size, and statistical
  method; require zero findings on the critical Windows/system-file subset and
  evaluate on the final release candidate with the same shipping default
  settings as detection.
- Freeze and record the evaluated build, configuration, policy, rules, and
  definition set with cryptographic hashes and build provenance. Require a
  fresh, unused held-out split after any material change to an evaluation that
  did not pass.
- Identify all security-critical assets, trust boundaries, abuse cases, and
  failure modes.

**Exit gate:** All 30 source items map to requirements and measurable
acceptance tests; the agreed gates above are tied to named test corpora,
reference hardware, and supported OS builds; remaining product assumptions
and unresolved legal/privacy decisions are recorded and assigned. A laboratory
meeting every mandatory independence and capability criterion is selected, and
its statement of work, test protocol, corpus/legal scope, evidence deliverables,
and publication limits are approved before licensed threat-corpus testing.
Development and CI use safe test artifacts only.

### Stage 1 — Secure product foundation

**Outcome:** A supportable, updateable application architecture with explicit
trust boundaries.

**Work:**

- Define component boundaries for UI, privileged Windows service, scanning
  engine, kernel component, update/reputation services, and installer.
- Design authenticated IPC, least privilege, service recovery, upgrade and
  rollback behavior, the agreed offline-root/online-delegated signing-key
  hierarchy, secrets handling, and signed release artifacts.
- Specify the agreed fail-open policy, including degraded-state reporting,
  recovery behavior, residual exposure, and in-flight scan decisions.
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

The current target architecture proposal is documented in
[commercial-core-architecture.md](./commercial-core-architecture.md). Close
Stage 0 by selecting and contracting an independent evaluation laboratory for
the licensed threat corpus, selecting a benign corpus and methodology,
validating the dated 25H2/26H2 reference-build matrix and lower-spec
compatibility system, specifying statistical acceptance procedures and
separate active-scan budgets, and agreeing release priorities against the
matrix above. Windows 10 is excluded from the initial support scope; any later
addition requires a separate compatibility, lifecycle, and release review.
Do not begin kernel-driver or cloud-service implementation before their
architecture, threat model, and recovery behavior pass review.
