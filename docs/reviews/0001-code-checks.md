# Review: Set up code checks

## Scope

- Starting revision: `839fa61` (`Define library contracts`).
- Change: configure deterministic Java formatting and make ArchUnit core available
  to ordinary JUnit Jupiter tests.
- Versions: Gradle 9.6.0, Java 21, JUnit 6.0.0, Spotless 8.10.2,
  Palantir Java Format 2.96.0, and ArchUnit 1.4.2.
- Repository Java files created or changed: none. No SMPP classes or permanent
  architecture tests are introduced in this step.
- Temporary Java type: `tooling.ToolingProbeTest`, in an isolated copy of the
  project's build configuration under `/tmp/lightweight-smpp-step2-whdycztq`.
- Final probe source SHA-256:
  `87b2f3e534772822b896e9f5e01ea10a60c99b1bdd352b0ea85e2c7bc61bfdeb`.
- Build configuration SHA-256:
  `71f517d4dc95a3f712ae5b5cc6901eca27b42b6bf2da924c0f6baab0876c9616`.

The temporary copy used the same `build.gradle`, `settings.gradle`,
`gradle.properties`, and wrapper configuration as the repository. It supplied a
real source file so formatting and test execution could be verified before the
first library implementation. This fixture is retained below as documentation;
it is not part of the published library or permanent project test suite.

## Tool configuration

Spotless runs Palantir Java Format on `src/*/java/**/*.java`, covering the standard
main/test source directories and similarly named additional source sets. UTF-8
and LF are explicit. The formatter's standard style uses four-space indentation
and a 120-column wrapping preference. Generated build output is outside the
configured target.

The plugin connects `spotlessCheck` to `check`; `build` includes it through the
normal verification lifecycle. `spotlessCheck` reports differences while leaving
source files untouched. `spotlessApply` is the explicit source-rewriting command.
The selected tool versions are pinned in the build.[^1][^2]

ArchUnit core is a test-only dependency. Its rules execute inside ordinary
Jupiter `@Test` methods; no additional ArchUnit test engine or production runtime
dependency is configured. Project package rules start in Step 3, when there are
actual production classes to select.[^3][^4]

## Observed verification sequence

Commands below ran from the temporary copy unless labelled “repository.”

| Phase | Command/action | Observed outcome |
| --- | --- | --- |
| Baseline | Repository: `./gradlew check --console=plain` before the change | Passed; `test NO-SOURCE`, with no behavior tests executed. |
| Configuration | Repository: `./gradlew check --console=plain` after configuration | Passed; formatting tasks were included; sources/tests were still absent. |
| Formatting red | `./gradlew spotlessCheck --console=plain` with malformed fixture whitespace | Exit 1 at `spotlessJavaCheck`, with a diff showing missing spacing, tabs, and indentation. |
| Non-mutating check | Compare fixture hash before and after the failed check | Both were `613939a5551331a5ee26cfb58946b03da4c7d6ef3099218d3f2600b2d7ce9172`. |
| Formatting apply | `./gradlew spotlessApply --console=plain` | Passed; source used four-space indentation, LF, and a final newline. |
| Architecture red | `./gradlew check --console=plain` with the formatted public test class | Formatting passed; Jupiter ran one test, which failed because ArchUnit detected the class's PUBLIC modifier. |
| Minimal correction | Remove `public` from the fixture class; keep the rule unchanged | The fixture now satisfies the selected visibility contract. |
| Green | `./gradlew check --console=plain` | One test executed, zero failures/errors; compile, formatter, and test tasks executed. Configuration cache reused. |
| Output restoration | `./gradlew clean check --console=plain` | `compileTestJava`, `spotlessJava`, and `test` reported `FROM-CACHE`; this was reused evidence, not another fresh test execution. |
| Final build | Repository: `./gradlew build --console=plain`, repeated | Passed; second run reused the configuration cache and finished in 432 ms. Repository tests remained `NO-SOURCE`. |
| Cache diagnostic | Repository: `./gradlew build --console=plain --info` | Confirmed active filesystem watching, local build cache, configuration-cache reuse, and formatting tasks in the build graph. |
| Dependencies | Repository: `./gradlew dependencies --configuration testRuntimeClasspath --console=plain` | Resolved Jupiter/Platform 6.0.0 and ArchUnit core 1.4.2; no separate ArchUnit engine. |
| Runtime footprint | Repository: `./gradlew dependencies --configuration runtimeClasspath --console=plain` | `No dependencies`. |

