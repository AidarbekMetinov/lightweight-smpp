# First behavior scenarios and verification evidence

Step 1 test-design baseline, updated after Step 12. Header/framing scenarios
`FRAME-01` through `FRAME-08` now have executed evidence in
[the framing review](reviews/0002-pdu-framing.md). Generic field/TLV primitives,
initial typed interpretation, and profile occurrence scenarios have
[Step 5 evidence](reviews/0004-fields-profiles.md). Bind/control wire scenarios
have [Step 6 evidence](reviews/0005-session-command-codecs.md). Basic message wire,
field, TLV and original-request conditions have
[Step 7 evidence](reviews/0006-message-codecs.md). Pure session, version and
permission scenarios have [Step 8 evidence](reviews/0007-session-state.md). Request
window, completion, cancellation, controlled deadline and bounded notification
contracts have [Step 9 evidence](reviews/0008-request-tracking.md). Frame-port,
real socket and listener contracts have [Step 10 evidence](reviews/0009-tcp-transport.md).
Binding/control endpoints, authentication, explicit connection cancellation and
bounded shutdown have [Step 11 evidence](reviews/0010-client-server-binding.md).
Message application services and bounded handler/reply ownership have
[Step 12 evidence](reviews/0011-message-exchange.md). Remaining operations and
simulators stay **planned**. Write only the next scenario needed by
the active roadmap step, observe its relevant failure, implement the smallest
passing behavior, then refactor and review every affected type. Follow
[TDD.md](TDD.md) and [SOLID.md](SOLID.md).

## First production increment: headers and framing

Use `FRAME-01` as the first behavior test in Step 3, after Step 2 establishes
the test and formatting workflow. Expected bytes come from the SMPP header layout
and command values, independently of a future encoder.[^1][^2]

| ID | Scenario | Observable expectation |
| --- | --- | --- |
| `FRAME-01` | Encode enquiry header, length 16, status 0, sequence 1 | Exactly the independently specified 16 bytes below. |
| `FRAME-02` | Decode the same complete header | Recover the four fields and consume exactly the header. |
| `FRAME-03` | Response command with high bit set | Preserve `80000015` without signed comparison or conversion errors. |
| `FRAME-04` | Incomplete header or body | Report incomplete input without inventing a complete PDU or losing retained bytes. |
| `FRAME-05` | Length below 16, above configured maximum, or unsigned `FFFFFFFF` | Deterministic rejection before allocation of the claimed body. |
| `FRAME-06` | Header/body split at every byte boundary | Emit one frame only after all its bytes arrive. |
| `FRAME-07` | Two complete frames and a partial third | Emit the first two once, retain the remainder, preserve order. |
| `FRAME-08` | Mutable input or reused backing buffer | Honor the documented ownership contract; no exposed mutable internal state. |

The initial enquiry fixture is:

```text
00 00 00 10  00 00 00 15  00 00 00 00  00 00 00 01
```

The same layout applies to both profiles. Decode raw header bits separately from
command/profile legality: an unknown command is not an incomplete frame. Add
sequence-range and invalid-header nack cases at the appropriate validation layer.

## Field and command scenario templates

The [field/operation inventory](PROTOCOL.md) and [TLV inventory](TLVS.md) provide
stable test identities. Expand one command at a time into these independent cases:

| Template | Cases required before claiming the corresponding behavior |
| --- | --- |
| `FIELD-<name>` | Valid wire representation, empty/boundary values, terminator/count/length errors, reserved values and command context. |
| `TLV-<tag>` | Independent bytes, valid and invalid lengths/values, permitted/repeated/unexpected contexts, profile differences. |
| `OP-<name>-WIRE` | Request and response fixtures, successful and negative responses, body omission rules, malformed input. |
| `OP-<name>-ROLE` | Sender/receiver direction, each permitted bind mode, each forbidden mode, both profiles where defined. |
| `OP-<name>-SESSION` | Correlation, callback/result, handler absence/failure, deadline, state transition, resource release. |
| `OP-<name>-PEER` | Pinned independent peer/version and exact exercised behavior; mark pending when no peer is available. |

Use individual version cases for the 3.4/5.0 `data_sm` and `replace_sm` permission
differences. Include unknown command, missing bind version advertisement, 5.0
congestion on a control response, repeated broadcast-area tags, and error-response
body variants. These cases address concrete ambiguity or cross-version risk.

## API and session contracts

Step 8 executes the network-independent policy portions of `API-02`, `API-03`,
`SESSION-01` and `SESSION-04`: version decisions, current permissions, bind
transitions and opposite-direction response matching. Its 158 session cases
include both roles/profiles, all bind modes, duplicate/failed binds, unbinding,
error responses and idempotent close. They use no sleeps or sockets.
`SessionPermissionsTest`, `VersionNegotiationTest`, `SessionStateMachineTest`
and `SessionResponsePermissionTest` supply the evidence. Steps 9–11 compose the
binding/control subset with transport; Step 12 adds message-handler integration.

