# Step 16 standalone broadcast integration

The existing CLI composition now selects the broadcast adapter and registers
its three typed handlers. Help names the 5.0 TX/TRX restriction and raw content
requirement. The adapter's protocol, payload and lifecycle contracts are reviewed
in [the SMPP 5.0 report](0015-smpp5.md); this report reviews the complete changed
launcher, composition and independent-process fixture types.

## Actual TDD

The focused command was
`./gradlew :simulator:test --tests kg.aidarbek.simulator.SimulatorProcessTest.broadcastOperationsRunThroughTheStandaloneRegistry --console=plain`.
`/tmp/lightweight-smpp-step16-integration-red.log` records a real behavioral
failure: the standalone client rejected `broadcast` as invalid configuration
and exited 2 instead of 0. The adapter was absent from the registry. This was
a running process assertion, not a compilation or dependency failure.

The scenario exercises broadcast submission, query and cancellation with 4 KiB
raw payload configuration and TX binding under 5.0, then full rejection through
the ordinary bounded fault controller. It asserts ten successful responses and
ten receives per positive pair, and ten peer-negative/rejected outcomes for the
negative pair. Both process owners have bounded waits and unconditional cleanup.
The preexisting message/common-operation/configuration scenarios remain intact.

The same focused command passed after integrating the adapter and selecting it
through the existing registry (`/tmp/lightweight-smpp-step16-integration-green.log`):
one process test, four independent client/server pairs. The build took 12 seconds,
executed four tasks and restored matching library compilation from cache.
`spotlessApply` ran separately before that final source snapshot and reused the
configuration cache (`/tmp/lightweight-smpp-step16-integration-format.log`).

## Whole-type reviews

