# Development Setup

## Repositories

Keep these repositories next to each other for the shortest development loop:

```text
sources/
├── hop-geometry-type-plugin/
└── hop-vector-raster-plugin/
```

The GeoTools transforms and `ValueMetaGeometry` must share the Hop class-loader group `sogeo-geometry`. For that reason the GeoTools distribution does not contain another copy of `hop-geometry-type` or `jts-core`.

The distribution includes GeoTools 35.1 raster/CRS libraries and ImageIO-Ext COG support. The distribution audit excludes unsupported GeoTools modules, GDAL bindings and additional JTS/Geometry copies; SQLite JDBC bundled natives are allowed.

## Java and Maven

The project builds with Java 17 and Maven. The GeoTools artifacts are resolved from the OSGeo release repository.

## One-command development loop

Set `HOP_HOME` to an unpacked local Apache Hop installation and run:

```bash
cd /path/to/hop-vector-raster-plugin
bash scripts/dev-sync-hop-plugin.sh "$HOP_HOME"
```

The script performs the following steps in order:

1. runs `mvn clean install` in `hop-geometry-type-plugin`
2. installs its ZIP into `$HOP_HOME/plugins/misc/hop-geometry-type`
3. runs `mvn clean verify` in `hop-vector-raster-plugin`
4. checks all vector/raster/GENERATE modules, GeoTools 35.1, the reviewed dependency allowlist and shared JTS/Geometry packaging
5. installs the ZIP into `$HOP_HOME/plugins/transforms/vector-raster`
6. stops a running Hop GUI process
7. starts `$HOP_HOME/hop-gui.sh` again

Hop startup output is written to:

```text
${TMPDIR:-/tmp}/hop-vector-raster-dev-hop.log
```

If the Geometry type repository is not next to this repository, pass it explicitly:

```bash
bash scripts/dev-sync-hop-plugin.sh "$HOP_HOME" /path/to/hop-geometry-type-plugin
```

or set:

```bash
export HOP_GEOMETRY_TYPE_REPO=/path/to/hop-geometry-type-plugin
```

`dev-sync-hop-plugin.sh` is the familiar entry point used by the other Hop plugin repositories; it delegates to `dev-install-and-run.sh`, where the actual workflow lives.

## Manual build

Build and install the Geometry type dependency into the local Maven repository:

```bash
mvn -f ../hop-geometry-type-plugin/pom.xml -U clean install
```

Build and test this plugin:

```bash
mvn -U clean verify
python3 scripts/check-distribution.py
```

The resulting distribution is:

```text
assemblies/assemblies-hop-vector-raster/target/hop-vector-raster-plugin-0.1.0-SNAPSHOT.zip
```

Install manually:

```bash
rm -rf "$HOP_HOME/plugins/misc/hop-geometry-type"
unzip -q -o \
  ../hop-geometry-type-plugin/assemblies/assemblies-hop-geometry-type/target/hop-geometry-type-plugin-0.2.0-SNAPSHOT.zip \
  -d "$HOP_HOME"

rm -rf "$HOP_HOME/plugins/transforms/vector-raster" "$HOP_HOME/plugins/transforms/geotools-vector"
unzip -q -o \
  assemblies/assemblies-hop-vector-raster/target/hop-vector-raster-plugin-0.1.0-SNAPSHOT.zip \
  -d "$HOP_HOME"
```

Then restart Hop GUI so that the plugin registry and plugin class loaders are recreated.

## Quick functional check

Create a pipeline:

```text
Vector Reader -> Vector Writer
```

Example:

- Reader file: `/tmp/input.shp`
- Reader layer: empty
- Reader geometry field: `geometry`
- Writer file: `/tmp/output.gpkg`
- Writer layer: `parcels`
- Writer geometry field: `geometry`

Run it once. The MVP deliberately fails if the output file already exists, so delete `/tmp/output.gpkg` before repeating the test.

Read `output.gpkg` with another `Vector Reader` to verify the result.

### Curve check

For a GeoPackage containing `CIRCULARSTRING`, `COMPOUNDCURVE`, or `CURVEPOLYGON`, run a GeoPackage-to-GeoPackage pipeline and inspect the output again with `Vector Reader`. The geometry remains a curve in the shared Hop `Geometry` value type and the output GeoPackage registers the matching `gpkg_geom_<TYPE>` extension.

Writing the same curve geometry to Shapefile is intentionally different: Shapefile has no curved geometry type, so the writer linearizes the curve explicitly before writing.

The automated tests cover exact file roundtrips for `CIRCULARSTRING`, `COMPOUNDCURVE`, and `CURVEPOLYGON`, GeoPackage extension metadata, ordinary point output through the same GeoPackage path, and Shapefile linearization.

## Current Vector Reader/Writer limitations

- Shapefile and GeoPackage only
- one input layer at a time
- output files must not already exist
- output geometry type is inferred from the first non-null geometry
- an input where every geometry is null cannot be written yet
- curved geometries are currently 2D only; curve Z/M ordinates are not supported
- Shapefile necessarily linearizes curved geometries
- no reprojection, clipping or other geoprocessing in Reader/Writer
- no generic DataStore parameter UI yet

For raster transforms and the GENERATE format in Vector Writer, see [raster usage](raster-transforms.md) and [GENERATE format decisions](generate-format.md).

For upgrades, follow the [migration table](migration.md). Do not leave the old `geotools-vector` folder alongside `vector-raster`; old IDs have no aliases.

After `mvn verify`, run `python3 scripts/check-vector-dialogs.py` with a desktop display to open and automatically check both vector dialogs. It checks the format selectors and that every GENERATE control belongs to the format-specific group. The script obtains the matching Apache Hop RCP fragment from Maven Central into `target/ui-smoke`; it is not included in the plugin ZIP.
