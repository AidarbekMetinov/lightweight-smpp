# Review: Transport architecture boundaries

## Scope and responsibility

This Step 10 review covers the complete updated `ArchitectureTest`. It follows
the Step 9 test snapshot with hash
`fffc8b6983a19620a2517dbd837f7fb4be30ef8b17c44523f587e1e11e94cf2c`.
The independent worktree `/tmp/lightweight-smpp-architecture911` contains commit
`210359e`, that Step 9 test and request record, and two real Step 10 value types.
Temporary violations never changed the transport implementation worktree.

`spi` owns narrow frame transport contracts and may depend on its own types and
JDK language, time and concurrency/value facilities. The exact `java.io.Serial`
annotation is allowed for exception metadata; streams and files are not allowed
by that exception. `transport` may use those ports, codecs, protocol values and
JDK networking/I/O/concurrency facilities. It cannot depend on request tracking,
session decisions or endpoint composition. Existing layer checks and package
cycle detection remain active. `WriteClass` and `TcpTransportConfig` anchor real
imports; no rule permits an empty selection.

## Type review

```solid-review
source: src/test/java/kg/aidarbek/smpp/architecture/ArchitectureTest.java
type: kg.aidarbek.smpp.architecture.ArchitectureTest
sha256: 7a5e7bbb55820be949bc797b5e9747bd9b33dbf45860bb73a1ec1dec22846994
responsibility: Verify production dependency boundaries for separate frame transport ports and their concrete TCP adapters alongside existing value, codec, policy and request layers.
consumers: Jupiter discovers ten architecture tests; ArchUnit checks nonempty package selections, production imports, allowed dependencies and package cycles.
S: pass | The class checks only the architecture graph and its imported scope. Socket behavior, framing, admission, response ownership and endpoint lifecycle have their own behavioral tests.
O: pass | Package rules automatically cover added port and transport types without enumerating concrete implementations. The exact Serial annotation exception accommodates Throwable metadata without admitting stream or file dependencies into ports.
L: pass | The final test preserves Object behavior and Jupiter zero-argument discovery. Ten discovered tests pass after formatting, including every previous rule, and shared imported classes remain read-only.
I: pass | Each concern is an independent test entry point. There is no test-specific production interface, optional stub method or required application callback.
D: pass | Test-only ArchUnit predicates describe source dependency direction. Concrete adapters depend on ports; ports cannot import codecs or adapters. Actual port-to-codec and transport-to-session fields produced test failures, establishing detection beyond a passing empty graph.
findings: none
```

The whole source contains one top-level class and no additional named, local or
anonymous Java type. Formatting preceded the source hash and final review.

## Actual TDD and verification

All Gradle commands include
`--console=plain -Dorg.gradle.jvmargs='-Xmx512m -Dsmpp.worker=architecture911'`.
This invocation-only daemon identity prevents cross-worktree watcher interference;
normal build and configuration caching stay enabled.

1. Baseline: copied the transport owner's current `WriteClass` and
   `TcpTransportConfig`, then ran
   `./gradlew test --tests kg.aidarbek.smpp.architecture.ArchitectureTest`.
   All eight preceding tests executed and passed
   (`/tmp/step10-architecture-01-baseline.log`). The copied hashes were
   `8849a60bf2db928a061c7950e0011b721d0782910a85d804c26ab339841b420b`
   and `d557fd17a8d83a2ba5e706176d215d08754723ebbad6e9ec8110566eacb7c1cc`,
   respectively.
2. Port red: added the two new boundary rules and import anchors, then inserted
   a private static null `FieldReader` reference in `WriteClass`. The probe hash
   was `f7aa3ccdef4693a1513ff8cd72ce6bceba49c4ed2f6eb2664f727364c6027dc9`.
   `./gradlew test --tests kg.aidarbek.smpp.architecture.ArchitectureTest.transportPortsDependOnlyOnPortsAndJdkContracts`
   compiled successfully and failed one executed test with a violation naming
   `WriteClass.forbiddenCodec` and `FieldReader`
   (`/tmp/step10-architecture-02-spi-red.log`).
3. Port green: restored the exact enum snapshot and reran the full architecture
   class command. Ten tests executed and passed; matching production compilation
   came from cache (`/tmp/step10-architecture-03-spi-green.log`).
