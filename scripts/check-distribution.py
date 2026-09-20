#!/usr/bin/env python3
from io import BytesIO
from pathlib import Path
import zipfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
target = root / "assemblies" / "assemblies-hop-vector-raster" / "target"
zips = sorted(target.glob("hop-vector-raster-plugin-*.zip"))
if len(zips) != 1:
    raise SystemExit(f"Expected exactly one plugin ZIP in {target}, found {len(zips)}")

zip_path = zips[0]
with zipfile.ZipFile(zip_path) as archive:
    entries = [name for name in archive.namelist() if not name.endswith("/")]
    names = [Path(name).name.lower() for name in entries]
    if len(names)!=len(set(names)):
        raise SystemExit("Duplicate distribution filenames")
    if any(not entry.startswith("plugins/transforms/vector-raster/") for entry in entries):
        raise SystemExit("Unexpected plugin installation path")

    required = [
        "hop-vector-transforms-",
        "hop-vector-core-",
        "hop-vector-format-shapefile-",
        "hop-vector-format-flatgeobuf-", "hop-vector-format-parquet-", "flatgeobuf-", "parquet-hadoop-",
        "hop-vector-format-geopackage-",
        "hop-vector-format-filegeodatabase-",
        "filegdb4j-core-",
        "filegdb4j-geometry-",
        "filegdb4j-jts-",
        "hop-geotools-support-",
        "hop-raster-values-",
        "hop-raster-geotools-",
        "hop-vector-format-generate-",
        "sqlite-jdbc-",
    ]
    for fragment in required:
        if not any(fragment in name for name in names):
            raise SystemExit(f"{zip_path.name}: required dependency matching {fragment!r} is missing")

    dependencies = ET.fromstring(archive.read("plugins/transforms/vector-raster/dependencies.xml"))
    folders = {node.text for node in dependencies.findall("folder")}
    if folders != {"../../misc/hop-geometry-type", "../../misc/hop-raster-type", "../../misc/hop-raster-type/lib"}:
        raise SystemExit("Geometry Type and Raster Type dependencies must be explicit")

    forbidden = [
        "hop-geometry-type-", "hop-raster-type-", "hop-raster-core-", "jts-core-",
        "gt-", "imageio-ext-", "indriya-", "unit-api-", "systems-common-", "uom-", "si-",
        "gt-shapefile-", "gt-geopkg-", "gt-jdbc-", "hop-transform-arcinfo-generate-writer-",
        "hadoop-common-", "hadoop-client-", "hadoop-mapreduce-", "snappy-java-", "zstd-jni-",
        "gdal", "ogr-", "kakadu", "turbojpeg", "imageio-ext-gdal",
    ]
    for fragment in forbidden:
        matches = [name for name in names if fragment in name]
        if matches:
            raise SystemExit(
                f"{zip_path.name}: dependency {fragment!r} must not be bundled: {matches}"
            )

    # SQLite JDBC's bundled natives are intentional; no shared raster/vector runtime is allowed.
    for entry in entries:
        if not entry.endswith(".jar"):
            continue
        with zipfile.ZipFile(BytesIO(archive.read(entry))) as nested:
            if "META-INF/registryFile.imagen" in nested.namelist():
                raise SystemExit(
                    f"Imagen registry metadata must come from Geometry Type, not {entry}"
                )
            if Path(entry).name.startswith(("hop-vector-core-", "hop-vector-format-shapefile-", "hop-vector-format-geopackage-", "hop-vector-format-filegeodatabase-", "hop-vector-format-generate-", "hop-vector-format-flatgeobuf-", "hop-vector-format-parquet-", "hop-vector-transforms-")):
                for name in nested.namelist():
                    if name.endswith(".class") and b"org/geotools/" in nested.read(name):
                        raise SystemExit(f"GeoTools type leaked into neutral vector module: {entry}:{name}")
            required_content = []
            if Path(entry).name.startswith("hop-vector-transforms-"):
                required_content = [
                    "ch/so/agi/hop/vector/transforms/FileGdbWriter.class",
                    "ch/so/agi/hop/vector/transforms/FileGdbWriterDialog.class",
                    "ch/so/agi/hop/vector/transforms/FileGdbCatalogReader.class",
                    "ch/so/agi/hop/vector/transforms/FileGdbCatalogReaderDialog.class",
                ]
            elif Path(entry).name.startswith("hop-vector-format-filegeodatabase-"):
                required_content = [
                    "ch/so/agi/hop/vector/formats/filegeodatabase/export-schema-v1.json",
                ]
            for name in required_content:
                if name not in nested.namelist():
                    raise SystemExit(f"Missing FileGDB content in {entry}: {name}")
            if any("ArcInfoGenerateWriter" in name for name in nested.namelist()):
                raise SystemExit(f"Legacy separate GENERATE transform in {entry}")
            natives = [n for n in nested.namelist() if n.lower().endswith((".dll", ".so", ".dylib", ".jnilib"))]
            if natives and not Path(entry).name.startswith("sqlite-jdbc-"):
                raise SystemExit(f"Unexpected native library in {entry}: {natives}")

size_mib = zip_path.stat().st_size / (1024 * 1024)
print(f"Distribution OK: {zip_path} ({size_mib:.1f} MiB)")
print("  Native Java Shapefile + Geometry Type supplied GeoTools/Imagen runtime; GeoPackage uses SQLite JDBC")
print("  File geodatabase support is pure Java via filegdb4j, no GDAL bindings")
print("  Geometry, JTS, GeoTools, Imagen, ImageIO-Ext and UOM are supplied only by Geometry Type")
print("  No GeoTools/Imagen registry metadata or GDAL bindings; SQLite JDBC natives are allowed")
