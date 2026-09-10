# Endpoint, request and transport readiness audit

This is a fresh whole-file review for Maven deployment readiness, performed on
10 September 2026. The reviewed Java baseline is commit
`094a84f595b61779904c0190ef15f6430561cd22`, using the isolated checkout
`/tmp/lightweight-smpp-endpoint-audit`. The assigned `endpoint` partition of
`build/runs/maven-readiness-20260910/file-partitions.json` contains exactly
133 tracked Java files: 63 production files, 68 test/fixture files and two
compiled examples. Every assigned file was read in full, including its nested
and anonymous declarations. Historical reviews supplied project context, not
the conclusion of this review.

No production defect that blocks publication was confirmed in this partition.
The review did confirm test-port substitution defects and one incorrect fixture
expectation. Their bounded regressions and corrections are recorded in
[the fixture contract report](0019-fixture-contracts.md). The corrections change
five test-source files, including one new test, and do not change any production
class or dependency. Fifteen added cases pass; all 764 library cases execute
successfully after formatting. This conclusion is limited to the examined
contracts and evidence. It does not promise correctness for every schedule,
provider, network environment or SMPP deployment, nor authorize publication.
Publication metadata, signing, archives and repository requirements are reviewed
separately by the root reviewer.

## Findings and disposition

| Finding | Observed problem | Disposition |
| --- | --- | --- |
| F1: claimed fake write | `FakeFrameTransport` could record bytes after close or deadline won while its guard was paused. | Actual close/deadline assertion reds; recheck lifecycle and original deadline immediately before recording bytes under the fixture guard. |
| F2: fake listener retirement | The fake could complete termination while an already-entered connected/frame callback remained active; callback exceptions also escaped its caller. | Actual retirement and exception reds; count entered listener invocations through return, abort progress failure with its cause, and publish cleanup only after all callbacks retire. |
| F3: deferred write deadlines | `CommonTestTransport` did not retain an accepted write's deadline, permitting both queued guard invocation and active byte emission after expiry. | Actual queued and active reds; retain the original deadline and check it at both driven progress boundaries. Queued expiry leaves a healthy stream open; active expiry aborts it. |
| F4: callback outcome classification | Common fixture progress failures were labelled ordinary closure and exceptional cleanup; one alert fixture test expected that incorrect exceptional result. | Actual connected/frame/guard reds; preserve `OBSERVER_FAILED` and complete successful cleanup normally. The incorrect assertion was corrected from the independent SPI contract. Terminal callback failures still produce `CLEANUP_FAILED`. |
| F5: abort-cause preservation | Fake queued closure used `cancel()` and reported `CANCELLED`; common terminal observer failures reported `CLOSED` to the listener. | Four actual reason-mismatch reds; queued aborts retain their transport reason/cause, and terminal observer failures retain `OBSERVER_FAILED` while separately failing cleanup. |
| D1: control-progress wording | `API.md` overstated what separate control capacity guarantees when a physical write is already active. | Clarified that handler delay does not consume the control reserve, but active output cannot be preempted and its deadline bounds a nonreading peer. |
| D2: explicit outbind reconnect | `COMMON_OPERATIONS.md` described both outbind owners as having no reconnection loop without qualifying the later opt-in initiating-owner API. | Clarified the single-attempt default and linked the bounded explicit `OutbindConnector.reconnect` contract. |

No speculative production change was made. The two transport fakes remain
explicitly driven test instruments: their deferred modes do not own a background
deadline scheduler. The corrected deadline assertions concern the progress a test
actually drives. Real queued/active expiry, socket interruption, listener cleanup
and TLS negotiation remain independently exercised by the actual TCP/TLS suites.
The common immediate `FrameTransportContract` is useful evidence but did not
cover the delayed races found here; passing it alone was insufficient.

## Complete-contract and SOLID assessment

The per-file ledger below identifies each complete file and the contract used
to inspect it. These cross-file assessments cover the interactions, inheritance
obligations and actual consumers, including the nested state holders and test
callbacks, rather than merely counting types or public methods.

**Endpoint composition and ownership.** `SmppClient`, `SmppServer` and the two
outbind owners construct variable transport and dispatcher infrastructure;
`EndpointConnection` composes codecs, version/state policy and the one request
window through the frame SPI. This division gives each layer a distinct reason
to change (S) and leaves transport/authentication/handler variation at actual
composition seams (O/D). Listener and session `AutoCloseable` implementations
retain idempotent abort ownership and separate physical termination rather than
strengthening close into an unbounded wait (L). Small senders, authenticators,
request handlers and notification handlers keep unrelated methods optional (I).
The review traced construction rejection, accepted/connecting permits, failed
physical cleanup, start/close races, callback-capacity failure, cancellation,
crossed unbind, draining, late authentication and observed failure causes.
The endpoint, adversarial, resource, outbind and TLS suites provide independent
raw-peer or actual-pair evidence for those paths.

**Request correlation and publication.** `RequestWindow` owns local sequence,
pending count/bytes and one terminal decision; it neither parses nor sends
frames (S/D). Expected response ID/type and immutable generation permit operation
extension without a second tracker (O). Its handles and value/exception classes
preserve exact once-only settlement, protected future observation, serialization,
cause and transmission-certainty contracts (L). A focused handle gives observation
and cancellation without transferring mutation of the future or sequence allocator
(I). The post-notification-reservation deadline check preserves timeout precedence
without consuming a sequence or byte permit. The endpoint separately rejects
responses for still-unsent handles. Tests cover wrong/late/duplicate identities,
opposite namespaces, explicit clock wrap/deadline boundaries, local terminal
races, shared-notifier pressure and the absence of message replay.

**Application dispatch and replies.** Authentication, handler invocation,
notification publication and reply sequencing are separate bounded owners (S).
Typed immutable operation descriptors compose the implemented catalogue and
operation-specific response rules; the request engine has no command switch
(O). Async callbacks may fail, return null, block during invocation, return an
unfinished stage or fail completion registration; logical timeout must not
release retained physical work (L). Optional registration and separate one-way
handlers avoid unsupported mandatory services (I). User callbacks run on owned
bounded dispatchers; transport callbacks only bridge internal state (D).
The shared connection guard is explicit, callback callouts are outside dispatcher
locks, and no new reverse lock edge was found. Ordered reply accounting includes
the incoming frame, fallback/ready response and active write; FULL retries retain
the same head and original deadline. Inline write completion drains iteratively.
These are covered by the ordered backlog, 6,000-reply inline drain, saturation,
deadline, physical-retention and control-progress regressions.

**Transport and TLS.** `TcpTransport` owns framing, bounded output, absolute
deadlines and physical socket lifecycle; `TcpListener` owns acceptance/handoff,
and `TlsConfig` supplies explicit TLS policy (S). Plain/TLS and client/server
variation reuse the frame contract and initialized JDK context rather than
introducing endpoint state into socket code (O/D). Admission, owned byte copies,
FIFO within each queue class, conservative writer claim, exactly one terminal
callback, failed cleanup reporting and late-success cancellation were traced
against the SPI (L). The ports are small and explicitly internal/nonblocking;
application policy is absent (I). Queue class priority does not preempt an active
write. The raw socket closes before layered TLS cleanup; uncooperative custom
provider work can remain owned and must be reported. Fresh code inspection found
the real adapter already has the guards/retirement barriers missing in the test
fakes. TCP/TLS tests include refused and stalled peers, queued and active expiry,
throwing internal callbacks, provisional accepts, untrusted/identity-mismatched
certificates, mutual authentication and blocked provider cleanup.

**Reconnect, keepalive and observations.** These are policy/observation types,
not another messaging engine (S). Explicit opt-in immutable configurations leave
defaults unchanged and provide bounded policies rather than subclass hooks (O).
Reconnect waits for physical retirement and observer return, uses fresh generations,
counts failed admissions in its finite attempt bound and never copies messages;
offered callbacks retain their ownership after cancellation (L). Separate
termination/current-session/resource views expose only what consumers need (I).
They reuse the endpoint timer, notifier and window (D). Resource snapshots are
sampled, not atomic admission decisions. The full-scope review includes the fresh
two-carrier child-JVM regressions and their exact-thread contention barrier;
the parent forcibly reaps a failed child, bounding deliberately stuck probes.

