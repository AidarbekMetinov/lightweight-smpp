# Review: Automatic SOLID review coverage

## Scope

- Development started from `bde0e1e6e470897dd15df008d7427121e18f116d` in the isolated
  worktree `/tmp/lightweight-smpp-review-tool`; integration follows Step 3 commit
  `c684414` (`Add PDU framing`). No Step 3 Java type is changed by this implementation.
- Behavior: parse every selected Java source, reconcile per-type current hashes
  against tagged evidence, reject missing/stale/malformed/unresolved evidence,
  and expose development-only Gradle verification with declared inputs.
- Final inventory: 13 Java types in 12 source files, including the private
  `JavaSourceInventory.TypeScanner`, four test classes and the compiler adapter.
  The following blocks record each complete final formatted source version.
- Manual bootstrap reviewed whole types, contracts and affected consumers before
  asking the validator to accept these same records. Architecture/library tests
  from Step 3 remain separate evidence; compiler/type coverage is not semantic
  proof of SOLID compliance.
- `review` and `reviewTest` source sets keep the tool and Jupiter tests outside
  all library archives and runtime dependencies. A separate subproject would
  add unnecessary project wiring for this one small build command.
- The [format and task contract](../REVIEW_FORMAT.md) describes identities,
  selected inputs, historic evidence and the exact command modes.

## Type reviews

### kg.aidarbek.smpp.review.JavaSourceInventory

```solid-review
source: src/review/java/kg/aidarbek/smpp/review/JavaSourceInventory.java
type: kg.aidarbek.smpp.review.JavaSourceInventory
sha256: 0bc8211a1fa5699db4830c27aafc7b40b3625dd33c25370c7806398b3e48ba85
responsibility: Builds a deterministic Java type inventory for the review coverage tool from compiler source snapshots.
consumers: ReviewCheck requests the inventory; JavaSourceSnapshot supplies content; TypeScanner extracts identities; inventory tests assert the language and hash contracts.
S: pass | All methods build or validate the source inventory; evidence parsing, SOLID verdict policy and output files stay with their own consumers.
O: pass | Java syntax variation goes through JDK compiler trees and the focused scanner; new report formats do not change inventory construction.
L: pass | Returns a sorted immutable list, rejects syntax and identity collisions, closes the compiler file manager and parses the same snapshots it hashes; snapshot-edit and diagnostic tests pass.
I: pass | The command needs scan; the package-level snapshot entry point supplies a focused compiler-input boundary for deterministic ownership tests. No unused application capability is exposed.
D: pass | Depends on JDK compiler contracts and immutable source values; it does not import Gradle, review Markdown, protocol code or runtime transport.
findings: none
```

### kg.aidarbek.smpp.review.JavaSourceInventory.TypeScanner

```solid-review
source: src/review/java/kg/aidarbek/smpp/review/JavaSourceInventory.java
type: kg.aidarbek.smpp.review.JavaSourceInventory.TypeScanner
sha256: 0bc8211a1fa5699db4830c27aafc7b40b3625dd33c25370c7806398b3e48ba85
responsibility: Translates each compiler class tree into the complete enclosing type and member identity for coverage.
consumers: JavaSourceInventory owns each scanner and result list; JDK TreePathScanner invokes visitClass; the nested/local/anonymous fixture exercises all traversal paths.
S: pass | Identity construction and enclosing-member lookup serve one inventory rule; the scanner does not read files, hash content or judge evidence.
O: pass | The existing compiler visitor traverses all syntax forms; named member and source-location rules are centralized without an artificial type hierarchy.
L: pass | visitClass returns the Void traversal result from super.visitClass so descendants remain visited; all eleven fixture types including enum constant and anonymous bodies are inventoried.
I: pass | A private visitor implements only the visitClass specialization needed by its compiler consumer; inherited traversal remains available to the framework.
D: pass | Uses compiler tree and source-position abstractions plus per-scan values; no filesystem or library policy depends on this private adapter.
findings: none
```

### kg.aidarbek.smpp.review.JavaSourceSnapshot

```solid-review
source: src/review/java/kg/aidarbek/smpp/review/JavaSourceSnapshot.java
type: kg.aidarbek.smpp.review.JavaSourceSnapshot
sha256: 76621cd7d0e14a58197b1ea73037a469766099180d8fb9a7a96239b1a70900d9
responsibility: Owns one strict UTF-8 source snapshot, its source identity and exact byte hash for the compiler boundary.
consumers: JavaSourceInventory creates snapshots; the JDK compiler reads them as JavaFileObject instances; snapshot and encoding tests verify ownership.
S: pass | Reading, decoding and hashing produce one immutable source version; the snapshot does not parse types, traverse directories or inspect reviews.
O: pass | Adapts source content through the existing JavaFileObject contract; changing compiler traversal or review policy does not change snapshot ownership.
L: pass | SimpleJavaFileObject gets a source URI and SOURCE kind; getCharContent returns the same immutable valid text for either encoding flag, getName gives the relative source name, and inherited unsupported operations remain unchanged. A disk-edit regression verifies AST/hash consistency.
I: pass | The compiler receives its source-file interface while inventory callers use only source and sha256 accessors; no mutable byte array or stream is exposed.
D: pass | The concrete filesystem read is confined to the source adapter factory; compiler consumers use JDK file-object contracts and the snapshot has no Gradle or evidence dependency.
findings: none
```

### kg.aidarbek.smpp.review.PrincipleReview

