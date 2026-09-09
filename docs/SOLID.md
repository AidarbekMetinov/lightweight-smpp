# SOLID review policy

SOLID review is required for every Java type created or updated in this project.
The requirement applies to production code, tests, fixtures, and Java build
logic, including records, enums, interfaces, and nested or local types. Review
all five principles even when a particular obligation is not applicable.

This policy is active now. ArchUnit rules run through Jupiter against actual
protocol, codec, profile, session, and request types. `solidReview` checks current type identities
and source hashes against recorded evidence; `check` runs both layers.
The [research report](RESEARCH.md) explains the supporting design literature and
the distinction between structural checks and behavioral review.

## Review each principle

| Principle | Required review | Examples of findings to fix |
| --- | --- | --- |
| Single responsibility | State the type's responsibility and the requirement owner or change driver it serves. Examine all methods and dependencies for unrelated responsibilities. | A frame decoder also authenticates accounts; a session object stores messages in a database; a test fixture owns an unrelated server lifecycle. |
| Open/closed | Identify the actual variation this type must support and show where an extension belongs. Keep stable behavior isolated from that variation. | Adding a vendor TLV requires editing unrelated request tracking; adding a command requires changes throughout client and server loops. |
| Liskov substitution | Review declared and inherited contracts, accepted inputs, promised results, exceptions, ordering, mutability, concurrency, cancellation, and resource ownership. Run shared contract tests for substitutable implementations. | A transport silently drops writes despite a promised failure result; a subtype rejects inputs its interface accepts; a test double accepts writes after closure despite the shared rejection contract. |
| Interface segregation | Name each consumer and the subset of behavior it needs. Ensure capabilities match roles and that implementations do not need meaningless methods. | A bind authenticator must implement delivery callbacks; a receiver must implement unsupported message submission; an optional broadcast handler is forced into every application. |
| Dependency inversion | Inspect source dependencies across boundaries. Session policies should receive abstractions for variable infrastructure, with concrete wiring at the client/server entry points. | Core session logic constructs a socket transport; a codec imports a persistence framework; a core API exposes Netty buffer types. |

The checklist is a project-specific application of SOLID. It is not a metric
based on class length, interface counts, inheritance depth, or naming alone.

## Keep the design small

A cohesive concrete value type can satisfy this policy. A final type does not
need an artificial extension point. An interface is justified by a real consumer
boundary, alternate implementation, callback contract, or extension requirement.

For a type without a custom supertype, explain which substitution checks do not
apply and still check applicable `Object`, collection, resource, or other Java
contracts. A record containing an array needs an explicit ownership and equality
decision; its syntax alone does not establish deep immutability.

Ordinary bug fixes and improvements may change existing code. Open/closed review
focuses on whether a supported extension forces unrelated stable code to change.
Document the intended extension boundary instead of creating a plugin system for
every possible future requirement.

## Workflow for every class-changing step

1. Identify the starting revision and the intended behavior or refactoring. List
   the types expected to change and their responsibilities before implementation.
2. Follow the [TDD workflow](TDD.md). Keep the scope small enough to review clearly.
3. Inventory the final changed files, including staged, unstaged, renamed, and
   untracked Java files. Inspect each file for additional top-level, nested, and
   local types. A Java file is not necessarily one class.
4. Review the complete affected types and the interfaces and consumers whose
   contracts their changes affect. Check all five principles and record concrete
   findings with evidence.
5. Fix violations, rerun relevant tests, and repeat the review for changed code.
6. Save a change-specific report under `docs/reviews/`, for example
   `docs/reviews/0001-pdu-header.md`. Reference the actual source identity and
   SHA-256 of each final Java source file after formatting.
7. Report completion only when the inventory is covered, the reviews are current,
   no known violation remains, and the relevant checks pass.

Reviewing only `git diff HEAD` misses untracked files. Run
`./gradlew solidReviewInventory --console=plain` to inventory current Java types
through the JDK parser, including nested, local, and anonymous types. See
[the input scope and identity rules](REVIEW_FORMAT.md).

No additional permission step is created by this policy. Carry out the review and
fixes as part of the authorized implementation step.

## Evidence format

Use this narrative structure in each change report, plus the required
[`solid-review` blocks](REVIEW_FORMAT.md). Add one type review for every affected
type, including new or changed tests. Existing report entries remain historical;
subsequent changes need new evidence for the new source version.

```markdown
# Review: <short change name>

## Scope

- Starting revision:
- Behavior or refactoring:
- Java files and all affected type identities:
- Source file SHA-256 values after final formatting:
- Affected contracts and consumers:

## Type: <fully qualified type name>

- Responsibility and change driver:
- Consumers and collaborators:
- S: verdict, reasoning, and evidence
- O: verdict, intended extension boundary, and evidence
- L: verdict, applicable contracts, and shared tests
- I: verdict, consumer needs, and evidence
- D: verdict, dependency direction, and evidence
- Findings corrected:
- Remaining findings: none / specific unresolved findings

## TDD and verification

- Red command, scenario, and observed relevant failure:
- Green command and observed result:
- Refactoring and final verification:
- Architecture checks and their actual scope:
- Type inventory reconciliation:
```

Use `pass`, `fail`, or `not applicable` with reasoning for each principle. An
unresolved `fail` prevents completion. A `not applicable` entry must identify the
absent obligation and cannot be used to excuse a violation or missing review.
For local or anonymous types without a stable qualified name, record the enclosing
type, enclosing member, and source location tied to the recorded file hash.

## Automated support

Architecture tests enforce package dependency rules, absence of cycles, and
separation from infrastructure against actual production types. Extend them with
new package boundaries as code arrives. Confirm that every rule selects its
intended types; a vacuous success must not be reported as coverage.

`solidReview` reconciles current type identities and whole-file hashes against
review records. It rejects missing entries, stale hashes, renamed or newly nested
types without coverage, malformed evidence, and unresolved findings. Gradle tracks
the exact source/report file lists, policy documents, launcher, mode, and tool
classpath. It does not depend on Git state. The development-only `review` and
`reviewTest` source sets stay outside the published library.

That check can enforce evidence coverage and freshness. The reviewer still has to
evaluate the substance of the design; a recorded verdict is not a machine proof.

## Current status

Step 3's three production types and four test classes have a complete
[framing review](reviews/0002-pdu-framing.md), with final source hashes and
principle-by-principle findings. Actual package rules run in `ArchitectureTest`.
The validator's 13 Java types also have a complete
[tooling review](reviews/0003-review-coverage.md), and it checks those same records
alongside library/test types. Step 5 adds 23 types and updates ArchitectureTest;
[its review](reviews/0004-fields-profiles.md) supplies current evidence for all 24
affected identities, bringing that snapshot to 43 types. Step 6 adds complete
[command codec reviews](reviews/0005-session-command-codecs.md), including
registry extensions and every nested test fixture. Step 7 adds
[message reviews](reviews/0006-message-codecs.md) for all 28 new identities,
including the nested per-tag fixture. Step 8 adds
[14 session type reviews](reviews/0007-session-state.md) and refreshes
[ArchitectureTest evidence](reviews/0007-session-architecture.md). The complete
integrated inventory now contains 113 current type identities. Each later step must provide evidence for every
affected type; historical reports do not cover changed source.
