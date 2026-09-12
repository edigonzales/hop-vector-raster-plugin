# Apache Hop Vector Raster Plugin

Geospatial transforms for Apache Hop: read and write vector data, clip and
reproject rasters, and calculate zonal statistics. Raster processing uses
GeoTools 35.1; no GDAL/OGR installation is required.

**[User manual (Deutsch)](https://edigonzales.github.io/hop-vector-raster-plugin/)** ·
[Maven-Artefakte](https://jars.interlis.guru/releases/ch/so/agi/hop-vector-raster-plugin/) ·
[Examples](examples/README.md)

## Transforms

All transforms appear in the **Geospatial** category.

| Transform | Purpose |
|---|---|
| Vector Reader | Read Shapefile, GeoPackage or File Geodatabase layers into typed Hop rows. |
| Vector Writer | Write Shapefile, GeoPackage, File Geodatabase, FlatGeobuf, native spatial Parquet or ArcInfo GENERATE. |
| Raster Clip (GeoTools) | Clip one band from a local GeoTIFF or public COG using a box or polygon. |
| Raster Reproject / Resample (GeoTools) | Reproject and resample all bands, including color, palette and alpha rasters. |
| Raster Zonal Statistics (GeoTools) | Add polygon statistics such as mean, min, max and valid pixel count to rows. |

FlatGeobuf, Parquet and GENERATE are output-only. Parquet uses native
GEOMETRY/GEOGRAPHY logical types, not GeoParquet metadata.

## Requirements

- Java 21 or newer; the plugin is built against Apache Hop **2.19.0**.
- The separately installed
  [Geometry Type plugin](https://github.com/edigonzales/hop-geometry-type-plugin),
  **0.2.0-SNAPSHOT with the Z/M serialization update or a compatible newer build**.
- A writable local directory for output files.

The Geometry Type plugin supplies the Geometry runtime and JTS. Vector/Raster uses
`classLoaderGroup=sogeo-geometry` and an explicit `dependencies.xml` referencing
both `../../misc/hop-geometry-type` and `../../misc/hop-geometry-type/lib`.
Hop does not recursively include `lib` when resolving dependency folders.
The Vector/Raster ZIP contains neither Geometry Type nor JTS runtime copies.
Install the separate Geometry Type ZIP before running Vector/Raster. On upgrade,
replace the complete `plugins/transforms/vector-raster` folder to remove stale JARs.

## Installation

1. Stop Apache Hop.
2. Install the Geometry Type plugin according to its installation instructions.
3. Download the versioned `hop-vector-raster-plugin-<version>.zip` from the
   [Maven repository](https://jars.interlis.guru/releases/ch/so/agi/hop-vector-raster-plugin/).
4. Keep exactly one Vector Raster installation. When changing versions,
   replace the complete `plugins/transforms/vector-raster` directory.
5. Extract the ZIP into Hop home. It creates `plugins/transforms/vector-raster/`.
6. Restart Hop and check that the five transforms appear under **Geospatial**.

Check the installed transforms and their plugin IDs in the
[installation chapter](https://edigonzales.github.io/hop-vector-raster-plugin/benutzerhandbuch/main/index.html#installation).

## First steps

Use **Vector Reader → Vector Writer** for vector conversion. For one raster
operation, use **Generate Rows (one row) → Raster Clip (GeoTools)** or
**Raster Reproject / Resample (GeoTools)**.

The [manual sources](docs/biblios/user/master.adoc) are also available directly
in this repository. The manual explains every dialog, defaults, output fields,
format limitations, errors and runnable examples.

## Important boundaries

- Vector CRS settings assign a CRS; they do not reproject coordinates.
- Raster input supports local GeoTIFFs and public HTTP/HTTPS COGs. Remote servers
  must support byte ranges; authentication and custom headers are not exposed.
- Raster output is tiled GeoTIFF, not a promised COG.
- Dimension, curve, NULL and overwrite behavior depend on the chosen format;
  consult the manual before converting a dataset.

## Development and documentation

Build, test, local Hop installation and troubleshooting are described in the
[developer guide](docs/dev-setup.md). Architecture decisions remain in
[docs/adr](docs/adr/0001-java-geospatial-suite.md).

The German manual uses Thoth Biblios. See the
[documentation build guide](docs/biblios/README.md) for local build and preview.
Automatic documentation builds run **only when `docs/biblios/**` changes**;
PRs validate, and successful builds on `main` publish to GitHub Pages.

## License

[MIT](LICENSE). Third-party notices are included with the relevant format modules.

## FileGDB curves, precision and filtering

The FileGDB reader/writer transports CircularString, CompoundCurve, CurvePolygon, MultiCurve and
MultiSurface through the shared Geometry Type plugin, preserving circular segments and Z/M.
Dates are returned as Hop date values and GUIDs as strings. The generated `OBJECTID` remains a
FileGDB row identifier: an incoming field of that name is exported as `SOURCE_OBJECTID`. An
existing field with the resulting name causes a collision error.

FileGDB writer options expose XY resolution, tolerance and both origins, plus spatial-index
creation (enabled by default). New transforms use `AUTO`: projected CRSs use 0.0001 metre
resolution and 0.001 metre tolerance converted to CRS units; geographic CRSs use 1e-9 degree
resolution. Old XML pipelines without the precision option use `LEGACY`. Explicit options override
these defaults. Unknown units in AUTO mode fail; use an explicit legacy configuration where needed.
The source schema preview shows the stored resolution, tolerance and origins.

The reader accepts four optional inclusive bounding coordinates in the source CRS. All four are
required together. It uses compatible envelope indexes and reports a scan fallback for foreign line/polygon
geometry-cell indexes (foreign point indexes are usable). The predicate compares geometry envelopes, not exact polygon intersection.
Bezier/ellipse spatial queries are not supported; ordinary reads retain their existing stroked
representation. Filtered results are currently materialized by filegdb4j.

Use Geoprocessing `linearize_curves` when linear output is explicitly required, or
`coverage_linearize` for grouped polygon boundaries. For coverage export, configure its target
XY grid identically to the FileGDB writer. XY tolerance is not the curve approximation tolerance.

The installed E2E includes a reader-to-writer FileGDB pipeline with 1,025 XYZM curves, dates,
source OBJECTIDs, a bounding filter and explicit XY precision (`scripts/e2e/filegdb-curves.hpl`).

Repeated native curve round-trips avoid introducing extra quantized midpoint vertices. A midpoint
is stored separately only when needed for a non-linear Z/M profile. GDAL 3.11.4 may reconstruct
synthetic arc midpoints with M=0; this upstream reader behaviour differs from the Hop adapter's
interpolated values and should be considered when passing measured curves through GDAL.
