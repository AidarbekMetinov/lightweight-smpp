# Review: load efficiency and the 99% success floor

## Requirement and scope

The user set a minimum 99% success rate after reviewing the earlier failed load
targets. The denominator is every planned originating request in the measured
cohort. Generator skips and local rejections cannot disappear from it. Warmup
remains a separate, fully accounted cohort. The existing development target is
10,000 aggregate requests/second over 100 sessions; the soak profile is 5,000/s
for a full hour. These rates remain explicit local qualification workloads,
not a guarantee for arbitrary traffic, peers or deployment hardware.

This work starts from `4da2aecd6394f9ff18d700bb8b47ae1422451002`. The three bounded
parallel worktrees review codec allocation, endpoint encoding, and independent
success assessment. Root owns integration, the TLS fixture correction and actual
client/server campaigns. Cloudhopper code and dependencies remain outside the
project. The library keeps its existing runtime dependency boundary.

The original zero-skip verdict remains visible. An additive assessment evaluates
the requested 99% floor, full measurement duration, successful in-phase rate,
healthy outcomes, cleanup and configured resource/latency limits. A floor pass
does not rewrite a failed historical or current zero-skip result. Expected-fault
and receiving-only roles are distinguished from originating healthy workloads.

## Initial evidence

The earlier campaigns shared a heavily oversubscribed workstation. The first
new comparison ran one pair without competing load campaigns, with unchanged
runtime artifacts, 100 sessions, window 32, a 160-byte payload, 30-second warmup,
60-second measurement and 30-second drain. It used Java 21 and the unchanged
256 MiB initial/512 MiB maximum heap settings.

The SMPP 3.4 submission diagnostic planned 600,000 requests, skipped 246 and
completed all 599,754 admitted requests successfully. There were no rejections,
other terminal outcomes or pending requests. Eventual success/planned was
99.959%; 599,753 successes occurred within the measured phase. Scheduled p99
was 102 microseconds. Both endpoints and sampling workers completed cleanup;
final owned request/reply/decision/stream/connection counters were zero.

The legacy originating verdict failed on its warmup/measured generator skips.
Its unchanged production input digest is
`0040cb7a326693847f4e4504e0f219b25ce220e37a012a0bc93e1b97b627ab68`.
All commands, source/executable hashes, full reports, resource series and the
independent arithmetic are retained in `build/runs/efficiency-20260910/`.
This result precedes the code changes and must not be attributed to them.

## Changes and contract evidence

- [Codec scan refactor](0020-codec-efficiency.md): remove transient stream
  pipelines while preserving ordered occurrence/first-value semantics. The
  bounded before/after probe measures complete encode/decode pairs.
- [Endpoint refactor](0020-endpoint-efficiency.md): reuse an already validated,
  endpoint-owned frame and assign its reserved sequence, preserving admission,
  permission, cancellation and deadline order. Two fixed immutable codec
  catalogues also avoid rebuilding wrappers and lists for each connection;
  dispatch maps, role selection and configured limits remain connection-local.
- [Success assessment](0020-success-assessment.md): exact integer floor checks
  and direct report-evidence guards, with real Python red/green regressions.
- [TLS fixture](0020-tls-load-fixture.md): deterministic queued-control admission
  replaces a peer-read timing prerequisite; the write deadline is unchanged.

The codec and endpoint allocation comparisons are separate experiments. Their
percentage reductions cannot be added, and their calling-thread CPU/allocated
byte observations are not end-to-end capacity or retained-heap measurements.

The codec probe measured 45.59–74.51% fewer allocated bytes and 34.01–67.79%
less calling-thread CPU across its eight profile/mode fixtures. The complete
controlled endpoint send/response probe measured 34.2–36.9% less allocation for
160-byte submit/data cycles. Warm codec construction measured 18,200.7 to
6,489.1 bytes per instance, a 64.35% reduction, across three fresh JVMs per
variant. The default TLV support tables were already shared before this work;
the construction change does not rebuild or cache them. These bounded probes
do not establish the cause of earlier 1000-connection startup failures.

A separate read-only review of the six retained corrected-matrix connection
failures found 830–986 established sessions, no measured traffic, 26–28 sampled
server Java platform threads, and complete cleanup. Authentication concurrency
is four and handler concurrency is capped at 64; neither equals the configured
1000 sessions. No retained log reports native-thread allocation failure. The
server's original startup budget is 16 s plus 40 ms per configured session, or
56 s; the client's 40 ms spacing follows each completed bind. Budget expiry
under contention is consistent with the source and elapsed times, but the
retained exception-class-only failures do not prove the exact cause. The fresh
connection checks keep these settings unchanged.

## Integrated verification

The final integration commands, run from the project root, were:

