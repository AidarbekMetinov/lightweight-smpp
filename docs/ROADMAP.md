# Step-by-step implementation plan

Build both client and server endpoints for SMPP 5.0 and 3.4, plus independently
runnable client and server simulators for functional and heavy-load testing.
The [research report](RESEARCH.md) explains the protocol targets and architecture.
The [simulator plan](SIMULATORS.md) defines workloads and measurement requirements.

Current stage: **research and Steps 1–4 completed; Step 5 is next**.
Java 21, Gradle, JUnit, strict compiler warnings, local caching, formatting, and Git
are configured. Header values, a binary header codec, bounded framing, behavior
tests, and meaningful architecture rules are implemented. Automatic review-evidence
coverage and freshness checks run through the normal verification lifecycle.

Each step delivers one coherent result. Larger steps contain several small TDD
cycles and may use several simple commits. Follow [TDD](TDD.md), review every
created or updated type under [SOLID](SOLID.md), and use the
[development guide](DEVELOPMENT.md). Work on the requested step; this plan does
not authorize implementing all later stages at once.

## Sequence and dependencies

| Step | Result | Prerequisites |
| --- | --- | --- |
| 1 | API contracts, version/role inventory, and workload criteria | Research |
| 2 | Test workflow and deterministic formatting | 1 |
| 3 | Header values, bounded framing, and first architecture rules | 2 |
| 4 | Automatic review-evidence coverage and freshness checks | 3 |
| 5 | Field primitives, TLVs, and protocol profiles | 3–4 |
| 6 | Binding and control command codecs | 5 |
| 7 | Basic messaging codecs | 5–6 |
| 8 | Session state and endpoint permissions | 6–7 |
| 9 | Request correlation, deadlines, and bounded admission | 8 |
| 10 | First TCP transport | 9 |
| 11 | Client and server binding | 10 |
| 12 | Submission and delivery through both endpoints | 11 |
| 13 | Runnable client and server simulators | 12 |
| 14 | Remaining common operations | 12–13 |
| 15 | Encoding, segmentation, and receipt helpers | 12–13 |
| 16 | Complete the declared SMPP 5.0 feature inventory | 14–15 |
| 17 | TLS, keepalives, reconnect, and lifecycle hardening | 12–16 |
| 18 | Complete heavy-load scenarios and reproducible reporting | 13–17 |
| 19 | Independent interoperability and release preparation | 18 |

Dependencies describe technical ordering, not calendar estimates. Set numerical
capacity targets from a real workload in Step 1 and refine them with measurements.

## 1. Define contracts and the support inventory

Status: **completed as a design baseline**. Deliverables:
[API contracts](API.md), [protocol inventory](PROTOCOL.md),
[TLV inventory](TLVS.md), [first test scenarios](TEST_PLAN.md), and
[workload criteria](WORKLOADS.md). Production performance requirements remain open;
the documented numerical profiles are provisional development targets.

Write small client and server usage examples before fixing public class names.
Decide request completion semantics, callback acknowledgements, asynchronous and
blocking needs, error representation, and resource ownership. Describe what the
server delegates to an application, including authentication and durable acceptance.

Create an operation, field, and TLV inventory with specification sections,
versions, endpoint roles, bind permissions, and planned tests. Keep codec, session,
and independent-interoperability evidence separate. Include both profiles from
the first shared operation; do not describe a successful 5.0 bind as full support.

Define simulator scenarios and measurable acceptance criteria: connections,
offered messages per second, request windows, payload mix, duration, latency,
errors, memory limits, and test hardware. Record unresolved numerical targets.

**Review and completion:** establish explicit contracts, a dependency diagram,
the inventory, and the first behavior-test list. This is a documentation step;
proposed code examples do not establish executed TDD or reviewed implementations.

Suggested commit: `Define library contracts`.

## 2. Set up the test and formatting workflow

