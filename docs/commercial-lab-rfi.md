# Independent Evaluation Laboratory RFI and Draft Statement of Work

**Status:** Internal draft for vendor inquiry. Not a contract, legal opinion,
test authorization, or evidence that a laboratory has passed qualification.
No laboratory has been contacted or selected.

## Product and market context

- Product: planned paid consumer antivirus for Windows 11 Home/Pro x64.
- Initial market: Greece and the EU/EEA.
- Initial OS scope: Windows 11 25H2 and 26H2, generally available servicing
  releases with current applicable security updates.
- Target protection: commercial real-time blocking, including a Windows
  service and Microsoft-signed kernel component. The current repository is
  still a prototype and is not ready for efficacy or release claims.
- Initial RFI candidates: AV-Comparatives, SE Labs, and Virus Bulletin. AV-TEST
  is a reserve candidate pending confirmation that it accepts a bespoke private
  engagement for this scope. Public service descriptions are screening evidence
  only; all mandatory qualification gates require written confirmation.

## RFI purpose and response instructions

Request a written proposal and evidence sufficient to evaluate the mandatory
gates and the common scorecard. A candidate must answer each question directly,
identify any exception, and distinguish its standard service from proposed
custom work. Unsupported or unverified claims remain pending and do not count as
a pass.

No candidate should receive malware samples, personal data, source code, or
confidential product material during the RFI. A mutual NDA and approved
information-handling process are prerequisites to sharing non-public
architecture or build details. Any later transfer of personal data or
threat-corpus material requires separate legal and security approval.

## Copy-ready initial inquiry

**Subject:** RFI: independent custom evaluation of a planned Windows consumer antivirus

Hello,

We are planning a paid consumer antivirus for Windows 11 Home/Pro x64, initially
for Greece and the EU/EEA. We are seeking an independent laboratory for a
custom evaluation, not assuming that a standard certification or public test
series alone will satisfy our release gates.

Could you confirm whether you would consider this engagement and provide an
initial proposal covering:

1. Your recent Windows consumer-antivirus evaluation experience and any
   relevant conflicts, funding, or commercial relationships.
2. Whether you can evaluate malware detection by the ransomware, trojan, worm,
   RAT, and infostealer families and by detection layer, report PUA separately,
   and assess false positives on a legally sourced stratified benign corpus.
3. Your proposed lawful corpus sources/licensing basis, sample attribution,
   custody/security controls, confidence-interval method, sample-size
   recommendations, repeatability controls, and retest approach.
4. Your ability to test Windows 11 25H2 and 26H2 on four identified physical
   hardware cells, including a synthetic benign scan workload and idle/active
   performance measurements.
5. How you handle confidentiality, unfavorable findings, report publication,
   retention/destruction, incident notification, and any subcontractors.
6. Estimated schedule, deliverables, fee structure, prerequisites, and the
   process for agreeing a detailed protocol and statement of work.

At this RFI stage we will not provide malware samples, personal data, source
code, or confidential product information. Please identify any NDA or
information-handling steps required before a technical discovery discussion.
Please also identify any part of the requested scope that you cannot support
or that differs from your standard service.

This is an exploratory request for information and does not constitute a
commitment to purchase services. Payment for any later engagement will not be
contingent on passing or on favorable results.

Regards,\
Sentinel AV project team

## Mandatory pass/fail qualification gates

Reject a proposal that fails any gate. Record the response, evidence, reviewer,
and disposition for each item.

| Gate | Evidence required |
|---|---|
| Relevant experience | Recent, attributable experience evaluating Windows consumer antivirus or endpoint protection, including the product layers and environments tested. |
| Independence | Written disclosure of ownership, funding, material commercial relationships, conflicts, and controls. The laboratory must be free to report adverse results; compensation must not depend on passing or favorable findings. |
| Legal authority | Written description of lawful corpus access, licensing/permissions, jurisdictional basis, and permitted test activities for each proposed corpus. No samples are to be transferred to Sentinel or developer/CI environments. |
| Corpus security and custody | Security controls for storage, access, isolation, egress, logging, incident response, subcontractors, backup, retention, and verified destruction; named accountable roles and incident-notification timing. |
| Protocol and repeatability | A versioned, reviewable protocol with frozen test inputs, sample attribution, confidence-interval methods, repeat controls, deviation handling, and evidence sufficient for independent reproduction of reported calculations. |
| Required results | Ability to report the defined malware families and layers, PUA separately, benign strata, statistical bounds, failures, deviations, and limitations without hiding unfavorable results. |
| Scope and hardware | Willingness and capacity to test the four Windows/hardware cells, the agreed synthetic workloads, and any agreed service/driver reliability scenarios on identified physical equipment. |
| Contract terms | Acceptance of the agreed scope, no contingent payment, secure-custody requirements, retest protections, retention/destruction obligations, and written publication/claims restrictions. |

