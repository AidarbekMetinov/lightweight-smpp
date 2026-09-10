# Review: allocation-efficient message TLV scans

## Scope and preserved contracts

The starting revision is `4da2aecd6394f9ff18d700bb8b47ae1422451002`.
The isolated worktree is `/tmp/lightweight-smpp-codec-efficiency`. This change
updates one package-private production type, `MessageTlvSupport`, and no public
API, wire layout, profile, test fixture, build configuration or runtime dependency.
There is no commit or edit to the root checkout in this handoff.

The earlier [allocation diagnostics](../ALLOCATION.md) attributed
22.64–28.98% of whole-recording sampled allocation weight to the first project
stack frame `MessageTlvSupport.count`. Those contended, instrumented campaigns
remain historical evidence. Their sampled percentages are not this experiment's
per-operation allocation denominator, and this refactor does not revise their
failed workload results.

`count` previously constructed a stream/filter/count pipeline for every tag
lookup, including an empty TLV list. `octet` similarly constructed a
stream/filter/find/map pipeline. Both now scan the existing immutable list by
index. Counting still includes every matching occurrence; octet lookup still
returns the first matching unsigned octet or `-1` if absent. A matching empty
value still fails rather than becoming an absent value. Neither method caches
per-message state, changes ordering, nor accesses mutable internal arrays.
`Tlv.value()` retains its defensive copy.

The full type, its consumers and relevant contracts were reread: `MessageCommandCodec`,
`MessageCommandCodecs`, `MessageResponseRules`, `CommonTlvSupport`,
`BroadcastTlvSupport`, `MessageTlvRules`, `TlvRules`, `TypedTlvRegistry`,
`MessageTlvExtension`, `MessageTlvValueCodec`, `TlvValueCodec`, `Tlv` and
`OptionalParameters`, together with [field](../FIELDS.md),
[message](../MESSAGES.md) and [TLV](../TLVS.md) contracts. The immutable
`List.copyOf` storage preserves iteration order. No supported extension requires
editing these scan methods or another subsystem.

Occurrence validation, raw byte/count preflight, typed interpretation, reserved
incoming-value handling, outgoing permission, payload exclusivity, complete SAR,
UDHI, callback multiplicity, network/node companions, number portability,
broadcast per-area companions and original-request response conditions remain
in their existing order. There are no weakened assertions or relaxed checks.

## Whole-type SOLID review

Final formatting ran before the inventory and this source hash. There is exactly
one changed repository Java file/type and no new, local, nested or anonymous Java
type in that file. The constructor still copies extension declarations,
rejects duplicate exact contexts and freezes its rules/registry composition.
The helper remains stateless between calls and safe to share to the same extent
as the explicitly supplied conforming TLV codecs.

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/MessageTlvSupport.java
type: kg.aidarbek.smpp.codec.MessageTlvSupport
sha256: c35e12f1f68f01cca9e9a8aeaaf4a01400803ef28b15693330f327b6fe3dec92
responsibility: Composes exact message TLV occurrence, supported-value and companion checks while retaining immutable raw storage.
consumers: MessageCommandCodec uses validation and the registry; CommonTlvSupport and BroadcastTlvSupport reuse companion/count/octet helpers; MessageResponseRules checks supported response diagnostics.
S: pass | Construction and all methods serve the one TLV validation composition; indexed scans remove transient collection machinery without adding scheduling, ownership or application behavior.
O: pass | Vendor variation remains explicit MessageTlvExtension and TlvValueCodec registration by profile, command and direction; no new tag-specific dispatch or global mutable cache is introduced.
L: pass | All duplicate occurrences and first-match unsigned values preserve ordered-list semantics, absent values remain zero count or minus one, matching malformed empty values still fail, and raw arrays remain defensively owned; the same 196 codec/architecture cases pass before and after.
I: pass | The package-private helpers expose only counts, first-octet reads and companion validation required by their message/common/broadcast consumers; public codec and extension interfaces are unchanged.
D: pass | Dependencies remain JDK collections and inward protocol/profile/codec contracts; no transport, endpoint, storage, measurement API or runtime dependency enters production.
findings: none
```

## Baseline and refactoring verification

This is a behavior-preserving refactor. The relevant existing behavior was green
before production changed, so no artificial failing behavioral test was created.
The existing companion, malformed-input, ownership, profile and independent-wire
tests already cover the public conditions being preserved. The disposable probe
also characterizes repeated counts, first-occurrence `255`, empty/absent lookups
and a matching malformed empty value on both source versions.

All Gradle invocations below used
`--console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=codec-efficiency'`
in the isolated worktree. Caches were preserved; no `clean`, forced rerun,
dependency refresh, disabled check or lowered validation limit was used.