Status: **completed**. Pinned Spotless 8.10.2, Palantir Java Format 2.96.0, and
ArchUnit core 1.4.2 work with Java 21, Gradle 9.6.0, and Jupiter 6.0.0. Formatting
is connected to `check`. The [review record](reviews/0001-code-checks.md) contains
negative/positive fixture checks, the temporary class's SOLID review, dependency
verification, and observed configuration/output cache reuse.

Keep JUnit Jupiter and strict compiler checks. Select a deterministic formatter
that preserves four-space indentation and wire its verification into `check`.
Pin compatible tool versions after checking Java 21, JUnit 6, and Gradle 9.6
requirements. Formatting verification should identify differences without
silently rewriting sources.

Prepare ArchUnit integration through ordinary Jupiter tests using its core API;
add helpers only for concrete test needs. Activate structural rules against the
first real production types in Step 3. Do not disable empty-selection failures
and report that as architectural coverage.

**Test first:** show a formatting violation is detected using an isolated sample,
then show the formatted sample passes. Develop any Java helper behavior with TDD.

**SOLID and completion:** review all introduced test/helper types, verify the
configured checks, and verify configuration-cache reuse for matching invocations.

Suggested commit: `Set up code checks`.

## 3. Add the PDU header and bounded framing

Status: **completed**. [Framing contracts](FRAMING.md) describe the raw unsigned
header and bounded pull-style assembly. [The review](reviews/0002-pdu-framing.md)
records executed TDD, final source hashes, per-type SOLID findings, and real
architecture-rule violation probes. Command bodies and session legality remain
separate later steps.

Introduce the smallest header values and binary header codec needed by the first
fixtures. Keep stream assembly separate from command-body parsing. Define
partial-input handling, maximum frame length, and buffer ownership.

**Test first:** independent header bytes, unsigned values, truncated headers,
invalid and oversized lengths, frames split across reads, and multiple frames
in one read. Implement one scenario at a time.

**SOLID review:** header types own value invariants; the framer owns bounded frame
assembly. Neither owns sockets or session state. Review equality and mutable
buffer exposure. Include records, nested types, and test fixtures in the report.

**Completion:** fixtures pass, first meaningful architecture rules select actual
types, and an isolated forbidden-dependency example demonstrates rule detection.
Keep the initial review evidence manually checked until Step 4 is implemented.

Suggested commit: `Add PDU framing`.

## 4. Check review coverage and freshness automatically

Status: **completed**. `solidReview` inventories Java types using immutable source
snapshots and the JDK parser, then verifies current evidence for every identity.
`reviewTest` covers the tool and `check` runs both tasks. See [the evidence format](REVIEW_FORMAT.md)
and [the review](reviews/0003-review-coverage.md) for TDD, full type/fixture reviews,
self-review bootstrap, and source/report/policy cache invalidation evidence.

Implement a small development-only check of type identities and final source
hashes against recorded review evidence. Use Java parsing support instead of
regular expressions that overlook nested or local types. Include the check's
own Java types and tests in its review scope.

**Test first:** missing entries, stale hashes, a newly added nested type, renamed
types, malformed evidence, unresolved findings, and a complete current report.
Bootstrap with manual review, then have the check validate that same evidence.

**SOLID review:** keep source discovery, evidence parsing, and validation policy
cohesive. Avoid combining Git operations, formatting, and semantic review into
one utility. The tool checks evidence; a reviewer still evaluates every principle.

**Completion:** wire the check into verification with declared source, report,
and policy inputs. Demonstrate invalidation after either source or report edits
and reuse when inputs match. Keep tooling out of the library's runtime artifact.

Suggested commit: `Check SOLID review coverage`.

## 5. Add field primitives, TLVs, and version profiles

Implement bounded binary fields, C-octet strings, optional-parameter storage,
and explicit profile capabilities from the support inventory. Separate raw TLV
preservation from typed interpretation.

**Test first:** boundary lengths, missing terminators, invalid field bytes,
truncated TLV headers and values, well-formed unknown TLVs, and command-specific
duplicate/required-parameter rules. Cover each applicable profile.

