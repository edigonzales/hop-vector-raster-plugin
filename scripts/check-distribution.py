#!/usr/bin/env python3
from io import BytesIO
from pathlib import Path
import zipfile

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
        "hop-geotools-support-",
        "hop-raster-core-",
        "hop-raster-clip-",
        "hop-raster-reproject-",
        "hop-raster-zonal-stats-",
        "hop-vector-format-generate-",
        "jts-core-",
        "gt-geotiff-",
        "gt-coverage-",
        "imageio-ext-cog-reader-",
        "imageio-ext-cog-streams-",
        "gt-main-",

        "gt-epsg-hsql-",
        "sqlite-jdbc-",
        "imagen-core-",
        "indriya-",
        "systems-common-",
        "unit-api-",
    ]
    for fragment in required:
        if not any(fragment in name for name in names):
            raise SystemExit(f"{zip_path.name}: required dependency matching {fragment!r} is missing")

    forbidden = [
        "hop-geometry-type",
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

    # Explicitly reviewed GeoTools modules; none originate in modules/unsupported.
    # Additions require reviewing their source module and runtime requirements.
    allowed_geotools = {
        "gt-api", "gt-main", "gt-metadata", "gt-referencing", "gt-http",
        "gt-epsg-hsql", "gt-coverage",
        "gt-geotiff", "gt-xml", "gt-xsd-core", "gt-xsd-ows", "gt-xsd-gml2",
        "gt-xsd-gml3", "gt-xsd-filter", "gt-xsd-fes",
    }
    for name in names:
        if name.startswith("gt-") and name.endswith(".jar"):
            if name.removesuffix("-35.1.jar") not in allowed_geotools:
                raise SystemExit(f"Unreviewed GeoTools module or version (expected 35.1): {name}")
    # SQLite JDBC's bundled natives are intentional; no raster native runtime is allowed.
    for entry in entries:
        if not entry.endswith(".jar"):
            continue
        with zipfile.ZipFile(BytesIO(archive.read(entry))) as nested:
            if Path(entry).name.startswith(("hop-vector-core-", "hop-vector-format-shapefile-", "hop-vector-format-geopackage-", "hop-vector-format-generate-", "hop-vector-format-flatgeobuf-", "hop-vector-format-parquet-", "hop-vector-transforms-")):
                for name in nested.namelist():
                    if name.endswith(".class") and b"org/geotools/" in nested.read(name):
                        raise SystemExit(f"GeoTools type leaked into neutral vector module: {entry}:{name}")
            if any("ArcInfoGenerateWriter" in name for name in nested.namelist()):
                raise SystemExit(f"Legacy separate GENERATE transform in {entry}")
            natives = [n for n in nested.namelist() if n.lower().endswith((".dll", ".so", ".dylib", ".jnilib"))]
            if natives and not Path(entry).name.startswith("sqlite-jdbc-"):
                raise SystemExit(f"Unexpected native library in {entry}: {natives}")

    indriya_entries = [
        entry for entry in entries if Path(entry).name.lower().startswith("indriya-") and entry.endswith(".jar")
    ]
    if len(indriya_entries) != 1:
        raise SystemExit(
            f"{zip_path.name}: expected exactly one Indriya runtime JAR, found {indriya_entries}"
        )

    service_path = "META-INF/services/tech.units.indriya.spi.NumberSystem"
    with archive.open(indriya_entries[0]) as nested_file:
        with zipfile.ZipFile(BytesIO(nested_file.read())) as nested:
            if service_path not in nested.namelist():
                raise SystemExit(
                    f"{zip_path.name}: {indriya_entries[0]} is missing {service_path}"
                )
            provider = nested.read(service_path).decode("utf-8").strip()
            if "tech.units.indriya.function.DefaultNumberSystem" not in provider.splitlines():
                raise SystemExit(
                    f"{zip_path.name}: Indriya NumberSystem service does not declare DefaultNumberSystem"
                )

size_mib = zip_path.stat().st_size / (1024 * 1024)
print(f"Distribution OK: {zip_path} ({size_mib:.1f} MiB)")
print("  Native Java Shapefile + GeoTools 35.1 raster + EPSG runtime; GeoPackage uses SQLite JDBC")
print("  Indriya NumberSystem service metadata is present")
print("  hop-geometry-type remains shared via classLoaderGroup=sogeo-geometry; JTS is bundled for raster startup")
print("  No unsupported GeoTools modules or GDAL bindings; SQLite JDBC natives are allowed")
