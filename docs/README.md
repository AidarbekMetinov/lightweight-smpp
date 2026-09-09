# Lightweight SMPP

A personal Java library for the Short Message Peer-to-Peer (SMPP) protocol, focused
on a small implementation and a simple API.

## License

Copyright 2026 Aidarbek Metinov. This project is licensed under the
[Apache License, Version 2.0](../LICENSE) (`Apache-2.0`). See [NOTICE](../NOTICE)
for the project attribution. Binary, source, and Javadoc JARs include both files
under `META-INF/`. The license text is the
[official Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0.txt).

## Agreed direction

- Build our own lightweight SMPP library.
- Support both client and server endpoints, targeting SMPP 5.0 and 3.4.
- Provide client and server simulators for functional and heavy-load testing.
- Develop it step by step, keeping each piece small and understandable.
- Use Cloudhopper SMPP and Cloudhopper Commons as reference projects.
- Keep project planning documents in this directory.
- Use strong Java coding practices and caching to keep development efficient.
- Review every created or updated Java type against all five SOLID principles.
- Use TDD for new behavior and bug fixes, with recorded failing and passing tests.
- Keep Git commit messages short and simple.

Project working guidance is recorded in [AGENTS.md](../AGENTS.md).

## Initial project state

Inspected on 2026-09-09:

- Existing Gradle project with group `kg.aidarbek` and name `lightweight-smpp`.
- Java 21 is installed locally.
- JUnit Jupiter is configured.
- Source and test directories are empty.
- `./gradlew test --console=plain` completes successfully with no tests to run.

## Development setup

The build now uses Java 21, the `java-library` plugin, strict compiler warnings,
UTF-8 encoding, and reproducible archives. Gradle build and configuration caches
are enabled. Git is initialized on `main`.

See the [development guide](DEVELOPMENT.md) for coding conventions, commands, and
caching details. Java 21 is the current build target; release compatibility can
be revisited when the library's scope is settled.

## Research and working policies

- [Research report](RESEARCH.md): primary sources, version findings, Cloudhopper
  lessons, architecture, design principles, testing, and tradeoffs.
- [Implementation plan](ROADMAP.md): ordered milestones, behavior tests, per-type
  review requirements, and completion criteria.
- [SOLID policy](SOLID.md) and [evidence format](REVIEW_FORMAT.md): mandatory
  per-type reviews, current source hashes, and automatic coverage checks.
- [TDD workflow](TDD.md): observed red, minimal green, refactoring, and regression tests.
- [Simulator design](SIMULATORS.md): client/server modes, workloads, fault scenarios,
  metrics, and reproducible heavy-load results.

SMPP 5.0 is the latest public standard verified in the research. The report also
explains the unresolved “5.1” terminology in older Oracle documentation. The shared
header/framing, field/TLV foundations, bind/control and message codecs, and
deterministic session policies are implemented for both profiles. Binding and control
endpoints and submission/delivery/data services now run for both profiles;
complete version support remains pending. Formatting, real
architecture rules, and automatic review coverage/freshness checks are active;
SOLID and TDD policies apply to every change.

## Step 1 design baseline

- [API contracts](API.md): implemented binding/control and message exchange
  contracts, outcomes, cancellation,
  callbacks, capability/version rules, resource ownership, and dependency diagram.
- [Protocol inventory](PROTOCOL.md) and [TLV inventory](TLVS.md): commands, fields,
  tags, source references, version/role permissions, and pending evidence.
- [First test scenarios](TEST_PLAN.md): framing fixtures and contracts to drive
  implementation through TDD.
- [Workload criteria](WORKLOADS.md): provisional simulator profiles, measurement
  accounting, acceptance goals, and the inspected local hardware baseline.

The baseline uses one asynchronous request mechanism and focused capabilities,
one library artifact without initial runtime dependencies, and a separate
application subproject for simulator tooling. Binding/control and basic messaging
endpoint APIs and examples are compiled. Binary framing, fields, TLVs, profiles,
codecs, session policies and bounded live message exchange are implemented;
simulators remain Step 13.

## Remaining design choices

These choices remain open:

- Transport changes justified by workload measurements beyond the initial JDK experiment.
- Production workload, latency, and deployment requirements; local profiles are provisional.
- Provider-specific exceptions backed by interoperability evidence.
- Exact API names and additional dependencies justified by implementation needs.

## Step 2 tooling

Spotless 8.10.2 and Palantir Java Format 2.96.0 enforce Java formatting through
`check`. ArchUnit core 1.4.2 is ready for ordinary Jupiter 6.0.0 tests. Formatting
failures, real test discovery, rule violations, successful correction, and cache
reuse were verified with an isolated fixture. See the
[tooling review](reviews/0001-code-checks.md) and
[development commands](DEVELOPMENT.md).

