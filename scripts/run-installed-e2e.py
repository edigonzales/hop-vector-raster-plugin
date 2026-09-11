#!/usr/bin/env python3
"""Run the vector/raster smoke against a clean Hop installation and ZIP."""

from __future__ import annotations

import argparse
import csv
import json
from contextlib import nullcontext
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile


def jars(root: Path) -> list[Path]:
    return sorted(path for path in root.rglob("*.jar") if path.is_file())


def run_command(command: list[str], env: dict[str, str], timeout: int = 180) -> None:
    print("==>", command[0], "...", flush=True)
    if os.name == "nt" and command[0].lower().endswith(".bat"):
        command = ["cmd.exe", "/d", "/s", "/c", subprocess.list2cmdline(command)]
    log_dir = Path(env["E2E_LOG_DIR"])
    log = log_dir / f"command-{len(list(log_dir.glob('command-*.log'))):02d}.log"
    if Path(command[0]).stem in {"java", "javac"}:
        argfile = log.with_suffix(".args")
        argfile.write_text("\n".join(json.dumps(arg) for arg in command[1:]), encoding="utf-8")
        command = [command[0], "@" + str(argfile)]
    with log.open("w", encoding="utf-8") as output:
        result = subprocess.run(command, env=env, cwd=env.get("E2E_CWD"),
                                stdout=output, stderr=subprocess.STDOUT, timeout=timeout)
    if result.returncode:
        print(log.read_text(encoding="utf-8", errors="replace")[-20000:])
    result.check_returncode()


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
    parser.add_argument("--work-dir", type=Path, help="Retain fixtures and logs here, including on failure")
    args = parser.parse_args()
    args.hop_home = args.hop_home.resolve()
    if not (args.hop_home / "hop-run.sh").is_file():
        raise SystemExit(f"Not an Apache Hop home: {args.hop_home}")
    if not args.plugin_zip.is_file():
        raise SystemExit(f"Plugin ZIP does not exist: {args.plugin_zip}")
    if not args.geometry_zip.is_file():
        raise SystemExit(f"Geometry snapshot ZIP does not exist: {args.geometry_zip}")
    for plugin_root in (
        args.hop_home / "plugins/misc/hop-geometry-type",
        args.hop_home / "plugins/transforms/vector-raster",
    ):
        if plugin_root.exists():
            raise SystemExit(f"Hop home is not clean; plugin directory already exists: {plugin_root}")

    if args.work_dir:
        args.work_dir = args.work_dir.resolve()
        args.work_dir.mkdir(parents=True, exist_ok=True)
    context = nullcontext(str(args.work_dir)) if args.work_dir else tempfile.TemporaryDirectory(prefix="hop-vector-raster-installed-e2e-")
    with context as temp:
        work = Path(temp)
        config = work / "config"
        audit = work / "audit"
        classes = work / "classes"
        data = work / "data"
        config.mkdir()
        audit.mkdir()
        classes.mkdir()
        data.mkdir()
        run_config = config / "metadata/pipeline-run-configuration/local.json"
        run_config.parent.mkdir(parents=True)
        run_config.write_text(
            '{\n'
            '  "name": "local",\n'
            '  "engineRunConfiguration": {"Local": {"rowset_size": "2", "safe_mode": true}},\n'
            '  "configurationVariables": []\n'
            '}\n',
            encoding="utf-8",
        )
        env = os.environ.copy()
        env["E2E_LOG_DIR"] = str(work)
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

        run_command([str(javac), "-proc:none", "-cp", classpath, "-d", str(classes), str(source)], env)
        java_smoke = [
            str(java), "-cp", str(classes) + os.pathsep + classpath,
            "DocumentationExamplesSmoke", str(Path(__file__).parents[1]), str(data),
        ]
        run_command(java_smoke + ["prepare"], env)

        # Probe the real plugin classloader; never put plugin JARs on its initial classpath.
        hop_classpath = os.pathsep.join(str(path) for path in jars(args.hop_home / "lib"))
        probe = Path(__file__).with_name("RuntimeIdentityProbe.java")
        run_command([str(javac), "-proc:none", "-cp", hop_classpath, "-d", str(classes), str(probe)], env)
        probe_env = dict(env, E2E_CWD=str(args.hop_home))
        for order in ("raster-first", "geometry-first"):
            run_command([str(java), "-cp", str(classes) + os.pathsep + hop_classpath,
                         "RuntimeIdentityProbe", order], probe_env)
        hop_run = str(args.hop_home / ("hop-run.bat" if os.name == "nt" else "hop-run.sh"))
        run_command([
            hop_run, "-r", "local", "-f",
            str(Path(__file__).parents[1] / "examples/raster-clip/clip-cog.hpl"),
            "-p", f"INPUT_RASTER={data / 'input.tif'}",
            "-p", f"OUTPUT_FILE={data / 'clip.tif'}",
            "-p", "BBOX_CRS=EPSG:2056", "-p", "MIN_X=2600001", "-p", "MIN_Y=1200001",
            "-p", "MAX_X=2600003", "-p", "MAX_Y=1200003", "-p", "NODATA=255",
        ], env)
        run_command([
            hop_run, "-r", "local", "-f",
            str(Path(__file__).parents[1] / "scripts/e2e/zonal-statistics-to-csv.hpl"),
            "-p", f"INPUT_VECTOR={data / 'zones.gpkg'}", "-p", "INPUT_LAYER=zones",
            "-p", f"INPUT_RASTER={data / 'input.tif'}", "-p", "GEOMETRY_CRS=EPSG:2056",
            "-p", "NODATA=255", "-p", f"OUTPUT_FILE={data / 'zonal.csv'}",
        ], env)
        run_command([
            hop_run, "-r", "local", "-f",
            str(Path(__file__).parents[1] / "examples/cloud-output/vector-to-flatgeobuf.hpl"),
            "-p", f"INPUT_VECTOR={data / 'zones.gpkg'}", "-p", "INPUT_LAYER=zones",
            "-p", f"OUTPUT_FILE={data / 'zones.fgb'}",
        ], env)
        run_command([
            hop_run, "-r", "local", "-f",
            str(Path(__file__).parents[1] / "examples/cloud-output/vector-to-parquet.hpl"),
            "-p", f"INPUT_VECTOR={data / 'zones.gpkg'}", "-p", "INPUT_LAYER=zones",
            "-p", f"OUTPUT_FILE={data / 'zones.parquet'}",
        ], env)

        run_command(java_smoke + ["check"], env)
        with (data / "zonal.csv").open(newline="", encoding="utf-8") as stream:
            rows = list(csv.DictReader(stream, delimiter=";"))
        if len(rows) != 1:
            raise SystemExit(f"Expected exactly one zonal-statistics row, got {len(rows)}")
        row = rows[0]
        expected = {"raster_mean": 5.5, "raster_min": 0.0, "raster_max": 11.0, "raster_count": 12.0}
        for name, value in expected.items():
            if float(row[name]) != value:
                raise SystemExit(f"Unexpected {name}: {row[name]!r}")
        if row["name"] != "whole raster":
            raise SystemExit(f"Input attribute was not preserved: {row['name']!r}")
        for output in (data / "zones.fgb", data / "zones.parquet"):
            if not output.is_file() or output.stat().st_size <= 4:
                raise SystemExit(f"Installed Hop did not create {output.name}")
        if (data / "zones.parquet").read_bytes()[:4] != b"PAR1":
            raise SystemExit("Unexpected zones.parquet header")

    print("Installed Hop vector/raster E2E OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