```solid-review
source: src/review/java/kg/aidarbek/smpp/review/PrincipleReview.java
type: kg.aidarbek.smpp.review.PrincipleReview
sha256: 9f679f7267afef5d9b6bc87f28a4716e47095ab42a4f579893aaedd3c64d3eba
responsibility: Carries one recorded SOLID verdict and its explanation between parsing and coverage checks.
consumers: ReviewDocumentParser constructs validated assessments; ReviewCoverage reads verdict and reasoning; parser and policy tests compare these values.
S: pass | Both components describe one assessment; syntax validation stays in the parser and acceptance policy stays in coverage.
O: pass | An immutable value needs no supported extension point; new evidence parsing rules do not require subclassing or extra methods.
L: pass | Generated record equality, hash code and accessors retain String value semantics; there is no custom supertype, mutable storage or resource contract.
I: pass | Its two accessors match the parser and policy consumers; it defines no unused interface methods.
D: pass | Contains only Strings and depends on neither compiler infrastructure nor the library runtime.
findings: none
```

### kg.aidarbek.smpp.review.ReviewEvidence

```solid-review
source: src/review/java/kg/aidarbek/smpp/review/ReviewEvidence.java
type: kg.aidarbek.smpp.review.ReviewEvidence
sha256: bda09be23f4d63162b1574f287558c5768220c9d8339b3233832c19ab76f54c2
responsibility: Carries one complete type review as immutable data for coverage validation.
consumers: ReviewDocumentParser supplies the source key, responsibility, consumers, five assessments and findings; ReviewCoverage consumes the keyed assessments.
S: pass | Every component belongs to a single recorded type review; parsing and validation remain separate services.
O: pass | Fixed evidence data follows the documented format; there is no alternate implementation or unsupported extension API to maintain.
L: pass | Map.copyOf rejects null map entries and prevents caller mutation from changing review contents; record equality uses immutable keys, Strings, assessment records and map values. Parser and coverage fixtures exercise whole-value use.
I: pass | Package-private accessors expose only the fields the parser and policy need; consumers are not forced into IO or compiler operations.
D: pass | Depends on immutable review values and the JDK Map contract; no reporting framework, transport or Gradle type enters the model.
findings: none
```

### kg.aidarbek.smpp.review.SourceType

```solid-review
source: src/review/java/kg/aidarbek/smpp/review/SourceType.java
type: kg.aidarbek.smpp.review.SourceType
sha256: 1cccba7cf1153628431b10bad981839710305f1c117e37c0e529e52ffd3efb5e
responsibility: Identifies one source type at one exact whole-file SHA-256 revision.
consumers: JavaSourceInventory constructs identities; ReviewDocumentParser reads reviewed identities; ReviewCoverage matches source path, identity and hash together.
S: pass | The three immutable Strings jointly form one review coverage key; none owns parsing, discovery or verdict behavior.
O: pass | Source/type/hash variation is represented as values; named and local types use the same key without a subclass for each syntax kind.
L: pass | Generated record equality and hash code include all three components, so separate source roots and changed hashes cannot compare as the same reviewed version; inventory and coverage tests assert those contracts.
I: pass | Three accessors and value equality are the complete needs of inventory, parser and validator; no consumer receives unrelated capabilities.
D: pass | This shared internal value depends only on Java Strings, keeping the source and evidence layers independent of their IO adapters.
findings: none
```

### kg.aidarbek.smpp.review.ReviewDocumentParser

```solid-review
source: src/review/java/kg/aidarbek/smpp/review/ReviewDocumentParser.java
type: kg.aidarbek.smpp.review.ReviewDocumentParser
sha256: aec124b0d54289bd68d986eef5e4fb1701313e94895533ebf5ed1a910b4cfd5c
responsibility: Reads and structurally validates tagged SOLID review blocks in Markdown reports.
consumers: ReviewCheck supplies text and a diagnostic document name; ReviewCoverage receives immutable ReviewEvidence values; parser tests cover valid, historical, quoted and malformed blocks.
S: pass | Fence handling, required fields, paths, hashes and verdict syntax all belong to the evidence format; it performs no source discovery or coverage decisions.
O: pass | The documented format boundary is contained here, so changing Markdown evidence syntax does not alter the compiler inventory or policy matcher.
L: pass | Stateless calls return immutable results, preserve assessment explanations and fail malformed tagged evidence with its document location; ordinary fenced examples are ignored, including the regression for a spaced malformed review tag.
I: pass | One text-to-evidence operation meets the command caller need; no filesystem, task lifecycle or Git API is exposed.
D: pass | Depends only on standard collections and internal immutable evidence records; source inventory and CLI infrastructure do not enter the parser.
findings: none
```

### kg.aidarbek.smpp.review.ReviewCoverage

```solid-review
source: src/review/java/kg/aidarbek/smpp/review/ReviewCoverage.java
type: kg.aidarbek.smpp.review.ReviewCoverage
sha256: fc8b1a135cc187c65b9a04e2ba1e2c6b3c6ddfb60f1d108cecd37faeaecd6dd7
responsibility: Decides whether every inventoried type has complete current review evidence with no unresolved findings.
consumers: ReviewCheck supplies parsed inventory and evidence; coverage tests exercise current, absent, stale, renamed, nested, cross-root and conflicting records.
S: pass | Every branch applies the coverage and freshness policy; it performs no parsing, filesystem discovery, formatting or Git operation.
O: pass | Current source identities and historical evidence are data inputs; extensions to Java syntax and Markdown parsing remain outside this fixed policy.
L: pass | Returns immutable violation lists, matches all key fields, rejects every failing current assessment and remaining finding, and allows only historical mismatches to remain historical; current-failure and cross-root tests pass.
I: pass | One in-memory validation method serves the command and tests without requiring consumers to manage compiler resources or files.
D: pass | The policy depends on immutable inventory/evidence values and JDK collections, leaving concrete IO and Gradle wiring at the command boundary.
findings: none
```

