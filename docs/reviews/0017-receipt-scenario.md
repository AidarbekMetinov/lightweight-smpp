# Review: finite synthetic receipt round trip

## Scope and contracts

Starting source baseline: `387aed9`, with the parent-provided frozen Step18 prerequisites. This change owns exactly nine new Java files (five production tool files, four test files), 17 current type identities, this report and `docs/RECEIPT_SCENARIO.md`. It changes no existing Java, build logic, protocol API, simulator entry point or benchmark candidate. No temporary Java fixtures or anonymous/local types were added. SDK and sampler corrections copied for final validation are prerequisites and excluded from the delivery.

The change supplies a separate finite two-process fixture for one synthetic receipt per successful submission. Both versions use public typed submit/delivery APIs and immutable `ReceiptTlvs`. Early receipt acknowledgement is explicitly structural; final opaque-ID correlation is a separate requirement. Internal monitors protect only local counters/collections, while SDK calls, handle cancellation and future completion happen outside them. The client and server bound pending work and report cleanup, actual peer outcomes and application decisions separately.

Source formatting ran explicitly before the parser inventory. The pre/post-format audit observed zero changes to prerequisite Java files. The following review covers each complete current type, including nested values and test fixtures, rather than only changed methods.

## TDD and verification

All Gradle invocations use `--console=plain --max-workers=1 -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=receipt-scenario'`; `/tmp/receipt-scenario-run.py` records the exact command, worktree, cache policy and exit status into each log. Normal compilation, incremental, build and configuration caches remain enabled. No `clean`, forced rerun or disabled check was used.

| Cycle | Actual behavioral red | Minimal green |
| --- | --- | --- |
| 01 | `01-ledger-red.log`: both tests failed because admission ignored the full round trip and early receipt admission was absent | `01-ledger-green.log`: two methods passed with bounded admission and correlation |
| 02 | `02-ledger-rejection-red.log`: seven new cases exposed accepted duplicates, mismatches, unbounded early storage, ignored expiry and invalid bounds/IDs | `02-ledger-rejection-green.log`: all nine ledger methods passed |
| 03 | `03-messages-red.log`: three executable assertions failed for absent message construction/validation | `03-messages-green.log`: all 12 then-current receipt methods passed with independent payload/TLV literals |
| 04 | `04-options-red.log`: valid parsing returned no value and invalid options were accepted | `04-options-green.log`: all 14 then-current methods passed with finite immutable options |
| 05 | `05-process-red.log`: both real child server JVMs exited without binding or announcing READY | `05-process-green.log`: 3.4 and 5.0 separate-JVM mixed-result pairs passed, each with three early receipts and one rejection |
| 06 | `06-fault-red.log`: all six configured fault pairs incorrectly passed as healthy because no fault was injected | `06-fault-green.log`: all missing/duplicate/mismatch pairs failed correlation for the expected counters, with cleanup complete |
| 08 | `08-fault-policy-red.log`: the invalid-option test demonstrated an accepted client-side fault setting | `08-fault-policy-green.log`: invalid client faults and faults with all-negative submissions reject before resources |

Log names above have prefix `/tmp/receipt-scenario-`. Cycle 07 adds characterization of already-implemented all-positive/all-negative pairs, finite idle expiry, refused connection and exclusive report-path protection; it passed without claiming a preceding red. An initial shell invocation used unavailable `python`; it did not execute a test and is not counted as a red. Subsequent commands use `python3`.

`09-format.log` passed explicit Spotless formatting; `10-inventory.log` parsed the final source identities (review tool compilation came from cache). `11-formatted-source.log` freshly compiled the formatted tool/test files, executed 27 receipt cases plus both actual simulator architecture cases, and built `:simulator:installDist`; it passed in 24 seconds with three tasks up-to-date. Twelve profile/scenario pairs each used two real JVMs. Tests identify class-directory launch mode and the actual library JAR; this initial functional checkpoint used library SHA-256 `d7ed9830384a5acdf13475f4dba6e61c3ef061dd117134527a2a816ae98c89ea`. The later corrected-library validation and installed evidence are recorded below when complete.

No remaining finding is known in these types. External-provider or handset interoperability and sustained throughput are outside this finite fixture.

