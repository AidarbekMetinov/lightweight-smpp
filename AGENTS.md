# Project guidance

## Purpose

Build a lightweight, simple Java SMPP library in this project. Use Cloudhopper
SMPP and Cloudhopper Commons as reference projects.

The project uses [Apache License 2.0](LICENSE). Preserve the project attribution
in [NOTICE](NOTICE); the binary, source, and Javadoc JARs include both files.

## Working agreement

- Develop the project step by step. Keep work focused on the current step requested
  by the user.
- Complete that step and its relevant verification before reporting the result.
  A roadmap entry alone is not a request to implement later stages.
- Create and maintain project Markdown documentation under `docs/`. The user has
  also authorized this root `AGENTS.md` file.
- Continue routine work within the authorized step without repeatedly asking for
  confirmation. User instructions can extend or change the scope.
- Keep agreed requirements, proposals, and open questions distinct in documentation.
- Use short, simple commit messages, such as `Set up project` or `Add PDU header`.

## Current planning context

The project must support both SMPP clients and servers. The protocol targets are
SMPP 5.0, the latest verified public specification, and SMPP 3.4 for interoperability.
Track support by operation, version, and endpoint role; partial support must be
described accurately. SMPP 3.3 is a possible later compatibility addition.

Client and server simulators are also required, including configurable heavy-load
and fault scenarios. Keep their tooling dependencies outside the library runtime
artifact. Follow [the simulator plan](docs/SIMULATORS.md); use TDD and SOLID review
for simulator code as well as library code. Actual load measurements must execute
freshly, even though compilation and deterministic tests can use build caches.

Research and Steps 1–12 are complete. Step 13, the first simulators, is next.
Bounded fields, raw TLVs, explicit profiles, bind/control
and message codecs, deterministic session/version policies, and bounded request
ownership are implemented. See [field contracts](docs/FIELDS.md), [command contracts](docs/COMMANDS.md),
[message contracts](docs/MESSAGES.md), [session contracts](docs/SESSIONS.md),
and [request contracts](docs/REQUESTS.md). Bounded TCP transport and listener
adapters implement [frame transport ports](docs/TRANSPORT.md). [Client/server
binding](docs/ENDPOINTS.md), authentication, enquiries, cancellation and bounded
shutdown are implemented for both profiles. [Message exchange](docs/EXCHANGE.md)
adds typed submission, delivery and data-message senders, optional asynchronous
handlers, ordered bounded replies and handler cleanup. Simulators remain Step 13.
The design baseline uses one library artifact, no initial runtime dependencies,
one asynchronous request mechanism, and focused
endpoint capabilities. Simulator tooling will be a separate application subproject.
Do not implement later roadmap steps without a request to proceed with them.

Use [the API contracts](docs/API.md), [protocol inventory](docs/PROTOCOL.md),
[TLV inventory](docs/TLVS.md), and [first test scenarios](docs/TEST_PLAN.md) as the
implementation baseline. [Workload profiles](docs/WORKLOADS.md) contain provisional
development targets; production capacity and latency requirements remain open.
Binding and messaging examples are compiled from the separate examples source
set and exercised against real endpoints and raw peers. Refine names through TDD and
document changes to the agreed behavior.

The user wants strong Java coding practices and development caching. The build
now uses a Java 21 toolchain as the development baseline, matching the installed
JDK. Revisit the target version if release compatibility requirements change.

The existing Gradle project uses the group `kg.aidarbek`, the project name
`lightweight-smpp`, and JUnit Jupiter for tests. Java 21 is available locally.

Read [the project overview](docs/README.md) and [the roadmap](docs/ROADMAP.md) for
planning context. Update the documents as decisions are made and steps are
completed.

## Mandatory SOLID review

- Review every project-owned Java class or other type created or updated against
  all five SOLID principles. Include records, enums, interfaces, nested types,
  test classes, fixtures, and Java build logic. Review the whole affected type,
  its contracts, and affected consumers, not just changed lines.
- Follow [the SOLID review policy](docs/SOLID.md). Record the type inventory,
  source revisions or hashes, principle-by-principle findings, and test evidence
  in a change-specific Markdown report under `docs/reviews/`.
- Fix identified violations within the current step before declaring it complete.
  Do not silently skip a type, mark unexplained checks as passed, or disable a
  rule to obtain a successful build. Any non-applicable check needs a specific
  explanation; existing inherited contracts must still be considered.
- Recheck the review after refactoring or other source changes. A review of an
  older version of a class does not cover its current implementation.
- Use architecture checks and contract tests to support review. A passing linter,
  compiler, coverage report, or architecture test alone does not establish SOLID
  compliance. ArchUnit core is available to Jupiter tests; project architecture
  rules cover production protocol, codec, profile, session, request, frame-port
  and transport packages, plus endpoint composition and its coordinator-to-port boundary. `solidReview`
  checks review coverage and freshness, including the tool's own Java sources and
  tests.
