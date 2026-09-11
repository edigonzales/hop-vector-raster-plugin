#!/usr/bin/env python3
"""Extract the shared CI inputs and exercise the canonical plugin ZIP."""
from pathlib import Path
import subprocess
import sys
import zipfile

root = Path(__file__).resolve().parents[1]
inputs = root / ".ci/e2e-inputs"
def one(folder, pattern):
    matches = list(folder.rglob(pattern))
    if len(matches) != 1:
        raise SystemExit(f"Expected one {pattern}, found {matches}")
    return matches[0]
hop_root = root / ".ci/e2e-hop"
with zipfile.ZipFile(one(inputs, "hop.zip")) as archive:
    archive.extractall(hop_root)
    for info in archive.infolist():
        path = hop_root / info.filename
        if path.is_file() and info.external_attr >> 16:
            path.chmod(info.external_attr >> 16)
subprocess.run([
    sys.executable, str(root / "scripts/run-installed-e2e.py"),
    "--hop-home", str(hop_root / "hop"),
    "--plugin-zip", str(one(root / ".ci/verified", "hop-vector-raster-plugin-*.zip")),
    "--geometry-zip", str(one(inputs, "hop-geometry-type-plugin.zip")),
    "--work-dir", str(root / ".ci/e2e-results"),
], check=True)