## Scored proposal rubric

Score only proposals that pass every mandatory gate. Set the threshold before
reviewing commercial proposals; the approved threshold is **80/100**.

| Category | Points |
|---|---:|
| Protocol, statistical rigor, and reproducibility | 30 |
| Threat/benign corpus provenance, representativeness, and family coverage | 20 |
| Reporting completeness, evidence quality, and repeatability | 20 |
| Corpus custody/security and incident handling | 15 |
| Independence, transparency, and conflict management | 10 |
| Total cost and delivery schedule | 5 |
| **Total** | **100** |

Each score must cite proposal evidence and a rationale. A score of 80 or more
does not override a failed mandatory gate. Record tie-break reasoning and do
not lower the threshold after proposals are received.

## Draft scope of work

The following scope is the basis for proposals. The protocol, sample sizes,
legal scope, and numeric floors marked TBD must be agreed in writing before any
licensed-corpus test begins.

### Work package A: Detection and false-positive evaluation

- Test the frozen release candidate with the final shipping default settings.
  Record build provenance and cryptographic hashes for the binary, policy,
  configuration, rules, and intelligence/definition set.
- Evaluate ransomware, trojans, worms, RATs, and infostealers as separate
  primary malware families. Report PUA separately and do not include PUA in the
  primary malware pass calculation.
- Report results by family and detection layer, with sample attribution rules,
  denominators, confidence intervals, misses, false detections, deviations,
  limitations, and repeatability evidence.
- The overall detection point target is at least 95%, and each family must meet
  a pre-agreed minimum floor. The one-sided 95% lower confidence bound must
  meet the overall target and each family floor. Per-family floors, taxonomy,
  weighting, sample counts, sample freshness, and repetition rules remain TBD
  until the lab proposes them and they are approved before testing.
- Measure false positives against a legally sourced, stratified benign corpus
  spanning Windows/system files, widely used applications/installers, and
  common personal file types. The one-sided 95% upper confidence bound must be
  strictly below 1/100,000, with zero detections in the critical Windows/system
  subset. The lab must propose strata, lawful provenance, sample counts, and
  the statistical method for approval before testing.
- After a failed evaluation, any materially changed candidate must be evaluated
  on a fresh, unused held-out split. Preserve the prior result and document the
  change that triggered the new split.

### Work package B: Performance and compatibility evidence

- Cover Windows 11 25H2 and 26H2 on two profiles each: baseline PC (4 cores,
  8 GB RAM, SSD) and low-spec compatibility profile (2 cores, 4 GB RAM, HDD).
  The laboratory must identify proposed physical systems and record CPU and
  microcode, RAM, storage, firmware, power/thermal settings, drivers, OS build,
  and patch level. Virtual machines may support preliminary checks but are not
  release-performance evidence.
- For idle measurement, use 15 minutes of warm-up followed by 8 hours of
  measurement and three independent repetitions per cell. Keep the UI closed,
  scheduled tasks and definition checks enabled, and do not run user-triggered
  scans. Report Sentinel-attributed and whole-system resource use; Defender
  remains enabled and unmodified.
- Normalize CPU use to the total capacity of all logical processors. Sum
  working set across Sentinel user-mode processes. Report peak working set and
  driver CPU/paged/nonpaged pool separately.
- For active scans, use a versioned synthetic benign-corpus generator with a
  fixed seed and a manifest/SHA-256 per generated file. Agree file, size, and
  archive distributions and steady/burst interactive workloads before testing.
  Run three cold-start scans per cell and report warm-cache results separately.
- The idle internal targets are p95 CPU below 1% and p95 total Sentinel
  user-mode working set below 200 MB on baseline hardware. The lab must propose
  active-scan, responsiveness, disk-I/O, pause/resume, and completion-time
  budgets after baseline runs; these are not yet numeric acceptance gates.
