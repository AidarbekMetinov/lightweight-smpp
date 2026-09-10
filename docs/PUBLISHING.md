# Maven Central publication

The unpublished candidate is `kg.aidarbek:lightweight-smpp:0.1.0-rc.1`, targeting
Java 21. Local Maven and Gradle consumption, complete POM metadata, signed staging
and bundle verification are exercised by the [publication audit](reviews/0019-maven-readiness.md).
The audit uses a disposable signing key. It does not establish ownership of a
Central namespace, availability of the maintainer's public signing key, or acceptance
by Central's validator. No upload, release tag or publication has been performed.

## Coordinates and account setup

Central requires a verified namespace matching the publication's group ID.
`kg.aidarbek` requires ownership of `aidarbek.kg`; the GitHub-based
alternative is `io.github.aidarbekmetinov`. Confirm the namespace in the Central
Portal before selecting the first public coordinates. GitHub identity and a local
successful build alone do not prove that verification has completed.
See [Sonatype's namespace instructions](https://central.sonatype.org/register/namespace/).

The existing group remains the default pending that decision. To select another
verified group, pass `-PmavenGroup=GROUP` to every Gradle publication command and
`--group GROUP` to both Python tools. Java package names remain `kg.aidarbek.smpp`;
changing Maven coordinates does not rename the API. Avoid changing coordinates
after consumers begin using a published release.

The POM contains the project URL, maintainer name and email, Git SCM connections,
Apache License 2.0, and the actual release coordinates. The library declares no
third-party dependencies. The simulator, examples, tests, review tool and their
dependencies are excluded from the publication. Required metadata, sources,
Javadoc, signatures and checksums follow [Central's requirements](https://central.sonatype.org/publish/requirements/).

## Verify the candidate

Use JDK 21, the checked-in Gradle wrapper, Python 3.11 or later, and GnuPG.
Python and GnuPG are release-audit tools; applications using the library need
only Java 21. The release-tool tests use temporary GPG homes and disposable keys.
They do not access the maintainer's secret keyring.

From the project root, run these commands sequentially:

```sh
./gradlew check build solidReviewInventory --console=plain
./gradlew generatePomFileForMavenJavaPublication generateMetadataFileForMavenJavaPublication :simulator:installDist --console=plain
python3 -m unittest discover -s tools/tests
python3 -m unittest discover -s simulator/scripts/tests
python3 tools/check_release.py --version 0.1.0-rc.1 --output build/reports/release/artifacts.json
```

`check` includes Java behavior/architecture tests, formatting, review-tool tests
and current SOLID evidence. The explicit Python commands check release artifacts
and simulator scripts. Source changes require a new review and verification;
past load results retain their recorded source and binary identity.

The artifact checker verifies production class/source bytes, all archive licenses,
simulator runtime boundaries, Maven metadata and Gradle artifact hashes. It rejects
snapshot versions and removes an older success report if a subsequent check fails.
Read [release evidence](RELEASE.md) for the protocol and workload limits of this
candidate. Passing publication checks is separate from establishing production
capacity or compatibility with a particular SMSC.

## Sign and stage locally

Select the full fingerprint of the intended primary release key in your GPG keyring and
make its public key available as described in [Sonatype's signing guidance](https://central.sonatype.org/publish/requirements/gpg/).
Keep private keys and passphrases outside the repository. Gradle uses the local
GPG agent; enter a passphrase through that agent when required, rather than a
Gradle property. This keeps private signing material out of configuration-cache inputs.
The build uses Gradle's [GnuPG signing support](https://docs.gradle.org/current/userguide/signing_plugin.html).

Set `RELEASE_KEY_FINGERPRINT` in your shell to that full public fingerprint, then:

```sh
./gradlew publishMavenJavaPublicationToCentralStagingRepository --console=plain \
    -PreleaseSigning=true -Psigning.gnupg.keyName="$RELEASE_KEY_FINGERPRINT!"
python3 tools/prepare_central_bundle.py --version 0.1.0-rc.1 \
    --fingerprint "$RELEASE_KEY_FINGERPRINT"
```

The Gradle task writes only to `build/maven-staging/`. The build defines no remote
publishing repository. Signing is enabled explicitly and requires a full key
fingerprint; normal builds do not select a signing key. GPG's agent owns unlocking
the key. An isolated keyring can be selected with
`-Psigning.gnupg.homeDir=/absolute/path` and the same `--gpg-home /absolute/path`
on the Python command. Do not use the audit's disposable key for a real release.
The `!` selects the exact key in GPG. The local bundle policy requires a primary-key
signature, following Sonatype's signing guidance; a valid subkey signature is
rejected rather than assumed to work with every Central verification path.

The resulting `build/central-bundle.zip` contains exactly these five artifacts,
their ASCII-armored detached signatures, and MD5, SHA-1, SHA-256 and SHA-512 checksums
in the selected group's Maven directory layout:

- `lightweight-smpp-0.1.0-rc.1.jar`
- `lightweight-smpp-0.1.0-rc.1-sources.jar`
- `lightweight-smpp-0.1.0-rc.1-javadoc.jar`
- `lightweight-smpp-0.1.0-rc.1.pom`
- `lightweight-smpp-0.1.0-rc.1.module`

The bundle tool first rechecks the current build, compares each staged artifact
with it, snapshots the bytes, and verifies every signature against the expected
primary-key fingerprint without fetching keys. It creates checksums
from those verified bytes. Missing signatures, stale artifacts, tampering and a
different signing key fail preparation and remove an old bundle. Revoked or
expired signatures/keys known to the local keyring also fail. Check current key
status separately before publication: this verifier does not fetch revocations.
Repository-level metadata is excluded. The same signed inputs produce identical ZIP bytes; a new
signature can change the bundle even when its unsigned artifacts are identical.

## Validate and publish through the Portal

After account, namespace and signing-key setup, upload the reviewed bundle through
the Central Portal and inspect its validation result before publishing. The local
tool never uploads or calls a publishing API. Sonatype documents the
[bundle layout and manual upload workflow](https://central.sonatype.org/publish/publish-portal-upload/).
Central validation and publication are still required external steps; a local ZIP
is not a published dependency. A rejected upload must be corrected and revalidated.

Only after Central confirms publication can consumers use the coordinates from
Maven Central without the local staging repository. For the current default group:

```xml
<dependency>
    <groupId>kg.aidarbek</groupId>
    <artifactId>lightweight-smpp</artifactId>
    <version>0.1.0-rc.1</version>
</dependency>
```

```groovy
repositories { mavenCentral() }
dependencies { implementation 'kg.aidarbek:lightweight-smpp:0.1.0-rc.1' }
```

Use the selected verified group in both examples if it changes before publication.
The audit's Maven consumer resolves the binary, sources and Javadoc from a local
file repository; a separate Gradle consumer uses Gradle module metadata exclusively.
Those checks establish local artifact usability and do not claim Central visibility.