| Order | Command / evidence | Actual outcome |
| --- | --- | --- |
| 1 | `./gradlew :test --tests 'kg.aidarbek.smpp.codec.*' --tests 'kg.aidarbek.smpp.ArchitectureTest'`; `/tmp/codec-efficiency-01-baseline-tests.log` | 196 cases in 32 classes passed, zero failures/errors/skips. Test execution was fresh; three compilation tasks came from cache. |
| 2 | `javac -Xlint:all -Werror` for the external probe; `/tmp/codec-efficiency-02-probe-compile.log` | Compiled without warnings against the frozen baseline classes. |
| 3 | Probe `submit-rich 3.4 100 100 1`; `/tmp/codec-efficiency-03-probe-fixture.log` | Independently assembled complete frame and lookup characterization passed. This short setup invocation is excluded from performance comparisons. |
| 4 | `python3 -B /tmp/codec-efficiency-measurements/run_probe.py baseline`; `/tmp/codec-efficiency-04-baseline-measure.log` | All 24 fresh forks completed and produced 120 measured rows before production was edited. |
| 5 | Same focused Gradle test command after replacing the two scans; `/tmp/codec-efficiency-05-refactor-tests.log` | The same 196 cases passed freshly; production compilation executed, test/example compilation stayed up to date, configuration cache reused. |
| 6 | `./gradlew spotlessApply`; `/tmp/codec-efficiency-06-format.log` | Formatter completed separately; no additional Java path changed. |
| 7 | `./gradlew solidReviewInventory`; `/tmp/codec-efficiency-07-inventory.log` | Inventory completed with the current whole-file hash shown above; review-tool compilation came from cache. |
| 8 | `python3 -B /tmp/codec-efficiency-measurements/run_probe.py candidate`; `/tmp/codec-efficiency-08-candidate-measure.log` | All 24 fresh candidate forks completed and produced 120 measured rows with the same probe bytes and configuration. |
| 9 | `./gradlew check`; `/tmp/codec-efficiency-09-final-check.log` | Failed in 1m51s: 764 fresh library cases included one TLS fixture failure; all 141 fresh simulator cases passed. All 62 review-tool cases came from cache, formatting passed, and 525 current type reviews reconciled. |
| 10 | `./gradlew :test --tests 'kg.aidarbek.smpp.transport.TlsCleanupTest.tlsSlowReaderDeadlineAbortsRawSocketAndSettlesAQueuedControlWithoutStartingIt'`; `/tmp/codec-efficiency-10-tls-focused.log` | The unchanged single TLS scenario passed freshly in 2s; compilation stayed up to date. This does not erase or establish the cause of the earlier suite failure. |

`MessageOptionalParametersTest` covers outgoing limits before interpretation,
unknown/reserved incoming retention, outgoing rejection, payload alternatives,
SAR/UDHI and callback/network/number-portability relationships.
`MessageResponseRulesTest` covers original-request context, supported failure
diagnostics and profile-specific error bodies. `CommonMessageValidationTest`,
`CommonTlvContractsTest`, `BroadcastOptionalParametersTest` and the complete
codec selection cover the shared helpers' other consumers. Architecture checks
exercise the actual production dependency rules and their negative probes.

## Focused allocation and CPU experiment

