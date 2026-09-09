# SOLID review evidence format

`./gradlew solidReview --console=plain` checks coverage and freshness for every
current project-owned Java type. `check` includes it and `reviewTest`; the
validator and its tests use separate `review` and `reviewTest` source sets and
are absent from the library JAR, sources JAR, Javadoc and runtime classpath.
The validator uses only the JDK compiler API and Java standard library.

Run `./gradlew solidReviewInventory --console=plain` to write
`build/reports/solid-review/types.tsv`. It lists source paths, type identities,
and SHA-256 values without claiming review completion. Successful verification
writes `build/reports/solid-review/coverage.tsv`. A failed check removes its
previous success output. Formatting remains an explicit prior step:
`./gradlew spotlessApply --console=plain`.

## Required blocks

Keep change context, TDD evidence, corrections and contract analysis in the
change-specific Markdown report under `docs/reviews/`. Add one block per type:

````markdown
```solid-review
source: src/main/java/example/Value.java
type: example.Value
sha256: 461785f603bb69c8efde1c0586a85ff9ef2e0c7d3d08616bcd18530f7286b1e1
responsibility: Stores one integer value for callers of the example API.
consumers: Example callers read the integer and use record equality.
S: pass | Only the integer value invariant drives changes to this record.
O: pass | This value has no supported extension or variable operation.
L: pass | Generated equality and hash code preserve immutable integer value semantics.
I: not applicable | No custom interface; callers need only its integer accessor.
D: pass | Primitive value storage has no variable infrastructure dependency.
findings: none
```
````

Every shown key is required exactly once. Values occupy one physical line;
ordinary Markdown outside the blocks can carry longer explanations. Principle
values use `pass`, `fail`, or `not applicable`, then ` | ` and nonempty specific
reasoning. `not applicable` must identify the absent obligation. The human review
must satisfy [the SOLID policy](SOLID.md); nonempty prose is not a machine proof
that the explanation is correct or sufficient.

The parser rejects unknown/duplicate/missing fields, empty values, invalid
verdict syntax, malformed hashes, unclosed/nested review fences and noncanonical
source paths. Hashes are 64 lowercase hexadecimal digits. Source paths are
repository-relative, slash-separated `.java` paths without empty, `.` or `..`
components, a leading slash, backslashes or drive prefixes. Type identities are
case-sensitive and contain no whitespace. Exactly three backticks followed by
`solid-review` open a block; exactly three backticks close it. Surrounding line
whitespace is ignored. Examples inside ordinary fenced code are not evidence.

## Identities and freshness

The lookup key is **source path + type identity + whole-file SHA-256**. Identical
qualified names in separate source roots require separate evidence. A renamed
file or type requires a matching current entry. Every named type in one file
shares that file's hash, so any edit invalidates its old type reviews.

The inventory uses JDK 21 `JavacTask.parse()` and `TreePathScanner`, with annotation
processing and preview features disabled. Each file is decoded strictly as UTF-8
from one immutable byte snapshot used for both parsing and hashing. Syntax errors
fail with file and source location. It does not resolve dependencies or execute
source code. Classes, records, interfaces, annotation interfaces, enums, named
members, local classes, anonymous classes and enum constant class bodies are
included. Duplicate type identities within one source file fail.

- Top-level type: `example.Outer`.
- Named member: `example.Outer.Inner`.
- Local type: `example.Outer#run/Local@8:9`.
- Anonymous class: `example.Outer#run/<anonymous>@9:24`.
- Field initializer: `example.Outer#field:field/<anonymous>@11:33`.
- Initializer block: `example.Outer#<initializer>/Local@12:7` (static blocks use
  `<static-initializer>`). Constructors use `<init>` as the member name.

Locations are the compiler AST start position, with one-based line/column
numbers, tied to the file hash. An enum constant class body starts at its constant
name in the JDK AST. Method overloads and repeated local/anonymous names remain
distinct through location. Named members of a local/anonymous type append `.Name`
to their enclosing type's complete identity.

A current type needs at least one complete matching entry. Historical blocks
with old paths, types or hashes remain historical and cannot supply coverage.
Every entry for the current key must have no `fail` verdict and must say
`findings: none`; another passing entry cannot hide a current unresolved finding.
Malformed tagged evidence in any selected report fails, including old reports.
Historical reports without tagged blocks are allowed but provide no automatic
coverage. A project with no Java types cannot claim successful coverage.

## Build inputs and caching

Gradle selects every repository `.java` file, including tooling, tests, untracked
files, arbitrary source sets and `buildSrc`. It excludes the root `build/` and explicit `simulator/build/` output
directories, `.git`/`.gradle` directories and Gradle's standard metadata/editor
exclusions. A source package named `build` remains included. Reports are all
`docs/reviews/**/*.md` files. When introducing subprojects or nonstandard generated
output directories, explicitly configure those output roots; do not exclude all
directories named `build`, which could hide legitimate Java packages.

`SolidReviewArguments` supplies those exact lazily selected file collections
to the child process, with annotated relative-path inputs. Verification consumes
no extra discovered source/report files. `docs/SOLID.md`, this format policy,
the tool classpath, launcher and command mode also affect the task inputs.
Successful output is deterministic and has no timestamps, absolute paths, Git
state or environment-dependent measurement. The two JavaExec tasks explicitly
enable caching on that basis; matching input/output states can be up to date or
restored from cache. No JavaExec task is globally made cacheable.

The standalone command also supports `[--inventory] ROOT OUTPUT` for direct
filesystem use. Gradle exclusively uses the explicit-file modes `--check-files`
and `--inventory-files`, followed by `ROOT OUTPUT` and repeated `--source PATH`
or `--report PATH` arguments. Output files belong to the caller and are replaced.
Use the Gradle tasks for the declared project scope and caching contract.

The JDK APIs are documented in [JavacTask](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.compiler/com/sun/source/util/JavacTask.html)
and [SourcePositions](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.compiler/com/sun/source/util/SourcePositions.html).
Gradle documents [JavaExec](https://docs.gradle.org/9.6.0/dsl/org.gradle.api.tasks.JavaExec.html)
and [task outputs and caching](https://docs.gradle.org/9.6.0/javadoc/org/gradle/api/tasks/TaskOutputs.html).
