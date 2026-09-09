# Review: Endpoint architecture boundaries

## Scope and dependency contracts

This Step 11 review covers the complete updated `ArchitectureTest`, starting
from the Step 10 snapshot with SHA-256
`7a5e7bbb55820be949bc797b5e9747bd9b33dbf45860bb73a1ec1dec22846994`.
The independent worktree `/tmp/lightweight-smpp-architecture911` contains the
final Step 9/10 production prerequisites and the endpoint owner's formatted
production snapshot. Temporary violations never changed the endpoint worktree
or the root production sources. Step 10 is integrated as commit `4cf9d0e`.

The endpoint package composes implemented layers and JDK network metadata,
concurrency, time and value facilities. The exact `IOException` and `Serial`
types support listener failures and exception metadata without allowing arbitrary
stream or file dependencies. The exact `java.nio` package allows value buffers,
not filesystem or channel subpackages. Entry points may construct TCP adapters.

The narrower coordinator rule selects `EndpointConnection` and every nested
class by binary name. It permits frame ports and policy/value collaborators,
but excludes concrete transport classes and socket/channel I/O. The only allowed
network type is the exact `SocketAddress` metadata abstraction. `EndpointOptions`
and `SmppClient` anchor actual imports, and no empty-selection failure is disabled.
All ten preceding architecture cases and package-cycle detection remain active.

## Type review

```solid-review
source: src/test/java/kg/aidarbek/smpp/architecture/ArchitectureTest.java
type: kg.aidarbek.smpp.architecture.ArchitectureTest
sha256: a210a9ff1f1e147682bd0414515dbae0afdba41ed4f487a7205fd8d89a4097f2
responsibility: Verify production dependency boundaries for endpoint composition and its port-based connection coordinator alongside all existing library layers.
consumers: Jupiter discovers twelve architecture cases; ArchUnit imports actual production classes and evaluates nonempty rules, allowed dependencies and package cycles.
S: pass | Every method establishes the production dependency graph or its imported scope. Bind outcomes, socket cleanup, request races, authentication and application notifications have separate behavioral suites.
O: pass | Package predicates cover added endpoint types automatically; the coordinator predicate includes nested classes without listing current write-observer names. Exact exception and network-metadata allowances preserve the intended extension boundaries.
L: pass | The final class preserves Object contracts and zero-argument Jupiter discovery. Twelve cases pass after formatting, including all prior rules. The shared imported graph is read-only and no success relies on an empty selection.
I: pass | Each boundary is a focused test entry point. The rules introduce no production method, interface, stub capability or dependency solely for testing.
D: pass | Concrete construction stays at composition entry points; the coordinator consumes frame ports and deterministic policy owners. Actual forbidden filesystem and concrete-adapter fields compiled and then failed their corresponding rules, demonstrating detection on real classes.
findings: none
```

The source contains one top-level class and no nested, local or anonymous type.
The recorded SHA follows the pinned formatter and complete whole-type review.

## Actual TDD and final isolated verification

All Gradle commands below include:

```sh
--console=plain -Dorg.gradle.jvmargs='-Xmx512m -Dsmpp.worker=architecture911'
```

This invocation-only daemon identity separates concurrent worktree watchers.
Build and configuration caching remain enabled; no clean, cache-disable, rerun
or dependency-refresh flag was used.

1. Baseline: with the Step 10 test and formatted endpoint production snapshot,
   `./gradlew test --tests kg.aidarbek.smpp.architecture.ArchitectureTest`
   executed all ten preceding cases and passed. Both compilation tasks executed
   (`/tmp/step11-architecture-01-baseline.log`).
2. Filesystem red: added both rules and imports, then inserted a private static
   null `java.io.File` field into the real `EndpointOptions` record. Its probe
   SHA was `ae1a74e53ef672929b70e9d0bba77e2f0c9cc0362fddd77ce10aaa94e5502b3e`.
   `./gradlew test --tests kg.aidarbek.smpp.architecture.ArchitectureTest.endpointsDependOnlyOnImplementedLayersAndJdkFacilities`
   compiled successfully and failed one executed test, explicitly naming
   `EndpointOptions.forbiddenFilesystem` and `java.io.File`
   (`/tmp/step11-architecture-02-filesystem-red.log`).
3. Filesystem green: restored the exact original record and ran the complete
   architecture class command. Twelve cases executed and passed; matching
   production compilation was restored from cache
   (`/tmp/step11-architecture-03-filesystem-green.log`).
