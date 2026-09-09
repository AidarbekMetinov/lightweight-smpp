# Review: TLS, keepalive and explicit reconnect lifecycle

## Scope and contracts

Implementation began at `e0f0f9c` in the isolated Step 17 worktree. The final
delta is relative to committed Step 16, `eee5142`; its broadcast/protocol files
were copied unchanged and its small `BoundSession`/`EndpointConnection`
congestion additions were merged without replacing the lifecycle work. Root
owns both architecture files and their eight reviewed identities. They are
validation prerequisites, excluded from this owner delta.

The owned inventory has 55 identities in 40 Java files. The blocks below cover
the complete current types, including unchanged methods and all nested types
in edited files, rather than only the textual diff. There are no owned
anonymous/local Java types or temporary Java probes outside this inventory.
PEM/PKCS12 test material is deliberately public development-only material.
It has no library default loader and introduces no runtime dependency.

The implementation places TLS setup, handshake, decrypted framing and physical
socket cleanup in `TcpTransport`. SMPP policies and request completion still
use the existing state machine, operation descriptors and single request
window. Keepalive adds one ordinary correlated request through that window;
reconnect adds a bounded sequence of connection attempts and never owns or
copies a message request. Endpoint composition selects security by TCP origin,
including both reversed outbind owners. Application handlers and observers stay
on their existing bounded dispatchers.

The review checked three distinct lifetime boundaries: readiness versus
physical socket termination; logical handler cancellation versus physical
invocation/stage return; and request settlement versus completion-dependent
return. Reconnect uses a private permit-retirement stage and reserves its
terminal notification. Its ready observer cannot be overtaken by the next
generation, and a blocked result dependent continues to consume global
notification capacity. Cleanup errors stop replacement and remain observable.

`SessionResources` is explicitly sampled ownership data. It does not claim
transport-buffer measurements, exact cross-layer atomicity or global handler
counts. `TlsConfig` has immutable policy fields but retains caller-owned
cryptographic configuration. The review corrected its documentation to avoid
claiming that external `SSLContext`/trust-manager state is deeply immutable.

## Sequential TDD evidence

All Gradle commands used `--console=plain
-Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=step17'`; no cache directories were
deleted or normal cache behavior disabled. The selections below name executable
Jupiter classes/methods; each log records the executed tasks and observed
failure. Library selections used `:test --tests 'kg.aidarbek.smpp.…'` and adapter
selections `:simulator:test --tests 'kg.aidarbek.simulator.…'`.

| Logs under `/tmp/step17-` | Test-first behavior and executed result |
| --- | --- |
| `02-tls-client-red` / `03-tls-client-green` | `TlsTransportTest.trustedClientExchangesFramesOnlyAfterHandshake`: real JSSE peer rejected plaintext; transport-owned TLS made frame exchange pass with TCP regressions. |
| `04-tls-identity-red` / `05-tls-identity-green` | `TlsTransportTest.trustAndExpectedIdentityAreBothRequired`: wrong host failed to close and untrusted negotiation had the wrong local kind; both now close before connected with TLS failure. |
| `06-tls-deadline-red` / `07-tls-deadline-green` | `TlsTransportTest.stalledHandshakeOwnsAnIndependentDeadlineAndNeverStartsQueuedFrames`: ignored handshake bound timed out the test observer; independent deadline now settles queued writes without starting their guards. |
| `08-tls-server-red` / `09-tls-server-green` | `TlsTransportTest.acceptedTlsTransportAuthenticatesAndExchangesWithAnIndependentClient`: raw TLS client saw EOF from a plain adopted transport; accepted TLS exchange now passes. |
| `10b-tls-validation-red` / `11-tls-validation-green` | `TlsConfigTest`: invalid identity/configuration and connecting/listening role mismatch were accepted; validation now rejects before socket/worker ownership. |
| `12b-tls-endpoints-red` / `13c-tls-endpoints-green` | `TlsEndpointsTest`: endpoint constructors ignored TLS and let credentials reach the wrong boundary; both endpoint integration cases pass with existing bind tests. |
| `14-tls-outbind-red` / `15-tls-outbind-green` | `TlsBindingMatrixTest`: untrusted reversed TLS was incorrectly allowed to bind; explicit origin-based policy now passes all valid mode/profile/origin cases. |
| `16-tls-endpoint-deadline-red` / `17-tls-endpoint-deadline-green` | `TlsEndpointDeadlineTest`: the outer TCP timer killed a valid delayed handshake, while an accepted bind budget ignored pre-TLS time; distinct initiating and accepted budgets now pass. |
| `18-keepalive-idle-red` / `19-keepalive-idle-green` | `KeepaliveTest.idleDeadlineSendsOneEnquiryAndMatchingResponseRestartsTheInterval`: exact fake-clock idle threshold produced no enquiry; one correlated enquiry and interval reset now pass. |
| `20-keepalive-failure-red` / `21-keepalive-failure-green` | `KeepaliveTest.failedAutomaticEnquiryClosesWithItsOriginalOutcome`: timeout, negative and nack left the session bound; each now closes with the original outcome. |
| `22-keepalive-bounds-red` / `23-keepalive-bounds-green` | `KeepaliveTest`: full-window exceptions escaped and ordinary saturation left no heartbeat reserve; finite admission deferral and CONTROL transport priority now pass. |
| `24b-keepalive-owners-red` / `25-keepalive-owners-green` | `KeepaliveEndpointsTest`: all four actual endpoint owners omitted the policy; raw-peer matching/missing heartbeat cases now pass. |
| `26-attempt-cleanup-refactor-green` | Existing endpoint/resource suites remained green while a private physical permit-retirement stage was added. This is a behavior-preserving prerequisite refactor, not a fabricated behavioral red. |
| `27-reconnect-generations-red` / `28-reconnect-generations-green` | `ReconnectEndpointsTest`: new controller was not registered with the existing timer; actual peer accept timed out. Registered ownership now creates two fresh namespaces and supports cancellation. |
| `29-reconnect-callout-refactor-green` | Reconnect/resource suites stayed green after moving coordinator queries and close callouts outside the reconnect monitor. |
| `30-reconnect-terminal-race-red` / `31-reconnect-terminal-race-green` | `ReconnectControlTest.losingCancellationCannotAbortAnAlreadySelectedGracefulEndpointStop`: losing cancel aborted a bound session; only the winning cancellation now closes it. |
| `32-reconnect-factory-red` / `33-reconnect-factory-green` | `ReconnectControlTest.factoryFailureConsumesTheFiniteAttemptWithoutEscapingTheEndpointTimer`: internal factory failure escaped tick; it now consumes a finite attempt and preserves the cause. |
| `35-reconnect-outbind-red` / `36-reconnect-outbind-green` | `ReconnectEndpointsTest.explicitOutbindReconnectRepeatsOnlyConnectionAuthenticationOnFreshSockets`: unregistered reversed controller never connected; the same bounded controller mechanism now handles outbind. |
| `38-session-resources-red` / `39-session-resources-green` | `SessionResourcesTest`: placeholder zero observations missed a live request; actual request/reply reservations now remain counted through active response writes and clear on settlement. |
| `40-lifecycle-settings-red` / `41-lifecycle-settings-green` | `LifecycleSettingsTest`: optional policies were ignored and contradictions accepted; finite parsing and explicit context creation now satisfy the adapter contract. |
| `43-tls-setup-red` / `44-tls-setup-green` | `TlsProviderFailureTest`: provider setup I/O and argument failures were classified READ_FAILED/MALFORMED_FRAME; both now preserve TLS_HANDSHAKE_FAILED and their original cause, with raw cleanup. |
| `47-simulator-tls-boundary-red` / `51-simulator-tls-boundary-green` | Real `LifecycleSettings` called public TlsConfig factories rejected by the existing simulator rule. Root allowed precisely that identity; both architecture tests execute green and the forbidden RequestWindow probe remains effective. |
| `55-control-reply-cause-red` / `56-control-reply-cause-green` | `KeepaliveTest.failedControlReplyPreservesTheWinningLocalTransportCause`: immediate and guarded reply-write failures produced empty close reasons. Both failure paths now preserve the winning transport failure before closing. |
| `59-lifecycle-report-red` / `60-lifecycle-report-green` | `LifecycleSettingsTest.reportsRetainExactNonsecretMaterialLocationsAndEnvironmentReferences`: standalone reports omitted configured key/trust paths and environment-variable references. Exact nonsecret fields are now retained while resolved password values remain absent; the real adapter TLS cases still pass. |

Executed green characterizations add material contracts without inventing
implementation regressions: `34-reconnect-retention-replay-characterization`
tests an actually transmitted ambiguous submission, blocked observer retention
and throwing observer; `37-reconnect-exact-saturation-characterization` fills all
six notification permits; `42b-lifecycle-tls-characterization` runs real adapter
TLS pairs under both profiles and missing/wrong-secret rejection;
`52-tls-shared-contract` runs the unchanged shared frame-port contract for both
TLS roles. The final suites additionally cover cancellation during authentication
with late acceptance, automatic CONTROL saturation, manual CONTROL priority,
mutual TLS, expiry before listener installation, close during handshake,
an 8 MiB nonreading TLS peer and blocked handler invocation/returned-stage
cleanup while bidirectional enquiries succeed.