```sh
./gradlew spotlessApply --console=plain
./gradlew :test --rerun :reviewTest --rerun :simulator:test --rerun check build solidReviewInventory generatePomFileForMavenJavaPublication generateMetadataFileForMavenJavaPublication :simulator:installDist --console=plain
python3 -B /tmp/lightweight-smpp-efficiency-python-checks.py
```

Only the three test tasks were explicitly rerun to verify the combined runtime
and fixture changes freshly. Compilation and configuration caches remained
enabled. The retained Python runner executes both `unittest discover` suites
and `tools/check_release.py --version 0.1.0-rc.1`; it supplies the final installed
launcher, source identity and a fresh retained raw-peer output directory.

The final formatted integration freshly passed 769 library/architecture tests,
141 simulator tests and 62 review-tool tests: 972 Java cases, with no failures
or skips. `check`, `build`, `solidReviewInventory`, POM/module metadata
generation and simulator installation passed. The current inventory covers
530 Java identities. The nine affected Java files contain 15 reviewed type
identities, including nested types and the allocation probes. The linked
change-specific reports review every affected whole type against all five
SOLID principles with final source hashes.

The Python suites freshly passed 25 publishing-tool tests and 59 simulator-tool
tests, with both optional installed-JVM methods enabled. Those methods expanded
to 56 raw-peer client/server fault pairs across both profiles. The artifact
inspector passed for `0.1.0-rc.1`, including licensed binary/source/Javadoc
archives, POM and Gradle metadata. Library runtime dependencies remain empty;
the simulator retains only the library and its existing HdrHistogram dependency.

Exact commands, fresh XML results, logs, current review inventory and artifact
hashes are in `build/runs/efficiency-20260910/`. Integrated tests are archived
in `integrated-java-results.tar.gz`; `integrated-java-counts.json` records the
counts. `integrated-python-result.json` and the Python logs record the installed
fault tests and publication inspection. The earlier TLS fixture failure and
all real assessment red runs remain in the retained evidence.

The frozen production/tooling digest is
`7021cf660bc306e158fd4ae42a5bf374438ce69a8a170f7c45db22b5e6ca6bb4`.
The library JAR SHA-256 is
`8426f15563a44efe4951c3ec61267cb1eccd431b632eed0e54a1bbbcaacb6879`;
the simulator JAR is unchanged at
`a59d553d3ec84de5d953a927696565f2c498384386a241a74a113426011d1d9e`.
`final-integrated-inputs.json` preserves every declared source input and
installed executable identity. Documentation changes do not enter that declared
production/tooling digest. Source and executable hashes are checked before and
after every campaign.

## Qualification plan

The immutable plan is `qualification-plan.json` in the run directory. All 12
planned campaign pairs completed; their actual outcomes are recorded below.

1. Apply reviewed changes, run full behavior/architecture/format/review checks,
   build the installed simulator and preserve its source/binary identities.
2. Repeat the initial 60-second diagnostic with the final runtime.
3. Run the complete five-minute 10,000/s target for SMPP 3.4 and 5.0, separately
   for submission, delivery and bidirectional data traffic, one pair at a time.
4. Run a separately labelled five-minute 20,000/s bidirectional diagnostic.
5. Run both full five-minute 1000-session connection profiles with their original
   1000/s offered rate and 40 ms spacing between initial binds.
6. If that higher-rate run meets the floor, run both full-hour bidirectional
   5,000/s soaks together as an explicitly combined 10,000/s endurance workload.
   This concurrency is part of the final soak scenario and is not labelled an
   isolated pair measurement.

The finite driver records exact arguments, before/after host observations,
source and installed-artifact hashes, original process/report verdicts and
independently recomputed per-originator success floors. Every attempt uses a
fresh directory. No build/test cache is a performance measurement. Owned process
groups have bounded cancellation and reaping. No rate, payload, window, timeout
or original acceptance rule is silently changed after an attempt.

## Completed qualification

All six full 300-second `W-TARGET` runs passed, with 17,988,550 successful
measurement requests from 18,000,000 planned. Each originating role passed both
99% floors individually. Role success ranged from 99.7257% to 99.9873%; scheduled
p99 ranged from 0.101 to 0.404 ms. All admitted warmup and measurement requests
succeeded, the full phases and cleanup completed, and active ownership ended at
zero. The original zero-skip originating verdicts remain failed.

The full 300-second, 20,000/s SMPP 5.0 bidirectional diagnostic passed as well:
2,997,337/3,000,000 client successes and 2,998,119/3,000,000 server successes,
or 99.9112% and 99.9373%. Scheduled p99 was 0.105–0.106 ms, with healthy outcomes,
full measurement, matched peer counts and complete cleanup.

