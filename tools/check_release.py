"""Verify local release artifacts without publishing or accessing a network."""
import argparse
import hashlib
import json
from pathlib import Path
import re
from xml.etree import ElementTree
from zipfile import ZipFile
from zipfile import BadZipFile


def require(condition, explanation):
    if not condition:
        raise ValueError(explanation)


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def contents(directory, suffix):
    return {path.relative_to(directory).as_posix(): path.read_bytes()
            for path in sorted(directory.rglob("*" + suffix))}


def inspect_archive(path, licensing, expected=None, suffix=None):
    with ZipFile(path) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), f"Duplicate archive entry: {path.name}")
        for name, data in licensing.items():
            require(name in names and archive.read(name) == data, f"Missing or changed {name}: {path.name}")
        require(not any(any(part in name for part in ("kg/aidarbek/smpp/review/", "kg/aidarbek/examples/", "org/junit/"))
                        for name in names), f"Nonproduction content: {path.name}")
        if expected is not None:
            require(not any(name.endswith((".class", ".java")) and not name.endswith(suffix)
                            for name in names), f"Mixed source and binary content: {path.name}")
            actual = {name: archive.read(name) for name in names if name.endswith(suffix)}
            require(actual == expected and actual, f"Unexpected production {suffix} content: {path.name}")
        else:
            require("index.html" in names and not any(name.endswith((".class", ".java")) for name in names),
                    f"Invalid Javadoc archive: {path.name}")


def check(root, version):
    root = Path(root).resolve()
    require(re.fullmatch(r"[0-9][A-Za-z0-9.+-]*", version) is not None, "Invalid release version")
    licensing = {"META-INF/" + name: (root / name).read_bytes() for name in ("LICENSE", "NOTICE")}
    libraries = root / "build/libs"
    binary = libraries / f"lightweight-smpp-{version}.jar"
    source = libraries / f"lightweight-smpp-{version}-sources.jar"
    javadoc = libraries / f"lightweight-smpp-{version}-javadoc.jar"
    inspect_archive(binary, licensing, contents(root / "build/classes/java/main", ".class"), ".class")
    inspect_archive(source, licensing, contents(root / "src/main/java", ".java"), ".java")
    inspect_archive(javadoc, licensing)
    distribution = root / "simulator/build/install/simulator"
    runtime = distribution / "lib"
    simulator = runtime / f"simulator-{version}.jar"
    histogram = runtime / "HdrHistogram-2.2.2.jar"
    require({path.name for path in runtime.glob("*.jar")} == {binary.name, simulator.name, histogram.name},
            "Unexpected simulator runtime dependencies")
    require((runtime / binary.name).read_bytes() == binary.read_bytes(), "Simulator uses a different library binary")
    inspect_archive(simulator, licensing, contents(root / "simulator/build/classes/java/main", ".class"), ".class")
    for name in ("LICENSE", "NOTICE"):
        require((distribution / name).read_bytes() == (root / name).read_bytes(), f"Missing distribution {name}")
    with ZipFile(histogram) as archive:
        require("META-INF/LICENSE.txt" in archive.namelist(), "Missing HdrHistogram license")
    pom = root / "build/publications/mavenJava/pom-default.xml"
    document = ElementTree.parse(pom).getroot()
    for tag, expected in (("groupId", "kg.aidarbek"), ("artifactId", "lightweight-smpp"), ("version", version)):
        require(document.findtext("{*}" + tag) == expected, f"Incorrect POM {tag}")
    require(not document.findall(".//{*}dependency"), "Library POM declares dependencies")
    require(document.findtext("{*}licenses/{*}license/{*}name") == "Apache License, Version 2.0", "Missing Apache POM license")
    metadata = pom.with_name("module.json")
    module = json.loads(metadata.read_text(encoding="utf-8"))
    component = module.get("component", {})
    require(all(component.get(key) == value for key, value in
                (("group", "kg.aidarbek"), ("module", "lightweight-smpp"), ("version", version))),
            "Incorrect Gradle publication identity")
    variants = module.get("variants", [])
    require(variants and all(not item.get("dependencies") and not item.get("dependencyConstraints") for item in variants),
            "Gradle metadata declares library dependencies")
    candidates = {path.name: path for path in (binary, source, javadoc)}
    published_names = set()
    for variant in variants:
        for artifact in variant.get("files", []):
            require(artifact.get("name") in candidates, "Unexpected Gradle publication artifact")
            path = candidates[artifact["name"]]
            require(artifact.get("url") == path.name, "Incorrect Gradle artifact URL")
            require(artifact.get("sha256") == digest(path) and artifact.get("size") == path.stat().st_size,
                    "Gradle metadata artifact hash or size differs")
            published_names.add(path.name)
    require(published_names == set(candidates), "Incomplete Gradle publication artifact set")
    artifacts = (binary, source, javadoc, simulator, histogram, pom, metadata)
    return {"version": version, "libraryRuntimeDependencies": [],
            "simulatorRuntimeDependencies": [f"lightweight-smpp:{version}", "org.hdrhistogram:HdrHistogram:2.2.2"],
            "artifacts": {path.relative_to(root).as_posix(): {"bytes": path.stat().st_size, "sha256": digest(path)}
                          for path in artifacts}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--version", required=True)
    parser.add_argument("--output", type=Path)
    arguments = parser.parse_args()
    try:
        report = check(arguments.root, arguments.version)
    except (ValueError, OSError, BadZipFile, ElementTree.ParseError) as failure:
        parser.exit(1, f"Release verification failed: {failure}\n")
    encoded = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if arguments.output:
        arguments.output.parent.mkdir(parents=True, exist_ok=True)
        arguments.output.write_text(encoded, encoding="utf-8")
    else:
        print(encoded, end="")


if __name__ == "__main__":
    main()
