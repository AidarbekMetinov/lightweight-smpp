# Review: Maven publication readiness

## Scope and result boundary

The starting revision is `094a84f595b61779904c0190ef15f6430561cd22` (`Prepare first release`).
The audit covers all 489 tracked baseline paths: 100 root/build/docs/tooling paths,
162 protocol/helper/session/port paths,133 endpoint/request/transport/example
paths, and 94 simulator paths. Counts are file coverage, not a claim of100% test
coverage or absence of all possible defects. New and changed files are reviewed
before integration is declared complete.

The three independent whole-source reviews are [protocol](0019-protocol-audit.md),
[endpoint](0019-endpoint-audit.md), and [simulator](0019-simulator-audit.md).
Every production and test Java source was read in full by its assigned reviewer.
The root reviewer also checked build files, all 12 review-tool Java sources,
release Python, wrapper scripts, licenses and TLS fixture metadata. Current
contract guides were cross-checked by the corresponding source reviewer.
Historical review documents received complete structured parsing, current-hash
reconciliation and document/link checks; this does not claim a second manual
reading of every historical principle paragraph or a new execution of old load
campaigns. Their historical results remain tied to their recorded inputs.

Central publication requires external namespace, account and public-key checks.
The default group remains `kg.aidarbek` while the owner selects a verified
namespace. The local workflow was exercised with a disposable primary signing
key and did not upload or publish anything. [PUBLISHING.md](../PUBLISHING.md)
contains the actual local commands and the remaining external steps.

## Findings and corrections

- The old POM lacked the project URL, developer identity and SCM information.
  The Maven publication now provides all of them and retains the Apache license.
- There was no signing or Central bundle workflow. Gradle's built-in signing and
  publishing plugins now provide explicit GPG signing and a local staging repository.
  The independent bundle tool checks all five current publication artifacts,
  verifies primary-key signatures, creates four checksum forms and emits only
  the intended Maven paths. No remote repository/plugin/runtime dependency was added.
- The old artifact checker accepted snapshot release versions and incomplete
  metadata. It now rejects both, supports an explicit publication group and removes
  stale success output on failure. A group override changes GAV metadata only.
- The checked-in wrapper JAR did not match the configured Gradle 9.6.0 release
  checksum. Regeneration produced the official JAR and current launcher scripts;
  the distribution now has an independently retrieved SHA-256 pin. No Gradle
  version or development-cache setting changed.
- Standalone review discovery included `simulator/build` generated Java even
  though Gradle's explicit inputs excluded it. Both paths now exclude that output
  root while keeping legitimate source packages named `build` included.
- Source reviewers corrected observable test-fixture contract and cleanup gaps,
  a misplaced defensive-copy assertion, and current documentation contradictions.
  Their reports record behavioral reds, corrections, final source hashes and SOLID
  findings. Production library/simulator Java and executable load scripts are unchanged.