4. Adapter red: inserted a private static null `TcpTransport` field into the real
   `EndpointConnection`. The whole-file probe SHA was
   `7abeffeabcb520af069718d7fcae1e063c6f0213a822f471dde1558bb0e5b355`.
   `./gradlew test --tests kg.aidarbek.smpp.architecture.ArchitectureTest.connectionCoordinatorUsesPortsAndNetworkMetadata`
   compiled successfully and failed one executed case, naming
   `EndpointConnection.forbiddenTransport` and the concrete adapter
   (`/tmp/step11-architecture-04-adapter-red.log`).
5. Adapter green: restored the exact original coordinator and repeated that
   focused command. One case executed and passed; matching production compilation
   came from cache (`/tmp/step11-architecture-05-adapter-green.log`).
6. `./gradlew spotlessApply` passed separately
   (`/tmp/step11-architecture-06-format.log`). Both probe targets retained their
   pristine hashes below; only the architecture test required final layout changes.
7. `./gradlew test --tests kg.aidarbek.smpp.architecture.ArchitectureTest spotlessCheck`
   passed with twelve freshly executed cases and no failures, errors or skips.
   Test compilation and formatting checks executed; production compilation was
   up-to-date. The configuration cache was reused
   (`/tmp/step11-architecture-07-final.log`).

## Whole-type review of transient probe targets

The pristine and restored `EndpointOptions` SHA-256 is
`114f98080bcdabb6917c42c2c251170126a902a84fb793fc7edbb7cf993edff0`.
The pristine and restored `EndpointConnection` whole-file SHA-256 is
`58390aaf4915aefd5832cfc34a5e103da9ece761f09cf74bc73e43fb65056962`.
Its type identities are `kg.aidarbek.smpp.endpoint.EndpointConnection`,
`kg.aidarbek.smpp.endpoint.EndpointConnection.ReplyWrite` and
`kg.aidarbek.smpp.endpoint.EndpointConnection.RequestWrite`. The probe hash above
covers all three; no type was added, removed or renamed by either experiment.

`EndpointOptions`: S — stores endpoint capacities, durations and codec limits
under one configuration invariant. O — explicit values vary those policies
without subclassing or changing connection logic. L — final immutable components
preserve record access, equality and hashing; invalid capacities and unsafe
monotonic durations fail before construction. I — consumers receive only resource
and deadline policy, without lifecycle or application callbacks. D — its clean
dependencies are immutable codec limits and JDK values. The temporary null file
field intentionally violated S/D, offered no supported extension under O and
changed no record components or public substitution/interface contract under L/I.
Restoration removed it byte for byte; no file was opened.

`EndpointConnection`: S — coordinates one protocol lifecycle, delegating frame
I/O, parsing, deterministic state, request settlement and application execution
to their owners. O — frame transport and authentication are real variation points;
profile/state decisions reuse the existing policies rather than a second table.
L — its FrameListener callbacks process internal work, preserve owned frame and
close contracts, and publish application results through bounded notifications;
AutoCloseable closure is idempotent. The real/fake endpoint tests cover readiness,
write certainty, correlation, cancellation, deadlines and graceful shutdown.
I — callers use the focused BoundSession/ConnectionAttempt facades; its internal
listener and observer capabilities match the consumed SPI. D — the coordinator
uses frame ports and SocketAddress metadata, with concrete construction outside.
The null adapter field deliberately violated S/D and provided no supported O
extension; it changed neither callback/substitution behavior under L nor exposed
capabilities under I. Exact restoration removed the dependency without creating
a socket or adapter.

`EndpointConnection.ReplyWrite`: S — bridges one reply's local write result to
reply-drain and lifecycle progression. O — a private continuation varies the
post-write step without duplicating physical I/O. L — beforeWrite refuses output
after closure, terminal callbacks obey the WriteObserver contract, and internal
continuations do not execute application work on transport progress. I — it
implements only the required guard/written/failed trio. D — it consumes the SPI
and its owning coordinator, with no concrete I/O. Its source text and contract
were unchanged by the enclosing class's probe, though the whole-file hash changed.

`EndpointConnection.RequestWrite`: S — bridges a locally originated request to
its write guard and failure owner. O — the existing RequestHandle/RequestWindow
contract supports each implemented lifecycle/control operation without another
request engine. L — beginWrite marks conservative transmission knowledge before
output, a false guard forbids bytes, failed output settles through the single
window owner, and a local written event makes no peer-acceptance claim. I — only
write-observer methods are exposed; the empty written callback is meaningful
because response completion belongs to the request window. D — it depends on
request abstractions and SPI, never a socket adapter. Its text and behavior were
unchanged by the outer probe, while sharing the recorded whole-file hashes.