Fixture/build corrections are kept distinct from behavioral evidence. Log 01
selected unqualified `test`, which also selected the simulator; subsequent
library selections use `:test`. Original log 12 had overlong fixture credentials,
fixed before 12b. Original log 13 incorrectly expected an empty TLS rejection
stream; JSSE can send a fatal alert. An alert-aware fixture was fault-probed
against temporarily disabled TLS in 13b and then passed in 13c. Logs 24 and 46
were strict compiler warnings about fixture cleanup declarations, fixed without
production changes. Log 42 had an incorrect authenticator lambda arity. In 46b,
a rejected mutual handshake raised raw Broken pipe rather than SSLException;
the fixture now accepts either IOException while checking the precise server
failure and absence of readiness. These are not protocol implementation reds.

Log 45 revealed three old saturation assertions whose fixtures used manual
enquiries as ordinary writes. Since manual enquiries now intentionally use the
CONTROL reserve, those fixtures use `query_sm` and retain their original
ordinary/control capacity and reply-deadline assertions. Logs 46c/48 also caught
an invalid fixture assumption that force-closing at the exact end of a consumed
grace period must already report zero physical connections. The explicit-abort
cleanup proofs now abort before the observation interval; log 49 passes with
the exact retained callback/handler assertions intact. Production shutdown still
reports honest incomplete snapshots at its original bound.

## Verification and type review

Final formatting is an explicit separate task. The current source hashes below
come from the subsequent JDK-parser inventory. Root's
[lifecycle architecture report](0016-lifecycle-architecture.md) covers transport
TLS allow-list probes and continued rejection of endpoint policy in transport
and Socket/SSLContext in the coordinator. Its
[simulator architecture report](0016-simulator-architecture.md) covers the exact
public TlsConfig exception and retained second-request-window rejection.

The complete behavior/Javadoc/inventory checkpoint in log 54 executed 736
library cases and 66 simulator cases with zero failures, errors or skips, and
both Javadoc tasks passed. The final two control-cause regressions were added
after that checkpoint. The final owner `check build javadoc` passed in 1m24s
(`/tmp/step17-63-final-check-build.log`): 24 actionable tasks, 13 executed,
5 FROM-CACHE and 6 UP-TO-DATE, with configuration caching stored. Its XML
contains 738 library, 67 simulator and 60 review-tool cases, all successful.

Root integrated `:test :simulator:test javadoc :simulator:installDist
solidReviewInventory` passed in 1m29s, with 738 library/architecture cases in
111 test classes and 66 simulator cases in 22 classes, zero failures, errors or
skips (`/tmp/lightweight-smpp-step17-integration-behavior.log`). After the final
nonsecret adapter-report correction and separate formatting, root
`check build solidReviewInventory :simulator:installDist` passed in 1m23s
(`/tmp/lightweight-smpp-step17-integration-final.log`): 25 actionable tasks,
10 executed, 2 FROM-CACHE and 13 UP-TO-DATE, with the configuration cache reused.
The library and 60 review-tool cases were unchanged and UP-TO-DATE in that
last command; all 67 simulator cases executed freshly, including the final
nonsecret-report regression added after the 66-case integration checkpoint.

The current inventory and successful coverage output match exactly: 447 Java
identities, all at their current whole-source SHA-256. All three library
archives contain exact LICENSE/NOTICE files and the expected production-only
contents. The installed simulator contains exactly its own JAR, the library
and HdrHistogram 2.2.2, with project and dependency licensing. Document validation
checked 56 Markdown files and 462 local links; `git diff --check` was clean.
Fifteen independent Cloudhopper comparison cases also executed successfully
against the final library binary outside this repository; the complete peer
configuration and final candidate replay are recorded in Step 19.

No unresolved design or ownership finding remains in the reviewed types. The
documented limits remain: supplied cryptographic context/manager state is caller
owned; application callbacks cannot be forcibly terminated; resource samples
are diagnostic; finite tests do not establish a production capacity rating.
## Current type evidence

```solid-review
source: simulator/src/main/java/kg/aidarbek/simulator/LifecycleSettings.java
type: kg.aidarbek.simulator.LifecycleSettings
sha256: 7d1ddc610ef37da10a1f29a5982623905c788a1bd7de43bf2b69dc0424aa594d
responsibility: Parses and constructs simulator lifecycle configuration without retaining credential values.
consumers: Step18 CLI composition uses optionNames/parse/create/nonSecretReport/reconnectPolicy; adapter tests supply a finite environment lookup.
S: pass | The tool configuration boundary owns finite option validation, PKCS12 loading and nonsecret reporting; none enters protocol dispatch or the library.
O: pass | Public ConnectionLifecycle and JDK key/trust factories are the composition seams; command traffic and request correlation need no TLS-specific branches.
L: pass | Map.copyOf protects retained settings and unrelated options are dropped; missing/wrong secrets reject before endpoints exist and temporary password arrays are erased in finally. Context ownership is explicit.
I: pass | CLI validation/reporting and endpoint construction get small separate methods; no network callbacks or internal engine handles are exposed.
D: pass | Uses public endpoint/TlsConfig values and JDK SSL/keystore APIs; exact simulator architecture allowance remains limited to public TlsConfig. Logs40/41 and42b exercise the boundary.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/LifecycleSettingsTest.java
type: kg.aidarbek.simulator.LifecycleSettingsTest
sha256: 023cf93bfd87e3b9fb9a052b6367062c656a5527c14da760dd7d338ede8b099a
responsibility: Verifies finite option parsing and nonsecret reproducibility of the simulator lifecycle adapter.
consumers: Jupiter exercises public-policy construction and reports without allocating endpoints for invalid input.
S: pass | Cases focus on lifecycle configuration, contradictions, bounds and exact material/env references; protocol traffic belongs to LifecycleTlsTest.
O: pass | The same adapter seam accepts future CLI composition without changing these policy assertions; unrelated option filtering is explicitly covered.
L: pass | Fresh immutable input maps and direct exceptions isolate each case; the environment is not mutated and no real secret is retained. Tests assert actual policies and selected report fields rather than duplicating parser implementation.
I: pass | No custom test interface is introduced; Jupiter invokes focused methods and standard assertions.
D: pass | Depends on adapter/public policy values and JUnit; no internal request engine or live network fixture is needed.
findings: none
```