## Step 3 framing

Immutable unsigned header values, network-order header translation, and bounded
stream assembly are implemented with permanent behavior tests. Step 3 introduced
five architecture checks against actual production types; later layers extend them. See [framing contracts](FRAMING.md) and
[the TDD/SOLID review](reviews/0002-pdu-framing.md).

## Step 4 review coverage

The JDK-based validator discovers every selected Java type, including nested,
local, and anonymous classes. `solidReview` rejects missing, stale, malformed,
or unresolved evidence; `reviewTest` verifies the tool itself. Both run through
`check`, with declared inputs and verified cache reuse. See [the tool contract](REVIEW_FORMAT.md)
and [the review](reviews/0003-review-coverage.md).

## Step 5 fields and profiles

Bounded binary cursors, immutable ordered raw TLVs, profile catalogues, occurrence
rules, and the first typed TLV interpretations are implemented. The library and
architecture suite at Step 5 completion contained 95 passing cases; the review
tool had 60. That snapshot covered all 43 Java type identities. See
[field contracts](FIELDS.md) and [the review](reviews/0004-fields-profiles.md)
for precise scope and verification.

## Step 6 bind and control codecs

All three bind modes and responses, unbind, enquire-link, and generic-nack now
have bounded codecs for SMPP 3.4 and 5.0. The immutable PDU envelope preserves
status, sequence, raw version advertisements, and raw incoming extensions.
See [command contracts](COMMANDS.md) and the
[TDD/SOLID review](reviews/0005-session-command-codecs.md).

## Step 7 message codecs

Submit, deliver and data messages and their responses have bounded codecs for
both profiles, with explicit data-message direction, immutable payload bytes,
and structural checks for all 51 message TLV tags. Original-request response
conditions are separate from decoding. See [message contracts](MESSAGES.md) and
the [TDD/SOLID review](reviews/0006-message-codecs.md), including the documented
SMPP 5.0 error-body interpretation.

## Step 8 session policies

Client and server roles share deterministic bind/unbind transitions, exact
3.4/5.0 operation permissions, version negotiation, outgoing field requirements,
and response authorization. Crossed unbinds keep their directional identities,
and ordinary responses require caller-supplied correlation context. Seven
architecture checks enforce the implemented package boundaries. See
[session contracts](SESSIONS.md), the [TDD/SOLID review](reviews/0007-session-state.md),
and [architecture evidence](reviews/0007-session-architecture.md).

The Step 8 integrated build passed 341 library/architecture cases, plus 60 unchanged
review-tool cases reused from cache. Its SOLID evidence covered all 113 Java
type identities. Runtime dependencies remain empty; build/configuration caching
and licensed binary/source/Javadoc archives are verified.

## Step 9 request tracking

Bounded request windows now own connection generations, monotonic non-reused
sequences, correlation, cancellation and deadlines. Exactly one terminal outcome
wins, with explicit transmission certainty and bounded asynchronous notification.
See [request contracts](REQUESTS.md), [TDD/SOLID evidence](reviews/0008-request-tracking.md),
and [architecture evidence](reviews/0008-request-architecture.md).

The Step 9 integrated build passed 383 library/architecture cases, including 41 request
cases and eight architecture cases, plus 60 review-tool cases. Root integration
restored the matching library tests from cache and reused the tooling results;
the request review records their fresh development runs. That SOLID snapshot
covered all 129 Java type identities. Runtime dependencies remain empty, and all
three archives preserve the Apache license and project notice.

## Step 10 TCP transport

The first TCP adapter uses JDK sockets and Java 21 virtual threads. Frame ownership,
ordinary/control write bounds, cancellation, deadlines, listener handoff and
cleanup have local-peer and shared-port tests. See [transport contracts and the
small measurement](TRANSPORT.md), [TDD/SOLID evidence](reviews/0009-tcp-transport.md),
and [architecture evidence](reviews/0009-transport-architecture.md).

The Step 10 integrated build passed 419 library/architecture cases and 60
review-tool cases. Integration restored matching library results from cache and
reused tooling results; the transport review records a fresh 419-case run.
That SOLID snapshot covers all 164 Java type identities. Three fresh, separate
client/server JVM experiments each reconciled 1,600 echoes with complete cleanup;
the transport guide records their environment and measurement limits.

## Step 11 client and server binding

Real client and server endpoints bind RX/TX/TRX under SMPP 3.4 and 5.0, with
focused authentication, explicit connection-attempt cancellation, two-way
enquiries and unbind. Connection, request and callback bounds apply across
failures and shutdown. See [endpoint contracts and examples](ENDPOINTS.md),
[TDD/SOLID evidence](reviews/0010-client-server-binding.md), and
[architecture evidence](reviews/0010-endpoint-architecture.md).