The official Gradle 9.6.0 wrapper JAR SHA-256 is
`497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`;
the distribution pin is
`bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01`.
Both were retrieved from Gradle's distribution service and compared locally.
See [wrapper authentication](https://docs.gradle.org/current/userguide/gradle_wrapper.html),
[the wrapper checksum](https://services.gradle.org/distributions/gradle-9.6.0-wrapper.jar.sha256)
and [the distribution checksum](https://services.gradle.org/distributions/gradle-9.6.0-bin.zip.sha256).
The project LICENSE matches the canonical Apache 2.0 text, SHA-256
`cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`.
The public loopback TLS test key and certificate are structurally valid, match,
and contain `localhost`/`127.0.0.1` SANs. They remain test resources excluded from
publication. The simulator PKCS12 fixture is accounted for in its audit.

## Root TDD evidence

Commands ran in the root checkout unless noted. Logs are retained locally;
missing declarations or fixture-setup errors are explicitly excluded from red evidence.

| Cycle | Observed red | Correction and green |
| --- | --- | --- |
| POM completeness / release version | `python3 -m unittest discover -s tools/tests`: 12 relevant assertion failures in `/tmp/lightweight-smpp-maven-metadata-red2.log` (5 missing fields, 6 blank nested fields, 1 accepted snapshot). The new group argument also caused a TypeError, which is not behavioral evidence. | Required metadata and non-snapshot validation passed all 14 then-current methods. Checking real pre-fix artifacts separately failed with `Missing POM url`; regenerated complete metadata passed. |
| Generated-source discovery | `./gradlew reviewTest --tests '*ReviewCheckTest' --console=plain`: 15 cases, 1 failure for unexpected `simulator/build/generated/Generated.java`; `/tmp/maven-review-scope-red.log`. | Specific simulator build-root exclusion; all 15 passed with regenerated POM/module metadata in `/tmp/maven-metadata-review-green.log`. The source package named `build` remains covered. |
| Signed bundle | The compilable initial unsigned bundle assembled only five files; 7 tests yielded 6 assertion failures for missing checksums/signatures, stale/tampered artifacts, wrong key and incomplete publication (`/tmp/maven-bundle-red.log`). | Snapshot/signature/current-byte validation and exact 30-entry layout passed all 7; the deterministic timestamp test explicitly changes source mtimes. |
| Stale success JSON | `test_failed_cli_check_removes_an_earlier_success_report` failed after a real CLI success followed by a corrupted license (`/tmp/maven-release-stale-report-red.log`). | Output invalidation before checking; all 22 then-current release-tool tests passed. |
| Primary signing key | A real valid subkey signature was accepted unexpectedly (`/tmp/maven-primary-key-red.log`: 8 bundle cases, 1 assertion failure). | Explicit primary-key policy and fingerprint check; all 23 release-tool tests passed in `/tmp/maven-primary-key-green.log`. |
| Revoked / expired keys | Independent real-key review demonstrated that GPG can return success and VALIDSIG for a revoked key. Two permanent real-key regressions then failed (`/tmp/maven-key-state-red.log`: 10 bundle cases, 2 relevant assertion failures). | Require GOODSIG, reject revoked/expired/error statuses and retain exact primary fingerprint checks. All 25 release-tool tests passed in `/tmp/maven-key-state-green.log` and the independent `/tmp/publication-independent-key-state-green.log`. |

The first metadata test attempt also had a snapshot fixture filename error;
that error was corrected before the reported 12 behavioral failures. It is not
counted as the required red. No production-code mutation remains. The protocol
review separately records a temporary mutation proving the corrected ownership
assertion detects removed defensive copying, followed by restoration and green.

## Publication and consumer execution

`build/runs/maven-readiness-20260910/publishing-smoke.json` retains exact argument
vectors, durations and exits. The disposable key was generated in an isolated
GPG home, used only for local staging, exported publicly for verification, and
its agent/key directory were retired afterward. No maintainer private key was read.
The subsequent primary-key check verified the bundle using that exported public
key alone. The audit bundle is explicitly named `test-key-bundle.zip`.

- Signed local staging succeeded; repeating the same Gradle task reused the
  configuration cache and up-to-date signatures (0.615s including wrapper startup).
- The 30-entry bundle passed signature and checksum validation. It was 1366320
  bytes, SHA-256 `d17d59ad3978fa4c92ff17098565677250b1abffcca0f41c61ea6b7068a657af`.
- Maven 3.9.15 compiled an independent Java 21 consumer from the staged POM and JAR,
  then executed header encoding/decoding and loaded both public endpoint classes.
  Its dependency tree contained only this library. Sources and Javadoc classifiers
  resolved successfully into an isolated Maven local repository.
- A separate Gradle consumer configured `metadataSources { gradleMetadata() }`,
  resolved only this library, and executed the same published API. This exercises
  the `.module` artifact without falling back to the POM.

These checks prove local artifact usability. The following remain unverified:
Central namespace ownership, the actual release key and its public distribution,
and Portal validation of the real signed bundle. Publication and visibility in
Maven Central are not claimed. The existing external 5.0 peer and production-load
limitations in [RELEASE.md](../RELEASE.md) remain unchanged.

## Whole-type SOLID review: changed root Java

The whole `ReviewCheck` and `ReviewCheckTest` sources, their inventory/parser/
coverage collaborators, all CLI modes and failure outputs were inspected. The
change only aligns standalone source selection with the documented build roots.

```solid-review
source: src/review/java/kg/aidarbek/smpp/review/ReviewCheck.java
type: kg.aidarbek.smpp.review.ReviewCheck
sha256: 28a76af00c8f376d738d1fc2b650088547feb2dccfd5ac2b8116aefc2aaa3206
responsibility: Coordinates development-only source discovery, review validation and deterministic report output for the documented CLI modes.
consumers: Gradle uses explicit declared files; standalone callers use discovery; inventory, parser and coverage collaborators retain their own policies.
S: pass | Command orchestration and filesystem selection serve the review invocation; Java parsing and semantic coverage remain delegated.
O: pass | The two documented output roots are explicit selection policy; adding a generated root does not require changing Java parsing or principle validation.
L: pass | CLI modes retain relative-path restrictions, UTF-8 input, deterministic output and failed-check invalidation; the new regression excludes simulator output while retaining actual build-named packages.
I: pass | The entry point exposes only command arguments; inventory-only and validation paths retain their distinct outputs without forcing runtime library callers to depend on tooling.
D: pass | The CLI is the filesystem composition boundary; it delegates syntax, evidence parsing and matching to narrow development collaborators and depends on no protocol or publishing implementation.
findings: none
```

```solid-review
source: src/reviewTest/java/kg/aidarbek/smpp/review/ReviewCheckTest.java
type: kg.aidarbek.smpp.review.ReviewCheckTest
sha256: d2c47c55f1ed2198aa485b8e7ffcb0de8db729b3e511274450baa008290c508b
responsibility: Exercises the review CLI's observable source-selection, validation, argument and output-lifecycle contracts in temporary repositories.
consumers: Jupiter supplies parameter cases and temporary directories; the tests call real CLI/inventory collaborators and inspect actual output or failures.
S: pass | Scenarios all protect the command boundary; the generated-root cases complement the existing legitimate build-package inclusion case.
O: pass | Parameterized paths extend the scenario set without introducing a parallel source-discovery implementation or modifying production contracts for tests.
L: pass | Temporary repositories use independent Java/evidence fixtures and known hashes; both generated roots succeed and unreviewed legitimate sources still fail, preserving exception/output expectations.
I: pass | Only focused CLI entry points and filesystem observations are used; the fixture helper creates one minimal coherent reviewed project.
D: pass | Tests exercise the real development boundary with JDK files and Jupiter; they do not mock parsing or require protocol, simulator or network dependencies.
findings: none
```

## Other root design checks

The remaining review-tool sources retain their previous hashes. Their fresh
whole-source review confirms separate immutable byte snapshots, JDK syntax/type
inventory, strict evidence parsing, immutable review values and coverage matching.
The tests independently check syntax, ownership, identities, malformed evidence
and stale/conflicting reviews; no additional violation was found.

`SolidReviewArguments` in `build.gradle` is unchanged as a type. Its five-principle
review was repeated alongside the changed build: S: it adapts declared review
inputs into CLI arguments; O: supplied file collections and mode carry variation;
L: it retains deterministic sorted relative arguments and the argument-provider
contract; I: annotated properties expose only required task inputs/output location;
D: it depends on Gradle's provider/file APIs at the build composition boundary.
The task separately declares policy/classpath/output dependencies. Signing remains
opt-in and uses GPG's agent; publishing targets only a build-directory file repository.

For Python, archive inspection, POM validation and coordinate policy are focused
functions; bundle assembly delegates baseline verification and GPG validation.
Independent fixtures and actual temporary GPG signatures cover observable integrity
contracts. Mapping/path arguments separate assembly from CLI selection. Context
managers close archives and temporary directories; bounded subprocesses do not own
an upload/API client. Tests own disposable key/process cleanup. No library/runtime
API or dependency is exposed to either tool. These are specific S/O/L/I/D design
checks, not a claim that a formatter establishes SOLID.

## Baseline root file accounting

This table fingerprints the 100 baseline paths before changes. Final changed-file
hashes are reconciled after integration; additional contract-doc tables in the
three source reports record final hashes for their reviewed guides. Historical
records are checked structurally and retained with their source provenance.

| Baseline path | Baseline SHA-256 | Review method |
| --- | --- | --- |
| `.editorconfig` | `d6d9813147bb491c8ae4de053ad6b53c8a5abe33b952f8a915812f748ed14cc3` | Whole-file configuration/tooling and relevant executable checks |
| `.gitattributes` | `697b5603a3f4dbb2a942b970aebfe8c96e2d430e6aea02650d19d7c81190b65a` | Whole-file configuration/tooling and relevant executable checks |
| `.gitignore` | `da0c1816e502430a680ab275689e4f24e2cbb615d7b521f5952963124bf7c5eb` | Whole-file configuration/tooling and relevant executable checks |
| `AGENTS.md` | `6f41d8c87d448c8e5c2d9e1e874b08f59084222fb7099d608af837f0ca90ed6b` | Content/status and document/link review |
| `LICENSE` | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` | Exact canonical Apache 2.0 text comparison |
| `NOTICE` | `85b5a1f473011e1daeaef75f1613214f2caf52f11c88bf532b1b5bb9f4ebb831` | Whole-file configuration/tooling and relevant executable checks |
| `build.gradle` | `47502759a279fb1118327a4e6026348175594d6ec23bb04ebec6b1a40965fa56` | Whole-file configuration/tooling and relevant executable checks |
| `docs/ALLOCATION.md` | `f1699452007a2118874292d8c0df52ee03df52ccb9dd14eb68dd5ca771ab589f` | Content/status and document/link review |
| `docs/API.md` | `4dda9fa36800bf0081402befb77c4081c66c2460758c8e72b25c1b02fd7e5978` | Full contract read in endpoint audit |
| `docs/BROADCAST.md` | `3162f6e19ab7176723ff746a6e5ac4ce429c6a562e183831ba3fa7e2c0ae748b` | Full contract read in endpoint audit |
| `docs/COMMANDS.md` | `9e3948d08b388ad376a53b7a55021de376e45090b4a9712b0b79c2d18309d9bb` | Full contract read in protocol audit |
| `docs/COMMON_OPERATIONS.md` | `921a6d9f4ea19f908665473b17974ab547d207a0183640b487c5e85932856e58` | Full contract read in endpoint audit |
| `docs/DEVELOPMENT.md` | `480736009acb9557a5d38f7a5c0e8596dd226c51273f97274a83423850113292` | Content/status and document/link review |
| `docs/ENDPOINTS.md` | `7d5c7459266918095b0827a39e257213b60c1e6d5728cc372f9f169336f606bc` | Full contract read in endpoint audit |
| `docs/EXCHANGE.md` | `7f02d8e46c619f1b284a974e66ee75cdf0edf22d496dcadd142c1b3c1fcee08a` | Full contract read in endpoint audit |
| `docs/FAULT_PEER.md` | `1846a248be2d619cd467865ec02423483acc6437af538f23c72369cc28e2bf98` | Full contract read in simulator audit |
| `docs/FIELDS.md` | `9e23ee63af436d02d779018f2d12999c59dda83593dd4a7812164cc2a3f49ffc` | Full contract read in protocol audit |
| `docs/FRAMING.md` | `aba443e2858008d09a35e0eef012a5d5cc34254220f941109fc14378b6ac2cd1` | Full contract read in protocol audit |
| `docs/INTEROPERABILITY.md` | `20dec16643071678483a9cc1e9b251fc46387fe109283031654644b8da08f6b0` | Content/status and document/link review |
| `docs/LIFECYCLE.md` | `17b0f79aafa7fdf0af618c2a1a93c310fb701f5802893cd327917268e11feb67` | Full contract read in endpoint audit |
| `docs/LOAD_TESTING.md` | `4f9d2627d17e320770249be43cdae4f05257c2ad31d9fe00c1011526d125d6f3` | Full contract read in simulator audit |
| `docs/MATRIX_RESULTS.md` | `f84e919a1df87d5a03526373227dfe199d61ae5678f872cae370f3d299960257` | Content/status and document/link review |
| `docs/MEASUREMENTS.md` | `ec4cd05de7229338478bfcd7a63a297d790a71dbf178444d1082a258e13baa41` | Content/status and document/link review |
| `docs/MESSAGES.md` | `39a041c4a377d5236856e52d2eefb83f778d72e0bf33219972f3c98c9de6208f` | Full contract read in protocol audit |
| `docs/MESSAGE_HELPERS.md` | `cdd6ce76ae5c3e923d5dc0c89cd7494f6f534de854976e632799214fa40933bf` | Full contract read in protocol audit |
| `docs/PROTOCOL.md` | `54a9bf315c2b7ccbc19458a88460b041b153d4ca148ff3bdcc54568b41bc2449` | Full contract read in protocol audit |
| `docs/README.md` | `5bca98b1794b627c3228b98a5e6a00a4f5b3e6aa1ae693ae8600cf1e59092763` | Content/status and document/link review |
| `docs/RECEIPT_SCENARIO.md` | `92df2ec87c0d01e6c2fd7c18ff01d857c6f64d8a1ffec63ff8cfa04075f358ca` | Full contract read in simulator audit |
| `docs/RELEASE.md` | `e686a726496f8694b05f57dcf0854c76a11fb833d0aa0ba6a4cdb42b4cba34b9` | Content/status and document/link review |
| `docs/REQUESTS.md` | `311c18be00d0d41fb9e642db4c026fce87d990a71e0ff9dc279c006c0697761d` | Full contract read in endpoint audit |
| `docs/RESEARCH.md` | `2b964ab3524d143a26f36bbdd48fc1a99544b22824099ed91bf387a491a85f35` | Content/status and document/link review |
| `docs/REVIEW_FORMAT.md` | `4c8dcb5bc0697f33d2f0f5005e1a17a98ba553ee8646bf874aba0e59600a1af8` | Content/status and document/link review |
| `docs/ROADMAP.md` | `cab6b0e3aa1c2d8c1709ec7b47233efc64a32f5cd84134cbe0f64fe7328da163` | Content/status and document/link review |
| `docs/SESSIONS.md` | `43d2e54475e253bd78ab041556dc6be5f4e249544ed22fec39f181295d85f474` | Full contract read in protocol audit |
| `docs/SIMULATORS.md` | `1d0d661bf2ec473401e639c359e51f3b3870dc6c611b1dc2cf495279d51ccbd5` | Full contract read in simulator audit |
| `docs/SOLID.md` | `f2672fa892fd1427826f34031124b378b88ab56698d4e9f82fb3df3e248cf17d` | Content/status and document/link review |
| `docs/TDD.md` | `a6aca14759a1131a511b3c3171688dc49789e9494ad63c8a010d97e2d864a945` | Content/status and document/link review |
| `docs/TEST_PLAN.md` | `9f8c01f6e7f5adf729ea0898e3d600ca2315a0245816913fba8521db34a9c499` | Content/status and document/link review |
| `docs/TLVS.md` | `42e3153a40cc1df866d7ce71ad834057e55382ecf86d36cbd47c622b0e4a8f3e` | Full contract read in protocol audit |
| `docs/TRANSPORT.md` | `ef8b0390e26d57defd7370bc16795311117c8b22481176581f827eba0456fd34` | Full contract read in endpoint audit |
| `docs/WORKLOADS.md` | `b7d5ee8339f239e7849ae6250b4114acb86877cf2fa4d4dbcca2a26a602af59a` | Content/status and document/link review |
| `docs/reviews/0001-code-checks.md` | `d8f758c926971effdecb2beaccd68c8fb10a5db10c806275ec0dd9e22115285d` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0002-pdu-framing.md` | `57c3a7646837d079926ef4e354f671865c1e5c88e8473792f48fd8cdbbd8e439` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0003-review-coverage.md` | `5c619f742285391b74535a274dddfa089d44f1921fd32b89abe28172f2285158` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0004-fields-profiles.md` | `77c1203db1d5a772226556c6e540ccd660c41cdb71a5ac33e2cb84ea0c5fdcc8` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0005-apache-license.md` | `96ed5cd9a3b9827321c2bd6319cfc95b3edcd013705360ab2eaaf540986d243f` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0005-session-command-codecs.md` | `dfea53eb858631ec794616f2e558447f3dfe74ec8ce858ae1844d6124cbfd489` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0006-message-codecs.md` | `4fa60483527d8457b902519b8619228cefcc5ef9938df3a4335be88ebc87d21f` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0007-session-architecture.md` | `2ca8ad242c2674aa78b885d5e3da6615b1fdda5c977c456631afce603124d6ee` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0007-session-state.md` | `8197b5ff09ee6a6b26b4dd42209b5cd85ed95ab269c7cd4ddc93c9e0327327df` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0008-request-architecture.md` | `25bed618c84bb476285af6c2032f4d4060c16b3cd5077288972a364b925cd1e2` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0008-request-tracking.md` | `5081f984a30785f2e44b0a562ad311fa1ab2b91c9025d50e4ceb49e3f9a94169` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0009-tcp-transport.md` | `4aba11f428edd1b23a3c70bb4089ff2d743b8d54414eea59933956254faa0bd6` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0009-transport-architecture.md` | `9923e285bd08f6c4fc9311a01f9c1245c9e38e908b02e5665d541f38024a60fb` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0010-client-server-binding.md` | `de3e7e07e9a1e42826cb090acb6f31c0ef683d78805daa4aca4455e0f6103bca` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0010-endpoint-architecture.md` | `fd3ba2dc06e81aba39a23037d0baafb85ede9ed456db5dc3b99dfe7ca3306276` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0011-message-exchange.md` | `5215d62d1ecb03b2400ebbd12bc32d4cdb76e2b7739e0151b8ff816b59a139d4` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0012-simulator-types.md` | `7ef8689a8ed1c889deb56d26290ed4db82f0cf3954f56b3c18a8991d47391bd3` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0012-simulators.md` | `86fa9a5fd76ee8618a6f969dabe555831b3e5857d30204e059d0fca1789a5fc9` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0013-common-operations.md` | `c9e60959be24d42e5278e83d737b128bb2d87e377316477a42ea8489069f4e9a` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0013-simulator-integration.md` | `766ba7b33febcbd5c393fd67f102b86b3d3008c17c4591ee63416707093d5128` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0014-message-architecture.md` | `58dafaf1bb6b497b08363dbc1e294616739101dfdc8da45ba4443b186ed261db` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0014-message-helpers.md` | `f7b3c3a835231ce56e613075f640445c238692f88b9b9911ce1ae79d432d4ed8` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0014-simulator-integration-types.md` | `f6dfd5051744461159236e8584dd0e4c247a63475cb9a1b63d18efd47c450766` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0014-simulator-integration.md` | `bd0da95a691914b642ee779a7d7ecbcfa5c0ed9d0287171bb95bae77fa4a5593` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0015-simulator-integration.md` | `2281a6f62c2922fd374529b362c2e6853b38502364d7e60a98d14a66104bcf51` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0015-smpp5.md` | `6eda35f1f5a9cc631c0df0de5fda65a87c24120eb9f5e9870fb73a0d21f02ffd` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0016-connection-lifecycle.md` | `6a481655c7dfbee93afb4b012f86f4292a6869853a34a422caaf6bc2c189c300` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0016-lifecycle-architecture.md` | `b1d7331da681f571c36f4f54b7c63de01369fdde94dcc6bffc15e1f21370d982` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0016-simulator-architecture.md` | `e74aaa89d4a265131cfde2214e00e2ae85720a51f2c361e9ba4818b42ff56a46` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0017-fault-peer.md` | `6aebd47711f365e81fea20b0f8e4a016609dac3efa09a6050f866da3e2859568` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0017-load-scenarios.md` | `cbc937c4ec238444cfb017f7a2de943ba94e8786eb1005056fe3da7d1f065d5b` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0017-receipt-scenario.md` | `20cc792de1c20bd3c369ade0abd9e6d166c14eb9090e9e0006ce24df1f0b2431` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0018-carrier-progress.md` | `abff348bcf3bc62e2666459003e83d5b2eba42225ab279dbf40af12728a49c8a` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0018-concurrency-selection.md` | `3af3653fa250a97ef11fd376f35e82e4402c93c96cf327814cdc3c0bd22cefe4` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0018-receiver-grace.md` | `e1fd2e7ae13b8f550b7e794d79f20d2f1d7bb95d26d731d5180e65f1d124817c` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0018-release-preparation.md` | `8375afd69c3c6b3ca2417f69a32a024a28a33b664f67fdc7c9a59a7b0201090c` | Historical evidence: complete parser, freshness and document/link checks |
| `docs/reviews/0018-sampling-progress.md` | `c54e45252ec4880ab64a0a7177ea4ee9eb938652e8363f034a029b96bf37752e` | Historical evidence: complete parser, freshness and document/link checks |
| `gradle.properties` | `de18b85536f590dd36a68ac89ae49c51f20c693643446bb5d054be6d9abbadbd` | Whole-file configuration/tooling and relevant executable checks |
| `gradle/wrapper/gradle-wrapper.jar` | `91a239400bb638f36a1795d8fdf7939d532cdc7d794d1119b7261aac158b1e60` | Binary verified against official Gradle 9.6.0 checksum; regenerated |
| `gradle/wrapper/gradle-wrapper.properties` | `ec78deafdeba7116d68c0db47299b8d899de9b5acd62ef1e0d991dd7fefb92f5` | Whole-file configuration/tooling and relevant executable checks |
| `gradlew` | `f96a757581fe5292465e3f3393380bd11fd8086d450b92e7c693da6513f583fe` | Generated Gradle 9.6.0 scripts, full diff and launch/syntax checks |
| `gradlew.bat` | `af835f98787e9269af5a046edcb821a592fed372139df7b947b471a63cfc236b` | Generated Gradle 9.6.0 scripts, full diff and launch/syntax checks |
| `settings.gradle` | `52ae2f0007aa4c15c77d8448361f21cb89830daf1dd4279e3ed4d15769a348fa` | Whole-file configuration/tooling and relevant executable checks |
| `src/review/java/kg/aidarbek/smpp/review/JavaSourceInventory.java` | `0bc8211a1fa5699db4830c27aafc7b40b3625dd33c25370c7806398b3e48ba85` | Whole-source and test-contract review; JDK inventory and execution |
| `src/review/java/kg/aidarbek/smpp/review/JavaSourceSnapshot.java` | `76621cd7d0e14a58197b1ea73037a469766099180d8fb9a7a96239b1a70900d9` | Whole-source and test-contract review; JDK inventory and execution |
| `src/review/java/kg/aidarbek/smpp/review/PrincipleReview.java` | `9f679f7267afef5d9b6bc87f28a4716e47095ab42a4f579893aaedd3c64d3eba` | Whole-source and test-contract review; JDK inventory and execution |
| `src/review/java/kg/aidarbek/smpp/review/ReviewCheck.java` | `6a8d139cf7315510790c0c8d522248c9f8cdbd48ad5f94c3e165c18c60c45a9d` | Whole-source and test-contract review; JDK inventory and execution |
| `src/review/java/kg/aidarbek/smpp/review/ReviewCoverage.java` | `fc8b1a135cc187c65b9a04e2ba1e2c6b3c6ddfb60f1d108cecd37faeaecd6dd7` | Whole-source and test-contract review; JDK inventory and execution |
| `src/review/java/kg/aidarbek/smpp/review/ReviewDocumentParser.java` | `aec124b0d54289bd68d986eef5e4fb1701313e94895533ebf5ed1a910b4cfd5c` | Whole-source and test-contract review; JDK inventory and execution |
| `src/review/java/kg/aidarbek/smpp/review/ReviewEvidence.java` | `bda09be23f4d63162b1574f287558c5768220c9d8339b3233832c19ab76f54c2` | Whole-source and test-contract review; JDK inventory and execution |
| `src/review/java/kg/aidarbek/smpp/review/SourceType.java` | `1cccba7cf1153628431b10bad981839710305f1c117e37c0e529e52ffd3efb5e` | Whole-source and test-contract review; JDK inventory and execution |
| `src/reviewTest/java/kg/aidarbek/smpp/review/JavaSourceInventoryTest.java` | `7a3dcdaaed6e422fcbfb22c39f7b743f5e8a21a845d3f543aa8eeab7c49229a0` | Whole-source and test-contract review; JDK inventory and execution |
| `src/reviewTest/java/kg/aidarbek/smpp/review/ReviewCheckTest.java` | `e19f8aad05a09c70df7bab2059e41906abeac30f6139938e9bf419522c365627` | Whole-source and test-contract review; JDK inventory and execution |
| `src/reviewTest/java/kg/aidarbek/smpp/review/ReviewCoverageTest.java` | `a3b5089b5fca6479532cdb61084e966d9b236df8e5895b3889ec3357330c928b` | Whole-source and test-contract review; JDK inventory and execution |
| `src/reviewTest/java/kg/aidarbek/smpp/review/ReviewDocumentParserTest.java` | `29e52ceff68e78c8eb5405dfe75f88daf55dd92705229159d6091d682a007bd5` | Whole-source and test-contract review; JDK inventory and execution |
| `src/test/resources/tls/development-certificate.pem` | `e83bd30889df6911a52ebfff4693149e9f7efa2d30bdd3a5218c1ce0be6da301` | OpenSSL structure, identity, validity and key/certificate correspondence |
| `src/test/resources/tls/development-key.pem` | `0193797e694eba3a1203ac94a91361f2a7b2f92ee4e9fdbc724cabc0ccf5f371` | OpenSSL structure, identity, validity and key/certificate correspondence |
| `tools/check_release.py` | `9664be032f19f35ebd11d6b7031f12aad70812f55978541bf4e3e2e447474918` | Whole-file configuration/tooling and relevant executable checks |
| `tools/tests/test_check_release.py` | `cf3d2287b4f418f99381af36e014df90b81eaf113c061caeaa1b4f76fa69f30d` | Whole-file configuration/tooling and relevant executable checks |

## Integrated final verification

The integrated Java invocation was:

```sh
./gradlew test --rerun reviewTest --rerun :simulator:test --rerun check build solidReviewInventory generatePomFileForMavenJavaPublication generateMetadataFileForMavenJavaPublication :simulator:installDist --console=plain
```

All **967 Java cases** executed freshly: 764 library/architecture, 141 simulator
and 62 review-tool cases, with zero failures, errors or skips. The build completed
in 1m 50s; compilation used declared current inputs and normal caching. A separate
explicit rerun rebuilt library and simulator Javadoc without warnings. The
integrated inventory has **525 current Java identities**, each with matching
whole-type evidence. Full-source review remains distinct from automatic coverage.

All 202 published class files use Java 21 major version 65. `jdeps --jdk-internals`
reported no internal JDK dependency. Source/build scans found no Cloudhopper code
or dependency. The artifact checker passed exact class/source membership,
LICENSE/NOTICE, POM/module metadata and simulator dependency boundaries.

An independent checkout reproduced all six compared unsigned outputs byte for
byte: binary/source/Javadoc JARs, POM, Gradle module metadata and simulator JAR.
It then exercised `-PmavenGroup=io.github.aidarbekmetinov`, exact primary-key
selection with `!`, and a key that also had a signing subkey. Signing and bundle
verification succeeded; the repeated signing task reused configuration and
up-to-date signatures. This tests the override, without selecting or verifying
that namespace for the maintainer. The alternate audit bundle is 1367188 bytes,
SHA-256 `f5d2ba34cc30fb5215e6d5882bcb39d941d62af4775a7c99f9643533ebfccc5a`.
After the revoked/expired-key correction, both real audit bundles were verified
again with the final tool and public keys alone; their ZIP bytes stayed identical.

One reused Gradle daemon reported a transient VFS registration conflict during
format/inventory setup and reset its watcher. The following normal info-level
build explicitly reported `File system watching is active`, the local build
cache, and all three test tasks up to date. Settings remain enabled; no cache or
watcher was disabled to pass. The final normal repetition records configuration
reuse separately from the fresh test run.

The primary local bundle uses a disposable key. Its staging repository was moved
to `build/runs/maven-readiness-20260910/test-key-staging` after the consumer checks,
leaving the default staging path clear for a real release key. Both public test
keys and explicitly named test bundles remain audit evidence; secret temporary
key homes and their owned agents were removed. There is no default release bundle
signed with a disposable key and no remote upload.

All 489 original tracked files and 12 added files are accounted for (**501 final
project files**). The final per-file snapshot, actual XML totals, retained logs
and artifact hashes are recorded under
`build/runs/maven-readiness-20260910/`. The original five historical evidence
summaries for final/corrected soaks, the matrix, JFR and build reproducibility
were rehashed successfully against the values in the existing guides. None was
relabeled as a fresh measurement, and all prior failed targets remain visible.

The final `build.gradle` whole-file SHA-256 is `7c59b6b8e7b626ca3ea76efe008ed3d37ffa76759cf8900a5edc564981969233`.
The manually reviewed temporary `PublishedApi` consumer (outside the project
source inventory) has SHA-256
`fb0b84c06ae4f3e98d3bcf5bca18c603f78029c4a2205f49cd99b34f84241c86`.
S: it checks usability of one published API; O: no runtime extension is required;
L: its main method fails on a wrong decoded value or absent endpoint contract;
I: it calls the codec/value API and checks only the public endpoint resource type;
D: it depends on the published library and JDK only. Maven and Gradle each ran it.

The root report, new publishing guide, both new Python files and all new Java
fixtures received complete content/design review. Generated audit JSON is
structurally checked against the exact frozen/final source hashes. All Markdown
is parsed for balanced fences, heading structure, UTF-8/LF and local links.
A final source fingerprint and verification manifest preserve this audit's exact
inputs; subsequent edits require checking them again.

The final simulator Python command enabled the two installed-target methods,
using the freshly checked launcher and source identity
`094a84f595b61779904c0190ef15f6430561cd22+0040cb7a326693847f4e4504e0f219b25ce220e37a012a0bc93e1b97b627ab68`.
All **38 simulator Python methods passed with zero skips** in 113.927s, including
**56 fresh installed fault pairs** across both profiles/roles and applicable
submit/deliver/data directions. The pairs retained expected fault outcomes, zero
pending calls and no abandoned injection; they are finite functional checks,
not new capacity measurements. Together with 25 release-tool methods, **63 Python
methods passed**. The simulator report's earlier two skipped methods remain its
accurate isolated-worktree result; this later integrated execution supplies the
previously optional installed-target evidence.

The actual final runs and input hashes are retained in
`simulator-python-verification.json`, `integrated-test-summary.json`,
`current-production-inputs.json`, `final-artifacts.json`, and the 53 copied TDD/
diagnostic logs listed by `tdd-log-manifest.json`, all under
`build/runs/maven-readiness-20260910/`. Original log names remain in the cycle
reports. Root integration trimmed one extra final blank line from the simulator
audit document; its Java and guide hashes remain those reviewed by its author.
