# Common-operation implementation review

Work proceeds in an isolated Step 14 worktree from `e79b9f4`; finalized Step 12
endpoint prerequisites and Step 13 simulator prerequisites are excluded from the
Step 14 patch and type inventory. Every Gradle command below uses
`--console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step14'`.
Build/configuration caches are retained. Logs are `/tmp/step14-*.log`.

## Actual sequential TDD evidence

| Cycle | Focus and observed red | Green scope / correction |
| --- | --- | --- |
| 01 | `CommonValuesTest`: overlong query ID accepted. `01-query-values-red.log`. | Same selector passes after representation validation, `01-query-values-green.log`. |
| 02 | Independent query frame expected 26 octets, stub produced 16. `02-query-wire-red.log`. | Query full bytes in both profiles plus values pass, `02-query-wire-green.log`. |
| 03 | Relative query completion accepted, failed 5.0 body incorrectly parsed, and congestion rejected. `03-query-validation-red.log`. | `Common*Test` plus values pass, `03-query-validation-green.log`. |
| 04 | Cancel/one-way fixture failed because no body codec was registered. `04-cancel-notifications-red.log`. | Selected query/cancel/one-way/validation/value tests pass, `04-cancel-notifications-green.log`. An initial implementation run failed on availability TLV registration; retained as `04-cancel-notifications-initfailure.log`, not a green. |
| 05 | Replacement/multi fixture failed with missing registration after cycle 04 was green. `05-replace-multi-wire-red.log`. | All common codec/value tests pass, `05-replace-multi-wire-green.log`. Earlier `05-replace-multi-wire-initfailure.log` was the same registration initialization failure and is not behavioral red evidence. |
| 06 | 256-byte payload accepted, outbind identifier limit absent, and caller mutation changed destination list. `06-immutable-values-red.log`. | Common codec/value tests pass, `06-immutable-values-green.log`; immutable ownership and redacted value output verified. |
| 07 | Four semantic tests failed for missing profile payload limits, flags, response-body presence, and single-payload policy. `07-message-validation-red.log`. | `Common*Test` plus values pass, `07-message-validation-green.log`. |
| 08 | Repeated callbacks rejected as singleton; incomplete outgoing SAR accepted. `08-tlv-companions-red.log`. | Common tests, existing message optional/value tests, and common values pass, `08-tlv-companions-green.log`. Initial green attempt retained as `08-tlv-companions-fixture-correction.log`: a GSM network-ID fixture incorrectly used reserved prefix `0`; the primary §4.8.4.56 defines `1`, so both test occurrences were corrected without changing production interpretation. Exact 20 operation/profile tables and unsupported/malformed cases also pass. |
| 09 | Query operation mismatch and non-transaction diagnostic context were accepted. `09-response-context-red.log`. | Same common/message/value suite passes, `09-response-context-green.log`. |
| 10 | Interleaved one-way allocation reused request sequence 1; unsupported one-way identity was accepted. `10-notification-sequence-red.log`. | Full `kg.aidarbek.smpp.request.*Test` suite passes, `10-notification-sequence-green.log`. Initial `10-notification-sequence-compile-attempt.log` is an explicit-close lint failure in test cleanup; fixed before the behavioral red and not counted as evidence. |
| 11 | Explicit vendor permission was ignored and reserved tag declarations were accepted. `11-explicit-extension-red.log`. | Common/message/value suite passes, `11-explicit-extension-green.log`; configured permission is immutable and limited to one command/profile. |
| 12 | Characterization, no invented red: all ten body codecs already satisfy direct status/null/bounds/owned-array contracts. | `CommonBodyContractTest` passes on its first run, `12-body-contract-characterization.log`. Includes every truncated mandatory body prefix, malformed TLVs/counts/discriminators, identifier/address boundaries, and raw TLV preflight ahead of outgoing unsupported-tag interpretation. |
| 13 | Outbind listener accepted unresolved addresses and connector retained the mutable version set. `13-outbind-config-red.log`. | `OutbindConfigTest` passes, `13-outbind-config-green.log`, including the 3.4 receiver-only and explicit 5.0 bind-mode policy. |
| 14 | Real paired service registration rejected an unimplemented operation key. `14-paired-endpoint-red.log`. | Catalogue composition makes common paired endpoint and existing SmppEndpointsTest cases pass, `14-paired-endpoint-green.log`. |
| 15 | Current common sending conveniences returned empty when the exact profile/mode permitted them. `15-common-capabilities-red.log`. | Forwarding through the same typed sender policy passes the profile/mode/role matrix and missing-service negatives, `15-common-capabilities-green.log`. |
| 16 | MC alert capability was absent in both real-loopback cases. `16-alert-endpoint-red.log`. | Typed alert delivery, off-I/O handler invocation, unique sequence and local completion without a peer response pass, `16-alert-endpoint-green.log`. |
| 17 | Expired physical guard returned REJECTED instead of WRITE_TIMEOUT; the controlled fake also reported writeStarted=true after guard refusal. `17-alert-guard-red.log`. | Correct deadline classification and shared FrameTransportContract pass with cancellation, capacity and close cases, `17-alert-guard-green.log`. Earlier `17-alert-guard-compile-attempt.log` is only a test-cleanup checked-exception lint correction. |
| 18 | Explicit outbind entry stubs failed instead of binding the two roles on one socket. `18-outbind-endpoint-red.log`. | 3.4 RX plus 5.0 RX/TX/TRX and the existing ordinary endpoint/control suites pass, `18-outbind-endpoint-green.log`. Earlier `18-outbind-endpoint-fixture-correction.log` used an overlong demo bind password; the fixture was corrected before the relevant red. |
| 19 | Rejected MC follow-up bind was observed as CLOSED instead of BIND_REJECTED with status. `19-outbind-lifecycle-red.log`. | Status retention, no automatic reconnect, authentication gating, duplicate rejection, timeout retention and cancellation pass, `19-outbind-lifecycle-green.log`. |
| 20 | Reserved outgoing bind TON was accepted by listener start instead of rejected before opening. `20-outbind-preflight-red.log`. | Outgoing bind preflight and existing outbind lifecycle/profile cases pass, `20-outbind-preflight-green.log`. |
| 21 | Listener start still succeeded after graceful shutdown. `21-outbind-admission-red.log`. | Permanent admission closure passes, `21-outbind-admission-green.log`. The first attempt passed library tests but unqualified multi-project `test` also selected simulator tests with a library-only filter; retained as `21-outbind-admission-task-scope-attempt.log`. The corrected `:test` call legitimately reused those outputs; later complete test runs execute the expanded suite. |
| 22 | Common simulator keys were absent and invalid sender roles were accepted. `22-common-simulator-red.log`. | Real positive/negative request matrix for query/cancel/replace/multi in both profiles plus role checks passes, `22-common-simulator-green.log`. |
| 23 | Finite one-way smoke stub reported zero notifications and accepted unsupported scenarios. `23-oneway-smoke-red.log`. | Alert/outbind runs in both profiles observe exact counts and physical cleanup, `23-oneway-smoke-green.log`. |
| 24 | Late successful alert stage beat its deadline while the periodic tick had not run; state remained BOUND_RX. `24-alert-decision-deadline-red.log`. | Completion checks the same total deadline, cancels context and closes; alert handler/write/loopback suites pass, `24-alert-decision-deadline-green.log`. |
| 25 | Controlled port reported queued cancellation success after a concurrent guard had already won. `25-fixture-cancellation-race-red.log`. | Atomic final cancellation check plus all alert write/port contracts pass, `25-fixture-cancellation-race-green.log`; this is an owned test-fixture contract fix, not a runtime transport modification. |
| 26 | Deterministic 255-byte 3.4 replacement configuration passed early simulator selection. `26-replacement-preflight-red.log`. | Configured-size preflight rejects before traffic/report/socket creation while 5.0 payload placement remains supported, `26-replacement-preflight-green.log`. |
| 27 | Independent review found successful MC authentication left an ESME socket open when follow-up bind callback reservation failed; controlled raw peer timed out. `27-outbind-admission-failure-red.log`. | The coordinator catches admission failure and closes immediately; outbind and alert suites pass in `27-outbind-admission-failure-green.log`. |
| 28 | Whole-type fixture review found a throwing write guard/terminal observer escaped and could abandon other accepted writes. `28-fixture-observer-cleanup-red.log` (initial terminal-only red retained separately). | The bounded fixture retains the first cleanup failure, drains all accepted writes and waits for internal callbacks before exceptional termination; `28-fixture-observer-cleanup-green.log` passes. The initial green attempt exposed a late-stage test synchronization race: completing a supplied future can precede the dispatcher attaching its dependent. `28-fixture-observer-cleanup-synchronization-attempt.log` is retained; the test now awaits the same required closure before asserting state/cancellation, without changing production deadline behavior. |


No dependency, syntax, compiler-warning, initialization, or fixture-correction
failure is counted as a meaningful behavioral red. The final validation below includes explicit source formatting, current inventory, full type reviews, executable finite smoke runs and complete checks.

## Additional characterization and integration evidence

- `step14-wire-error-fixtures.log` adds independent complete error-response bytes for all four pairs in both profiles; first run passed, with no invented red.
- `step14-wire-composition-refactor.log` verifies shared immutable default codec composition after the established green wire suite.
- `step14-wire-check.log` was the isolated wire checkpoint: 527 library/architecture cases passed, review coverage and formatting passed, and review-test/tool compilation cache reuse was reported by Gradle.
- `step14-oneway-handler-characterization.log`, `step14-alert-callback-characterization.log` and `step14-oneway-drain-characterization.log` passed on their first runs: shared message/alert invocation order, independent controls, blocked callback capacity, ignored absent hooks, physical handler retention, accepted-work drain and explicit 3.4 outbind RX restriction.
- `step14-full-behavior-checkpoint.log` passed 547 library cases and 44 of 45 simulator cases. Its sole failure was the copied simulator architecture allow-list rejecting exact public policy/negotiation types required by common adapters. The root owner corrected its policy boundary and retained the forbidden RequestWindow probe; `step14-integration-02-public-policy-green.log` passes both tests. That root-owned architecture file and CLI integration are excluded from this patch/review inventory.
- Step 12 was copied from its exact finalized 19-file production snapshot; additional finalized tests/examples and its complete owner review were later added from `step12-tests-review-snapshot.tar`. Step 13 build/settings/core simulator files are separate copied prerequisites. No shared build checks or caches were disabled.
- No temporary Java sources or ad-hoc Java benchmark fixtures were created outside the repository. The finite CommonOperationSmoke and all nested test/fixture types are included in the owned inventory. Temporary review/patch orchestration scripts are Python, not runtime or project Java.