Step 9 executes the request portions of `API-04` through `API-06` and
`SESSION-02` through `SESSION-05`: `RequestWindowTest` checks admission, exact
correlation, non-reused sequences and every terminal outcome;
`RequestConcurrencyTest` checks coordinated races, protected future observation
and bounded notifications shared between windows. `RequestValuesTest` verifies
structured failures and preserved generic-nack data. Local write failures and
controlled deadlines are tested independently of the socket adapter.
See [request contracts](REQUESTS.md) for the executed boundary and ownership.

Step 11 executes readiness/version cases (`API-01`/`API-02`), the implemented
control capability and result contracts, explicit attempt cancellation, bounded
resource ownership and diagnostic redaction. `SmppEndpointsTest` covers the real
pair; `EndpointAdversarialTest` supplies raw peer version/failure/capacity cases;
`EndpointConnectionTest` coordinates write/deadline/correlation races over the
shared frame contract. `AuthenticationDispatcherTest` and `EndpointResourcesTest`
cover application execution and honest cleanup observations. The full class and
case inventory is in the [endpoint review](reviews/0010-client-server-binding.md).
That Step 11 snapshot covered bind authentication for `SESSION-06`. Step 12 adds
message invocation/reply ordering, deadline and physical-capacity retention.
Receipt helpers and automatic reconnect remain Steps 15 and 17.

Step 12 exercises `API-03` through `API-06` and `SESSION-02` through `SESSION-07`
for submit/deliver/data. `ExchangeMatrixTest` runs both profiles, all three bind
modes, both directions and positive/negative application decisions; `data_sm`
uses each profile's distinct permission matrix. `ExchangeSessionTest` inspects
raw wire acknowledgements, out-of-order/nack/cancel/late responses and original
request conditions. `ExchangeCapabilitiesTest` proves actual TLVs and 5.0-only
fields cannot bypass missing-advertisement restrictions.

`ExchangeConnectionTest` controls arrival/deadline/queue boundaries, retains reply
slots through active writes, exercises full ordinary/control capacity and drains
6,000 inline completions without recursive stack growth. `HandlerDispatcherTest`
coordinates per-session invocation order and physical stage retention.
`ExchangeFailureTest` covers absent/throwing/failed/null/invalid/slow handlers,
graceful drain and repeated reconnects while one closed session still owns an
unfinished handler. `FakeFrameTransportTest` checks faulty internal callbacks;
the shared frame-port contract still runs on the fake and real TCP adapter.
`EndpointExamplesTest` executes the compiled messaging pair and inspects each
example against a raw peer. Submission acceptance and delivery acknowledgement
are separate; receipt-state helpers are not claimed by this evidence.

These own-endpoint/raw-byte scenarios do not establish pinned independent-peer
interoperability. The [exchange review](reviews/0011-message-exchange.md) records
actual red/green commands, characterization runs and the final case inventory.

| ID | Contract scenario; executed subset described above |
| --- | --- |
| `API-01` | Connect succeeds only after a positive bind; rejected/expired binds close the socket and fail readiness. |
| `API-02` | Version policy follows the decision table; missing advertisement never silently enables TLV-dependent operations. |
| `API-03` | Capability views match profile and role; a send after unbind/close is rejected locally. |
| `API-04` | Negative operation status remains a typed PDU; correlated generic nack is a distinct known peer exception. Local timeout/transport/cancellation failures preserve transmission certainty. |
| `API-05` | Mutating supplied arrays or result-future copies cannot change internal protocol state. |
| `API-06` | Caller interruption and explicit handle cancellation have their distinct documented effects. |
| `API-07` | Owned resources close once; supplied executors remain usable; an application task exceeding shutdown bounds is reported. |
| `API-08` | Ordinary diagnostics and configuration descriptions exclude credentials and payloads. |
| `SESSION-01` | Client and server independently bind TX/RX/TRX under each supported version and reject illegal transitions. |
| `SESSION-02` | Full windows/byte bounds fail admission; control responses retain capacity. |
| `SESSION-03` | Requests complete once under response/timeout/cancellation/disconnect races. |
| `SESSION-04` | Identical peer/local sequence values coexist; wrong, duplicate, and late responses cannot match another request. |
| `SESSION-05` | Sequence exhaustion rejects admission without wrapping; further requests require a new connection generation. Replacement never implies automatic replay. |
| `SESSION-06` | Slow/failed handlers stay bounded; normal replies obey the chosen ordering and control traffic progresses. |
| `SESSION-07` | Submission acceptance, delivery acknowledgement, and receipt state remain separate results. |
| `SESSION-08` | Reconnect cancels on shutdown and does not replay messages with ambiguous outcomes. |

