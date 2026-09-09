# Review: Common-operation simulator integration

## Scope and contracts

This report covers the four root-owned simulator files changed for Step 14,
reviewing **seven current type identities**, including the process-pair record,
the architecture predicate's anonymous class and its permanent negative fixture.
The Step 13 application baseline is
`be7f4e51bf70b8265754796fa512904506b29104`; Step 14 supplies the common-operation
library and adapter prerequisites. Their separately owned Java types are reviewed
in [the common-operation report](0013-common-operations.md). The earlier whole
simulator contracts and original behavior evidence remain documented in
[the Step 13 report](0012-simulators.md).

`SimulatorRun` now selects common traffic after the existing message catalogue
and registers common typed receiving handlers. `SimulatorMain` describes the new
CLI operations. The process regression exercises query, cancel, replace and
multiple submission under each profile. It deliberately uses TX for both
versions: SMPP 3.4 replacement is not permitted in TRX. The simulator remains an
application over the public endpoint/request API; one library RequestWindow
still owns each connection's sequence, correlation, deadlines and cancellation.

The source review covered all methods and affected consumers of these four types,
not only changed lines. No additional Java/build source was edited by this
reviewer. The owner completed Spotless and inventory before these blocks; every
whole-file hash below was independently recomputed against the actual final
source. The inventory is `/tmp/step14-root-integration-types.tsv`, and all seven
identities also occur in the complete project inventory. No temporary Java probe
was introduced by this report-only review.

## Complete per-type SOLID review

### kg.aidarbek.simulator.SimulatorMain

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorMain.java
type: kg.aidarbek.simulator.SimulatorMain
sha256: d49c69159b509d3f67ee150df21c343a2eb6b895ff48d1102e297ee5f02827c3
responsibility: Provides the standalone simulator command-line entry, help text and process exit classification.
consumers: Installed launchers and SimulatorProcessTest call main; SimulatorArguments parses configuration and SimulatorRun executes the workload.
S: pass | Help, environment credentials, configuration-error classification and exit status all belong to one CLI boundary. The class does not encode PDUs, schedule traffic or calculate metrics.
O: pass | The help text now advertises implemented query/cancel/replace/multi client operations; runtime variation is delegated to the parser and run composition rather than added to a command loop.
L: pass | The final utility class has no custom substitution hierarchy. Help returns without opening endpoints; invalid arguments exit two, execution failures exit one, and successful work returns normally. Diagnostic text reports categories instead of credentials or command bodies. Process tests assert invalid-input/no-report behavior and real nonzero/zero process outcomes.
I: pass | The only public method is main; applications do not implement an all-operation simulator interface or gain access to library request internals.
D: pass | Depends on the simulator parser/run composition and JDK console/environment APIs. SMPP implementation and tool measurement dependencies remain behind their own collaborators.
findings: none
```

### kg.aidarbek.simulator.SimulatorRun

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/SimulatorRun.java
type: kg.aidarbek.simulator.SimulatorRun
sha256: f74699d82d3bee126daa9a290657f699607ad32c768493013d15defaa1589506
responsibility: Composes one finite simulator operation, receiver policy, workload execution, resource observations, cleanup and final report.
consumers: SimulatorMain calls execute; MessageTraffic/CommonTraffic provide TrafficOperation adapters, ReplyController supplies handlers, SimulatorEndpoint owns network resources and TrafficRunner/RunCriteria/RunReport own accounting.
S: pass | This is the application composition point: it sequences existing responsibility owners without implementing wire codecs, request correlation, fault decisions, metrics formulas or file formats itself.
O: pass | Actual common-operation variation is connected through CommonTraffic.find and register alongside MessageTraffic; both feed the same TrafficOperation and EndpointHandlers contracts. No second scheduler or per-command request engine is introduced.
L: pass | Unknown or forbidden operation selection fails before report/endpoint allocation. The no-operation server branch still performs finite maintenance, and active traffic uses the existing bounded runner. Try-with-resources plus finally stop receiver work and request endpoint shutdown; execution/cleanup failures become failed run criteria. Monotonic pauses preserve the interrupt flag. The eight common-operation process pairs and existing duplex/failure process cases exercise these composition contracts.
I: pass | Traffic generation needs only TrafficOperation.send and RequestObservation; receiving needs typed handler registration. Concrete report, endpoint and receiver owners remain internal application collaborators rather than one large public extension interface.
D: pass | The run depends on public EndpointHandlers and simulator contracts; operations return library RequestHandles. Library sequence allocation, correlation, deadline and cancellation remain in the library. Protocol/session policy values are read by the operation adapter rather than reimplemented in this composition.
findings: none
```

