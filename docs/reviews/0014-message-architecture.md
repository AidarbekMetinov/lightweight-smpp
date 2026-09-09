# Review: pure message-helper architecture

## Scope and contracts

Starting revision: `e79b9f4cb905056fe6d91ded563e2a19b48ce4d9`. The Step 15
message-helper implementation and simulator adapters are reviewed separately in
[0014-message-helpers.md](0014-message-helpers.md). This follow-up changes the
complete existing `ArchitectureTest` and adds its one named nested negative
fixture. There are no anonymous or local types in this file. No production
Java type or copied simulator prerequisite is part of this architecture change.

The new rule permits message helpers to depend on their own package, immutable
protocol values, and JDK language, math, time, collection, function, regex and
stream facilities. The `java.util` package itself is allowed, with only those
three named subpackage families; `java.util.concurrent` is excluded. Codec,
profile, session, request, frame-port, concrete transport, endpoint, network and
file dependencies remain outside the helper boundary. Existing codec rules also
continue to exclude the message package, so wire codecs cannot silently select
an application text encoding through these helpers.

The existing production import check now explicitly requires TextEncoding and
SegmentReassembler and excludes the negative fixture. The permanent negative
case applies the same dependency rule to a separately selected fixture and
requires a violation naming each of ten forbidden dependency types. It tests
the dependency predicate without weakening the production package selector or
enabling empty-rule success.

The full affected test type was reviewed, including the existing protocol,
codec, profile, pure-session, request, frame-port, transport, endpoint,
coordinator and package-cycle rules. This is structural evidence: permitted JDK
APIs are not a proof that arbitrary future helper code terminates or respects
memory bounds. The helper behavior/ownership tests and complete per-type reviews
provide that separate evidence for the implementation delivered here.

## Actual TDD and verification

Every Gradle command used the suffix:

```
--console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step15'
```

Commands ran sequentially in the isolated Step 15 worktree, preserving normal
build/configuration caches. Logs are retained under `/tmp/step15-...` and copied
into the handoff evidence directory.

1. The runnable rule declaration initially permitted the library/JDK graph,
   representing the missing pure-helper restriction. The new permanent negative
   fixture then demonstrated that this graph was too permissive.
   `47-message-architecture-red` used an unqualified `test` task and failed
   because Gradle also selected simulator tests with the library-only filter.
   That task-selection failure is **not behavioral red evidence**.
2. The corrected command
   `./gradlew :test --tests kg.aidarbek.smpp.architecture.ArchitectureTest.messageBoundaryRejectsStatefulLayersAndRuntimeInfrastructure`
   executed the new assertion in `47b-message-architecture-red`. It failed with
   expected `hasViolation=true`, actual `false`: the forbidden dependency fixture
   was accepted.
3. The dependency allow-list was narrowed to the helper boundary. The complete
   class command
   `./gradlew :test --tests kg.aidarbek.smpp.architecture.ArchitectureTest`
   in `48-message-architecture-green` was **not green**: its negative fixture
   passed, but the production rule correctly reported two uses of the legitimate
   `java.util.stream.IntStream` API omitted from the first list. The stream family
   was explicitly admitted as a collection-processing facility; no forbidden
   dependency was allowed and no negative assertion was removed.
4. The same full-class command freshly passed all **14 architecture cases** in
   `48b-message-architecture-green`. Every forbidden target remains asserted:
   PduHeaderCodec, ProtocolProfile, SessionState, RequestOptions, WriteClass,
   TcpTransportConfig, EndpointOptions, Socket, Path and Executor.
5. `51-architecture-adapter-format` separately ran `./gradlew spotlessApply`
   before final hashes. `52-architecture-adapter-verification` ran
   `./gradlew :test --tests 'kg.aidarbek.smpp.message.*Test' --tests kg.aidarbek.smpp.architecture.ArchitectureTest :simulator:test --tests kg.aidarbek.simulator.HelperContentPlansTest :javadoc solidReviewInventory`.
   Its **31 library cases** (17 helper plus 14 architecture) and **5 simulator
   adapter cases** freshly passed with zero failures or skips. The already-clean
   library Javadoc was UP-TO-DATE; it is not reported as a fresh generation.
