# Release preparation review

Step19 prepares reviewable local artifacts and reports measured behavior.
Publishing remains a separate action. Cloudhopper comparison adapters and their
standalone dependency resolution remain outside the project at the user's
explicit direction; no project source set or runtime acquires that dependency.

## Artifact verification TDD

The standard-library Python checker in `tools/check_release.py` verifies binary,
source and Javadoc contents, exact LICENSE/NOTICE bytes, simulator runtime
isolation, Maven identity/licensing/dependencies and Gradle metadata dependencies
and file hashes. It reads archives without extracting or loading their code.
`tools/tests/test_check_release.py` constructs independent small bundles and
then corrupts one relevant property per case.

1. `python3 -m unittest discover -s tools/tests` produced one error and four
   behavioral failures against the compiling, explicitly unimplemented checker.
   The valid bundle was not accepted, and missing attribution, extra classes,
   runtime dependencies and simulator dependencies were not classified.
   Log: `/tmp/lightweight-smpp-step19-release-red.log`.
2. The same command passed five cases after implementation. That run exposed
   unclosed digest input streams through Python's ResourceWarning.
   Log: `/tmp/lightweight-smpp-step19-release-green.log`.
3. Added a warning-capture regression and Gradle metadata dependency scenario.
   The next run failed on the resource leak, absent metadata checking and
   incomplete artifact reporting. Log:
   `/tmp/lightweight-smpp-step19-release-red2.log`.
4. Scoped digest inputs with context managers and validated module metadata.
   All seven cases passed without warnings, followed by successful verification
   of real generated candidate archives. Log:
   `/tmp/lightweight-smpp-step19-release-green2.log`.
5. Final review found that metadata could omit an archive or point its URL to
   different content while its listed name/hash looked valid. Added independent
   metadata fixtures and corruption cases; the next run failed twice for those
   omissions (`/tmp/lightweight-smpp-step19-release-red3.log`). Validating the
   complete three-archive set and exact local artifact URLs made all nine cases
   pass (`/tmp/lightweight-smpp-step19-release-green3.log`). Hash/size corruption
   subcases also pass without weakening the earlier checks.
6. A final archive-boundary probe placed source in the binary JAR and compiled
   bytes in the source JAR. Both were incorrectly accepted by suffix-filtered
   comparison (`/tmp/lightweight-smpp-step19-release-red4.log`, two subcase
   failures in ten tests). Rejecting mixed Java source/binary entries made all
   ten tests pass (`/tmp/lightweight-smpp-step19-release-green4.log`), followed
   by successful inspection of the real generated archives. The probe invokes
   archive validation directly so an unrelated stale metadata hash cannot hide
   the intended failure.

Release build configuration adds the built-in Maven publication model and local
candidate version. It configures no remote repository or automatic publication.
No Java behavior is changed by these packaging settings, so no artificial Java
red is claimed. Artifact probes supply the relevant package-boundary evidence.

The complete `build.gradle` snapshot has SHA-256
`47502759a279fb1118327a4e6026348175594d6ec23bb04ebec6b1a40965fa56`.
Its existing Groovy `SolidReviewArguments` class was also checked in full:
S: it converts declared review inputs into one deterministic argument vector;
O: managed source/report collections provide the required input variation;
L: its `CommandLineArgumentProvider` contract and property annotations are
unchanged, with sorted relative paths and explicit inventory/check modes;
I: each managed property supplies either consumed inputs or the output location;
D: Gradle managed properties provide values without capturing a live `Project`
inside task execution. Publication configuration does not change this class,
its declared inputs/outputs, or the Java review tool's runtime boundary.

## Review of non-Java tooling

The checker separates archive inspection, content collection, hashing, publication
validation and CLI/report output into functions. Its only variable infrastructure
inputs are explicit paths and the requested version. It has no runtime service,
network access, codec logic or application callback. `ReleaseCheckTest` owns
fixture lifecycle and verifies observable acceptance/rejection; its unittest
inheritance and cleanup registration preserve test isolation. These are Python
files and are outside the Java type inventory; their review does not substitute
for any required Java SOLID evidence.

S: each helper has a single artifact-validation responsibility; the CLI alone
selects inputs and writes output. O: new publication checks extend the verifier
without changing library runtime APIs. L: fixture setup/cleanup obey unittest's
per-case lifecycle; the verifier has no custom inheritance contract. I: callers
use one check function or the three-option CLI; library users acquire no tooling
interface. D: validation depends on the Python standard library and explicit
artifact paths; external peer frameworks are absent.

The whole current Python files reviewed here have these SHA-256 values:

- `tools/check_release.py`: `9664be032f19f35ebd11d6b7031f12aad70812f55978541bf4e3e2e447474918`.
- `tools/tests/test_check_release.py`: `cf3d2287b4f418f99381af36e014df90b81eaf113c061caeaa1b4f76fa69f30d`.

Full project verification, external comparison evidence,
reproducibility and cold/warm build measurements are recorded with the completed
candidate in [release evidence](../RELEASE.md).

## External comparison fixture review

These seven Java fixture types are **outside the repository** in
`/tmp/lightweight-smpp-external-interop`. They are not production classes, project
test dependencies or entries in the repository's Java inventory. They still
receive a manual contract review because their behavior determines the recorded
comparison evidence. The final comparison replay must identify their exact
source hashes and the library binary selected as input.