**SOLID review:** field codecs own wire rules; profiles own version rules;
application callbacks do not decide parsing. Codec registration must reject
ambiguous duplicates without bypassing frame bounds.

**Completion:** malformed input has deterministic bounded outcomes and unknown
optional data can be preserved without implying supported semantics. Reconcile
the inventory and all type reviews with the final source.

Suggested commit: `Add fields and protocol profiles`.

## 6. Add binding and control codecs

Implement all bind modes and responses, `unbind`, `enquire_link`, and
`generic_nack`. Keep requested interface version, peer advertisement, and
effective capabilities distinguishable.

**Test first:** known wire bytes for both profiles and all bind modes, error
responses, malformed fields, unknown commands, and missing or unknown version
advertisements. Test only each layer's decision at this stage; session policies
come later.

**SOLID review:** PDU values hold data and invariants, codecs translate bytes,
and session policies will own permissions. Share field logic without inventing
inheritance between commands with different contracts.

**Completion:** network-independent fixtures verify control commands and preserve
the information needed for response correlation and version decisions.

Suggested commit: `Add session command codecs`.

## 7. Add basic message codecs

Implement `submit_sm`, `deliver_sm`, `data_sm`, their responses, and the optional
parameters needed by their declared profiles. Preserve raw payload bytes before
introducing text helpers.

**Test first:** independent message fixtures, address limits, empty and boundary
payloads, `short_message`/`message_payload` rules, error responses, unsupported
optional fields, and invalid field combinations.

**SOLID review:** share body-field contracts where they are actually the same.
Keep socket writes, retries, timers, and receipt parsing out of PDU value types.

**Completion:** the inventory records codec evidence for both roles and versions.
It must still show session and independent interoperability evidence as pending.

Suggested commit: `Add message codecs`.

## 8. Model session state and endpoint permissions

Define connection, binding, bound-mode, unbinding, and closed behavior independently
of real networking. Specify unexpected requests and responses and both roles'
allowed operations from the protocol decision table.

**Test first:** submission before binding, forbidden operations for a bind mode,
duplicate bind, failed bind, simultaneous activity in a transceiver session,
unbind from either endpoint, and repeated close. Exercise both profiles.

**SOLID review:** the state machine makes protocol decisions without parsing bytes,
authenticating accounts, or owning sockets. Public capabilities must describe
what their implementations can actually do.

**Completion:** transitions are deterministic without sleeps; shared contracts
hold across role/profile variants without unsupported-method stubs.

Suggested commit: `Add session state rules`.

## 9. Add requests, deadlines, and bounded admission

Implement a bounded pending-request window, sequence correlation, and exactly
one terminal completion. Define admission, write, and response deadlines and
distinguish a request never sent from an ambiguous outcome after sending.

**Test first:** full windows, sequence wraparound, duplicate keys, out-of-order or
wrong-command responses, late/duplicate responses, timeout/completion races,
cancellation, and disconnect with pending work. Use controlled time and explicit
thread coordination.

**SOLID review:** request tracking owns correlation and capacity; state rules own
permissions; the transport owns writing. Shared completion contracts must hold
for all terminal outcomes and test doubles.

**Completion:** each request completes once, every occupied slot is released,
and failure paths cannot silently resubmit messages.

Suggested commit: `Add request tracking`.

## 10. Implement the first TCP transport

Choose the transport against the workload and ownership contracts. JDK sockets
with Java 21 virtual threads are the first experiment recommended by the research.
Define ordering, buffer ownership, write acceptance, failure, and close behavior.

**Test first:** fragmented reads, slow readers, EOF within a frame, failed
connection/write, bounded outbound work, simultaneous close, and cleanup. Exercise
partial writes if the selected transport exposes them. Use controlled local peers
and bounded coordination instead of timing assumptions.

**SOLID review:** adapters implement session-owned ports. Run applicable contracts
against real and fake transports. Keep transport types out of public PDU APIs.

**Completion:** resources and queues stay bounded, shutdown is verified, and a
small measurement records the environment and observed behavior. This is not yet
a heavy-load capacity claim.