Run common contracts against real adapters and their test doubles. Use controlled
time for policy tests, explicit synchronization for races, and bounded real-socket
tests for transport behavior. An in-house loopback pair supplements independent
fixtures; it cannot prove interoperability by itself.

## Simulator contracts

Step 13 implements LOAD-01 through LOAD-07 for the initial supported operations.
Forty simulator cases exercise invalid preflight, controlled scheduling and fault
clocks, disjoint accounting, bounded delayed/stalled decisions, report semantics,
interruption/partial results and separate process scenarios. Seeded bucket tests
verify exact decisions; real fault runs distinguish observed outcomes. Architecture
checks reject tool ownership of a second protocol request engine. Actual fresh
runs are recorded in [WORKLOADS.md](WORKLOADS.md), independently of cached tests.
Broader scenarios below remain staged work; statistical fault-mix acceptance,
churn and sustained heavy-load qualification remain Step 18.

| ID | Scenario; implemented subset described above |
| --- | --- |
| `LOAD-01` | Reject invalid rates, counts, windows, sizes, deadlines, and incompatible role/operation combinations. |
| `LOAD-02` | Controlled time produces the intended arrival schedule; delayed scheduling reports missed arrivals and no unbounded catch-up burst. |
| `LOAD-03` | Fixed concurrency obeys request limits; arrival-rate overload records rejections without hidden backlog. |
| `LOAD-04` | A seeded fault mix produces known disjoint decisions; actual injections and observed outcomes are distinguishable. |
| `LOAD-05` | Known samples yield correct units, counts, percentiles, aggregate histograms, and accounting identities. |
| `LOAD-06` | Stop scheduling, drain within its bound, classify unfinished work, and report cleanup/threshold failures through exit status. |
| `LOAD-07` | Separate client and server processes run a small scenario against configured peers; reports identify both sides and the executed revision. |
| `LOAD-08` | Requested performance runs execute freshly; deterministic simulator checks and compilation retain normal caching. |

Run hardware-sensitive [workload profiles](WORKLOADS.md) after these deterministic
contracts have evidence. A throughput number does not validate an untested counter.
Convert behavioral defects discovered under load into regression tests before fixes.

## Structural checks and evidence

Step 2 verified formatting detection and cache-compatible tooling with an isolated
fixture; see [the tooling review](reviews/0001-code-checks.md). Step 3 now runs
architecture rules against actual production types:
allowed dependency directions, no package cycles, and transport-independent core
contracts. Isolated forbidden dependencies and a package cycle were detected; the final
rules select real, nonempty production packages. Step 8 extends them to session
policies with [actual session-to-codec/executor violation probes](reviews/0007-session-architecture.md).
Step 9 adds the request boundary, bringing the suite to eight cases, with
[actual request-to-codec/socket violations](reviews/0008-request-architecture.md).
Step 10 adds port and adapter boundaries, bringing the suite to ten cases, with
[actual port-to-codec and transport-to-session violations](reviews/0009-transport-architecture.md).
Step 11 adds endpoint dependencies and the coordinator-to-port rule, bringing the
suite to twelve cases. Its [architecture review](reviews/0010-endpoint-architecture.md)
records actual forbidden dependency probes against final endpoint types. Step 12
reruns those same twelve nonempty rules against the expanded endpoint package;
no new package boundary or new violation-probe claim is introduced.

Step 4 provides `reviewTest` and `solidReview`, with executed failing/passing
cases for missing or stale evidence, new nested/local/anonymous types, malformed
reports, and unresolved findings. See [the evidence format](REVIEW_FORMAT.md)
and [the observed tool checks](reviews/0003-review-coverage.md).
The metadata result supports the required class review; it does not prove the
semantic verdicts.

For each implemented scenario record the actual test name, applicable version and
role, observed red command/failure, green command/result, refactoring checks, final
source identity, and per-type SOLID findings under `docs/reviews/`. Update inventory
evidence only after the claimed checks actually run or valid matching results are
reused with that fact stated.

The original Step 1 changed documents only and used source/content review, command
and TLV inventory reconciliation, Markdown links and formatting checks. Later
steps record their actual Java TDD and per-type SOLID evidence in the linked reviews.

## Sources

[^1]: SMPP Developers Forum. [SMPP 3.4, Issue 1.2](https://smpp.org/SMPP_v3_4_Issue1_2.pdf), §§3.2 and 5.1, 12 October 1999.
[^2]: SMS Forum. [SMPP 5.0](https://smpp.org/SMPP_v5.pdf), §§3.2, 4.7.4–6, and 4.7.24, 19 February 2003.
