# Review: Add PDU framing

## Scope

- Starting revision: `bde0e1e` (`Set up code checks`).
- Added three production types and four permanent test types; no additional nested,
  local, anonymous, or helper types occur in their final sources.
- Contracts: [header/framing guide](../FRAMING.md), [API ownership](../API.md), and
  `FRAME-01`–`FRAME-08` in [the test plan](../TEST_PLAN.md).
- Final source hashes below were captured after Spotless formatting. Every whole
  type and its consumers were reviewed, with independent subagent review of all
  production and behavior-test types. No remaining SOLID findings were identified.
- No runtime dependencies, command-body codecs, session implementation, or transport
  were added. Raw header bits do not imply legal command/session semantics.

## Actual TDD and final verification

Primary header expectations come from SMPP 3.4 §§3.1–3.2/5.1 and SMPP 5.0
§§3.1–3.2/4.7.4/4.7.24; source links are in the framing guide. The enquiry fixture
was specified before an encoder existed. Initial compilable API declarations
allowed relevant behavioral failures to execute; no unconditional failure test was used.

Commands below ran in the project root with `--console=plain`. Each row's red
was observed before its corresponding implementation change. Each subsequent
green reran the stated selection and preserved the assertions. Boundary variants
for one contract use parameterized cases. Logs were captured in
`/tmp/lightweight-smpp-step3-evidence/01-…08-*.log` during implementation.

| Cycle | Test command | Observed red | Observed green |
| --- | --- | --- | --- |
| 1, independent encode | `./gradlew test --tests '*PduHeaderCodecTest'` | Missing write: expected byte 16 at index 3, actual 0; 1 failure. | 1 test passed. |
| 2, unsigned decode | Same | Decoder returned null instead of independently specified unsigned header; 1 of 2 failed. | 2 tests passed. |
| 3, header invariants | `./gradlew test --tests '*PduHeaderTest' --tests '*PduHeaderCodecTest'` | Invalid unsigned/minimum lengths accepted; 4 of 7 failed. | 7 tests passed. |
| 4, atomic network-order buffers | Same | Wrong endian bytes, partial writes, and cursor movement on truncated/invalid header; 6 of 16 failed. | 16 tests passed. |
| 5, one-frame pull | `./gradlew test --tests '*PduFramerTest'` | No complete frame returned; 1 failure. | 1 test passed, returning two successive frames separately. |
| 6, fragmentation/coalescing | Same | Partial input underflow/index errors; 21 of 22 failed. | 22 tests passed, including every split of the 20-byte fixture. |
| 7, early length bounds | Same | Invalid lengths accepted or wrong exceptions, unusable maximum accepted; 9 of 31 failed. | 31 tests passed, rejecting exactly at the fourth length octet. |
| 8, truncated EOS/lifecycle | Same | End-of-input failed to detect partial input or terminate; 6 of 40 failed. | 40 tests passed. Ownership/direct-buffer/null cases already passed and remain characterization evidence. |

After the independent review, read-only encode atomicity and empty-stream EOS
characterization tests were added; they passed existing behavior without an
invented red run. Formatting and documentation clarified buffer ownership and
error contracts without changing protocol expectations. No behavior-preserving
code restructure beyond the already-tested translation/assembly increments was needed.

Final `./gradlew build --console=plain` passed with 63 executed tests: 5 header
value cases, 12 header-codec cases, 41 framer cases, and 5 architecture checks.
Strict compilation, formatting, Javadoc, sources/Javadoc archives, and configuration
cache reuse passed. Historical Step 2 `NO-SOURCE` runs do not describe this suite.
A shared-daemon VFS warning during early isolated-checkout switching was addressed
by giving each agent distinct invocation-only JVM options; later root builds
completed without that warning. No cache or filesystem-watching setting was disabled.

## Production and behavior-test review inventory

Each fence is a per-type review tied to the entire final source file. The
machine-readable format is prepared for Step 4; this step manually reconciles
all seven actual type identities and file hashes. Temporary forbidden fields in
isolated production copies were removed and are discussed in the architecture
appendix; no additional project-owned type was introduced for those probes.

