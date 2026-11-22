#!/usr/bin/env python3
"""Compare Maven's published Geometry JAR with the installed runtime ZIP."""
import argparse
from pathlib import Path, PurePosixPath
import xml.etree.ElementTree as ET
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--geometry-zip", type=Path, required=True)
parser.add_argument("--maven-repository", type=Path, required=True)
args = parser.parse_args()

pom = Path(__file__).resolve().parents[1] / "pom.xml"
version = ET.parse(pom).getroot().findtext("{*}properties/{*}hop.geometry.type.version")
jar = args.maven_repository / "ch/so/agi/hop-geometry-type" / version / f"hop-geometry-type-{version}.jar"
plugin_root = PurePosixPath("plugins/misc/hop-geometry-type")
with zipfile.ZipFile(args.geometry_zip) as archive:
    entries = [
        entry for entry in archive.infolist()
        if PurePosixPath(entry.filename).parent == plugin_root
        and PurePosixPath(entry.filename).match("hop-geometry-type-*.jar")
    ]
    if len(entries) != 1:
        raise SystemExit("Expected one Geometry JAR in the published runtime ZIP")
    if archive.read(entries[0]) != jar.read_bytes():
        raise SystemExit(f"Published Geometry {version} JAR differs from the runtime ZIP. "
                         "Re-run after the Geometry publication is complete.")
print(f"Published Geometry {version}: Maven JAR matches runtime ZIP")