Both full `W-CONNECTIONS` checks established all 1000 sessions, measured for
300 seconds at 1000/s without warmup, and passed the independent 99% floor.
The 3.4 run completed 299,977/300,000 requests (99.9923%); 5.0 completed
299,665/300,000 (99.8883%). Their scheduled p99 values were 1.009 and 1.027 ms.
Both retained the original 40 ms bind spacing and startup budget, had no
replacement or admitted-request failure, and returned all connection/ownership
counters to zero. These observations establish the local configured connection
profile for this build; they do not prove the cause of the historical failures.

The report helper's named healthy-throughput policy excludes `W-CONNECTIONS`,
which remains not applicable there. The independent retained driver explicitly
enforces both 99% floors plus full profile, latency, resource and cleanup guards
for those connection runs. No not-applicable assessment is treated as success.

The retained `cross-peer-reconcile.py` also checks every completed pair's exact
warmup-plus-measurement admissions against its opposite peer's received and
accepted totals, all initial binds, no replacements, zero receiver faults or
retention, and completed cleanup. All twelve completed pairs, including the initial
after-change diagnostic and both soaks, reconcile. Their result is
`cross-peer-reconciliation.json`; all raw report hashes remain intact.

Detailed completed results are in [performance qualification](../PERFORMANCE.md).
The two full-hour soaks ran together from 05:29:11 to 06:30:44 UTC as an explicit
combined 10,000/s workload. Both completed their full 60 s warmup, 3600 s measured
phase and 30 s drain. Every role planned 9,000,000 requests at 2500/s. SMPP 3.4
client/server successes were 8,981,271 and 8,980,984; SMPP 5.0 successes were
8,982,843 and 8,982,165. The total is 35,927,263/36,000,000, with 72,737 skips
retained in the denominator. Every admitted warmup and measurement request
succeeded; each role completed one measured-cohort success during drain, which
is excluded from its in-phase numerator.

All four soak roles passed both 99% floors independently: eventual success
ranged from 99.7887% to 99.8094%, and scheduled p99 from 0.472 to 0.751 ms.
The largest sampled RSS was 295.83 MiB; the largest recorded process high-water
mark was 296.05 MiB. All 100 initial sessions per endpoint bound without
replacement. Peer counts matched, endpoint/sampler cleanup completed, and final
active ownership was zero. The original zero-skip verdicts remain failed.

`qualification-execution.json` records the completed controller and original
per-campaign outcomes. `qualification-summary.json` records all 17 originating
roles, both success ratios, resource/latency observations, report hashes and
unchanged frozen source/executable inputs. Across the 12 final campaign pairs,
the lowest eventual success was 99.7257333% and the lowest normalized in-phase
rate was 99.7256666%. All attempts are retained; none were replaced by retries.

## Final compatibility verification

After every load process exited, the existing external SMPP 3.4 comparison
freshly passed all 15 cases with zero failures, errors or skips against the
frozen optimized JAR. This includes the documented expected failed-bind
incompatibility; it does not broaden the claimed operation/version subset.
All seven external peer/controller Java source hashes remained unchanged.
The only external build change selected the final library file input. The peer
artifact again matched SHA-256
`a9235c5c00270e0477bb5d32ad832f7c252ad2c8a397f48abc91486791923721`.

The command was:

```sh
./gradlew --project-dir /tmp/lightweight-smpp-efficiency-interop-20260910 test --rerun --no-daemon --console=plain
```

Compilation caching remained enabled; the test task executed freshly. The
standalone run completed in 8.98 s, with unchanged candidate and comparison
inputs. `external-interop-result.json`, `external-interop.log` and
`external-interop-results/` retain the exact input identities, command, outcomes
and XML reports. The executable comparison and all Cloudhopper source/dependencies
remain outside the repository. See [interoperability scope](../INTEROPERABILITY.md).

## Final build and document checks

After the qualification and comparison finished, the incremental command
`./gradlew check build solidReviewInventory generatePomFileForMavenJavaPublication generateMetadataFileForMavenJavaPublication :simulator:installDist --console=plain`
passed in 1 s with the configuration cache reused: three tasks executed and
24 were up to date. The unchanged Java test results were reused; the 972 fresh
cases above remain the integration evidence. Review coverage executed against
the updated reports. The log is `final-verification.log`.

The release artifact inspector passed again with
`python3 -B tools/check_release.py --version 0.1.0-rc.1 --output build/runs/efficiency-20260910/final-release-artifacts.json`.
Source, installed simulator and library archive hashes still matched the frozen
qualification inputs. The selected binary remains 384,256 bytes with no runtime
dependencies. No signing key, publication namespace or deployment action changed.

The document checker passed all 84 Markdown documents and 790 local links;
`git diff --check` passed. `document-check.log` and the retained checker record
the Markdown/link verification. `evidence-manifest.json` inventories the final
local evidence by relative path, size and SHA-256. Raw qualification verdicts,
failed historical campaigns and genuine TDD red results remain intact.
