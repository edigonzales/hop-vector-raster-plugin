#!/usr/bin/env python3
"""Run the vector/raster smoke against a clean Hop installation and ZIP."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile


def jars(root: Path) -> list[Path]:
    return sorted(path for path in root.rglob("*.jar") if path.is_file())


def extract_plugin(zip_path: Path, hop_home: Path, plugin_root: str) -> None:
    required_root = plugin_root.rstrip("/") + "/"
    with zipfile.ZipFile(zip_path) as archive:
        if archive.testzip() is not None:
            raise SystemExit(f"Corrupt plugin ZIP: {zip_path}")
        entries = archive.namelist()
        if not any(name.startswith(required_root) for name in entries):
            raise SystemExit(f"{zip_path.name} lacks required installation root {required_root}")
        for name in entries:
            path = Path(name)
            if path.is_absolute() or ".." in path.parts:
                raise SystemExit(f"Unsafe ZIP entry {name!r} in {zip_path.name}")
        archive.extractall(hop_home)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--hop-home", required=True, type=Path)
    parser.add_argument("--plugin-zip", required=True, type=Path)
    parser.add_argument("--geometry-zip", required=True, type=Path)
    args = parser.parse_args()
    if not (args.hop_home / "hop-run.sh").is_file():
        raise SystemExit(f"Not an Apache Hop home: {args.hop_home}")
    if not args.plugin_zip.is_file():
        raise SystemExit(f"Plugin ZIP does not exist: {args.plugin_zip}")
    if not args.geometry_zip.is_file():
        raise SystemExit(f"Geometry runtime ZIP does not exist: {args.geometry_zip}")
    for plugin_root in (
        args.hop_home / "plugins/misc/hop-geometry-type",
        args.hop_home / "plugins/transforms/vector-raster",
    ):
        if plugin_root.exists():
            raise SystemExit(f"Hop home is not clean; plugin directory already exists: {plugin_root}")

    with tempfile.TemporaryDirectory(prefix="hop-vector-raster-installed-e2e-") as temp:
        work = Path(temp)
        config = work / "config"
        audit = work / "audit"
        classes = work / "classes"
        data = work / "data"
        classes.mkdir()
        data.mkdir()
        env = os.environ.copy()
        env["HOP_CONFIG_FOLDER"] = str(config)
        env["HOP_AUDIT_FOLDER"] = str(audit)
        env["HOP_JAVA_HOME"] = env.get("JAVA_HOME", "")

        extract_plugin(args.geometry_zip, args.hop_home, "plugins/misc/hop-geometry-type")
        extract_plugin(args.plugin_zip, args.hop_home, "plugins/transforms/vector-raster")

        classpath_entries = jars(args.hop_home / "lib") + jars(args.hop_home / "plugins")
        if not classpath_entries:
            raise SystemExit("No Hop/plugin JARs found for the isolated smoke")
        classpath = os.pathsep.join(str(path) for path in classpath_entries)
        source = Path(__file__).with_name("DocumentationExamplesSmoke.java")
        javac = Path(env.get("JAVA_HOME", "")) / "bin" / "javac"
        java = Path(env.get("JAVA_HOME", "")) / "bin" / "java"
        if not javac.is_file() or not java.is_file():
            javac = Path(shutil.which("javac") or "javac")
            java = Path(shutil.which("java") or "java")

        subprocess.run(
            [str(javac), "-cp", classpath, "-d", str(classes), str(source)],
            check=True,
            env=env,
        )
        subprocess.run(
            [
                str(java),
                "-cp",
                str(classes) + os.pathsep + classpath,
                "DocumentationExamplesSmoke",
                str(Path(__file__).parents[1]),
                str(data),
            ],
            check=True,
            env=env,
            timeout=180,
        )

    print("Installed Hop vector/raster E2E OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
