"""Check Central bundle integrity using an isolated, disposable real GPG key."""
import hashlib
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from prepare_central_bundle import assemble

GROUP = "kg.aidarbek"
VERSION = "0.1.0-rc.1"
PREFIX = f"kg/aidarbek/lightweight-smpp/{VERSION}/"


class CentralBundleTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.keys = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.keys.cleanup)
        cls.gpg_home = Path(cls.keys.name)
        cls.gpg_home.chmod(0o700)
        cls.gpg("--pinentry-mode", "loopback", "--passphrase", "", "--quick-generate-key",
                "Bundle verification test <bundle-test@example.invalid>", "ed25519", "sign", "0")
        listing = cls.gpg("--with-colons", "--list-keys").stdout.decode()
        cls.fingerprint = next(line.split(":")[9] for line in listing.splitlines() if line.startswith("fpr:"))
        cls.addClassCleanup(subprocess.run, ["gpgconf", "--homedir", str(cls.gpg_home), "--kill", "gpg-agent"],
                            check=True, capture_output=True, timeout=30)

    @classmethod
    def gpg(cls, *arguments):
        return subprocess.run(["gpg", "--batch", "--homedir", str(cls.gpg_home), *arguments],
                              check=True, capture_output=True, timeout=30)

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.staging = self.root / "repository"
        self.directory = self.staging / PREFIX
        self.directory.mkdir(parents=True)
        current = self.root / "current"
        current.mkdir()
        self.publication = {}
        for suffix in (".jar", "-sources.jar", "-javadoc.jar", ".pom", ".module"):
            name = f"lightweight-smpp-{VERSION}{suffix}"
            source = current / name
            source.write_bytes(b"Separately validated publication " + suffix.encode())
            self.publication[name] = source
            staged = self.directory / name
            staged.write_bytes(source.read_bytes())
            self.gpg("--armor", "--detach-sign", "--local-user", self.fingerprint + "!", str(staged))
        self.output = self.root / "central-bundle.zip"

    def assemble(self, fingerprint=None):
        return assemble(self.staging, GROUP, VERSION, self.publication, fingerprint or self.fingerprint,
                        self.output, self.gpg_home)

    def test_contains_only_publication_signatures_and_correct_checksums_in_maven_layout(self):
        (self.staging / "maven-metadata.xml").write_text("repository metadata must not be uploaded")
        self.assemble()
        with ZipFile(self.output) as archive:
            self.assertEqual(30, len(archive.namelist()))
            self.assertEqual(30, len(set(archive.namelist())))
            for name, source in self.publication.items():
                data = source.read_bytes()
                self.assertEqual(data, archive.read(PREFIX + name))
                self.assertEqual((self.directory / (name + ".asc")).read_bytes(), archive.read(PREFIX + name + ".asc"))
                for algorithm in ("md5", "sha1", "sha256", "sha512"):
                    self.assertEqual(hashlib.new(algorithm, data).hexdigest(),
                                     archive.read(PREFIX + name + "." + algorithm).decode().strip())

    def test_same_signed_inputs_produce_identical_zip_bytes(self):
        self.assemble()
        first = self.output.read_bytes()
        for path in self.directory.iterdir():
            os.utime(path, (946684800, 946684800))
        self.assemble()
        self.assertEqual(first, self.output.read_bytes())

    def test_rejects_missing_signature_and_removes_stale_bundle(self):
        self.output.write_bytes(b"previous success must not survive a failed check")
        next(self.directory.glob("*.asc")).unlink()
        with self.assertRaises((ValueError, OSError)):
            self.assemble()
        self.assertFalse(self.output.exists())

    def test_rejects_staged_bytes_that_differ_from_current_build(self):
        next(self.directory.glob("*.jar")).write_bytes(b"stale binary")
        with self.assertRaisesRegex(ValueError, "current"):
            self.assemble()
        self.assertFalse(self.output.exists())

    def test_rejects_tampering_even_when_current_and_staged_files_match(self):
        name, source = next(iter(self.publication.items()))
        source.write_bytes(b"changed after signing")
        (self.directory / name).write_bytes(source.read_bytes())
        with self.assertRaisesRegex(ValueError, "signature"):
            self.assemble()
        self.assertFalse(self.output.exists())

    def test_rejects_a_valid_signature_from_a_different_key(self):
        with self.assertRaisesRegex(ValueError, "fingerprint"):
            self.assemble("A" * 40)
        self.assertFalse(self.output.exists())

    def test_rejects_an_incomplete_publication(self):
        del self.publication[next(name for name in self.publication if name.endswith("-sources.jar"))]
        with self.assertRaisesRegex(ValueError, "publication"):
            self.assemble()
        self.assertFalse(self.output.exists())

    def test_requires_the_expected_primary_key_to_sign(self):
        self.gpg("--pinentry-mode", "loopback", "--passphrase", "", "--quick-add-key",
                 self.fingerprint, "ed25519", "sign", "0")
        listing = self.gpg("--with-colons", "--list-keys").stdout.decode()
        subkey = [line.split(":")[9] for line in listing.splitlines() if line.startswith("fpr:")][-1]
        name = next(iter(self.publication))
        (self.directory / (name + ".asc")).unlink()
        self.gpg("--armor", "--detach-sign", "--local-user", subkey + "!", str(self.directory / name))

        with self.assertRaisesRegex(ValueError, "primary"):
            self.assemble()
        self.assertFalse(self.output.exists())

    def test_rejects_revoked_primary_key_despite_cryptographically_valid_signature(self):
        key_home = self.isolated_key_home("revoked")
        public = self.gpg("--armor", "--export", self.fingerprint).stdout
        revocation = (self.gpg_home / "openpgp-revocs.d" / (self.fingerprint + ".rev")).read_bytes()
        revocation = revocation.replace(b"\n:-----BEGIN", b"\n-----BEGIN", 1)
        subprocess.run(["gpg", "--batch", "--homedir", str(key_home), "--import"],
                       input=public + revocation, check=True, capture_output=True, timeout=30)

        with self.assertRaisesRegex(ValueError, "signature|revoked"):
            assemble(self.staging, GROUP, VERSION, self.publication, self.fingerprint, self.output, key_home)
        self.assertFalse(self.output.exists())

    def test_rejects_expired_primary_key_despite_cryptographically_valid_signature(self):
        key_home = self.isolated_key_home("expired")
        command = ["gpg", "--batch", "--homedir", str(key_home)]
        subprocess.run([*command, "--faked-system-time", "1577836800", "--pinentry-mode", "loopback",
                        "--passphrase", "", "--quick-generate-key", "Expired audit key <expired@example.invalid>",
                        "ed25519", "sign", "1d"], check=True, capture_output=True, timeout=30)
        listing = subprocess.run([*command, "--with-colons", "--list-keys"],
                                 check=True, capture_output=True, text=True, timeout=30).stdout
        fingerprint = next(line.split(":")[9] for line in listing.splitlines() if line.startswith("fpr:"))
        for name in self.publication:
            (self.directory / (name + ".asc")).unlink()
            subprocess.run([*command, "--faked-system-time", "1577836801", "--armor", "--detach-sign",
                            "--local-user", fingerprint + "!", str(self.directory / name)],
                           check=True, capture_output=True, timeout=30)

        with self.assertRaisesRegex(ValueError, "signature|expired"):
            assemble(self.staging, GROUP, VERSION, self.publication, fingerprint, self.output, key_home)
        self.assertFalse(self.output.exists())

    def isolated_key_home(self, name):
        key_home = self.root / name
        key_home.mkdir(mode=0o700)
        self.addCleanup(subprocess.run, ["gpgconf", "--homedir", str(key_home), "--kill", "gpg-agent"],
                        check=True, capture_output=True, timeout=30)
        return key_home


if __name__ == "__main__":
    unittest.main()