## Type: kg.aidarbek.smpp.protocol.PduHeader

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/PduHeader.java
type: kg.aidarbek.smpp.protocol.PduHeader
sha256: 4a5835e2f2e116d9f7dc2425534739064df08e24d3d38a1e7e52c23fe56101ac
responsibility: Represent immutable raw SMPP header values and enforce their structural unsigned ranges.
consumers: PduHeaderCodec consumes and creates it; future command/session validation interprets raw fields; framing reads its header-size constant.
S: pass | Range validation and scalar record accessors share the wire-header value responsibility; no parsing, transport, or session state is owned.
O: pass | Unknown command/status and sequence bits remain representable without editing the value when a command is added. No user-defined inheritance extension is required.
L: pass | The final record preserves Record/Object value equality, hash consistency, and immutable primitive ownership. HeaderTest exercises the complete unsigned domain boundaries; session-only restrictions are deliberately absent.
I: pass | The four scalar accessors and LENGTH constant are the header consumers' complete needs. No callbacks, sockets, or unrelated command methods are imposed.
D: pass | Only Java language facilities are used. The value does not depend on codecs, profiles, infrastructure, or test tools; the nonempty protocol ArchUnit rule enforces this direction.
findings: none
```

## Type: kg.aidarbek.smpp.codec.PduHeaderCodec

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/PduHeaderCodec.java
type: kg.aidarbek.smpp.codec.PduHeaderCodec
sha256: 2770d47722a6c4c98240aba614538ed153c693478a4db2b1cedb349493405bd6
responsibility: Translate the fixed header to and from network-order bytes while honoring ByteBuffer cursor and ownership contracts.
consumers: Protocol consumers call static encode/decode; tests provide independent byte fixtures; it consumes PduHeader and caller-owned JDK buffers.
S: pass | Both directions translate the same fixed header. Length/range policy is delegated to the header value; no command-body parsing or stream/session lifecycle is mixed in.
O: pass | Its fixed format requires no speculative extension point. Unknown wire values round-trip through the header; new body codecs do not require changes here.
L: pass | There is no custom supertype; Object contracts are unchanged. Tests establish exact cursor advancement, limit/order preservation, atomic error behavior, unsigned values, and direct/read-only buffer contracts. Stateless methods retain no buffers.
I: pass | Separate encode and decode operations serve their corresponding consumers, with no required transport, application, or command implementation methods.
D: pass | The codec points to protocol values and JDK buffer facilities only. It never constructs networking or application infrastructure; architecture rules check the actual dependencies.
findings: none
```

## Type: kg.aidarbek.smpp.codec.PduFramer

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/PduFramer.java
type: kg.aidarbek.smpp.codec.PduFramer
sha256: 7744ca88fa19768a94647e40ddb9705b0322ff8154d9d18ab047fee72a70bdaf
responsibility: Assemble a single bounded SMPP frame incrementally from caller-supplied bytes.
consumers: A future stream adapter supplies ByteBuffers and drains one Optional frame per call; no adapter, socket, executor, or callback is constructed.
S: pass | Length-prefix validation, partial storage, emission, and end-of-input handling belong to frame assembly. Command semantics and session outcomes are absent.
O: pass | Any structurally sized command frame is accepted without command registration. Transport implementations supply bytes through the same narrow JDK boundary; no artificial subclass extension is needed.
L: pass | The final class has no custom supertype. Read consumes through one frame or available input; malformed length and EOS terminate consistently. Tests verify every fixture split, prior emissions before error, exclusive returned-array ownership, source reuse, end idempotence, and explicit thread confinement.
I: pass | The constructor, read, and endOfInput form the actual stream-assembly capability. Callers need no command handlers, socket callbacks, or unrelated lifecycle methods.
D: pass | Only the protocol header-size constant and JDK byte/collection facilities are used. The framer accepts data from callers without choosing transport or scheduling infrastructure.
findings: none
```

## Type: kg.aidarbek.smpp.protocol.PduHeaderTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/protocol/PduHeaderTest.java
type: kg.aidarbek.smpp.protocol.PduHeaderTest
sha256: efa25e4c1d2aca75e996f463b57fda9fee3329b6d1c6a0176a9b2127479ad554
responsibility: Verify the raw header's unsigned and minimum-length invariants independently of session rules.
consumers: Jupiter parameterized discovery invokes boundary cases against the production record.
S: pass | All cases concern scalar header-value invariants and preservation; no wire codec, fixture I/O, or unrelated setup is owned.
O: pass | Boundary data extend through parameter inputs. New command IDs need no specialized header test hierarchy because raw unknown values are already covered.
L: pass | No custom supertype or altered Object contracts; final package-private Jupiter discovery is verified by five executed cases. Tests mutate no shared state and impose only the documented structural constraints.
I: pass | Jupiter consumes only focused annotated methods; production consumers do not implement or depend on test methods.
D: pass | Tests depend directly on the value under test and test-only Jupiter. They do not add dependencies to the runtime artifact.
findings: none
```