### kg.aidarbek.smpp.review.ReviewCheck

```solid-review
source: src/review/java/kg/aidarbek/smpp/review/ReviewCheck.java
type: kg.aidarbek.smpp.review.ReviewCheck
sha256: 6a8d139cf7315510790c0c8d522248c9f8cdbd48ad5f94c3e165c18c60c45a9d
responsibility: Runs the development command, selects its input mode and owns deterministic inventory output lifecycle.
consumers: Gradle invokes explicit file-list modes through SolidReviewArguments; standalone callers can request filesystem discovery or an unvalidated inventory; command tests use temporary projects.
S: pass | CLI selection, concrete wiring and output lifecycle form one command adapter; AST parsing, evidence syntax and SOLID acceptance are delegated to their focused types.
O: pass | Declared-file and standalone modes share validation and rendering; source syntax or verdict rule changes remain within the inventory/parser/policy boundaries.
L: pass | Explicit modes consume exactly the supplied relative files, reject invalid argument pairs and escaping paths, fail empty coverage, remove prior success output on validation failure and emit deterministic UTF-8 results. The caller owns the replaced destination; directory streams are closed.
I: pass | Gradle consumes one main entry point and selected flags; helpers remain private except the package-level command runner used by integration tests.
D: pass | The entry point constructs JDK file adapters and focused tool services; ReviewCoverage never depends on the CLI, and none of the tooling enters public library APIs or runtime artifacts.
findings: none
```

### kg.aidarbek.smpp.review.JavaSourceInventoryTest

```solid-review
source: src/reviewTest/java/kg/aidarbek/smpp/review/JavaSourceInventoryTest.java
type: kg.aidarbek.smpp.review.JavaSourceInventoryTest
sha256: 7a3dcdaaed6e422fcbfb22c39f7b743f5e8a21a845d3f543aa8eeab7c49229a0
responsibility: Verifies compiler inventory identities, exact hashes, snapshot consistency and invalid source handling.
consumers: JUnit Jupiter owns @TempDir and test discovery; the tests use JavaSourceInventory and JavaSourceSnapshot with independently written Java source fixtures.
S: pass | All test methods concern the source inventory contract, including strict encoding and duplicate-input behavior; Markdown and Gradle behavior are tested elsewhere.
O: pass | Additional Java syntax examples can be added as focused inputs without changing production contracts or introducing a test inheritance framework.
L: pass | Package-private final Jupiter tests preserve runner discovery, use bounded local files owned by @TempDir, and compare literal identities and an independently calculated SHA-256; snapshot mutation is coordinated synchronously without timing races.
I: pass | The test runner sees only focused annotated scenarios; no custom fixture interface forces unrelated setup or unused methods.
D: pass | Test-only code depends on its subject, JDK files and Jupiter; it supplies ordinary source strings and does not inject test dependencies into the library.
findings: none
```

### kg.aidarbek.smpp.review.ReviewCheckTest

```solid-review
source: src/reviewTest/java/kg/aidarbek/smpp/review/ReviewCheckTest.java
type: kg.aidarbek.smpp.review.ReviewCheckTest
sha256: e19f8aad05a09c70df7bab2059e41906abeac30f6139938e9bf419522c365627
responsibility: Verifies the review command against complete and invalid temporary project inputs and explicit argument lists.
consumers: JUnit Jupiter owns per-test temporary roots; ReviewCheck is the subject; private createProject supplies one reviewed integer record and matching report.
S: pass | Output bytes, failed-output cleanup, scope selection, argument rejection and empty coverage are all externally observable command contracts.
O: pass | New command scenarios add independent temporary inputs; the shared project builder contains only the minimal coherent fixture and no extensible service framework.
L: pass | Tests preserve Jupiter lifecycle, assert the literal deterministic result, leave cleanup to @TempDir and require relevant exceptions before checking artifact removal. Parameterized cases do not share mutable project state.
I: pass | The runner uses focused test methods and a private setup helper; no implementation must provide meaningless callback or fixture operations.
D: pass | The integration test uses the concrete filesystem boundary that the command owns, plus Jupiter and the tool entry point; all dependencies remain in reviewTest.
findings: none
```

### kg.aidarbek.smpp.review.ReviewCoverageTest

```solid-review
source: src/reviewTest/java/kg/aidarbek/smpp/review/ReviewCoverageTest.java
type: kg.aidarbek.smpp.review.ReviewCoverageTest
sha256: a3b5089b5fca6479532cdb61084e966d9b236df8e5895b3889ec3357330c928b
responsibility: Verifies source/evidence matching and the unresolved-findings policy using explicit immutable values.
consumers: JUnit invokes independent policy scenarios; ReviewCoverage receives values created by the focused private review helper.
S: pass | Missing, stale, current, nested, renamed, cross-root, historical and conflicting entries all exercise one coverage policy rather than compiler or Markdown internals.
O: pass | Adding policy cases changes test data and assertions, while the fixed review builder retains the complete evidence contract without an inheritance hierarchy.
L: pass | Each test supplies fresh or immutable values, checks externally returned violations, and verifies all five failing principles; the helper preserves complete evidence and record value semantics.
I: pass | Jupiter discovers only the intended scenarios and parameterized inputs; the private builder has one complete-review purpose and exposes no fixture interface.
D: pass | Uses only the policy and value types, JDK collections and Jupiter; no filesystem, compiler or Gradle infrastructure is needed for policy behavior.
findings: none
```