**Tests and examples.** Tests are organized by observable behavior, with fixture
responsibilities separated from assertions (S). Profile/mode/origin matrices and
focused port seams provide meaningful variation (O). The fake substitutions were
challenged against the real port, exposing the findings above rather than being
accepted from their historical reviews (L). Test callbacks implement only the
required listener/observer surface, and compiled examples use public capabilities
(I/D). The examples are finite loopback demonstrations with explicit demo
credentials, no persistence or handset-delivery claim, independent send/receive
handling and checked cleanup. Timing/network fixtures have finite gates and
deadlines; they do not prove behavior under all host loads. No example, test
transport or reference implementation belongs in the library artifact.

## Coverage classifications

All 133 assigned files are UTF-8 Java text and received a whole-file human read.
None of these 133 entries is binary, automated-only or uninspected. Baseline
hashes below identify the bytes that were reviewed before changes. Four baseline
test files subsequently changed, and one new test was added; final hashes and
all 21 affected Java identities are recorded in the separate fixture report.
Hashes, partition equality, formatting, compilation and test XML are automated
supporting evidence, not substitutes for the inspection. Unassigned SPI sources
were read for the shared contract and coordinated with the protocol reviewer;
they are not silently added to this partition. Eight additional current guides
are accounted separately after the Java ledger. Binary TLS resources, remaining
protocol/profile/codec/session sources, simulator sources, build tooling and
publication metadata have other assigned owners and are not claimed here.

## Baseline Java file ledger

Each row is `whole-file text inspection`; the file-specific note identifies the
responsibility, relevant consumers or behavioral evidence examined. An unqualified
note means no additional concrete finding was confirmed in that file, subject
to the limits above. `F1`–`F5` refer to the corrected fixture findings.