- Keep abstractions purposeful. SOLID does not require an interface for every
  class, a subclass hierarchy for every command, or separate Gradle modules.

## Mandatory TDD

- Follow [the TDD workflow](docs/TDD.md) for new or changed behavior: select one
  scenario, observe a relevant failing test, make the smallest implementation
  pass, then refactor with tests passing.
- For a bug fix, demonstrate the bug with a failing regression test first.
- Record actual red, green, and final verification commands and outcomes in the
  change report. Do not invent an earlier failing run or substitute an unrelated
  compilation, dependency, or environment failure for behavioral evidence.
- For behavior-preserving refactors, establish the existing tests are green,
  refactor, and verify them again. Add characterization tests first when the
  affected behavior is not adequately covered.
- Keep tests focused on observable contracts and independently derived protocol
  expectations. Do not weaken assertions or copy implementation output into
  expected values to make a test pass.
- Apply SOLID review during refactoring and before completion. TDD and SOLID are
  both required; neither replaces the other.
- Documentation-only changes need document verification, not artificial failing
  Java tests. Report when no Java types or behavior changed.

## Implementation and verification

- Favor a small, understandable API and dependencies justified by actual needs.
- Follow the Java conventions and build workflow in
  [the development guide](docs/DEVELOPMENT.md).
- Java formatting uses pinned Spotless and Palantir Java Format versions. Use
  `./gradlew spotlessApply --console=plain` to apply formatting before final source
  hashes and class review. Run `./gradlew solidReviewInventory --console=plain`
  to list current type identities and hashes. Record every affected type in the
  [review evidence format](docs/REVIEW_FORMAT.md). `./gradlew check --console=plain`
  includes formatting, behavior and tooling tests, and review coverage; checking
  does not rewrite source files.
- Keep compiler warnings enabled and fix them. Any warning suppression should be
  narrow and explain why it is necessary.
- Preserve Gradle build and configuration caching. New task logic must declare its
  inputs and outputs and remain compatible with configuration caching.
- Use normal incremental builds during development. Reserve `clean`,
  `--rerun-tasks`, and `--refresh-dependencies` for a specific verification or
  troubleshooting need.
- Check wire formats and protocol rules against the SMPP specification; use the
  reference projects to understand implementation choices and interoperability.
- When protocol code is introduced, verify known wire bytes and malformed input.
  When networking is introduced, verify timeouts, disconnections, and cleanup
  using a local test peer.
- Run focused checks appropriate to each change. Documentation-only changes need
  content and link review, not new tests.
- The existing test command, run from the project root, is:

  ```sh
  ./gradlew test --console=plain
  ```

- Report clearly when a successful build had no tests to run.
- Step 2 tooling evidence is in [the code-check review](docs/reviews/0001-code-checks.md).
  Step 3 framing contracts and evidence are in [the framing guide](docs/FRAMING.md)
  and [the framing review](docs/reviews/0002-pdu-framing.md). Step 4 validator
  evidence is in [the review-coverage report](docs/reviews/0003-review-coverage.md).
  Step 5 fields/profile evidence and the updated architecture review are in
  [the field review](docs/reviews/0004-fields-profiles.md). Step 6 command codec
  evidence is in [the command review](docs/reviews/0005-session-command-codecs.md).
  Step 7 evidence is in [the message review](docs/reviews/0006-message-codecs.md).
  Step 8 evidence is in [the session review](docs/reviews/0007-session-state.md)
  and [the architecture review](docs/reviews/0007-session-architecture.md). Step 9
  evidence is in [the request review](docs/reviews/0008-request-tracking.md) and
  [the architecture review](docs/reviews/0008-request-architecture.md). Step 10
  evidence is in [the transport review](docs/reviews/0009-tcp-transport.md) and
  [the architecture review](docs/reviews/0009-transport-architecture.md). Step 11
  evidence is in [the endpoint review](docs/reviews/0010-client-server-binding.md)
  and [the architecture review](docs/reviews/0010-endpoint-architecture.md). Step 12
  evidence is in [the exchange review](docs/reviews/0011-message-exchange.md).

## References

- [Cloudhopper SMPP](https://github.com/fizzed/cloudhopper-smpp)
- [Cloudhopper Commons](https://github.com/twitter/cloudhopper-commons)
- [SMPP 3.4 specification, issue 1.2](https://smpp.org/SMPP_v3_4_Issue1_2.pdf)
- [SMPP 5.0 specification](https://smpp.org/SMPP_v5.pdf)
- [Research and architecture recommendations](docs/RESEARCH.md)