### kg.aidarbek.smpp.review.ReviewDocumentParserTest

```solid-review
source: src/reviewTest/java/kg/aidarbek/smpp/review/ReviewDocumentParserTest.java
type: kg.aidarbek.smpp.review.ReviewDocumentParserTest
sha256: 29e52ceff68e78c8eb5405dfe75f88daf55dd92705229159d6091d682a007bd5
responsibility: Verifies strict tagged Markdown evidence parsing and preservation of review data.
consumers: JUnit runs valid and malformed report scenarios; ReviewDocumentParser consumes explicit text fixtures; the private malformedEvidence source enumerates schema violations.
S: pass | The tests all concern the evidence format, including nested/quoted fences and a malformed spaced tag; they do not validate coverage by relying on source discovery.
O: pass | New malformed inputs extend the parameter source or a focused regression method without altering unrelated parser expectations.
L: pass | Expected field values are literal independent fixtures; exceptions must have the requested diagnostic type/name, ordinary fenced examples cannot inflate count, and no shared mutable report state is retained.
I: pass | Jupiter consumes focused annotated methods and one argument source; no custom test interface requires irrelevant capabilities.
D: pass | Test-only dependencies are the parser, immutable assessment values, Strings/streams and Jupiter; no framework dependency enters production library code.
findings: none
```

## Gradle build type review

`SolidReviewArguments` is a project-owned Groovy type in `build.gradle`, outside
Java AST inventory. Its complete final build file SHA-256 is
`9cd99f9b9fd782ae43b8cef471e8a13c30c2677a113bfd1bb1312ebcb2ce59eb`.

- Responsibility and consumers: translate Gradle's declared file collections into
  the exact argument list consumed by `ReviewCheck`. The two JavaExec tasks and
  Gradle's configuration/input serialization consume its managed properties.
- **S — pass:** argument projection and its input annotations belong to the same
  build boundary. The provider does not parse Java, evaluate evidence or manage Git.
- **O — pass:** task mode and selected files are property values; source additions
  flow through the lazy file collections without editing stable command logic.
- **L — pass:** `CommandLineArgumentProvider.asArguments()` returns a fresh ordered
  iterable of strings. Required properties are set at configuration time; managed
  getters follow Gradle's property contract. Execution reads no live `Project`
  object. New-source and policy probes retained configuration-cache reuse.
- **I — pass:** Gradle needs one argument-provider operation; getters expose only
  its source/report inputs, mode, root and destination. The command receives plain
  paths rather than Gradle objects or filesystem callbacks.
- **D — pass:** this concrete build adapter depends on Gradle APIs at the wiring
  boundary; Java inventory and review policy remain independent of Gradle.
- Findings corrected: input discovery was aligned by supplying the exact declared
  file collections instead of relying on similar but potentially different
  filesystem walks. Root `build/` is excluded explicitly, while a Java package
  named `build` is included. Remaining findings: none.

## TDD and verification

Development logs are retained for this session under `/tmp/lws-review-tdd`.
The table records actual behavior failures and matching successful runs, not a
reconstructed or assumed earlier red. Except for the first inventory cycle and
initial nested run, commands used the isolated-daemon option
`-Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=review-tool'` and always used
`--console=plain`. That option prevented shared worktrees from competing for one
Gradle daemon's filesystem-watch state; it is not a committed project setting.

For a row naming a selector, the red command was
`./gradlew reviewTest --tests '<selector>'` with those suffix options. Green runs
used `./gradlew reviewTest` with the same suffix, except the first cycle also
retained its selector. The two selectors in cycle 10 were both passed as
`--tests` arguments in one invocation. Cycle 19 additionally requested
`solidReviewInventory spotlessApply` in its green invocation. All code was compiled before behavioral
assertions ran; dependency or compilation failures are not counted as reds.

