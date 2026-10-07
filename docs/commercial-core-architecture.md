# Commercial Core Target Architecture

## Status and scope

This document proposes the target architecture for the first commercial
Windows home-user release. It is a design artifact, not an implementation
description, threat-model approval, compatibility claim, or authorization to
load a kernel driver. The current prototype remains a local JavaFX application
and C++ child process; the service, driver, authenticated update system, and
cloud backend below do not yet exist.

V1 scope is deliberately limited to trusted intelligence updates,
on-demand scanning and detection, quarantine, real-time file protection,
tamper protection, and a truthful protection-status experience. Network/web
protection, ML, privacy extras, Gamer Mode, and vulnerability scanning remain
later-release candidates.

## Agreed product gates

These targets were agreed during planning and must be treated as provisional
internal release gates until the corpora, methodology, operating-system builds,
and measurement tools are independently reviewed:

- At least **95% known-threat detection** on a documented, legally obtained,
  held-out corpus, plus a pre-agreed minimum detection floor for each primary
  malware family: ransomware, trojans, worms, RATs, and infostealers. Strong
  results in one family cannot compensate for a weak family. Report PUA
  separately; exclude PUA from the primary malware aggregate and do not use it
  to offset a malware family result. The lower bound of a one-sided 95%
  confidence interval must meet the overall and every per-family detection
  threshold. Report results by family and detection layer. Agree on taxonomy
  and sample-attribution rules before testing; the independent laboratory
  proposes family floors, sufficient sample size, weighting, repetitions, and
  confidence interval methodology for approval. A qualified independent
  laboratory handles the licensed threat corpus; development and CI use only
  EICAR/AMTSO test artifacts and synthetic fixtures. Before final evaluation,
  freeze the exact product build, configuration, policies, rules, and
  definition set; record cryptographic hashes and build provenance. Any
  material change after a failed evaluation requires retesting on a fresh,
  previously unused held-out split. Run the primary detection and
  false-positive gates on the final release candidate with the shipping default
  settings; report optional modes and individual detection layers separately,
  without using them to substitute for the default-settings gate.
- The upper bound of a one-sided 95% confidence interval for the false-positive
  rate must be **strictly below 1 per 100,000 files** in an agreed, legally
  sourced, stratified benign corpus covering Windows files, widely used
  applications and installers, and common personal file types. The independent
  laboratory proposes sources, stratum proportions, sample size, and statistical
  method. Require zero false positives against the explicitly defined critical
  Windows/system-file subset. Report by stratum and measure jointly with
  detection quality at identical policy/settings.
- Idle p95 CPU **below 1%** and p95 aggregate working set **below 200 MB**
  across all Sentinel user-mode processes on Windows 11 with a 4-core CPU,
  8 GB RAM, and SSD, with protection enabled. Apply these targets on both
  supported OS versions on baseline hardware. Normalize aggregate Sentinel
  user-mode CPU time to total host capacity across logical processors per
  sample; report driver CPU separately and capture whole-system impact. Record
  peak working set as a diagnostic. Also test compatibility with a Windows 11
  2-core CPU, 4 GB RAM, and HDD profile. This is a test target only, not a
  support promise or minimum hardware requirement. Set minimum supported
  hardware only after measuring scan time, responsiveness, and resource use.
- Test all four combinations of Windows 11 Home/Pro x64 25H2 and 26H2 with
  both profiles: baseline (4 cores, 8 GB RAM, SSD) and low-spec compatibility
  test (2 cores, 4 GB RAM, HDD). Apply quantitative release performance gates
  on baseline cells for both supported OS versions. Low-spec cells require
  functional operation, stability, data integrity, and truthful protection
  status; characterize performance there to decide support eligibility before
  setting a minimum hardware requirement. The independent lab proposes exact
  physical systems for approval before testing. Record CPU SKU/microcode,
  memory configuration, storage model/firmware, BIOS/UEFI, power and thermal
  settings, drivers, OS build, and installed updates. VMs may support
  preliminary checks but cannot replace physical-system release performance
  evidence.
- For idle performance in each cell, use a 15-minute warm-up followed by an
  8-hour measurement, repeated in three independent runs. Keep protection on,
  close the main UI, allow scheduled tasks and definition checks, and do not run
  a user-triggered scan. Aggregate user-mode CPU and working set across all
  Sentinel processes; normalize their CPU time to total host capacity across
  logical processors per sample. Measure kernel-driver CPU and paged/nonpaged
  pool use separately, with driver limits set before release; calculate p95
  over measured samples and record peak working set. Report active-scan
  resources separately. Keep Microsoft Defender enabled and unmodified with
  consistent settings across runs; control and record Windows background
  workload, and capture whole-system impact alongside Sentinel-attributed
  resources.