## Type: kg.aidarbek.smpp.codec.PduHeaderCodecTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/PduHeaderCodecTest.java
type: kg.aidarbek.smpp.codec.PduHeaderCodecTest
sha256: 9a616917a09758c81317aa90a8056b24177535e6a4d18a02c6ef9fc1f1d15691
responsibility: Verify header translation and atomic ByteBuffer boundary behavior from independent wire fixtures.
consumers: Jupiter invokes encode/decode cases; JDK buffers cover heap/direct/read-only/slice variants; only the header record and codec are exercised.
S: pass | All methods test the fixed-header translation contract. Hex fixture creation is local to cases; no networking or session semantics is mixed in.
O: pass | Buffer variants use parameterized cases; further translation fixtures extend test data rather than production abstractions.
L: pass | The final class preserves Object behavior and meets Jupiter method discovery. Independent expected bytes prevent paired encoder/decoder errors from hiding; tests assert unsigned values, nonmutating failures, cursor and byte-order behavior.
I: pass | Only test methods are exposed to Jupiter. No custom interface or broad fixture framework makes other tests implement irrelevant methods.
D: pass | Direct references to the codec/value under test and JDK/Jupiter are appropriate at this test boundary; no production type depends back on it.
findings: none
```

## Type: kg.aidarbek.smpp.codec.PduFramerTest

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/PduFramerTest.java
type: kg.aidarbek.smpp.codec.PduFramerTest
sha256: f695b2bb0e655a7cd17ff037432b5fe27313537ca67b304a6a02ed793bcd5ff0
responsibility: Verify incremental bounded framing, ownership, malformed-input outcomes, and terminal lifecycle.
consumers: Jupiter parameterizes split/size/EOS boundaries; fixtures are independently specified private byte arrays consumed without mutation.
S: pass | Forty-one cases share one framer contract. Known body octets are opaque fixture data; no command parsing, socket, clock, or application ownership is added.
O: pass | Split and malformed-length cases extend through test parameters. The single-frame pull contract is exercised without subclass or callback infrastructure.
L: pass | Final package-private test discovery is verified; Object contracts are unchanged. Fixture arrays remain unmodified, ownership mutation uses clones, and tests need no shared mutable lifecycle or timing assumptions.
I: pass | Jupiter consumes the annotated cases only; helper hierarchies, mocks with unrelated methods, and production interfaces are absent.
D: pass | Tests call the real framer and JDK buffers, including read-only/direct inputs. No infrastructure dependency is introduced into the library.
findings: none
```

## Independent architecture review and probes


This evidence was produced in the detached worktree
`/tmp/lightweight-smpp-archunit-step3`, starting at
`bde0e1e6e470897dd15df008d7427121e18f116d`. Only
`src/test/java/kg/aidarbek/smpp/architecture/ArchitectureTest.java` is a new
Java file for integration. The main checkout was read but never modified by this
reviewer. No commit was created.

The production files were copied from the active Step 3 checkout into the
isolated worktree. Temporary dependency fields were injected into those copies
and then removed; no temporary helper, nested, local, or anonymous type was
introduced. The test has five Jupiter methods and uses ArchUnit core directly.

### Scope and limitations

The importer selects production classes below `kg.aidarbek.smpp` and excludes
test classes. The coverage test verifies that it includes `PduHeader`,
`PduHeaderCodec`, and `PduFramer`, and excludes `ArchitectureTest`. The protocol
and codec rules therefore each have actual selected classes; ArchUnit's
empty-selection failure remains enabled.

- Protocol types may depend only on their protocol package and JDK language,
  collection, math, and time facilities.
- Codecs may depend on codec, protocol, and profile types and those JDK facilities
  plus NIO buffers/character conversion. This boundary was agreed with the
  profile implementation agent: typed field codecs may consume profiles, while
  profiles must not consume codecs.