4. Adapter red: inserted a private static null `SessionState` reference into
   `TcpTransportConfig`, producing hash
   `ada460b2d5deeff8de963f7e7fb87350ead89a3a1de15412833033bdda9d6edc`.
   `./gradlew test --tests kg.aidarbek.smpp.architecture.ArchitectureTest.transportsDependOnlyOnPortsCodecsProtocolAndJdkInfrastructure`
   compiled successfully and failed one executed test with a violation naming
   `TcpTransportConfig.forbiddenSession` and `SessionState`
   (`/tmp/step10-architecture-04-transport-red.log`).
5. Adapter green: restored the exact record snapshot and repeated that focused
   command. One test executed and passed, with matching production compilation
   restored from cache (`/tmp/step10-architecture-05-transport-green.log`).
6. Formatting: `./gradlew spotlessApply` passed
   (`/tmp/step10-architecture-06-format.log`). The restored enum retained its
   original hash. Formatting the record changed its hash to
   `58c0c1fc6735f6d6e086341c6fd5de55a15144936f85ea258ea1b3371f906e92`;
   its constructor conditions and defaults are unchanged.
7. Final isolated verification:
   `./gradlew test --tests kg.aidarbek.smpp.architecture.ArchitectureTest spotlessCheck`
   passed (`/tmp/step10-architecture-07-final.log`). Both compilation tasks,
   formatter checks and all ten architecture cases executed freshly. There were
   no failing or skipped tests; the configuration cache was reused.

These isolated results establish detection against actual layer anchors. The
integrated build separately verifies the complete production transport and port
packages, including all exception and callback contracts.

## Transient source review

`WriteClass` has one responsibility: name the two separately bounded write
classes. S: it stores scheduling identities only. O: a final enum does not promise
user-defined scheduling strategies. L: constants preserve Enum identity, names
and ordering. I: consumers use those identities without methods unrelated to
classification. D: the clean type depends only on JDK enum facilities. The
temporary null codec field intentionally violated S/D, established no supported
extension point under O, and changed no enum substitution or public interface
contract under L/I. It was removed byte for byte.

`TcpTransportConfig` stores frame, queued/in-flight count/byte and send-buffer
bounds. S: the one configuration invariant owns all constructor checks. O: values
are supplied explicitly; the final record does not claim subclass extension.
L: immutable primitive components preserve record equality, hashing and access,
and invalid bounds fail before construction. I: consumers need only these
transport limits and the default factory. D: the clean record has no variable
infrastructure dependency. The session field deliberately violated S/D by
coupling configuration to protocol state. It added no supported extension under
O and changed no record components, equality or public capability under L/I.
Removal restored the original snapshot; subsequent formatting changed no logic.

No probe introduced another Java type or allocated a parser, session or socket.
All original, probe and final formatted hashes are recorded above. The transport
owner's report covers the final production values and their actual consumers.

## Root integration verification

The transport patch was integrated after Step 9 commit `64675b7`. Its manifest
contains 25 owned files, including 35 new Java type identities, and every file
matched its reviewed SHA-256. The patch SHA-256 is
`3d6360c5df0b58089ff9a50740c2c68d251aa60e6d8be3249e45f5cf0477e67c`.
The final architecture snapshot above was copied unchanged.

`./gradlew build solidReviewInventory --console=plain` passed in
`/tmp/lightweight-smpp-step10-integration-build.log`, reusing the configuration
cache. The **419 library/architecture cases** in 46 classes were restored
from the matching cache; the **60 review-tool cases** in four classes were
up-to-date. No result is represented as fresh root test execution. The transport
owner's fresh full run is recorded in its review. There were no failing, errored
or skipped cases. Review inventory and coverage agree on **164 current types**,
and independently recomputed source hashes agree with all current records.

All binary, source and Javadoc archives contain the exact Apache `LICENSE` and
project `NOTICE`. Archive membership contains production code only and excludes
transport experiments, fixtures and review tooling. No runtime dependency or
build configuration changed. Markdown content/local-link and Git whitespace
checks passed. The root and independent request reviewer findings about physical
close completion, rejected child ownership and failed cleanup are fixed with
the actual red/green regressions recorded in the transport review.