The following four identities cover all three changed integration files,
including the process fixture's nested result record. Hashes are SHA-256 of the
complete formatted file. No source-side instrumentation, new runtime dependency
or special broadcast scheduler was introduced.

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorMain.java
type: kg.aidarbek.simulator.SimulatorMain
sha256: c6c50f08186e493735299da7392220c6c8f386e260c0abe96d57823291884fd7
responsibility: Boots the standalone workload process, documents its input choices and maps parsing/execution outcomes to exit status.
consumers: Installed scripts and separate-process tests invoke main; SimulatorArguments and SimulatorRun own parsing and execution.
S: pass | Its complete implementation owns CLI bootstrap, environment credentials and result/error presentation; it implements no broadcast encoding or application service.
O: pass | Broadcast support adds help text at the launcher boundary; selecting and implementing operations remains with composition and the existing TrafficOperation seam.
L: pass | Help still exits without allocating endpoints. Valid runs retain 0/1 outcomes and invalid configuration exits 2 without printing secrets. Independent process cases verify the new operation selection and existing failures.
I: pass | The public static main entry point is the only launcher API; users need no protocol engine or measurement callback interface.
D: pass | Depends on the application parser/execution boundary and JDK streams/environment, without concrete socket or codec construction.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorRun.java
type: kg.aidarbek.simulator.SimulatorRun
sha256: 58dbfe2f5ad24182618bb3b231d531a6f3d68473390090f31905daab54519762
responsibility: Composes one finite configured operation, content plan, receiver, measurements, endpoint lifecycle and final report.
consumers: SimulatorMain supplies validated configuration and event output; focused adapters, TrafficRunner and report types provide delegated behavior.
S: pass | The whole type owns application composition and sequencing; broadcast binary fields, fault selection, pacing, metrics and file serialization stay in their existing focused collaborators.
O: pass | BroadcastTraffic extends the existing operation lookup and handler-registration seams. The common runner, content stream ordinals and request observation contract require no broadcast-specific branch.
L: pass | Preflight still occurs before report/endpoint allocation, unsupported helper content is rejected, and every selected sender uses the same bounded outcome/drain contract. Finally blocks stop receivers and shut down endpoints; the new positive/negative process scenarios exercise that path.
I: pass | Composition consumes the small TrafficOperation, ContentPlan and event interfaces and the public endpoint handler builder; no expanded interface burdens ordinary adapters.
D: pass | This application composition root selects concrete tooling adapters deliberately while depending on public library capabilities; protocol request engines and transport implementation details remain absent.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorProcessTest.java
type: kg.aidarbek.simulator.SimulatorProcessTest
sha256: d1d13ad6e2e26e308fbaad68864971d881dd6c4322664f98c0d2af9340534166
responsibility: Verifies standalone launcher configuration, bidirectional/common/broadcast exchanges, bounded faults and fresh report accounting using separate JVMs.
consumers: Jupiter invokes finite scenarios with per-test temporary directories; installed application entry points produce observable process outcomes and reports.
S: pass | Tests own process-level integration assertions; they do not duplicate wire encoding, fault hashing or the traffic scheduler. Shared pair/start helpers own only fixture setup and cleanup.
O: pass | The broadcast scenario supplies operation names and CLI options through the existing pair helper without changing prior cases or production contracts.
L: pass | Jupiter temporary-directory and timeout contracts remain intact. Child processes have bounded readiness and exit waits and are force-terminated and awaited in finally blocks. Assertions require exact success/negative/receive counts, accepted exit status and fresh reports.
I: pass | Fixtures use only CLI arguments, logs and reports; no private endpoint state, test-only runtime hook or broad fake is required.
D: pass | Depends on JDK process/file APIs, Jupiter assertions and public classpath identities; the independent JVMs use the same production composition as actual launchers.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorProcessTest.java
type: kg.aidarbek.simulator.SimulatorProcessTest.Pair
sha256: d1d13ad6e2e26e308fbaad68864971d881dd6c4322664f98c0d2af9340534166
responsibility: Carries the two immutable report strings returned after a finite test process pair terminates.
consumers: The enclosing fixture's scenario assertions read the client and server reports independently.
S: pass | The record only groups completed observations; process ownership and parsing assertions belong to the enclosing fixture.
O: pass | New scenarios reuse the same two-role result without adding protocol-specific components or inheritance.
L: pass | Record value/accessor semantics are preserved and both components are immutable Strings; no live child process is exposed as a completed observation.
I: pass | Two component accessors are sufficient for paired assertions; there are no lifecycle or sender methods for callers to implement.
D: pass | Depends only on JDK String values and holds no concrete protocol, transport, metric or filesystem dependency.
findings: none
```

## Final integrated verification

After the owner found and corrected the ignored incoming broadcast identity
edge, the two affected codec/test files and their reviews were recopied and
formatted separately. The earlier successful full build remains preliminary
evidence; `/tmp/lightweight-smpp-step16-integration-final2.log` is the complete
corrected result for
`./gradlew check build solidReviewInventory :simulator:installDist --console=plain`.
It passed in 1 minute 24 seconds: nine tasks executed, four restored matching
outputs from cache and ten were up to date; configuration was reused.

The resulting XML contains 664 library/architecture cases across 96 classes,
61 simulator cases across 20 classes, and 60 review-tool cases across four
classes, with zero failures, errors or skips. The simulator suite ran freshly;
matching library results came from the owner's cache and unchanged tooling
remained up to date. All 412 current identities reconcile between inventory,
coverage and whole-file source hashes. The owner's 38 and this integration's
four affected identities have current principle-by-principle evidence.

`./gradlew dependencies --configuration runtimeClasspath --console=plain`
confirmed no library runtime dependencies. Binary, source and Javadoc archives
contain exact Apache LICENSE/NOTICE and only expected production content.
The installed simulator retains exactly its own JAR, the library JAR and
HdrHistogram 2.2.2 with its license. No Cloudhopper source, adapter, build source
set or dependency is present in the project. Documentation and local links
were checked separately; `git diff --check` passed.

Seven freshly executed installed-launcher pairs exercised all three broadcast
operations in both TX and TRX modes plus a fully rejected TRX submission. Each
used two connections, window 8, 4 KiB raw configuration and four requests. Every
positive pair produced four successful responses and four receives; the negative
pair produced four peer negatives and four counted rejections. Both sides
reported complete cleanup and no skipped, rejected-at-admission, pending,
invalid-content or incomplete-assembly work. These are functional checks, not
throughput, geographic-area expansion or handset-delivery measurements.

Exact commands, per-file source hashes, executable identities and report hashes
are retained in `build/runs/step16-broadcast-20260909/manifest.json`, SHA-256
`521c54cc71822de6b86e2ddd21616b97081c95dce3c9a39cf8a64189f27766b1`.
The declared source identity is
`e0f0f9c049dd63c89cff6c78078adf9e6267acd9+dc30dfef9727a1a27e0bca07744d2301143c47746e5580367bf05270f90344dd`.
The manifest defines the hash algorithm and ordered source inputs; all runs
executed outside Gradle test caching.