- The low-spec profile is a compatibility test, not a support commitment.
  Derive any minimum supported hardware only from approved measured results.

### Work package C: Reliability and evidence handling

- Propose safe fault-injection scenarios for service/driver availability,
  update verification, status reporting, and recovery. Do not test failure
  behavior on production systems or expose a host to unmitigated risk.
- Record degraded-state timing and recovery attempts against the internal
  objectives (visible degraded status within 5 seconds; first recovery attempt
  within 30 seconds). Define end-to-end evidence required before status returns
  to healthy. Final scenario-specific pass criteria require product and lab
  approval.
- Keep the licensed threat corpus in the laboratory's legally controlled,
  access-restricted environment. Do not put samples or payloads in this
  repository, developer workstations, or CI.
- The product must not upload file contents to external services during tests
  unless a distinct written approval, lawful basis, exact test design, and
  data-handling agreement are in place. The lab must disclose network access,
  egress controls, and any third-party services used.

## Required deliverables

1. Written protocol and statistical analysis plan, approved before testing.
2. Test environment inventory, physical hardware/OS details, product
   configuration, build provenance, and cryptographic hashes.
3. Corpus provenance and licensing summary, versions, taxonomy, sample counts,
   and non-sensitive identifiers/hashes only; no sample contents in the report.
4. Results by malware family and detection layer, with PUA separate; benign
   results by stratum; denominators, confidence bounds, misses, false positives,
   deviations, failed runs, limitations, and repeatability evidence.
5. Idle/active workload scripts or reproducible specifications, raw
   non-sensitive measurement data, aggregation method, and per-cell results.
6. Incident report for any custody/security event, including containment,
   notification, impact, and corrective action.
7. Final report, executive summary, retest proposal if needed, and a clear
   statement of what the results do and do not support. No certification,
   endorsement, or marketing claim is implied unless separately authorized in
   writing.

## Draft custody, privacy, and contract requirements

- The laboratory identifies whether it acts as controller, processor, or in
  another role for each data flow; identifies subprocessors and locations; and
  supplies applicable security and privacy terms for legal review. Do not
  presume a GDPR role or lawful basis from this draft.
- Contract only for the minimum product/build information and non-sensitive
  output required to run and verify the evaluation. Any collection of personal
  data requires a separate documented necessity, lawful basis, minimization,
  retention, access, and deletion review for the Greece/EU/EEA launch scope.
- Specify corpus ownership/licence, allowed use, sample access, copying and
  export restrictions, secure deletion, backups, audit evidence, incident
  response, and notification deadlines. The lab must provide a destruction
  certificate at contract end or agreed expiry.
- Define confidentiality, permitted disclosure, report review for factual
  errors, publication consent, name/logo use, and restrictions on unsupported
  efficacy or certification claims. Payment is for work performed and is not
  contingent on results.
- Define retest scope, fresh held-out splits after material changes, schedule,
  acceptance of deliverables, fees, cancellation, liability, insurance, governing
  law, dispute handling, and subcontractor approval. Legal counsel must review
  final terms.

## Open decisions before a test can be commissioned

- Exact corporate contracting entity, signing authority, budget ceiling, and
  procurement/payment terms.
- Approved minimum detection floor for each primary family; lab-supported
  sample sizes, family weighting, sample freshness, repetitions, and
  confidence-interval method.
- Named lawful corpus sources/licences and legal confirmation of permitted
  access, processing, storage, and evaluation for the launch markets.
- Exact physical PC models, minimum hardware claim, monthly Windows update
  coverage, active-scan budgets, and fault-injection acceptance criteria.
- Final publication policy, report confidentiality period, and which
  performance statements may be used in marketing.
- Privacy counsel's assessment of data roles and any personal-data processing
  in the Greece/EU/EEA launch context.

## Approval status

The user has approved the three-lab initial shortlist (AV-Comparatives, SE Labs,
Virus Bulletin), with AV-TEST as a reserve, and an 80/100 proposal threshold
after all mandatory gates pass. The initial market is Greece and the EU/EEA.
No vendor outreach, corpus transfer, legal review, contract, test run, or
laboratory selection has occurred. These remaining actions require a product
owner, contracting authority, legal/privacy review, and written vendor
responses.