| Cycle and selector | Observed red | Observed green and logs |
| --- | --- | --- |
| 1: `*JavaSourceInventoryTest.inventoriesNamedTypesWithTheirExactSourceHash` | Empty inventory instead of the source/type/hash value. | Focused test passed; `01-inventory-red-corrected-fixture.log`, `01-inventory-green.log`. |
| 2: `*JavaSourceInventoryTest.inventoriesMembersLocalsAnonymousClassesAndEnumConstantBodies` | Only Outer was found instead of all 11 declarations. | Complete identities passed in `03-collision-and-nested-green.log`; corrected red is `02-nested-red-corrected-location.log`. |
| 3: `*JavaSourceInventoryTest.rejectsCollidingTypeIdentitiesWithinOneSource` | Duplicate declarations produced no exception. | Whole suite passed; `03-collision-red.log`, `03-collision-and-nested-green.log`. |
| 4: `*ReviewDocumentParserTest.readsOneCompleteReviewFromMarkdown` | Zero records instead of the complete parsed review. | Whole suite passed; `04-evidence-red.log`, `04-evidence-green.log`. |
| 5: `*ReviewDocumentParserTest.rejectsMalformedEvidenceWithItsDocumentName` | All 21 malformed cases were tolerated or raised the wrong exception. | All cases and prior tests passed; `05-malformed-red.log`, `05-malformed-green.log`. |
| 6: `*ReviewDocumentParserTest.ignoresExamplesInsideOrdinaryFencedCodeAndReadsSeparateReviewBlocks` | A quoted example inflated the count to 3 instead of 2. | Whole suite passed; `06-fenced-example-red.log`, `06-fenced-example-green.log`. |
| 7: `*ReviewCoverageTest.rejectsAMissingTypeReview` | No violation was returned for missing evidence. | Whole suite passed; `07-missing-red.log`, `07-missing-green.log`. |
| 8: `*ReviewCoverageTest.acceptsACompleteCurrentReview` | A complete current review was reported missing. | Whole suite passed; `08-current-red.log`, `08-current-green.log`. |
| 9: `*ReviewCoverageTest.identifiesStaleEvidenceByTheExpectedHash` | The old hash was rejected only as missing; the required stale classification and expected hash were absent. | Whole suite passed; `09-stale-red.log`, `09-stale-green.log`. |
| 10: `*ReviewCoverageTest.rejectsFailingPrinciplesEvenBesidePassingCurrentEvidence` and `*ReviewCoverageTest.rejectsRemainingFindingsEvenWhenEveryVerdictPasses` | All five failing principles and remaining findings were ignored beside passing evidence: 6 failures. | All cases and prior tests passed; `10-unresolved-red.log`, `10-unresolved-green.log`. |
| 12: `*ReviewCheckTest.writesADeterministicInventoryForAReviewedProject` | The expected output file was absent after the unimplemented command. | Literal output matched; `12-command-red.log`, `12-command-green.log`. |
| 13: `*ReviewCheckTest.invalidInputRemovesAnEarlierSuccessArtifact` | All 3 source/report/malformed cases left the previous success file behind. | Artifact cleanup and earlier tests passed; `13-failed-output-red.log`, `13-failed-output-green.log`. |
| 14: `*ReviewCheckTest.discoversUnreviewedTypesInAPackageNamedBuild` | An unreviewed type in a package named build was silently skipped. | Missing review was reported; `14-source-scope-red.log`, `14-source-scope-green.log`. |
| 15: `*ReviewCheckTest.onlyInventoriesUnreviewedSourcesWhenExplicitlyRequested` | Inventory mode was not interpreted; the earlier two-path command failed instead of producing the requested inventory. | Explicit inventory mode passed; `15-inventory-command-red.log`, `15-inventory-command-green.log`. |
| 16: `*ReviewCheckTest.refusesVacuousCoverageWhenNoJavaTypeExists` | A package-info-only project incorrectly produced successful empty coverage. | Empty coverage was rejected; `16-vacuity-red.log`, `16-vacuity-green.log`. |
| 17: `*ReviewCheckTest.explicitFileModeReadsExactlyTheFilesDeclaredByGradle` | The explicit-file mode was rejected instead of validating exactly the supplied inputs. | Selected-file behavior passed; `17-declared-files-red.log`, `17-declared-files-green.log`. |
| 18: `*JavaSourceInventoryTest.usesTheSameImmutableSnapshotForSyntaxAndHashAfterADiskEdit` | A captured Value snapshot was ignored after a disk edit: the parser returned Renamed and its later hash. | Compiler trees and hash came from the captured bytes; `18-snapshot-red.log`, `18-snapshot-green.log`. |
| 19: `*JavaSourceInventoryTest.syntaxFailuresIdentifyTheSourceAndLine` | Syntax failure omitted the source and line. | Diagnostic context passed; `19-diagnostic-red.log`, `19-diagnostic-green.log`. |
| 20: `*ReviewDocumentParserTest.rejectsAReviewTagSeparatedFromItsFenceByWhitespace` | A malformed spaced review tag was silently treated as ordinary code. | It was rejected with prior tests passing; `20-fence-spacing-red.log`, `20-fence-spacing-green.log`. |

Two fixture expectations were corrected openly. The initial manually entered
SHA-256 was replaced with the independently calculated SHA-256 of the exact
literal UTF-8 bytes, then the corrected assertion was rerun against the empty
implementation and failed. The enum-constant body location was changed from the
opening brace to the JDK AST start at the constant name (column 19), consistent
with the chosen identity contract. The corrected full nested assertion was rerun
with top-level-only traversal and failed before restoring full traversal.
`02-nested-green.log` is the earlier failed attempt with the wrong expected
location; the successful combined log is named explicitly above.

The identity/history/cross-root/comment tests in
`11-identity-history-characterization.log` passed against existing behavior;
no new failure is claimed for those scenarios. The snapshot adapter was first
introduced as a behavior-preserving preparation with the existing suite green
(`18-snapshot-refactor-baseline.log`), then the disk-edit regression exposed the
remaining reread. Argument rejection, malformed UTF-8, duplicate snapshots and
empty selected-input cases characterized already implemented behavior in
`21-argument-encoding-characterization.log` and all passed.

Final local commands were executed in this order:

```sh
./gradlew spotlessApply -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=review-tool' --console=plain
./gradlew reviewTest solidReviewInventory spotlessCheck -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=review-tool' --console=plain
./gradlew solidReview -Dorg.gradle.jvmargs='-Xmx768m -Dsmpp.worker=review-tool' --console=plain
```

The first formatted the final source; the second freshly executed all **60 tool
test cases** (inventory 10, command 13, coverage 13, parser 24) with zero failures,
errors or skips, and generated the final 13-type inventory. The third executed
and accepted these same manually reviewed type blocks. Logs are
`22-final-format.log`, `23-final-source-verification.log` and
`24-self-review-bootstrap.log`. The isolated worktree does not contain Step 3
library sources; its successful tool tests are not library behavior evidence.

