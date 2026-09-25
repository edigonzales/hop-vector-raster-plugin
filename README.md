# hop-vector-raster-plugin

Geospatial transforms for Apache Hop: read and write vector data, clip and
reproject rasters, and calculate zonal statistics. Raster processing uses
GeoTools 35.1; no GDAL/OGR installation is required.

## Features

All transforms appear in the **Geospatial** category.

| Transform | Purpose |
|---|---|
| Vector Reader | Read Shapefile, GeoPackage or File Geodatabase layers into typed Hop rows. |
| Vector Writer | Write Shapefile, GeoPackage, File Geodatabase, FlatGeobuf, native spatial Parquet or ArcInfo GENERATE. |
| Raster Reader (GeoTools) | Create a Raster value from a local GeoTIFF or public HTTP/HTTPS COG. |
| Raster Info (GeoTools) | Add selected raster metadata without calculating pixels. |
| Raster Clip (GeoTools) | Plan a box/polygon clip of all bands or an explicit selection. |
| Raster Reproject / Resample (GeoTools) | Reproject and resample all bands, including color, palette and alpha rasters. |
| Raster Writer (GeoTools) | Materialize a Raster value as a local GeoTIFF/BigTIFF or Cloud Optimized GeoTIFF. |
| Raster Zonal Statistics (GeoTools) | Add polygon statistics such as mean, min, max and valid pixel count to rows. |

FlatGeobuf, Parquet and GENERATE are output-only. Parquet uses native
GEOMETRY/GEOGRAPHY logical types, not GeoParquet metadata.

## Requirements