- Manual/full scans use adaptive background CPU and disk-I/O priority, throttle
  under interactive load, and support pause/resume. Set quantitative resource
  and completion-time budgets from representative-device measurements rather
  than promising an unmeasured scan duration. Benchmark each matrix cell with a
  deterministic synthetic benign corpus using pre-agreed file-type, size, and
  archive-depth distributions. Version the generator, use a fixed seed, and
  preserve a manifest plus SHA-256 per generated file; approve benign template
  sources and distributions with the lab. A corpus/generator change requires a
  new version and baseline. Use independent-lab-proposed, pre-approved workload
  scripts for common application launch/use, benign-file open/save, and file
  copy/search, covering separately reported steady everyday and short-burst
  concurrent activity. Approve workload rates before acceptance. Measure
  completion/throughput, CPU, disk I/O, p95 foreground latency, and pause/resume
  separately for each scenario. Approve numerical scan and latency budgets from
  baseline runs before acceptance runs. Run three cold-start scans per matrix
  cell from the same clean system image after reboot, with no pre-populated
  Sentinel scan cache and a consistent OS filesystem-cache procedure. Report
  warm-cache diagnostics separately from cold-start acceptance; never use live
  malware in development/CI.
- Cloud lookups send only hashes and minimum necessary metadata by default.
  File contents require separate explicit consent. Do not persist hashes or
  user identifiers; retain anonymous aggregates only unless the user separately
  consents to temporary identifiable logging.
- Local protection remains available offline.
- Check for threat-definition updates every 4 hours. If definitions are more
  than 24 hours old while internet connectivity is available, show degraded
  status. Mark definitions stale after 30 days offline, but keep local scanning
  and protection active; an unknown result must never become a clean verdict.
- When service or driver enforcement fails, use **fail-open** for home-user
  availability across all file-operation types, display a degraded state
  within **5 seconds**, and make a first recovery attempt within **30 seconds**.
  Clear degraded state only after end-to-end health verification. Leave
  Microsoft Defender and its settings untouched. Never report Sentinel
  protection while enforcement is unavailable; measure and document the
  exposure window and residual risk.
- V1 focuses on trusted updates, scanning/detection, quarantine, real-time
  blocking, tamper protection, and truthful status. Network/web, ML, privacy
  extras, Gamer Mode, and vulnerability scanning are deferred.
- Initial support scope: Windows 11 Home/Pro x64 versions 25H2 and 26H2 on
  generally available servicing releases with the latest applicable security
  updates. Exclude ARM64, version 26H1, version 24H2, Windows 10, and Insider
  or preview releases. Reconsider excluded platforms and versions only through
  a separate compatibility and release review. ARM64 requires separate
  driver-signing, compatibility, and performance validation before it may be
  added.
