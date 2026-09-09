# First behavior scenarios and verification evidence

Step 1 test-design baseline, updated after Step 8. Header/framing scenarios
`FRAME-01` through `FRAME-08` now have executed evidence in
[the framing review](reviews/0002-pdu-framing.md). Generic field/TLV primitives,
initial typed interpretation, and profile occurrence scenarios have
[Step 5 evidence](reviews/0004-fields-profiles.md). Bind/control wire scenarios
have [Step 6 evidence](reviews/0005-session-command-codecs.md). Basic message wire,
field, TLV and original-request conditions have
[Step 7 evidence](reviews/0006-message-codecs.md). Pure session, version and
permission scenarios have [Step 8 evidence](reviews/0007-session-state.md). Live
endpoint API, full session and simulator scenarios remain **planned**. Write only the next scenario needed by
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
and `SessionResponsePermissionTest` supply the evidence. The complete endpoint
scenarios below still require their later correlation, transport and handler layers.

| ID | Planned scenario |
| --- | --- |
| `API-01` | Connect succeeds only after a positive bind; rejected/expired binds close the socket and fail readiness. |
| `API-02` | Version policy follows the decision table; missing advertisement never silently enables TLV-dependent operations. |
| `API-03` | Capability views match profile and role; a send after unbind/close is rejected locally. |
| `API-04` | Negative peer status remains a typed response; timeout/transport/cancellation failures preserve transmission certainty. |
| `API-05` | Mutating supplied arrays or result-future copies cannot change internal protocol state. |
| `API-06` | Caller interruption and explicit handle cancellation have their distinct documented effects. |
| `API-07` | Owned resources close once; supplied executors remain usable; an application task exceeding shutdown bounds is reported. |
| `API-08` | Ordinary diagnostics and configuration descriptions exclude credentials and payloads. |
| `SESSION-01` | Client and server independently bind TX/RX/TRX under each supported version and reject illegal transitions. |
| `SESSION-02` | Full windows/byte bounds fail admission; control responses retain capacity. |
| `SESSION-03` | Requests complete once under response/timeout/cancellation/disconnect races. |
| `SESSION-04` | Identical peer/local sequence values coexist; wrong, duplicate, and late responses cannot match another request. |
| `SESSION-05` | Default sequence exhaustion closes/replaces the session without reusing a potentially stale identity. |
| `SESSION-06` | Slow/failed handlers stay bounded; normal replies obey the chosen ordering and control traffic progresses. |
| `SESSION-07` | Submission acceptance, delivery acknowledgement, and receipt state remain separate results. |
| `SESSION-08` | Reconnect cancels on shutdown and does not replay messages with ambiguous outcomes. |

Run common contracts against real adapters and their test doubles. Use controlled
time for policy tests, explicit synchronization for races, and bounded real-socket
tests for transport behavior. An in-house loopback pair supplements independent
fixtures; it cannot prove interoperability by itself.

## Simulator contracts

| ID | Planned scenario |
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
policies, with seven current cases and
[actual session-to-codec/executor violation probes](reviews/0007-session-architecture.md).

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

Step 1 changes documents only. Its verification is source/content review, command
and TLV inventory reconciliation, Markdown links, and formatting checks. There is
no executed Java TDD cycle or class-level SOLID verdict to report at this stage.

## Sources

[^1]: SMPP Developers Forum. [SMPP 3.4, Issue 1.2](https://smpp.org/SMPP_v3_4_Issue1_2.pdf), §§3.2 and 5.1, 12 October 1999.
[^2]: SMS Forum. [SMPP 5.0](https://smpp.org/SMPP_v5.pdf), §§3.2, 4.7.4–6, and 4.7.24, 19 February 2003.