```solid-review
source: simulator/src/test/java/kg/aidarbek/simulator/LifecycleTlsTest.java
type: kg.aidarbek.simulator.LifecycleTlsTest
sha256: 520791660942d054b9a1d9aad25558ed70f1a1e4f7a6e932141dd35eb46f5e31
responsibility: Exercises simulator-created TLS contexts through actual public endpoint traffic and secret-loading failures.
consumers: Jupiter runs both profile variants against ephemeral local listeners and development-only PKCS12 material.
S: pass | The test owns only adapter-to-endpoint TLS composition; broad simulator workloads remain in their separate process tests.
O: pass | Public endpoint constructors and immutable settings are the extension seams; no library-private helper or request window is imported.
L: pass | Every connection/bind/control wait is finite and finally closes both owners with cleanup assertions. Missing/wrong environment secrets reject initialization, reports retain references without password values, and both versions exchange real enquiry/unbind.
I: pass | Only public readiness/control/termination APIs are consumed; the fixture needs no application message handler interface.
D: pass | Uses JDK resource paths, the adapter and public endpoint types; ephemeral ports and explicit test credentials avoid host/application configuration dependencies.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/BoundSession.java
type: kg.aidarbek.smpp.endpoint.BoundSession
sha256: 9c105f146788e1b834579386ee7c653b28f02709caabd09fd899ccd350a58e44
responsibility: Exposes currently permitted operations and lifecycle observation for one bound generation.
consumers: Applications, handlers, reconnect observers and simulator use focused senders, controls, negotiation, closeReason and sampled resource accessors.
S: pass | Every method describes or delegates one session capability; no reconnect loop, socket factory or credential loading is embedded in the facade.
O: pass | Operation descriptors preserve existing submit/deliver/data/common/broadcast extension behavior; lifecycle observations append without changing operation execution.
L: pass | Thread-safe delegate checks preserve role/profile restrictions, original failure identity and idempotent close. Snapshot records cannot mutate ownership and document nontransactional counters; SessionResourcesTest proves active-write retention.
I: pass | Optional specific senders avoid unsupported-method stubs; observing resources or closeReason requires neither send capability nor request-engine access.
D: pass | Depends on endpoint coordinator and immutable public protocol/session/request values; transport selection stays in the four owner factories.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/ConnectionAttempt.java
type: kg.aidarbek.smpp.endpoint.ConnectionAttempt
sha256: 8290f43d7f29a031672c3c7e4afb041ff2c94b513369ec81221e1fe6e81b48a5
responsibility: Separates pre-bind cancellation and readiness observation from internal physical retirement.
consumers: Client/outbind callers use result/cancel; ReconnectHandle alone consumes package-private connection/rejection/cleanup access.
S: pass | This is one attempt ownership token; it does not schedule retries, correlate requests or own an executor.
O: pass | Client and reversed connector provide the same attempt contract, letting reconnect reuse their established binding workflows.
L: pass | Derived result mutation cannot cancel the operation, readiness-winning cancel returns false, and cleanup is not published to application dependents on the I/O completion path. Existing cancellation suites and26 verify unchanged behavior.
I: pass | Public callers get only result and explicit cancellation; physical permit retirement is intentionally internal.
D: pass | Stores CompletionStage values and a coordinator reference; no concrete socket/transport or application executor is exposed.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/ConnectionLifecycle.java
type: kg.aidarbek.smpp.endpoint.ConnectionLifecycle
sha256: 45da4aeb025a8f18f740291627b7df4caa9c0713f76b83580498f3603ce87f9a
responsibility: Carries optional transport security and idle-enquiry configuration for endpoint composition.
consumers: All four endpoint constructors validate origin and obtain total startup allowance; callers compose defaults/withTls/withKeepalive.
S: pass | One value captures opt-in connection policy; reconnect attempt ownership remains a separate explicit handle/policy.
O: pass | Old constructors delegate to defaults; transport settings are passed only at composition and do not widen pure session policy dependencies.
L: pass | Optional containers are nonnull, durations are representable and role mismatches fail before owned workers. Copy methods return new values; retained TLS context mutability is explicitly caller-owned.
I: pass | A caller can select TLS, keepalive, both or neither without supplying an unused implementation.
D: pass | Depends only on focused KeepalivePolicy, TlsConfig, EndpointOptions and JDK values; no I/O occurs in the record. TLS matrix/deadline tests exercise the composed budgets.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection
sha256: 2f5e0b94a88dccb5f9a35afc5f7e12e32b2c8cf7166953541c0de2c4f164a91c
responsibility: Coordinates one generation of SMPP state, request/reply ownership and connection progress over the frame port.
consumers: Owner factories, BoundSession, internal frame/write observers, existing exchanges and endpoint timer call its lifecycle/permission methods.
S: pass | TLS setup stays out; new startup metadata and one heartbeat extend connection progress, while RequestWindow, state machine, message/notification exchanges and auth dispatch retain their existing separate duties.
O: pass | OperationCatalog still extends paired commands and transport remains replaceable through FrameTransport; security choices arrive as primitive timing metadata and keepalive policy.
L: pass | Retains both role/version namespaces, caller invocation deadlines, guarded transmission certainty, matched response checks and atomic close. Keepalive uses the same window, stops on shutdown, retains original failure, and defers admission only within the original due budget. All existing endpoint suites plus new raw-peer, TLS and saturation tests pass.
I: pass | Package-only coordination methods serve narrow facade/exchange/owner consumers; no socket/context, pending-window handle or application-executor API leaks through the public session.
D: pass | Concrete networking and TLS imports remain absent; FrameTransport is the only I/O port. Root negative probes still reject Socket/SSLContext and concrete adapters. Internal callbacks under the monitor contain no SMPP application handler invocation.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection.OutbindWrite
sha256: 2f5e0b94a88dccb5f9a35afc5f7e12e32b2c8cf7166953541c0de2c4f164a91c
responsibility: Bridges the one MC outbind write to its existing bind-deadline and sending guard.
consumers: FrameTransport invokes beforeWrite/written/failed; OutbindFlow and coordinator observe only internal sending state.
S: pass | Only pre-bind one-way transmission ownership is represented; a successful write has no response-window completion to perform.
O: pass | Reversed TLS and reconnect reuse this observer without additional socket or retry logic.
L: pass | Guard refuses expired/closed outbind and failed writes close with the actual cause. The empty written method is intentional because subsequent bind, not local write, decides readiness; outbind/TLS/reconnect tests cover this distinction.
I: pass | Implements exactly the WriteObserver port contract and never invents a response handle for outbind.
D: pass | Uses enclosing coordinator/outbind state and SPI values, without any concrete network dependency or application notification.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection.ReplyWrite
sha256: 2f5e0b94a88dccb5f9a35afc5f7e12e32b2c8cf7166953541c0de2c4f164a91c
responsibility: Settles an internal control reply and advances its queued lifecycle action.
consumers: The port invokes the three write callbacks; writeReply supplies only private lifecycle continuations.
S: pass | Counts successful control reply settlement and closure; it does not complete outbound request results or invoke a public message handler.
O: pass | All bind/unbind/enquiry/negative control replies share the bridge, including encrypted ports, without command-specific transport branches.
L: pass | Guard rejects a closed session, success releases pending reply ownership once through the port contract, and both immediate/guarded failure paths now preserve TransportFailure before close. Logs55/56 prove the corrected cause and existing unbind races remain covered.
I: pass | Only the internal WriteObserver interface is implemented; afterWrite is a private coordination continuation, not an application callback.
D: pass | Depends on enclosing state and the SPI observer/failure contract; TLS and socket mechanics remain below the port.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java
type: kg.aidarbek.smpp.endpoint.EndpointConnection.RequestWrite
sha256: 2f5e0b94a88dccb5f9a35afc5f7e12e32b2c8cf7166953541c0de2c4f164a91c
responsibility: Bridges a correlated request to its atomic transmission guard and write-failure settlement.
consumers: FrameTransport invokes it for binding, controls and all paired message operations; RequestWindow owns outcome selection.
S: pass | The observer handles only the transition to possible transmission and local write failure; successful local output does not pretend to be peer acceptance.
O: pass | New automatic enquiries and existing ordinary/control requests use the identical bridge; no second request engine or resend path is introduced.
L: pass | beginWrite keeps deadline/cancel precedence and preserves NOT_SENT versus MAY_HAVE_BEEN_SENT. Write failures use the winning terminal outcome before lifecycle close; empty written intentionally waits for the actual peer response.
I: pass | Implements the small internal write port rather than requiring application request-result callbacks on I/O.
D: pass | Depends on RequestWindow/RequestHandle and SPI failure values; public completion notifications retain the existing bounded dispatcher.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointException.java
type: kg.aidarbek.smpp.endpoint.EndpointException
sha256: 589fb84dc38cd80fcd8ae9112ab5de8b6b3e0a79cec91ba88daf9a94d19e1e0e
responsibility: Carries credential-free endpoint outcome metadata distinct from request and transport failures.
consumers: Connection factories, keepalive/reconnect, applications and tests inspect reason/session/status/advertisement.
S: pass | Adds only the negative automatic-enquiry category; message results, transport causes and retry decisions retain separate types.
O: pass | Enum growth represents a new endpoint outcome without changing status preservation or serialization behavior.
L: pass | RuntimeException serialization still retains primitive metadata/UUID; optional unsigned wire fields retain their prior meaning and no generated message includes credentials. Negative keepalive tests inspect status/category.
I: pass | Consumers read structured metadata without parsing exception text or gaining mutation of session state.
D: pass | Only JDK values and its nested outcome enum are required; the exception imports no infrastructure.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointException.java
type: kg.aidarbek.smpp.endpoint.EndpointException.Reason
sha256: 589fb84dc38cd80fcd8ae9112ab5de8b6b3e0a79cec91ba88daf9a94d19e1e0e
responsibility: Names endpoint-level connection and binding outcomes including negative keepalive response.
consumers: EndpointException and application diagnostics select/inspect the finite category.
S: pass | The vocabulary describes local endpoint decisions; it does not encode retransmission or provider implementation policy.
O: pass | KEEPALIVE_REJECTED is appended as an explicit outcome while request certainty and transport kinds stay in their own domains.
L: pass | Enum identity/serialization semantics are preserved; existing categories keep their meaning and none guarantees peer message rejection or retry safety.
I: pass | A consumer can switch on a single reason without implementing unused behavior.
D: pass | Dependency-free enum; transport and protocol classes do not need to depend on endpoint diagnostics.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointResources.java
type: kg.aidarbek.smpp.endpoint.EndpointResources
sha256: 2d3ac470481fed880e4ae1c42c7ae3bcfa36feeafb3fd76689159e2750fac794
responsibility: Owns globally bounded connection/controller admission, shared workers, timer and shutdown observation.
consumers: Four endpoint owners reserve/attach transports, register reconnect controllers and request graceful or abortive shutdown.
S: pass | Shared lifetime accounting is the change driver; protocol state and I/O remain per-connection/transport responsibilities.
O: pass | Reconnect reuses the existing timer, physical permits and bounded notifier instead of creating a new thread pool or queue; the factory seam covers both initiating owners.
L: pass | No replacement releases capacity merely because a callback times out; physical retirement is separate. Shutdown stops new controllers, keeps existing callbacks accounted, and returns an honest bounded snapshot including cleanup errors. Exact six-permit saturation and blocked-handler/observer tests cover retention.
I: pass | Package-only owner methods expose reservations and observations, not internal executor management to public sessions.
D: pass | Uses coordinator abstractions, bounded dispatchers and JDK scheduling/locks; concrete TCP/TLS selection remains in client/server composition. Snapshot callouts avoid holding its admission lock across coordinator entry.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/EndpointResources.java
type: kg.aidarbek.smpp.endpoint.EndpointResources.Permit
sha256: 2d3ac470481fed880e4ae1c42c7ae3bcfa36feeafb3fd76689159e2750fac794
responsibility: Tracks one reserved connection slot through construction, attachment and physical retirement.
consumers: Owner factories attach/retire once; reconnect internally observes its protected retirement stage.
S: pass | Only slot ownership and physical cleanup outcome determine this token; no readiness or request completion is stored here.
O: pass | The same permit covers normal, TLS, failed-construction and reversed outbind transport ownership.
L: pass | Double attach/retire is rejected, release is idempotent, and only successful physical cleanup frees the slot. Retirement completion occurs after unlocking; failed cleanup is retained and stops replacements rather than concealing ownership.
I: pass | Its stage is package-private so application dependents cannot block the physical-cleanup publication path.
D: pass | Uses its enclosing endpoint accounting lock and CompletionStage, independent of socket implementation. Construction/cleanup and reconnect retirement tests verify the contract.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/KeepalivePolicy.java
type: kg.aidarbek.smpp.endpoint.KeepalivePolicy
sha256: cdbac8fa715e74af8700755de7ea3320cd531c4d054ae09c494c476abfa3ab1d
responsibility: Defines idle duration and the one automatic enquiry response budget.
consumers: ConnectionLifecycle stores it; coordinator timer uses its two durations; simulator maps explicit options.
S: pass | A validated timing value holds no scheduler, reconnect counter or application message.
O: pass | Omission disables the feature and existing request/deadline mechanisms execute it; no artificial timing-service extension is needed.
L: pass | Both durations use existing positive monotonic-range validation and immutable Duration equality. Fake-clock tests prove exact idle threshold, original budget under saturation and one outstanding request.
I: pass | The two required values are sufficient; callers do not implement a heartbeat callback or reserve another request window.
D: pass | Only JDK Duration and existing endpoint duration validation are dependencies.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/MessageExchange.java
type: kg.aidarbek.smpp.endpoint.MessageExchange
sha256: aa32a0ac2f803b39af1206f629481a56ad6d07bc45e01bcd7af2cf4122e8097c
responsibility: Owns one session paired application decisions and bounded ordered reply reservations.
consumers: Coordinator receive/expiry/shutdown paths and its new resource sampler consume pending count and retained bytes.
S: pass | The only Step17 addition observes the existing retained-byte total; handler selection, FIFO replies and physical dispatcher ownership retain their focused responsibilities.
O: pass | Operation descriptors still supply request/response validation and negative factories across ordinary/common/broadcast operations; TLS needs no exchange branch.
L: pass | All guarded reply writes, FULL retry, original deadlines and iterative inline completion behavior remain intact. SessionResourcesTest proves counts include delayed/active replies and immutable snapshots; amended saturation fixtures still verify ordinary capacity and deadline preservation.
I: pass | Package accessors expose counters only; no mutable entry list, dispatcher ticket or application response state escapes.
D: pass | Uses operation values, bounded HandlerDispatcher and coordinator port methods, not concrete transport/TLS classes.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/MessageExchange.java
type: kg.aidarbek.smpp.endpoint.MessageExchange.Entry
sha256: aa32a0ac2f803b39af1206f629481a56ad6d07bc45e01bcd7af2cf4122e8097c
responsibility: Retains one paired inbound request, deadline, cancellation flag and finite reply-byte reservation.
consumers: MessageExchange and its internal ReplyWrite access the entry while holding the coordinator monitor.
S: pass | The entry is an ownership record, not an execution service or secondary request window.
O: pass | Generic Operation and Command representations carry every supported paired command without embedding command-specific fields.
L: pass | Original request/handler/write deadlines and one writing flag remain stable; fallback/ready bytes are charged until settlement and close clears logical ownership while dispatcher tickets retain physical stages.
I: pass | Private fields are visible only to enclosing implementation consumers; public observations return scalar snapshots.
D: pass | Protocol values, AtomicBoolean and the existing dispatcher ticket are sufficient; no socket, TLS or persistence dependency exists.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/MessageExchange.java
type: kg.aidarbek.smpp.endpoint.MessageExchange.ReplyWrite
sha256: aa32a0ac2f803b39af1206f629481a56ad6d07bc45e01bcd7af2cf4122e8097c
responsibility: Settles one application reply write and releases its entry reservation.
consumers: FrameTransport callbacks enter through coordinator writeMessage; MessageExchange advances the ordered queue.
S: pass | Only reply guard, release and failure propagation belong here; application decisions execute elsewhere.
O: pass | All descriptors and both TLS/plain ports share the observer, preserving the existing response extension route.
L: pass | beforeWrite checks retained ownership, success removes once and releases exact bytes, failure preserves its cause and closes. The flush guard prevents recursive inline callback growth; shared fake and large-backlog tests remain green.
I: pass | A private WriteObserver requires no application listener or exposed mutable handle.
D: pass | Depends on enclosing exchange/coordinator and SPI failure types; transport encryption stays below the boundary.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/OutbindConnector.java
type: kg.aidarbek.smpp.endpoint.OutbindConnector
sha256: 08f1df93b0a94e80e1dae82e134ec130c67aa2fbb174d5ba4f493ab25679074f
responsibility: Composes a bounded TCP-initiating message-center owner for explicit outbind and follow-up bind.
consumers: Applications call connect/connectAttempt/reconnect and lifecycle observation; coordinator handles the received bind.
S: pass | Origin-specific wiring and owned endpoint lifetime remain its responsibilities; ReconnectHandle owns repeat policy and no message replay.
O: pass | Old constructors stay plain, longest constructor selects lifecycle, and the existing connection factory is reused by reconnect.
L: pass | Validates TLS client role and input before transfer, attaches physical permits before start, preserves original total outbind/bind budget and releases failed construction correctly. Real reversed TLS and fresh-socket reconnect cases cover trust, modes, namespace and cancellation.
I: pass | Dedicated outbind configuration/authentication avoids a dummy server-listen address or irrelevant ESME request methods.
D: pass | This is an explicit concrete composition boundary for TcpTransport/TlsConfig plus abstract auth/handlers; coordinator remains port-based.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/OutbindListener.java
type: kg.aidarbek.smpp.endpoint.OutbindListener
sha256: ab8d3c60a63d87330680901bfbda26a2575aa74322a628d906002852e7376a2b
responsibility: Composes the TCP-accepting ESME owner with bounded outbind authentication and explicit follow-up bind.
consumers: Applications start/close the listener and supply outbind decisions; accepted transports transfer to the coordinator after admission.
S: pass | Listener admission and resource ownership are separate from outbind wire policy, handler execution and TLS negotiation.
O: pass | Lifecycle overload preserves old defaults and reuses the established reversed-role coordinator without another protocol loop.
L: pass | TLS role is server despite ESME role; accepted-arrival bind budget includes handshake and asynchronous outbind decision. Rejection, late success, closed listener and all valid mode/profile cases retain bounded cleanup.
I: pass | Only outbind-specific authentication/readiness and endpoint lifetime APIs are required; no automatic reconnect behavior is imposed on acceptors.
D: pass | Concrete TcpListener/TLS wiring belongs here; application decisions use bounded callback abstractions and the coordinator sees only FrameTransport.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/ReconnectHandle.java
type: kg.aidarbek.smpp.endpoint.ReconnectHandle
sha256: f8b3a5b42d38c5aa42d58f70fe0eb965d4a629991edea02352228cec5cf7b530
responsibility: Owns a finite, explicitly cancellable sequence of fresh connection generations without retaining messages.
consumers: Client and outbind factories register it; endpoint timer ticks it; applications observe sessions, attempt count and protected termination.
S: pass | Attempt/backoff/observer ownership is the single change driver; bind, transport, request completion and message reconciliation remain separate collaborators.
O: pass | A focused internal attempt supplier supports the two real initiating owners; no additional scheduler, request engine, persistence or replay strategy is introduced.
L: pass | One current attempt, finite lifetime attempts, physical retirement barrier and prior observer return prevent overlap. Winning cancel stops future offers, already-owned callbacks remain bounded, cleanup failure stops replacement, and protected stages preserve cancellation ownership. Raw-peer no-replay, exact saturation, late-auth, throwing-observer and deterministic race tests verify these contracts.
I: pass | Public cancel/currentSession/attempts/termination are lifecycle-only capabilities; application observers never receive an internal pending-request or cleanup handle.
D: pass | Uses ConnectionAttempt, endpoint resource reservations, functional callback/clock seams and JDK stages; socket/TLS and message command implementations are absent. Coordinator callouts occur outside its monitor.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/ReconnectPolicy.java
type: kg.aidarbek.smpp.endpoint.ReconnectPolicy
sha256: e9e31141019761420c7a3a18774f72a7c2ca64db36218adebf5661983adcd23e
responsibility: Defines total connection-attempt limit and delay after retirement.
consumers: ReconnectHandle enforces it and simulator parses its explicit finite options.
S: pass | Only retry timing/count values are retained; there is no per-message replay or error classifier.
O: pass | Different caller limits compose through the same bounded controller; no strategy hierarchy is required for the requested fixed-delay policy.
L: pass | Positive attempt count and nonnegative representable delay reject invalid inputs; zero delay is allowed and the initial attempt counts toward the limit. Deterministic clock tests cover exact delay and exhaustion.
I: pass | Two scalar values avoid demanding callback implementations from configuration users.
D: pass | Depends on JDK values and existing duration validation, with no transport or request-engine dependency.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/ReconnectResult.java
type: kg.aidarbek.smpp.endpoint.ReconnectResult
sha256: a458f417141a8c556e250c6c5f75b84998b84786b79bbfbfa331ca5bf10be2ef
responsibility: Records why a connection sequence ended and its bounded attempt/publication observations.
consumers: Applications and tests consume the handle termination stage; controller constructs the snapshot once.
S: pass | Outcome reporting is separate from connection scheduling and message outcomes.
O: pass | Reason vocabulary can grow locally without changing the protected stage or transport port.
L: pass | Nonnegative counts and published<=attempts are validated; reason/count/Optional fields are final. The original RuntimeException identity is intentionally preserved rather than claiming deep immutability of Throwable state; no message history is retained.
I: pass | Only terminal reason, two counts and latest local failure are exposed; consumers need no internal resource object.
D: pass | Uses JDK Optional/RuntimeException and its enum, independent of networking and protocol codecs.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/ReconnectResult.java
type: kg.aidarbek.smpp.endpoint.ReconnectResult.Reason
sha256: a458f417141a8c556e250c6c5f75b84998b84786b79bbfbfa331ca5bf10be2ef
responsibility: Enumerates terminal connection-loop policy decisions.
consumers: ReconnectHandle selects it and ReconnectResult/application diagnostics inspect it.
S: pass | Each category explains stopping future connection work; none represents a remote message status.
O: pass | New lifecycle stop reasons can be added without modifying request/transport failure domains.
L: pass | CANCELLED, ENDPOINT_CLOSED, exhaustion, notification, observer and cleanup outcomes have stable enum identity. Unit/raw-peer cases establish which contender wins; cleanup failure can truthfully supersede a requested stop.
I: pass | Consumers inspect one category instead of implementing policy methods.
D: pass | No infrastructure or protocol dependencies.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/SessionResources.java
type: kg.aidarbek.smpp.endpoint.SessionResources
sha256: 98884e66a7807fd743c56256c5c5e5d86225a9af26b3e6bfa439717e11169632
responsibility: Provides an immutable sampled view of request and paired-reply ownership for one generation.
consumers: BoundSession and the simulator read scalar count/byte accessors; SessionResourcesTest retains earlier samples.
S: pass | The record reports exactly owned counters, not estimated transport buffers or admission control decisions.
O: pass | A separate value keeps diagnostic observation from exposing or mutating request/exchange internals.
L: pass | Negative counters reject construction; primitive fields have ordinary record equality and remain unchanged after later settlement. Documentation explicitly allows independent request settlement between counter reads and excludes control replies/one-way/transport queues.
I: pass | Only the four meaningful per-session counters are exposed, without window handles, tickets, messages or mutable collections.
D: pass | Depends only on primitives/JDK validation; lower-layer engines do not depend on this public diagnostic record.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/SmppClient.java
type: kg.aidarbek.smpp.endpoint.SmppClient
sha256: 11f9a186c26230430f53bacdd36fab35c28b274ee5b42e242981b0b9087e25ff
responsibility: Composes and owns bounded TCP-initiating ESME connections and their optional explicit lifecycle policy.
consumers: Applications/simulator construct it and use connect, attempts, reconnect, sessions and bounded shutdown.
S: pass | Concrete transport wiring and endpoint lifetime belong here; RequestWindow, coordinator, TLS implementation and reconnect policy remain focused collaborators.
O: pass | Existing constructors delegate to defaults and the longest overload adds lifecycle; reconnect reuses connectAttempt instead of duplicating bind/correlation code.
L: pass | Resolved addressing, early validation, protected readiness and cancellation contracts remain intact. Permits attach before start and retire on actual I/O cleanup, including failed construction; TLS origin and startup budget are validated before workers. Matrix, retention and no-replay tests cover the new paths.
I: pass | Immediate one-attempt clients need not adopt reconnect callbacks; session sending stays in optional BoundSession capabilities.
D: pass | The public owner is the deliberate concrete TcpTransport/TLS composition boundary; application handlers remain narrow callbacks on owned bounded dispatchers.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/endpoint/SmppServer.java
type: kg.aidarbek.smpp.endpoint.SmppServer
sha256: 3835681f060a54dcbe6b72aba027a061e33c2d003627b1704b6997965a31c6f5
responsibility: Composes bounded TCP acceptance, bind authentication and ready-session publication for a message center.
consumers: Applications configure/start it, supply async bind/operation handlers and request bounded cleanup.
S: pass | Listener/endpoint ownership is separate from TLS negotiation, protocol permission and application handler execution.
O: pass | Existing overloads remain compatible and optional lifecycle selects TLS/keepalive without changing credential or operation handlers.
L: pass | TLS server role rejects client policy before allocation, acceptance is bounded, bind budget includes pre-bind TLS time and failed admission closes owned transports. Both unbind origins, plaintext rejection, mutual/auth delay and blocked-handler cleanup stay covered.
I: pass | Auth, ready observer and optional ExchangeConfig remain focused capabilities; callers do not implement submission or reconnect methods merely to listen.
D: pass | TcpListener selection occurs only at composition; accepted coordinator uses the frame port and bounded application abstractions, including supplied-executor ownership from existing tests.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/spi/TransportFailure.java
type: kg.aidarbek.smpp.spi.TransportFailure
sha256: 160624d69805c02ba237175408e28fee053b0b1a08a510c1e10519ff45c1b281
responsibility: Carries a payload-free local transport category, guard-claim flag and original cause.
consumers: Frame/write listeners, coordinator/request failure mapping, reconnect cleanup and diagnostics inspect it.
S: pass | New TLS categories describe local readiness failure only; SMPP wire statuses and request transmission certainty remain separate.
O: pass | Adding handshake/setup outcomes to Kind does not change the frame-port interface or request engine.
L: pass | RuntimeException serialization retains kind/flag and Throwable cause; generated message contains only category. writeStarted denotes guard claim, not peer receipt or RequestFailure certainty; shared contract and TLS setup/write tests preserve that distinction.
I: pass | Consumers can inspect category/claim/cause without parsing payloads or depending on TLS provider classes.
D: pass | SPI failure depends only on JDK and its nested enum, preserving network-independent port use.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/spi/TransportFailure.java
type: kg.aidarbek.smpp.spi.TransportFailure.Kind
sha256: 160624d69805c02ba237175408e28fee053b0b1a08a510c1e10519ff45c1b281
responsibility: Names finite local transport failures including TLS setup/handshake timeout.
consumers: TransportFailure, TCP adapter and higher-level diagnostics use the category identity.
S: pass | The enum separates local transport causes rather than mixing protocol statuses or retry decisions.
O: pass | TLS_HANDSHAKE_FAILED/TIMEOUT extend local vocabulary while existing read/connect/write/cleanup meanings remain stable.
L: pass | Enum identity/serialization is unchanged. Raw/provider fixtures distinguish setup, active write timeout and ordinary cleanup failures instead of collapsing them into malformed protocol data.
I: pass | Only outcome identity is required; consumers implement no infrastructure behavior.
D: pass | No dependencies on concrete TLS/socket or endpoint types.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/transport/TcpListener.java
type: kg.aidarbek.smpp.transport.TcpListener
sha256: e6ef52fd1136918dc0d5f4bc8612e468eeb1b9ada09a78970a3f2abca3fbd94f
responsibility: Owns one bounded blocking accept loop and transfers unstarted frame transports after admission.
consumers: Server and reversed listener factories supply finite admission callbacks and observe listener termination.
S: pass | TLS configuration is propagated to adopted connections; the accept loop itself does not handshake or invoke application auth.
O: pass | The overload preserves the original plain-TCP API and existing ownership callback; per-connection transport handles TLS variation.
L: pass | Wrong TLS role/input fails before binding, rejected handoffs close, transferred connections remain endpoint-owned and listener termination preserves cleanup failure. Existing listener tests plus accepted TLS/outbind matrix verify no accidental ownership change.
I: pass | AcceptListener remains the small fast ownership transfer contract; no TLS callback or application handler is forced on it.
D: pass | Concrete network composition legitimately uses ServerSocket/TcpTransport; endpoint/session policies are not imported into transport.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/transport/TcpTransport.java
type: kg.aidarbek.smpp.transport.TcpTransport
sha256: 743a9570cd43e4ebcd3f91143be6848cf5833e3d107357841c519dbc858e2f7f
responsibility: Implements bounded complete-frame I/O, optional transport-owned TLS and physical connection cleanup.
consumers: The four endpoint owners compose it through FrameTransport; listener/write callbacks are internal nonblocking bridges.
S: pass | Encryption/handshake belong with socket byte transport; no SMPP role permission, authentication, message replay or application scheduling is added.
O: pass | TlsConfig and compatible factory overloads select security while the same bounded queues, deadlines, framer and observer ports remain stable.
L: pass | connected follows trust/identity verification; guards cannot begin during TLS, original connect/write/handshake deadlines remain independent, and wrapper ownership is stored before setup. Raw socket abort precedes TLS wrapper close; termination waits workers/accepted notifications and preserves cleanup failures. Unchanged shared contracts pass both TLS roles, with real slow-peer, provider, handshake and mutual-auth fixtures.
I: pass | FrameTransport remains free of socket/context details and keeps separate control/ordinary bounds; encryption adds no mandatory session callbacks.
D: pass | Transport imports only JDK/JDK TLS plus codec framing and SPI/profile limits already allowed; root probes reject endpoint policy and coordinator crypto/socket dependencies.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/transport/TcpTransport.java
type: kg.aidarbek.smpp.transport.TcpTransport.PendingWrite
sha256: 743a9570cd43e4ebcd3f91143be6848cf5833e3d107357841c519dbc858e2f7f
responsibility: Owns one copied frame and its write class, deadline, guard and terminal state.
consumers: TCP writer/deadline/close loops settle it; callers see only WriteHandle cancellation.
S: pass | Only frame transmission ownership changes it; TLS readiness is an enclosing transport prerequisite, not a second pending queue.
O: pass | The existing two bounded write classes and observer contract support both TLS/plain output without subclass-specific frame state.
L: pass | Cancellation wins only while queued/unclaimed, release/terminal selection is once, and byte ownership remains through active output. Shared contracts and stalled-TLS/slow-write tests verify no early guard or unbounded queue; response correlation remains outside this handle.
I: pass | Implements only WriteHandle.cancel; frame bytes and mutable state are private implementation details.
D: pass | Uses enclosing transport lock/queues and the SPI observer contract; no application or endpoint dependency.
findings: none
```