### kg.aidarbek.simulator.SimulatorArchitectureTest

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorArchitectureTest.java
type: kg.aidarbek.simulator.SimulatorArchitectureTest
sha256: b91da54e732f9faa645d889f082b0ec59aecd3d4987db52e736846e9d46655e5
responsibility: Enforces the simulator-to-public-library dependency boundary and proves its forbidden-request-engine detection.
consumers: JUnit executes two architecture cases; ClassFileImporter scans actual production simulator types and the permanent ForbiddenRequestEngine test fixture.
S: pass | Both tests and the predicate concern the same architectural obligation: a simulator may consume public capabilities and explicit policy/result values, but cannot own another protocol request window.
O: pass | The narrow named allow-list was extended for actual public ProtocolProfile, VersionNegotiation, SessionPermissions, SessionState and EndpointRole consumers. This avoids a blanket session/profile package exception while accommodating the common-operation adapter.
L: pass | The production import explicitly excludes tests, retains ArchUnit nonempty selection behavior and checks real current classes. The negative test separately imports its violating fixture and requires a failure mentioning RequestWindow. The real eleven-violation red and restored two-test green show the boundary changed deliberately without disabling detection.
I: pass | Consumers need one production rule and one negative-probe assertion; no full ArchUnit engine or runtime dependency is added to the simulator distribution.
D: pass | Depends only on development-only JUnit/ArchUnit and a test fixture reference to the deliberately forbidden RequestWindow. Production simulator code receives no dependency on architecture testing.
findings: none
```

### kg.aidarbek.simulator.SimulatorArchitectureTest#boundary/<anonymous>@20:109

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorArchitectureTest.java
type: kg.aidarbek.simulator.SimulatorArchitectureTest#boundary/<anonymous>@20:109
sha256: b91da54e732f9faa645d889f082b0ec59aecd3d4987db52e736846e9d46655e5
responsibility: Evaluates whether one ArchUnit JavaClass is an allowed simulator dependency.
consumers: The boundary rule invokes this DescribedPredicate for dependency targets in both production and negative-fixture checks.
S: pass | Its only decision is the exact package/public-type allow policy; test orchestration and class importing stay in the enclosing test.
O: pass | The explicit set lists new public policy/value types needed by common-operation preflight. Public endpoint/protocol types retain their existing visibility check, while RequestWindow and other unlisted library internals remain rejected.
L: pass | The DescribedPredicate override is a pure deterministic boolean query over non-null ArchUnit metadata, with no mutation or I/O. It accepts outside-library tooling dependencies by the documented rule and preserves the existing public-modifier requirement. Positive production and negative RequestWindow tests exercise both branches.
I: pass | Implements only the predicate test method and description; it does not require a custom importer or simulator fixture API.
D: pass | Uses ArchUnit metadata plus JDK names/modifier/set checks; its source dependency policy does not create a reverse runtime dependency from the SMPP library.
findings: none
```

