# Step 15 simulator integration

This integration selects explicit raw/GSM/UCS-2/SAR/receipt content through the
existing ContentPlan seam and records the selected convention in reports.
It rejects invalid helper sizes, incompatible originating operations and the
simulator's unsupported receipt direction before opening endpoints or reports.
MC-only receipt origination is a simulator policy; SMPP 5.0's message-type table
also defines ESME-originated receipt bits. No wire permission is weakened.

## Actual TDD and regression evidence

Commands used the Java 21 wrapper with `--console=plain` and
`-Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step15-integration'` in the isolated
integration worktree. Focused commands selected methods in HelperSimulatorTest
or the complete SimulatorArchitectureTest. Log paths begin `/tmp/step15-integration-`.

- `01b-content-red` rejected valid `--content` input before the server READY
  event; `02-content-green` then passed GSM/UCS2 separate-process scenarios under
  both profiles, including exact content reporting and receiver validation.
- `03-sar-stream-red` admitted all four requests but left two incomplete
  assemblies across two connections. Per-connection content ordinals preserve
  global request identity and deliver both fixture parts to each receive stream.
  That scenario passed under both profiles in `04-helper-green`.
- `04` was not an entirely green run: its initial flexible receipt/data fixture
  originated from the ESME under 3.4 and was correctly rejected by wire validation.
  The fixture was corrected to MC origination. This was not a library defect.
- `05-receipt-role-red` demonstrated missing early simulator-role preflight:
  invalid selected receipt origination reached execution and returned 1 rather
  than configuration exit 2. `06-helper-green` passed all four process methods,
  including receipt text/flexible/TLV variants, SAR isolation and invalid-input
  preflight. Both profiles were exercised without weakening wire checks.
- `07-helper-boundary-red` rejected 37 real dependencies on the new public helper
  package. `08-helper-boundary-green` permitted that public API family while
  preserving the meaningful forbidden-RequestWindow probe.
- The first `01` attempt failed to compile a checked URI conversion in the
  process fixture and is not behavioral red evidence. `09-format` ran separately
  before `10-final-validation`; the latter passed 49 simulator cases with matching
  prerequisite sources and warning-free tool Javadoc. These are test executions,
  not capacity measurements.

The [helper package review](0014-message-helpers.md) records strict conversion,
raw-value preservation, bounded reassembly and adapter evidence. The separate
[message architecture review](0014-message-architecture.md) checks ten forbidden
library/JDK edges against the pure helper package. The process-cleanup regression
and whole test-fixture review are recorded in
[the integration fixture report](0014-simulator-integration-types.md).

## Current type reviews

