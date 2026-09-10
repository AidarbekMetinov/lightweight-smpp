# Release candidate evidence

The local candidate version is `0.1.0-rc.1`, with Java 21 as the minimum runtime
and Apache License 2.0. The Maven publication model describes the candidate;
publication remains a separate action. Production throughput and latency
requirements remain open. This document distinguishes the implemented protocol
inventory, independently observed interoperability and measured workload limits.

The subsequent [Maven publication audit](reviews/0019-maven-readiness.md) rechecks
the repository and supplies complete Central metadata, explicit GPG signing,
authenticated wrapper files and verified local bundles. Follow [the publishing
guide](PUBLISHING.md) for current commands and the remaining namespace/signing-key
setup. The Step 19 measurements below retain their original source identities;
they are historical evidence, not new measurements from the publication audit.

The later [efficiency follow-up](reviews/0020-load-efficiency.md) changes the
private production implementation and library JAR identity. Its fresh integration
passed 972 Java and 84 Python tests, covers 530 current Java identities, and passed
the current artifact inspector. [Performance qualification](PERFORMANCE.md)
records the new source/binary hashes, passing full target and connection results,
and both passing full-hour endurance runs. The external SMPP 3.4 comparison
also freshly passed all 15 cases against that optimized JAR, with the documented
peer incompatibility still explicitly expected. Its exact command, source hashes
and fresh reports are in `build/runs/efficiency-20260910/external-interop-result.json`
and sibling evidence files. The candidate remains unpublished.

## Rebuilding and inspecting the candidate

Run these commands sequentially from the project root:

```sh
./gradlew spotlessCheck --console=plain
./gradlew check build solidReviewInventory --console=plain
./gradlew generatePomFileForMavenJavaPublication generateMetadataFileForMavenJavaPublication :simulator:installDist --console=plain
python3 -m unittest discover -s tools/tests
python3 tools/check_release.py --version=0.1.0-rc.1 --output=build/reports/release/artifacts.json
```

The release checker uses Python 3.11 or later and only its standard library;
the bundle tests additionally use GnuPG with disposable keys. These tools are
needed for the explicit release audit, not for building or using the Java library. It
verifies current production class/source bytes, licensing in all three library
archives, the simulator's exact runtime JAR set, Maven coordinates, an empty
library dependency declaration and Gradle artifact hashes. The JSON report
records artifact sizes and SHA-256 digests.

Library outputs are `build/libs/lightweight-smpp-0.1.0-rc.1.jar`, the adjacent
`-sources.jar` and `-javadoc.jar`. Maven POM and Gradle module metadata are under
`build/publications/mavenJava/`. The simulator distribution is under
`simulator/build/install/simulator/`. Archive ordering and timestamps are fixed;
reproducibility observations must use the same source, toolchain and build inputs.

The library has no third-party runtime dependencies. The separate simulator
runtime contains the library, its own executable JAR and HdrHistogram 2.2.2.
JUnit, ArchUnit, formatter and review tooling are development dependencies.
Cloudhopper remains entirely outside the project as an
[external comparison](INTEROPERABILITY.md).

## API and application responsibilities

Start with [binding and endpoint examples](ENDPOINTS.md),
[message exchange](EXCHANGE.md), [common operations](COMMON_OPERATIONS.md) and
[message helpers](MESSAGE_HELPERS.md). The examples source set is compiled and
exercised by project tests and excluded from published library archives.
[Protocol](PROTOCOL.md) and [TLV](TLVS.md) inventories distinguish version and
role support. A locally available operation sender does not establish that the
remote peer has registered a service.

Applications own authentication policy, credential and trust material, durable
message acceptance, message IDs and storage, query/cancel/replace semantics,
network routing, carrier-specific encodings and TLV meaning, receipt generation
and correlation, billing and congestion response policy. Binary codecs preserve
raw values without supplying those application services. Request success records
a peer's SMPP response and does not establish handset delivery. A connection
failure can leave a transmitted message's remote acceptance unknown.

The loopback simulators validate bounded fixtures and acknowledge in memory.
Their reports distinguish originating requests, destination multiplicity,
latency populations, expected faults and final cleanup. They are development
tools, not a durable SMSC or a delivery guarantee.