- Java 21 or newer; the plugin is built against Apache Hop **2.19.0**.
- The separately installed
  [Geometry Type plugin](https://github.com/edigonzales/hop-geometry-type-plugin),
  **0.2.0-SNAPSHOT with the Z/M serialization update or a compatible newer build**.
- The matching [Raster Type plugin](https://github.com/edigonzales/hop-raster-type-plugin),
  which supplies the shared Raster model and Hop value type.
- A writable local directory for output files.

The Geometry Type plugin supplies the Geometry runtime, JTS and the shared GeoTools/Imagen,
ImageIO-Ext and UOM runtime. Vector/Raster uses
`classLoaderGroup=sogeo-geometry` and an explicit `dependencies.xml` referencing
`../../misc/hop-geometry-type`. The Geometry Type plugin adds its own `lib/`
directory to that shared classloader; repeating the `lib/` folder here would
make Hop scan every Imagen registry twice because dependency URLs are not
canonicalized.
The Vector/Raster ZIP contains neither Geometry Type nor JTS runtime copies.
Install both separate Geometry Type and Raster Type ZIPs before running Vector/Raster.
The Raster Type root and `lib/` folder are also referenced by `dependencies.xml`; its shared classes
are excluded from the Vector/Raster ZIP. On upgrade,
replace the complete `plugins/transforms/vector-raster` folder to remove stale JARs.

## Install

1. Stop Apache Hop.
2. Install the Geometry Type plugin and the matching Raster Type ZIP.
3. Download the versioned `hop-vector-raster-plugin-<version>.zip` from the
   [Maven repository](https://jars.interlis.guru/releases/ch/so/agi/hop-vector-raster-plugin/).
4. Keep exactly one Vector Raster installation. When changing versions,
   replace the complete `plugins/transforms/vector-raster` directory.
5. Extract the ZIP into Hop home. It creates `plugins/transforms/vector-raster/`.
6. Restart Hop and check that the vector and raster transforms appear under **Geospatial**.

Check the installed transforms and their plugin IDs in the
[installation chapter](https://edigonzales.github.io/hop-vector-raster-plugin/benutzerhandbuch/main/index.html#installation).

## Documentation

- [User manual (Deutsch)](https://edigonzales.github.io/hop-vector-raster-plugin/)
- [Manual source](docs/biblios/user/master.adoc)
- [Examples](examples/README.md)
- [Documentation build guide](docs/biblios/README.md)
- [Developer setup](docs/dev-setup.md)
- [Architecture decisions](docs/adr/0001-java-geospatial-suite.md)

Build and preview the nested Biblios handbook locally:

```sh
python3 docs/biblios/build.py --serve --port 8080
```

## Build and development

Use **Vector Reader → Vector Writer** for vector conversion. For raster processing, use
**Raster Reader → Raster Clip → Raster Reproject → Raster Writer**. Reader can run without
upstream rows; downstream operations carry a typed Raster field and do not write intermediate
TIFFs. The writer publishes plain tiled GeoTIFFs or Cloud Optimized GeoTIFFs.
See the [complete value-chain example](examples/raster-values/README.md) and the
[COG writer example](examples/raster-writer/cog.hpl).

File-based raster pipelines require migration with
`scripts/migrate-raster-values.py old.hpl new.hpl`. Migrate them before opening the pipeline in Hop,
because the old transform IDs are no longer registered. The script converts supported old raster
IDs into the current Raster Reader → operation value pipeline, adding a Raster Writer for file output.

Build, test, local Hop installation and troubleshooting are described in the
[developer guide](docs/dev-setup.md). The manual sources are in
`docs/biblios/user/`; the nested structure is intentional because it contains the extensive
German handbook and its build tooling.

## Modules and artifacts

- `vector/`: vector core, transforms and format adapters.
- `raster/`: the Raster value integration.
- `support/`: shared GeoTools support.
- `assemblies/assemblies-hop-vector-raster`: the installable plugin ZIP under `target/`.

The Geometry Type and Raster Type plugins are separate runtime dependencies and are not bundled
into the Vector/Raster ZIP.

## Important boundaries

- Vector CRS settings assign a CRS; they do not reproject coordinates.
- Raster input supports local GeoTIFFs and public HTTP/HTTPS COGs. Remote servers
  must support byte ranges; authentication and custom headers are not exposed.
- Raster output is tiled GeoTIFF; the Raster Writer's COG format additionally writes internal
  overviews, the cloud-optimized layout and a GDAL ghost area, with JPEG/YCbCr for byte rasters.
- Dimension, curve, NULL and overwrite behavior depend on the chosen format;
  consult the manual before converting a dataset.

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
required together. It uses compatible envelope indexes and reports a scan fallback for foreign
line/polygon geometry-cell indexes (foreign point indexes are usable). The predicate compares
geometry envelopes, not exact polygon intersection. Bezier/ellipse spatial queries are not
supported; ordinary reads retain their existing stroked representation. Filtered results are
currently materialized by filegdb4j.

Use Geoprocessing `linearize_curves` when linear output is explicitly required, or
`coverage_linearize` for grouped polygon boundaries. For coverage export, configure its target XY
grid identically to the FileGDB writer. XY tolerance is not the curve approximation tolerance.

The installed E2E includes a reader-to-writer FileGDB pipeline with 1,025 XYZM curves, dates,
source OBJECTIDs, a bounding filter and explicit XY precision
(`scripts/e2e/filegdb-curves.hpl`). Repeated native curve round-trips avoid introducing extra
quantized midpoint vertices. A midpoint is stored separately only when needed for a non-linear Z/M
profile. GDAL 3.11.4 may reconstruct synthetic arc midpoints with M=0; this upstream reader
behaviour differs from the Hop adapter's interpolated values and should be considered when passing
measured curves through GDAL.

### FileGDB catalog and schema export

FileGDB Catalog Reader exposes domains, field assignments and relationship metadata.
FileGDB Writer creates or extends a geodatabase with multiple named inputs, each
creating a dataset or appending rows. A versioned JSON schema defines new datasets,
domains and relationships; append-only runs use the existing schema without JSON.
Vector Writer also supports creating a GDB, adding a feature class and appending
features. Existing indexes and extents are maintained in a recoverable working copy.
Vector Reader also reads ordinary FileGDB tables. See the
[complete example and supported scope](docs/examples/filegdb/README.md).

### GeoPackage append

Vector Writer can create a new GeoPackage, add a layer to an existing file, or append
features to an existing layer. Spatial indexes and extents are maintained transactionally.
See the [sequential example pipelines and GUI settings](docs/examples/geopackage-append/README.md).

## CI and publication

The shared CI contract verifies Java 21/25 compatibility on Linux, macOS and Windows.
The canonical Ubuntu/Java 21 build creates the publishable ZIP; package, cloud-output and
installed E2E checks use that exact artifact. Existing workflow and `ci-ref` pins are retained.
The repository contract is checked separately with the `multi-module-suite` profile.
Documentation PRs validate the nested Biblios handbook, and pushes to `main` publish it when
`docs/biblios/**`, `examples/**` or the documentation workflow changes.

## License

See [LICENSE](LICENSE). Third-party notices are included with the relevant format modules.