The probe is external tooling, retained under `/tmp/codec-efficiency-measurements/`.
It does not enter a repository source set or published artifact. Compilation and
test-cache hits are not measurements. The source, bytecode, complete command
lists, raw CSV rows, stderr files, environment snapshots and class manifests are
retained; no successful row was substituted for a failed run. All 48 measured
JVMs exited successfully, were reaped, and passed their fixture assertions.

The baseline ran at `2026-09-10T04:01:17.665502Z` through
`04:01:43.766020Z`; the candidate ran at `04:02:46.323898Z` through
`04:03:03.200492Z`. The host used Ubuntu OpenJDK
`21.0.12+8-1-24.04-Ubuntu`, twelve logical processors and the same fixed
`-Xms256m -Xmx512m` heap for every probe. No collector, carrier count, compilation
threshold or global JVM setting was changed. Parent load measurements had
finished before these probes began. One-minute host load snapshots were
2.10→2.50 for the baseline and 2.24→2.67 for the candidate; this was a shared host,
not an isolated machine.

Each of the eight scenario/profile combinations ran in three separate, sequential
JVMs. Each fork performed 150,000 warmup encode/decode pairs followed by five
measured rounds of 50,000 pairs. That is 6,000,000 measured and 3,600,000 warmup
pairs per source version. Every fork has a 45-second supervisor timeout. Each
measured pair encodes a preconstructed immutable request and decodes a separate
independently assembled full frame. Volatile sinks retain the latest encoded
array and decoded PDU; the checksum also consumes frame length and sequence.
Setup, printing and construction of the request/expected frame are outside the
measured intervals. The probe creates no sockets or application worker threads.

All payloads contain 160 deterministic binary octets. `submit-short` and
`deliver-short` use `short_message` with an empty optional block. `data-payload`
uses one `message_payload` TLV. `submit-rich` uses the payload TLV, all three SAR
companions, two callback numbers and their two presentation/display companions
in explicit wire order. Raw expected headers/field/TLV bytes are assembled with
JDK byte primitives, not the project's encoder. Both profiles use their own
explicit `ProtocolProfile`; the data request direction is `SUBMISSION`.