```solid-review
source: src/main/java/kg/aidarbek/smpp/transport/TlsConfig.java
type: kg.aidarbek.smpp.transport.TlsConfig
sha256: e8bc7cda5ca25f10c3ed6f0191d13ba0fd688089425624454db284f72aaaea96
responsibility: Defines explicit TLS role, certificate identity and total handshake allowance around caller-owned context.
consumers: Endpoint lifecycle composition validates role; TcpTransport creates and configures layered sockets before handshake.
S: pass | Key/trust choice belongs to the caller; this type only validates/configures transport security policy and owns no workers or credential files.
O: pass | Initialized JDK SSLContext supplies supported provider/key/trust variation; client/server factories make role and mutual-auth choice explicit.
L: pass | Client peer identity is mandatory, timeouts representable/positive and uninitialized context rejected. Immutable policy fields do not claim deep immutability of supplied context/managers. Layer creation is separate from setup so transport records wrapper ownership before parameter failures; exact provider regressions verify classification/cleanup.
I: pass | Callers choose client, server or mutual server policy and expose no raw socket through the public configuration API.
D: pass | Uses only JDK SSL/socket/value APIs inside the concrete transport package. HTTPS identification checks the explicit certificate identity independently of resolved destination; no permissive verifier or external runtime dependency is introduced.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/ExchangeConnectionTest.java
type: kg.aidarbek.smpp.endpoint.ExchangeConnectionTest
sha256: f8e6151a694bb38e876b5b43c945b605824eaf5a22774ac8713d18dfd7bbac06
responsibility: Verifies controlled paired-reply ordering, admission, expiry and cleanup over the shared bounded frame fake.
consumers: Jupiter drives EndpointConnection, HandlerDispatcher and FakeFrameTransport under explicit ownership schedules.
S: pass | Existing application-decision/reply contracts remain the subject; only ordinary-saturation fixture commands changed for intentional manual enquiry priority.
O: pass | Operation descriptors and the shared frame port support query replacements without creating a new fake or branching production code for tests.
L: pass | Queries now genuinely occupy ORDINARY capacity, while independent control reserve and original reply deadlines remain asserted. Large inline drains, expired queued handlers and retained active writes keep their prior finite cleanup checks; logs45/46 document why fixture expectations changed.
I: pass | The class implements no surrogate production interface; its methods need only the coordinator and controlled fake capabilities.
D: pass | Concrete fake/dispatcher dependencies are deliberate composition tests, and the same fake is independently checked against FrameTransportContract.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/KeepaliveEndpointsTest.java
type: kg.aidarbek.smpp.endpoint.KeepaliveEndpointsTest
sha256: 65465ae92ff46b200d91afeb83a6a075d7bb562096540d03b6c59d6d35d5d255
responsibility: Verifies automatic control liveness and timeout through all four real endpoint owners.
consumers: Jupiter and its Pair fixture drive raw finite peers for normal/reversed TCP origins.
S: pass | Owner policy wiring and real request correlation are the focus; detailed timing arithmetic is delegated to KeepaliveTest.
O: pass | The four explicit owner variants share the raw peer checks while preserving distinct outbind ordering.
L: pass | Real replies correlate to the generated enquiry, missing replies close with bounded waits, and all owned peers/endpoints close in finally. It tests actual sockets instead of inferring wiring from constructors.
I: pass | Test consumers use only control/lifetime methods; no optional message services are required.
D: pass | Uses public owners plus raw peer fixtures intentionally at the composition boundary; protocol timing policy itself remains network-independent.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/KeepaliveEndpointsTest.java
type: kg.aidarbek.smpp.endpoint.KeepaliveEndpointsTest.Pair
sha256: 65465ae92ff46b200d91afeb83a6a075d7bb562096540d03b6c59d6d35d5d255
responsibility: Owns the finite raw-peer and endpoint resources for one normal or reversed keepalive scenario.
consumers: The enclosing parameterized endpoint tests obtain the bound session and counterpart peer.
S: pass | Only fixture setup and teardown vary by TCP/SMPP origin; assertions remain in the test methods.
O: pass | Four actual owner configurations are constructed explicitly rather than approximated by an unsupported generic endpoint mock.
L: pass | Construction failure closes partially created resources, waits are bounded, and AutoCloseable.close reliably closes the selected owner/peer without a misleading broad checked exception. Profile/outbind setup follows valid protocol modes.
I: pass | A private fixture exposes only values needed by its tests; it implements no production socket or session interface.
D: pass | Concrete owners and raw Socket peers are appropriate test composition dependencies; no fixture logic is published in the library.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/KeepaliveTest.java
type: kg.aidarbek.smpp.endpoint.KeepaliveTest
sha256: c19cfdcb8899497ff5731d2e31bd1f93fb26bb72dcb5d79eecd0b41175fa1bb5
responsibility: Proves exact idle timing, one-request ownership, priority, saturation and failure propagation deterministically.
consumers: Jupiter drives an injected monotonic clock and the shared fake through the nested Connection fixture.
S: pass | Keepalive/control lifecycle is the single focus, including the final immediate/guarded control-reply cause correction.
O: pass | Existing RequestWindow/FakeFrameTransport behavior is reused to vary capacity and outcomes; no special second heartbeat request implementation is tested.
L: pass | Asserts exact deadlines, NOT_SENT/MAY_HAVE_BEEN_SENT, negative/nack outcomes, ordinary versus CONTROL capacity and original local cause. Real reds18/20/22/55 precede fixes; all waits/cleanup are bounded and fake clock advancement does not sleep.
I: pass | Focused fixture methods expose only time advancement and owned port/session state needed by the tests.
D: pass | The fake satisfies the shared frame-port contract; policy assertions depend on public failure values rather than concrete socket scheduling.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/KeepaliveTest.java
type: kg.aidarbek.smpp.endpoint.KeepaliveTest.Connection
sha256: c19cfdcb8899497ff5731d2e31bd1f93fb26bb72dcb5d79eecd0b41175fa1bb5
responsibility: Creates one fake-bound generation with an injected clock and bounded notification ownership.
consumers: Keepalive tests and the reconnect graceful-cancel race borrow its coordinator/session/port.
S: pass | It owns only deterministic connection setup/cleanup and clock advancement, leaving scenario assertions outside.
O: pass | Policy presence and request-window capacity are constructor inputs; existing client coordinator factories remain the substitution seam.
L: pass | Completes a valid bind before returning, cleans failures, closes coordinator/notifier and awaits finite notifier termination. Null policy deliberately represents disabled keepalive, and port deadlines retain the fake shared contract.
I: pass | AutoCloseable plus minimal package fixture fields meet test needs; no unsupported service interface is implemented.
D: pass | Uses the network-independent frame fake, real request notifier and atomic clock; no new executor or sockets are introduced.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/ReconnectControlTest.java
type: kg.aidarbek.smpp.endpoint.ReconnectControlTest
sha256: e106f31c65b9a0d5e3feacc5b76c2c21e8e2848a7598aff69f04ec7b783ca530
responsibility: Verifies finite reconnect policy, physical retirement, cancellation races and timer-safe factory failure deterministically.
consumers: Jupiter creates package-private handles with controlled clocks and attempt/cleanup stages.
S: pass | Only controller ownership and terminal selection are varied; actual TCP generation/replay tests live separately.
O: pass | Supplier/clock/stage seams exercise the two real owner workflows without inventing another request mechanism.
L: pass | Original failure identity is asserted, physical-retirement failure prevents another attempt, derived stage mutation cannot win cancellation and a losing cancel preserves graceful state. All resource fixtures are finally closed with bounded completion.
I: pass | No production interface is faked unnecessarily; attempt outcomes and cleanup stages express exactly the controlled boundary.
D: pass | Depends on existing EndpointResources and coordinator fixture for one race; no raw network timing is needed for policy arithmetic.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/ReconnectEndpointsTest.java
type: kg.aidarbek.smpp.endpoint.ReconnectEndpointsTest
sha256: d637ba54834be4523b708c6c5e6417b9ed8e52ce5500236dccaa587b07122b9f
responsibility: Verifies fresh bound generations and explicit cancellation for both initiating endpoint owners.
consumers: Jupiter uses bounded raw ServerSocket peers and records observer-delivered sessions.
S: pass | Real owner registration, fresh namespaces and reconnect notification placement are the test responsibility.
O: pass | Normal and outbind owners share ReconnectHandle while retaining separate bind/outbind wire ordering in fixtures.
L: pass | Peer accept/read/termination waits are finite; generations have distinct IDs and fresh sequence1. Tests check observer dispatch and lifetime attempt exhaustion/cancellation, then close all sockets and endpoint workers.
I: pass | Uses public reconnect/session/control APIs and raw wire observations; no internal request-window ownership escapes.
D: pass | Concrete loopback sockets deliberately validate composition, independent of internal timer mocks.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/ReconnectFailureTest.java
type: kg.aidarbek.smpp.endpoint.ReconnectFailureTest
sha256: efa5aec3a5a76e3ac3fe7bb6e56223e2411cbc199adf63d66f5ac8d7db29023b
responsibility: Proves no message replay and globally bounded reconnect notification retention under adversarial lifecycle events.
consumers: Jupiter orchestrates raw peers, public sessions and blocked/throwing observer or authentication gates.
S: pass | Ambiguous submission, exact notification capacity and cancellation/late-auth behavior all test one connection-generation ownership boundary.
O: pass | Failures are supplied through real peers/application callbacks; no production retry/replay hook or extra request engine is added.
L: pass | An actually transmitted submit fails MAY_HAVE_BEEN_SENT in its original generation; replacement receives no replay. Six occupied notification permits stop publication without extra work. Blocking gates always release in finally; abort cleanup remains independent and late auth cannot start another offer.
I: pass | Consumers observe public RequestFailure/ReconnectResult/EndpointTermination facts rather than requiring private pending-state mutation.
D: pass | Uses actual endpoint/socket composition and bounded JDK latches/queues; environment credentials and persistent external services are absent.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/SessionResourcesTest.java
type: kg.aidarbek.smpp.endpoint.SessionResourcesTest
sha256: 95e115bc9df69ef9e2e45d2b74b9bbdf7a15de451224ba199752f4f53b068a95
responsibility: Verifies sampled request/reply accounting against delayed application and transport settlement.
consumers: Jupiter drives a fake bound client with one retained handler result and response write.
S: pass | Tests the new observation contract, not unrelated queue implementation details or global telemetry.
O: pass | Uses standard operation/handler and frame-port seams; no debug mutation API is added to production.
L: pass | Proves request bytes/count while pending, received/fallback reply byte retention, active-write ownership and zero after settlement; a prior record remains unchanged. All handler/notifier ownership is released in finally.
I: pass | Reads only the four public sampled counters; no mutable exchange entry is required.
D: pass | Known bounded fake and real internal dispatcher provide controlled composition; public snapshots remain dependency-free.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/TlsBindingMatrixTest.java
type: kg.aidarbek.smpp.endpoint.TlsBindingMatrixTest
sha256: be8d56be0e32f66a4a6be62b3d1f02fc22f6b2dd465d9b2ce6cd9e12201c131c
responsibility: Verifies all valid mode/profile/TCP-origin/unbind-origin secure binding combinations.
consumers: Jupiter creates finite real normal/outbind endpoint pairs with development TLS contexts.
S: pass | TLS composition must preserve protocol role/version permissions and both successful unbind directions.
O: pass | Parameterized cases describe normal three-mode behavior and the precise reversed outbind permissions rather than assuming a universal matrix.
L: pass | Twenty successful combinations plus untrusted reversed-client rejection check authenticating/readiness boundaries, matching controls and cleanup. No invalid3.4 outbind TRX capability is claimed; all waits and finally cleanup are finite.
I: pass | Only required authentication/outbind/ready callbacks and controls are supplied, with no message-service stubs.
D: pass | Uses public concrete owners and a JDK-only TLS fixture; pure protocol state rules are still separately tested without networking.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/TlsEndpointDeadlineTest.java
type: kg.aidarbek.smpp.endpoint.TlsEndpointDeadlineTest
sha256: de9dab46211564f4abde8d449e06a1cd93f5035869e24d071e67ec84e204881b
responsibility: Verifies composition of TCP, TLS and accepted bind startup deadlines.
consumers: Jupiter controls an independent SSLServerSocket handshake gate and a stalled raw accepted peer.
S: pass | Checks outer endpoint deadlines that could incorrectly supersede or extend transport handshake policy.
O: pass | Existing public lifecycle configuration supplies timing variation; no production sleep/test-only timer branch is introduced.
L: pass | A connected TCP socket survives its old100ms TCP deadline while a2s TLS budget is live, and an accepted150ms bind budget includes TLS waiting. Gates release in finally, sockets have finite read bounds and peer threads are joined.
I: pass | Only connect/readiness and cleanup APIs are necessary; no application message handler is installed.
D: pass | Independent raw/JSSE peers make the timing boundary observable instead of duplicating transport implementation.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/TlsEndpointsTest.java
type: kg.aidarbek.smpp.endpoint.TlsEndpointsTest
sha256: 1999cf22e8181d7767557f969a1b488c16b0bbde00e47bed121f032faef868d0
responsibility: Checks that invalid TLS cannot cross into ordinary SMPP bind authentication.
consumers: Jupiter constructs public client/server owners against trusted and plaintext/raw peers.
S: pass | Authentication boundary and constructor policy wiring are the narrow subject.
O: pass | Uses the same public longest constructors as real applications and leaves authentication policy separate from TLS trust.
L: pass | Untrusted TLS cannot bind; plaintext does not reach the authenticator. Alert-aware rejection reads remain bounded and failed construction/connection fixtures close their resources;13b confirmed the fixture detects removed TLS wiring.
I: pass | Uses focused BindAuthenticator and readiness counters, without optional message callbacks.
D: pass | JDK TLS material and real loopback sockets validate the endpoint boundary; no permissive trust implementation is used.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/endpoint/TlsHandlerLifecycleTest.java
type: kg.aidarbek.smpp.endpoint.TlsHandlerLifecycleTest
sha256: dd72084279d26666e73586d738db0865b255899694f48d44ebdc72a817704bd0
responsibility: Proves blocked application invocation/stage cannot stall encrypted control traffic or raw socket abort.
consumers: Jupiter creates one secure client/server pair and a bounded delayed submission handler for each blocking variant.
S: pass | The test relates physical callback retention to encrypted connection cleanup, rather than claiming callbacks can be forcibly stopped.
O: pass | Standard handler/ExchangeOptions/lifecycle inputs express both blocked invocation and returned-stage cases.
L: pass | Both directions of enquiry succeed while the submission handler remains occupied. Explicit abort reports zero physical connections and one retained handler, marks cancellation and settles the original request; finally always releases the gate/stage and closes owners.
I: pass | Only one submission handler plus public controls/termination/snapshot access are required.
D: pass | Real TLS endpoints and bounded latches validate composition; no transport thread runs the SMPP application handler.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TlsCleanupTest.java
type: kg.aidarbek.smpp.transport.TlsCleanupTest
sha256: d68e760c7b8704db7c5927a6ac5b7631ca3415e159d4eddce1b7ec7580c066f8
responsibility: Exercises mutual TLS, stalled/expired handshake and slow encrypted writer cleanup with independent peers.
consumers: Jupiter uses raw sockets/JSSE clients against adopted transports with bounded read/write/handshake times.
S: pass | Focuses on physical TLS ownership and deadlines, not SMPP permission or application authentication logic.
O: pass | Real contexts vary client identity and server trust, while the same adopted port/queue contract handles all cases.
L: pass | Requires trusted client certificates when configured; absence/untrusted identity never publishes connected. Pre-start time counts toward handshake bound. Repeated close aborts stalled TLS, and an8MiB active write deadline leaves queued CONTROL guard unstarted with once-only settlement.
I: pass | Uses existing internal Events/Writes observation helpers and public transport APIs; no new production interface is demanded.
D: pass | Independent JSSE/raw peers avoid testing one copy of the transport against another; development-only trust material is explicit.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TlsConfigTest.java
type: kg.aidarbek.smpp.transport.TlsConfigTest
sha256: 504bf24d7eb0b09f8d60a5d6ac289e9feca21556c86945478debdb6ff1129d53
responsibility: Verifies TLS configuration and composition-role validation before resource allocation.
consumers: Jupiter supplies invalid names/durations/contexts and mismatched connect/listen roles.
S: pass | Only fail-fast configuration invariants are under test; handshake traffic belongs to other fixtures.
O: pass | Different JDK contexts and TLS roles are direct values, without a new validation-service abstraction.
L: pass | Invalid inputs are rejected and regression-accepted resources would still be closed by the fixture. An initialized caller context is required and explicit role mismatches cannot allocate a valid endpoint accidentally.
I: pass | No custom subtype or service interface is implemented; direct factory assertions meet the contract.
D: pass | Depends on JDK SSLContext and concrete transport factories intentionally at the configuration boundary.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TlsPortContractTest.java
type: kg.aidarbek.smpp.transport.TlsPortContractTest
sha256: 18bcc069c0bb2da15062044b1913aaf23f58f62ba745b3f2651756ecef5ec179
responsibility: Runs the unchanged shared FrameTransport behavioral contract through encrypted ports in both TLS roles.
consumers: Jupiter supplies an independent layered JSSE peer to FrameTransportContract.verify.
S: pass | Its only purpose is substitution evidence for TLS-configured TcpTransport: framing, guards, ownership, cancellation and close.
O: pass | Both roles reuse the same shared test rather than inventing weaker encryption-specific assertions.
L: pass | Handshake is finite on one joined peer worker; all sockets/resources close in structured cleanup. Shared assertions verify copied bytes, pre-start/closed rejection, guard refusal, exactly-once callbacks and protected termination under encryption.
I: pass | Inject/takeWritten functions are sufficient peer controls; no alternate production port API is added.
D: pass | Uses the existing SPI contract harness plus an independent JSSE socket, with no endpoint policy dependencies.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TlsProviderFailureTest.java
type: kg.aidarbek.smpp.transport.TlsProviderFailureTest
sha256: c7d9248351f03e8f73b3dc09e70d0f8e2078748e485d6399f65fcc07a23b105e
responsibility: Verifies classification and cleanup when a configured TLS provider fails before handshake.
consumers: Jupiter tests IOException and runtime setup failure through a real connected socket and controlled context factory.
S: pass | The test isolates provider setup failure from framing, peer trust failure and ordinary socket read errors.
O: pass | The standard SSLContextSpi/SSLSocketFactory extension boundary supplies only the selected setup fault.
L: pass | Asserts exact original cause, TLS_HANDSHAKE_FAILED, no readiness, peer EOF and finite transport termination. The real red43 distinguished both incorrect prior categories, followed by44green.
I: pass | Fault types implement required JDK methods rather than adding test-only public production hooks.
D: pass | Uses JDK provider interfaces and delegates normal operations to initialized JSSE; real socket cleanup remains externally observable.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TlsProviderFailureTest.java
type: kg.aidarbek.smpp.transport.TlsProviderFailureTest.FailingContext
sha256: c7d9248351f03e8f73b3dc09e70d0f8e2078748e485d6399f65fcc07a23b105e
responsibility: Wraps initialized JSSE configuration with the test setup-failure SPI.
consumers: TlsProviderFailureTest passes it through the same public TlsConfig factory used by callers.
S: pass | Only context-provider substitution is represented; no sockets or executors are owned by this wrapper.
O: pass | Uses the documented protected SSLContext constructor and SPI seam rather than overriding final public methods.
L: pass | Provider/protocol metadata and initialized delegate are retained; all normal context capabilities delegate through FailingSpi, while the selected layer-creation operation is intentionally faulted.
I: pass | Inherited SSLContext API is the required test boundary; no extra application methods are exposed.
D: pass | Depends on JDK context/provider abstractions and its narrowly scoped fixture SPI.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TlsProviderFailureTest.java
type: kg.aidarbek.smpp.transport.TlsProviderFailureTest.FailingFactory
sha256: c7d9248351f03e8f73b3dc09e70d0f8e2078748e485d6399f65fcc07a23b105e
responsibility: Injects the selected layered-socket setup failure while delegating required ordinary socket factory operations.
consumers: FailingSpi exposes it to TlsConfig.layer during provider-failure tests.
S: pass | One provider failure path is the change driver; it does not simulate TLS negotiation or socket framing.
O: pass | The existing SSLSocketFactory layered-create seam varies the injected exception without production conditionals.
L: pass | Cipher-suite and required ordinary factory methods delegate to real JSSE. Layered creation throws the specified IOException or runtime provider failure before taking socket ownership, matching the scenario verified by raw peer cleanup.
I: pass | Implements the required JDK abstract factory surface meaningfully instead of returning null or fake success for unused methods.
D: pass | Uses only a real SSLSocketFactory delegate and selected local exception; there is no endpoint/request dependency.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TlsProviderFailureTest.java
type: kg.aidarbek.smpp.transport.TlsProviderFailureTest.FailingSpi
sha256: c7d9248351f03e8f73b3dc09e70d0f8e2078748e485d6399f65fcc07a23b105e
responsibility: Delegates SSL context services while substituting only the layered-socket test factory.
consumers: FailingContext and the inherited SSLContext methods invoke the required SPI operations.
S: pass | The SPI wrapper isolates one configured provider variation from key/trust/session behavior.
O: pass | Normal engine, session-context and server-factory behavior stays with initialized JSSE; only socket factory selection differs.
L: pass | All required SPI methods delegate with their native exceptions/results and engineInit preserves key/trust/random arguments. No null/no-op SSL engine or handshake stub is returned.
I: pass | Implements exactly the JDK provider contract needed for legitimate substitution.
D: pass | Depends on SSLContext/JSSE interfaces plus the test FailingFactory, independent of library protocol layers.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TlsTestMaterial.java
type: kg.aidarbek.smpp.transport.TlsTestMaterial
sha256: 2150250106f4b0f244a9a94c9929a502a8b5c379c5d02a72541f3fefa2fa86e3
responsibility: Builds independent development-only JDK key/trust contexts from checked-in PEM fixtures.
consumers: Transport and endpoint TLS tests select identity and trust presence explicitly.
S: pass | Only finite test cryptographic material loading belongs here; production never imports or loads it.
O: pass | Boolean identity/trust inputs express trusted, untrusted and mutual-authentication cases using normal JDK factories.
L: pass | Each call creates independent key/trust stores/context; no trust-all manager is used. PKCS8/certificate parsing uses bounded local resources, and development password/material is intentionally public fixture data.
I: pass | One context factory is sufficient for tests across packages; no mock TLS socket/service methods are exposed.
D: pass | JDK certificate/key/keystore/SSL APIs keep third-party dependencies out of test and runtime paths.
findings: none
```

```solid-review
source: src/test/java/kg/aidarbek/smpp/transport/TlsTransportTest.java
type: kg.aidarbek.smpp.transport.TlsTransportTest
sha256: 4606ddf7bab9b049e9569544e7f08a6c1fce1129e2eee21e6f260aed93792e04
responsibility: Verifies trusted encrypted frame exchange and rejection/deadline readiness boundaries.
consumers: Jupiter connects TcpTransport to independent SSLServerSocket clients/servers and one stalled raw peer.
S: pass | Frame transport TLS behavior is isolated from SMPP state or authentication composition.
O: pass | Explicit TlsConfig roles/identity/trust and existing Events/Writes fixtures vary only relevant transport inputs.
L: pass | Checks binary frame ownership, trusted readiness, wrong-name/untrusted rejection and queued guards staying unstarted during timeout. Peer reads, latch waits and joins are finite; finally releases and closes every fixture.
I: pass | Only the frame port and existing internal observation helper contracts are consumed.
D: pass | Real independent JDK peers provide wire evidence and JDK-only material avoids circular endpoint-to-endpoint assumptions.
findings: none
```