## Complete current-type reviews

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ReceiptScenario.java
type: kg.aidarbek.simulator.ReceiptScenario
sha256: 5efd94e762fac9d229d1330c41c7066c56d7f51b9be297b8bbd362c284b05d3f
responsibility: Composes one finite role, endpoint lifetime and truthful fresh report for the receipt-workload owner.
consumers: The standalone main owns client/server endpoints; it consumes validated options, the ledger, server decision owner, public typed senders, and the existing tool JSON encoder.
S: pass | All methods implement this executable lifecycle: role wiring, bounded drive/drain, immutable result capture, and identifying the actual executing artifacts. Message semantics and correlation are separate collaborators.
O: pass | Role selection and optional fixture faults use existing focused collaborators; supporting another SMPP operation would belong in a separate fixture rather than changing the default simulator or request engine.
L: pass | Its only public operation is main. Parse and exclusive directory creation precede sockets; both roles have finite connect, decision, overall and shutdown bounds. Pending request handles are bounded, cancellation occurs on exit, and reports distinguish decisions from observed peer responses. Process tests cover success, errors, early arrivals and cleanup.
I: pass | Users get one purpose-specific CLI; the client registers only delivery handling and the server only submission handling. Internal helpers expose no artificial application interface.
D: pass | This is a concrete composition root using public SmppClient/SmppServer and typed OperationSender capabilities. It never constructs RequestWindow or transport internals; SimulatorArchitectureTest verifies the public-only boundary.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ReceiptScenario.java
type: kg.aidarbek.simulator.ReceiptScenario.Pending
sha256: 5efd94e762fac9d229d1330c41c7066c56d7f51b9be297b8bbd362c284b05d3f
responsibility: Associates one local ledger index with its already-admitted public submission handle.
consumers: Only the client owner loop retains these records in a window-bounded list.
S: pass | The record stores precisely the two values required to connect a terminal response to its existing correlation slot.
O: pass | No alternate pending-entry behavior is required; correlation policy remains in ReceiptScenarioLedger and request lifecycle in the SDK handle.
L: pass | The record does not claim deep immutability of its RequestHandle collaborator. The handle stays owned by the client loop, is cancelled on exit, and appears in at most one pending entry; its immutable index and generated record equality do not mutate the handle.
I: pass | The loop uses only index and handle accessors; no request-window or transport operation is exposed.
D: pass | It depends on the public RequestHandle abstraction and immutable response type, never the internal request engine.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ReceiptScenario.java
type: kg.aidarbek.simulator.ReceiptScenario.Result
sha256: 5efd94e762fac9d229d1330c41c7066c56d7f51b9be297b8bbd362c284b05d3f
responsibility: Carries a completed role observation into JSON rendering.
consumers: Only ReceiptScenario creates and reads this private record after endpoint shutdown observation.
S: pass | Counts, cleanup, remaining reservations, bounded failure class and criterion are one coherent final observation.
O: pass | Its Object counts field accepts either of the two concrete immutable snapshot records at this private composition boundary; new public result polymorphism is not promised.
L: pass | Every construction supplies an immutable snapshot and String plus primitive values. Generated equality retains those values; no mutable queue, exception graph, or endpoint is exposed in the report.
I: pass | Report rendering needs all six accessors and requires no network lifecycle methods.
D: pass | The observation depends only on JDK values; it does not acquire resources or call the library.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ReceiptScenarioLedger.java
type: kg.aidarbek.simulator.ReceiptScenarioLedger
sha256: 6f082c49b1508941ca688542ba9c7a7789d7cf48a099e617f375cde08ba2198a
responsibility: Enforces bounded client correlation using actual opaque submission-response IDs and explicit monotonic deadlines.
consumers: The client owner admits and settles submissions; bounded delivery handlers record receipts; tests exercise both orderings and corruption.
S: pass | All methods operate on the same finite correlation state: admission, returned-ID recognition, early storage, duplicate detection, expiry and immutable observation.
O: pass | Wire representation is delegated to ReceiptScenarioMessages and peers are accessed by the composition root. Both protocol versions use the same opaque-ID contract without profile branches.
L: pass | Synchronized access makes admission and receipt/response races atomic. Slots remain reserved through positive-response receipt waiting; count/window and 64-character ASCII bounds constrain storage. Exact IDs preserve case/zeros, subtraction handles clock wrap, and repeated expiry/finish cannot settle twice. The nine ledger tests cover these contracts.
I: pass | The owner gets admission/settlement/expiry/snapshot methods and the handler only receipt admission. There is no callback-registration, socket, persistence or alternate sequence interface.
D: pass | Its only dependencies are JDK bounded collections and primitive/string observations. It receives time explicitly and never reads a socket, clock service or RequestWindow.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ReceiptScenarioLedger.java
type: kg.aidarbek.simulator.ReceiptScenarioLedger.Slot
sha256: 6f082c49b1508941ca688542ba9c7a7789d7cf48a099e617f375cde08ba2198a
responsibility: Retains one admitted submission deadline and terminal-state flags.
consumers: Only its enclosing ledger accesses slots under the ledger monitor.
S: pass | Deadline, response-known and settled state are exactly the per-submission state needed to avoid double release.
O: pass | There is no supported slot variant; the enclosing ledger owns correlation changes.
L: pass | The private mutable object never escapes. The ledger creates each slot once, guards all reads/writes and settles at most once, including wrap-safe expiry; it relies on Object identity and promises no value equality.
I: pass | The helper has no public methods or unused lifecycle interface.
D: pass | It stores only primitives, with time and policy supplied by the ledger.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ReceiptScenarioLedger.java
type: kg.aidarbek.simulator.ReceiptScenarioLedger.Snapshot
sha256: 6f082c49b1508941ca688542ba9c7a7789d7cf48a099e617f375cde08ba2198a
responsibility: Captures immutable client submission, receipt, correlation and capacity counts with the healthy criterion.
consumers: ReceiptScenario reports it after shutdown; ledger/process tests inspect exact independent counters.
S: pass | Every field describes the same correlation run, and passed evaluates the complete healthy condition rather than a throughput metric.
O: pass | Fault injection is outside the snapshot; any fault is assessed by the same truthful healthy criterion and its explicit counters.
L: pass | All components are primitive immutable counts. Generated equality is value equality; passed requires full planned outcomes, one matched receipt per positive and zero pending/errors. Early PDU decisions remain distinct from completed correlation.
I: pass | The report and tests need count accessors plus a single criterion; no mutable state or transport API is exposed.
D: pass | The record has no infrastructure dependencies and does not reinterpret protocol bytes.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ReceiptScenarioMessages.java
type: kg.aidarbek.simulator.ReceiptScenarioMessages
sha256: 99604b4d21472fd8f1759a0ace80f3be66025dd5cbdbece1a3ca34f683fa4d2b
responsibility: Defines the fixture binary submission and explicit TLV-only synthetic receipt representation.
consumers: The client and server use builders/readers; independent literal tests assert raw index and TLV bytes.
S: pass | All four operations describe this one deliberately narrow fixture format, including DCS, flags, fixed addresses and required receipt metadata.
O: pass | General text and receipt parsing already belong to the public message helpers; new provider formats do not belong in this fixture. The two profiles share the exact supported representation.
L: pass | Builders produce immutable SDK values with owned byte arrays. Readers reject wrong flags, DCS, length, TLV shape, state or missing ID without normalizing IDs; the builder restricts indices to the finite count domain. Independent fixtures exercise correct and malformed paths.
I: pass | Consumers select submission/index or receipt/ID functions; they need no encoder, callback or session contract.
D: pass | The format depends on public immutable protocol values and ReceiptTlvs only. It does not parse full PDUs, duplicate wire codecs, infer handset state or allocate network resources.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ReceiptScenarioOptions.java
type: kg.aidarbek.simulator.ReceiptScenarioOptions
sha256: e458378a9056eff4ed2985766d954a8d1cda7c7ed93b697426ffd6443e58b913
responsibility: Validates immutable finite configuration before the receipt executable acquires files or sockets.
consumers: The main and decision owner read configuration; focused parser tests cover valid and rejected options.
S: pass | Role, version, finite counts/deadlines, injected fault and fresh report destination are one executable invocation policy, separate from throughput SimulatorConfig.
O: pass | Only the declared two roles, two profiles and finite fault enum are supported; unknown and repeated options fail explicitly. This small standalone policy does not modify general simulator parsing.
L: pass | The canonical constructor enforces count 1..10000, window 1..32, valid role ports and bounded positive Duration values. Path/Duration/enum fields are immutable. Invalid role/fault combinations and duplicate keys are rejected; record equality preserves configuration values.
I: pass | Only the CLI parser and role composition consume its accessors; there are no meaningless transport/provider settings or credential report fields.
D: pass | The record uses JDK parsing/value types plus public SmppVersion, and performs no DNS, filesystem mutation or connection work.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ReceiptScenarioOptions.java
type: kg.aidarbek.simulator.ReceiptScenarioOptions.Fault
sha256: e458378a9056eff4ed2985766d954a8d1cda7c7ed93b697426ffd6443e58b913
responsibility: Names the finite server-only receipt deviations.
consumers: Options validation and the server decision owner use these four enum values.
S: pass | NONE, MISSING, DUPLICATE and MISMATCH classify only this fixture fault axis.
O: pass | A deliberately finite executable domain is explicit rather than an extension registry; no alternate implementation contract is promised.
L: pass | Ordinary enum identity and valueOf rejection are preserved; constants have no mutable fields or special subclasses.
I: pass | Consumers need only selection/comparison, with no optional methods forced on cases.
D: pass | The enum has no dependencies on sockets, endpoints or reporting.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ReceiptScenarioServer.java
type: kg.aidarbek.simulator.ReceiptScenarioServer
sha256: 6dc1cc3cc6994b04dffb4caf58b3c9d5c347586900cbdd0d5d8302afeb8639a0
responsibility: Owns bounded synthetic submission decisions until their receipt requests settle.
consumers: Public submit handlers admit entries; the main owner polls receipt outcomes and closes retained work.
S: pass | Acceptance/rejection, first-positive fault selection, receipt initiation/outcome counting and positive decision release all describe the same controlled synthetic MC behavior.
O: pass | General command dispatch and deadlines remain in public endpoint APIs; the closed fault enum changes only this fixture. No default handler or automatic receipt loop is installed in the library.
L: pass | A count-sized bitmap rejects repeat indices; a window-bounded entry list retains one receipt handle per decision. SDK calls and CompletableFuture completion occur outside intrinsic guards. Volatile handle publication covers handler/main races; close prevents new admission, cancels handles and resolves retained stages. Decisions, admitted requests and peer outcomes are counted distinctly, including local failures.
I: pass | The SDK needs only the typed submit method; the main owns tick, close and immutable snapshot. No false response-window or transport interface is implemented.
D: pass | This application collaborator consumes public BoundSession.delivery and RequestHandle. SDK request certainty, sequencing and physical handler bounds stay inside the library; it does not create a second engine.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ReceiptScenarioServer.java
type: kg.aidarbek.simulator.ReceiptScenarioServer.Entry
sha256: 6dc1cc3cc6994b04dffb4caf58b3c9d5c347586900cbdd0d5d8302afeb8639a0
responsibility: Retains one accepted ID, selected fault, session capability, decision and current receipt handle.
consumers: Only the enclosing decision owner creates, publishes, polls and disposes entries.
S: pass | All fields are the minimum state for one accepted submission and at most one active receipt request, including a sequential duplicate attempt.
O: pass | Fault choice is immutable per entry; no entry inheritance or vendor hook is required.
L: pass | The private identity object does not claim value immutability: session/handle/future are explicit owned collaborators. Handle publication is volatile, duplicate scheduling is owner-thread state, and the list admission gate bounds retained entries. Completion occurs once through the future after guarded removal.
I: pass | No external entry API exists; no caller can mutate its fields or complete its decision.
D: pass | It refers only to public endpoint/request capabilities and JDK CompletableFuture, never internal transport/session ownership.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/ReceiptScenarioServer.java
type: kg.aidarbek.simulator.ReceiptScenarioServer.Snapshot
sha256: 6dc1cc3cc6994b04dffb4caf58b3c9d5c347586900cbdd0d5d8302afeb8639a0
responsibility: Captures immutable server decision, receipt-result, injection and retention counts.
consumers: The main report and process assertions consume its primitive values and healthy criterion.
S: pass | All components distinguish observations of the same finite server run, including expected/selected/applied faults.
O: pass | The same healthy criterion exposes injected violations rather than redefining success for a fault. Client correlation remains a separate criterion.
L: pass | Primitive components give true record value equality. Positive handler decisions are not represented as observed submission writes; admitted receipts and positive/negative peer responses are separate. A mismatch can satisfy the server count criterion while failing the client, which is documented and tested.
I: pass | Consumers need accessors and passed only; no mutable state or application handler methods escape.
D: pass | The snapshot has no dependencies on mutable infrastructure or protocol parsing.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/ReceiptScenarioLedgerTest.java
type: kg.aidarbek.simulator.ReceiptScenarioLedgerTest
sha256: 6c2ca6cd48ed454f9c762c409b7668fc45ddbbfac1aea4b382d402841a430f38
responsibility: Verifies correlation, deadline, identity and reservation contracts with controlled order and explicit times.
consumers: JUnit runs nine focused methods against the real ledger; no network or fake request engine is used.
S: pass | All cases test this single correlation state machine, including early response ordering and missing/duplicate/malformed identities.
O: pass | Additional correlation scenarios extend these fixtures without changing production dependencies or adding test-only hooks.
L: pass | Tests use independent expected counters and exact IDs, assert real behavioral reds, and verify deadline wrap and idempotent settlement. There is no substitutable mock interface; JUnit test-instance isolation and assertions are preserved.
I: pass | The fixture uses only the package-private ledger surface it verifies and JUnit assertions.
D: pass | Explicit long times and literal IDs make policy checks independent of clocks, transport, Gradle timing and persistence.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/ReceiptScenarioMessagesTest.java
type: kg.aidarbek.simulator.ReceiptScenarioMessagesTest
sha256: e286b971f661ca3a98d8834c702fc9361a64af1146c1caf6a3bd1b91077a7839
responsibility: Verifies exact synthetic message fields and malformed-format rejection using independent bytes.
consumers: JUnit invokes the message builders/readers with separately constructed immutable SDK fixtures.
S: pass | Every test asserts the binary submission or explicit receipt-TLV representation, with no session lifecycle responsibility.
O: pass | New format cases can add independent literals; the helper under test is never used to calculate the expected TLV/index bytes.
L: pass | Literal 00 00 01 02 and ASCII 000A plus NUL assert ownership-independent representations; malformed flags, lengths, absent/incorrect receipt state and empty ID must reject. It implements no fake codec or transport contract.
I: pass | The test consumes only the four fixture message operations and the immutable protocol constructors needed for independent inputs.
D: pass | It relies on JUnit and public wire values; no external SMPP implementation, network or runtime dependency is introduced.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/ReceiptScenarioOptionsTest.java
type: kg.aidarbek.simulator.ReceiptScenarioOptionsTest
sha256: a62fdc2fdc0995de364ab62ffe5efbd6e059bfdfb5e546e7e07254e54282b950
responsibility: Verifies the finite standalone CLI policy before resource acquisition.
consumers: JUnit supplies literal argument arrays to the parser and reads immutable options.
S: pass | Both tests cover only valid configuration and invalid/duplicate/contradictory options.
O: pass | Further declared option cases extend literal fixtures without altering general simulator parsing or adding parser injection.
L: pass | The valid case asserts exact profile, count, port, fault, duration and path; rejection cases require actual exceptions for invalid finite bounds and role/fault contradictions. No external state or mock subtype is involved.
I: pass | The parser and record accessors are the complete consumer surface used by the tests.
D: pass | The fixtures depend on JDK immutable values, public SmppVersion and JUnit, with no filesystem mutation or endpoint startup.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/ReceiptScenarioProcessTest.java
type: kg.aidarbek.simulator.ReceiptScenarioProcessTest
sha256: 3fd7f25d1b9a1fd77083c2d4f1fc735ebd7e0c9ad1053b28959a3c7a5f888122
responsibility: Verifies the separate-JVM receipt fixture, reports and bounded process ownership for both profiles.
consumers: JUnit owns child processes and temporary fresh report directories; the public fixture main uses real endpoints.
S: pass | Healthy, all-negative, injected correlation faults, idle expiry, refused connection and report reuse are all executable-lifecycle contracts of this fixture.
O: pass | The parameter matrix covers real profile/fault variations without replacing the endpoint or copying its request engine. New finite scenario cases belong in the matrix.
L: pass | Every process wait is finite; finally blocks destroy lingering children with bounded escalation. READY is observed from real output, reports assert independent counts and cleanup, and the reused report remains byte-identical. Separate runtime classpaths are derived from actual code sources, not assumed Gradle worker classpaths.
I: pass | Tests launch only the public main and inspect documented report fields; private source internals are not needed for success or fault assertions.
D: pass | The test uses JDK process/files/socket reservation APIs, JUnit and public SmppClient only to locate the actual library artifact. No third-party SMPP peer or runtime dependency is added.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/ReceiptScenarioProcessTest.java
type: kg.aidarbek.simulator.ReceiptScenarioProcessTest.Pair
sha256: 3fd7f25d1b9a1fd77083c2d4f1fc735ebd7e0c9ad1053b28959a3c7a5f888122
responsibility: Preserves both child exits, summary texts and logs after a finite pair has terminated.
consumers: Only the enclosing process tests construct and inspect this immutable record.
S: pass | Its six components are exactly the evidence needed to explain and assert one paired run.
O: pass | No evidence-record variants or behavior extension are promised; process supervision remains in the test owner.
L: pass | All fields are primitive ints or immutable Strings; generated equality and accessors cannot mutate files or child processes. No Process handle escapes the ownership finally block.
I: pass | The assertions need both role exits/reports/logs and receive no extra execution methods.
D: pass | It is a JDK value carrier with no endpoint, filesystem or subprocess dependency.
findings: none
```


## Documentation correction

Root integration identified a missing `@throws Exception` tag on the new public main method. The accurate tag was added without changing executable statements; no artificial behavioral red was claimed. `12-javadoc-format.log` ran formatting explicitly, followed by `13-javadoc-inventory.log` with parser inventory and `:simulator:javadoc`; all tasks passed with no Javadoc warnings. The three affected main/nested review hashes were refreshed to the final formatted source.

## Corrected-library validation and fresh installed measurements

The final carrier-progress checkpoint from `/tmp/step19-carrier-java-snapshot.tar` (seven production and six test paths) and the separate four-file sampler-progress patch were copied strictly as prerequisites. They are absent from this owned manifest and type inventory. `14-corrected-library.log` recompiled/rebuilt against that checkpoint, freshly executed all 27 receipt cases plus both architecture cases, and installed the resulting tooling distribution. It passed in 28 seconds; one compile task used the existing build cache, one task was up-to-date and seven executed. No receipt source changed.

The fresh installed workload ran outside Gradle through `/tmp/receipt-scenario-installed.py`, with command/result log `/tmp/receipt-scenario-15-installed-matrix.log`. It started 24 independent JVMs for twelve pairs; each pair planned eight submissions with window three, receipt timeout two seconds, client duration ten seconds, server duration twelve seconds and drain one second. Both roles used Java 21.0.12, Linux amd64 and `-Xms32m -Xmx128m`. The JSON matrix retains every exact launch command, both complete reports and executing class/JAR identities. These runs executed freshly; no result was reused from a cache.

Evidence directory: `/tmp/receipt-scenario-installed-4bekp1ua`; matrix SHA-256 `034afd4afa29a3870c28d0d2ee63a8216b52d96b224dce0da6835600fe0499f3`.

Installed artifact hashes:

- `HdrHistogram-2.2.2.jar`: `22d1d4316c4ec13a68b559e98c8256d69071593731da96136640f864fa14fad8`
- `lightweight-smpp-1.0-SNAPSHOT.jar`: `609e0d4e6150e3704942339a9df621465f662ef86e52241bc696c2adbd5a717f`
- `simulator-1.0-SNAPSHOT.jar`: `064cd161ac1398772a57c92daf0af0ef1acf24ad04a3939b0de512825b108e13`

The histogram artifact was an existing distribution dependency; this fixture adds no dependency and uses no histogram API. The library runtime classpath remains separately checked.

| Profile | Fault / rejection | Submission + / − | Receipt requests | Correlated | Receipt responses + / − | Missing / duplicate / unmatched | Client / server exit |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 3.4 | none / 0 | 8 / 0 | 8 | 8 | 8 / 0 | 0 / 0 / 0 | 0 / 0 |
| 3.4 | none / 3 | 6 / 2 | 6 | 6 | 6 / 0 | 0 / 0 / 0 | 0 / 0 |
| 3.4 | none / 1 | 0 / 8 | 0 | 0 | 0 / 0 | 0 / 0 / 0 | 0 / 0 |
| 3.4 | missing / 0 | 8 / 0 | 7 | 7 | 7 / 0 | 1 / 0 / 0 | 1 / 1 |
| 3.4 | duplicate / 0 | 8 / 0 | 9 | 8 | 8 / 1 | 0 / 1 / 0 | 1 / 1 |
| 3.4 | mismatch / 0 | 8 / 0 | 8 | 7 | 8 / 0 | 1 / 0 / 1 | 1 / 0 |
| 5.0 | none / 0 | 8 / 0 | 8 | 8 | 8 / 0 | 0 / 0 / 0 | 0 / 0 |
| 5.0 | none / 3 | 6 / 2 | 6 | 6 | 6 / 0 | 0 / 0 / 0 | 0 / 0 |
| 5.0 | none / 1 | 0 / 8 | 0 | 0 | 0 / 0 | 0 / 0 / 0 | 0 / 0 |
| 5.0 | missing / 0 | 8 / 0 | 7 | 7 | 7 / 0 | 1 / 0 / 0 | 1 / 1 |
| 5.0 | duplicate / 0 | 8 / 0 | 9 | 8 | 8 / 1 | 0 / 1 / 0 | 1 / 1 |
| 5.0 | mismatch / 0 | 8 / 0 | 8 | 7 | 8 / 0 | 1 / 0 / 1 | 1 / 0 |

Every run observed complete cleanup, zero final client active/early entries, zero server pending decisions and zero endpoint pending requests/replies. Observed active/early/decision peaks never exceeded the configured window of three. All positive healthy receipts arrived before their submission response was processed; no receipt was emitted for configured negatives. Each fault run reported exactly one selected/applied fault. Every expected matrix assertion passed; the intentional negative exits remain visible rather than being relabeled healthy successes. These are bounded functional observations, not throughput or capacity results.

## Final handoff boundary

The owned final inventory is nine new Java sources and 17 reviewed current types, plus the new guide and this report: 11 files total. The final explicit source manifest and SHA-256 list are `/tmp/receipt-scenario-final-manifest.txt` and `/tmp/receipt-scenario-final-sha256.txt`. All 17 review blocks match the final formatted source, including the Javadoc correction; there are no remaining findings in the owned types. No existing source or build file belongs to this delta.

This worktree copied the documented Step18 baseline, sampler-progress patch and carrier-progress source checkpoint solely as prerequisites. Their production/test types and reviews belong to their original owners and are excluded from this change inventory. The carrier review was still being finalized at handoff. As explicitly coordinated with root, the combined full `check`/`build` and global coverage reconciliation run at root integration once that prerequisite report is present; this report does not claim those combined tasks have already passed locally. The focused corrected-library tests, actual architecture checks, warning-free Javadoc and fresh installed matrix above are completed evidence. Root may repeat the matrix against a later final candidate; that must be recorded as a new measurement with its own exact artifact hashes.

## Fresh candidate 2 replay: exact final installed artifacts

The coordinator requested a fresh replay against the immutable corrected
release candidate `/tmp/lightweight-smpp-candidate2`, version `0.1.0-rc.1`,
declared as `387aed9ef523c85fb3cfa0f8f245438759deb6fc+fb3db4466410adc08ae1521edd2b4713134de38eed5eb7458eff86c95842b68c`. No fixture source, assertion or deadline changed.
The external orchestration script preserved the previous installed matrix and
changed only the target classpath and fresh output location. Its exact copy is
retained at `build/runs/step19-corrected-wire-checks/scripts/receipt-candidate2.py`.
It invoked `kg.aidarbek.simulator.ReceiptScenario` directly from the candidate's
installed JARs; it did not invoke a locally recompiled class directory.

| Executing candidate input | SHA-256 |
| --- | --- |
| `bin/simulator` | `03fbbcfad916ff2b1a59b3a0e604b902bdeb9a1b195a6dfbaa66ea8d5c71abf5` |
| `lightweight-smpp-0.1.0-rc.1.jar` | `609e0d4e6150e3704942339a9df621465f662ef86e52241bc696c2adbd5a717f` |
| `simulator-0.1.0-rc.1.jar` | `47705647225df458d2f4fc8cb18c12a37198d82b72c25c8fbace72d308649161` |
| Existing `HdrHistogram-2.2.2.jar` | `22d1d4316c4ec13a68b559e98c8256d69071593731da96136640f864fa14fad8` |

All **12 pairs / 24 fresh JVMs** passed their original assertions. Each pair
planned eight submissions, with window three, two-second receipt request
budget, ten-second client duration, twelve-second server duration and one-second
drain. JVM flags remained `-Xms32m -Xmx128m`, with Java 21.0.12 / Linux amd64.
The exact commands and role reports are retained in the fresh matrix.

| Profile | Fault / rejection | Submission + / − | Receipt requests | Correlated | Receipt responses + / − | Missing / duplicate / unmatched | Client / server exit |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 3.4 | none / 0 | 8 / 0 | 8 | 8 | 8 / 0 | 0 / 0 / 0 | 0 / 0 |
| 3.4 | none / 3 | 6 / 2 | 6 | 6 | 6 / 0 | 0 / 0 / 0 | 0 / 0 |
| 3.4 | none / 1 | 0 / 8 | 0 | 0 | 0 / 0 | 0 / 0 / 0 | 0 / 0 |
| 3.4 | missing / 0 | 8 / 0 | 7 | 7 | 7 / 0 | 1 / 0 / 0 | 1 / 1 |
| 3.4 | duplicate / 0 | 8 / 0 | 9 | 8 | 8 / 1 | 0 / 1 / 0 | 1 / 1 |
| 3.4 | mismatch / 0 | 8 / 0 | 8 | 7 | 8 / 0 | 1 / 0 / 1 | 1 / 0 |
| 5.0 | none / 0 | 8 / 0 | 8 | 8 | 8 / 0 | 0 / 0 / 0 | 0 / 0 |
| 5.0 | none / 3 | 6 / 2 | 6 | 6 | 6 / 0 | 0 / 0 / 0 | 0 / 0 |
| 5.0 | none / 1 | 0 / 8 | 0 | 0 | 0 / 0 | 0 / 0 / 0 | 0 / 0 |
| 5.0 | missing / 0 | 8 / 0 | 7 | 7 | 7 / 0 | 1 / 0 / 0 | 1 / 1 |
| 5.0 | duplicate / 0 | 8 / 0 | 9 | 8 | 8 / 1 | 0 / 1 / 0 | 1 / 1 |
| 5.0 | mismatch / 0 | 8 / 0 | 8 | 7 | 8 / 0 | 1 / 0 / 1 | 1 / 0 |

Every healthy positive receipt arrived early and was subsequently correlated by
the actual returned ID. Each selected fault was applied once. The mismatch
server's zero exit remains deliberately distinct from the client's failed
correlation. All roles reported complete cleanup, zero endpoint pending requests
and replies, and zero final local reservations; observed active, early and
decision peaks were at most three. There were no assertion failures or retries.
This remains a finite functional workload with no throughput claim.

Fresh evidence is retained under `build/runs/step19-corrected-wire-checks/receipt`,
including every original role report and process log. The matrix SHA-256 is
`6e09a638d7748b46f870cbb763ebb0d8368722ce706d9c92c89099819f0daf8f`;
its supervisor log SHA-256 is
`14ffc852a47373ecc7afff556d398390639b2cb5b4f57ccb2446b187f686be6a`.
The shared `inputs-before.json` / `inputs-after.json` verify all installed
candidate files remained byte-identical throughout both the receipt replay and
the independent 56-pair raw replay. Shared reconciliation `summary.json` has
SHA-256 `321e3d037f51ae0e60ac0ca813f27193a025b463c7da80dd978692fd3496d366` and identifies all
136 fresh role processes (80 Java, 56 Python) across the two matrices.
No Java/build changes or extra type reviews arise from this measurement-only
documentation append; the 17 whole-type source hashes above remain current.

## Final candidate 3 verification: installed receipt matrix

All **12 pairs / 24 fresh JVMs** passed the unchanged installed fixture assertions
against `/tmp/lightweight-smpp-candidate3`, declared `387aed9ef523c85fb3cfa0f8f245438759deb6fc+2d63cd99c32ca3b197cbc973a100c9280ef00a05d4c5311b0cb7c011bb823a00`. There was no
Java/build change, no assertion/deadline adjustment and no retry; the 17 existing
whole-type reviews and hashes remain current. The exact external script
`build/runs/step19-final-wire-checks/scripts/receipt-candidate3.py` differs from the prior script only in
candidate path and fresh output path. It launches the public separate entry
point `kg.aidarbek.simulator.ReceiptScenario` from installed JARs, not from a
locally recompiled class directory.

| Final installed input | SHA-256 |
| --- | --- |
| `bin/simulator` | `03fbbcfad916ff2b1a59b3a0e604b902bdeb9a1b195a6dfbaa66ea8d5c71abf5` |
| `lib/lightweight-smpp-0.1.0-rc.1.jar` | `609e0d4e6150e3704942339a9df621465f662ef86e52241bc696c2adbd5a717f` |
| `lib/simulator-0.1.0-rc.1.jar` | `a59d553d3ec84de5d953a927696565f2c498384386a241a74a113426011d1d9e` |
| `lib/HdrHistogram-2.2.2.jar` | `22d1d4316c4ec13a68b559e98c8256d69071593731da96136640f864fa14fad8` |

The original count 8/window 3, request timeout 2s, client duration 10s, server
12s and drain 1s are retained, with `-Xms32m -Xmx128m`. Every role reports the
actual Java 21 environment and final artifact hashes. Each selected fault
executes exactly once; every local reservation and endpoint request/reply
count returns to zero, with active/early/decision peaks bounded at three.

| Profile | Fault / reject-every | Submission + / − | Receipt requests | Correlated | Receipt responses + / − | Missing / duplicate / unmatched | Client / server exit |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 3.4 | none / 0 | 8 / 0 | 8 | 8 | 8 / 0 | 0 / 0 / 0 | 0 / 0 |
| 3.4 | none / 3 | 6 / 2 | 6 | 6 | 6 / 0 | 0 / 0 / 0 | 0 / 0 |
| 3.4 | none / 1 | 0 / 8 | 0 | 0 | 0 / 0 | 0 / 0 / 0 | 0 / 0 |
| 3.4 | missing / 0 | 8 / 0 | 7 | 7 | 7 / 0 | 1 / 0 / 0 | 1 / 1 |
| 3.4 | duplicate / 0 | 8 / 0 | 9 | 8 | 8 / 1 | 0 / 1 / 0 | 1 / 1 |
| 3.4 | mismatch / 0 | 8 / 0 | 8 | 7 | 8 / 0 | 1 / 0 / 1 | 1 / 0 |
| 5.0 | none / 0 | 8 / 0 | 8 | 8 | 8 / 0 | 0 / 0 / 0 | 0 / 0 |
| 5.0 | none / 3 | 6 / 2 | 6 | 6 | 6 / 0 | 0 / 0 / 0 | 0 / 0 |
| 5.0 | none / 1 | 0 / 8 | 0 | 0 | 0 / 0 | 0 / 0 / 0 | 0 / 0 |
| 5.0 | missing / 0 | 8 / 0 | 7 | 7 | 7 / 0 | 1 / 0 / 0 | 1 / 1 |
| 5.0 | duplicate / 0 | 8 / 0 | 9 | 8 | 8 / 1 | 0 / 1 / 0 | 1 / 1 |
| 5.0 | mismatch / 0 | 8 / 0 | 8 | 7 | 8 / 0 | 1 / 0 / 1 | 1 / 0 |

Every healthy positive receipt arrived early and was safely matched when its
actual opaque submission ID became available. The mismatch server's exit 0
remains distinct from the client's correlation failure. Positive submission,
receipt request and observed receipt response counts remain separate; no
handset delivery/replay/persistence is inferred. All original assertions passed
without retries or dropped observations.

Fresh role reports, process logs, exact commands and source/artifact hashes are
retained in `build/runs/step19-final-wire-checks/receipt`. Its matrix SHA-256 is
`7694a3f41424e2bc3211e3b36eb95add9cc3129f8a99d4b5781d7cabc647e1f2` and supervisor log SHA-256 is
`a7416da22f28a48ad73b72b41137ab7a6b4bc860a190b877a46e119c405798d6`. Shared reconciliation
`summary.json` SHA-256 `cd48951751a55b297d65715d52904039fe466bf08708a4bfb61e9e44624022cc` verifies all final installed inputs before/after
both functional matrices. An independent enclosing-JAR class comparison found
only TrafficRunner and its four nested compiled entries changed from candidate2;
all 12 ReceiptScenario class entries were byte-identical. The initial auxiliary
comparison expected only the outer class to differ and was corrected to include
its nested class entries; that bookkeeping observation is retained and is not
product/TDD evidence. These finite checks ran on the shared host after the old
heavy campaigns ended, with final soak activity still present. They provide
functional assertions rather than a throughput estimate.
