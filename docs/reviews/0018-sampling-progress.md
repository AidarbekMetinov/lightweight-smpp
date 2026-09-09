# Review: isolated blocking simulator observations

The Step18 sampling worker could consume the only Java21 virtual-thread carrier
while a resource or pressure observation blocked under its sampler's intrinsic
monitor. A virtual thread needed to release that observation could then never
run. Sampling now owns one dedicated daemon platform thread. The change neither
increases the library's carrier pool nor adds queued sampling work.

The production baseline is the accepted Step18 source. Its original
`SamplingLoop.java` SHA-256 was
`5ee473b6029cabfbbd861ba5bdd820eec11b93bd31d3743246d40bf1ef8d5d8f`.
Only that production file changes. Two new test files contain three identities;
all four affected identities are reviewed below against their separately
formatted whole-source hashes. Copied Step16/17 prerequisites and the earlier
Step18 implementation are excluded from this follow-up patch.

The Java21 [virtual-thread guide](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
describes intrinsic-monitor and native-call pinning. Proc-file, JMX and buffered
report I/O belong to this one fixed observer resource. The existing
[ThreadMXBean](https://docs.oracle.com/en/java/javase/21/docs/api/java.management/java/lang/management/ThreadMXBean.html)
observations count platform threads; the new worker therefore appears in the
ordinary resource reports rather than being hidden as a virtual thread.

## Contracts and caller audit

- `SimulatorMain.main` calls `SimulatorRun.execute` on the launcher main thread.
  Resource/pressure construction, baseline marking and the final sample execute
  on that same platform owner. `TrafficRunner` invokes its measurement-start
  action directly; it does not move the baseline onto an executor.
- Recurring resource/pressure samples run only through `SamplingLoop`. Its
  dedicated platform worker now covers both the real sampler monitors and their
  potentially blocking collaborators. `ResourceSampler.tick` has no production
  caller. The samplers are package-private tool components, not embedding APIs.
- The one-worker, no-backlog policy is retained. Elapsed observation slots count
  as skipped; a blocked sample is not replaced with additional workers. Stop is
  bounded and returns false while physical observation work remains. Release
  allows the same worker to finish and retire; daemon status preserves the
  previous process-liveness policy for an irrecoverably blocked observer.
- `ReplyController` calls decision advancement and cancellation outside its
  monitor. `DecisionQueue` completes/cancels futures after releasing its monitor.
  Its production cancellation supplier is an atomic request-cancellation read.
  Their remaining guarded callbacks are finite in-repository content helpers;
  no additional receiver monitor conversion was needed for this defect.

## Actual TDD and verification

All Gradle commands use the isolated `/tmp/lightweight-smpp-step18` worktree and
append `--console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step18'`.
No build runs concurrently with another build in this worktree.

1. `/tmp/step19-sampling-01-red.log`: `./gradlew :simulator:test --tests
   'kg.aidarbek.simulator.SamplingProgressTest'` executed both new scenarios.
   Both compiled and failed with `TimeoutException` at the virtual releaser's
   bounded `CompletableFuture.get`, producing child exit1 instead of0. The
   resource and pressure cases held the real sampler guards. This was an
   observed behavioral failure, not a compilation or dependency failure.
2. `/tmp/step19-sampling-02-green.log`: after replacing only the worker builder,
   the same test plus `SamplingLoopTest`, `ResourceSamplerTest` and
   `PressureSamplerTest` passed all six cases freshly.
3. `/tmp/step19-sampling-03-format.log`: a separate `./gradlew spotlessApply`
   completed before the final hashes and inventory.
4. `/tmp/step19-sampling-04-final-check.log`: `./gradlew :simulator:check
   :simulator:javadoc :simulator:installDist solidReviewInventory` passed. The
   simulator executed95 cases in34 classes, with zero failures/errors/skips;
   Javadoc, installable distribution and whole-worktree type inventory passed.
   No load measurement was taken from a test-cache result.
5. `/tmp/step19-sampling-05-resource-probe.log` and
   `/tmp/step19-sampling-06-pressure-probe.log`: separately launched formatted
   probes both completed with actual platform-thread counts7 before,8 during,
   and7 after the one sampler. The same Java21 one-carrier flags were used.
6. `/tmp/step19-sampling-07-review-check.log`: the repository's compiled
   `ReviewCheck --check-files` accepted exactly the three owned Java sources
   against this report and wrote `/tmp/step19-sampling-coverage.tsv` with all
   four identities. The whole-worktree inventory independently lists the same
   source/type/hash triples. This is focused follow-up coverage; historical and
   copied prerequisite types remain covered by their respective owners.

Each regression launches a real child JVM with scheduler parallelism and maximum
pool size both fixed at1. Constructor sampling returns immediately; the second
observation blocks on an explicit gate. A separate virtual thread must release
it within2seconds. The fixture also verifies that a short stop reports retained
work, exactly one observation remains active, no sample executes after stop,
missed slots are counted, and the real platform worker is visible to
`ThreadMXBean` while active and absent after retirement. The platform driver
releases the gate in `finally`, joins the worker/releaser, and the JUnit owner
forcibly reaps the bounded child if needed. No elapsed-workload throughput claim
is made by these progress and ownership tests.

## Whole-type review

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SamplingLoop.java
type: kg.aidarbek.simulator.SamplingLoop
sha256: b11a8924829dccc2d683aee2a023a4d009ec0b30a6d46d84fe64cbb866d4ec27
responsibility: Owns one periodic observation worker and reports its physical stop, skipped slots and failure.
consumers: SimulatorRun supplies resource/pressure sampling, starts once, observes failure/skips and stops within its cleanup bound.
S: pass | All state and methods govern the same fixed observation worker; proc reads, endpoint snapshots and report formatting remain with their existing collaborators.
O: pass | Runnable and the bounded interval provide the supported work variation; changing measurement content does not alter scheduling or retirement, and no executor framework is introduced.
L: pass | AutoCloseable close is idempotent and nonwaiting; start rejects reuse or closed ownership, stop validates its bound and avoids self-join, interruption propagates, blocked work remains owned, and eventual retirement and no backlog pass existing and one-carrier regressions.
I: pass | The owner uses the small start/stop/close and failure/skipped observation surface without receiving the worker, queue or sampling internals.
D: pass | Variable observation work is injected as Runnable; choosing one JDK daemon platform worker is the component's explicit resource-ownership responsibility and isolates blocking tool I/O from library carriers.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SamplingProgressTest.java
type: kg.aidarbek.simulator.SamplingProgressTest
sha256: 0bd2239dab96a0fe1fb579cc2d9eda589bcfbfa0a8eb1e627e7b336b2764dab4
responsibility: Checks both sampler progress scenarios in finite child JVMs with an independently constrained virtual scheduler.
consumers: JUnit invokes two cases; each launches SamplingProgressProbe with fresh output and report paths and verifies its exit and ownership result.
S: pass | Both cases and private helpers belong to the same isolated-scheduler regression, including bounded process cleanup and diagnostic capture.
O: pass | The scenario parameter reuses launch and assertion behavior for resource and pressure sampling without duplicating lifecycle logic; wider simulator features need separate tests.
L: pass | Tests use real Process and JDK thread behavior, preserve fresh-file ownership, require bounded exit and reap in finally; the exact unchanged assertions observed both old-code timeouts and new-code progress.
I: pass | JUnit needs only the two test methods and temporary directory; process/classpath helpers are private and no unused fixture interface is imposed.
D: pass | Depends on JUnit and JDK process/filesystem contracts; compiled simulator/library/HDR locations form the explicit child classpath without private endpoint or transport access.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SamplingProgressProbe.java
type: kg.aidarbek.simulator.SamplingProgressProbe
sha256: bbb5b427394d25c0dff7ec9211a296d66aa8edc2b24c78e6c262cf6adb2de3f5
responsibility: Coordinates one blocked real sampler and a virtual releaser, then verifies bounded sampling and platform-worker accounting.
consumers: SamplingProgressTest launches its test-only main with one known scenario and a fresh report directory.
S: pass | Scenario construction, progress assertions and fixture cleanup all establish one observation-isolation contract; no network workload or reporting policy is implemented here.
O: pass | The real ResourceSampler or PressureSampler supplies the Runnable under test; shared gate and ownership assertions stay identical for both supported scenarios.
L: pass | Real latch, future, Thread.join and ThreadMXBean contracts establish visibility and retirement; the platform finally path releases blocked work even when progress fails, no callback or transport double relaxes production semantics, and the probe remains outside all published artifacts.
I: pass | Only a test-only main is exposed for the JVM launcher; assertion and coordination helpers do not add application APIs.
D: pass | Uses the real SamplingLoop, samplers, ReportWriter and RunEnvironment with their observation-supplier seam, plus JDK coordination and management APIs rather than reflective lock mutation.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SamplingProgressProbe.java
type: kg.aidarbek.simulator.SamplingProgressProbe.Gate
sha256: bbb5b427394d25c0dff7ec9211a296d66aa8edc2b24c78e6c262cf6adb2de3f5
responsibility: Holds precisely the second observation until explicit release and records its worker's real management visibility.
consumers: The enclosing probe adapts observe through the existing sampler Supplier boundary and owns both release and final joining.
S: pass | Latches, call count and worker/accounting observations describe the same controlled blocking point; constructor sampling remains immediate.
O: pass | Generic value return supports both immutable observation types without changing coordination, and the fixture deliberately exposes no general scheduling abstraction.
L: pass | The Supplier adaptation returns the supplied immutable value, models a legitimate blocking observation, restores interruption before reporting failure, and publishes recorded fields through latch/join happens-before edges.
I: pass | The private nested fixture exposes only the state needed by its enclosing progress assertions; it does not implement a broad fake sampler or filesystem interface.
D: pass | Coordinates through real JDK latches/atomics and samples the same JDK management source used by production reports; it does not depend on endpoint implementation locks.
findings: none
```

## Fresh corrected-candidate allocation diagnostics

This is a documentation/measurement follow-up to the sampler correction, with no
Java or production-tool source change. The four existing reviewed sampler types
and their hashes above are unchanged; no new project-owned Java types, fixtures or
fakes require SOLID review. No artificial TDD red is claimed for this diagnostic.

On 2026-09-09, the existing full candidate campaign runner executed two fresh
sequential pairs through the inspected external JFR wrapper. The commands,
child arguments, initial/final OS snapshots and all failed results are preserved
under `build/runs/step19-corrected-jfr/`. The source digest is
`fb3db4466410adc08ae1521edd2b4713134de38eed5eb7458eff86c95842b68c`;
installed library SHA-256 is
`609e0d4e6150e3704942339a9df621465f662ef86e52241bc696c2adbd5a717f`
and simulator SHA-256 is
`47705647225df458d2f4fc8cb18c12a37198d82b72c25c8fbace72d308649161`.
A before/after manifest verifies the candidate inputs and executables were not
mutated. No candidate rebuild, test-cache measurement, forced GC, carrier-pool
change or global JVM option was used.

Actual bounded execution evidence:

- 13:49:16.722–13:50:13.924 UTC: SMPP 3.4 submission, 30-second measurement,
  5-second warmup and drain bound, 500 offered/s, two connections, window 16,
  raw 160-byte payload. Measurement had 15,000 planned, 1,281 skipped and
  13,719 successful admitted requests; the campaign correctly exited 1.
- 13:50:14.536–13:51:08.376 UTC: SMPP 5.0 bidirectional data, same aggregate
  settings, 250 offered/s from each role. Client measurement had 7,500 planned,
  97 skipped and 7,403 SUCCESS; server had 7,500 planned, 112 skipped,
  7,387 SUCCESS and one LOCAL_FAILURE of unreported precise cause. Campaign
  exit 1 and the original healthy criteria were retained.
- Both pairs had zero campaign launch/cleanup errors, complete endpoint/sampler
  termination and reaped child processes. All eight phase cohorts reconcile;
  opposite receiver lifetime counts equal warmup-plus-measurement successes.
  Latest sampled connection/request/reply/decision/stream ownership is zero.
- `jfr summary` and selected JSON extraction succeeded for all four bounded
  recordings. Independent counts are 982/800 samples for submission
  server/client and 1,305/1,315 for data server/client. Each has zero DataLoss
  events, completed chunk headers and a final size below 256 MiB. Grouped
  sample counts and weights reconcile exactly with retained event records.

The one-off driver and extraction arithmetic are investigation artifacts under
the run directory, not additions to the shipped simulator. Their commands and
manual checks are retained in `invocations.json`, `extraction-commands.json`,
`verification.json`, `observed-inputs.json` and `post-run-integrity.json`. The
[allocation report](../ALLOCATION.md) documents environment contention, exact
artifact/recording hashes, configuration, actual outcomes, class weights and
primary JDK references. The estimate is whole-recording sampled allocation
pressure, including recorded startup and JSON reporting, not exact bytes/request,
phase attribution, retained-memory proof, latency acceptance or a capacity claim.

Document verification checked the report's copied counters/hashes against the
retained JSON and JFR data, primary-source interpretation, local evidence links
and unchanged Java/type inventory. Existing sampler source hashes remain valid.
