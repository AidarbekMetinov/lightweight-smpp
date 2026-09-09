# Review: Apache License 2.0

## Scope

Starting revision: `03959d4`. The user requested an Apache open-source license
while Steps 6–8 were being developed independently. This change adds the complete
official Apache License 2.0 as `LICENSE`, project attribution as `NOTICE`, the
license declaration in the overview/agent guidance, and inclusion of both files
in every binary, source, and Javadoc JAR under `META-INF/`.

The license was downloaded from the
[Apache Software Foundation](https://www.apache.org/licenses/LICENSE-2.0.txt)
without text modification. Its 11358 bytes have SHA-256
`cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`.
The project notice names Aidarbek Metinov and the 2026 copyright year.

No Java source or Java type changed. The existing 43 type identities retain
their reviewed source hashes. No artificial Java behavior test is needed for
the license text or documentation.

## Packaging verification and build review

The archive contract is exact inclusion of the repository's `LICENSE` and
`NOTICE` in each of the three generated JARs. The temporary artifact check
`python3 /tmp/lightweight-smpp-license-check.py` first failed against the existing
archives because `META-INF/LICENSE` was absent. After adding the copy rule and
building, the same check passed for all three archives, comparing each embedded
file byte for byte with its repository source.

The initial multi-argument Groovy copy call caused a task-cycle configuration
failure. It was corrected to pass the two source paths as one list to
`from(source, closure)`. This configuration failure is separate from the actual
artifact assertion failure and supplies no behavioral TDD evidence. The final
copy rule uses Gradle's standard
[CopySpec contract](https://docs.gradle.org/current/javadoc/org/gradle/api/file/CopySpec.html).

`./gradlew build --console=plain` then passed in one second, executing the three
archive tasks and review coverage while nine tasks were up to date. It stored
the updated configuration-cache entry (`/tmp/lightweight-smpp-license-build.log`).
The unchanged 95 library/architecture cases and 60 review-tool cases reused
matching results; this packaging change did not execute them anew. Markdown
content/link verification and `git diff --check` passed.

All five SOLID principles were considered for the build change. It adds one
packaging responsibility to existing `Jar` tasks, applies automatically to all
three archives, preserves the tasks' existing copy/output contracts, introduces
no caller-facing interface, and confines Gradle/file dependencies to build logic.
The custom `SolidReviewArguments` class is unchanged. Standard copy inputs remain
declared, and deterministic ordering/timestamps remain configured. There is no
runtime dependency or new Java type requiring a `solid-review` block.
