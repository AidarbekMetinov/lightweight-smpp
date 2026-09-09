# Project guidance

## Purpose

Build a lightweight, simple Java SMPP library in this project. Use Cloudhopper
SMPP and Cloudhopper Commons as reference projects.

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

The SMPP implementation is at the scope and API discussion stage. SMPP 3.4, a
single module, no initial runtime dependencies, and client support first have been
suggested. Protocol and client/server scope remain open.

The user wants strong Java coding practices and development caching. The build
now uses a Java 21 toolchain as the development baseline, matching the installed
JDK. Revisit the target version if release compatibility requirements change.

The existing Gradle project uses the group `kg.aidarbek`, the project name
`lightweight-smpp`, and JUnit Jupiter for tests. Java 21 is available locally.

Read [the project overview](docs/README.md) and [the roadmap](docs/ROADMAP.md) for
planning context. Update the documents as decisions are made and steps are
completed.

## Implementation and verification

- Favor a small, understandable API and dependencies justified by actual needs.
- Follow the Java conventions and build workflow in
  [the development guide](docs/DEVELOPMENT.md).
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

## References

- [Cloudhopper SMPP](https://github.com/fizzed/cloudhopper-smpp)
- [Cloudhopper Commons](https://github.com/twitter/cloudhopper-commons)
- [SMPP 3.4 specification, issue 1.2](https://smpp.org/SMPP_v3_4_Issue1_2.pdf)
