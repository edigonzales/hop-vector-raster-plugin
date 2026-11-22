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
    if len(names) != len(set(names)):
        raise SystemExit("Duplicate distribution filenames")
    if any(not entry.startswith("plugins/transforms/vector-raster/") for entry in entries):
        raise SystemExit("Unexpected plugin installation path")

    required = [
        "hop-vector-transforms-",
        "hop-vector-core-",
        "hop-vector-format-shapefile-",
        "hop-vector-format-flatgeobuf-", "hop-vector-format-parquet-", "flatgeobuf-", "parquet-hadoop-",
        "hop-vector-format-geopackage-",
        "hop-vector-format-geojson-",
        "hop-vector-format-filegeodatabase-",
        "filegdb4j-core-",
        "filegdb4j-geometry-",
        "filegdb4j-jts-",
        "hop-geotools-support-",
        "hop-raster-values-",
        "hop-vector-format-generate-",
        "sqlite-jdbc-",
    ]
    for fragment in required:
        if not any(fragment in name for name in names):
            raise SystemExit(f"{zip_path.name}: required dependency matching {fragment!r} is missing")

    for prefix in ("hop-plugin-commons-ui-", "hop-plugin-commons-core-", "hop-raster-geotools-"):
        matches = [entry for entry in entries if Path(entry).name.lower().startswith(prefix)]
        if len(matches) != 1 or Path(matches[0]).parent.as_posix() != "plugins/transforms/vector-raster/lib":
            raise SystemExit(f"{prefix} must be packaged exactly once in the plugin lib directory")

    dependencies = ET.fromstring(archive.read("plugins/transforms/vector-raster/dependencies.xml"))
    folders = {node.text for node in dependencies.findall("folder")}
    if folders != {"../../misc/hop-raster-type", "../../misc/hop-raster-type/lib"}:
        raise SystemExit("Raster Type dependencies must be explicit")

    forbidden = [
        "hop-core-", "hop-engine-", "hop-ui-", "org.eclipse.swt",
        "hop-geometry-type-", "hop-raster-type-", "hop-raster-core-", "jts-core-",
        "gt-", "imageio-ext-", "indriya-", "unit-api-", "systems-common-", "uom-", "si-",
        "hop-transform-arcinfo-generate-writer-",
        "hadoop-common-", "hadoop-client-", "hadoop-mapreduce-", "snappy-java-", "zstd-jni-",
        "gdal", "ogr-", "kakadu", "turbojpeg",
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
            contents = nested.namelist()
            jar_name = Path(entry).name
            if "META-INF/registryFile.imagen" in contents:
                raise SystemExit(f"Imagen registry metadata must come from Geometry Type, not {entry}")
            if jar_name.startswith(("hop-vector-core-", "hop-vector-format-", "hop-vector-transforms-")):
                for name in contents:
                    if name.endswith(".class") and b"org/geotools/" in nested.read(name):
                        raise SystemExit(f"GeoTools type leaked into neutral vector module: {entry}:{name}")
            if jar_name.startswith("hop-vector-transforms-"):
                # Installed pipelines run headless, so check the packaged SWT dialogs here.
                for dialog in (
                    "GeoJsonFeatureDecoderDialog", "GeoJsonFeatureEncoderDialog",
                    "FileGdbWriterDialog", "FileGdbCatalogReaderDialog",
                ):
                    if f"ch/so/agi/hop/vector/transforms/{dialog}.class" not in contents:
                        raise SystemExit(f"Missing packaged dialog {dialog} in {entry}")
            if jar_name.startswith("hop-vector-format-filegeodatabase-"):
                schema = "ch/so/agi/hop/vector/formats/filegeodatabase/export-schema-v1.json"
                if schema not in contents:
                    raise SystemExit(f"Missing FileGDB export schema in {entry}")
            if jar_name.startswith("hop-raster-values-"):
                legacy_ids = (
                    b"SOGIS_RASTER_CLIP",
                    b"SOGIS_RASTER_REPROJECT",
                    b"SOGIS_RASTER_ZONAL_STATS",
                )
                for name in contents:
                    if name.endswith(".class"):
                        content = nested.read(name)
                        if any(legacy_id in content for legacy_id in legacy_ids):
                            raise SystemExit(
                                f"Legacy raster transform ID remains in {entry}:{name}"
                            )
            natives = [n for n in contents if n.lower().endswith((".dll", ".so", ".dylib", ".jnilib"))]
            if natives and not jar_name.startswith("sqlite-jdbc-"):
                raise SystemExit(f"Unexpected native library in {entry}: {natives}")

size_mib = zip_path.stat().st_size / (1024 * 1024)
print(f"Distribution OK: {zip_path} ({size_mib:.1f} MiB)")