### Integrated verification and cache probes

Root-checkout logs are retained under `/tmp/lightweight-smpp-step4-evidence`.
Every probe below used `./gradlew solidReview --console=plain` unless its command
is given separately. Temporary edits were restored before subsequent work.

| Probe | Observed result |
| --- | --- |
| `01-integration.log`: `./gradlew reviewTest solidReviewInventory --console=plain` | The initial functional version executed 51 passing tool cases and inventoried 20 types: 7 Step 3 types plus 13 tool/test types. |
| `02-bootstrap-red.log` | Accepted the current Step 3 records and reported exactly 13 missing tool reviews, including TypeScanner. |
| `03-bootstrap-green.log`: `./gradlew reviewTest solidReview --console=plain` after final source/evidence integration | Accepted all 20 current type records. The final 60 tool cases were restored FROM-CACHE from the isolated fresh run; validation itself executed. |
| `04-baseline.log`, `05-warm.log` | Matching inputs retained UP-TO-DATE validation and configuration-cache reuse. |
| `06-source-edit-red.log` | Appending a comment to PduHeader invalidated its recorded hash and failed with the exact expected new SHA-256. Configuration cache was reused. |
| `07-source-restored.log` | Restoring the exact source allowed validation output to be restored FROM-CACHE. |
| `08-report-edit-red.log` | Changing a current finding to `integration probe unresolved` invalidated the result and failed for PduHeader. Configuration cache was reused. |
| `09-report-restored.log` | Restoring the report restored matching validation output FROM-CACHE. |
| `10-new-source-red.log` | A newly created, untracked `src/coverageProbe/java/build/ReviewProbe.java` was selected during configuration-cache reuse and failed as missing evidence. |
| `11-new-source-removed.log` | Removing the temporary source executed a successful check again. |
| `12-output-restored.log` | Deleting only coverage.tsv restored it FROM-CACHE; bytes matched the saved successful output. |
| `15-policy-only-edited.log` | Appending only a paragraph to REVIEW_FORMAT.md triggered validation execution with configuration-cache reuse. |
| `16-policy-only-restored.log` | Restoring that policy alone restored matching output FROM-CACHE. |
| `17-build.log`: `./gradlew build --console=plain` | Integrated build passed. Both test tasks and archives reused current outputs; formatter checks executed. Totals were 63 library cases plus 60 tool cases, with zero failures/errors/skips and 20 current covered types. |

The failed source, report and new-file checks removed the old coverage output.
The early policy probes numbered 13/14 coincided with documentation updates;
only the isolated 15/16 pair is used for the policy-invalidation claim. The
runtimeClasspath dependency report remained `No dependencies`, and JAR inspection
found no review-tool classes. Cached tests and outputs above are explicitly
identified as reused evidence, not newly executed tests or measurements.

## Temporary Java fixture inventory and full reviews

These are deliberately small Java inputs generated by tests or cache probes.
They are reviewed as project-authored fixture types, even though the tests only
parse them and they are absent from library/runtime artifacts. This appendix
uses plain Markdown, not invented current repository evidence blocks. A path
relative to an individual `@TempDir` identifies that test input; repeated cases
create fresh copies with the same bytes. Reference copies of the listed fixture
bytes are retained for this session under `/tmp/lws-review-fixtures`.

| ID | Input identity and provenance | Exact source SHA-256 |
| --- | --- | --- |
| F1 | `example.Value`, `Value.java` in named-inventory, snapshot and duplicate-snapshot tests; `src/main/java/example/Value.java` in every command fixture; integer record | `461785f603bb69c8efde1c0586a85ff9ef2e0c7d3d08616bcd18530f7286b1e1` |
| F2 | `example.Value` at the command fixture path after the source-edit case; long record | `9b45c7ed3bf45a9e617688c3638c15055aa05fb544cdadc59c5682676fd296f1` |
| F3 | `example.Value` separately in `src/main/java/Value.java` and `src/test/java/Value.java` in the distinct-source-root test; empty class | `3fe45dca59830b589ab630b4290f149625a744112a26166a4b4b9d990f8aa56a` |
| F4 | `Outer.java` in the nested/local/anonymous inventory test; all 11 identities listed below | `cf7f6ab33a31514e23eeb13d1bb1d6f3b12cf7e37bb469f32c39c526e448d926` |
| F5 | `Duplicate.java`, two `Duplicate` declarations at 1:1 and 1:20; intentionally colliding input | `9e5a63d44a48a63049423ba801a3f56852856fd1bbcfa6b6695e1994bdee4853` |
| F6 | `Real.java`, class `Real`, with Java-looking comment and String contents | `3a25aa9858c94b2c465f55f69d4634afa605a83b61a84d000211ba4fb328cec5` |
| F7 | `class Broken {` in `Real.java` or `Broken.java`; intentionally incomplete source | `028432417f09c802744f05a6b1cbae9e84e2e00978725e9cf8451ac048cfce0a` |
| F8 | `example.Renamed` written to `Value.java` after the immutable snapshot is captured | `b8ed96b8ebe032326db1af4013db2160f0e53b1a6971d4585a138e6fc96f1559` |
| F9 | `build.NewType`, `src/custom/java/build/NewType.java`, in standalone/explicit source-selection tests | `37e8de7b88082bbc464224272c881add3ca9c723dc3d6bc24c3d27556d94a658` |
| F10 | `package-info.java`, package declaration only; no Java type exists | `b70911c0bf9e52e3b721c31a6e82f5c7c7eff52d8603ef722271768a5429a92b` |
| F11 | `build.ReviewProbe`, `src/coverageProbe/java/build/ReviewProbe.java`, added then removed during integrated cache verification | `0555544cd1b902ec6f22143eca726c6c58dc5e974fcf0776de29ad20a003380f` |
| F12 | `Invalid.java`, bytes `c3 28`; invalid UTF-8, no valid Java type | `eddf68639913a3cb8331cdfe7f87559e0beccf2c289c0d90ac4d89b3204004f8` |
| F13 | `kg.aidarbek.smpp.protocol.PduHeader`, its real source file with a temporary appended comment for freshness verification | `0c406a9d834254c4a3ef1e27de1586bd96abbf72bce04112d6f049b29915de4e` |