The combined Step 11 build freshly executed all 498 library/architecture cases;
60 unchanged review-tool cases remained up to date. That snapshot covered all
199 Java type identities with current SOLID evidence. Runtime dependencies remain empty, and examples
stay outside all three Apache-licensed production archives. The command-line
client/server exchange and both Gradle example helpers ran successfully; repeated
client execution reused configuration while running the exchange freshly.

## Step 12 message exchange

Focused senders and optional typed asynchronous handlers connect `submit_sm`,
`deliver_sm` and `data_sm` under both profiles and all permitted endpoint/mode
combinations. One request window handles responses, cancellation and deadlines.
Global handler limits retain physically unfinished work after timeout or close;
ordered replies retain bounded count/bytes through output settlement. Control
traffic has separate finite capacity, and graceful shutdown drains message work.
See [exchange contracts and runnable examples](EXCHANGE.md) and
[TDD/SOLID evidence](reviews/0011-message-exchange.md).

The examples perform a real submission and an independent delivery; acceptance
and handset delivery remain separate. The final formatted suite freshly executed
559 library/architecture cases, including 138 endpoint and 12 architecture cases;
the final build reused that matching output and restored 60 unchanged review-tool
cases from cache. All 228 current Java identities have matching SOLID evidence.
A separate client/server JVM smoke completed successfully.

## Step 13 client and server simulators

The separate `simulator` application runs independent client/server workloads
under both profiles, with finite arrival/ramp/concurrency plans, seeded receiver
faults, HDR latency distributions, resource CSV and explicit outcome accounting.
The 40 simulator cases cover deterministic policies and real separate JVMs;
all 293 current type identities have matching SOLID reviews. HdrHistogram 2.2.2
is a tool dependency; the library runtime remains dependency-free.

[The guide](SIMULATORS.md) documents installation and CLI options.
[Fresh measurements](WORKLOADS.md) include both-profile smoke, three repeated
short runs per profile, full development baselines and short ramps. Both full
1,000/s baselines failed strict criteria because of recorded generator skips;
these results are not production-capacity claims.

## Step 14 common operations

Typed query, cancel, replace and multiple-submission operations now share the
bounded request/handler mechanism. Alerts expose local write completion without
a paired response. Explicit outbind listener/connector owners authenticate both
sides and bind on the reversed TCP connection, with finite deadlines and cleanup.
Independent wire fixtures, endpoint permission/lifecycle tests and simulator
scenarios cover both profiles. The codecs retain raw fields and per-destination
outcomes; application storage and distribution-list expansion remain caller-owned.

See [common-operation contracts](COMMON_OPERATIONS.md),
[TDD/SOLID evidence](reviews/0013-common-operations.md), and
[standalone simulator integration](reviews/0013-simulator-integration.md).
The integrated checks pass 615 library cases, 47 simulator cases and 60 review-tool
cases; all 357 current Java identities have matching reviews. Four fresh installed
alert/outbind checks completed successfully.

## Step 15 message helpers

Session-independent helpers provide strict unpacked GSM and UCS-2 conversion,
encoded-octet segmentation, explicit SAR/concatenation headers, bounded timed
reassembly and raw-preserving text/TLV receipts. Provider conventions remain
explicit; codecs still expose the original bytes. The simulator selects GSM,
UCS-2, SAR and receipt variants with early preflight, per-connection segment
ordinals and receiver validation. Its receipt scenarios originate from the MC
by policy; no automatic receipt correlation or retransmission is implied.

See [helper APIs and conventions](MESSAGE_HELPERS.md),
[helper TDD/SOLID evidence](reviews/0014-message-helpers.md), and
[integration evidence](reviews/0014-simulator-integration.md).
The final checks pass 634 library cases, 57 simulator cases and 60 review-tool
cases; all 381 Java identities have current reviews. Fourteen fresh installed-tool
content scenarios passed, including two-connection multipart multiple submission.

Next is Step 16: broadcast and the remaining declared SMPP 5.0 inventory.
TLS/lifecycle hardening, full heavy-load qualification and independent-peer
interoperability remain Steps 17–19.

## References

- [Cloudhopper SMPP](https://github.com/fizzed/cloudhopper-smpp): protocol and session implementation reference.
- [Cloudhopper Commons](https://github.com/twitter/cloudhopper-commons): supporting utilities, including message character encoding and request tracking.
- [SMPP 3.4 specification, issue 1.2](https://smpp.org/SMPP_v3_4_Issue1_2.pdf): protocol definitions and wire format.
- [SMPP 5.0 specification](https://smpp.org/SMPP_v5.pdf): latest verified public protocol specification.