Suggested commit: `Add TCP transport`.

## 11. Bind real client and server endpoints

Assemble both endpoints with server authentication callbacks, accepted-version
policies, connection limits, bind deadlines, and explicit executor ownership.

**Test first:** all bind modes for both profiles, authentication rejection,
slow/failing authentication, version mismatch or missing advertisement, connection
limits, and close during binding.

**SOLID review:** composition code may construct adapters; session policies use
their contracts. Authentication remains focused and does not require delivery or
submission callbacks.

**Completion:** a local client and server can establish and close valid sessions.
Invalid and stalled binds consume only bounded resources. Add a minimal example
for each endpoint, keeping the feature inventory accurate.

Suggested commit: `Add client and server binding`.

## 12. Exchange submissions and deliveries

Connect client submission to server application acceptance and server delivery to
client callbacks. Define acknowledgement deadlines, application errors, and
asynchronous completion. Provide a runnable pair of examples.

**Test first:** successful and rejected submission, delayed or exceptional
callbacks, delivery acknowledgements, traffic in both directions, and disconnect
while application work is pending.

**SOLID review:** focused handlers own application decisions. Core sessions do
not equate protocol acceptance with handset delivery. Callback execution and
capacity policies preserve progress for control traffic.

**Completion:** basic messaging works with both profiles and endpoints. The
remaining 5.0 commands stay required work in Step 16.

Suggested commit: `Add message exchange`.

## 13. Deliver the first client and server simulators

Add a development application subproject, depending on the public library API,
with independently runnable client and server modes. Implement configurable
connections, bind mode/version, request window, message count/rate, payload,
duration, and deterministic data seeds. Add server response delay, rejection,
delivery generation, and controlled disconnect scenarios.

**Test first:** invalid configuration, pacing under controlled time, request
accounting, selected fault behavior, shutdown, and a small process-to-process
smoke run. Verify counters against known scenarios before interpreting throughput.

**SOLID review:** CLI configuration, workload scheduling, server behavior, metrics,
and report output have distinct responsibilities. Simulators use the public
contracts; measurement dependencies do not enter the library artifact.

**Completion:** both tools run independently against a configured peer, support
both profiles' implemented features, and produce a reproducible basic report.
Run short ramps now; expand scenarios alongside Steps 14–17.

Suggested commit: `Add SMPP simulators`.

## 14. Add remaining common operations

Implement `query_sm`, `cancel_sm`, `replace_sm`, `submit_multi`, `outbind`, and
`alert_notification` with typed application hooks and appropriate behavior when
an optional application service is unavailable.

**Test first:** wire fixtures, role and state permissions, per-destination results,
absent handlers, and one-way notifications without invented response PDUs.
Exercise outbind without introducing uncontrolled connection loops.

**SOLID review:** command codecs and handlers should extend the intended boundaries.
Changing request correlation needs a reason tied to its own contract.

**Completion:** each selected common operation has codec and role-aware session
tests, simulator coverage, and documented application responsibilities.

Suggested commit: `Add remaining common operations`.

## 15. Add message encoding and receipt helpers

Add explicit GSM alphabet and UCS-2 handling, segmentation/reassembly where
required, and delivery-receipt helpers while retaining direct raw payload access.
Specify unsupported-character and provider-variant policies before coding.

**Test first:** extension characters, encoded-length boundaries, unsupported
characters, multipart ordering and duplicates, incomplete assembly, and receipt
variants or missing fields. Bound any state retained for reassembly.

**SOLID review:** encoding, segmentation, assembly, and receipt interpretation
remain cohesive and usable without a live session. Provider behavior must be
explicit rather than inferred inside unrelated codecs.

**Completion:** documented encoded-length limits match fixtures, raw values remain
available, and simulator scenarios cover payload and receipt variants.

Suggested commit: `Add message helpers`.

## 16. Complete the declared SMPP 5.0 inventory