These nine identities cover the complete final six composition/configuration/
reporting/architecture files. The helper process fixture is reviewed separately.
Whole-file SHA-256 values were computed after formatting and independently
checked against the source inventory; subsequent source changes require a new
whole-type review. There are no known remaining findings in these types.

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/RunReport.java
type: kg.aidarbek.simulator.RunReport
sha256: 8008606806d4cda3954ea5b410141fa694eb90bf98df4408e1a0db9db79fc3a0
responsibility: Assembles explicit report semantics and immutable observations into a non-secret JSON-ready object tree.
consumers: SimulatorRun supplies completed snapshots; ReportWriter/Json serialize the result for run analysis.
S: pass | This type owns report field naming and meaning, including the actual selected content; measuring, scheduling, content conversion and file I/O are outside it.
O: pass | New content variants are reported through the configuration value without requiring new histogram or traffic engine code.
L: pass | The report now records config.content rather than the old raw constant; real process assertions verify the selected variant on both peers. Exact caller-supplied revision, executable hashes, latency populations, partial-result markers and cleanup/failure semantics remain intact; credentials are not fields.
I: pass | The static assembly method consumes only observations and configuration, without a callback or live-session requirement for report users.
D: pass | Depends on immutable snapshots and JDK collection/time values, not an encoder, network transport or histogram engine implementation.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorArguments.java
type: kg.aidarbek.simulator.SimulatorArguments
sha256: 822108c2ddf72c3392538ac4b103f45f2a68985297c4e2a6a9d8f68d26928959
responsibility: Parses explicit simulator command-line arguments into the bounded immutable run configuration.
consumers: SimulatorMain invokes parsing before executable composition; argument/process tests supply valid and invalid option vectors.
S: pass | Known option names, parsing, defaults and duplicate detection form one CLI input contract; no reports, credentials, sockets or workers are created here.
O: pass | Content selection adds one declared option and a raw default; adapter registries own behavior without changing scheduling or endpoint machinery.
L: pass | Existing defaults and strict duplicate/unknown/boolean/version handling are preserved. The content name passes through unchanged; valid GSM/UCS2/receipt/SAR process scenarios and early invalid selections exercise its consumers.
I: pass | The single parse operation serves the CLI without requiring callers to use measurement or protocol encoder functions.
D: pass | Depends on immutable configuration/value types and JDK parsing; it does not depend on a concrete network adapter or helper implementation.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorConfig.java
type: kg.aidarbek.simulator.SimulatorConfig
sha256: 3d16843cc391db8863b9539f9005b65c94b480f85beb75dab82c4677f4f99774
responsibility: Stores bounded non-secret inputs for one finite simulator run, including the explicitly selected content convention.
consumers: SimulatorArguments constructs it; composition, scheduling, endpoint setup and RunReport read their own input subsets.
S: pass | Its constructor enforces only configuration shape and finite allocation/time limits; encoding, wire placement and runtime I/O belong to adapters.
O: pass | A bounded content name is an input value; changing the helper factory does not change its resource validation or the traffic scheduler.
L: pass | The record retains immutable values and validated LoadPlan/FaultPolicy records; content is a non-null bounded name. Existing invalid-input and finite-plan tests remain valid, and helper process tests verify preflight before resource allocation.
I: pass | Callers read the record components they need without implementing unrelated scenario methods or inheriting a large endpoint interface.
D: pass | Depends on JDK values and explicit public SMPP version/bind values, with no socket, timer, content implementation or report writer dependency.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorConfig.java
type: kg.aidarbek.simulator.SimulatorConfig.Mode
sha256: 3d16843cc391db8863b9539f9005b65c94b480f85beb75dab82c4677f4f99774
responsibility: Names the two independently launched TCP/SMPP simulator owner roles.
consumers: Argument parsing selects the mode; endpoint composition, operation preflight and receipt scenario selection read it.
S: pass | Only client/server role identity is represented; protocol permission and content interpretation are separate policies.
O: pass | This is the deliberately closed pair of supported launch modes; operation and content extensions require no new mode.
L: pass | Enum identity and immutability are unchanged; exhaustive parsing rejects unknown launch modes and tests cover receive-only operation for either content interpretation.
I: pass | Consumers need only the constant identity; no unsupported behavior is attached to either constant.
D: pass | The enum has no infrastructure or implementation dependency.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorMain.java
type: kg.aidarbek.simulator.SimulatorMain
sha256: a86f9800acac93f96d88387505a9f898f92da0c7a8e96fcdad2dc11116e7cd1f
responsibility: Provides the standalone simulator entry point, help text, credential acquisition and documented process exit status.
consumers: Installed launchers and process tests call main; SimulatorArguments and SimulatorRun implement its parsing/execution boundaries.
S: pass | CLI bootstrap is its one responsibility; help identifies content choices and directs users to exact size/operation rules without implementing them.
O: pass | New scenarios are selected through configuration/composition; the launcher retains its simple dispatch and error mapping.
L: pass | Help exits successfully without opening resources. Valid runs preserve 0/1 outcomes; invalid content or role selections exit 2 before reports/sockets. Credentials remain environment-only with demo defaults, and failures do not print raw secret values.
I: pass | Only the public static main entry point is exposed; all non-launcher tool implementation classes remain package-private.
D: pass | Depends on the application parser/composition boundary and JDK process streams/environment, with no protocol engine dependency.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorRun.java
type: kg.aidarbek.simulator.SimulatorRun
sha256: ec78764a9034f208d74c6f399fa5e2a46ad120fe6706ee1d7cc920130ae27a5b
responsibility: Composes a finite simulator execution from public endpoint capabilities, selected content, bounded receiver decisions and reporting.
consumers: SimulatorMain supplies validated inputs and an event sink; ContentPlan and TrafficOperation supply scenario variation, while TrafficRunner owns pacing/accounting.
S: pass | Setup, execution and cleanup form the application composition lifecycle; payload conversion, protocol correlation, receiver policy, counters and JSON encoding stay in their focused components.
O: pass | The composition point selects MessageTraffic/CommonTraffic and raw/helper ContentPlan implementations; the stable scheduler and library request mechanism do not acquire encoding, receipt or common-command branches.
L: pass | Content factory and origin compatibility checks run before report/endpoint creation. Receipt MC-only selection is an explicit simulator policy. Round-robin sends use per-connection content ordinals while retaining global request identity; the real two-connection/two-part SAR regression proves complete isolated assembly. Existing partial-failure and bounded cleanup behavior remains unchanged.
I: pass | It consumes narrow operation, content, maintenance and event callbacks; receive-only runs skip the originating-operation check while still validating the selected receive content.
D: pass | Concrete construction is localized to this application composition root. Runtime pacing consumes interfaces and public endpoint/request APIs; no second correlation engine or transport implementation is introduced.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorArchitectureTest.java
type: kg.aidarbek.simulator.SimulatorArchitectureTest
sha256: 3840788834dcb9d53c21bf167411cba140dbeb8e66eceb8aa8879c49f6dea786
responsibility: Verifies that simulator production code consumes permitted public library APIs and rejects a duplicated request engine.
consumers: JUnit imports actual production classes; a deliberate RequestWindow fixture exercises the rule independently.
S: pass | Both tests establish the same dependency boundary and its non-vacuous failure behavior; protocol semantics and load assertions are tested elsewhere.
O: pass | The existing predicate admits public message helpers alongside public endpoint/protocol values and explicit configuration/observation classes; adding a content fixture does not require a new request engine exemption.
L: pass | Actual helper dependencies caused37 violations before the public-helper extension; production passes afterward while the forbidden RequestWindow fixture still fails the rule as expected. Test imports exclude test sources from the production claim.
I: pass | The fixture and production check use the same ArchRule; no unrelated benchmark or socket fixture is required.
D: pass | Depends on ArchUnit/JUnit and an explicit forbidden library type solely for the negative fixture. It remains outside both runtime artifacts.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorArchitectureTest.java
type: kg.aidarbek.simulator.SimulatorArchitectureTest#boundary/<anonymous>@20:109
sha256: 3840788834dcb9d53c21bf167411cba140dbeb8e66eceb8aa8879c49f6dea786
responsibility: Decides whether a class dependency belongs to the simulator public-capability boundary.
consumers: The enclosing ArchRule invokes DescribedPredicate.test for each dependency from imported production classes.
S: pass | The predicate only classifies dependency identities and public visibility; it does not execute library or tool behavior.
O: pass | Public message helper types join existing public endpoint/protocol families; other library packages still require exact approved names.
L: pass | The DescribedPredicate contract remains a total boolean classification with no side effects. The real helper-usage red/green and forbidden engine probe verify both permitted and prohibited edges.
I: pass | Only the required test(JavaClass) method is implemented; no extra contract is imposed on imported types.
D: pass | Depends on ArchUnit metadata and immutable names/visibility, with no runtime library construction.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorArchitectureTest.java
type: kg.aidarbek.simulator.SimulatorArchitectureTest.ForbiddenRequestEngine
sha256: 3840788834dcb9d53c21bf167411cba140dbeb8e66eceb8aa8879c49f6dea786
responsibility: Declares a deliberately forbidden library RequestWindow dependency for the simulator architecture negative probe.
consumers: SimulatorArchitectureTest imports its bytecode and expects a boundary violation naming RequestWindow.
S: pass | The fixture exists only to demonstrate the real forbidden dependency; it allocates or operates no protocol engine.
O: pass | The selected prohibited edge remains fixed while supported public helper/policy dependencies expand independently.
L: pass | It implements no production interface or resource owner, and has no runtime behavior; class identity and field declaration provide the intended bytecode evidence.
I: pass | Only the single forbidden field is needed; no permissive transport or request fake is introduced.
D: pass | Its concrete dependency is intentionally invalid input to the rule, never production composition or runtime API evidence.
findings: none
```

## Final combined verification

All final sources were formatted separately before the main-checkout inventory
and checks. `./gradlew test javadoc :simulator:installDist solidReviewInventory
--console=plain` freshly executed all 57 simulator cases across 19 classes and
produced warning-free tool Javadoc. The 634 library/architecture cases across 84
classes were up to date from their fresh successful library task in the preceding
integration command. That preceding command's simulator task still used the
pre-helper architecture boundary and reported 37 legitimate public-helper edges;
the completed integration admits that public package and the final check passes.
This preliminary failure is not claimed as a successful full build.

`./gradlew check build dependencies --configuration runtimeClasspath --console=plain`
passed with matching library/simulator tests and 60 unchanged review-tool cases
up to date. Formatting and SOLID coverage passed; all 381 current type identities
reconcile with whole-file source hashes. The Step 15 owner covers 23 affected
identities, these integration blocks cover 9 and the process-fixture report covers 2.
The pure message package adds no runtime dependency. All three library archives
contain exact LICENSE/NOTICE and expected production-only classes/sources;
examples, tests, review tooling, simulator and HdrHistogram classes are absent.
The simulator distribution retains exactly its tool JAR, the library and the
licensed HdrHistogram 2.2.2 dependency. Build/configuration caches remain enabled.

Fourteen fresh installed-launcher client/server pairs then exercised GSM/UCS2/SAR
multiple submission, 4 KiB UCS2 delivery and EXAMPLE/FLEXIBLE/TLV receipts under
both profiles. SAR multi used two connections and four total requests, proving
both parts reach each receive stream. Every pair reported four successful peer
responses, four received requests, no skipped/rejected/pending/invalid/incomplete
content, and complete cleanup on both sides. These runs are finite content
characterizations, not throughput or production-capacity measurements.
Exact commands, executable/source identities, outcome checks and raw reports
are retained in `build/runs/step15-variants-20260909/`; the manifest SHA256 is
`ccfac137fea11b28ba7f821962d22403482c4251042eaafee2914fda09cb52bf`.
The source identity is
`1be88b17b2e3c2a95f4cf4fdfcbecf89dc7ed266+af5d9256675239dbb7aa5dc223aa890471bcc2d1c85267ef7cc0eecd37412ab8`.

Full SMPP 5.0 broadcast/inventory completion, TLS/lifecycle hardening, sustained
heavy-load qualification and independent-peer interoperability remain Steps 16–19.