F1 and F2 have the following complete sources, respectively:

```java
package example; record Value(int number) {}
```

```java
package example; record Value(long number) {}
```

Their repeated input locations and the two identical F3 source roots are grouped
only because the entire type implementations and contracts are identical within
each listed version. Each distinct source path still needs its own coverage key
when it is a current project input, as the cross-root tests demonstrate.

| Type and version | S | O | L | I | D |
| --- | --- | --- | --- | --- | --- |
| `example.Value`, F1/F2 records | **pass:** the single numeric value supplies a known hash and mutation witness. | **pass:** no supported variable operation or extension is required of the test value. | **pass:** generated equality/hash code use the sole int or long component, with no array or mutable ownership; accessors return that primitive. | **pass:** only construction, numeric access and value comparison exist; no custom interface is imposed. | **pass:** primitive storage depends on no variable infrastructure. |
| `example.Value`, both F3 class copies | **pass:** an inert declaration isolates source-root identity matching. | **pass:** the root-path variation is test input data, with no behavior extension requirement. | **pass:** both empty classes preserve inherited Object identity contracts and own no resources. | **not applicable:** no custom interface or callable fixture capability is declared. | **pass:** neither copy has a collaborator beyond inherited Object. |
| `example.Outer`, F4 | **pass:** owns the compact source fixture whose members exercise compiler inventory locations. | **pass:** syntax examples extend the fixture without creating a runtime extension contract. | **pass:** no custom supertype or equality override; the package field is not promised immutable and the fixture is parsed without execution. | **pass:** its run method and initializer exist only as compiler input contexts; no application implements irrelevant callbacks. | **pass:** only its own nested values and standard Object/Runnable syntax occur. |
| `example.Outer.Member`, F4 | **pass:** witnesses member-interface discovery. | **pass:** no runtime extension behavior is supported or required for the marker. | **not applicable:** it adds no behavioral interface methods and has no project implementation to substitute. | **not applicable:** the interface is an empty syntax marker and imposes no method requirements. | **pass:** it depends on no infrastructure or sibling type. |
| `example.Outer.Mark`, F4 | **pass:** witnesses annotation-interface discovery. | **pass:** the empty annotation needs no variable member schema in this fixture. | **pass:** no custom members alter the language's inherited Annotation contract; no project proxy implementation exists. | **pass:** no unused annotation attributes are required from consumers. | **pass:** only the Java annotation language contract applies. |
| `example.Outer.Choice`, F4 | **pass:** provides the enum declaration enclosing the anonymous constant body. | **pass:** the one-constant syntax fixture has no production enum-extension requirement. | **pass:** Enum name, ordinal and identity behavior are unmodified; no mutable payload is stored. | **pass:** it does not force any application handler interface. | **pass:** it uses only the Java enum contract and its own constant. |
| `example.Outer.Choice#field:FIRST/<anonymous>@5:19`, F4 | **pass:** witnesses an enum constant's class body and method. | **pass:** the no-op action is only syntax test data; no supported polymorphic extension is promised. | **pass:** the constant body does not override Enum contracts; the extra void action has no preconditions, effects or resource promises. | **pass:** the method imposes no interface obligation on other enum constants or applications. | **pass:** it has no external dependencies. |
| `example.Outer.Value`, F4 | **pass:** witnesses nested-record discovery with one integer. | **pass:** this value has no variable behavior requiring extension. | **pass:** the nested record uses primitive value equality and has no mutable component or resource. | **pass:** construction and the number accessor are the complete value surface. | **pass:** only its primitive component and language record contract apply. |
| `example.Outer#run/Local@8:9`, F4 | **pass:** witnesses a named local type that encloses another declaration. | **pass:** only local syntax discovery varies, with no runtime extension requirement. | **pass:** it adds no custom supertype, state, equality or lifecycle contract to Object. | **not applicable:** no interface or fixture operation is declared. | **pass:** its only source relation is ownership of Child. |
| `example.Outer#run/Local@8:9.Child`, F4 | **pass:** verifies that traversal continues inside a local type. | **pass:** it is an inert syntax marker with no behavior extension point. | **pass:** the empty member type preserves Object contracts and owns no resources. | **not applicable:** it declares no methods or interface. | **pass:** only its enclosing declaration and inherited Object are involved. |
| `example.Outer#run/<anonymous>@9:24`, F4 | **pass:** supplies the anonymous Runnable body for member/location discovery. | **pass:** it uses the existing Runnable shape; the no-op fixture has no further extension requirement. | **pass:** public void run returns normally and satisfies Runnable without extra preconditions or resource ownership; no asynchronous execution is claimed. | **pass:** it implements exactly Runnable's single required method. | **pass:** Runnable is the standard boundary, with no executor or external system dependency. |
| `example.Outer#field:field/<anonymous>@11:33`, F4 | **pass:** witnesses anonymous-class discovery in a field initializer. | **pass:** the empty Object subtype needs no variable behavior. | **pass:** it adds no overrides to Object and exposes no mutable state or resource operation. | **not applicable:** no custom interface or method is declared. | **pass:** it depends only on Object and its fixture owner. |
| `example.Outer#<initializer>/InitializerLocal@12:7`, F4 | **pass:** witnesses a local declaration in an instance initializer. | **pass:** it is a fixed grammar case with no supported extension requirement. | **pass:** the empty local class retains Object contracts and owns no resources. | **not applicable:** it defines no behavior interface or callable fixture method. | **pass:** no collaborator beyond its enclosing context and Object exists. |
| First `Duplicate` at 1:1, F5 | **pass:** one half of the deliberate identity-collision input. | **pass:** an inert duplicate declaration is fixed negative test data. | **pass for its declaration:** it has no custom inherited behavior or overrides; the pair's illegal identity is rejected instead of used as an executable class. | **not applicable:** no interface or method exists. | **pass:** it adds no source dependency. |
| Second `Duplicate` at 1:20, F5 | **pass:** creates the second declaration needed to test collision rejection. | **pass:** no behavioral extension is promised by this negative sample. | **pass for its declaration:** it preserves the empty Object shape; duplicate source identity is intentionally rejected before coverage can be claimed. | **not applicable:** no interface or method exists. | **pass:** it adds no source dependency. |
| `Real`, F6 | **pass:** distinguishes a real declaration from class-looking comment/String text. | **pass:** the text is fixture data, with no behavior extension requirement. | **pass:** Object equality is unmodified; the package String field has no immutability or thread-safety promise, and the fixture is never executed. | **not applicable:** no custom interface or callable fixture operation is declared. | **pass:** only standard String and Object types appear. |
| Intended `Broken` declaration, F7 | **pass:** the incomplete declaration is the entire malformed-Java test input. | **pass:** this is a fixed rejection case, not an extension API. | **not applicable:** no valid compiled type or substitutable implementation exists; both syntax tests require rejection with useful diagnostics. | **not applicable:** malformed input offers no usable interface to a caller. | **pass:** the negative input names no collaborator or external dependency. |
| `example.Renamed`, F8 | **pass:** witnesses a disk edit after a snapshot is captured. | **pass:** renaming is source data for the snapshot test, with no required behavior extension. | **pass:** the empty class preserves Object contracts; the captured earlier Value snapshot remains unchanged and is the compiler input under test. | **not applicable:** no interface or operation is declared. | **pass:** it depends only on Object. |
| `build.NewType`, F9 | **pass:** exposes incorrect exclusion of a Java package named build and distinguishes declared-file selection. | **pass:** presence or absence in selected inputs varies as test data; the empty class needs no extension API. | **pass:** no custom supertype, state, equality or lifecycle contract alters Object. | **not applicable:** no custom interface or method is declared. | **pass:** the declaration is independent of application infrastructure. |
| `build.ReviewProbe`, F11 | **pass:** the final empty class witnesses newly added source detection during configuration-cache reuse. | **pass:** it has no supported behavior variation; finality is appropriate for this inert probe. | **pass:** inherited Object identity behavior is unchanged and no state or resource ownership is added. | **not applicable:** no custom interface or callable capability exists. | **pass:** the isolated probe has no runtime/library collaborator beyond Object. |