Implement broadcast operations and responses, required fields, new optional
parameters, status/receipt options, and congestion information from the inventory.
Cover both client APIs and server application hooks.

**Test first:** independent fixtures for each addition, version acceptance and
rejection, application outcomes, congestion input, and absent optional capabilities.
Extend both simulators with applicable 5.0 scenarios.

**SOLID review:** add profile/codec/handler behavior at the intended extension
points. Keep congestion feedback, admission limits, and any rate policy distinct
from binary decoding while preserving their shared contracts.

**Completion:** every declared 5.0 item has the required codec and session-role
evidence. Reconcile any remaining optional or application-provided behavior in
the inventory. Complete this step before claiming the planned 5.0 scope is ready.

Suggested commit: `Complete SMPP 5 support`.

## 17. Harden connection lifecycle

Add TLS for both endpoints, keepalives, explicit reconnect policy, and complete
cleanup under slow peers and handlers. Keep reconnection independent of message
replay and retain bounded callback and outbound work.

**Test first:** trusted/untrusted certificates, server identity mismatch, TLS
deadlines, unresponsive peers, reconnect cancellation, start/close and unbind
races, saturated queues, and resource cleanup. Expand simulator faults to cover
these cases under ongoing traffic.

**SOLID review:** TLS belongs to the transport; heartbeat and reconnect policy
must not redefine message completion or introduce persistence into sessions.

**Completion:** ownership and deadline contracts survive faults; control traffic
remains responsive under the stated workload; reconnect does not silently create
duplicate submissions.

Suggested commit: `Harden connection lifecycle`.

## 18. Complete heavy-load scenarios and reporting

Complete both simulators' arrival-rate and fixed-concurrency modes, ramps,
bursts, soak runs, connection churn, slow consumers, and fault mixes. Follow
[the simulator design](SIMULATORS.md) for bounded generation and latency accounting.

**Test first:** reproducible schedules, missed arrivals, saturation accounting,
known latency distributions, outcome reconciliation, report aggregation,
threshold evaluation, and clean stop/drain behavior. Keep these deterministic
tests separate from environment-sensitive performance measurements.

**SOLID review:** workload generation, fault policy, measurement, and report
formatting must remain independently testable. Each implementation of a simulator
extension must preserve its documented contract.

**Completion:** reports include offered/achieved rates, latency percentiles,
errors, queue/window occupancy, and generator/target resource measurements.
Run both target roles, separate processes, and separate hosts when needed to
identify generator or host bottlenecks. Document measured limits against the
Step 1 workload. Heavy-load tasks always execute; past results are not cache hits.

Suggested commit: `Add load test scenarios`.

## 19. Verify interoperability and prepare a release

Test our client against an independent server and our server against an independent
client. Pin peer versions and configuration. Cloudhopper can cover its actual
feature subset; independent 5.0 evidence needs appropriate peers or separately
derived fixtures. Mark unavailable external verification as pending.

Run agreed load and soak scenarios with the simulators. Record throughput,
latency, allocation, memory, connection churn, and failure recovery with environment
details. Measure cold and warm development builds separately. Any discovered
bug needs a failing regression test before its fix and a new review of affected types.

**Completion:** API examples, version/operation/role evidence, application duties,
known limitations, dependency footprint, source/Javadoc archives, and reproducible
builds are documented. Publish performance claims only for measured workloads.
Release preparation produces reviewable artifacts; publishing is a separate action.

Suggested commit: `Prepare first release`.

## Completion criteria for every implementation step

- The selected behavior is covered for its applicable versions and endpoint roles.
- Actual red, green, and refactoring evidence is recorded, or the change is a
  documented behavior-preserving refactor with before/after verification.
- Every created or updated Java type has current findings for S, O, L, I, and D.
- No known SOLID violation, unexplained omission, or stale review remains.
- Relevant behavior, architecture, formatting, and build checks pass. State which
  checks are installed and whether results were freshly executed or reused.
- The support inventory and documentation match what is actually implemented.
- Each commit is focused and has a simple message.