The first post-apply check ran the formatter again and accepted its output,
providing idempotence evidence for this fixture. The observed architecture failure
was an assertion about an imported real class, not a compilation or dependency
failure. Removing the offending modifier made the same assertion pass.

The reported timings describe these local runs only. The isolated check used
`clean` specifically to verify restoration of cached outputs; ordinary development
keeps incremental outputs. Gradle retained its own per-task cacheability rules.

When the shared daemon switched between the temporary copy and repository,
Gradle emitted a duplicate-watch-path warning and invalidated its filesystem
snapshot. Subsequent builds at each location completed without that warning.
The final repository diagnostic explicitly reported filesystem watching active.

## Final temporary fixture

Path within the temporary copy: `src/test/java/tooling/ToolingProbeTest.java`.
The public modifier was present for the negative architecture run. The visibility
rule below is a tooling probe; it is not a rule that all library types must be
package-private.

```java
package tooling;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

final class ToolingProbeTest {
    @Test
    void enforcesPackageVisibility() {
        classes().should().bePackagePrivate().check(new ClassFileImporter().importClasses(ToolingProbeTest.class));
    }
}
```

## Type review: tooling.ToolingProbeTest

- Responsibility: verify that the chosen Jupiter/ArchUnit combination executes a
  real visibility rule against Java 21 bytecode in the configured build.
- Consumers and collaborators: Jupiter test discovery, ArchUnit's rule API, and
  `ClassFileImporter`. The class is final and has one test method, no nested or
  anonymous types, no mutable fields, and no resource ownership.
- **S — pass:** the fixture has one integration-check purpose. Formatting changes
  affect its representation without adding another application responsibility.
- **O — pass:** there is no required production extension point. The focused test
  uses the rule library's existing API; no additional hierarchy or plugin API is
  needed for this fixture.
- **L — pass for applicable contracts:** there is no custom supertype or
  implemented interface. Inherited `Object` behavior is unchanged. The final
  package-private type and method remain valid for Jupiter discovery, as the
  successful executed test demonstrates.
- **I — not applicable to a custom interface:** the fixture defines or implements
  no interface. Its sole annotated method is the only test-runner entry point;
  no consumer is forced to implement unrelated behavior.
- **D — pass:** test-only code directly uses the infrastructure it verifies.
  No library policy, public API, or runtime artifact depends on this fixture,
  Jupiter, or ArchUnit. An extra abstraction around the tested rule API would
  not create a useful boundary here.
- Findings corrected: formatting and the deliberately violated fixture visibility
  rule. Public visibility by itself is not a universal SOLID violation.
- Remaining SOLID findings: none identified in the complete final fixture.

## Inventory and completion

The repository still contains zero Java source files. The only project-authored
Java type created for this step was the temporary fixture reviewed above; its
final formatted source is preserved here with its hash. Build configuration
changes add no custom Java or Groovy helper classes.

Step 2 is complete. The evidence establishes formatter behavior, tool integration,
and cache operation for the stated fixture. It does not establish SMPP behavior,
project architecture compliance, or the planned review-coverage validator.
Step 3 adds the first protocol types, behavior tests, and nonempty project
architecture rules. Step 4 adds automatic review-evidence coverage checks.

## Sources

[^1]: DiffPlug. [Spotless Gradle plugin](https://plugins.gradle.org/plugin/com.diffplug.spotless/8.10.2) and [configuration guide](https://github.com/diffplug/spotless/tree/main/plugin-gradle), accessed 9 September 2026.
[^2]: Palantir. [Palantir Java Format 2.96.0](https://github.com/palantir/palantir-java-format/tree/2.96.0), style and Java 21 support, accessed 9 September 2026.
[^3]: ArchUnit contributors. [ArchUnit user guide](https://www.archunit.org/userguide/html/000_Index.html), plain Java rule API and empty-selection behavior, accessed 9 September 2026.
[^4]: JUnit team. [JUnit 6.0.0 overview](https://docs.junit.org/6.0.0/overview.html), Java runtime requirements and Jupiter model, accessed 9 September 2026.
