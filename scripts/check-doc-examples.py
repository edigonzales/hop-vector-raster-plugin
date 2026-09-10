#!/usr/bin/env python3
"""Run the documented HPLs from a clean Apache Hop installation.

The smoke deliberately uses only the installed Hop and plugin ZIPs. In
particular, it must not assemble a classpath from Maven test dependencies,
because that can load a second copy of Imagen/ImageIO service providers.
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]


def first_zip(directory: Path, pattern: str) -> Path | None:
    matches = sorted(directory.glob(pattern)) if directory.is_dir() else []
    return matches[0] if len(matches) == 1 else None


def discover_geometry_zip() -> Path | None:
    candidates = [
        os.environ.get("GEOMETRY_ZIP", ""),
        str(ROOT / ".ci/geometry-dist/hop-geometry-type-plugin-*.zip"),
        str(ROOT.parent / "hop-geometry-type-plugin/assemblies/assemblies-hop-geometry-type/target/hop-geometry-type-plugin-*.zip"),
    ]
    for candidate in candidates:
        if "*" in candidate:
            match = first_zip(Path(candidate).parent, Path(candidate).name)
        else:
            match = Path(candidate) if candidate else None
        if match and match.is_file():
            return match
    return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--hop-home",
        type=Path,
        default=Path(os.environ["HOP_HOME"]) if os.environ.get("HOP_HOME") else None,
        help="Clean Apache Hop home; defaults to HOP_HOME",
    )
    parser.add_argument(
        "--geometry-zip",
        type=Path,
        default=None,
        help="Geometry runtime ZIP; defaults to GEOMETRY_ZIP or local CI/development locations",
    )
    args = parser.parse_args()

    hop_home = args.hop_home
    geometry_zip = args.geometry_zip or discover_geometry_zip()
    plugin_zip = first_zip(
        ROOT / "assemblies/assemblies-hop-vector-raster/target",
        "hop-vector-raster-plugin-*.zip",
    )
    if hop_home is None:
        raise SystemExit("Pass --hop-home or set HOP_HOME to a clean Apache Hop installation")
    if not (hop_home / "hop-run.sh").is_file():
        raise SystemExit(f"Not an Apache Hop home: {hop_home}")
    if geometry_zip is None:
        raise SystemExit("Pass --geometry-zip or set GEOMETRY_ZIP to the geometry runtime ZIP")
    if plugin_zip is None:
        raise SystemExit("Expected exactly one vector/raster plugin ZIP in the assembly target")

    subprocess.run(
        [
            sys.executable,
            str(ROOT / "scripts/run-installed-e2e.py"),
            "--hop-home",
            str(hop_home),
            "--plugin-zip",
            str(plugin_zip),
            "--geometry-zip",
            str(geometry_zip),
        ],
        check=True,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