## Final verification and finite executions

The last separate formatter is `step14-final-format-after-fixtures.log`, followed
by `step14-final-inventory-after-fixtures.log`. The current ownership manifest
contains **79 reviewed types in 62 Java sources**. The final whole-repository
coverage result includes **357 types**, including finalized prerequisites and
the separate root-owned simulator integration review.

Exact final verification commands, with the shared flags stated above:

```sh
./gradlew spotlessApply --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step14'
./gradlew solidReviewInventory --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step14'
./gradlew check build :dependencies --configuration runtimeClasspath --console=plain -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step14'
```

`step14-final-check-build.log` succeeds with 10 executed and 12 up-to-date tasks;
configuration cache is stored. Library tests execute freshly after the fixture
changes: **615 tests, zero failures/errors/skips**. The unchanged simulator suite
legitimately reuses its previous fresh result: **47 tests, zero failures/errors/skips**.
The review-tool suite likewise reuses its established **60 passing tests**. The
prior `step14-final-tests-javadoc-tools.log` freshly executed library/simulator
suites, generated warning-free library/tool Javadoc, and assembled the tool
distribution. `runtimeClasspath` reports **No dependencies** for the library.

A ZIP audit confirms LICENSE/NOTICE are packaged and the library JAR contains no
simulator, review, examples or test classes. Its SHA-256 is
`132f91f867417bd8b36c3ef6539e27a01afdeff86040c4e626f3091e346a7810`.
The finite smoke runs used that exact artifact; subsequent changes affected only
test fixtures and documentation, so its final hash is unchanged.

Fresh finite executions ran on 2026-09-09 at 11:13 UTC, Linux x86_64
7.0.0-31-generic/glibc 2.39, 12 logical processors, OpenJDK 21.0.12+8
(Ubuntu). Baseline checkout identity was
`e79b9f4cb905056fe6d91ded563e2a19b48ce4d9`, composed with the finalized Step 12/13
prerequisites and the current reviewed Step14 sources. The standalone process
commands were:

```sh
java -cp 'simulator/build/install/simulator/lib/*' kg.aidarbek.simulator.CommonOperationSmoke alert 3.4
java -cp 'simulator/build/install/simulator/lib/*' kg.aidarbek.simulator.CommonOperationSmoke alert 5.0
java -cp 'simulator/build/install/simulator/lib/*' kg.aidarbek.simulator.CommonOperationSmoke outbind 3.4
java -cp 'simulator/build/install/simulator/lib/*' kg.aidarbek.simulator.CommonOperationSmoke outbind 5.0
```

| Scenario | Notifications | Authentications | Bound sessions | Cleanup | Exit |
| --- | ---: | ---: | ---: | --- | ---: |
| alert 3.4 | 1 | 1 | 2 | complete | 0 |
| alert 5.0 | 1 | 1 | 2 | complete | 0 |
| outbind 3.4 | 1 | 2 | 2 | complete | 0 |
| outbind 5.0 | 1 | 2 | 2 | complete | 0 |

Raw commands, stdout/stderr, artifact hashes, environment and elapsed process
durations are retained in `/tmp/step14-fresh-finite-smoke.json`. These executions
were fresh JVM processes, not cached tests; observed durations include JVM startup
and cleanup and establish no load, capacity or production latency claim.

The independent endpoint lifecycle review identified the cycle27 admission failure
and verified the behavioral red, fresh green and corrected source. It found no
remaining scoped lifecycle/concurrency issue. The root-owned simulator integration
has its own full seven-type review in `0013-simulator-integration.md`. There are no
remaining known in-scope findings. Protocol incompatibilities and intentionally
external message storage/list expansion policies are explicit in COMMON_OPERATIONS.

## Reviewed final types

