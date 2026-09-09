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
deterministic session policies are implemented for both profiles. Complete
version support and live endpoints remain pending. Formatting, real
architecture rules, and automatic review coverage/freshness checks are active;
SOLID and TDD policies apply to every change.

## Step 1 design baseline

- [API contracts](API.md): client/server usage sketches, outcomes, cancellation,
  callbacks, capability/version rules, resource ownership, and dependency diagram.
- [Protocol inventory](PROTOCOL.md) and [TLV inventory](TLVS.md): commands, fields,
  tags, source references, version/role permissions, and pending evidence.
- [First test scenarios](TEST_PLAN.md): framing fixtures and contracts to drive
  implementation through TDD.
- [Workload criteria](WORKLOADS.md): provisional simulator profiles, measurement
  accounting, acceptance goals, and the inspected local hardware baseline.

The baseline uses one asynchronous request mechanism and focused capabilities,
one library artifact without initial runtime dependencies, and a separate
application subproject for simulator tooling. API class names remain sketches
until implementation tests establish them. Binary framing, fields, TLVs, and profile
foundations, bind/control and message codecs, and session policies are implemented;
live endpoint APIs and simulators remain planned.

## Remaining design choices

These choices remain open:

- Final transport selection after the JDK sockets/virtual-thread experiment.
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

The integrated build passes 383 library/architecture cases, including 41 request
cases and eight architecture cases, plus 60 review-tool cases. Root integration
restored the matching library tests from cache and reused the tooling results;
the request review records their fresh development runs. Current SOLID evidence
covers all 129 Java type identities. Runtime dependencies remain empty, and all
three archives preserve the Apache license and project notice.

Next: Step 10 adds the first bounded TCP transport.

## References

- [Cloudhopper SMPP](https://github.com/fizzed/cloudhopper-smpp): protocol and session implementation reference.
- [Cloudhopper Commons](https://github.com/twitter/cloudhopper-commons): supporting utilities, including message character encoding and request tracking.
- [SMPP 3.4 specification, issue 1.2](https://smpp.org/SMPP_v3_4_Issue1_2.pdf): protocol definitions and wire format.
- [SMPP 5.0 specification](https://smpp.org/SMPP_v5.pdf): latest verified public protocol specification.