### kg.aidarbek.simulator.SimulatorArchitectureTest.ForbiddenRequestEngine

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorArchitectureTest.java
type: kg.aidarbek.simulator.SimulatorArchitectureTest.ForbiddenRequestEngine
sha256: b91da54e732f9faa645d889f082b0ec59aecd3d4987db52e736846e9d46655e5
responsibility: Supplies one permanent compiled negative fixture referencing the library request-window owner.
consumers: Only ruleDetectsASimulatorTryingToOwnASecondProtocolRequestWindow imports this fixture.
S: pass | The sole field intentionally represents the forbidden dependency; the fixture has no runtime traffic, lifecycle or metrics responsibility.
O: pass | The real boundary evaluates the compiled dependency rather than a hard-coded false predicate. Future permitted public values need not weaken this permanent negative fixture.
L: pass | This final fixture has no custom supertype or resource contract; its field is never instantiated, so it allocates no worker or request engine. Its intentional architectural violation is the asserted test input, not an unresolved production finding.
I: pass | No interface or fake request methods exist; the field alone supplies the dependency needed by the rule.
D: pass | The test-only RequestWindow dependency is intentionally forbidden in production and detected by the actual rule; it is excluded from the production import and simulator distribution.
findings: none
```

### kg.aidarbek.simulator.SimulatorProcessTest

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorProcessTest.java
type: kg.aidarbek.simulator.SimulatorProcessTest
sha256: 65a1bbf2b3077b6dc543cceb56b8806b7515534a14f15267f13693ecbbe21df8
responsibility: Verifies standalone client/server process behavior, fresh reports, exit codes and bounded process cleanup.
consumers: JUnit runs four current methods; ProcessBuilder launches SimulatorMain with actual library/tool class locations, and Pair retains returned report text.
S: pass | All methods concern runnable process contracts rather than reproducing scheduling or protocol internals. The helper owns ephemeral-port discovery, bounded child waits and report collection for each pair.
O: pass | An overload supplies the explicit protocol version and option lists, allowing query/cancel/replace/multi scenarios to reuse the real launcher. The previous default-version helper and existing fault/process cases are preserved.
L: pass | Each pair has independent temp report paths, a finite JVM heap/lifetime, exit assertions and finally destruction/waits for live children. READY polling has an absolute bound; its short sleeps are real process-readiness polling, not simulated protocol timing. The new method runs eight pairs in TX under 3.4/5.0 and requires ten successes plus ten received operations each. Existing tests retain cleanup, resource CSV and credential-redaction assertions. Fixture revision text is test provenance, not an asserted benchmark measurement.
I: pass | The helper returns only two immutable report strings; process handles and mutable command builders remain locally owned. No library internal session/request engine is used.
D: pass | Depends on JDK processes/files, JUnit, the public SmppClient class location and tool-only HdrHistogram location. Child classpaths omit architecture/review test tooling. Same-library separate processes are functional evidence, not independent implementation interoperability.
findings: none
```