## Candidate verification record

The immutable final candidate is based on Git revision
`387aed9ef523c85fb3cfa0f8f245438759deb6fc` plus declared production/build/tool
source digest `2d63cd99c32ca3b197cbc973a100c9280ef00a05d4c5311b0cb7c011bb823a00`.
Its complete declared-input archive, additional licensing/TLS inputs and artifact
audit are retained under `build/runs/step19-final-inputs/`. The archive SHA-256
is `c090e3ade02a73f5f10e9d2a2ca1da07cfb6f8899fee11768ce765871de7bf4e`.
This explicit snapshot includes the [carrier/deadline fix](reviews/0018-carrier-progress.md),
[platform sampler](reviews/0018-sampling-progress.md) and
[per-session concurrency selection](reviews/0018-concurrency-selection.md), plus
the [receiver drain correction](reviews/0018-receiver-grace.md). The earlier
candidate2 measurements retain their original simulator identity; only its
library bytes are identical to the final candidate.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Library binary | 384267 | `609e0d4e6150e3704942339a9df621465f662ef86e52241bc696c2adbd5a717f` |
| Library sources | 217609 | `b0e762014837a763efb2faa3d3086d1a5506337b925a93ea2e899f51542ba0bd` |
| Library Javadoc | 749763 | `0509b185e43cd230db9c3e8c7e02c24339918502231c5db92ec24b0e372629cf` |
| Simulator executable | 206843 | `a59d553d3ec84de5d953a927696565f2c498384386a241a74a113426011d1d9e` |
| Simulator distribution ZIP | 729732 | `3f4ddec407d8e21ee6be9c26d14b122b5aec20300b75bc9eaf1b8065de48c66d` |
| Simulator distribution TAR | 799744 | `48fbee0aa135827495d03173d7a09668d9bfdada5220b04d84957f2d80cf772b` |

The external 3.4 comparison freshly executed all 15 tests against this library
binary, with zero failures/errors/skips. The standalone build took 1 minute
46 seconds while load campaigns shared the host; peer compilation was reused,
test compilation and the explicitly rerun test task executed. Exact source,
result and command fingerprints are in
`build/runs/step19-corrected-inputs/external-interop.json`, SHA-256
`33cb94a4572bdd1d1e13f3bf86490a42109f19bfadaa4c8eed0a5ace6ee3dcb1`.
The [compatibility notes](INTEROPERABILITY.md) retain the negative-bind
incompatibility and the absence of an external 5.0 peer.

All 56 [raw fault pairs](FAULT_PEER.md) and 12 [receipt pairs](RECEIPT_SCENARIO.md)
also passed their original expected assertions on these exact artifacts. Every
pair completed cleanup with zero retained reservations. Fault scenarios that
deliberately violate healthy criteria remain negative endpoint results; passing
the fault assertion does not reclassify them as healthy traffic.

Separate [allocation diagnostics](ALLOCATION.md) recorded 3.24–7.85 MiB/s of
weighted allocation estimates per JVM. They include recorded startup and report
generation, and their load criteria failed under host contention. These are
statistical observations, with complete cleanup and no recorded JFR data loss.
They identify the earlier simulator `477056…` and the same final library binary;
the receiver-drain correction was not part of those JFR recordings.

An independent wire observer checked both profiles, all three message directions
and payload sizes 0, 32, 160 and 4096 octets on the final simulator. All 24
application capture audits passed: 128 requests and positive responses, with
137216 exact opaque payload bytes and matching receiver counts. The original
observer's additional shutdown-response criterion passed 23 of 24 pairs; one
5.0 delivery pair closed after successful traffic without acknowledging the
client's unbind. Both endpoints retired their resources. This control-exchange
limitation and the unsuccessful earlier harness attempts remain in the
[payload evidence](LOAD_TESTING.md).

## Integrated checks and build reproducibility

The final root `check build solidReviewInventory` and publication/distribution
tasks passed on 9 September 2026 in 117.26 seconds. That run freshly executed
137 simulator cases; 749 matching library results came from the build cache,
and 60 unchanged review-tool cases were reused. A separate fresh checkout then
executed **all 946 Java cases**: 749 library/architecture, 137 simulator and
60 review-tool cases, with zero failures, errors or skips. Compilation, examples,
formatting, Javadoc and current whole-type review coverage also passed. The final
inventory covers 512 Java identities, SHA-256
`047ec126e089416d889eac598c8adfcbfaa431a20505c55ee4f093e1aabeec77`.