6. `53-final-architecture-check` ran `./gradlew check :javadoc`. All **517 library
   tests** and **5 adapter tests** freshly passed with zero failures/errors/skips;
   `solidReview` covered all 224 identities. Matching formatting, review-tool
   tests and Javadoc outputs were UP-TO-DATE. `54-final-review-coverage` runs
   `./gradlew solidReview` after recording these outcomes, without changing Java.
7. The companion factory visibility refactor changed no architecture source.
   Following its separate formatter run, `56-visibility-refactor-green` reused
   the 31 unchanged helper/architecture cases FROM-CACHE and freshly passed all
   5 adapter tests. `57-final-visibility-check` passed full check with 224 current
   review identities, 5 freshly executed adapter tests and 517 matching library
   cases FROM-CACHE. `58-final-review-coverage` verifies the final report text.

The inventory lists both identities below at the same formatted whole-file
SHA-256. The isolated total is 224 identities: 198 unchanged baseline, 23 Step 15
affected types and 3 separately reviewed copied simulator prerequisite types.
The prerequisite sources/build files/review stay outside the Step 15 patch.

## Complete per-type review

```solid-review
source: src/test/java/kg/aidarbek/smpp/architecture/ArchitectureTest.java
type: kg.aidarbek.smpp.architecture.ArchitectureTest
sha256: 96fff92c2ddaecc6ebde112aee524165060efacc2d756adcae0598adc2f24f5f
responsibility: Verifies the complete library's implemented package dependency boundaries and proves the new pure message-helper rule rejects concrete forbidden edges.
consumers: JUnit runs the production-import, per-layer dependency, coordinator-port and cycle checks; ArchUnit evaluates actual imported classes and the isolated negative fixture.
S: pass | Every test describes dependency direction or nonvacuous selection for the same library architecture. Message conversion, session state, request tracking, endpoint startup and simulator reporting behavior stay in their own test suites.
O: pass | Named layer constants and a single messageBoundary factory keep the dependency policy consistent between production and negative selections. A new helper does not require editing protocol or codec rules; the package-level rule imports it automatically. Legitimate boundary changes remain explicit instead of admitting all library or JDK packages.
L: pass | This final JUnit class has no custom supertype or runtime resource ownership. Production imports exclude test classes, explicit core/helper assertions guard selection, and all ten forbidden targets must appear in the negative result. The existing package/coordinator rules and nonempty checks remain intact; all 14 cases pass with no allowEmptyShould workaround or suppressed violation.
I: pass | Each case consumes only the ArchUnit import/check/evaluate and JUnit assertion APIs it needs. The private rule factory accepts a class-selection predicate so the negative fixture shares the policy without forcing production code to expose test hooks or broad new interfaces.
D: pass | Architecture policy is expressed against imported class metadata, package predicates and explicit public representatives. The fixture's dependency edges are inspected rather than instantiated; the test creates no request window, socket, executor or endpoint. Existing rules still isolate pure policy from infrastructure and keep the coordinator dependent on frame ports.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/architecture/ArchitectureTest.java
type: kg.aidarbek.smpp.architecture.ArchitectureTest.ForbiddenMessageDependencies
sha256: 96fff92c2ddaecc6ebde112aee524165060efacc2d756adcae0598adc2f24f5f
responsibility: Provides named forbidden library-layer, network, file and executor dependency edges for one negative message-architecture probe.
consumers: ArchitectureTest imports this nested class separately and requires the shared boundary to report every one of its ten field-type dependencies.
S: pass | Each field is a minimal counterexample for the same pure-helper dependency policy. The fixture contains no protocol logic, mutable request engine, networking implementation or unrelated assertion helper.
O: pass | Another forbidden dependency can be represented as an explicit field plus its required diagnostic assertion; production helper APIs and the shared rule factory need no test-specific branches.
L: pass | The final static class has no custom supertype or operational interface. Fields remain uninitialized metadata references; no socket, executor or endpoint is allocated and no lifecycle contract is impersonated. Production import exclusion prevents this intentionally invalid graph from contaminating positive checks.
I: pass | The fixture exposes no behavioral capability or meaningless implementation methods; its sole consumer requires class-file metadata rather than a runnable fake subsystem.
D: pass | Direct concrete dependencies are deliberate negative-test inputs, not production composition. The enclosing test proves they are rejected for helpers and never instantiates them, while actual message code depends only on pure values and supplied time.
findings: none
```
