"""Exercise artifact boundaries with separately constructed, deliberately corrupted bundles."""
import importlib.util
import hashlib
from pathlib import Path
import tempfile
import unittest
import json
import warnings
import subprocess
import sys
from xml.etree import ElementTree
from zipfile import ZipFile

SPEC = importlib.util.spec_from_file_location("check_release", Path(__file__).parents[1] / "check_release.py")
CHECKER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECKER)
VERSION = "0.1.0-rc.1"


class ReleaseCheckTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        for path, data in {
            "LICENSE": "project license", "NOTICE": "project attribution",
            "src/main/java/kg/aidarbek/smpp/Demo.java": "package kg.aidarbek.smpp; public class Demo {}",
            "build/classes/java/main/kg/aidarbek/smpp/Demo.class": "compiled library",
            "simulator/build/classes/java/main/kg/aidarbek/simulator/Main.class": "compiled tool",
        }.items():
            target = self.root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(data)
        self.library = self.root / f"build/libs/lightweight-smpp-{VERSION}.jar"
        self.archive(self.library, {"kg/aidarbek/smpp/Demo.class": b"compiled library"})
        self.archive(self.library.with_name(f"lightweight-smpp-{VERSION}-sources.jar"),
                     {"kg/aidarbek/smpp/Demo.java": (self.root / "src/main/java/kg/aidarbek/smpp/Demo.java").read_bytes()})
        self.archive(self.library.with_name(f"lightweight-smpp-{VERSION}-javadoc.jar"), {"index.html": b"API"})
        self.distribution = self.root / "simulator/build/install/simulator"
        self.archive(self.distribution / f"lib/simulator-{VERSION}.jar", {"kg/aidarbek/simulator/Main.class": b"compiled tool"})
        (self.distribution / "lib" / self.library.name).write_bytes(self.library.read_bytes())
        self.archive(self.distribution / "lib/HdrHistogram-2.2.2.jar", {"META-INF/LICENSE.txt": b"BSD or public domain"}, False)
        for name in ("LICENSE", "NOTICE"):
            (self.distribution / name).write_bytes((self.root / name).read_bytes())
        self.pom = self.root / "build/publications/mavenJava/pom-default.xml"
        self.pom.parent.mkdir(parents=True)
        self.pom.write_text(f'''<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>kg.aidarbek</groupId><artifactId>lightweight-smpp</artifactId><version>{VERSION}</version>
<name>Lightweight SMPP</name><description>A lightweight Java SMPP library</description>
<url>https://github.com/AidarbekMetinov/lightweight-smpp</url>
<developers><developer><id>AidarbekMetinov</id><name>Aidarbek Metinov</name><email>aidar.financier@gmail.com</email></developer></developers>
<scm><connection>scm:git:https://github.com/AidarbekMetinov/lightweight-smpp.git</connection>
<developerConnection>scm:git:ssh://git@github.com/AidarbekMetinov/lightweight-smpp.git</developerConnection>
<url>https://github.com/AidarbekMetinov/lightweight-smpp</url></scm>
<licenses><license><name>Apache License, Version 2.0</name><url>https://www.apache.org/licenses/LICENSE-2.0.txt</url></license></licenses></project>''')
        self.metadata = self.pom.with_name("module.json")
        published = sorted(self.library.parent.glob("*.jar"))
        self.metadata.write_text(json.dumps({"component": {"group": "kg.aidarbek", "module": "lightweight-smpp", "version": VERSION},
                                           "variants": [{"name": "fixtureArtifacts", "files": [
                                               {"name": path.name, "url": path.name, "size": path.stat().st_size,
                                                "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
                                               for path in published]}]}))

    def archive(self, path, entries, licensing=True):
        path.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(path, "w") as archive:
            if licensing:
                for name in ("LICENSE", "NOTICE"):
                    archive.writestr("META-INF/" + name, (self.root / name).read_bytes())
            for name, data in entries.items():
                archive.writestr(name, data)

    def test_accepts_complete_isolated_bundle(self):
        report = CHECKER.check(self.root, VERSION)
        self.assertEqual(VERSION, report["version"])
        self.assertEqual(7, len(report["artifacts"]))

    def test_rejects_unattributed_binary(self):
        self.archive(self.library, {"kg/aidarbek/smpp/Demo.class": b"compiled library"}, False)
        with self.assertRaisesRegex(ValueError, "LICENSE"):
            CHECKER.check(self.root, VERSION)

    def test_rejects_test_or_external_class_leak(self):
        with ZipFile(self.library, "a") as archive:
            archive.writestr("example/external/Unexpected.class", b"external")
        with self.assertRaisesRegex(ValueError, "class|production"):
            CHECKER.check(self.root, VERSION)

    def test_rejects_mixed_source_and_binary_entries(self):
        licensing = {"META-INF/" + name: (self.root / name).read_bytes()
                     for name in ("LICENSE", "NOTICE")}
        source = self.library.with_name(f"lightweight-smpp-{VERSION}-sources.jar")
        examples = ((self.library, ".class", {"kg/aidarbek/smpp/Demo.class": b"compiled library"},
                     "external/Unexpected.java", b"unexpected source"),
                    (source, ".java", {"kg/aidarbek/smpp/Demo.java":
                                      (self.root / "src/main/java/kg/aidarbek/smpp/Demo.java").read_bytes()},
                     "external/Unexpected.class", b"unexpected binary"))
        for path, suffix, expected, extra, data in examples:
            with self.subTest(archive=path.name):
                with ZipFile(path, "a") as archive:
                    archive.writestr(extra, data)
                with self.assertRaisesRegex(ValueError, "Mixed source and binary"):
                    CHECKER.inspect_archive(path, licensing, expected, suffix)

    def test_rejects_declared_runtime_dependency(self):
        self.pom.write_text(self.pom.read_text().replace("</project>", "<dependencies><dependency><groupId>external</groupId></dependency></dependencies></project>"))
        with self.assertRaisesRegex(ValueError, "dependenc"):
            CHECKER.check(self.root, VERSION)

    def test_rejects_missing_required_central_metadata(self):
        original = self.pom.read_text()
        for tag in ("name", "description", "url", "developers", "scm"):
            with self.subTest(field=tag):
                document = ElementTree.fromstring(original)
                document.remove(document.find("{*}" + tag))
                self.pom.write_bytes(ElementTree.tostring(document))
                with self.assertRaisesRegex(ValueError, "POM"):
                    CHECKER.check(self.root, VERSION)

    def test_rejects_incomplete_developer_scm_and_license_metadata(self):
        original = self.pom.read_text()
        for path in ("developers/developer/name", "developers/developer/email", "scm/connection",
                     "scm/developerConnection", "scm/url", "licenses/license/url"):
            with self.subTest(field=path):
                document = ElementTree.fromstring(original)
                document.find("/".join("{*}" + part for part in path.split("/"))).text = "   "
                self.pom.write_bytes(ElementTree.tostring(document))
                with self.assertRaisesRegex(ValueError, "POM"):
                    CHECKER.check(self.root, VERSION)

    def test_rejects_snapshot_as_a_release_version(self):
        snapshot = "0.1.0-SNAPSHOT"
        for path in list(self.root.rglob("*")):
            if path.is_file() and VERSION in path.name:
                path.rename(path.with_name(path.name.replace(VERSION, snapshot)))
        for path in (self.pom, self.metadata):
            path.write_text(path.read_text().replace(VERSION, snapshot))
        with self.assertRaisesRegex(ValueError, "release version"):
            CHECKER.check(self.root, snapshot)

    def test_accepts_explicit_alternative_publication_group(self):
        group = "io.github.aidarbekmetinov"
        self.pom.write_text(self.pom.read_text().replace("kg.aidarbek", group))
        self.metadata.write_text(self.metadata.read_text().replace("kg.aidarbek", group))
        self.assertEqual(group, CHECKER.check(self.root, VERSION, group)["group"])

    def test_failed_cli_check_removes_an_earlier_success_report(self):
        report = self.root / "artifacts.json"
        command = [sys.executable, str(SPEC.origin), "--root", str(self.root), "--version", VERSION,
                   "--output", str(report)]
        subprocess.run(command, check=True, capture_output=True, timeout=30)
        self.assertTrue(report.exists())
        self.archive(self.library, {"kg/aidarbek/smpp/Demo.class": b"compiled library"}, False)

        result = subprocess.run(command, capture_output=True, timeout=30)

        self.assertNotEqual(0, result.returncode)
        self.assertFalse(report.exists(), "A failed artifact check must not leave stale success evidence")

    def test_rejects_unexpected_simulator_dependency(self):
        self.archive(self.distribution / "lib/unexpected.jar", {}, False)
        with self.assertRaisesRegex(ValueError, "runtime"):
            CHECKER.check(self.root, VERSION)

    def test_closes_every_hash_input(self):
        with warnings.catch_warnings(record=True) as recorded:
            warnings.simplefilter("always", ResourceWarning)
            CHECKER.check(self.root, VERSION)
        self.assertEqual([], [str(item.message) for item in recorded if item.category is ResourceWarning])

    def test_rejects_dependency_in_gradle_metadata(self):
        metadata = json.loads(self.metadata.read_text())
        metadata["variants"][0]["dependencies"] = [{"group": "external", "module": "unexpected"}]
        self.metadata.write_text(json.dumps(metadata))
        with self.assertRaisesRegex(ValueError, "dependenc"):
            CHECKER.check(self.root, VERSION)

    def test_rejects_incomplete_gradle_artifact_set(self):
        metadata = json.loads(self.metadata.read_text())
        metadata["variants"][0]["files"].pop()
        self.metadata.write_text(json.dumps(metadata))
        with self.assertRaisesRegex(ValueError, "artifact"):
            CHECKER.check(self.root, VERSION)

    def test_rejects_incorrect_gradle_artifact_identity(self):
        original = self.metadata.read_text()
        for key, value in (("url", "other.jar"), ("sha256", "0" * 64), ("size", 1)):
            with self.subTest(field=key):
                metadata = json.loads(original)
                metadata["variants"][0]["files"][0][key] = value
                self.metadata.write_text(json.dumps(metadata))
                with self.assertRaisesRegex(ValueError, "artifact"):
                    CHECKER.check(self.root, VERSION)


if __name__ == "__main__":
    unittest.main()