The simulator Python suite discovered 37 methods: 35 passed and two optional
process-matrix methods were skipped in that default invocation. Both optional
methods separately ran the 56 fresh raw-peer pairs above and passed. All ten
release-checker tests and the actual artifact audit passed. Earlier process-test
deadline failures on the heavily contended host remain recorded in the reviews;
their assertions and production time bounds were unchanged for the successful
full runs.

The reproduction used a fresh checkout path and Java 21.0.12+8 on the same Linux
workstation. The complete invocation was:

```sh
./gradlew check build solidReviewInventory generatePomFileForMavenJavaPublication generateMetadataFileForMavenJavaPublication :simulator:installDist :simulator:javadoc --console=plain
```

| Build condition | Elapsed | Observation |
| --- | ---: | --- |
| Fresh task outputs and single-use JVM, with `--no-daemon --no-build-cache --no-configuration-cache` | 128.56 s | All 28 actionable tasks executed, including all 946 Java cases |
| First normal cached invocation | 1.17 s | Configuration cache stored; compilation and tests up to date |
| Identical normal invocation | 0.67 s | Configuration cache reused; compilation and tests up to date |

The Gradle distribution and dependency downloads were already cached. These are
fresh task-output observations, not cold network downloads or a cleared operating
system cache. The two built-in publication-metadata tasks executed in both warm
builds. Gradle daemon, build, configuration and file-system watching caches stay
enabled for normal development. Two ongoing final soak pairs shared the host.

All 11 compared outputs were byte-identical between the root and fresh checkout,
and between cold and warm builds: the three library archives, simulator JAR,
ZIP/TAR distributions, Unix/Windows launchers, POM, Gradle module metadata and
HdrHistogram JAR. This establishes reproducibility for these inputs on this
toolchain and host; other operating systems and machines were not tested.
The 489-path checkout-input manifest has SHA-256
`a9fccfdb248ca7c38d5639a826c0aaa5dd67dc25198a49ef6762c9b28897e083`.
Later measurement-document edits do not alter Java source, tests or archive contents.
Commands, logs, XML hashes, artifact hashes and timings are retained under
`build/runs/step19-reproducibility/`; `summary.json` SHA-256 is
`e03e0ed3c450622b0da70017fcebd20ba491157a9b6e35a507770bee8db5dd21`.

## Workload limits

The [measurement record](MEASUREMENTS.md) and [54-cell matrix](MATRIX_RESULTS.md)
retain failed provisional targets and source identities. Six final bidirectional
fixed-window repeats passed, with 10913898 successful measured requests and
complete cleanup. Fixed-concurrency runs have no independent offered-arrival
rate and do not establish the arrival-rate targets. Both final bidirectional
soaks completed the full 3600 s measured phase and 30 s receiver drain. All
31891984 admitted measurement requests succeeded, with complete endpoint and
sampler cleanup and zero final live ownership. The generators skipped 4108016
of 36000000 planned arrivals, so both campaigns failed their original zero-skip
and 99% successful-rate criteria. Their phase rates were 2206.912–2219.819/s
per originating JVM against the configured 2500/s. Scheduled p99 was
5.283–5.499 ms and sampled peak RSS reached 304.883 MiB. These are observations
from the shared-host workload, without an isolated capacity claim.

Step 19 local preparation is complete. The local candidate, full verification and
source-specific measurements are reviewable; failed targets and earlier
defects remain in the evidence record. The [release review](reviews/0018-release-preparation.md)
and regression reviews record TDD and whole-type SOLID verification.

These Step 19 measurements did not establish 1000-connection startup within the
configured budget; the later [performance qualification](PERFORMANCE.md) records
successful full profiles for the optimized runtime. The available evidence does
not establish production capacity, leak freedom, an external SMPP 5.0 peer
matrix, or acknowledged unbind in every simultaneous shutdown. Application duties
and provider-specific compatibility still apply. The local candidate has not
been published.
