# Review: Request architecture boundary

## Scope and responsibility

This Step 9 review covers the complete updated `ArchitectureTest`, based on
commit `210359e` and the previous test hash
`d5cac089a679e9368f9cf7d43941e2d4e4724513b9880fecd35d5ff722ace1b5`.
Work ran in `/tmp/lightweight-smpp-architecture911`. Dependency probes changed
only a copied production record there; neither the implementation worktree nor
the root checkout contained these intentionally forbidden references.

The request package may depend on its own types, immutable protocol values,
and JDK language, math, time, collection and concurrency facilities. It must not
parse bytes, decide session permissions, or own sockets. Existing protocol,
profile, codec and pure session rules are unchanged. The package-cycle rule
automatically selects the new request package. A real `RequestOptions` import
anchor prevents a missing production import from hiding the new layer; no rule
allows an empty selection.

## Type review

```solid-review
source: src/test/java/kg/aidarbek/smpp/architecture/ArchitectureTest.java
type: kg.aidarbek.smpp.architecture.ArchitectureTest
sha256: fffc8b6983a19620a2517dbd837f7fb4be30ef8b17c44523f587e1e11e94cf2c
responsibility: Enforce production dependency boundaries as request ownership joins the existing protocol, profile, codec and pure session layers.
consumers: Jupiter discovers eight tests; ArchUnit inspects production imports, inward dependencies, infrastructure exclusions and package cycles.
S: pass | All cases check the architecture graph or establish its import scope. Correlation, protocol interpretation, clocks and socket behavior remain in their separately tested owners.
O: pass | Package scanning includes additional production request types automatically. The one new allow-list describes a newly implemented boundary; no command-specific implementation list or empty-selection exemption is introduced.
L: pass | The final test class preserves Object and Jupiter discovery contracts. Its zero-argument methods query a shared imported graph without mutation; eight passing cases include all seven inherited checks.
I: pass | Focused annotated cases expose only test-runner entry points. Production APIs acquire no testing interface, instrumentation callback or irrelevant capability.
D: pass | ArchUnit and Jupiter stay in the test classpath. Production identities only anchor imports. Actual request-to-codec and request-to-socket references were each rejected, while request dependencies on protocol values and JDK concurrency remain permitted.
findings: none
```

This source contains one top-level class and no member, local or anonymous type.
The formatter ran before the final hash and whole-type review.

## Actual TDD and verification

Every Gradle command below includes
`--console=plain -Dorg.gradle.jvmargs='-Xmx512m -Dsmpp.worker=architecture911'`.
The invocation-only daemon identity separates worktree watchers without changing
the project configuration or disabling caches.

1. Baseline: copied the implementation owner's current `RequestOptions`, then
   ran `./gradlew test --tests kg.aidarbek.smpp.architecture.ArchitectureTest`.
   All seven original architecture cases executed and passed. Production
   compilation was restored from cache; test compilation and execution ran
   (`/tmp/step9-architecture-01-baseline.log`). The copied record hash was
   `a334dce413632f5d3d392fed7c5fbc06b05b224144dca817eef029708f231806`.
2. Codec red: added the request rule and import anchor, then inserted a private
   static null `FieldReader` field into that isolated record. Its probe hash was
   `00faceebb6c03bf71e04ab9885d120875ca9b463f05b9a150571b13f2993a9fb`.
   `./gradlew test --tests kg.aidarbek.smpp.architecture.ArchitectureTest.requestsDependOnlyOnRequestsProtocolAndJdkConcurrency`
   compiled successfully and failed its one executed test with a dependency
   violation naming `RequestOptions.forbiddenCodec` and `FieldReader`
   (`/tmp/step9-architecture-02-codec-red.log`).
3. Codec green: restored the exact record snapshot and reran the full architecture
   class command. All eight tests executed and passed; matching production
   compilation came from cache (`/tmp/step9-architecture-03-codec-green.log`).
4. Socket red: inserted a private static null `java.net.Socket` field into the
   same record, producing hash
   `56108695d5993a6e6fe385e641d411586fd958f3ae3a8af0b030ca5b8b20253c`.
   The same focused request-rule command compiled successfully and failed one
   executed test, naming `RequestOptions.forbiddenSocket` and `java.net.Socket`
   (`/tmp/step9-architecture-04-socket-red.log`).
5. Socket green: restored the record byte for byte and repeated that focused
   command. One test executed and passed; production compilation came from cache
   (`/tmp/step9-architecture-05-socket-green.log`).
6. Formatting: `./gradlew spotlessApply` passed
   (`/tmp/step9-architecture-06-format.log`). Import ordering changed in the test;
   the record retained its original hash.
7. Final isolated verification:
   `./gradlew test --tests kg.aidarbek.smpp.architecture.ArchitectureTest spotlessCheck`
   passed (`/tmp/step9-architecture-07-final.log`). Test compilation and formatter
   checks executed. The eight passing test results were restored from a matching
   build-cache entry, not freshly executed. The prior codec-green run supplies
   fresh execution of all eight rules; formatting preserved their behavior.

These isolated checks use the Step 8 production baseline plus the real request
record. The integrated build separately checks the complete request package.

## Transient source review

The only production type copied and changed here was
`kg.aidarbek.smpp.request.RequestOptions`, at the original and two probe hashes
above. The clean record validates one request timeout. S: timeout validity is
its sole responsibility. O: it is a final value with no variable implementation
or subclass contract. L: positive bounded immutable `Duration` storage preserves
record equality, hash code and component access; invalid input fails before
construction. I: consumers receive only the timeout and its convenience factory,
without infrastructure methods. D: it depends only on immutable JDK time values
and argument-validation facilities. Its final implementation and consumers are
reviewed again in the request implementation report.

Each temporary field deliberately violates S and D by coupling a timeout value
to parsing or networking. O: neither field establishes a supported extension
point. L: the private static null references change no record component,
constructor invariant, equality, hashing or resource lifecycle. I: they expose
no public method or interface obligation. They allocate no parser or socket and
introduce no new Java type. Both violations were removed; the restored record
matches the original hash exactly.

## Integrated production graph

Root integration applied the request owner's exact 16-file manifest alongside
this architecture snapshot. `./gradlew build solidReviewInventory --console=plain`
passed (`/tmp/lightweight-smpp-step9-integration-build.log`), reusing the
configuration cache. The 383 library/architecture results, including all eight
architecture rules and 41 request cases, were restored from a matching build
cache entry. The unchanged 60 review-tool results were up to date. This is
integration of matching verified outputs, not a claim of new root test execution;
the request review records the fresh complete development run.

`solidReview`, inventory and formatter checks executed. All 129 source/type/hash
identities agree with current coverage and recomputed whole-source hashes. The
updated architecture hash remains
`fffc8b6983a19620a2517dbd837f7fb4be30ef8b17c44523f587e1e11e94cf2c`.
All three JARs contain the exact root LICENSE/NOTICE; binary and source membership
matches production output, and review tooling is absent. No dependency, Java
warning or configuration-cache setting changed. Markdown titles, fences,
footnotes and local links were verified separately. Common document edits clarify
implemented request contracts and correct stale descriptions of installed checks.