| External type | S | O | L | I | D |
| --- | --- | --- | --- | --- | --- |
| `CloudhopperPeer` | Composes one finite external process role and cleanup. | Adding a comparison scenario changes this explicit executable composition, without modifying either library. | Final executable has no custom inherited API contract. | Its bounded three-argument process interface carries role, local port and scenario only. | Depends on the independently pinned peer API; our library is deliberately absent from its classpath. |
| `PeerServerHandler` | Owns demonstration authentication and accepted-session handoff. | Uses the peer's published handler seam. | Implements bind, ready and destroyed callbacks with their required ownership; destroys accepted peer sessions. | Implements only the required server callback contract. | Uses peer session/configuration contracts and completion stages, without our codec or endpoint classes. |
| `PeerSessionHandler` | Validates received fixture requests and returns application decisions. | Specializes the external framework's documented session callback seam. | Returns paired responses; response observation preserves the superclass filter contract. | Narrow raw-type suppressions exist only where the published callback method requires the raw generic signature. | Uses peer PDU values and independently constructed fixture expectations; no implementation-under-test helper is used. |
| `PeerMessages` | Constructs and checks the external side's finite message exchanges. | New cases are explicit fixture additions, not production extension hooks. | Final static utility has no inherited substitutability obligation. | Exposes only scenario send/field-check helpers to the external adapters. | Uses the peer's public session and PDU types and JDK values. |
| `PeerProcess` | Owns one child JVM, bounded output observation and physical process cleanup. | The executable scenario is supplied through arguments. | AutoCloseable cleanup force-terminates a remaining child and preserves interruption. | Test callers use readiness, completion and close only. | Uses JDK process/file APIs and an explicit standalone peer classpath property. |
| `CloudhopperInteropTest` | Verifies independent bind/control and credential outcomes. | The bind-mode matrix uses parameterized fixtures. | Jupiter lifecycle and temporary-directory cleanup remain intact. | Assertions exercise public endpoint results or independent captured wire bytes. | Uses our public endpoint APIs and the process fixture; no shared peer codec creates expected bytes. |
| `MessageInteropTest` | Verifies binary fields, typed message services and responses across processes. | Scenario methods extend the comparison matrix without library changes. | Handler fixtures obey paired-response and bounded completion contracts. | Tests register only the operations needed by each scenario. | Uses our public immutable fields/endpoint ports; independent peer fields are separately constructed. |

The initial comparison used an explicit unimplemented peer executable and
observed a behavioral readiness failure before implementation. Subsequent
expansions observed missing client-role and message-service behavior before
adding those adapters. A compilation warning and an oversized demonstration
password were setup corrections, not behavioral TDD evidence. Two mistaken
fixture expectations were corrected against the specification: failed 3.4 bind
bodies are absent, and successful delivery response message IDs are empty.
The peer's default negative-submission encoding required an explicit header-only
length. These findings produced the compatibility notes in
[interoperability evidence](../INTEROPERABILITY.md); no library validation was
relaxed to obtain a passing comparison.

## Final source and artifact verification

Root's integrated check/build passed in 117.264 seconds, including 137 freshly
executed simulator cases and matching cached library/tool results. A fresh
checkout with fresh task outputs then executed all 946 Java cases (749 library,
137 simulator, 60 review tooling) and all 28 actionable build tasks successfully
in 128.559 seconds. Formatting, strict compilation, compiled examples, Javadoc,
licensed archives and all 512 current Java review identities passed. Root and
fresh-checkout source/test/tool hashes match the reviewed final files; later
measurement-document changes do not change that source snapshot.

The final simulator Python command discovered 37 methods, with 35 passing and
two opt-in process methods skipped. Both opt-in methods separately executed all
56 fresh raw fault pairs on the exact final simulator and passed. The ten
release-checker tests and the checker applied to the real artifacts also passed.
The before/after artifact checks keep external harness observations tied to the
actual binary, including 12 receipt pairs and 24 independent application-payload
audits. The payload observer's separate unmatched-unbind finding is retained
and is not reclassified as a passing graceful control exchange.

Two normal cache-enabled repetitions took 1.167 and 0.668 seconds, the latter
reusing configuration. Compilation/tests were up to date; the built-in POM and
module-metadata tasks still executed. Download and global caches were preserved
during the fresh-output build. All 11 audited root/fresh-checkout outputs were
byte-identical, and remained identical across cold/warm invocation conditions.
The independent checkout reproduces the final candidate's library and simulator
JAR hashes. Commands, test XML hashes, source manifest and artifact hashes are
retained under `build/runs/step19-reproducibility/`; the completed summary SHA-256
is `e03e0ed3c450622b0da70017fcebd20ba491157a9b6e35a507770bee8db5dd21`.

This verification introduces no further Java type or behavior change.
The [release record](../RELEASE.md) states the measured scope, application duties,
dependency footprint and remaining external-interoperability/performance limits.

Both final full-hour bidirectional soaks completed on 9 September 2026 at
15:31:55–56 UTC. Their four roles observed the original 3600-second phase and
30-second drain. All 31891984 admitted measurement requests succeeded, with
complete endpoint/sampler cleanup and zero final live ownership; 4108016 skipped
arrivals failed the unchanged rate/work criteria. The final read-only analysis
reconciled all eight final process pairs and sixteen reports without integrity
issues. Its SHA-256 is
`199565ed3e923dc870d9381a53faa98aeb3076e36ba057696c9e11c1d1992b47`.
The host observer was stopped explicitly only after the campaign helpers and
their owned child processes completed. This completes local release preparation;
publication and unestablished production-capacity/external-5.0 claims remain
separate from the completed implementation and evidence.