| Baseline file | SHA-256 at 094a84f | Whole-file inspection focus and outcome |
| --- | --- | --- |
| [src/examples/java/kg/aidarbek/examples/ExampleClient.java](../../src/examples/java/kg/aidarbek/examples/ExampleClient.java) | `dfe3dee47e409b03f4782c5a579fbd0980f391c90e62b9bc5a157c110f4374b5` | Public capability composition; bounded bind/submit/delivery/enquiry/drain; demo-only credentials and explicit raw bytes; compiled raw-peer and paired example tests. |
| [src/examples/java/kg/aidarbek/examples/ExampleServer.java](../../src/examples/java/kg/aidarbek/examples/ExampleServer.java) | `0ad505fa0de4f9be7e271761c2522bf38b7d7f9051c49aa4564849253dcbce91` | Finite loopback composition; independent delivery and in-memory submission ID, explicit authentication, no durable or handset-delivery claim; real example tests. |
| [src/main/java/kg/aidarbek/smpp/endpoint/AlertSender.java](../../src/main/java/kg/aidarbek/smpp/endpoint/AlertSender.java) | `532388fef6b3b590d0230af3115b1d7584c6b7a2717e32e941d02e341abd59cc` | Focused one-way capability; each send delegates current lifecycle/requirements and total invocation budget to the existing coordinator, with no response promise. |
| [src/main/java/kg/aidarbek/smpp/endpoint/AuthenticationDispatcher.java](../../src/main/java/kg/aidarbek/smpp/endpoint/AuthenticationDispatcher.java) | `9d30c9f41d3a18da642c01bd089803c0db6c0af221ed85f57a3cbd1667f718df` | Fixed physical invocation/stage ownership, finite queue, supplied inline/rejecting/discarding executor isolation, cancellation and bounded retirement; dispatcher/adversarial tests. |
| [src/main/java/kg/aidarbek/smpp/endpoint/BindAuthenticator.java](../../src/main/java/kg/aidarbek/smpp/endpoint/BindAuthenticator.java) | `738a23a85e9a6a8850d7804098e4672a01b661ea7be802747f1066f3e040b131` | Small async credential-decision boundary; immutable request/peer inputs, non-null decision stage and caller-owned authentication semantics. |
| [src/main/java/kg/aidarbek/smpp/endpoint/BindDecision.java](../../src/main/java/kg/aidarbek/smpp/endpoint/BindDecision.java) | `05968d5fa224ac23c909eb6a91ccafb75109e72af9126625bdcbebbab495120e` | Unsigned status value with shared acceptance constant; cannot itself grant profile or bind permissions; negative/null decision endpoint tests. |
| [src/main/java/kg/aidarbek/smpp/endpoint/BoundSession.java](../../src/main/java/kg/aidarbek/smpp/endpoint/BoundSession.java) | `9c105f146788e1b834579386ee7c653b28f02709caabd09fd899ccd350a58e44` | Focused optional senders and protected lifecycle observations; retained senders revalidate state, resource/congestion snapshots expose no mutable owner. |
| [src/main/java/kg/aidarbek/smpp/endpoint/BroadcastOperations.java](../../src/main/java/kg/aidarbek/smpp/endpoint/BroadcastOperations.java) | `0eac21823b4df826c5e83c4ef945a705f0e2aefa1d2b7df9728e89cabf3a556a` | Three typed descriptors compose existing codecs and original-request response rules; canonical 5.0 error factories and parameter extraction share exchange ownership. |
| [src/main/java/kg/aidarbek/smpp/endpoint/ClientConfig.java](../../src/main/java/kg/aidarbek/smpp/endpoint/ClientConfig.java) | `c5acdce7728839b7c5c7451f65d6f760b9d0b0f01d63c737aed0efbcfe4f0189` | Resolved remote address, immutable raw BindRequest and advertisement requirement; constructor preflight does not infer negotiated profile. |
| [src/main/java/kg/aidarbek/smpp/endpoint/CommonOperations.java](../../src/main/java/kg/aidarbek/smpp/endpoint/CommonOperations.java) | `a749b32d5d1fc1a55ad536fc19a776074e36d8185c2a312806333d333158e096` | Four typed paired descriptors, request/profile-aware negative factories and context validators; distinct query/multi error-body rules, no storage implementation. |
| [src/main/java/kg/aidarbek/smpp/endpoint/CongestionMonitor.java](../../src/main/java/kg/aidarbek/smpp/endpoint/CongestionMonitor.java) | `1416b39560e88f504e5afafce9afd214075fe6b082cc9a5e5dada9ccf310cefc` | Only validated matched 5.0 TLVs update the immutable sample; absent/reserved/stale observations cannot invent a rate or alter admission. |
| [src/main/java/kg/aidarbek/smpp/endpoint/CongestionObservation.java](../../src/main/java/kg/aidarbek/smpp/endpoint/CongestionObservation.java) | `efe5f4c743bfe3943a7c75876511db51c70e01c6b2dced5a49f925d65bcd2e15` | Bounded level and response identity/time sample; record equality and diagnostic values without mutable protocol ownership. |
| [src/main/java/kg/aidarbek/smpp/endpoint/ConnectionAttempt.java](../../src/main/java/kg/aidarbek/smpp/endpoint/ConnectionAttempt.java) | `8290f43d7f29a031672c3c7e4afb041ff2c94b513369ec81221e1fe6e81b48a5` | Cancellation before readiness and protected observation of the same workflow; internal physical retirement remains distinct from readiness failure. |
| [src/main/java/kg/aidarbek/smpp/endpoint/ConnectionLifecycle.java](../../src/main/java/kg/aidarbek/smpp/endpoint/ConnectionLifecycle.java) | `45da4aeb025a8f18f740291627b7df4caa9c0713f76b83580498f3603ce87f9a` | Immutable opt-in TLS/keepalive composition with unchanged plain defaults; initiating/accepting roles validate the appropriate transport policy. |
| [src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java](../../src/main/java/kg/aidarbek/smpp/endpoint/EndpointConnection.java) | `db0798c685671dc7bb3fb062bb62da17c9a4d420945312a0659242f8f4bbbd21` | All request/state/SPI paths, shared coordination guard, admission/deadline/certainty, auth races, reply routing, unbind/drain, keepalive, no replay or socket dependency. |
| [src/main/java/kg/aidarbek/smpp/endpoint/EndpointException.java](../../src/main/java/kg/aidarbek/smpp/endpoint/EndpointException.java) | `589fb84dc38cd80fcd8ae9112ab5de8b6b3e0a79cec91ba88daf9a94d19e1e0e` | Structured endpoint reason with raw status/version/identity, bounded non-payload diagnostics and exception inheritance; version/error assertions. |
| [src/main/java/kg/aidarbek/smpp/endpoint/EndpointHandlers.java](../../src/main/java/kg/aidarbek/smpp/endpoint/EndpointHandlers.java) | `5bedd5b8760ae7b127d937ce873301798673f8af1934d886e150c00347cedee8` | Immutable optional typed registry and alert hook; duplicate/unsupported keys fail, builder changes cannot mutate existing registries. |
| [src/main/java/kg/aidarbek/smpp/endpoint/EndpointOptions.java](../../src/main/java/kg/aidarbek/smpp/endpoint/EndpointOptions.java) | `20feca3f131e4ae8f3e3367c48c205d770315b39acf4ffb2d31d114198e5e942` | Finite positive connection/request/notification/frame/deadline bounds; default values and arithmetic boundaries, validated before worker construction. |
| [src/main/java/kg/aidarbek/smpp/endpoint/EndpointPdus.java](../../src/main/java/kg/aidarbek/smpp/endpoint/EndpointPdus.java) | `bee60a9862cb55185ae44c224b46065e6def8c4832743d3261a2ca1a4864ce20` | Concrete codec composition at endpoint boundary; original request direction selects data codecs, canonical negatives and invalid-sequence nack-zero fallback. |
| [src/main/java/kg/aidarbek/smpp/endpoint/EndpointResources.java](../../src/main/java/kg/aidarbek/smpp/endpoint/EndpointResources.java) | `2d3ac470481fed880e4ae1c42c7ae3bcfa36feeafb3fd76689159e2750fac794` | Global permits through physical cleanup, dispatcher ownership, graceful/abort convergence, listener/reconnect registration and bounded termination snapshot. |
| [src/main/java/kg/aidarbek/smpp/endpoint/EndpointTermination.java](../../src/main/java/kg/aidarbek/smpp/endpoint/EndpointTermination.java) | `ecfaeba84960ce9b222ebb97114c14361857933f105fd1d4208cbd09eda916ee` | Immutable counts/worker flags/original cleanup failures; complete requires actual zero retention and no failures, not merely a completed stage. |
| [src/main/java/kg/aidarbek/smpp/endpoint/ExchangeConfig.java](../../src/main/java/kg/aidarbek/smpp/endpoint/ExchangeConfig.java) | `8472cc11755ccbbf7ef9246dfa4c20e76e5daf811c03c6d2bbfa26fb261845eb` | Immutable pairing of exchange limits and optional handlers; empty defaults preserve existing constructors without mandatory application services. |
| [src/main/java/kg/aidarbek/smpp/endpoint/ExchangeOptions.java](../../src/main/java/kg/aidarbek/smpp/endpoint/ExchangeOptions.java) | `0b46bbe97e4af9716a4bb924a30845727aa0e712b94462b266df11c85c96bfb0` | Finite handler concurrency/queue and reply count/byte limits with total decision timeout; validation supports bounded retention and fallback reservation. |
| [src/main/java/kg/aidarbek/smpp/endpoint/HandlerDispatcher.java](../../src/main/java/kg/aidarbek/smpp/endpoint/HandlerDispatcher.java) | `04aa4203b6c4bc15933ba4ca778bd46a79082c9453b606bdb1a4168c1640b95b` | Per-session invocation lane with global physical concurrency, queue cancellation, invocation/stage completion barrier and registration-failure retention; coordinated tests. |
| [src/main/java/kg/aidarbek/smpp/endpoint/HandlerResponse.java](../../src/main/java/kg/aidarbek/smpp/endpoint/HandlerResponse.java) | `85a595153a156407f86b2f57936bfa077d2629c58a2ed5a999585a0558937294` | Typed unsigned status/body with non-null stricter requirements; cannot choose sequence or bypass response/field validation. |
| [src/main/java/kg/aidarbek/smpp/endpoint/IncomingNotification.java](../../src/main/java/kg/aidarbek/smpp/endpoint/IncomingNotification.java) | `2a9198481e6c584eb1ffc3e77d0f9508718fc6977a5b49a143e5437fe4c05ed1` | Immutable session/command/deadline context with cooperative cancellation observation; no fabricated response ownership. |
| [src/main/java/kg/aidarbek/smpp/endpoint/IncomingRequest.java](../../src/main/java/kg/aidarbek/smpp/endpoint/IncomingRequest.java) | `69e8ea67c299aa5798399cc589e1c288a31196085cdd64665bced6c58d493cb9` | Immutable PDU/session/deadline context and cooperative cancellation; handler receives no mutable request-window or transport handle. |
| [src/main/java/kg/aidarbek/smpp/endpoint/KeepalivePolicy.java](../../src/main/java/kg/aidarbek/smpp/endpoint/KeepalivePolicy.java) | `cdbac8fa715e74af8700755de7ea3320cd531c4d054ae09c494c476abfa3ab1d` | Positive bounded idle and response budgets; optional policy value rather than an independent scheduler or extra request window. |
| [src/main/java/kg/aidarbek/smpp/endpoint/MessageCenterBinding.java](../../src/main/java/kg/aidarbek/smpp/endpoint/MessageCenterBinding.java) | `fb4fcbc7491c440672f37b5bfec7110a0b6432faea5fdbc5bc8fe5c0ec4bf537` | Internal accepted/advertised version and system-ID policy reused by server/outbind connector without a dummy listener address. |
| [src/main/java/kg/aidarbek/smpp/endpoint/MessageExchange.java](../../src/main/java/kg/aidarbek/smpp/endpoint/MessageExchange.java) | `0f0c4c9f816f05667203a3ef4386e8d7470f43f9dc268d4ff8148c268a50c4ee` | Ordered bounded fallback/ready reply ownership through active settlement, original handler/write deadlines, FULL retry, iterative inline drain and physical handler cancellation. |
| [src/main/java/kg/aidarbek/smpp/endpoint/MessageOperations.java](../../src/main/java/kg/aidarbek/smpp/endpoint/MessageOperations.java) | `8249b9c6620b947ec71777c3b84d7eb9fd5e746ae2403dd678c5925306273375` | Submit/deliver/data typed descriptors, explicit original-request message direction, actual optional-parameter requirements and context validation. |
| [src/main/java/kg/aidarbek/smpp/endpoint/NotificationExchange.java](../../src/main/java/kg/aidarbek/smpp/endpoint/NotificationExchange.java) | `a5d2d0ed318840f8f2ce7360c2643f0f7c9d10d52696e5c15e1ccf66acf206a0` | One-way local-write result ownership, shared sequence allocator, bounded notification hooks/lane, cancellation guard and cleanup without a second response tracker. |
| [src/main/java/kg/aidarbek/smpp/endpoint/NotificationHandler.java](../../src/main/java/kg/aidarbek/smpp/endpoint/NotificationHandler.java) | `80efbeff271d5746abf232882f358569d0d80bbacb6e4762b6154ed312f52fdf` | Focused async one-way application contract; completion indicates local handling and cannot invent a protocol acknowledgement. |
| [src/main/java/kg/aidarbek/smpp/endpoint/NotificationSend.java](../../src/main/java/kg/aidarbek/smpp/endpoint/NotificationSend.java) | `cb0e17a5f7e248ba2e6a479ae411cf845463c8dcb961defed2c69c19c5fe0a0b` | Protected local-write observation with explicit queued cancellation and sequence; no remote acceptance or replay guarantee. |
| [src/main/java/kg/aidarbek/smpp/endpoint/Operation.java](../../src/main/java/kg/aidarbek/smpp/endpoint/Operation.java) | `5c581585a4bd3a9622422cb7949a0cc1963a647eae8368f05462b16a43876e9b` | Closed supported descriptor boundary, exact request/response types, negative factory, codec/context/requirement collaborators; unsupported keys cannot bypass catalogue checks. |
| [src/main/java/kg/aidarbek/smpp/endpoint/OperationCatalog.java](../../src/main/java/kg/aidarbek/smpp/endpoint/OperationCatalog.java) | `8e4715f708c80be374f8f7515ac04d92fb61f755e61f7052ed61732b2d2f7faf` | Immutable registered paired operation lookup and complete codec composition; no mutable application registry or alternate correlation engine. |
| [src/main/java/kg/aidarbek/smpp/endpoint/OperationSender.java](../../src/main/java/kg/aidarbek/smpp/endpoint/OperationSender.java) | `c8894985030c83c834d1894356d282cda2eab10684a389af40af39459cbcb518` | Typed focused forwarding API; default/explicit deadlines and stricter requirements delegate to the same session send path. |
| [src/main/java/kg/aidarbek/smpp/endpoint/OutbindAuthenticator.java](../../src/main/java/kg/aidarbek/smpp/endpoint/OutbindAuthenticator.java) | `804765f568659d03276d22644750d340bbba1099f03c09f866c38b85213bce98` | Small async MC-credential decision on immutable notification/peer; independent from subsequent ESME bind authentication. |
| [src/main/java/kg/aidarbek/smpp/endpoint/OutbindConnector.java](../../src/main/java/kg/aidarbek/smpp/endpoint/OutbindConnector.java) | `08f1df93b0a94e80e1dae82e134ec130c67aa2fbb174d5ba4f493ab25679074f` | Explicit reversed TCP owner, one notification/attempt, bounded authentication and optional fresh reconnect controller; no message replay or automatic default loop. |
| [src/main/java/kg/aidarbek/smpp/endpoint/OutbindConnectorConfig.java](../../src/main/java/kg/aidarbek/smpp/endpoint/OutbindConnectorConfig.java) | `5811f436a7100b1cba048c760e432be70c08e7d614ce856a51a1616fab752972` | Immutable accepted-version set, advertisement/system ID and authentication bounds; no invented remote address or implicit version negotiation. |
| [src/main/java/kg/aidarbek/smpp/endpoint/OutbindFlow.java](../../src/main/java/kg/aidarbek/smpp/endpoint/OutbindFlow.java) | `0db7fe8bd6a50618fba603e75f565c02923845a55ac8db65b931d0b2bc8a544e` | Same-socket notification/auth/follow-up-bind state, one total deadline and late-result suppression; shared physical handler ownership. |
| [src/main/java/kg/aidarbek/smpp/endpoint/OutbindListener.java](../../src/main/java/kg/aidarbek/smpp/endpoint/OutbindListener.java) | `b33cae913494337f0350b385a46f094fce9093c8d05eaf9c62d68c8d001e7222` | Reversed accepting owner and bounded admission/shutdown; configured ESME bind follows authenticated outbind, with ports created only at composition. |
| [src/main/java/kg/aidarbek/smpp/endpoint/OutbindListenerConfig.java](../../src/main/java/kg/aidarbek/smpp/endpoint/OutbindListenerConfig.java) | `d073b15688dd7993ed8de61e0af73310f70d980ab2837ac92f5bbb856fedf1e1` | Resolved listen address and fixed immutable bind; explicit 3.4 receiver restriction before listener allocation. |
| [src/main/java/kg/aidarbek/smpp/endpoint/ReconnectHandle.java](../../src/main/java/kg/aidarbek/smpp/endpoint/ReconnectHandle.java) | `63c1ec6474035c4e0c836945b079a33ce0ea88718d4b1b6251b30b6573be4005` | Finite attempts, private physical retirement barrier, backoff, bounded observer offers and cancel/endpoint-close precedence; reconnect failure and two-carrier tests. |
| [src/main/java/kg/aidarbek/smpp/endpoint/ReconnectPolicy.java](../../src/main/java/kg/aidarbek/smpp/endpoint/ReconnectPolicy.java) | `e9e31141019761420c7a3a18774f72a7c2ca64db36218adebf5661983adcd23e` | Explicit finite attempt count and nonnegative bounded delay; initial/admission attempts consume the budget and no replay settings exist. |
| [src/main/java/kg/aidarbek/smpp/endpoint/ReconnectResult.java](../../src/main/java/kg/aidarbek/smpp/endpoint/ReconnectResult.java) | `a458f417141a8c556e250c6c5f75b84998b84786b79bbfbfa331ca5bf10be2ef` | Immutable terminal reason/counts/latest failure; does not claim all endpoint callbacks stopped before bounded endpoint termination. |
| [src/main/java/kg/aidarbek/smpp/endpoint/RequestHandler.java](../../src/main/java/kg/aidarbek/smpp/endpoint/RequestHandler.java) | `c28ce28dc5b9bd2f2c4d70f65ca882061869d1257b2667da6d1ff792dddf879d` | Focused generic async paired-decision contract; application owns storage/acceptance, coordinator owns identity and response validation. |
| [src/main/java/kg/aidarbek/smpp/endpoint/ServerConfig.java](../../src/main/java/kg/aidarbek/smpp/endpoint/ServerConfig.java) | `c3ea6652e92a681191fec30e6c9393312be8456ecda69458b22fd99533a0614f` | Resolved listener identity, immutable accepted versions, separate advertised version and bounded authentication policy; atomic constructor validation. |
| [src/main/java/kg/aidarbek/smpp/endpoint/SessionResources.java](../../src/main/java/kg/aidarbek/smpp/endpoint/SessionResources.java) | `98884e66a7807fd743c56256c5c5e5d86225a9af26b3e6bfa439717e11169632` | Immutable sampled request/reply counters; no transport queue estimate, atomic admission promise or mutable internal handles. |
| [src/main/java/kg/aidarbek/smpp/endpoint/SmppClient.java](../../src/main/java/kg/aidarbek/smpp/endpoint/SmppClient.java) | `11f9a186c26230430f53bacdd36fab35c28b274ee5b42e242981b0b9087e25ff` | Concrete outgoing TCP/TLS composition, connection-attempt cancellation, optional reconnect, global resource ownership and protected termination. |
| [src/main/java/kg/aidarbek/smpp/endpoint/SmppServer.java](../../src/main/java/kg/aidarbek/smpp/endpoint/SmppServer.java) | `4809c4ff5b17e7fb0740b760b29a676758bee31f5614d3c5eb0251a44c049255` | Concrete accepting composition, explicit start/close guard, validated graceful shutdown before mutation, auth worker ownership and immutable session snapshots. |
| [src/main/java/kg/aidarbek/smpp/request/BoundedNotifications.java](../../src/main/java/kg/aidarbek/smpp/request/BoundedNotifications.java) | `58c24608b4ab46604363c05865a342c5bd10cf1d44f425982e1c4b73491a36fb` | Reservation-before-admission, fixed workers, callback execution outside lock, physical permit retention, Condition waiting and bounded last-failure diagnostics. |
| [src/main/java/kg/aidarbek/smpp/request/PeerNackException.java](../../src/main/java/kg/aidarbek/smpp/request/PeerNackException.java) | `b0b0805a71e3b1790d4a330f1c382bc8f820c361129f7cce1069d7b8cecd50ba` | Known peer outcome preserves generation/status/sequence and owned ordered TLV snapshots through exception serialization; no local-ambiguity claim. |
| [src/main/java/kg/aidarbek/smpp/request/RequestFailure.java](../../src/main/java/kg/aidarbek/smpp/request/RequestFailure.java) | `a2fca2ba67c9739783e034dff3042395e36f9c6ded2a1824d8fc3415f1c0a7cf` | Structured local reason/identity/sequence/certainty/cause; serializable primitive fields and bounded diagnostics, distinct from peer response status. |
| [src/main/java/kg/aidarbek/smpp/request/RequestHandle.java](../../src/main/java/kg/aidarbek/smpp/request/RequestHandle.java) | `9cd26eba1db35fcaaa31437f8f876f15585dea4fdcf85d9c18214842abb42c53` | Protected CompletionStage and owner-routed cancel, visible terminal snapshot, exact request header/deadline and certainty without exposed mutable future. |
| [src/main/java/kg/aidarbek/smpp/request/RequestIdentity.java](../../src/main/java/kg/aidarbek/smpp/request/RequestIdentity.java) | `cabca5aa327fe54d010cdd55a19c8204578aa5d0f065d7d148b68fa695b69d17` | Immutable UUID generation plus usable sequence, equality contract prevents cross-generation identity confusion. |
| [src/main/java/kg/aidarbek/smpp/request/RequestOptions.java](../../src/main/java/kg/aidarbek/smpp/request/RequestOptions.java) | `4e10fc026df29102345210c8b711bafd6bbb14e7aa714f85821a48a9c6579a1a` | Positive total invocation Duration with representable nanoseconds; no independent read/write reset or hidden retry settings. |
| [src/main/java/kg/aidarbek/smpp/request/RequestOutcome.java](../../src/main/java/kg/aidarbek/smpp/request/RequestOutcome.java) | `0e9ac1c2825363c23b7ec4d869ee81290a0d34fc21d6005c70553caa1bac2db0` | Exactly one typed response or local/peer failure in an immutable terminal value; optionals preserve the selected alternative. |
| [src/main/java/kg/aidarbek/smpp/request/RequestWindow.java](../../src/main/java/kg/aidarbek/smpp/request/RequestWindow.java) | `45c0f5767eb7ce5a9a190ef44f06c202c7991750b13b02a2d365d2769d817460` | Single monotonic non-reused sequence and pending owner, exact generation/type/command correlation, deadline precedence after notifier contention, counts/bytes released exactly once. |
| [src/main/java/kg/aidarbek/smpp/request/TransmissionCertainty.java](../../src/main/java/kg/aidarbek/smpp/request/TransmissionCertainty.java) | `b2ead7b789fddb5f41d442e7a27b3a5df1f26eebc3625d6f0dfc16d1020b8ac4` | Two explicit conservative local-write states; cannot represent peer acceptance or convert timeout into guaranteed rejection. |
| [src/main/java/kg/aidarbek/smpp/transport/AcceptListener.java](../../src/main/java/kg/aidarbek/smpp/transport/AcceptListener.java) | `4418102601754125803056bc6992ff1b11f413cb756dc294d1d75b4b678d3a50` | Focused bounded synchronous ownership decision; peer metadata and unstarted frame port, no application authentication policy in the socket loop. |
| [src/main/java/kg/aidarbek/smpp/transport/TcpListener.java](../../src/main/java/kg/aidarbek/smpp/transport/TcpListener.java) | `e6ef52fd1136918dc0d5f4bc8612e468eeb1b9ada09a78970a3f2abca3fbd94f` | One accept loop and provisional handoff ownership, reject/throw/close cleanup barriers, child physical retirement and original cleanup failure reporting. |
| [src/main/java/kg/aidarbek/smpp/transport/TcpTransport.java](../../src/main/java/kg/aidarbek/smpp/transport/TcpTransport.java) | `743a9570cd43e4ebcd3f91143be6848cf5833e3d107357841c519dbc858e2f7f` | Queued/active count and byte limits, framed reads, guard-before-output, total deadlines, control priority, physical close/worker/callback barriers and TLS layering. |
| [src/main/java/kg/aidarbek/smpp/transport/TcpTransportConfig.java](../../src/main/java/kg/aidarbek/smpp/transport/TcpTransportConfig.java) | `8b4f12bdbf8c8da7624dcefc03d64b7b6d8319fbd7683afca4ea24fb3fd38939` | Independent ordinary/control bounds and maximum frame, optional send-buffer hint; validated values do not silently reserve unbounded queues. |
| [src/main/java/kg/aidarbek/smpp/transport/TlsConfig.java](../../src/main/java/kg/aidarbek/smpp/transport/TlsConfig.java) | `e8bc7cda5ca25f10c3ed6f0191d13ba0fd688089425624454db284f72aaaea96` | Explicit client identity, TLS protocols, trust/key context ownership, bounded handshake and optional client certificates; no permissive defaults or secret diagnostics. |
| [src/test/java/kg/aidarbek/smpp/endpoint/AlertEndpointTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/AlertEndpointTest.java) | `fc4c64451fdb0711aba457d0a8f401ae219383d95add974122f1a793cb77df7e` | Real alert direction/profile/mode routing and missing-handler behavior; local write completion remains distinct from a remote response. |
| [src/test/java/kg/aidarbek/smpp/endpoint/AlertHandlerContractTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/AlertHandlerContractTest.java) | `7cab213be83326b1c09d1752ef8b935abbd11d02c0c0360cac76cfecd030688b` | Bounded mixed message/alert invocation order, failure/deadline closure and physical callback retention with controlled transport and stage gates. |
| [src/test/java/kg/aidarbek/smpp/endpoint/AlertWriteContractTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/AlertWriteContractTest.java) | `5e8bbce24b86f945f9f45b4f16e83f496cd6843f0e463b6c24ce30018a327697` | Local notification sequence/cancel/admission/drain and shared real/fake contract; F4 corrected the guard-failure cleanup expectation while preserving terminal-failure checks. |
| [src/test/java/kg/aidarbek/smpp/endpoint/AuthenticationDispatcherTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/AuthenticationDispatcherTest.java) | `7f91d5921728da6e48f4a4327825cf7b7aa834a01c0db8e7a81da793cad7a6c5` | Inline/rejecting supplied executor, blocked invocation/stage, queue limits, cancellation and shutdown; callback work stays off network progress. |
| [src/test/java/kg/aidarbek/smpp/endpoint/BroadcastEndpointTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/BroadcastEndpointTest.java) | `46157ce5c835d487b5ce290f8c30a4e3f5d99f6ec00e8da8e28c205bb6d90172` | Actual typed broadcast/query/cancel exchange under version/role/mode permissions, absent handlers and response validation. |
| [src/test/java/kg/aidarbek/smpp/endpoint/BroadcastPeerTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/BroadcastPeerTest.java) | `88dd3744dcc60a3d5f7ba99c410d8a3456fd204ab1e3b610a2b73473197a23ac` | Independent raw broadcast peers for malformed/unsupported requests, handler result errors and paired original-request context. |
| [src/test/java/kg/aidarbek/smpp/endpoint/CarrierContention.java](../../src/test/java/kg/aidarbek/smpp/endpoint/CarrierContention.java) | `f2c0fa0159bdbf0ec426f9e5da746cb0b68edcd19e463d3a17f9e03eb237dd0d` | Exact two-caller ReentrantLock barrier, finite ordering checks and completion observation; used only inside forcibly reaped child JVMs. |
| [src/test/java/kg/aidarbek/smpp/endpoint/CommonOperationsEndpointTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/CommonOperationsEndpointTest.java) | `0a152d70bee733bb9e5995be055040c18b145b800fe01cf5fb472c9873b9daf5` | Real query/cancel/replace/multi matrices and profile-specific negative bodies, typed async handlers and retained sender state checks. |
| [src/test/java/kg/aidarbek/smpp/endpoint/CommonTestTransport.java](../../src/test/java/kg/aidarbek/smpp/endpoint/CommonTestTransport.java) | `d8d4ceeaee63e683cb192d53419f52253d2538b54ac54cd56e47ebe434846417` | Manually claimed/settled finite port; F3/F4/F5 corrected original deadlines and connection-versus-cleanup failure/cause semantics. |
| [src/test/java/kg/aidarbek/smpp/endpoint/EndpointAdversarialTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/EndpointAdversarialTest.java) | `03945a23e29a58ff6b555df86520a6272927f31e6293b0dc1aaad04ed0fa4ccf` | Raw peers exercise malformed/unexpected traffic, idle and failed binds, pending control work, bounded shutdown and supplied-executor survival. |
| [src/test/java/kg/aidarbek/smpp/endpoint/EndpointCongestionTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/EndpointCongestionTest.java) | `301af4e47ccc1edecd0a92c3eaae89f4b9df8a2b0935f3e87f09ccea79192023` | Matched-only sample publication after bind/operation/control/nack validation; duplicate, wrong, missing/reserved and old-profile input cannot overwrite it. |
| [src/test/java/kg/aidarbek/smpp/endpoint/EndpointConnectionTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/EndpointConnectionTest.java) | `b3aa03838a807142ccf61b1f876c2857ae7d977ff6993a78ec62d43fa3ebe44e` | Coordinator over a port: bind/control correlation, early unsent response refusal, total invocation deadline, write certainty and cleanup causes. |
| [src/test/java/kg/aidarbek/smpp/endpoint/EndpointExamplesTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/EndpointExamplesTest.java) | `a2c107155c126108b1fc782acb4998d0f5cc0c5b2fcd1dbded239f1dcac336e2` | Compiled client/server plus independent raw-peer observations; real submit/deliver/enquiry and graceful reply drain, bounded process work. |
| [src/test/java/kg/aidarbek/smpp/endpoint/EndpointPdusTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/EndpointPdusTest.java) | `308161075607c7236c4164bd8e9214a83ad09ec249ecbb5f4bdf657c965fbbe3` | Endpoint codec composition/direction and independent canonical negative frames including invalid original sequence fallback. |
| [src/test/java/kg/aidarbek/smpp/endpoint/EndpointResourcesTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/EndpointResourcesTest.java) | `4da1ea1f9bc84ef31e85ffac6f24b1f0e56aca9507082bba794509751cb7db27` | Connection permit rollback and retirement, failed transport cleanup, bounded callbacks and immutable termination outcomes. |
| [src/test/java/kg/aidarbek/smpp/endpoint/EndpointValuesTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/EndpointValuesTest.java) | `ab965e2de75917572fcd72d775986f411a9bfc6143a36c5825a18df303982108` | Configuration/null/range and defensive-copy contracts, endpoint diagnostics and value invariants used by public callers. |
| [src/test/java/kg/aidarbek/smpp/endpoint/ExchangeCapabilitiesTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/ExchangeCapabilitiesTest.java) | `32ef8709563d86641da53ad1015c57449d1d5715460dea43852911807018d58d` | Optional senders follow effective profile/role/mode and current state; actual TLVs/5.0 fields cannot bypass missing-advertisement restrictions. |
| [src/test/java/kg/aidarbek/smpp/endpoint/ExchangeConnectionTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/ExchangeConnectionTest.java) | `b76368bc818095f34f0aaad4c39c452d53ddb99ad2d911d1c8f4ad68398feab9` | Controlled paired exchange checks response/context failure, reply counts/bytes, ordering, transport FULL and inline drain without recursive stack growth. |
| [src/test/java/kg/aidarbek/smpp/endpoint/ExchangeFailureTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/ExchangeFailureTest.java) | `edcf853af84e65c7c7cbf0c653f671c90b4888591c9e224543db3f17ff8c68ca` | Real handler absent/throw/null/failure/slow behavior, overload negatives, close/cancel and retained physical capacity across sessions. |
| [src/test/java/kg/aidarbek/smpp/endpoint/ExchangeMatrixTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/ExchangeMatrixTest.java) | `095a78b41b4c0a6e9f70ec2a0e1fb75064490f0f66bd9b26e33b6b7a470bda93` | Real submit/deliver/data paired exchanges and negative replies under both profiles and all permitted endpoint/mode directions. |
| [src/test/java/kg/aidarbek/smpp/endpoint/ExchangeSessionTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/ExchangeSessionTest.java) | `a97371de3bb1ea5d4d40f7ca356e617320569bf3662f78aff2a31e27f7df45e0` | Facade/handler registration and protected request results over real endpoints; asynchronous decisions, payload preservation and graceful exchange lifecycle. |
| [src/test/java/kg/aidarbek/smpp/endpoint/ExchangeValuesTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/ExchangeValuesTest.java) | `af29a9760d61b452bbeaa7f135debe5fb076233666467b9b2729a0e4f713cc8f` | Immutable registry/config/context/response validation, explicit requirements and no mutable builder leakage. |
| [src/test/java/kg/aidarbek/smpp/endpoint/FakeFrameTransport.java](../../src/test/java/kg/aidarbek/smpp/endpoint/FakeFrameTransport.java) | `63f15da582940d86f1f46db07beb4fe15420f4a1d38670ab2a31c96be4bc0c75` | Immediate/deferred bounded port; F1/F2/F5 corrected claimed write races, active listener retirement and queued abort reason preservation. |
| [src/test/java/kg/aidarbek/smpp/endpoint/FakeFrameTransportTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/FakeFrameTransportTest.java) | `ddc194f6448522ea57252b59ebaaad2dd65d36cd28ecc5f782451d5b3799afd4` | Original terminal observer checks missed delayed races; now actual F1/F2/F5 regressions assert bytes, causes, callback barriers and successful/failed cleanup independently. |
| [src/test/java/kg/aidarbek/smpp/endpoint/HandlerDispatcherTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/HandlerDispatcherTest.java) | `5eb2e32acabf4ce97ff73aebc66fff2565d4223a28e65a021c61b6bbbb49f0f7` | Physical invocation/stage ownership, mixed-lane ordering, deadline/cancel races, queue bounds and throwing completion-registration behavior. |
| [src/test/java/kg/aidarbek/smpp/endpoint/KeepaliveEndpointsTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/KeepaliveEndpointsTest.java) | `65465ae92ff46b200d91afeb83a6a075d7bb562096540d03b6c59d6d35d5d255` | Actual raw peers verify periodic enquiry and unresponsive-link cleanup for all four owner kinds, with finite resources and preserved timeout identity. |
| [src/test/java/kg/aidarbek/smpp/endpoint/KeepaliveTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/KeepaliveTest.java) | `c19cfdcb8899497ff5731d2e31bd1f93fb26bb72dcb5d79eecd0b41175fa1bb5` | Controlled monotonic idle budget, one heartbeat, admission saturation, manual/control reserves and original timeout/nack/write causes. |
| [src/test/java/kg/aidarbek/smpp/endpoint/OutbindConfigTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/OutbindConfigTest.java) | `60968978cf1e09da3d8400ba28c850a0d49a7d57f85e8a1efb5ed7a57cb70ce3` | Explicit version/mode/address and authentication configuration validation, including 3.4 receiver-only follow-up binding. |
| [src/test/java/kg/aidarbek/smpp/endpoint/OutbindEndpointTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/OutbindEndpointTest.java) | `d4dc5806d7f34dc7f4a3b4fbd96175a780c4ff5194f3ad5c177b20538dac4ed2` | Actual reversed owners across valid profile/mode flows, authenticated notification before bind and independent ESME/MC credentials. |
| [src/test/java/kg/aidarbek/smpp/endpoint/OutbindLifecycleTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/OutbindLifecycleTest.java) | `bcd30b0861ef483f84dd47a2aff21846a1d2ce7d4b0a0c24afdc20cb9c640962` | Raw cancellation, duplicate/stalled/rejected outbind, late authentication and admission failure cleanup; physical stage retention remains bounded. |
| [src/test/java/kg/aidarbek/smpp/endpoint/RawPeer.java](../../src/test/java/kg/aidarbek/smpp/endpoint/RawPeer.java) | `47a968a2df73b9e075a8429daa7614c1c3514c715d61a7e8e39b69ac858028dc` | Finite local socket helper with explicit read bounds, owned frame bytes, caller-driven malformed frames and independent header construction. |
| [src/test/java/kg/aidarbek/smpp/endpoint/ReconnectControlTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/ReconnectControlTest.java) | `e106f31c65b9a0d5e3feacc5b76c2c21e8e2848a7598aff69f04ec7b783ca530` | Injected clock/backoff/finite budget, cancel-before-attempt, physical retirement failure and graceful endpoint-stop precedence. |
| [src/test/java/kg/aidarbek/smpp/endpoint/ReconnectEndpointsTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/ReconnectEndpointsTest.java) | `d637ba54834be4523b708c6c5e6417b9ed8e52ce5500236dccaa587b07122b9f` | Real fresh generations and sequence restart, explicit normal/outbind reconnect counts, off-I/O observer and cancellation cleanup. |
| [src/test/java/kg/aidarbek/smpp/endpoint/ReconnectFailureTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/ReconnectFailureTest.java) | `efa5aec3a5a76e3ac3fe7bb6e56223e2411cbc199adf63d66f5ac8d7db29023b` | Real auth cancellation, notification saturation, blocked/throwing observer and ambiguous submission with independently observed no replay. |
| [src/test/java/kg/aidarbek/smpp/endpoint/SessionResourcesTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/SessionResourcesTest.java) | `95e115bc9df69ef9e2e45d2b74b9bbdf7a15de451224ba199752f4f53b068a95` | Diagnostic request/reply counts and retained bytes through delayed handlers and physical writes; no assertion that the snapshot is transactional admission. |
| [src/test/java/kg/aidarbek/smpp/endpoint/Smpp5ServicesTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/Smpp5ServicesTest.java) | `5217023f469f348e92c5ea5025b41ce8c7b8be8fad0a00572b4d4d0e5b4899e0` | Actual 5.0 application service fields/TLVs through both roles, preserving receipt/routing/billing values without implementing those business services. |
| [src/test/java/kg/aidarbek/smpp/endpoint/SmppEndpointsTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/SmppEndpointsTest.java) | `023ac36eb3ce70ad63fd3e477d6173d56dd8cf559881e93519b63fa314d17926` | Real ordinary bind matrix with both unbind origins, authentication outcomes and protected endpoint ownership/cleanup. |
| [src/test/java/kg/aidarbek/smpp/endpoint/TlsBindingMatrixTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/TlsBindingMatrixTest.java) | `be8d56be0e32f66a4a6be62b3d1f02fc22f6b2dd465d9b2ce6cd9e12201c131c` | Actual TLS ordinary/reversed role and valid mode/profile matrix, both unbind origins, and untrusted outbind that never reaches credentials. |
| [src/test/java/kg/aidarbek/smpp/endpoint/TlsEndpointDeadlineTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/TlsEndpointDeadlineTest.java) | `de9dab46211564f4abde8d449e06a1cd93f5035869e24d071e67ec84e204881b` | Real stalled handshake distinguishes TCP/TLS budgets and accepted total bind deadline; finite socket and worker cleanup. |
| [src/test/java/kg/aidarbek/smpp/endpoint/TlsEndpointsTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/TlsEndpointsTest.java) | `1999cf22e8181d7767557f969a1b488c16b0bbde00e47bed121f032faef868d0` | Untrusted TLS and plaintext-to-TLS peers never reach bind authentication; observed transport failure and cleanup rather than fabricated bind rejection. |
| [src/test/java/kg/aidarbek/smpp/endpoint/TlsHandlerLifecycleTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/TlsHandlerLifecycleTest.java) | `dd72084279d26666e73586d738db0865b255899694f48d44ebdc72a817704bd0` | Real TLS control traffic proceeds during blocked handler invocation/stage; bounded shutdown reports retained physical handler after socket abort. |
| [src/test/java/kg/aidarbek/smpp/endpoint/VirtualThreadProgressProbe.java](../../src/test/java/kg/aidarbek/smpp/endpoint/VirtualThreadProgressProbe.java) | `8dc2381f6651d45cc17be4f52e7b1f2d6e77f9f72344688e39f292ca5f99bac3` | Child executable exercises real notifier/resource contention in response/admission/reconnect/listener paths and observes complete retirement. |
| [src/test/java/kg/aidarbek/smpp/endpoint/VirtualThreadProgressTest.java](../../src/test/java/kg/aidarbek/smpp/endpoint/VirtualThreadProgressTest.java) | `ba559f5c639e500009a94b8b3104b62db857a58fb9f9e9ed5303d0b94ff04f04` | Nine separate two-carrier JVM cases with explicit scheduler bounds, retained output and forced process reaping on timeout/failure. |
| [src/test/java/kg/aidarbek/smpp/request/BoundedNotificationsTest.java](../../src/test/java/kg/aidarbek/smpp/request/BoundedNotificationsTest.java) | `c26761dc2c1e6a759e81979f4dfcf27e9624bc48a18600bc9d6f8741d213a28c` | Reservation/queue/worker limits, ordered single-worker dispatch, callback failures, blocked dependents and bounded shutdown. |
| [src/test/java/kg/aidarbek/smpp/request/NotificationSequenceTest.java](../../src/test/java/kg/aidarbek/smpp/request/NotificationSequenceTest.java) | `3579023ee01eb8020447f38b9b7abcfc1b3f4b3501cad94b698d055ba6f63cd1` | One-way and paired operations share non-reused allocation without a response entry; exhaustion/closed/invalid IDs retain admission invariants. |
| [src/test/java/kg/aidarbek/smpp/request/RequestAdmissionContentionTest.java](../../src/test/java/kg/aidarbek/smpp/request/RequestAdmissionContentionTest.java) | `f7148d6c07a5ad2f87b6eca3407d6b804fa65a0a2cd62009e15f16021a7d50f6` | Controlled notifier lock plus monotonic clock proves deadline wins after both successful and empty reservation attempts, without sequence/byte/permit loss. |
| [src/test/java/kg/aidarbek/smpp/request/RequestConcurrencyTest.java](../../src/test/java/kg/aidarbek/smpp/request/RequestConcurrencyTest.java) | `eb0437254bd57aefdf0e62147935dc79697e9e872f80d0e5e4cf9d82de9a0d81` | Coordinated competing terminal paths and shared callbacks; one winner, no duplicate publication and retained physical notifier ownership. |
| [src/test/java/kg/aidarbek/smpp/request/RequestOptionsTest.java](../../src/test/java/kg/aidarbek/smpp/request/RequestOptionsTest.java) | `5bb5f04da5ff39e4b5a41804f3b6e4b466b7cba3bba6bb8b638e2e357f74c08c` | Positive/overflow/null duration validation and exact total nanosecond budget, including boundary values. |
| [src/test/java/kg/aidarbek/smpp/request/RequestValuesTest.java](../../src/test/java/kg/aidarbek/smpp/request/RequestValuesTest.java) | `62df0f45c3a3f6211762331eac22e0ad4763df75bfded66d2804384ee2cc367e` | Immutable request identity/outcome fields, structured local/peer exception serialization and protected result observation. |
| [src/test/java/kg/aidarbek/smpp/request/RequestWindowTest.java](../../src/test/java/kg/aidarbek/smpp/request/RequestWindowTest.java) | `fde723ea3610f851ec951394f5bbfbad44f7585b7c5130e3bae83aaf16d97396` | Independent command/generation/sequence/type matching, nack distinctions, out-of-order replies, certainty and all terminal/admission reasons. |
| [src/test/java/kg/aidarbek/smpp/transport/TcpAdmissionTest.java](../../src/test/java/kg/aidarbek/smpp/transport/TcpAdmissionTest.java) | `56c66582cd02eb2d3c57b513b8fd36de565a8e0b6c6bf53e81430f5d8758a157` | Finite separate count/byte reserves, pre-start/closed/expired rejection, owned copies and queue cancellation without physical bytes. |
| [src/test/java/kg/aidarbek/smpp/transport/TcpConnectTest.java](../../src/test/java/kg/aidarbek/smpp/transport/TcpConnectTest.java) | `195840ccf5153db18a22f451d7b6428ea1166be6e9941f0e531b12eeb07d6826` | Actual connect/start/refusal/close races, resolved-address requirement and ownership of connecting socket and callbacks. |
| [src/test/java/kg/aidarbek/smpp/transport/TcpDeadlineTest.java](../../src/test/java/kg/aidarbek/smpp/transport/TcpDeadlineTest.java) | `5aef261b6996becf84d47e450ddaf980e11f4fe00da983556c28e4bcd880067d` | Actual queued and active write timeout against finite raw peers, stream closure and no late output after the original deadline. |
| [src/test/java/kg/aidarbek/smpp/transport/TcpFailureTest.java](../../src/test/java/kg/aidarbek/smpp/transport/TcpFailureTest.java) | `f966c6f5def92891f2db49f369372e6c9af315eece6b9ea9a34c1ab6e2ee3059` | Read/write/guard/listener faults preserve original reason and settle owned work; terminal callback cleanup failure is separate. |
| [src/test/java/kg/aidarbek/smpp/transport/TcpLifecycleTest.java](../../src/test/java/kg/aidarbek/smpp/transport/TcpLifecycleTest.java) | `bee407d27c74875b0586fcff8fe46bbd97b04141ebc3808b2f8024149b8325c1` | Winning physical close and accepted-write/callback barriers, repeated closure, cleanup exceptions and blocked internal gate retention. |
| [src/test/java/kg/aidarbek/smpp/transport/TcpListenerTest.java](../../src/test/java/kg/aidarbek/smpp/transport/TcpListenerTest.java) | `b022c78c0d3c8fc7d833d3b8588efb40cbd968ab83fb96e77a016c3d9e560296` | Real accept/reject/throw/close handoffs, unstarted transferred ownership and listener-owned child cleanup. |
| [src/test/java/kg/aidarbek/smpp/transport/TcpPortContractTest.java](../../src/test/java/kg/aidarbek/smpp/transport/TcpPortContractTest.java) | `ea2a676eff91544098ba60505b16409695a33e0f6da235ecbd5c8f031b3bb90a` | Shared frame ownership/guard/cancel/start/termination contract applied to the actual plain adapter, separate from scripted fake evidence. |
| [src/test/java/kg/aidarbek/smpp/transport/TcpTransportConfigTest.java](../../src/test/java/kg/aidarbek/smpp/transport/TcpTransportConfigTest.java) | `e0cd38fa67ddebab7d8fbed80494eec086d166681e88757f8bc4f95b15c67c04` | Independent class count/byte/frame/send-buffer configuration bounds and finite defaults. |
| [src/test/java/kg/aidarbek/smpp/transport/TcpTransportTest.java](../../src/test/java/kg/aidarbek/smpp/transport/TcpTransportTest.java) | `7ddff810e28d71f066369409ba83f8a45b1555057079cb7d94dbeeb5d6b49642` | Actual framed read/write, fragmentation/coalescing, malformed framing, queue priority and owned byte/callback behavior. |
| [src/test/java/kg/aidarbek/smpp/transport/TestPeer.java](../../src/test/java/kg/aidarbek/smpp/transport/TestPeer.java) | `e18bd7a4c9c52703c9b090e26a7296c2e8f08ebf7fd1a8c5fd531661ea92ba1c` | Finite raw listener/socket peer helper and explicit read/write/deadline gates; implements no production protocol policy. |
| [src/test/java/kg/aidarbek/smpp/transport/TlsCleanupTest.java](../../src/test/java/kg/aidarbek/smpp/transport/TlsCleanupTest.java) | `d68e760c7b8704db7c5927a6ac5b7631ca3415e159d4eddce1b7ec7580c066f8` | Raw-close-before-wrapper cleanup, slow/nonreading TLS peer, failed close and retained custom provider work with bounded termination observation. |
| [src/test/java/kg/aidarbek/smpp/transport/TlsConfigTest.java](../../src/test/java/kg/aidarbek/smpp/transport/TlsConfigTest.java) | `504bf24d7eb0b09f8d60a5d6ac289e9feca21556c86945478debdb6ff1129d53` | Explicit client identity, trust/context/role/timeout validation and secret-free immutable configuration surface. |
| [src/test/java/kg/aidarbek/smpp/transport/TlsPortContractTest.java](../../src/test/java/kg/aidarbek/smpp/transport/TlsPortContractTest.java) | `18bcc069c0bb2da15062044b1913aaf23f58f62ba745b3f2651756ecef5ec179` | The unchanged shared frame contract applied to both TLS roles after actual negotiation; encrypted ownership/lifecycle evidence. |
| [src/test/java/kg/aidarbek/smpp/transport/TlsProviderFailureTest.java](../../src/test/java/kg/aidarbek/smpp/transport/TlsProviderFailureTest.java) | `c7d9248351f03e8f73b3dc09e70d0f8e2078748e485d6399f65fcc07a23b105e` | Injected provider setup/handshake failures retain TLS_HANDSHAKE_FAILED and cleanup ownership, distinct from parser/read errors. |
| [src/test/java/kg/aidarbek/smpp/transport/TlsTestMaterial.java](../../src/test/java/kg/aidarbek/smpp/transport/TlsTestMaterial.java) | `2150250106f4b0f244a9a94c9929a502a8b5c379c5d02a72541f3fefa2fa86e3` | Public development-only key/trust loading for finite local fixtures; no library-default credential injection or production trust policy. |
| [src/test/java/kg/aidarbek/smpp/transport/TlsTransportTest.java](../../src/test/java/kg/aidarbek/smpp/transport/TlsTransportTest.java) | `4606ddf7bab9b049e9569544e7f08a6c1fce1129e2eee21e6f260aed93792e04` | Real trusted/untrusted, identity mismatch and mutual certificate cases; handshake deadlines, queued output and independent socket cleanup. |
| [src/test/java/kg/aidarbek/smpp/transport/TransportExperiment.java](../../src/test/java/kg/aidarbek/smpp/transport/TransportExperiment.java) | `fcbe6eefbd9303f2271a6d13329196e9e0d13e40395e8af523a84fb7f5cde7a7` | Finite test-source raw echo executable, bounded cohorts/rounds and exact accounting; no SMPP, TLS, capacity or publication claim. |
| [src/test/java/kg/aidarbek/smpp/transport/TransportExperimentTest.java](../../src/test/java/kg/aidarbek/smpp/transport/TransportExperimentTest.java) | `fcaac613db3d58e487899cbe176d7a5c96659f8e01c931b4cb6338b36c5228da` | Real finite echo experiment accounting and invalid bounds, explicit child/transport cleanup and no default benchmark execution. |

