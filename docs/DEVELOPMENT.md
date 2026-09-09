# Development guide

## Build baseline

- Use the checked-in Gradle wrapper: `./gradlew` on Linux/macOS or `gradlew.bat` on
  Windows. The current wrapper version is 9.6.0.
- Install JDK 21 and run Gradle with it (`JAVA_HOME` or the IDE's Gradle JVM).
  Palantir uses that JVM to format Java 21 syntax. The build also selects a Java 21
  toolchain for compilation. Preview features are disabled.
- Keep runtime dependencies small and purposeful. Declare internal dependencies
  as `implementation`; use `api` when a dependency's types are part of the public
  API. See [Gradle's Java Library plugin](https://docs.gradle.org/current/userguide/java_library_plugin.html).
- Java compilation uses UTF-8, `-Xlint:all`, and `-Werror` for production and test
  code. Fix warnings and keep unavoidable suppressions narrow and documented.
  See the [Java 21 compiler options](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javac.html).
- JUnit Jupiter 6.0.0 is configured for testing. ArchUnit core 1.4.2 is available
  to ordinary Jupiter tests, without an additional ArchUnit test engine. Both are
  test dependencies; the library runtime classpath remains empty.
- The build produces source and Javadoc archives with deterministic archive
  ordering and timestamps.
- `.editorconfig` defines UTF-8, four-space indentation, final newlines, and a
  120-column Java line-length preference. Spotless 8.10.2 with Palantir Java
  Format 2.96.0 now verifies Java formatting using that style.
- `.gitattributes` keeps text line endings consistent across platforms and uses
  CRLF for Windows batch scripts.

## Java coding conventions

Apply the mandatory [SOLID review policy](SOLID.md) to every created or updated
type, including nested types, simulator code, and tests. Use the
[TDD workflow](TDD.md) for behavior changes.

- Keep classes and methods focused. Prefer straightforward code and composition;
  introduce an interface or abstraction when it has a concrete purpose.
- Keep the public API small. Use the narrowest useful visibility and keep
  implementation details out of public method signatures.
- Prefer immutable value objects. Records are useful for values, but arrays and
  mutable collections still require defensive copies or an explicit ownership
  contract.
- Use descriptive names, generics, and named constants for protocol values. Avoid
  raw types, unchecked casts, and unexplained numbers.
- State nullability and validate inputs at API boundaries. Use explicit character
  encodings and byte order. Check wire lengths before allocating or reading
  buffers, and preserve unsigned protocol values correctly.
- Separate protocol encoding, session state, transport, and application callbacks
  so they can be reasoned about and tested independently.
- Document public APIs with Javadoc, including units, valid ranges, failure modes,
  ownership, and thread-safety guarantees.
- Model timeouts with explicit units, preferably `Duration`. Bound queues and
  outstanding requests. Keep application callbacks from blocking network progress.
- Give sockets, executors, and other resources clear owners and cleanup paths.
  Use `AutoCloseable` and try-with-resources where appropriate. Preserve thread
  interruption when handling interrupted operations.
- Use exceptions that identify the operation and failure. Never silently swallow
  exceptions. Keep credentials and message contents out of diagnostic output.
- Optimize based on measurements. Add complexity only when a measured problem or
  required use case justifies it.

## Tests and everyday commands

Select one behavior, observe its relevant test failure, implement the smallest
passing change, then refactor and review every affected type. Record actual
commands and outcomes in the change report under `docs/reviews/`. Bug fixes start
with a failing regression test. See [TDD](TDD.md) for the complete workflow.

Use known protocol bytes as fixtures, cover malformed input and boundary values,
and test failures as well as successful requests. Round-trip encoding tests alone
can miss matching encoder/decoder mistakes.

Use a local test peer for networking tests. Make timeouts bounded and coordinate
threads explicitly instead of relying on arbitrary sleeps. Keep unit tests
independent of external SMSCs and credentials.

Run from the project root:

```sh
./gradlew spotlessCheck --console=plain
./gradlew spotlessApply --console=plain
./gradlew test --console=plain
./gradlew check --console=plain
./gradlew build --console=plain
```

`spotlessCheck` reports formatting differences without changing sources;
`spotlessApply` rewrites them into the configured format. `test` runs tests,
`check` runs tests and formatting verification, and `build` also assembles the
library archives. Once tests exist, use `--tests 'fully.qualified.TestClass'` to
focus a TDD run.

Formatting targets `src/*/java/**/*.java`, including main/test Java and similarly
named source sets. UTF-8 and LF are explicit. Palantir's standard style uses
four-space indentation and a 120-column wrapping preference. Generated build
output and Java-looking documentation snippets are outside the target. Configure
additional modules or nonstandard source directories when they are introduced.

Apply formatting before recording final source hashes and completing the SOLID
review. Keep the pinned build formatter as the shared result across editors;
`.editorconfig` alone does not configure every editor's Java formatting engine.
See [Spotless](https://github.com/diffplug/spotless/tree/main/plugin-gradle) and
[Palantir Java Format](https://github.com/palantir/palantir-java-format/tree/2.96.0).

The repository has no Java sources or permanent tests yet. Step 2 verified
compilation, formatting, Jupiter discovery, and an ArchUnit rule using an isolated
fixture. [The review record](reviews/0001-code-checks.md) contains its observed
failures, passing run, and per-type SOLID review. Root `test NO-SOURCE` is not
evidence that SMPP behavior has been tested.

Project architecture rules begin with actual classes in Step 3. The automatic
review-evidence validator remains planned for Step 4. Simulators will have
deterministic behavior tests and separate explicit load-run commands; see
[the simulator design](SIMULATORS.md).

## Caching and build speed

Project settings live in `gradle.properties`.

| Mechanism | Configuration | Benefit |
| --- | --- | --- |
| Local task output cache | `org.gradle.caching=true` | Reuses outputs of cacheable tasks when their inputs match. |
| Configuration cache | `org.gradle.configuration-cache=true` | Reuses the configured task graph for a matching invocation and configuration inputs. |
| Dependency cache | Automatic Gradle behavior | Reuses downloaded dependencies and cached resolution metadata. |
| Incremental Java compilation | Enabled by the Java plugin by default | Recompiles classes affected by changes. |
| Up-to-date checks | Automatic Gradle behavior | Skips tasks whose inputs and outputs have not changed. |
| Gradle daemon | `org.gradle.daemon=true` | Reuses a warmed JVM between builds. |
| File-system watching | `org.gradle.vfs.watch=true` | Retains file-system state between builds on supported file systems. |
| Parallel project execution | `org.gradle.parallel=true` | Allows independent projects to run together if this single-module build grows. |

These settings follow Gradle's documentation for the
[build cache](https://docs.gradle.org/current/userguide/build_cache.html),
[configuration cache](https://docs.gradle.org/current/userguide/configuration_cache_enabling.html),
[dependency cache](https://docs.gradle.org/current/userguide/dependency_caching.html),
[Java plugin](https://docs.gradle.org/current/userguide/java_plugin.html),
[file-system watching](https://docs.gradle.org/current/userguide/file_system_watching.html),
and [performance](https://docs.gradle.org/current/userguide/performance.html).

Configuration-cache problems fail the build. Keep new build logic compatible and
declare task inputs and outputs correctly so changes invalidate cached results.
Retain Gradle's per-task cacheability rules; forcing every task to be cacheable can
produce incorrect results or unnecessary overhead.

Performance and load runs are measurements of the current environment. They must
execute when requested, with fresh reports, while simulator compilation and
deterministic tests retain normal caching. Do not cache a past benchmark result
and present it as a new measurement.

Use ordinary builds for day-to-day work. Run `clean` only when it serves a specific
purpose. Use `--refresh-dependencies` when dependency resolution needs refreshing,
and `--rerun-tasks` when intentionally checking fresh execution.

To check configuration-cache reuse, run `./gradlew build --console=plain` twice;
the second invocation should report `Configuration cache entry reused`. Once Java
sources and tests exist, task output reuse can also be checked by rebuilding after
`clean` and looking for `FROM-CACHE` on cacheable tasks.

Step 2 demonstrated output restoration for `compileTestJava`, `test`, and
`spotlessJava` in its isolated fixture build. The repository's warm build reused
the configuration cache, and an info-level diagnostic confirmed filesystem
watching and the local build cache were active. These are observations of the
recorded runs, not fixed timing guarantees.

The project uses local caches. A shared remote cache needs a backend and can be a
separate step when team development or CI calls for it. Parallel configuration
cache storage is incubating in Gradle 9.6.0 and is left at its default; see the
[version-specific configuration cache guide](https://docs.gradle.org/9.6.0/userguide/configuration_cache_enabling.html).

## Git

Use short, plain commit messages describing the change, for example:

- `Set up project`
- `Add PDU header`
- `Fix bind timeout`

Keep each commit focused on a coherent change and run the relevant checks first.
Generated builds, Gradle caches, and local IDE settings are ignored by Git.