The endpoint owner's complete current production/test/example reviews cover the
final implementation and affected consumers. These temporary experiments prove
architecture-rule detection; they do not substitute for behavioral SOLID review.

## Root integration and build wiring

After Step 10 commit `4cf9d0e`, the root applied the exact 28-file endpoint patch
with SHA-256
`0e149fa47e16e1a9f6d867ab6bceedda1e3c639bed379ca9a60aaf64b4cdcb65`.
Every owned file matched the worker's final manifest. The final architecture
snapshot above was copied unchanged. `EndpointConnection` and its two observer
types still have the pristine probe-target hash. `EndpointOptions` later changed
only Javadoc descriptions of closing connection reservations and notification
capacity, plus comment spacing; its final SHA is
`20feca3f131e4ae8f3e3367c48c205d770315b39acf4ffb2d31d114198e5e942`.
Its complete final review appears in the endpoint owner's report. The root
compared this change against the pristine snapshot: constructor conditions,
components, factories and dependencies are unchanged.

The separately reviewed `build.gradle` patch adds an `examples` source set,
puts its outputs on the test classpath, and registers the two JavaExec helpers.
Existing Java 21 compilation, UTF-8, strict warnings and pinned formatting also
apply to examples. Their classes are tested without entering the production
binary, source or Javadoc archives. JavaExec uses the explicit toolchain and
source-set runtime classpath and declares no reusable execution output. These
Groovy configuration additions introduce no Java type and do not change the
existing review argument-provider type. All new example/test types have their
own complete reviewed source records.

`./gradlew build solidReviewInventory dependencies --configuration runtimeClasspath --console=plain`
passed in `/tmp/lightweight-smpp-step11-integration-build.log`. All **498
library/architecture cases across 54 classes executed freshly**, including the
77 endpoint cases and twelve architecture checks. There were no failures, errors
or skips. The **60 review-tool cases across four classes were up-to-date** and
were not represented as freshly executed. Production/example compilation and
Javadoc came from matching caches; combined test compilation, tests, formatting
checks, inventory and coverage executed. The changed build configuration stored
a new configuration-cache entry successfully. Runtime classpath resolution
reported **No dependencies**.

Inventory and coverage agree on **199 current Java type identities**. An
independent SHA-256 recomputation matched every current source record. The three
archives contain the exact Apache license and project notice, production
class/source membership only, and no example, test/fixture, transport experiment
or review-tool classes. All 37 Markdown documents and 300 local links passed
content/link checks before this final evidence append; Git whitespace checks
also passed. Documentation review corrected remaining future-tense statements
and preserves the distinct scopes of manual controls, message services, scheduled
keepalives, simulators and independent interoperability.

## Fresh command-line example verification

The bounded runner `/tmp/lightweight-smpp-step11-example-smoke.py` used the
combined root compilation and launched a separate server JVM:

```sh
java -cp build/classes/java/main:build/classes/java/examples kg.aidarbek.examples.ExampleServer 0 20
```

After that server reported ephemeral port 38175, it ran
`./gradlew runExampleClient --args=38175 --console=plain` twice, sequentially.
Both client processes freshly completed bind, enquiry, unbind and their explicit
cleanup checks with exit zero. Compilation remained up-to-date; the first
invocation stored its configuration-cache entry and the second reused it while
**executing JavaExec again**. The server exited normally at its finite lifetime
with successful graceful cleanup. The runner then executed
`./gradlew runExampleServer --args='0 1' --console=plain` independently; that
helper also started, completed bounded cleanup and exited zero. No simultaneous
Gradle invocations in one checkout were needed.

All four recorded process results have exit code zero in
`/tmp/lightweight-smpp-step11-example-smoke.json`. Logs are
`/tmp/lightweight-smpp-step11-example-server.log`,
`/tmp/lightweight-smpp-step11-example-client-1.log`,
`/tmp/lightweight-smpp-step11-example-client-2.log` and
`/tmp/lightweight-smpp-step11-example-server-task.log`. These are fresh functional
command-line checks, not cached measurements or a simulator capacity claim.
The runner bounds startup, each process and teardown, and reaps every child.
