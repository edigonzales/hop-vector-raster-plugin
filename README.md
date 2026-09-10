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
| Vector Reader | Read Shapefile or GeoPackage layers into typed Hop rows. |
| Vector Writer | Write Shapefile, GeoPackage, FlatGeobuf, native spatial Parquet or ArcInfo GENERATE. |
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

The Geometry Type plugin supplies shared Geometry and JTS classes. They are not
included in this ZIP. Both plugins use classloader group `sogeo-geometry`.

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