Remaining findings for all reviewed fixture declarations: none. F5's duplicate
identities and F7's incomplete syntax are intentional rejected test inputs, not
unresolved implementation defects or shipping types. F10 has no type and F12
cannot decode as Java; no type is silently assigned to either. `Comment` and
`Literal` inside F6 are source text, not declarations. Fixture Java Strings in
the test classes are not additional runtime Java types.

### Comment-only PduHeader variant, F13

The complete current PduHeader and its PduHeaderCodec consumer were reinspected;
F13 appends only a probe comment and introduces no declaration or behavior change.
The source identity remains
`src/main/java/kg/aidarbek/smpp/protocol/PduHeader.java` /
`kg.aidarbek.smpp.protocol.PduHeader`. The final restored type review and original
behavior tests are in [the Step 3 report](0002-pdu-framing.md).

- **S — pass:** the record still owns four unsigned raw header fields and the
  structural minimum length, without interpreting command/session legality.
- **O — pass:** unknown command/status/sequence values remain representable;
  later command/profile policy can interpret them without changing this raw value.
- **L — pass:** all four components are immutable longs; generated equality/hash
  code and constructor range exceptions are unchanged. There are no mutable
  buffers, custom subtype obligations or resources.
- **I — pass:** four accessors and LENGTH are the codec/framer needs, with no
  unrelated endpoint operation or callback requirement.
- **D — pass:** the protocol value imports no transport, codec implementation,
  framework or tooling dependency. PduHeaderCodec depends on the value.
- Remaining findings: none. The comment's changed hash correctly failed freshness
  despite unchanged behavior; restoration returned the existing reviewed version.

## Completion

Every Step 4 Java type, its private nested scanner, its test classes, the Groovy
argument provider and all generated Java fixture declarations are reviewed above.
The validator accepted the same final source hashes after manual review. Known
findings were corrected: complete AST traversal, source/hash snapshot consistency,
strict evidence parsing, current conflict detection, source scope and declared
input parity, failure-output cleanup and nonvacuous coverage. The check establishes
coverage and freshness of recorded evidence; the substantive SOLID assessment
remains the responsibility of the reviewer.
