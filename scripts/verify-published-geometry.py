#!/usr/bin/env python3
"""Compare the Geometry JAR used by Maven with the published runtime ZIP."""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path, PurePosixPath
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parents[1]
GEOMETRY_ROOT = PurePosixPath("plugins/misc/hop-geometry-type")


def geometry_version(pom: Path = ROOT / "pom.xml") -> str:
    value = ET.parse(pom).getroot().findtext(
        "{*}properties/{*}hop.geometry.type.version"
    )
    if not value or not value.strip():
        raise ValueError("Missing hop.geometry.type.version in the root POM")
    return value.strip()


def digest(stream) -> str:
    result = hashlib.sha256()
    for block in iter(lambda: stream.read(1024 * 1024), b""):
        result.update(block)
    return result.hexdigest()


def verify(zip_directory: Path, maven_repository: Path, version: str) -> str:
    zips = sorted(zip_directory.rglob("hop-geometry-type-plugin*.zip"))
    if len(zips) != 1:
        raise ValueError(f"Expected exactly one published Geometry ZIP, found {len(zips)}")
    jar = maven_repository / "ch/so/agi/hop-geometry-type" / version / f"hop-geometry-type-{version}.jar"
    if not jar.is_file():
        raise ValueError(f"Missing resolved Maven Geometry JAR: {jar}")
    if not jar.with_suffix(".pom").is_file():
        raise ValueError(f"Missing resolved Maven Geometry POM: {jar.with_suffix('.pom')}")
    with jar.open("rb") as stream:
        actual = digest(stream)
    with zipfile.ZipFile(zips[0]) as archive:
        if archive.testzip() is not None:
            raise ValueError(f"Corrupt Geometry ZIP: {zips[0]}")
        entries = [
            entry for entry in archive.infolist()
            if PurePosixPath(entry.filename).parent == GEOMETRY_ROOT
            and PurePosixPath(entry.filename).match("hop-geometry-type-*.jar")
            and not entry.is_dir()
        ]
        if len(entries) != 1:
            raise ValueError(f"Expected exactly one Geometry JAR in the published ZIP, found {len(entries)}")
        with archive.open(entries[0]) as stream:
            expected = digest(stream)
    if actual != expected:
        raise ValueError(
            f"Published Geometry JAR/ZIP mismatch for {version}: Maven SHA-256 {actual}; "
            f"ZIP SHA-256 {expected}. Re-run after the Geometry publication is complete."
        )
    print(f"Published Geometry {version}: Maven JAR matches runtime ZIP (SHA-256 {actual})")
    return actual


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--zip-directory", type=Path, required=True)
    parser.add_argument("--maven-repository", type=Path, required=True)
    args = parser.parse_args()
    try:
        verify(args.zip_directory, args.maven_repository, geometry_version())
    except (ValueError, OSError, zipfile.BadZipFile, RuntimeError) as error:
        parser.exit(1, f"Geometry dependency verification failed: {error}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