- Protocol, profile, and codec types cannot depend on `java.net`, `javax.net`,
  NIO channels/files, or `java.util.concurrent`. The allowlists additionally
  exclude Java I/O, SQL, third-party framework, transport, and simulator types.
- The production package graph is checked for cycles at the first package level
  below `kg.aidarbek.smpp`; the final Step 3 selection contains protocol and codec.

There are no profile classes at this step. Allowing codecs to consume that
planned package does not claim profile-rule coverage. Step 5 must add a nonempty
profile-specific direction rule when those classes arrive. Session and transport
rules likewise remain later work. These structural checks do not prove all SOLID
behavior, immutability, or runtime ownership.

### Type review

```solid-review
source: src/test/java/kg/aidarbek/smpp/architecture/ArchitectureTest.java
type: kg.aidarbek.smpp.architecture.ArchitectureTest
sha256: 0353f7416b12f196e6b84e2d01ad8096243a9109fb3014c8855c0540e5e2c0bf
responsibility: Enforce and demonstrate the production package dependency boundaries introduced with the SMPP header and framer.
consumers: JUnit Jupiter discovers its five methods; ArchUnit imports production bytecode and evaluates dependency and cycle rules; production code never consumes this test type.
S: pass | Import coverage and all four architecture rules serve the single project architecture requirement. The class owns no SMPP parsing, session policy, mutable fixture buffers, or network lifecycle.
O: pass | New production classes are automatically included by package import and matching; the three explicit coverage anchors establish intended nonempty scope. A deliberate package-boundary change belongs in this test. New command values and codecs within the declared boundaries require no unrelated test infrastructure changes.
L: pass | This final test class has no custom supertype or interface; inherited Object contracts are unchanged. Its package-private zero-argument test methods are valid Jupiter entry points, as the five executed tests prove. ArchUnit state is imported once and only queried, with no owned resources or shared mutable test fixtures.
I: pass | Jupiter needs only the five annotated test methods. The class creates no production interface and forces no protocol or codec consumer to implement architecture or test behavior. No unnecessary test-helper abstraction is introduced.
D: pass | The test depends directly on the rule engine it verifies and on three production class identities solely as coverage anchors. All dependency direction checks operate on production bytecode with test classes excluded; neither production APIs nor the library runtime artifact gain an ArchUnit or Jupiter dependency.
findings: none
```

### Actual TDD commands and results

Every command below ran in the isolated worktree with
`--console=plain -Dorg.gradle.jvmargs='-Xmx512m -Dsmpp.worker=framing-review'`.
The distinct daemon setting was invocation-only; no Gradle configuration file
changed. Each red below was a freshly executed behavioral assertion failure.
Compilation cache reuse is identified separately from execution of tests.

1. `./gradlew test --tests 'kg.aidarbek.smpp.architecture.ArchitectureTest.importsProductionTypesWithoutTests' --console=plain -Dorg.gradle.jvmargs='-Xmx512m -Dsmpp.worker=framing-review'`
   failed because `ArchitectureTest` itself was imported: `expected: <false> but
   was: <true>`. Adding `ImportOption.Predefined.DO_NOT_INCLUDE_TESTS` and rerunning
   the identical command passed one executed test. Before this behavioral run,
   one initial setup attempt failed compilation because the production copy
   destination did not exist; that setup failure is not red evidence.
2. `./gradlew test --tests 'kg.aidarbek.smpp.architecture.ArchitectureTest.protocolDependsOnlyOnProtocolAndJdkValues' --console=plain -Dorg.gradle.jvmargs='-Xmx512m -Dsmpp.worker=framing-review'`
   failed with a field-dependency violation for `PduHeader.FORBIDDEN_DEPENDENCY`
   of type `java.net.Socket`. Removing only that injected field and running
   `./gradlew test --tests 'kg.aidarbek.smpp.architecture.ArchitectureTest' --console=plain -Dorg.gradle.jvmargs='-Xmx512m -Dsmpp.worker=framing-review'`
   passed both tests; the test task executed, while corrected production
   compilation was restored from cache.
