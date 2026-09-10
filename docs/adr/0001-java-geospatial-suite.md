# Java geospatial suite

Status: implemented 2026-09-08; vector architecture amended 2026-09-09.

## Decision

Provide a Java 17 geospatial alternative that does not require GDAL/OGR bindings or an installed GDAL runtime. GeoTools is pinned to **35.1**, with its platform BOM managing ImageIO-Ext and ImageN. This is a single platform-independent ZIP; it is not a promise that every transitive JAR is native-free. The existing GeoPackage implementation uses SQLite JDBC, including its bundled platform libraries.

Use only maintained GeoTools library/plugin/extension modules. In particular, do not use `modules/unsupported`, `gt-process-raster`, `RasterZonalStatistics`, or `RasterZonalStatistics2`. `scripts/check-distribution.py` checks the packaged GeoTools modules against an explicit reviewed allowlist, their 35.1 version, and embedded native libraries.

The modules are:

- `hop-geotools-support`: unit-system initialization and CRS support.
- `hop-vector-core`: format capabilities, layer/Hop row schemas, streaming source/sink interfaces and neutral CRS definition service.
- `hop-vector-transforms`: common Vector Reader and Writer, including GENERATE settings.
- `hop-vector-format-shapefile`: native Java Shapefile I/O with Z/M and DBF encoding.
- `hop-vector-format-geopackage`: SQLite JDBC schema, attributes, geometry headers and shared WKB/curve codecs; no GeoTools dependency.
- `hop-raster-core`: local/HTTP COG access, bounded original-resolution windows, polygon masks, streaming statistics and GeoTIFF writing.
- `hop-raster-clip`: one input row describes a clip; the original row receives output path/status.
- `hop-raster-reproject`: all-band reprojection/resampling through GeoTools CoverageProcessor, bounded weighted windows, target grid alignment and color/alpha semantics.
- `hop-raster-zonal-stats`: one input polygon row receives statistics, valid count and status.
- `hop-vector-format-generate`: independent Java/JTS encoder and file sink, without any GeoTools dependency.

Use the new installation directory `plugins/transforms/vector-raster` and remove the former `geotools-vector` directory on upgrade. Transform IDs intentionally change without aliases; see [migration](../migration.md). All transforms share `classLoaderGroup=sogeo-geometry`. The Geometry Type plugin supplies JTS and Hop Geometry once; neither JAR is included here. Different raster plugin IDs and explicit `(GeoTools)` display names permit installation alongside GDAL transforms.

## Raster semantics

HTTP access uses the ImageIO-Ext COG SPI with a JDK HTTP Range adapter. Require HTTP 206, valid Content-Range and exact response length; never accept a full-body 200 fallback. Check object length and available ETag/Last-Modified throughout a source lifetime. Use long byte offsets, including BigTIFF offsets beyond 4 GiB. No credentials, cloud SDKs or arbitrary request headers are exposed in v1.

Own one reader per transform copy/current source, close on source change, EOF, error or disposal. Cache raw windows with a 64 MiB limit; cache metadata per source with a 64 MiB ceiling. Source instances are not shared between threads. Statistics read aligned 512-pixel blocks at the original resolution; overviews and resampling are disabled. GeoTools 35.1 needs explicit source-region control and the corresponding translated grid transform for cropped reads; both are covered by tests. Capture NoData during initial metadata reading because GeoTiffReader.getMetadata() cannot reopen COG providers correctly.

Use JTS prepared polygons and pixel-centre inclusion (`covers`: boundary centres count). Support polygons, holes and multipolygons; reject invalid/nonfinite/curved geometries. Reproject a copy using explicit CRS or the shared geometry SRID. Never assume a missing CRS and never mutate the incoming geometry. XY/lon-lat axis order is explicit. Clip aligns to the original grid and preserves its resolution; it does not reproject/resample the raster.

Use raw NoData (or an explicit replacement override), masks and finite-value checks before applying scale/offset. Zero is valid. Accumulate mean and population standard deviation with Welford's algorithm and sum with compensated addition. `count` and `status` always accompany selected statistics. Empty/no-valid zones are normal results with null statistics and zero count; technical failures use Hop error handling or fail the transform.

GeoTIFF clips preserve the selected band's raw data type and scale/offset, georeferencing and NoData. Integer rasters without a NoData declaration require an explicit sentinel. Float rasters can use NaN. GeoTools supplies georeferencing and ImageIO-Ext writes explicit TIFF metadata (including GDAL_METADATA scale/offset; this metadata convention does not require GDAL bindings). Output is tiled/DEFLATE GeoTIFF; it is not advertised as COG. Write to a temporary sibling file and publish only after success.

## Vector adapter boundary

Common transforms use Hop row metadata and the shared Geometry type. They never use `DataStore` or `SimpleFeature`; those remain only in independent test references. GeoPackage requests CRS definitions through `CrsDefinitionResolver`, whose GeoTools implementation stays in the support module. Native SQLite libraries remain intentionally bundled in one cross-platform ZIP. Shapefile now uses native Java with Z/M and DBF encoding support. FlatGeobuf and Parquet remain separate future work packages.

Writers publish their output only after successful EOF. Shapefile stages its sidecar bundle and removes files it published if a move fails; GeoPackage stages one transactional database; GENERATE retains its existing temporary-file lifecycle. Reader/writer instances belong to one transform copy. No append/update or spatial-index feature is added to GeoPackage. Its current geometry contract is XY, including the existing curve types; unsupported Z/M is rejected rather than reduced.

## GENERATE

Use separate explicit XY and XYZ dialects, with strict geometry validation and deterministic ASCII. Classic XY polygon headers contain `ID AUTO`; XYZ polygon headers contain only `ID`. See [GENERATE format decisions](../generate-format.md) and its primary Esri sources. Multipart, holes and curves require explicit upstream handling. Output never silently discards additional ordinates or attributes. Only numeric ID and coordinates are serialized; the pipeline is responsible for CRS selection.

## Validation and future work

Deterministic tests cover local and HTTP BigTIFF, exact pixel statistics, ignored poisoned overviews, remote transfer volume, cache reuse, >4 GiB byte ranges, CRS, NoData/zero/scale/offset, selected bands, output roundtrips, cancellation, GENERATE dialects and Hop metadata/row contracts. Opt-in acceptance tests use the two public Solothurn COGs.

Authentication, COG output, fractional/all-touched weighting, quantiles, cross-row spatial batching and additional formats remain separate extensions. Do not broaden these semantics implicitly.