The measurement uses the JDK management extension's
[current-thread allocated-byte counter](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.management/com/sun/management/ThreadMXBean.html#getCurrentThreadAllocatedBytes())
and [current-thread CPU time](https://docs.oracle.com/en/java/javase/21/docs/api/java.management/java/lang/management/ThreadMXBean.html#getCurrentThreadCpuTime()).
Allocated bytes are JVM counter estimates, not retained/live heap. CPU time
covers the calling platform thread, excluding GC/compiler/other JVM threads;
nanosecond units do not promise nanosecond accuracy. Counter reads and volatile
sinks add a small common cost. Wall times are retained separately. Medians below
pool the fifteen measured rounds per row; they are observed comparisons, not
confidence intervals or production throughput claims.

| Profile / pair | Baseline bytes | Candidate bytes | Allocation reduction | Baseline thread CPU ns | Candidate thread CPU ns | CPU reduction |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 3.4 submit short | 9,168.00 | 2,443.21 | 73.35% | 1,188.43 | 443.54 | 62.68% |
| 3.4 deliver short | 9,230.77 | 2,432.00 | 73.65% | 1,276.59 | 458.78 | 64.06% |
| 3.4 data payload | 11,446.72 | 4,424.00 | 61.35% | 1,516.86 | 616.05 | 59.39% |
| 3.4 submit companions | 18,320.00 | 9,606.40 | 47.56% | 3,787.91 | 2,499.45 | 34.01% |
| 5.0 submit short | 9,279.24 | 2,484.21 | 73.23% | 1,352.41 | 446.86 | 66.96% |
| 5.0 deliver short | 9,728.00 | 2,480.00 | 74.51% | 1,451.98 | 467.70 | 67.79% |
| 5.0 data payload | 11,448.00 | 4,536.00 | 60.38% | 1,557.91 | 675.17 | 56.66% |
| 5.0 submit companions | 17,760.00 | 9,664.00 | 45.59% | 3,740.83 | 2,368.13 | 36.69% |

The frozen baseline and candidate each contain 202 production class files;
`MessageTlvSupport.class` is the only byte-different entry. The same compiled
probe executes against either class directory. Results and min/max values are
in `comparison.json`; the raw phase directories retain every measured round.
This is evidence of less codec allocation and calling-thread CPU for the stated
fixtures. It establishes neither a heavy-load success ratio nor capacity,
latency, GC CPU, memory retention or performance for other payload/tag mixes.
The user's end-to-end success objective is evaluated separately by the parent.

## External experiment type and function review

The temporary Java type is
`kg.aidarbek.smpp.codec.CodecAllocationProbe`, with no nested, local or anonymous
types. Its source is `/tmp/codec-efficiency-measurements/probe/CodecAllocationProbe.java`,
SHA-256 `b8236566162d7b90556f5be1fa0daaed4be46235bb84bae0005266c87f3deb23`;
its compiled class SHA-256 is
`8673931e74d92967d6f90f1a76e2073ba02c1d3f845dcaf645b443f5decbf46c`.
This external fixture is outside the repository's formatter and automatic type
inventory; its full immutable source identity is retained rather than presented
as a current library type review.

- **S: pass.** Constructor, bound parsing, fixed request/frame construction,
  lookup characterization, timed loop and `main` serve one finite comparison of
  the two codec scans; no workload policy or application implementation enters it.
- **O: pass.** It is a closed experiment with four named scenarios and two
  profiles. Baseline/candidate variation is classpath selection, and production
  code has no dependency on the probe or its management APIs.
- **L: pass.** It has no custom supertype. Its privately owned arrays and
  constructed immutable requests are confined to `main`; volatile sinks make
  encoded/decoded results escape without mutating them. Complete byte/equality
  checks run before timings, invalid/missing counters fail, iteration/round
  counts are bounded, and the output checksum is checked for every round.
- **I: pass.** The command accepts only scenario, profile and finite loop bounds;
  fixed probes need no session, transport, provider or publishing interfaces.
- **D: pass.** JDK measurement infrastructure is confined to external tooling;
  the probe calls codec/protocol/profile APIs and does not enter runtime artifacts.

`run_probe.py`, SHA-256
`5628bf1e47e7aacdb2b90a7551951d4a4e4f475f57f9d691ef981b6d9ac43111`,
has two functions (`file_identity`, `main`) and no custom type. **S: pass:** they
capture file identities and supervise the fixed finite matrix. **O: pass:** the
only variable is the explicitly selected baseline/candidate directory.
**L: pass:** fresh phase directories are required, each child has a timeout,
nonzero exits and incorrect row counts fail, and final results are written only
after all 24 forks complete. **I: pass:** one phase argument controls the fixed
experiment. **D: pass:** Python standard-library process/file/CSV handling stays
outside production. No callback, retained worker or third-party dependency is
introduced. Remaining findings in the changed type and external fixtures: none.

## Final integration verification

The complete isolated `check` is explicitly **failed**, not a successful full-suite
claim. `TlsCleanupTest.tlsSlowReaderDeadlineAbortsRawSocketAndSettlesAQueuedControlWithoutStartingIt`
expected 16 decrypted bytes but observed EOF at line 139. Its raw 8 MiB frame is
constructed directly by `TcpTransportTest.frame` using `ByteBuffer`; neither that
fixture nor the exercised transport calls `MessageTlvSupport` or a command codec.
The test starts a one-second write deadline and waits for the first decrypted
bytes before queuing its control frame. The original failure and all full-check
XML results are retained in `final-check-test-results/` within the experiment
directory. The unchanged focused rerun passed; no test, transport implementation
or deadline was changed in this handoff. The parent owns the separate fixture
sequencing investigation and final integrated qualification.

The focused before/after codec and architecture runs, independent fixture
assertions in all measurement forks, final formatting and current 525-type review
coverage pass. The only owned repository files are the one production source
and this review. The external experiment is delivered as evidence; none of its
management APIs, class files, scripts or outputs enter the library. Remaining
findings in the changed production type: none. The retained unrelated full-suite
failure is the explicit validation limitation at this handoff.
