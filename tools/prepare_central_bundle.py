"""Assemble a locally verified, signed Maven Central upload bundle; never upload it."""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import tempfile
from xml.etree import ElementTree
from zipfile import BadZipFile, ZipFile, ZipInfo

from check_release import check, digest, require, validate_coordinates


def publication_paths(root, version):
    prefix = f"lightweight-smpp-{version}"
    result = {prefix + suffix: root / "build/libs" / (prefix + suffix)
              for suffix in (".jar", "-sources.jar", "-javadoc.jar")}
    result[prefix + ".pom"] = root / "build/publications/mavenJava/pom-default.xml"
    result[prefix + ".module"] = root / "build/publications/mavenJava/module.json"
    return result


def verify_signature(path, fingerprint, gpg_home):
    command = ["gpg", "--batch", "--no-auto-key-retrieve", "--status-fd=1"]
    if gpg_home is not None:
        command.extend(["--homedir", str(gpg_home)])
    result = subprocess.run([*command, "--verify", str(path) + ".asc", str(path)],
                            capture_output=True, text=True, timeout=30)
    require(result.returncode == 0, f"Invalid signature: {path.name}")
    statuses = [line.split() for line in result.stdout.splitlines() if line.startswith("[GNUPG:] ")]
    labels = {fields[1] for fields in statuses if len(fields) > 1}
    require("GOODSIG" in labels and not labels.intersection(
                {"BADSIG", "ERRSIG", "EXPSIG", "EXPKEYSIG", "REVKEYSIG", "KEYEXPIRED", "KEYREVOKED", "SIGEXPIRED"}),
            f"Invalid, revoked or expired signature/key: {path.name}")
    valid = [fields for fields in statuses if len(fields) > 1 and fields[1] == "VALIDSIG"]
    require(len(valid) == 1 and len(valid[0]) >= 11, f"Missing valid signature: {path.name}")
    signing_key = valid[0][2]
    primary_key = valid[0][11] if len(valid[0]) > 11 else signing_key
    require(signing_key == primary_key, f"A primary-key signature is required: {path.name}")
    require(fingerprint.upper() == primary_key, f"Unexpected signing fingerprint: {path.name}")


def assemble(staging, group, version, publication, fingerprint, output, gpg_home=None):
    output = Path(output)
    output.unlink(missing_ok=True)
    validate_coordinates(version, group)
    require(re.fullmatch(r"(?:[A-Fa-f0-9]{40}|[A-Fa-f0-9]{64})", fingerprint) is not None,
            "A full signing fingerprint is required")
    require(set(publication) == set(publication_paths(Path("."), version)), "Incomplete or unexpected publication")
    prefix = f"{group.replace('.', '/')}/lightweight-smpp/{version}/"
    directory = Path(staging) / prefix
    output.parent.mkdir(parents=True, exist_ok=True)
    # Snapshot the exact bytes before GPG verification so the uploaded entries
    # cannot differ from the files whose signatures were checked.
    with tempfile.TemporaryDirectory(prefix="central-", dir=output.parent) as temporary:
        snapshot = Path(temporary)
        entries = {}
        for name, current in sorted(publication.items()):
            data = (directory / name).read_bytes()
            require(data == current.read_bytes(), f"Staged artifact differs from current build: {name}")
            signature = (directory / (name + ".asc")).read_bytes()
            require(signature.startswith(b"-----BEGIN PGP SIGNATURE-----"), f"Non-ASCII-armored signature: {name}")
            (snapshot / name).write_bytes(data)
            (snapshot / (name + ".asc")).write_bytes(signature)
            verify_signature(snapshot / name, fingerprint, gpg_home)
            entries[prefix + name] = data
            entries[prefix + name + ".asc"] = signature
            for algorithm in ("md5", "sha1", "sha256", "sha512"):
                entries[prefix + name + "." + algorithm] = hashlib.new(algorithm, data).hexdigest().encode("ascii")
        bundle = snapshot / "bundle.zip"
        with ZipFile(bundle, "w") as archive:
            for name, data in sorted(entries.items()):
                info = ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                info.create_system = 3
                info.external_attr = 0o100644 << 16
                archive.writestr(info, data)
        bundle.replace(output)
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--version", required=True)
    parser.add_argument("--group", default="kg.aidarbek")
    parser.add_argument("--fingerprint", required=True, help="Expected full primary signing-key fingerprint")
    parser.add_argument("--gpg-home", type=Path, help="Optional isolated GPG home, also used when signing with Gradle")
    parser.add_argument("--staging", type=Path, help="Defaults to ROOT/build/maven-staging")
    parser.add_argument("--output", type=Path, help="Defaults to ROOT/build/central-bundle.zip")
    arguments = parser.parse_args()
    root = arguments.root.resolve()
    output = arguments.output or root / "build/central-bundle.zip"
    try:
        output.unlink(missing_ok=True)
        check(root, arguments.version, arguments.group)
        assemble(arguments.staging or root / "build/maven-staging", arguments.group, arguments.version,
                 publication_paths(root, arguments.version), arguments.fingerprint, output, arguments.gpg_home)
    except (ValueError, OSError, BadZipFile, ElementTree.ParseError, subprocess.SubprocessError) as failure:
        parser.exit(1, f"Central bundle preparation failed: {failure}\n")
    print(f"Verified local bundle: {output} ({output.stat().st_size} bytes; SHA-256 {digest(output)})")
    print("No upload performed. Central namespace ownership, public-key availability and Portal validation remain external checks.")


if __name__ == "__main__":
    main()