- Dated reference builds for October 7, 2026: 25H2 build 26200.9550 and 26H2
  build 26300.9550. These are test baselines, not permanent build pins;
  compatibility must be maintained for the latest applicable cumulative
  security update while the supported version remains in servicing. See
  [Microsoft Windows 11 release information](https://learn.microsoft.com/en-us/windows/release-health/windows11-release-information).

The representative idle-performance baseline is Windows 11, 4-core CPU,
8 GB RAM, and SSD. The low-spec compatibility test profile is Windows 11,
2-core CPU, 4 GB RAM, and HDD; passing this profile is not a minimum-hardware
support commitment. Corpus identity and licensing, lab selection,
measurement procedure, supported hardware-model coverage, active-scan budgets,
and operation-specific failure behavior remain open design decisions. Specific
supported CPU models and minimum hardware requirements must be set from
measured scan-time, responsiveness, and resource-use results. The 5-second
degraded warning and 30-second first recovery-attempt objectives are agreed,
subject to measurement.

## Safe validation boundary

Normal developer machines and CI must not contain or execute a live malware
corpus. Use EICAR/AMTSO test artifacts and generated synthetic fixtures for
everyday detection-pipeline and regression tests. Contract a qualified
independent laboratory to handle the licensed threat corpus under its
controlled test environment, agreed measurement protocol, and legal terms.
Before shortlisting a lab, require documented Windows antivirus testing
experience; legal authority to access and safely handle the proposed threat
corpus; a reproducible methodology with pre-agreed taxonomy, sample attribution,
statistical analysis, and repeatability controls; family/layer-level reporting;
and disclosure of conflicts, funding, and relevant commercial relationships.
The lab must retain independence to report unfavorable findings, and its
compensation must not be contingent on passing or favorable results.

Qualify proposals in two stages. Apply mandatory pass/fail gates first for
capability, independence, lawful corpus access/handling, custody/security
controls, non-contingent payment and unfavorable-reporting terms, deliverables,
and fresh-split retest protections. Reject any proposal that fails a gate;
scored strengths cannot compensate for a mandatory failure. Score only
qualified proposals using one documented 100-point rubric: protocol/statistical
rigor and reproducibility (30); threat/benign corpus provenance,
representativeness, and family coverage (20); reporting/evidence quality and
repeatability (20); corpus custody/security and incident handling (15);
independence, transparency, and conflict management (10); cost and delivery
schedule (5). Record evidence and rationale for each score, establish a minimum
qualifying score before reviewing proposals, and document tie-breaks.

Approve a written statement of work before testing that covers corpus
provenance and legal scope, lab custody/access controls, approved physical
hardware and workload matrices, frozen build/configuration inputs, statistical
protocol, retests, deliverables, retention/destruction, and limits on marketing
claims. Do not distribute live samples or their contents to normal development
or CI systems.

The lab must report corpus version/composition, per-family and per-layer
results, PUA results separately, false positives, test configuration, and
repeatability evidence. Agree on threat taxonomy and sample-attribution rules
before the held-out test so family overlaps cannot be resolved after results
are known.
The evidence package must also include non-sensitive sample identifiers/hashes,
benign false positives by stratum, confidence bounds, deviations, failed runs,
test-build provenance, limitations, and retest lineage. Preserve underlying
sample custody at the lab under its legal and security controls.
Record the evaluated build, configuration, policy, rules, and definition-set
hashes with build provenance. Do not tune against held-out results. If an
evaluation does not pass and any material item changes, test the candidate
again with a fresh held-out split not previously used for tuning.
Do not publish or use the 95% target as an efficacy claim until the agreed
methodology and results have been independently reviewed.

## Trust zones and component responsibilities

| Zone / component | Privilege and trust | Responsibilities | Must not own |
|---|---|---|---|
| Desktop UI | Unprivileged interactive user | Display actual protection state, configure allowed user preferences, request scans, show incidents and recovery guidance | Direct privileged file operations, signing keys, or claims that degraded protection is active |
| Local service | Privileged Windows service with least privileges needed; exact account and rights require security review | Own policy, scan scheduling, update orchestration, event correlation, remediation authorization, UI/service IPC, and recovery state | Trusting arbitrary UI requests, downloading unsigned policy, or executing complex file parsing in a privileged request handler |
| Scan worker | Restricted user-mode worker with only required read access | Parse and scan files, apply local detection layers, return bounded structured verdicts | Kernel privileges, update-signing keys, direct network policy changes, or unrestricted remediation authority |
| File-system enforcement component | Kernel mode; Microsoft signing and applicable distribution requirements are release prerequisites | Enforce a small, versioned file-operation policy; perform bounded communication with the service; report health and enforcement status | Cloud access, general-purpose file parsing, unbounded waits, policy authoring, or complex threat classification |
| Update client | User mode, constrained network access | Fetch update packages, verify signatures and version policy, stage updates, report update state | Trusting transport security alone or activating an unverified package |
| Cloud reputation/update service | Remote, outside device trust boundary | Return authenticated, versioned intelligence responses and distribute signed update artifacts | Receiving file contents by default, persistently retaining query hashes or user identifiers, or being required for local protection |
| Local protected data | Device storage; integrity and access controlled | Store signed definitions, policy, event records, quarantine content and metadata, and update recovery state | Treating locally writable prototype files as an authenticated source of truth |
| Installer and updater | Elevated only for installation/update operations | Install, upgrade, roll back, repair, register supported Windows security status, and remove product components | Leaving partial driver/service state or silently disabling Windows protections |

Service account choice, driver model and callbacks, inter-process protocol,
Windows Security Center registration, exact operating-system APIs, and key
custody are implementation decisions that require Windows-specific design and
independent security review before coding.

## Principal data flows

### Local file operation

1. The file-system enforcement component observes only the supported operation
   classes defined by policy and obtains a bounded local verdict.
2. A cached, signed local definition or a bounded service request can provide a
   verdict. Kernel callbacks do not wait on cloud reputation.
3. The restricted scan worker performs content inspection and returns a
   versioned, size-limited result to the service.
4. The service authorizes an action according to policy and confidence:
   allow, block, or isolate. Every completed action and failure is recorded.
5. The UI receives the protection health state and a user-readable explanation.

The file-operation coverage must be explicitly enumerated and tested. A file
system driver alone does not establish coverage of all process creation,
scripts, DLL loading, memory-only threats, browser navigation, removable-media
events, or network traffic. These require separately designed and validated
signals. No unimplemented signal may be shown as enabled.

### Threat intelligence and updates

1. The update client fetches a manifest and packages over authenticated
   transport; transport authentication is not the artifact trust root.
2. The client verifies a cryptographic signature rooted in a release trust
   store, validates product/channel/version/expiry constraints, and rejects
   rollback to disallowed versions.
3. It stages and validates the update, retains a known-good recovery version,
   and activates the new package atomically or through an explicitly recoverable
   transaction.
4. The service reports current definition version, freshness, and failures.
   Check for updates every 4 hours, warn of degraded freshness after 24 hours
   without a successful update while online, and mark definitions stale after
   30 days offline without disabling local scanning.

Use an offline root signing key protected by an HSM and controlled signing
ceremony, with a separate online delegated signing key in a managed HSM/KMS.
Keep all signing keys isolated from ordinary CI and developer machines. The
client validates the signing chain, product/channel/version/expiry constraints,
and rollback policy before activation. Key rotation, compromise response,
revocation, emergency updates, and reproducible release evidence require a
separate reviewed key-management design.

### Cloud reputation and privacy

- The local detector remains able to scan and apply its installed policy while
  offline; remote reputation is an additional signal, not a prerequisite for
  local protection.
- Default reputation requests contain the file hash and only metadata shown
  to be necessary. The service does not persistently retain hashes or user
  identifiers. Anonymous aggregate metrics may be retained. Temporary
  identifiable logging requires separate, explicit consent.
- File content is never uploaded without separate, explicit consent. Requests
  are authenticated, protected in transit, rate-limited, and validated;
  responses are signed or otherwise cryptographically authenticated and
  versioned.
- Timeout, malformed response, authentication failure, or service outage is
  recorded as unavailable/degraded intelligence and never converted into a
  safe verdict.

## Fail-open policy and its security consequence

The agreed home-user policy is fail-open when enforcement is unavailable, to
avoid making ordinary system use depend on the health of a new security
product. This creates a deliberate exposure window: operations that cannot be
evaluated while the service or driver is unhealthy may proceed without
Sentinel's enforcement. This is not equivalent to protected operation.

Required behavior:

- Surface a degraded state immediately, with the unavailable protection
  modules identified and the time the state began, within the agreed 5-second
  objective.
- Allow all file-operation types during enforcement outages. Do not disable,
  reconfigure, or claim coverage from Microsoft Defender.
- Make the first bounded service/driver recovery attempt within 30 seconds;
  continue with backoff, avoid restart loops, and do not claim recovery until
  health is verified end-to-end.
- Keep Windows' own protections enabled; Sentinel must not disable or weaken
  them to make fail-open or coexistence easier.
- Do not classify a timeout, crash, cloud outage, or unknown result as clean.
- Preserve diagnostic evidence and offer safe recovery guidance; do not
  silently terminate arbitrary processes or delete files.
- Measure the frequency and duration of fail-open windows as release
  reliability metrics.

The exact behavior for a positive, high-confidence local verdict when the
service fails after a decision, a driver communication timeout, corrupted local
policy, and partial update activation must be specified per operation class.
Exceptions cannot silently change the agreed fail-open policy.

## Privileged IPC and authorization

- Only the intended local service identity may create the kernel communication
  channel; access is restricted with Windows security descriptors and
  authenticated peer identity.
- UI-to-service requests use a versioned, bounded message schema. The service
  validates operation, path, size, state, and user authorization independently.
- The UI cannot ask the driver to execute arbitrary commands or supply
  unbounded policy. The driver accepts only the minimal fixed set of operations
  needed for enforcement and health reporting.
- Scan workers return data, not privileged actions. The service validates the
  result and makes remediation decisions.
- All IPC operations have explicit timeout, cancellation, malformed-message,
  disconnect, and version-mismatch behavior.

## Local response, quarantine, and recovery

- Automatic actions are policy-limited and auditable. Deletion is not the
  default response.
- Quarantine content and metadata are protected against ordinary processes,
  tied by cryptographic integrity evidence, and updated crash-consistently.
- Restore verifies content integrity, destination safety, and user intent; a
  restore never overwrites an existing file silently.
- Service, driver, definitions, and installer upgrades retain a tested
  rollback path. Interrupted upgrade or power loss must not leave protection
  state falsely reported as healthy.
- Incident records distinguish a proposed action from an action that actually
  completed.

## Coexistence and operating-system integration

- Sentinel does not disable Microsoft Defender or alter its settings to claim
  sole protection.
- Product registration and coexistence behavior must use supported Windows
  mechanisms and be tested on each claimed Windows build.
- Installation, upgrade, repair, rollback, and uninstall must leave the system
  bootable and must remove or restore owned service/driver state safely.
- Driver signing and distribution prerequisites must be confirmed with current
  Microsoft requirements before any driver is packaged for external users.

## Threats, controls, and open verification

| Threat | Required architectural control | Evidence required before release |
|---|---|---|
| Malicious or compromised update | Signed metadata and packages, version/expiry checks, rollback control, isolated signing keys, recovery version | Key-compromise exercise, signature-negative tests, rollback and interrupted-update tests |
| Compromised UI or unprivileged process | Narrow authenticated IPC, independent service authorization, least privilege | Access-control tests, malformed-message tests, independent IPC review |
| Kernel attack surface or hang | Minimal callback responsibilities, bounded queues and waits, strict message validation, safe recovery | Independent driver review, fuzzing, deadlock/race and failure-injection tests |
| Scanner parser exploit | Restricted worker, bounded parsers/resources, no privileged parsing | Parser fuzzing, resource exhaustion tests, privilege-boundary tests |
| Service/driver outage | Agreed fail-open with visible degraded state and bounded recovery | Fault injection, outage duration and detection, no false healthy state |
| Cloud compromise or outage | Offline local protection, authenticated signed results, minimal nonpersistent query data | Tampered-response, replay, outage, privacy-retention, and consent tests |
| False positive causes loss of user data | Confidence-based policy, quarantine rather than default deletion, verified restore | Agreed benign corpus, zero critical-system-file false positives, restore/recovery tests |
| Conflict with Windows or another security product | Supported OS integration, no silent weakening, explicit coexistence matrix | Clean install, coexistence, upgrade/uninstall, boot and recovery test matrix |
| Misleading product status | Health derived from verified component state; explicit degraded/unknown values | UI state-transition tests and fault-injection evidence |

## Decisions that remain before implementation

1. Name and license the representative threat and benign corpora; approve the
   laboratory's held-out split, sample freshness, malware taxonomy and
   attribution rules for ransomware, trojans, worms, RATs, and infostealers,
   separate PUA reporting, minimum per-family detection floors, sufficient
   sample size, family weighting, repeated runs, and the one-sided 95%
   confidence-interval method before measuring the agreed detection thresholds.
   Require the lower confidence bound to meet each overall and per-family
   threshold. Separately agree on the benign corpus sample size and statistical
   method for a legally sourced benign corpus stratified across Windows files,
   widely used applications/installers, and common personal file types. Require
   its one-sided 95% upper confidence bound for false positives to be strictly
   below 1/100,000 and require zero false positives in the critical
   Windows/system-file subset. Report by stratum and test both gates at the
   final release candidate's shipping default settings. Freeze the evaluated
   build and inputs with hashes and build provenance; after a failed evaluation,
   require a fresh held-out split for any materially changed candidate. Report
   optional modes and individual detection layers separately.
2. Validate the initial Windows 11 Home/Pro x64 25H2 and 26H2 matrix against
   the dated reference builds and lower-spec compatibility test profile, plus
   ongoing monthly-update coverage. Approve exact physical lab systems and
   record their CPU/microcode, memory, storage, firmware, power/thermal
   settings, drivers, and OS updates. VMs are not release-performance evidence.
   Measure and publish minimum hardware requirements only after scan-time,
   responsiveness, and resource-use tests.
   ARM64, versions 26H1 and 24H2, Windows 10, and preview builds are excluded
   from the initial release.
3. Define idle measurement duration, CPU accounting, memory metric, thermal and
   background-work controls. Benchmark adaptive manual/full scans on
   representative hardware and set CPU, disk-I/O, responsiveness, pause/resume,
   and completion-time budgets from measured results. Define synthetic benign
   corpus distributions and repeatable interactive workload first, then run
   them across all four OS/hardware combinations.
4. Implement the agreed update freshness and offline-state policy. Design the
   offline-root and online-delegated signing-key lifecycle, including key
   rotation, revocation, emergency recovery, and compromise response.
5. Define operation-specific fail-open behavior, degraded UI copy, recovery
   handling, and what happens to detections already in progress. Initial
   objectives are degraded warning within 5 seconds and first recovery attempt
   within 30 seconds, with health cleared only after end-to-end verification.
6. Complete an independent Windows security review of the service/driver
   boundary, IPC, update trust, coexistence, and installer lifecycle.

No implementation of the proposed Windows service, driver, updater, or cloud
service should begin until these decisions and the Stage 1 security review
gate are recorded as approved.
