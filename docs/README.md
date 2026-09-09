# Lightweight SMPP

A personal Java library for the Short Message Peer-to-Peer (SMPP) protocol, focused
on a small implementation and a simple API.

## Agreed direction

- Build our own lightweight SMPP library.
- Develop it step by step, keeping each piece small and understandable.
- Use Cloudhopper SMPP and Cloudhopper Commons as reference projects.
- Keep project planning documents in this directory.
- Use strong Java coding practices and caching to keep development efficient.
- Keep Git commit messages short and simple.

Project working guidance is recorded in [AGENTS.md](../AGENTS.md).

## Initial project state

Inspected on 2026-09-09:

- Existing Gradle project with group `kg.aidarbek` and name `lightweight-smpp`.
- Java 21 is installed locally.
- JUnit Jupiter is configured.
- Source and test directories are empty.
- `./gradlew test --console=plain` completes successfully with no tests to run.

## Development setup

The build now uses Java 21, the `java-library` plugin, strict compiler warnings,
UTF-8 encoding, and reproducible archives. Gradle build and configuration caches
are enabled. Git is initialized on `main`.

See the [development guide](DEVELOPMENT.md) for coding conventions, commands, and
caching details. Java 21 is the current build target; release compatibility can
be revisited when the library's scope is settled.

## Proposed starting point

These choices are open for discussion:

- SMPP 3.4 as the initial protocol version.
- One library module.
- No runtime dependencies initially, adding them when a concrete need justifies it.
- Client support first; whether the first version also needs a server remains open.

The next step is to settle the initial scope and sketch a small usage example.
See the [roadmap](ROADMAP.md) for the proposed sequence.

## References

- [Cloudhopper SMPP](https://github.com/fizzed/cloudhopper-smpp): protocol and session implementation reference.
- [Cloudhopper Commons](https://github.com/twitter/cloudhopper-commons): supporting utilities, including message character encoding and request tracking.
- [SMPP 3.4 specification, issue 1.2](https://smpp.org/SMPP_v3_4_Issue1_2.pdf): protocol definitions and wire format.