## Additional current contract-document cross-check

These eight root-owned guides are additional UTF-8 text inspections, separate
from the 133 Java baseline files. Snapshots of the initially inspected root
versions and their manifest are preserved at `/tmp/maven-endpoint-contract-docs`.
The final column identifies the exact reviewed version supplied with this
change (or the unchanged original). Only D1 and D2 edit a guide; historical
measurements, scope notes and source interpretations are preserved. This is an
implementation-contract cross-check, not a new claim of external specification
or provider verification.

| Additional document | Initially inspected SHA-256 | Outcome | Final reviewed SHA-256 |
| --- | --- | --- | --- |
| [docs/API.md](../API.md) | `4dda9fa36800bf0081402befb77c4081c66c2460758c8e72b25c1b02fd7e5978` | Whole-file text cross-check against compiled capabilities, one request engine, physical callback retention, no replay and effective-version restrictions. D1 corrected the absolute control-progress wording; historical baseline and explicit later helper/lifecycle scope remain. | `af713c87baedcc5965ad5f30735de2accc5d35342f873956dc7a63ba9efa8366` |
| [docs/ENDPOINTS.md](../ENDPOINTS.md) | `7d5c7459266918095b0827a39e257213b60c1e6d5728cc372f9f169336f606bc` | Whole-file text cross-check against all four composition owners, constructor defaults, accepted/start deadlines, off-I/O publication qualification, physical permits and bounded immutable termination. No further concrete mismatch confirmed. | `7d5c7459266918095b0827a39e257213b60c1e6d5728cc372f9f169336f606bc` |
| [docs/EXCHANGE.md](../EXCHANGE.md) | `7f02d8e46c619f1b284a974e66ee75cdf0edf22d496dcadd142c1b3c1fcee08a` | Whole-file text cross-check against operation/handler APIs, data_sm direction matrix, derived requirements, ordered fallback/ready/active reply accounting, physical stage retention and iterative FULL retry. Final Step12-era scope paragraph retained as historical context. | `7f02d8e46c619f1b284a974e66ee75cdf0edf22d496dcadd142c1b3c1fcee08a` |
| [docs/COMMON_OPERATIONS.md](../COMMON_OPERATIONS.md) | `921a6d9f4ea19f908665473b17974ab547d207a0183640b487c5e85932856e58` | Whole-file text cross-check against common descriptors, one-way local-write ownership and explicit reversed owners. D2 qualifies single-attempt behavior and names optional bounded reconnect. Binary/source interpretation sections were read; protocol reviewer owns their independent specification audit. | `d4799f8e621913749f78f291c3e67df67ea0dfad413e2cef1ff6840ba15897da` |
| [docs/REQUESTS.md](../REQUESTS.md) | `311c18be00d0d41fb9e642db4c026fce87d990a71e0ff9dc279c006c0697761d` | Whole-file text cross-check against allocator, correlation, certainty, all terminal paths, post-reservation deadline precedence, protected stages and shared notifier shutdown. No further concrete mismatch confirmed. | `311c18be00d0d41fb9e642db4c026fce87d990a71e0ff9dc279c006c0697761d` |
| [docs/TRANSPORT.md](../TRANSPORT.md) | `ef8b0390e26d57defd7370bc16795311117c8b22481176581f827eba0456fd34` | Whole-file text cross-check against real TCP/TLS adapter ownership, internal callback obligations, separate request/write lifetimes and provisional listener handoffs. Historical experiment counts and provenance remain untouched; its port wording independently exposes F1–F5. | `ef8b0390e26d57defd7370bc16795311117c8b22481176581f827eba0456fd34` |
| [docs/LIFECYCLE.md](../LIFECYCLE.md) | `17b0f79aafa7fdf0af618c2a1a93c310fb701f5802893cd327917268e11feb67` | Whole-file text cross-check against explicit TLS role/identity/context ownership, handshake/accepted-bind budgets, keepalive saturation, reconnect observer retirement and sampled resources. No further concrete mismatch confirmed; simulator adapter implementation is another reviewer’s scope. | `17b0f79aafa7fdf0af618c2a1a93c310fb701f5802893cd327917268e11feb67` |
| [docs/BROADCAST.md](../BROADCAST.md) | `3162f6e19ab7176723ff746a6e5ac4ce429c6a562e183831ba3fa7e2c0ae748b` | Whole-file text cross-check against typed 5.0 endpoint descriptors, profile/mode restrictions, response-context validation, negative policy and matched-only congestion observation. Protocol catalogue/tag interpretations and simulator evidence remain assigned to their own reviewers. | `3162f6e19ab7176723ff746a6e5ac4ce429c6a562e183831ba3fa7e2c0ae748b` |

## Verification and remaining scope

The formatter ran separately before final Java inventory. The fresh endpoint
selection executed 254 cases in 39 classes with zero failures/errors, followed
by the post-format complete library run: 764 cases in 114 classes, zero
failures/errors/skips. The latter includes real TCP/TLS and the compiled examples.
The root `:test` task executed; it was neither `UP-TO-DATE` nor `FROM-CACHE`.
Compilation and configuration caching remained enabled. Exact commands, actual
red/green failures and final whole-type hashes are in the fixture report.

The baseline table was checked automatically against all 133 manifest entries
and their `git show 094a84f:<path>` bytes, with strict UTF-8 decoding. This
verification validates the coverage ledger’s identity and completeness; the
inspection conclusions above remain a human assessment. Production source
bytes have not changed. No Cloudhopper code or dependency was copied into any
project source set, test or tool. Maven signing/publishing readiness, remaining
partitions and final integrated test totals belong to the root integration
review; this report does not imply a remote deployment has occurred.

Final `check build` also passes, with library cases up to date from that fresh
run, 137 simulator cases and 60 review-tool cases restored from matching caches,
and all 523 current Java identities covered. The 21 identities changed by this
audit have explicit final whole-type blocks; no production identity changed.