### kg.aidarbek.simulator.SimulatorProcessTest.Pair

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/SimulatorProcessTest.java
type: kg.aidarbek.simulator.SimulatorProcessTest.Pair
sha256: 65a1bbf2b3077b6dc543cceb56b8806b7515534a14f15267f13693ecbbe21df8
responsibility: Stores the immutable client and server report text from one completed process scenario.
consumers: SimulatorProcessTest assertions inspect client/server JSON text after the helper has handled process cleanup.
S: pass | The two record components form one paired observation; it owns no processes, files or cleanup behavior.
O: pass | The fixed two-report shape is sufficient for current process assertions; different workload options reuse the record without subclassing.
L: pass | String components are immutable, so generated record equality/hashCode and accessors preserve snapshot semantics. It contains completed report text, not live mutable buffers or child handles; the parent tests verify success/negative/timeout counts in the appropriate side.
I: pass | Only client and server accessors are required by the assertion consumers; no generic report framework or mutable process controls are exposed.
D: pass | Depends solely on JDK String/record value semantics and remains a private test member, outside runtime artifacts.
findings: none
```

## Actual TDD and architecture evidence

The integration owner performed the commands; this reviewer inspected the logs
and final code without executing a second concurrent build in its worktree.

The actual command selector for the common-operation CLI cycle was:

```shell
./gradlew :simulator:test --tests 'kg.aidarbek.simulator.SimulatorProcessTest.commonOperationsRunThroughTheStandaloneRegistryUnderBothProfiles' --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step14'
```

- `/tmp/step14-integration-03-registry-red.log` compiled and executed the new
  process test. The child CLI exited 2 with invalid configuration instead of 0
  because the standalone registry did not resolve the requested common operation.
  This is the relevant behavioral red.
- `/tmp/step14-integration-04-registry-green.log` failed with a child
  `NoClassDefFoundError` during concurrent compilation. That infrastructure/build
  overlap is not behavioral evidence and is not described as a green run.
- `/tmp/step14-integration-05-registry-green.log` exposed an incorrect test setup:
  3.4 replacement was requested in TRX. Correcting the fixture to TX follows the
  already-defined version permission, rather than weakening production policy.
- `/tmp/step14-integration-06-registry-green.log` freshly compiled the corrected
  test and executed it successfully, reusing the configuration cache. This is
  **one discovered Jupiter test method containing eight separate process pairs**:
  four operations times two profiles, each asserting ten successful requests and
  ten received operations. It is neither eight discovered test cases nor a heavy
  load qualification run.

The initial public-dependency rule produced a real compiled architecture red in
`/tmp/step14-full-behavior-checkpoint.log`: the 45-case simulator run failed one
architecture test with eleven dependencies involving the newly used public
profile/session policy values. The correction explicitly admits only the needed
named types, rather than allowing the whole internal library namespace. The
architecture selector is:

```shell
./gradlew :simulator:test --tests 'kg.aidarbek.simulator.SimulatorArchitectureTest' --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step14'
```

`/tmp/step14-integration-02-public-policy-green.log` freshly compiled/executed the
architecture test and passed. Both production dependency validation and the
permanent `ForbiddenRequestEngine` detection remain present. Current XML at
review time records both architecture cases and all four process methods with
zero failures/errors. The owner runs final compilation, formatting and full
review coverage after this report is supplied; that final build is separate from
these inspected targeted execution records.

## Adjacent endpoint review finding and closure

The same reviewer performed a bounded read-only pass over NotificationExchange,
OutbindFlow, EndpointConnection and the reversed outbind owners. One concrete
failure boundary was missing: successful outbind authentication called
`beginClientBind` without handling request-window admission failure. A connection
using exactly two notification slots consumed them for readiness/termination;
its follow-up bind then failed with notification backlog, leaving the socket open
until the later bind deadline.

The owner added the regression before correction. In
`/tmp/step14-27-outbind-admission-failure-red.log`, seven lifecycle cases execute
and the new immediate-close assertion fails with a raw-peer read timeout. The
matching green log freshly compiles the correction and executes lifecycle,
outbind and alert selections successfully. The current callback catches
`RuntimeException` from bind admission and calls `fail(admissionFailure)`, matching
the existing connection-start boundary and retaining the original local failure.
The reviewer checked both logs and source; that finding is closed. The endpoint
types and their hashes belong to the common-operation owner's report, not the
seven tagged integration identities above.

No further concrete finding remained in that bounded pass: queued notification
cancellation competes with the write guard, notifications share bounded physical
handler ownership and invocation order with messages, graceful drain includes
notification work, and late/duplicate outbind decisions cannot reopen a closed
connection. This source review is not a claim that every concurrency interleaving
or independent peer was executed.

## Remaining scope and review outcome

All five principles were reviewed for each of the seven final identities; no
known violation remains. The integration adds no library runtime dependency and
no second protocol request engine. Existing report schema and lifecycle/accounting
owners remain separate. Functional process reports and ordinary cached test
outputs do not substitute for fresh benchmark runs or independent-provider
interoperability, which remain their later roadmap evidence.