Whole-file reviews cover each owned type and all inherited contracts at the post-format source hashes below. Finalized Step 12/13 prerequisites and root-owned architecture/CLI integration are excluded from this inventory and retain their owner reports.

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/CommonOperationSmoke.java
type: kg.aidarbek.simulator.CommonOperationSmoke
sha256: fc6d009e96d586270a3c91fbb4b0be843a33c49a767932e3920334e99157ca8a
responsibility: Executes finite real-loopback alert and explicit-outbind interoperability checks with bounded cleanup.
consumers: An independent JVM main and CommonOperationSmokeTest invoke one supported scenario/profile at a time.
S: pass | Finite notification/binding observations are separate from the simulator load scheduler and do not claim throughput capacity.
O: pass | A small explicit scenario switch matches the two implemented one-way workflows; it does not create fake request pacing or a retry framework.
L: pass | Both profiles observe one notification, expected authentication and session counts, and complete endpoint cleanup; unsupported scenarios reject before I/O.
I: pass | The CLI requires only operation/profile and prints a small immutable result without exposing internal endpoint coordination.
D: pass | Uses public endpoint capabilities and JDK observation primitives; no internal tracker or transport API is used by the simulator.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/CommonOperationSmoke.java
type: kg.aidarbek.simulator.CommonOperationSmoke.Result
sha256: fc6d009e96d586270a3c91fbb4b0be843a33c49a767932e3920334e99157ca8a
responsibility: Stores immutable finite-smoke operation/profile/count and cleanup observations.
consumers: The smoke main prints it and deterministic tests assert notification/authentication/binding counts.
S: pass | Only observation storage is performed; no timing thresholds or production capacity claims are inferred.
O: pass | Fixed fields describe the bounded checks without requiring a polymorphic report hierarchy.
L: pass | Record equality includes the exact scenario and profile/counts; no mutable collection or payload is retained.
I: pass | Accessors expose only finite-run observations, not request or transport internals.
D: pass | Depends on primitive/String/profile values, with no runtime measurement or endpoint implementation collaborator.
findings: none
```

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/CommonTraffic.java
type: kg.aidarbek.simulator.CommonTraffic
sha256: db84444bca0c3809c66167ec499827b902a1ea77be613ed8f1a253bce3854df4
responsibility: Adapts query/cancel/replace/multi simulator traffic to typed public senders and deterministic receiver decisions.
consumers: Simulator operation selection calls find and ReplyController registration; CommonTrafficTest exercises real endpoints and faults.
S: pass | Builds operation-specific values and extracts receiver content; scheduling, fault timing, counters and socket ownership remain in existing collaborators.
O: pass | TrafficOperation, ContentPlan and typed handler registration extend workloads without adding a second request tracker.
L: pass | Both profiles retain correct negative bodies; early role/mode/3.4 replacement bounds reject invalid runs and binary replacement never discards absent wire metadata.
I: pass | Only common operation selection/registration and focused builders are exposed within the tool package; one-way work uses a separate smoke owner.
D: pass | Depends on public endpoint/protocol/policy APIs and tool abstractions, never RequestWindow, transport internals or library runtime instrumentation.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/CommonOperationSmokeTest.java
type: kg.aidarbek.simulator.CommonOperationSmokeTest
sha256: faa3a27865daa17018937cc6b576a0380af2fbc5a05b236113d275cb2f10d897
responsibility: Checks finite one-way simulator scenarios and unsupported-scenario rejection.
consumers: CommonOperationSmoke runs real alert/outbind endpoints for both profiles with bounded cleanup observations.
S: pass | Finite correctness and cleanup are the only assertions; no throughput threshold is inferred from a unit test.
O: pass | The two supported scenario names and profiles are exercised through the same executable entry behavior.
L: pass | Red23 proved missing notification counts/rejection; green observes one notification, two bound sessions, exact authentication counts and physical cleanup.
I: pass | JUnit calls only the focused run/result API and does not require a request-pacing interface for one-way commands.
D: pass | Depends on the tool smoke abstraction and immutable profile values, not library internals or external network services.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/CommonTrafficTest.java
type: kg.aidarbek.simulator.CommonTrafficTest
sha256: 5f05a65a19afaa8497ab244356a0bbe409c4195d88c6593b62cbbe80fc9f3f9e
responsibility: Checks simulator common operation selection, binary replacement bounds and real deterministic positive/negative responses.
consumers: All four paired adapters execute over public endpoints in both profiles against ReplyController content/fault accounting.
S: pass | Only common adapter behavior is tested; load scheduling, metrics and helper text variants remain in their own tests.
O: pass | Version/operation/status tables expand scenarios without modifying production for testing.
L: pass | Red22 established typed registration/role checks and red26 rejects 3.4 oversized replacement before an operation can start; exact error-body presence and receiver counters are asserted.
I: pass | Tests consume package-local tool adapters and public endpoint capabilities rather than request or transport internals.
D: pass | Local endpoints and deterministic RawContent/ReplyController abstractions replace external providers; no dependency enters the library runtime.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/CommonCommandCodec.java
type: kg.aidarbek.smpp.codec.CommonCommandCodec
sha256: 6d7c4ccb67fba92cc709832bb29f2330c49a7769f9a943647d9b38857b445126
responsibility: Translates one registered common-operation body with exact profile/status and bound checks.
consumers: PduCodec and direct CommandCodec callers use stable ID/type metadata and owned arrays.
S: pass | The private adapter owns body translation; table/companion logic and list layouts have focused collaborators.
O: pass | Public extension remains CommandCodec/PduCodec and explicit vendor composition; this fixed ten-layout implementation is private.
L: pass | CommonBodyContractTest applies status/null/ownership/bounds contracts to all ten registrations and both profiles.
I: pass | Implements only the small CommandCodec interface; applications need no message service interface to use it.
D: pass | Depends on immutable protocol/profile values and cohesive pure field/TLV codecs, without session/network dependencies.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/CommonCommandCodecs.java
type: kg.aidarbek.smpp.codec.CommonCommandCodecs
sha256: 53ffb7940d7dec640079cb8a94c7f08e83c46b94317cb7c55c08a9038973cab0
responsibility: Composes immutable standard or explicitly vendor-configured common body codec registrations.
consumers: PduCodec registrants and standalone typed-TLV callers choose the required composition.
S: pass | Construction/registration is separate from wire algorithms and session capabilities.
O: pass | Explicit CommonTlvExtension values add exact-context vendor interpretation without weakening standard validation.
L: pass | Returned codec metadata stays stable, shared default composition is immutable, and interface contracts are exercised for every codec.
I: pass | Callers can request codecs or typed interpretation separately without endpoint dependencies.
D: pass | Composes the CommandCodec and TlvValueCodec boundaries with immutable profile data only.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/CommonResponseRules.java
type: kg.aidarbek.smpp.codec.CommonResponseRules
sha256: 688975a3275d60e85eb119f285608178afa6501cd6dabb25b9c9b572af00d0d1
responsibility: Checks response conditions requiring the original common-operation request.
consumers: Endpoint operation descriptors or standalone callers apply it after successful body validation.
S: pass | Query identity and transaction applicability are separated from sequence tracking, deadlines and delivery outcomes.
O: pass | Operation descriptors compose this validator; missing application expansion context is never guessed.
L: pass | Rejects mismatched concrete response types and IDs while preserving ignored unsupported diagnostics; tests cover valid pairs and failures.
I: pass | One focused validation method requires only original request, response and profile.
D: pass | Depends on immutable PDUs and typed raw-parameter interpretation, without request windows, sockets or application storage.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/CommonTlvExtension.java
type: kg.aidarbek.smpp.codec.CommonTlvExtension
sha256: 4a3c66804a18d564e7553dee51a6def18d080167e665597937b54cbdab02b1a6
responsibility: Declares one immutable vendor tag permission and interpretation in one command/profile.
consumers: CommonTlvSupport copies declarations when building immutable configured codecs.
S: pass | Context/range/metadata validation is distinct from interpreting vendor business semantics.
O: pass | The TlvValueCodec collaborator is the actual extension point; standard tags cannot be overridden.
L: pass | Descriptor equality retains exact context, and supplied codecs must honor the documented ownership/thread-safety contract.
I: pass | Only context, codec and repetition metadata required for registration are exposed.
D: pass | Depends on the inward value-codec abstraction and immutable profile/protocol values.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/CommonTlvSupport.java
type: kg.aidarbek.smpp.codec.CommonTlvSupport
sha256: 8015af4af812e5f64a38ff3af1aebac97dbadc7ccb7e4e55ebe0d658c4cb000a
responsibility: Composes common-operation occurrence, supported-value and companion policies once.
consumers: CommonCommandCodec validates incoming/outgoing raw parameters through the immutable composition.
S: pass | Validation composition delegates value structure and shared message companion rules rather than duplicating them.
O: pass | Scoped vendor codecs extend supported contexts; unsupported input remains raw and cannot enable output.
L: pass | Raw order/ownership remains untouched, reserved values are ignored semantically, and duplicate/companion contracts are tested.
I: pass | The private codec consumer needs only validate and typed-registry access.
D: pass | Depends on pure rule and TlvValueCodec abstractions, without endpoints or application callbacks.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/MessageTlvSupport.java
type: kg.aidarbek.smpp.codec.MessageTlvSupport
sha256: 69f4db1a241d3537dc0067a2327f872af986a2d80239638a42771c10f22e529d
responsibility: Composes the six established message occurrence/value policies and reusable message companion rules.
consumers: MessageCommandCodec validates message bodies; CommonTlvSupport reuses the same package-private companion contract.
S: pass | Only visibility of existing cohesive companion validation changed; direction/payload policy remains local to its caller.
O: pass | Explicit MessageTlvExtension codecs continue to extend exact contexts, and reuse avoids divergent SAR/callback implementations.
L: pass | Existing message optional/value tests pass with common callbacks/SAR/network fixtures after the visibility refactor.
I: pass | The package-private companion method exposes only raw/supported parameters, direction-independent flags and output mode needed by common codecs.
D: pass | Keeps pure profile/codec/protocol dependencies and no session or application execution.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/MessageTlvValueCodec.java
type: kg.aidarbek.smpp.codec.MessageTlvValueCodec
sha256: 49eb50c8169bd34c3d325a634688fb1434717c7f686b674e11bb6ac6bacee0db
responsibility: Validates standard raw TLV structure and returns explicit supported or ignored octet interpretations.
consumers: Typed message/common registries interpret registered tag values for the selected profile.
S: pass | Added ms_availability_status structural/value support stays within the same standard-value responsibility.
O: pass | Unsupported incoming values return empty interpretation; explicit vendor codec composition remains separate from standard ranges.
L: pass | Length-one availability accepts 0..2, rejects outgoing reserved values and retains incoming raw reserved bytes; all existing standard-value fixtures remain green.
I: pass | The TlvValueCodec interface supplies only type/decode/encode, with no text or provider-service methods.
D: pass | Depends on raw OctetString, explicit profiles and pure field checks without application or network infrastructure.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/codec/MultiFields.java
type: kg.aidarbek.smpp.codec.MultiFields
sha256: 5382fb6aab5b77aa975d82e5145f032fe33aa254abe95dea97b99adfaafc4ea0
responsibility: Reads and writes counted submit_multi alternatives and unsuccessful-destination layouts.
consumers: CommonCommandCodec delegates multi list grammar and standard field validation.
S: pass | List count/discriminator grammar is isolated from general dispatch and optional-parameter interpretation.
O: pass | The specification alternatives are closed and exhaustively switched; shared address/time contracts are reused.
L: pass | Count bounds precede list allocation, all fields are consumed, and independent full bytes plus truncation tests cover both profiles.
I: pass | Its private methods serve only the multi body adapter; no broader protocol facade is imposed.
D: pass | Depends on inward immutable protocol values and bounded field readers/writers.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/AlertSender.java
type: kg.aidarbek.smpp.endpoint.AlertSender
sha256: 532388fef6b3b590d0230af3115b1d7584c6b7a2717e32e941d02e341abd59cc
responsibility: Exposes the MC one-way alert sending capability and its explicit total write options.
consumers: BoundSession.alerts returns this focused facade; applications observe NotificationSend or cancel queued work.
S: pass | Delegates all role, profile, ownership and lifecycle enforcement to the single connection coordinator.
O: pass | SendRequirements and existing RequestOptions provide the intended variation without a transport-specific overload.
L: pass | Every retained facade rechecks live permission; null command/requirements and invalid fields cannot bypass encode validation.
I: pass | Only alert send overloads are present; no fake request response or message-storage methods are required.
D: pass | Depends on immutable request/protocol policy values and the internal coordinator, with no concrete socket knowledge.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/BoundSession.java
type: kg.aidarbek.smpp.endpoint.BoundSession
sha256: fbf73b8d37cc4860d21060fc75ffd41b39a4eb5e20078a419701b2794fe5a5cd
responsibility: Presents one bound connection generation and its current typed operation/control capabilities.
consumers: Client, server and reversed-outbind owners publish this facade through bounded notifications.
S: pass | Identity/lifecycle snapshots and capability delegation remain cohesive; request tracking and wire translation stay in collaborators.
O: pass | Operation descriptors expose new paired capabilities; alerts have a separate one-way facade rather than a fabricated response key.
L: pass | Retained senders recheck lifecycle and exact role/profile modes; existing binding, controls and protected termination APIs remain compatible.
I: pass | Applications obtain only available typed senders, plus common connection controls, without implementing a broad session interface.
D: pass | Depends on the coordinator and public immutable protocol/session/request contracts, without concrete transport or business services.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/CommonOperations.java
type: kg.aidarbek.smpp.endpoint.CommonOperations
sha256: a749b32d5d1fc1a55ad536fc19a776074e36d8185c2a312806333d333158e096
responsibility: Defines four typed request/response keys with codec factories, negative factories and contextual validators.
consumers: OperationCatalog, EndpointHandlers and BoundSession sender conveniences compose these immutable descriptors.
S: pass | Pairs query/cancel/replace/multi request and response types without executing application storage or networking.
O: pass | The existing Operation constructor and catalogue are the explicit extension seam; one-way commands remain outside paired tracking.
L: pass | Negative query responses echo the known request in 3.4; 5.0 omission and multi/error contracts are exercised on real endpoints.
I: pass | Each key describes exactly one typed operation and derives actual TLV requirements without forcing optional handlers.
D: pass | Depends on inward immutable protocol/profile data and pure codecs through Operation collaborators.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection
sha256: 82ee7a1872cd6ab5ab0998345a0c9d36a2d4c5db36c17b2f3a470bd1efabb166
responsibility: Serializes one connection lifecycle and composes typed operations, one-way work and explicit outbind over frame ports.
consumers: Ordinary and reversed endpoint owners attach the coordinator; BoundSession and internal frame/write callbacks advance it.
S: pass | Coordinates permissions, admission and lifecycle; MessageExchange, NotificationExchange, OutbindFlow and RequestWindow own their distinct retained work.
O: pass | Operation descriptors extend paired traffic; outbind configuration is explicit and bounded, while variable authentication remains supplied through focused ports.
L: pass | Preserves FrameListener and existing client/server semantics; both profiles, all supported modes, cancel/timeout/error/drain paths and off-I/O publication are tested.
I: pass | Consumes the small frame transport/listener/write observer contracts and optional handlers; applications never implement this internal coordinator.
D: pass | Has no concrete socket/transport dependency: MessageCenterBinding supplies policy, FrameTransport owns I/O, and immutable pure policies decide permissions.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection.OutbindWrite
sha256: 82ee7a1872cd6ab5ab0998345a0c9d36a2d4c5db36c17b2f3a470bd1efabb166
responsibility: Guards the single explicit MC outbind write before its follow-up bind may be accepted.
consumers: The reversed MC coordinator installs this observer without any pending response entry.
S: pass | Owns only physical-start permission and write failure closure; bind authentication is handled separately.
O: pass | The fixed one-way control flow reuses FrameTransport without adding a synthetic response operation or retry strategy.
L: pass | Expiry/closure refuses physical start, permitted start records sending before peer bind can race in, and terminal failure closes the attempt.
I: pass | Implements the small WriteObserver contract and performs no application callback.
D: pass | Depends on the coordinator deadline/state and frame-port failure abstraction, without concrete transport methods.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection.ReplyWrite
sha256: 82ee7a1872cd6ab5ab0998345a0c9d36a2d4c5db36c17b2f3a470bd1efabb166
responsibility: Settles internal bind/control replies and advances their connection lifecycle after physical output.
consumers: EndpointConnection.writeReply installs this observer for bounded control-class frames.
S: pass | Only pending-reply accounting and configured post-write lifecycle action are performed here.
O: pass | The supplied internal afterWrite action covers binding notification without exposing application work on I/O.
L: pass | Before-write refuses closed connections, failure closes, and successful callbacks retain existing crossed-unbind/bind reply contracts.
I: pass | Implements only the three fast internal WriteObserver methods required by transport.
D: pass | Depends on its coordinator and frame-port failure values; application work is dispatched through owned bounded notifications.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection.RequestWrite
sha256: 82ee7a1872cd6ab5ab0998345a0c9d36a2d4c5db36c17b2f3a470bd1efabb166
responsibility: Bridges paired-request transmission certainty and transport failure into RequestWindow.
consumers: Existing bind/enquiry/unbind and all paired application sends install this observer.
S: pass | The guard and write failure are correlation concerns; application result dispatch remains in RequestWindow.
O: pass | New paired operation descriptors reuse the same bridge with no per-command observer subclass.
L: pass | beginWrite atomically races terminal request cancellation; lifecycle failure paths close without changing request certainty.
I: pass | Implements only the internal guard/written/failed transport observer contract.
D: pass | Uses the independent RequestWindow abstraction and immutable request/transport outcomes, not socket APIs.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointHandlers.java
type: kg.aidarbek.smpp.endpoint.EndpointHandlers
sha256: 5bedd5b8760ae7b127d937ce873301798673f8af1934d886e150c00347cedee8
responsibility: Stores immutable optional typed paired handlers and the optional one-way alert hook.
consumers: MessageExchange and NotificationExchange read snapshots built during endpoint construction.
S: pass | Owns registration and type-safe invocation metadata without scheduling or executing handlers during lookup.
O: pass | Operation keys extend paired registration, while an alert hook can be absent independently of all paired services.
L: pass | Every build copies the map; duplicate/null alert registrations fail, and earlier snapshots cannot acquire later hooks.
I: pass | Applications implement only selected RequestHandler or NotificationHandler capabilities, not a mandatory all-command service.
D: pass | Depends on focused handler abstractions and immutable protocol contexts; worker and socket implementations remain external.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointHandlers.java
type: kg.aidarbek.smpp.endpoint.EndpointHandlers.Builder
sha256: 5bedd5b8760ae7b127d937ce873301798673f8af1934d886e150c00347cedee8
responsibility: Collects unshared typed registrations before producing immutable handler snapshots.
consumers: Endpoint configuration code registers implemented operations and at most one non-null alert hook.
S: pass | Only mutation and registration validation are builder responsibilities; runtime scheduling remains separate.
O: pass | New catalogue keys reuse on without reflection; one-way registration does not weaken paired type constraints.
L: pass | Duplicate/unsupported/null registrations reject predictably, and built snapshots remain independent of subsequent mutation.
I: pass | Two focused registration forms plus build avoid unrelated authentication or transport configuration methods.
D: pass | Depends on Operation/handler contracts and JDK maps, without I/O or execution services.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointHandlers.java
type: kg.aidarbek.smpp.endpoint.EndpointHandlers.Registration
sha256: 5bedd5b8760ae7b127d937ce873301798673f8af1934d886e150c00347cedee8
responsibility: Preserves the typed relation between an operation key and its registered paired handler.
consumers: MessageExchange invokes a selected registration with an immutable received request context.
S: pass | The single adapter performs checked type reconstruction; it does not select status policy or schedule application work.
O: pass | Generic operation types support further catalogue entries without a command-ID switch in invocation.
L: pass | The request class cast follows the key established at registration; Pdu status/sequence and cancellation identity are preserved.
I: pass | Only internal invoke is exposed, avoiding raw untyped application callback APIs.
D: pass | Depends on Operation, RequestHandler and immutable incoming context rather than worker or socket implementations.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointPdus.java
type: kg.aidarbek.smpp.endpoint.EndpointPdus
sha256: bee60a9862cb55185ae44c224b46065e6def8c4832743d3261a2ca1a4864ce20
responsibility: Composes bounded endpoint codecs and canonical malformed-request negative response shapes.
consumers: EndpointConnection encodes outbound commands and decodes frames using original request direction and effective profile.
S: pass | Wire composition and negative shape selection remain distinct from permissions, authentication and response correlation.
O: pass | Paired catalogue codec factories and explicit common one-way registrations extend one composition boundary without duplicate IDs.
L: pass | All common full-frame fixtures and endpoint tests retain profile/error rules; malformed typed queries use generic_nack because no valid original Q exists.
I: pass | The private coordinator needs only bounded encode/decode/header/control helpers, not individual codec details.
D: pass | Composes pure codec abstractions and immutable protocol/profile data, without concrete network or business dependencies.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/IncomingNotification.java
type: kg.aidarbek.smpp.endpoint.IncomingNotification
sha256: 2a9198481e6c584eb1ffc3e77d0f9508718fc6977a5b49a143e5437fe4c05ed1
responsibility: Exposes immutable notification identity/data with an observable local cancellation token and monotonic deadline.
consumers: Typed alert handlers inspect the Pdu, bound-session capability and cooperative cancellation state.
S: pass | Context storage does not decide peer acknowledgement, lifetime policy or authentication.
O: pass | The generic Command type supports focused notification handlers without an untyped payload object.
L: pass | Pdu/session/deadline identity is fixed; only the deliberately shared AtomicBoolean cancellation observation changes across threads.
I: pass | Four context accessors expose precisely the local application information; no response method exists.
D: pass | Depends on immutable protocol values and the public session facade, without executor or socket infrastructure.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/MessageCenterBinding.java
type: kg.aidarbek.smpp.endpoint.MessageCenterBinding
sha256: fb4fcbc7491c440672f37b5bfec7110a0b6432faea5fdbc5bc8fe5c0ec4bf537
responsibility: Stores the immutable MC binding policy shared by listening and explicitly connecting owners.
consumers: EndpointConnection consumes accepted/advertised versions and response system ID through this internal value.
S: pass | Separates protocol binding inputs from ServerConfig listen addresses and authentication pool sizes.
O: pass | Two validated owner configuration factories feed the same policy, avoiding role inference from TCP direction.
L: pass | Copies the version set; inputs originate from validated public configurations and preserve advertised/requested negotiation separation.
I: pass | Exposes only three coordinator-required fields and no artificial listen address for an MC connector.
D: pass | Depends on immutable profile values and copied JDK collections; the coordinator remains free of concrete network configuration.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/MessageExchange.java
type: kg.aidarbek.smpp.endpoint.MessageExchange
sha256: dd461bcb2035e5627126c8b6bb6a28093fa12046224243249b454be0f9902498
responsibility: Owns bounded retained incoming paired requests, asynchronous decisions and ordered encoded replies for one session.
consumers: EndpointConnection routes implemented operations here; NotificationExchange reuses the same dispatcher lane.
S: pass | Retained reply bytes/count and FIFO settlement remain local; permissions/codec context and global physical scheduling are delegated.
O: pass | Operation descriptors and focused handlers support common requests without duplicating queue/timeout/negative logic.
L: pass | Existing Step 12 matrix/failure/context tests plus common operations pass; exposing lane identity preserves invocation order across alert and paired hooks.
I: pass | The added package-private lane accessor exposes only the scheduling identity needed by the sibling one-way owner.
D: pass | Depends on HandlerDispatcher/Operation contracts, immutable PDUs and the coordinator boundary; no concrete transport or storage is embedded.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/MessageExchange.java
type: kg.aidarbek.smpp.endpoint.MessageExchange.Entry
sha256: dd461bcb2035e5627126c8b6bb6a28093fa12046224243249b454be0f9902498
responsibility: Retains one paired incoming decision, cancellation identity and bounded response bytes until write settlement.
consumers: MessageExchange expiry/completion/FIFO flush paths own the entry under the connection monitor.
S: pass | Only logical request/reply retention is stored; physical invocation/stage ownership remains in its dispatcher ticket.
O: pass | The Operation descriptor supplies type and negative behavior without per-command retained-entry subclasses.
L: pass | Cancellation and frame/byte ownership follow the existing deadline/count/byte/closure contracts tested in Step 12 and common operations.
I: pass | Private fields expose no application mutation or standalone lifecycle API.
D: pass | Depends on immutable Pdu/Operation data and the dispatcher ticket abstraction, without external infrastructure.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/MessageExchange.java
type: kg.aidarbek.smpp.endpoint.MessageExchange.ReplyWrite
sha256: dd461bcb2035e5627126c8b6bb6a28093fa12046224243249b454be0f9902498
responsibility: Settles one ordered application reply and releases its retained request/reply bytes.
consumers: MessageExchange.flush installs the observer through the coordinator ordinary-write boundary.
S: pass | Only queue/byte settlement and next-reply progress are handled; application result creation is separate.
O: pass | The same observer serves each typed operation descriptor with no common-command branches.
L: pass | Before-write requires live entry membership; successful settlement removes once and advances drain, while failure closes the connection.
I: pass | Implements only the internal WriteObserver methods, never an application future callback.
D: pass | Depends on its queue owner and the frame-port contract rather than concrete networking.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/NotificationExchange.java
type: kg.aidarbek.smpp.endpoint.NotificationExchange
sha256: dc621c8865b2936be5a4840e9bb1f8b6adccd44c6be4285482f8f5ee541e25d0
responsibility: Owns local one-way write outcomes and bounded incoming alert decisions without response tracking.
consumers: EndpointConnection composes it with shared notification permits, the handler dispatcher and the message invocation lane.
S: pass | One-way retention is separate from paired response queues and explicit outbind authentication, while lifecycle decisions remain coordinated.
O: pass | Optional NotificationHandler provides application variation; the fixed alert path reuses frame guards and dispatcher tickets rather than new transport loops.
L: pass | Queued cancel/guard races, callback backlog, expired guards, late stages before tick, handler retention and graceful drain are covered by real/controlled tests.
I: pass | Exposes only internal send/receive/expire/count/close operations needed by one connection; no fake paired-response API is added.
D: pass | Depends on FrameTransport observer/handle behavior, bounded notification reservations and handler abstractions; no sockets or application implementation are referenced.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/NotificationExchange.java
type: kg.aidarbek.smpp.endpoint.NotificationExchange.Incoming
sha256: dc621c8865b2936be5a4840e9bb1f8b6adccd44c6be4285482f8f5ee541e25d0
responsibility: Stores one logical alert deadline, shared cancellation token and physical handler ticket.
consumers: NotificationExchange completion, expiry and close update it under the connection monitor.
S: pass | Logical decision metadata does not own or prematurely release the separately retained invocation/stage.
O: pass | No per-alert or application subclasses are needed for this fixed retention contract.
L: pass | Atomic cancellation remains observable after timeout/close, and expired completion cannot beat an unexecuted periodic tick.
I: pass | Private storage exposes no response or application mutation interface.
D: pass | Depends only on the dispatcher ticket and AtomicBoolean, without transport or timing infrastructure ownership.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/NotificationExchange.java
type: kg.aidarbek.smpp.endpoint.NotificationExchange.Outgoing
sha256: dc621c8865b2936be5a4840e9bb1f8b6adccd44c6be4285482f8f5ee541e25d0
responsibility: Guards one unique local alert write and dispatches exactly one protected terminal observation.
consumers: FrameTransport invokes it; NotificationExchange cancellation, expiry and close share its membership guard.
S: pass | Only transmission-start identity and terminal callback ownership are retained; no response matching or delivery acceptance is inferred.
O: pass | The existing WriteObserver/WriteHandle ports allow the same state to work with real and controlled transports.
L: pass | The connection monitor makes successful queued cancellation exclude future guard success; callback capacity remains retained through application dependents.
I: pass | Implements the internal three-method observer and otherwise stays private to its owner.
D: pass | Depends on bounded callback reservation and frame-port abstractions, not concrete sockets or user callbacks on I/O.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/NotificationHandler.java
type: kg.aidarbek.smpp.endpoint.NotificationHandler
sha256: 80efbeff271d5746abf232882f358569d0d80bbacb6e4762b6154ed312f52fdf
responsibility: Defines the optional asynchronous one-way application handler contract.
consumers: EndpointHandlers registers alert implementations; NotificationExchange invokes them on bounded handler workers.
S: pass | The method reports local application completion and does not represent a peer acknowledgement.
O: pass | Applications provide concurrent-session-safe behavior through this functional interface independently of the wire codec.
L: pass | Implementations must return a non-null stage; failed/null/late outcomes are bounded and close rather than inventing a response.
I: pass | One typed method avoids mandatory request, authentication or delivery callbacks for notification-only services.
D: pass | The library depends on this application abstraction plus immutable IncomingNotification context, not a concrete handler.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/NotificationSend.java
type: kg.aidarbek.smpp.endpoint.NotificationSend
sha256: cb0e17a5f7e248ba2e6a479ae411cf845463c8dcb961defed2c69c19c5fe0a0b
responsibility: Provides read-only local write outcome, unique sequence and queued cancellation capability.
consumers: AlertSender returns this value after accepted local admission and protected callback reservation.
S: pass | Observation/cancellation are local transmission concerns, deliberately excluding peer acceptance and response correlation.
O: pass | A supplied internal BooleanSupplier encapsulates cancellation without exposing transport queue internals.
L: pass | The minimal CompletionStage protects internal completion; cancellation after a claimed or terminal write is false and sequence identity never changes.
I: pass | Three small accessors cover one-way consumers without RequestHandle response methods.
D: pass | Depends on JDK observation/function abstractions, with no codec, thread or socket implementation.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/OperationCatalog.java
type: kg.aidarbek.smpp.endpoint.OperationCatalog
sha256: 55d8489605076d3bfd13afdd48f736f70ccc95217ebbf6438186c5a9303a3220
responsibility: Owns the immutable list and exact-ID lookup of locally implemented paired endpoint operations.
consumers: Handler registration and EndpointPdus composition share this single registration boundary.
S: pass | Registration is separate from wire formats, request correlation and business handlers.
O: pass | CommonOperations is appended once beside MessageOperations; new paired operations need no socket dispatcher rewrite.
L: pass | The immutable list contains stable, unique library keys; one-way identities are intentionally not exposed as paired operations.
I: pass | Only all and find are exposed to internal consumers that require catalogue information.
D: pass | Depends on operation descriptors inside endpoint composition, without transport or application service implementation.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/OutbindAuthenticator.java
type: kg.aidarbek.smpp.endpoint.OutbindAuthenticator
sha256: 804765f568659d03276d22644750d340bbba1099f03c09f866c38b85213bce98
responsibility: Defines MC credential authentication before an ESME sends its fixed configured bind.
consumers: OutbindListener requires this service and invokes it through OutbindFlow on bounded handler workers.
S: pass | The contract returns only an authentication decision; it does not select addresses, modes, retries or open connections.
O: pass | Applications supply provider-specific credential policy without modifying notification codecs or reversed endpoint owners.
L: pass | A true result is actionable only within the total bind deadline; false/null/failure/late results cannot cause credential transmission or resurrection.
I: pass | One asynchronous method accepts immutable Outbind and peer identity without requiring message services.
D: pass | The endpoint depends on this focused application abstraction rather than concrete credential storage or networking.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/OutbindConnector.java
type: kg.aidarbek.smpp.endpoint.OutbindConnector
sha256: f4ec2b6578a844081d9df4fe2401060009f2a41820460de57ae903f5c9f8288c
responsibility: Owns bounded MC TCP attempts that send one outbind and authenticate one follow-up bind on the same socket.
consumers: Applications explicitly invoke connectAttempt and observe ConnectionAttempt, session snapshots and endpoint cleanup.
S: pass | Composition owns sockets/resources, while EndpointConnection and OutbindFlow own protocol state and authentication sequencing.
O: pass | BindAuthenticator and ExchangeConfig provide application policy; automatic retries are absent and each attempt is explicit.
L: pass | Resolved-address and outgoing-wire preflight precede reservation/connect; cancellation, rejected bind status, cleanup and all supported profile/mode flows are tested.
I: pass | The MC connector exposes only attempt/session/count/shutdown/termination ownership methods, without a fabricated listen address.
D: pass | Concrete TcpTransport construction stays at this composition boundary; protocol work consumes frame ports and focused authenticators.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/OutbindConnectorConfig.java
type: kg.aidarbek.smpp.endpoint.OutbindConnectorConfig
sha256: 5811f436a7100b1cba048c760e432be70c08e7d614ce856a51a1616fab752972
responsibility: Stores immutable MC version, response identity and bounded bind-authentication policy.
consumers: OutbindConnector composes this policy for each explicitly requested outgoing connection.
S: pass | Version/authentication configuration is separate from target addressing and initiating attempts.
O: pass | Authentication implementation varies at the endpoint hook; fixed policy remains an immutable value.
L: pass | Copied accepted versions resist caller mutation; invalid advertisement/ranges/identifier lengths fail consistently with MC bind policy.
I: pass | No unused listener address is imposed on connector users.
D: pass | Depends on pure negotiation and immutable response values, without network/resource acquisition.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/OutbindFlow.java
type: kg.aidarbek.smpp.endpoint.OutbindFlow
sha256: 0db7fe8bd6a50618fba603e75f565c02923845a55ac8db65b931d0b2bc8a544e
responsibility: Retains exactly one configured MC notification or one ESME MC-authentication decision before normal binding.
consumers: Reversed EndpointConnection factories install it and invoke connected/receive/close under the coordinator lifecycle.
S: pass | The helper owns duplicate detection and physical authentication ticket retention, while state/codec/write ownership remains delegated.
O: pass | OutbindAuthenticator is the variable business boundary; the implementation intentionally has no retry or automatic reconnect extension.
L: pass | Duplicate input closes, late or cancelled results cannot bind, and non-cooperating stages remain physically retained after logical timeout.
I: pass | Only three lifecycle operations and narrow internal phase metadata are exposed to its coordinator.
D: pass | Depends on the handler abstraction/dispatcher and immutable notification, without sockets or message-store implementation.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/OutbindListener.java
type: kg.aidarbek.smpp.endpoint.OutbindListener
sha256: 5f8e7793b9d4683f828f8cfdd746375a1ac803202d68752d69c49a07045f4b4b
responsibility: Owns the ESME TCP listener that authenticates MC outbind before sending a configured same-socket bind.
consumers: Applications configure fixed credentials/mode, supply MC authentication and observe bounded bound-session callbacks.
S: pass | Composition owns listener/admission/cleanup; the coordinator and flow own protocol states and bounded asynchronous authentication.
O: pass | OutbindAuthenticator and optional handlers vary application behavior; bind configuration is explicit and no connection loop is inferred.
L: pass | Start preflights outgoing bind before opening; shutdown permanently stops admission; duplicate/auth-timeout/rejection/cancellation cases and physical retention are tested.
I: pass | Listener lifecycle and bound session snapshots are separate from the MC connector attempt API and paired message senders.
D: pass | Constructs concrete listener/transport policy only at the endpoint boundary, passing the coordinator a frame port and validated client policy.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/OutbindListenerConfig.java
type: kg.aidarbek.smpp.endpoint.OutbindListenerConfig
sha256: d073b15688dd7993ed8de61e0af73310f70d980ab2837ac92f5bbb856fedf1e1
responsibility: Stores resolved ESME listener configuration and the exact authenticated follow-up bind.
consumers: OutbindListener and configuration callers choose address, version, mode and advertisement policy.
S: pass | Only immutable configuration validation and redaction are present, with no socket or authentication work.
O: pass | All specified 5.0 modes are explicit; 3.4 receiver-only policy follows its primary outbind contract.
L: pass | Record equality retains fixed BindRequest data; unresolved/unknown-version and invalid legacy modes fail before resource acquisition.
I: pass | Listener callers provide no MC authentication/version fields irrelevant to the ESME direction.
D: pass | Uses immutable bind/address values without starting DNS, sockets or workers.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/profile/CommonTlvRules.java
type: kg.aidarbek.smpp.profile.CommonTlvRules
sha256: 3076b52b6cc4dd23bb5933c497e7546e4d6b9047c8879176ad2be4981fe98435
responsibility: Records exact optional-parameter tag contexts for ten common commands in both profiles.
consumers: CommonTlvSupport and standalone callers obtain immutable permitted sets.
S: pass | Only catalogue occurrence membership changes this class; body/status/value policies stay elsewhere.
O: pass | Known specification tables are explicit; vendor permission belongs to configured composition, not table mutation.
L: pass | Independent 20-context sets prove that 3.4 restrictions and explicit dest_subaddress exclusions are preserved.
I: pass | Only permitted-tag lookup is exposed; callers are not forced to use codec/session state.
D: pass | Depends on profile values and immutable JDK sets, with no wire parser or service.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/AlertNotification.java
type: kg.aidarbek.smpp.protocol.AlertNotification
sha256: e5cea3fdb70a79d93d51e3f7b719c7242bf0c487d2209f76913309d1853daa10
responsibility: Stores the immutable source, ESME and ordered raw availability parameters for a one-way PDU.
consumers: Common codecs and alert applications inspect the address and raw parameter accessors.
S: pass | Only alert wire-value invariants drive this record; authentication and delivery actions are absent.
O: pass | The fixed command layout stays closed; explicit raw parameters carry extension data without extra service methods.
L: pass | Command identity is always 0x102 and immutable record equality includes all raw fields.
I: pass | The three accessors expose only alert data and impose no response contract.
D: pass | Depends only on immutable protocol values and Objects, with no codec or endpoint policy.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/CancelSm.java
type: kg.aidarbek.smpp.protocol.CancelSm
sha256: b269b6dde59d99f890683b2ca084a8a000312b4303ffc1a594760b952cec99ab
responsibility: Stores an immutable message cancellation selector.
consumers: The common codec serializes it; management handlers perform the actual store lookup.
S: pass | Identifier representation is separate from cancellation matching and message persistence.
O: pass | A fixed wire selector needs no provider subclass; raw optional data remains explicit.
L: pass | Command ID 8 and value equality remain stable, including an empty group-selection ID.
I: pass | Its accessors expose only cancellation selection fields.
D: pass | String and Address values contain no storage or network implementation dependency.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/CancelSmResponse.java
type: kg.aidarbek.smpp.protocol.CancelSmResponse
sha256: d3426e45d3f15d67810bafdc1eb082afce29a977228f81e6176af0d4992371e3
responsibility: Carries cancellation response optional parameters independently of envelope status.
consumers: Common codecs and typed cancellation handlers compose the response with a Pdu.
S: pass | The response value only requires non-null immutable parameters.
O: pass | Status and profile omission rules can evolve in the codec without adding record behavior.
L: pass | Command identity 0x80000008 and parameter equality preserve the Command contract.
I: pass | No request selectors or unrelated message payload methods are imposed on response consumers.
D: pass | Depends on immutable protocol storage; cancellation services stay external.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/MultiDestination.java
type: kg.aidarbek.smpp.protocol.MultiDestination
sha256: ad9744e77c849a23147acc981bd86daddb7f23bd95b9783d792c28b254df97bf
responsibility: Defines the two explicit alternatives in the counted submit_multi destination grammar.
consumers: MultiFields exhaustively discriminates SME and distribution-list alternatives.
S: pass | The sealed sum describes only destination representation, not expansion or routing.
O: pass | The two specification discriminators are intentionally closed; a new wire layout requires explicit codec work.
L: pass | Both permitted immutable alternatives satisfy destination identity without pretending interchangeable field layouts.
I: pass | No common address/name accessor forces an alternative to invent an unsupported value.
D: pass | The type depends only on its immutable protocol alternatives.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/MultiDestination.java
type: kg.aidarbek.smpp.protocol.MultiDestination.DistributionList
sha256: ad9744e77c849a23147acc981bd86daddb7f23bd95b9783d792c28b254df97bf
responsibility: Stores the bounded ASCII distribution-list name for discriminator 2.
consumers: MultiFields writes the name; application handlers perform expansion.
S: pass | Name representation and redaction are cohesive; no expansion state is retained.
O: pass | Resolver variation stays in handlers, while the fixed wire name remains closed.
L: pass | Implements MultiDestination with immutable name equality and a valid C-octet representation.
I: pass | Exposes only its list name and does not fabricate an SME address.
D: pass | No directory, storage or endpoint dependency is present.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/MultiDestination.java
type: kg.aidarbek.smpp.protocol.MultiDestination.Sme
sha256: ad9744e77c849a23147acc981bd86daddb7f23bd95b9783d792c28b254df97bf
responsibility: Stores the immutable SME-address alternative for discriminator 1.
consumers: MultiFields serializes its address and application handlers target that SME.
S: pass | Null representation validation is separate from numeric profile/address-length policy.
O: pass | A final address alternative needs no vendor subclass; Address retains raw octets explicitly.
L: pass | Implements MultiDestination with stable immutable record equality and a non-null Address.
I: pass | Exposes only the address needed by the SME alternative.
D: pass | No distribution-list resolver or network service is embedded.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/Outbind.java
type: kg.aidarbek.smpp.protocol.Outbind
sha256: aa1283b40916ea2e4484590eb791db195f501652569dbb118e156d27d655b24d
responsibility: Stores a bounded immutable MC identifier and password for an outbind notification.
consumers: The common codec and explicit outbind authentication workflow consume these credentials.
S: pass | Credential representation and redacted diagnostics are cohesive; no connection attempt is initiated.
O: pass | The fixed notification remains a value; authenticators are supplied separately at the endpoint.
L: pass | Command ID 0x0b is stable and record equality includes credentials despite redacted toString.
I: pass | There is no invented response or binding-mode accessor on this notification.
D: pass | Only immutable strings and raw parameters are retained, without sockets or authentication policy.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/QuerySm.java
type: kg.aidarbek.smpp.protocol.QuerySm
sha256: 37a5240cf3f33614712697637b16558209092cd1e236193091aaba1bc11f8760
responsibility: Stores immutable queried-message identity and originating address.
consumers: Common codecs and query application handlers consume the selector.
S: pass | Representation validation does not look up or mutate message lifecycle state.
O: pass | Application-specific lookup extends the handler boundary, not this final value.
L: pass | Command ID 3 and ordinary immutable record equality hold for every constructed value.
I: pass | The query exposes only identity, source and raw parameters.
D: pass | No message storage, time service or networking implementation is referenced.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/QuerySmResponse.java
type: kg.aidarbek.smpp.protocol.QuerySmResponse
sha256: 32d6802ad233cc6eb493c54100f6fc94eea418e4dc24a38a254d5ce1841b9c2c
responsibility: Distinguishes a present query result from an omitted standard response body.
consumers: Body codecs and typed query handlers use presence together with envelope status/profile.
S: pass | The record enforces only body/optional-parameter consistency and immutable ownership.
O: pass | Version-specific body rules remain in the codec; raw state fields are preserved for compatibility.
L: pass | Absent body cannot contain TLVs; command ID 0x80000003 and value equality are stable.
I: pass | Response consumers are not forced to supply query request or session metadata.
D: pass | Depends only on protocol result data and Optional, without tracking or clocks.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/QuerySmResponse.java
type: kg.aidarbek.smpp.protocol.QuerySmResponse.Result
sha256: 32d6802ad233cc6eb493c54100f6fc94eea418e4dc24a38a254d5ce1841b9c2c
responsibility: Stores raw queried-message identity, completion-time string, state and error octets.
consumers: Query response codecs validate grammar/profile, and applications interpret known lifecycle states.
S: pass | Only ASCII/octet representations are validated; no completion time is invented.
O: pass | Reserved incoming state octets can be retained without adding subclasses or enum fallbacks.
L: pass | Immutable equality retains the exact date/state/error values; redaction does not change equality.
I: pass | The four accessors match the standard result layout without requiring application services.
D: pass | Time remains a raw protocol string rather than a dependency on calendars or wall-clock services.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/ReplaceSm.java
type: kg.aidarbek.smpp.protocol.ReplaceSm
sha256: a2f921ee355569c270d6b481254569375692426f0405736d8979690f07b46b00
responsibility: Stores immutable replacement identity, timing flags and raw payload fields.
consumers: Common codecs and replacement handlers use the fields to modify a stored message explicitly.
S: pass | Constructor checks representation and ownership; profile semantics and persistence stay separate.
O: pass | Raw TLVs carry the 5.0 payload alternative without adding encoding or storage methods.
L: pass | Command ID 7 and immutable octet ownership satisfy Command and record equality contracts.
I: pass | No data-coding field or submission destination is invented for the replacement layout.
D: pass | Depends on immutable protocol values and local validation, without helpers, clocks or transport.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/ReplaceSmResponse.java
type: kg.aidarbek.smpp.protocol.ReplaceSmResponse
sha256: 5987856f8fd62b65c07c14489e51c10ed20725b78d9e296657f1c92b1dbd6549
responsibility: Carries replacement response optional parameters independently of envelope status.
consumers: Common codecs and typed replacement handlers compose the response with a Pdu.
S: pass | Only immutable response storage is enforced; applying a replacement is external.
O: pass | Profile-specific response rules remain in codecs without subclassing this fixed record.
L: pass | Command identity 0x80000007 and parameter equality remain stable.
I: pass | Response consumers need only optional parameters and command identity.
D: pass | The record contains no message-store, dispatcher or transport collaborator.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/SubmitMulti.java
type: kg.aidarbek.smpp.protocol.SubmitMulti
sha256: a75437d4de1c8d891c690bfa7f918caf78249f55265a521b907fd6d31d07faff
responsibility: Stores ordered immutable multiple-destination submission fields and raw payload.
consumers: Common codecs serialize the list; handlers expand distribution names and submit recipients.
S: pass | Count/representation ownership is separate from expansion, routing and delivery.
O: pass | The fixed grammar uses explicit sealed alternatives and raw extensions, not provider inheritance.
L: pass | List bounds precede copying, caller mutation cannot change equality, and command ID 0x21 is stable.
I: pass | Consumers receive exactly the multi-submission fields rather than a fabricated single destination.
D: pass | Only immutable protocol fields and copied JDK collections are retained.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/SubmitMultiResponse.java
type: kg.aidarbek.smpp.protocol.SubmitMultiResponse
sha256: 8cd139373886570a17f549fc57103165c08288d1db4f6cacb4a2f15d06726384
responsibility: Represents present or omitted multiple-submission standard result and ordered parameters.
consumers: Common codecs and typed multi handlers compose it with overall command status.
S: pass | Body-presence consistency is independent of per-destination application decisions.
O: pass | Status/profile omission evolves in the codec without changing the record to a service.
L: pass | Absent result excludes TLVs, and ID 0x80000021 plus immutable equality remain stable.
I: pass | The response exposes only its optional result and parameters.
D: pass | Has no dependency on expansion caches, correlation or endpoint resources.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/SubmitMultiResponse.java
type: kg.aidarbek.smpp.protocol.SubmitMultiResponse.Result
sha256: 8cd139373886570a17f549fc57103165c08288d1db4f6cacb4a2f15d06726384
responsibility: Stores an MC identity and copied ordered per-destination result list.
consumers: Multiple-submission response codecs and applications report unsuccessful expanded SMEs.
S: pass | Bounds and ownership are local; no unsupported membership or expansion rule is inferred.
O: pass | Application expansion policies vary outside this fixed result representation.
L: pass | At most 255 entries are copied before exposure; list order and raw statuses participate in equality.
I: pass | The result does not demand source, payload or request data irrelevant to its wire layout.
D: pass | Uses immutable protocol failures and JDK values, with no application message store.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/protocol/UnsuccessfulDestination.java
type: kg.aidarbek.smpp.protocol.UnsuccessfulDestination
sha256: 3966031e7aa7c354e694b64c67846c03c747510309138fdbff5790f8d91c8fd8
responsibility: Stores an immutable SME address and unsigned 32-bit status result.
consumers: Multi response codecs encode it and callers inspect expanded-destination outcomes.
S: pass | Only representation invariants are checked; no provider failure policy or list membership is inferred.
O: pass | Unsigned raw status preserves new values without requiring a subclass or enum expansion.
L: pass | Immutable Address and raw status have stable record equality over the full uint32 range.
I: pass | Two accessors match exactly one unsuccessful-SME wire entry.
D: pass | No request tracking or application message-store implementation is referenced.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/request/RequestWindow.java
type: kg.aidarbek.smpp.request.RequestWindow
sha256: d2fcdd6f159446bab3c94e541b1531a29dc858196d7fe8dfe3c49e4da2956b27
responsibility: Owns bounded local request correlation and one non-reused connection sequence stream.
consumers: Endpoint senders admit paired requests or allocate untracked one-way identities, then drive write/response/deadline transitions.
S: pass | The added allocator shares sequence ownership only; it creates no notification/request reservation and performs no wire or role work.
O: pass | Existing response representations remain generic; the small explicit one-way catalogue does not alter correlation or add fake response types.
L: pass | Full request suite and new interleaving/exhaustion/nack tests preserve atomic settlement, certainty, capacity and AutoCloseable behavior.
I: pass | Callers needing one-way identity need no fake RequestHandle; paired-request APIs remain unchanged.
D: pass | A monotonic clock and bounded notification collaborator remain explicit; no transport, endpoint policy or text dependency was introduced.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/CommonBodyContractTest.java
type: kg.aidarbek.smpp.codec.CommonBodyContractTest
sha256: 1d239bea5f8aaf55919b3bf6f8e553ef487b40b3dce13334cff14359f598acea
responsibility: Checks direct CommandCodec ownership/context/bounds and malformed mandatory bodies for all ten IDs.
consumers: Parameterized body verification uses real registered codecs, independent body strings and bounded raw tails.
S: pass | The stated observable protocol contract is this test type's only responsibility; it does not supply production business policy.
O: pass | Specification-derived tables add profile/operation cases without a test-only production switch.
L: pass | Real immutable values and codec/window contracts are exercised; Object identity and JUnit invocation contracts remain unchanged.
I: pass | JUnit methods and private fixture functions expose only the scenario assertions and bounded construction needed here.
D: pass | Tests consume focused protocol/profile/codec/request APIs and independent expected bytes, without external services or runtime dependencies.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/CommonCommandCodecsTest.java
type: kg.aidarbek.smpp.codec.CommonCommandCodecsTest
sha256: 16427541b80b0b33f83657c7145e1b08beb2d4aa8a79f95b1ed232e420218d8d
responsibility: Checks independently specified complete success/failure wire frames for every common command in both profiles.
consumers: Real PduCodec consumers are compared with literal specification-derived bytes, not round trips alone.
S: pass | The stated observable protocol contract is this test type's only responsibility; it does not supply production business policy.
O: pass | Specification-derived tables add profile/operation cases without a test-only production switch.
L: pass | Real immutable values and codec/window contracts are exercised; Object identity and JUnit invocation contracts remain unchanged.
I: pass | JUnit methods and private fixture functions expose only the scenario assertions and bounded construction needed here.
D: pass | Tests consume focused protocol/profile/codec/request APIs and independent expected bytes, without external services or runtime dependencies.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/CommonExtensionTest.java
type: kg.aidarbek.smpp.codec.CommonExtensionTest
sha256: 26d75ce70cc36d51af06bba1a9162086e47a48f0c4ae91d42680327f7e0d7208
responsibility: Checks explicit vendor permission isolation, immutable registration and invalid context rejection.
consumers: Uses an existing ownership-compliant value codec through the real extension composition.
S: pass | The stated observable protocol contract is this test type's only responsibility; it does not supply production business policy.
O: pass | Specification-derived tables add profile/operation cases without a test-only production switch.
L: pass | Real immutable values and codec/window contracts are exercised; Object identity and JUnit invocation contracts remain unchanged.
I: pass | JUnit methods and private fixture functions expose only the scenario assertions and bounded construction needed here.
D: pass | Tests consume focused protocol/profile/codec/request APIs and independent expected bytes, without external services or runtime dependencies.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/CommonMessageValidationTest.java
type: kg.aidarbek.smpp.codec.CommonMessageValidationTest
sha256: 35d3a3529b902e49984485e0057bc2be936e5a3373668fcf23692689ba58d8ee
responsibility: Checks profile-specific payload/count/flag/time/body-presence and availability-value contracts.
consumers: Actual immutable values and independently constructed malformed incoming frames exercise both profiles.
S: pass | The stated observable protocol contract is this test type's only responsibility; it does not supply production business policy.
O: pass | Specification-derived tables add profile/operation cases without a test-only production switch.
L: pass | Real immutable values and codec/window contracts are exercised; Object identity and JUnit invocation contracts remain unchanged.
I: pass | JUnit methods and private fixture functions expose only the scenario assertions and bounded construction needed here.
D: pass | Tests consume focused protocol/profile/codec/request APIs and independent expected bytes, without external services or runtime dependencies.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/CommonResponseRulesTest.java
type: kg.aidarbek.smpp.codec.CommonResponseRulesTest
sha256: 0f64aa8cbbdd4ef67e30513cafc7f6bb9713cd3f2fbf3d27f5e4c6ee037af6d4
responsibility: Checks query response identity and multi transaction diagnostic context.
consumers: Original and corresponding/contradictory PDUs exercise the public validator without trackers or sessions.
S: pass | The stated observable protocol contract is this test type's only responsibility; it does not supply production business policy.
O: pass | Specification-derived tables add profile/operation cases without a test-only production switch.
L: pass | Real immutable values and codec/window contracts are exercised; Object identity and JUnit invocation contracts remain unchanged.
I: pass | JUnit methods and private fixture functions expose only the scenario assertions and bounded construction needed here.
D: pass | Tests consume focused protocol/profile/codec/request APIs and independent expected bytes, without external services or runtime dependencies.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/CommonTlvContractsTest.java
type: kg.aidarbek.smpp.codec.CommonTlvContractsTest
sha256: 47301fef81bcb20455eb2ebf858465fb9b48f79f5d648be40ceb736cbd81b811
responsibility: Checks exact operation/profile tables and callback/SAR/network/raw-compatibility semantics.
consumers: Independent tag sets and hex values exercise real common codecs and the shared standard value interpretation.
S: pass | The stated observable protocol contract is this test type's only responsibility; it does not supply production business policy.
O: pass | Specification-derived tables add profile/operation cases without a test-only production switch.
L: pass | Real immutable values and codec/window contracts are exercised; Object identity and JUnit invocation contracts remain unchanged.
I: pass | JUnit methods and private fixture functions expose only the scenario assertions and bounded construction needed here.
D: pass | Tests consume focused protocol/profile/codec/request APIs and independent expected bytes, without external services or runtime dependencies.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/codec/CommonValidationTest.java
type: kg.aidarbek.smpp.codec.CommonValidationTest
sha256: eb9b6841bbcdba1a9377ac0f085172b2a00ba25abe5d40552c16ca465fe042b1
responsibility: Checks query error-body, final-time/state/address and unknown/congestion behavior.
consumers: Literal incoming frames and public encode/decode paths establish the compatibility boundary.
S: pass | The stated observable protocol contract is this test type's only responsibility; it does not supply production business policy.
O: pass | Specification-derived tables add profile/operation cases without a test-only production switch.
L: pass | Real immutable values and codec/window contracts are exercised; Object identity and JUnit invocation contracts remain unchanged.
I: pass | JUnit methods and private fixture functions expose only the scenario assertions and bounded construction needed here.
D: pass | Tests consume focused protocol/profile/codec/request APIs and independent expected bytes, without external services or runtime dependencies.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/AlertEndpointTest.java
type: kg.aidarbek.smpp.endpoint.AlertEndpointTest
sha256: fc4c64451fdb0711aba457d0a8f401ae219383d95add974122f1a793cb77df7e
responsibility: Checks typed one-way alert delivery and local completion through real loopback peers in both profiles.
consumers: Public MC/ESME endpoints and an independent RawPeer verify wire identity, handler thread and the absence of a paired response.
S: pass | The type tests alert interoperability; it does not implement request storage, pacing or server policy.
O: pass | Profile iteration supplies two versions through the same focused scenario without production test branches.
L: pass | Uses actual endpoint contracts and bounded real peers; local completion succeeds without any invented alert response and enquiry sequences remain distinct.
I: pass | Only scenario methods and existing bounded fixture helpers are required by JUnit.
D: pass | Depends on public endpoint/protocol values and a raw socket fixture; no external server or transport internals are assumed.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/AlertHandlerContractTest.java
type: kg.aidarbek.smpp.endpoint.AlertHandlerContractTest
sha256: 7cab213be83326b1c09d1752ef8b935abbd11d02c0c0360cac76cfecd030688b
responsibility: Checks shared invocation ordering, timeout retention, late completion and optional alert registration.
consumers: Real endpoints plus a controlled clock/port distinguish application work from control progress and physical cleanup.
S: pass | Handler cancellation/ordering is isolated from wire-byte correctness, already covered by codec tests.
O: pass | Configurable dispatcher limits and supplied handler stages express adversarial timing without new production branches.
L: pass | The controlled-clock red24 proves deadline enforcement even before tick; blocked stages retain physical capacity and same-lane message/alert invocation ordering is observable.
I: pass | JUnit scenarios use only focused handler/context and owned cleanup operations; no broad fake application interface is introduced.
D: pass | Depends on handler/session/port boundaries and bounded local peers rather than real external services or timing-sensitive provider behavior.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/AlertWriteContractTest.java
type: kg.aidarbek.smpp.endpoint.AlertWriteContractTest
sha256: 5e8bbce24b86f945f9f45b4f16e83f496cd6843f0e463b6c24ce30018a327697
responsibility: Checks one-way queued cancel, guard races, deadline, callback capacity and graceful drain ownership.
consumers: A bound real coordinator runs over CommonTestTransport; the same port ownership contract runs on the real TCP adapter elsewhere.
S: pass | Tests local transmission/notification settlement without fabricating peer acceptance or pending alert responses.
O: pass | Explicit guard progression and bounded latch gates cover race orderings without a production clock/transport test hook.
L: pass | Observed red17 corrected expired guard classification; red25 corrected fixture cancellation after concurrent claim; protected completion and drain tests verify all accepted writes settle once.
I: pass | Private fixture and observer helpers expose only operations needed to assert write outcomes and resource release.
D: pass | Depends on the frame-port contract, real coordinator and bounded notification owner, without a replacement request tracker or network dependency.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/AlertWriteContractTest.java
type: kg.aidarbek.smpp.endpoint.AlertWriteContractTest.Fixture
sha256: 5e8bbce24b86f945f9f45b4f16e83f496cd6843f0e463b6c24ce30018a327697
responsibility: Owns one bound MC coordinator, controlled transport, bounded callbacks and authentication for alert-write tests.
consumers: AlertWriteContractTest creates and closes a fresh fixture per scenario.
S: pass | Setup/cleanup and typed session acquisition are its only responsibilities; test assertions remain in scenario methods.
O: pass | Existing public/internal configuration ports provide variation without exposing new production options.
L: pass | Every accepted bind is completed before tests proceed, and close releases transport/authentication/callback ownership within finite waits.
I: pass | AutoCloseable exposes one cleanup operation; fixture fields remain test-local rather than a general endpoint facade.
D: pass | Composes the frame port and bounded authentication/notification collaborators, not concrete production sockets.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/AlertWriteContractTest.java
type: kg.aidarbek.smpp.endpoint.AlertWriteContractTest.GuardObserver
sha256: 5e8bbce24b86f945f9f45b4f16e83f496cd6843f0e463b6c24ce30018a327697
responsibility: Observes physical fixture writes and deliberately failing guards/terminal callbacks for deterministic port contract regressions.
consumers: Cycles25/28 use its counters and explicit fault flags to distinguish physical completion, cancellation and cleanup failure.
S: pass | Only internal guard/terminal observations and explicitly configured failures are supplied; no request or session policy is implemented.
O: pass | The existing WriteObserver abstraction and two explicit fault flags cover adversarial callbacks without a fake class hierarchy.
L: pass | Default callbacks are fast and count exact terminal outcomes; configured guard/failed exceptions verify the transport handles documented observer failure without abandoning other writes.
I: pass | Implements exactly the three internal observer methods and exposes no application callback API.
D: pass | Depends only on frame-port failure values and a primitive test counter.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/CommonOperationsEndpointTest.java
type: kg.aidarbek.smpp.endpoint.CommonOperationsEndpointTest
sha256: 0a152d70bee733bb9e5995be055040c18b145b800fe01cf5fb472c9873b9daf5
responsibility: Checks typed common request/reply services and current sending capabilities across both profiles and bind modes.
consumers: Real public endpoints exercise all four paired operations, negative missing-service replies and retained sender closure.
S: pass | The type validates endpoint composition rather than implementing application storage or wire parsing.
O: pass | A finite profile/mode/operation matrix reuses focused fixtures and descriptors, with no production switch for tests.
L: pass | Query echo, multi per-destination results, exact capability absence and profile-correct negatives verify the new descriptors preserve existing sender/handler contracts.
I: pass | Scenario helpers create only bounded values/endpoints needed by related common-operation and alert tests.
D: pass | Depends on public typed capabilities and local real peers instead of request-window or concrete transport mutation.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/CommonTestTransport.java
type: kg.aidarbek.smpp.endpoint.CommonTestTransport
sha256: d8d4ceeaee63e683cb192d53419f52253d2538b54ac54cd56e47ebe434846417
responsibility: Provides a bounded controlled frame port whose tests explicitly separate guard claim and write settlement.
consumers: Alert/coordinator regressions drive queued cancellation, expiry guards, drain, closure and callback capacity against this substitute.
S: pass | Only deterministic port progression and owned-frame observation are implemented; application/session policy stays in real coordinators.
O: pass | Controlled deferred output and a bounded race gate enable adversarial interleavings without adding production hooks.
L: pass | Shared FrameTransportContract and cycles25/28 cover atomic guard/cancel exclusion plus throwing guard/terminal observer cleanup; first failure is retained, remaining writes drain, and termination waits for all internal callbacks outside the monitor.
I: pass | Implements only FrameTransport with package-local test driving methods; ordinary and control count/byte capacities remain separate and finite.
D: pass | Depends on frame-port abstractions and JDK bounded collections rather than TcpTransport implementation; expected headers are independently read with ByteBuffer.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/CommonTestTransport.java
type: kg.aidarbek.smpp.endpoint.CommonTestTransport.Pending
sha256: d8d4ceeaee63e683cb192d53419f52253d2538b54ac54cd56e47ebe434846417
responsibility: Stores one fixture-owned accepted frame, write class, observer and guard-claim state.
consumers: CommonTestTransport cancellation, explicit claim, settlement and close own this private entry.
S: pass | Only accepted-write ownership metadata is retained until the terminal callback.
O: pass | A single entry shape serves ordinary/control queues without test per-command subclasses.
L: pass | Frame arrays are copied; cancellation is rechecked atomically with removal and cannot report success after guard claim.
I: pass | Private storage exposes no application or independent resource API.
D: pass | Depends only on frame-port observer/class types and owned bytes, not endpoint or real socket implementation.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/OutbindConfigTest.java
type: kg.aidarbek.smpp.endpoint.OutbindConfigTest
sha256: 60968978cf1e09da3d8400ba28c850a0d49a7d57f85e8a1efb5ed7a57cb70ce3
responsibility: Checks immutable outbind policies, outgoing-bind preflight and permanent shutdown admission closure.
consumers: Public listener/connector configurations and finite listener lifecycle calls expose validation before resource acquisition.
S: pass | Configuration/ownership validation is separated from authentication and on-wire flow tests.
O: pass | Profile/mode variants and invalid boundaries reuse real constructors without production test-only hooks.
L: pass | Observed reds13,20 and21 cover copied sets, unsupported address/mode, preflight before listening and rejection of start after shutdown; credential diagnostics remain redacted.
I: pass | JUnit needs only public configuration/lifecycle APIs and bounded cleanup, without full application handlers.
D: pass | Depends on immutable values and explicit endpoint abstractions; no DNS lookup or external socket service is required.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/OutbindEndpointTest.java
type: kg.aidarbek.smpp.endpoint.OutbindEndpointTest
sha256: d4dc5806d7f34dc7f4a3b4fbd96175a780c4ff5194f3ad5c177b20538dac4ed2
responsibility: Checks same-socket explicit outbind and successful binding for every supported profile/mode combination.
consumers: Real reversed MC/ESME owners authenticate immutable credentials and exchange enquiry traffic after binding.
S: pass | The test covers positive reversed connection workflow independently of lifecycle fault scenarios.
O: pass | Iterates 3.4 RX and 5.0 RX/TX/TRX through one public API without implicit role assumptions.
L: pass | Both authentications occur exactly once, negotiated profiles and peer address remain correct, and application authentication runs off I/O.
I: pass | Only focused bound callbacks/authenticators are supplied; no mandatory message-handler implementation is needed.
D: pass | Uses public endpoint and protocol contracts over local TCP peers, without coordinator or request-map access.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/OutbindLifecycleTest.java
type: kg.aidarbek.smpp.endpoint.OutbindLifecycleTest
sha256: bcd30b0861ef483f84dd47a2aff21846a1d2ce7d4b0a0c24afdc20cb9c640962
responsibility: Checks rejected bind status, idle/auth gating, duplicates, deadlines, cancellation and 3.4 receiver restriction.
consumers: Independent bounded RawPeer frames and explicit reversed owners expose both notification and bind lifecycle outcomes.
S: pass | Fault workflow assertions remain separate from binary codecs and ordinary message traffic.
O: pass | Supplied stages, raw headers and finite options express failure orderings without retry or production fault flags.
L: pass | Red19 preserved rejected status; no bind credentials precede MC acceptance, no late stage resurrects state, non-receiver 3.4 binds bypass authentication, and no automatic reconnect occurs.
I: pass | Uses focused authenticator callbacks and finite owner/peer helpers instead of a broad fake MC service.
D: pass | Depends on public reversed endpoint APIs and independently encoded local socket frames, with no external infrastructure.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/protocol/CommonValuesTest.java
type: kg.aidarbek.smpp.protocol.CommonValuesTest
sha256: da0159754fa84ffacf45a937b766187a71679789ab3b279b1af1872b1b522f97
responsibility: Checks common protocol representation bounds, list/octet ownership and redacted diagnostics.
consumers: Immutable value constructors and caller mutation establish observable ownership without codec assumptions.
S: pass | The stated observable protocol contract is this test type's only responsibility; it does not supply production business policy.
O: pass | Specification-derived tables add profile/operation cases without a test-only production switch.
L: pass | Real immutable values and codec/window contracts are exercised; Object identity and JUnit invocation contracts remain unchanged.
I: pass | JUnit methods and private fixture functions expose only the scenario assertions and bounded construction needed here.
D: pass | Tests consume focused protocol/profile/codec/request APIs and independent expected bytes, without external services or runtime dependencies.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/request/NotificationSequenceTest.java
type: kg.aidarbek.smpp.request.NotificationSequenceTest
sha256: 3579023ee01eb8020447f38b9b7abcfc1b3f4b3501cad94b698d055ba6f63cd1
responsibility: Checks shared one-way/request sequence identity without pending or callback reservations.
consumers: Real bounded request windows prove interleaving, generic_nack isolation, exhaustion and closed admission.
S: pass | The stated observable protocol contract is this test type's only responsibility; it does not supply production business policy.
O: pass | Specification-derived tables add profile/operation cases without a test-only production switch.
L: pass | Real immutable values and codec/window contracts are exercised; Object identity and JUnit invocation contracts remain unchanged.
I: pass | JUnit methods and private fixture functions expose only the scenario assertions and bounded construction needed here.
D: pass | Tests consume focused protocol/profile/codec/request APIs and independent expected bytes, without external services or runtime dependencies.
findings: none
```

## Main-checkout integration

Integration after `cde5556` (Step 12) and `be7f4e5` (Step 13) verified all 64
owned-file SHA-256 values before copying the four separately reviewed simulator
integration sources. `./gradlew check build :simulator:installDist
:simulator:javadoc solidReviewInventory dependencies --configuration
runtimeClasspath --console=plain` passed. The 615 library cases and 47 simulator
cases reused matching shared-cache outputs; the 60 review-tool cases were up to
date. Formatting, fresh inventory and coverage checks executed; all 357 current
type identities match whole-file reviews. Library runtime dependencies remain
empty, all three library archives contain exact Apache LICENSE/NOTICE and only
production contents, and the simulator distribution has exactly its tool JAR,
the library and licensed HdrHistogram 2.2.2. All 383 local links across 44 Markdown
documents resolved before this final integration note.

Four additional fresh installed-artifact JVM checks ran `CommonOperationSmoke`
for alert/outbind under 3.4/5.0 with `-Xms256m -Xmx512m`. Each observed one
notification, two bound sessions and complete cleanup; alert authenticated once,
outbind authenticated both stages. Exact argument vectors, binary fingerprints
and results are retained locally in `build/runs/step14-finite.json`. These finite
runs verify executable behavior without a workload capacity claim.