3. `./gradlew test --tests 'kg.aidarbek.smpp.architecture.ArchitectureTest.codecsDependOnlyOnCodecsProtocolProfilesAndJdkFacilities' --console=plain -Dorg.gradle.jvmargs='-Xmx512m -Dsmpp.worker=framing-review'`
   failed for `PduHeaderCodec.FORBIDDEN_DEPENDENCY` of type
   `java.sql.Connection`. Removing only that field and running the full
   `ArchitectureTest` command above passed all three tests, with the test task
   executed and production compilation restored from cache.
4. `./gradlew test --tests 'kg.aidarbek.smpp.architecture.ArchitectureTest.protocolProfilesAndCodecsDoNotDependOnInfrastructurePackages' --console=plain -Dorg.gradle.jvmargs='-Xmx512m -Dsmpp.worker=framing-review'`
   failed for `PduHeaderCodec.FORBIDDEN_DEPENDENCY` of type
   `java.util.concurrent.Executor`. Removing that field and running the full
   `ArchitectureTest` command passed four tests. This demonstrates that the
   infrastructure prohibition catches dependencies otherwise covered by the
   broader `java.util` allowlist. Tests executed; production compilation was
   restored from cache.
5. `./gradlew test --tests 'kg.aidarbek.smpp.architecture.ArchitectureTest.libraryPackagesAreFreeOfCycles' --console=plain -Dorg.gradle.jvmargs='-Xmx512m -Dsmpp.worker=framing-review'`
   failed with `Slice codec -> Slice protocol -> Slice codec` after a
   `PduHeaderCodec`-typed field was inserted into `PduHeader`. The existing real
   codec references to `PduHeader` supplied the opposite dependency. Removing
   only the injected field and running the full `ArchitectureTest` command
   passed all five tests; tests executed and compilation was restored from cache.

The unchanged assertions detected each injected defect. No disabled rule,
empty-selection override, unconditional `fail()`, or new artificial fixture type
was used.

### Temporary source identities

All temporary fields were `private static final` fields initialized to `null`;
no socket, executor, connection, or codec instance was constructed.

| Snapshot | SHA-256 |
| --- | --- |
| Initial importer including tests | `4e49ddac17baf016777bc8a013989f2768a2bcee195ba2268f5b516d4d4e3b1e` |
| PduHeader with Socket field | `5bf5cb5d6d29bfee90e1232a6d0d7ad02380c7b93ccc9ccbdd13ae16328f5110` |
| PduHeaderCodec with Connection field | `6dd7b2f951c7498f904e29774e1e030a4301639c403d65e6f011a675f246bf74` |
| PduHeaderCodec with Executor field | `90fb9b31af94843fbd64ad915313f0c9d21ac5cdd620be21f1278a8d3fcecf0a` |
| PduHeader with cyclic codec field | `9959a3820cbd78a0929f248e7c22342b63692e2df43e89d2d1749c7d757c518b` |

The temporary production snapshots deliberately fail dependency inversion: they
point stable protocol/codec code toward unrelated infrastructure or create the
reverse protocol-to-codec edge. They also introduce an unrelated dependency
outside each type's single responsibility. They do not create an extension point
or a required variation, and the correction removes the coupling. Their static
null fields change neither record instance equality nor public method signatures,
accepted inputs, ordering, ownership, or the inherited Object contracts. They
add no interface obligations. All fields were removed after their red run. The
complete corrected production types receive their own current-source reviews in
the root Step 3 report; provisional codec snapshots are not release evidence.

### Final verification and reconciliation

- After all five cycles, the latest root production files were copied into the
  isolated worktree and the framer was added to the import coverage anchors.
- `./gradlew spotlessApply test --tests 'kg.aidarbek.smpp.architecture.ArchitectureTest' --console=plain -Dorg.gradle.jvmargs='-Xmx512m -Dsmpp.worker=framing-review'`
  passed, applying the pinned formatter. The architecture tests executed.
- `./gradlew check --console=plain -Dorg.gradle.jvmargs='-Xmx512m -Dsmpp.worker=framing-review'`
  then passed with compilation of the formatted test source, formatting checks,
  and five executed tests: zero failures, errors, or skipped cases. This isolated
  check contains only architecture tests; the root checkout owns the full SMPP
  behavior suite and final integrated verification.
- The full final Java source was inspected: one new top-level final class,
  `ArchitectureTest`; no member, local, or anonymous types. Package constants,
  imported `JavaClasses`, test methods, and method calls add no project-owned
  types. Its final formatted source hash appears in the review block above.
