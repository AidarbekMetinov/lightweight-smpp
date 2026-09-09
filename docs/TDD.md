# Test-driven development workflow

TDD is required for new or changed behavior in this project. Use small cycles,
with one runnable scenario at a time, and keep the existing tests passing as the
implementation grows. The workflow follows Kent Beck's description of selecting
one scenario, making it pass, and improving the design before continuing.[^1]

## Before implementation

Describe the behavior in terms a caller or peer can observe. Identify success,
boundary, malformed-input, and failure scenarios relevant to the current step.
Choose the next smallest useful scenario. Keep the remaining scenarios as a list;
do not build an entire future implementation or test suite ahead of feedback.

For protocol behavior, derive expected bytes, state transitions, and status codes
from the applicable specification or an independently reviewed fixture. Include
the protocol version and endpoint role. For a public Java contract, state input,
output, error, ownership, and timing expectations before selecting assertions.

## Red

Write and run the selected test before implementing the behavior. Verify that it
fails for the intended missing or incorrect behavior, and record the command and
the observed failure.

For an entirely new API, minimal declarations may be needed to make the test
compile. Then demonstrate the behavior failure. A dependency download problem,
syntax error, unavailable port, or deliberately unconditional `fail()` is not the
required evidence that an assertion detects missing behavior.

A test that unexpectedly passes may be demonstrating existing behavior. Check
the assertion and record that fact; do not pretend a red result occurred. Select
an actual missing scenario before making the corresponding behavior change.

## Green

Implement only what is needed to satisfy the selected contract. Run the focused
test and the relevant existing tests. Preserve the assertions and independently
derived expected values. If a test expectation is wrong, explain the correction
and reconfirm the corrected test's ability to detect the missing behavior.

Record the actual passing command and outcome. The goal is a working behavior
with a meaningful test, followed by a design review; a large implementation that
happens to pass one weak test does not complete the step.

## Refactor and review

With the tests green, improve names, responsibilities, duplication, dependency
direction, and contracts as needed. Review every affected type using the
[SOLID policy](SOLID.md), including tests and helper classes introduced during the
cycle. Rerun the relevant tests after the refactoring.

Apply the pinned formatter with `./gradlew spotlessApply --console=plain` before
recording final source hashes and completing the review. `check` verifies the
format without rewriting source files.

A behavior-preserving refactoring starts from passing tests and returns to
passing tests. If existing coverage does not establish the behavior being
preserved, add characterization tests before restructuring the implementation.
It does not require manufacturing a failure for behavior that is already correct.

## Bug fixes

Reproduce the defect with a regression test and observe it fail before changing
the production behavior. Include the relevant peer input or state sequence.
Then make the fix, verify the test passes, and review neighboring cases and the
affected class contracts.

## Test design for this library

- Assert externally observable results and contracts. Use mocks or fakes only at
  meaningful boundaries, and ensure their behavior matches the real contract.
- Combine known byte fixtures with round-trip tests. Two complementary codec
  errors can cancel each other in a round-trip-only suite.
- Use deterministic time sources for deadline and heartbeat logic. Use bounded
  waits and explicit thread coordination for actual socket tests.
- Test both endpoint roles and both protocol profiles. Each client/server loopback
  test should be supplemented by an independent fixture or implementation when
  verifying interoperability.
- Use shared contract tests for alternate codec, transport, or policy
  implementations. JUnit Jupiter supports reusable test interfaces.[^2]
- Treat assertions about resource cleanup, cancellation, duplicate responses, and
  failure completion as part of the API contract.
- Keep tests focused. Do not add tests solely to mirror private implementation
  details or inflate a coverage percentage.

## Commands and evidence

For a focused cycle, once the test class exists:

```sh
./gradlew test --tests 'kg.aidarbek.smpp.SomeTest' --console=plain
```

Use `./gradlew check --console=plain` before completing a class-changing step.
Record the actual scope of that command: only configured checks can run. In the
current empty project, formatting is configured; architecture rules and
review-coverage checks are still planned. ArchUnit core is available to Jupiter.

Retain normal Gradle caching. Source and test changes should invalidate relevant
task results. When fresh execution is specifically needed to establish evidence,
use a focused diagnostic run with `--rerun-tasks --no-build-cache`. Do not describe
`UP-TO-DATE`, `FROM-CACHE`, skipped, or `NO-SOURCE` output as a freshly executed
test. Cached green results may be reused when their declared inputs match and no
fresh-execution question remains.

Store red, green, refactor, and SOLID evidence together in the change report under
`docs/reviews/`. Preserve short factual outcomes, test names, commands, and source
identities; do not invent timestamps or test histories.

Documentation-only changes use document and link checks. Build configuration
changes use appropriate build verification. Any new behavioral Java build logic
follows the same TDD requirement as library code.

## Simulator tests

Simulator code follows the same cycle. Test rate scheduling, bounded generation,
fault policies, counters, and report calculations with controlled inputs before
running heavy scenarios. A load run supplements these tests; it cannot establish
that an untested report calculation is correct. See [the simulator plan](SIMULATORS.md).

## Sources

[^1]: Kent Beck. [Canon TDD](https://newsletter.kentbeck.com/p/canon-tdd), 11 December 2023.
[^2]: JUnit team. [Test Interfaces and Default Methods](https://docs.junit.org/6.0.0/writing-tests/test-interfaces-and-default-methods.html), JUnit 6.0.0 documentation, accessed 9 September 2026.
