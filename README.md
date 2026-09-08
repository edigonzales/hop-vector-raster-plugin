# Apache Hop GeoTools Plugin

Java geospatial transforms for Apache Hop, using GeoTools **35.1** for vector and raster processing. Java 17+, without GDAL/OGR bindings or an installed GDAL runtime.

GeoTools is deliberately an implementation detail. The transforms live in the `Geospatial` category. Raster display names identify the GeoTools implementation so they can coexist with GDAL transforms.

## Vector transforms

Vector file I/O includes:

- **Vector Reader**
  - Shapefile (`.shp`)
  - GeoPackage (`.gpkg`)
  - layer names are discovered from the GeoTools DataStore and offered in the dialog; the first layer is used when omitted
  - the dialog shows a read-only schema preview with source attributes, geometry field name and geometry type
  - attributes become normal Hop fields
  - geometry is emitted using the existing Hop `Geometry` value type
  - the geometry output field is optional; when omitted the source geometry attribute name is preserved, with `geometry` used only as a fallback
- **Vector Writer**
  - Shapefile (`.shp`)
  - GeoPackage (`.gpkg`)
  - optional layer name; otherwise the output filename is used
  - schema is derived from the incoming Hop row metadata
  - geometry type is inferred from the first non-null geometry

## Raster and GENERATE transforms

- **Raster Clip (GeoTools)**: local GeoTIFF or public HTTP/HTTPS COG → local tiled GeoTIFF, using a bounding box or Hop Polygon/MultiPolygon. Raster source, output path and box coordinates can be constants or input fields.
- **Raster Zonal Statistics (GeoTools)**: enrich incoming polygon rows with selected `mean`, `min`, `max`, `sum`, population `stddev`, plus valid `count` and `status`. Read the original resolution, respect NoData, and transform a copy of the input geometry into the raster CRS.
- **ArcInfo Generate Writer**: independent ASCII writer for explicit XY/XYZ point, line and polygon dialects. See [format documentation](docs/generate-format.md).

See [raster usage and acceptance tests](docs/raster-transforms.md) and the [architecture decision](docs/adr/0001-java-geospatial-suite.md).

## Geometry type and class loading

This plugin depends on [hop-geometry-type-plugin](https://github.com/edigonzales/hop-geometry-type-plugin).

Both plugins use the Hop class-loader group `sogeo-geometry`. The GeoTools plugin therefore declares `hop-geometry-type` and `jts-core` as provided dependencies and **does not package them in its ZIP**. JTS and the `ValueMetaGeometry` implementation come from the separately installed Geometry type plugin, avoiding two incompatible JTS `Geometry` classes in the same pipeline.

The distribution includes supported GeoTools raster modules, ImageIO-Ext COG/TIFF and ImageN. It excludes GeoTools `modules/unsupported`, GDAL/OGR bindings and raster native codecs. SQLite JDBC's bundled native libraries remain for GeoPackage; no separate installation is required.

## Curved geometries

The Hop geometry value type is the canonical in-pipeline representation for SQL/MM curved geometries. GeoTools curve implementations are adapted at the `Vector Reader` boundary, so downstream transforms see the curve classes from `hop-geometry-type-plugin`, not `org.geotools.geometry.jts.*` curve classes.

The following 2D geometry types are preserved when reading and writing GeoPackage:

- `CIRCULARSTRING`
- `COMPOUNDCURVE`
- `CURVEPOLYGON`
- `MULTICURVE`
- `MULTISURFACE`

For GeoPackage output the writer registers the corresponding `gpkg_geom_<TYPE>` read-write extension, stores the extended type in `gpkg_geometry_columns`, and writes the exact SQL/MM curve WKB from the shared geometry type. This avoids the implicit linearization that occurs when a curved `LineString` is passed to the standard JTS `WKBWriter`.

Shapefile has no native curved geometry type. Curves are therefore explicitly linearized before they are handed to the Shapefile writer. The current curve implementation is 2D; Z/M curve ordinates are not supported yet.

## Build and test

Requirements:

- Java 17+
- Maven
- a local checkout of `hop-geometry-type-plugin`

Install the Geometry type snapshot first, then build this repository:

```bash
mvn -f ../hop-geometry-type-plugin/pom.xml -U clean install
mvn -U clean verify
python3 scripts/check-distribution.py
```

The tests perform real Shapefile and GeoPackage writes/reads in temporary directories. Schema-probe tests create a multi-layer GeoPackage and verify layer discovery, geometry column names and field metadata. Curve tests perform file roundtrips for `CIRCULARSTRING`, `COMPOUNDCURVE`, and `CURVEPOLYGON`, verify the GeoPackage extension metadata, and verify explicit Shapefile linearization. CI runs the same build on Linux, macOS and Windows.

## Releases

Pushes to `main` publish the installable plugin distribution as a public GitHub Release after the Maven tests and `scripts/check-distribution.py` have passed.

The release contains exactly one platform-independent asset:

```text
hop-geotools-plugin-<version>.zip
```

The ZIP contains `plugins/transforms/geotools-vector/` and its GeoTools runtime dependencies. It deliberately does **not** contain `hop-geometry-type` or `jts-core`; the final Hop distribution supplies those once through the shared Geometry Type plugin.

`hop-distributions` consumes this GitHub Release asset directly. The Maven repositories are only needed for Java build dependencies such as the provided `hop-geometry-type` artifact.

## Fast local Hop development

The intended directory layout is:

```text
sources/
├── hop-geometry-type-plugin/
└── hop-geotools-plugin/
```

With a local Hop installation in `$HOP_HOME`, the standard development entry point builds both plugins, runs the tests, installs both plugin distributions and restarts Hop GUI:

```bash
bash scripts/dev-sync-hop-plugin.sh "$HOP_HOME"
```

If the Geometry type repository is elsewhere:

```bash
bash scripts/dev-sync-hop-plugin.sh "$HOP_HOME" /path/to/hop-geometry-type-plugin
```

The script installs:

```text
$HOP_HOME/plugins/misc/hop-geometry-type
$HOP_HOME/plugins/transforms/geotools-vector
```

and then restarts the local Hop GUI. Startup output is written to `${TMPDIR:-/tmp}/hop-geotools-dev-hop.log`.

See [docs/dev-setup.md](docs/dev-setup.md) for the individual steps and troubleshooting notes.

## Quick manual test

After running the development script, create a pipeline like:

```text
Vector Reader                    Vector Writer
------------                    -------------
input.gpkg            --->       output.gpkg
layer: parcels                   layer: parcels
geometry output: (empty)         geometry input: geom
```

When the reader geometry output field is empty, the source geometry column name (for example `geom`) becomes the Hop field name. Run the pipeline and then use another `Vector Reader` on `output.gpkg` to verify the roundtrip.

## Modules

```text
hop-geotools-plugin
├── hop-geotools-common
├── hop-geotools-vector
│   ├── Vector Reader
│   └── Vector Writer
├── hop-geotools-raster-core
├── hop-transform-geotools-raster-clip
├── hop-transform-geotools-raster-zonal-stats
├── hop-transform-arcinfo-generate-writer
└── assemblies/assemblies-hop-geotools
```

Raster reprojection/resampling, authenticated remote sources and COG output are not included in this version.
