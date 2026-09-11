# Migration to hop-vector-raster-plugin

This is an intentional compatibility break. Back up existing pipelines and replace their transform types before running them with this distribution. There are no aliases for the former IDs.

| Previous ID | New ID | Display name |
|---|---|---|
| `GEOTOOLS_VECTOR_READER` | `SOGIS_VECTOR_READER` | Vector Reader |
| `GEOTOOLS_VECTOR_WRITER` | `SOGIS_VECTOR_WRITER` | Vector Writer |
| `GEOTOOLS_RASTER_CLIP` | `SOGIS_RASTER_CLIP` | Raster Clip (GeoTools) |
| `GEOTOOLS_RASTER_ZONAL_STATS` | `SOGIS_RASTER_ZONAL_STATS` | Raster Zonal Statistics (GeoTools) |
| `ARCINFO_GENERATE_WRITER` | `SOGIS_VECTOR_WRITER` with `format=ARCINFO_GENERATE` | Vector Writer |

Existing common vector fields `fileName`, `layerName`, `geometryField` (writer) and `geometryFieldName` (reader) retain their meaning. Select SHAPEFILE/GEOPACKAGE explicitly, or AUTO for extension detection. Raster settings retain their existing meaning.

For former GENERATE transforms, rename the `output` setting to `fileName`, set `format` to `ARCINFO_GENERATE`, and keep `geometryField`, `geometryType`, `dimension`, `idField`, `startId`, `discardExtraOrdinates`, `decimals`, `comma`, `skipEmpty` and `overwrite`. Layer is unused. The encoder dialects and EOF terminators are unchanged.

| Previous location/name | New location/name |
|---|---|
| GitHub `edigonzales/hop-geotools-plugin` | `edigonzales/hop-vector-raster-plugin` |
| `hop-geotools-plugin-<version>.zip` | `hop-vector-raster-plugin-<version>.zip` |
| `plugins/transforms/geotools-vector` | `plugins/transforms/vector-raster` |
| Maven `hop-geotools-plugin-parent` | `hop-vector-raster-plugin-parent` |
| Maven `hop-geotools-vector` | `hop-vector-transforms` plus `hop-vector-core` and format artifacts |
| Maven `hop-geotools-common` | `hop-geotools-support` |
| Maven `hop-geotools-raster-core` | `hop-raster-core` |
| Maven `hop-transform-geotools-raster-clip` | `hop-raster-clip` |
| Maven `hop-transform-geotools-raster-zonal-stats` | `hop-raster-zonal-stats` |
| Maven `hop-transform-arcinfo-generate-writer` | `hop-vector-format-generate` (format library only) |

Stop Hop before installing manually. Remove both the previous plugin folder and an existing `vector-raster` folder, then extract the new ZIP into Hop home. Install `hop-geometry-type-plugin` separately as before for the shared Hop value-type registration; the Vector/Raster ZIP references the separate Geometry plugin and its `lib` folder through `dependencies.xml` and contains no Geometry/JTS copies. Do not copy additional JARs manually. The development installation script performs the old-folder cleanup automatically.

Update distribution consumers such as `hop-distributions` to the Maven coordinates
`ch.so.agi:hop-vector-raster-plugin:<version>` and the ZIP installation root
`plugins/transforms/vector-raster`; plugin GitHub Releases are no longer a runtime
dependency. A local checkout may keep its existing directory name. Its Git remote
should use the new repository URL.

Java remains 17; GeoTools remains 35.1 for raster and isolated CRS services. GeoPackage I/O now uses JDBC directly. Read the [GeoPackage contract](geopackage.md) for dimensional and file-lifecycle rules.

The interim `SOGEO_VECTOR_READER`, `SOGEO_VECTOR_WRITER`, `SOGEO_RASTER_CLIP` and `SOGEO_RASTER_ZONAL_STATS` IDs must also be replaced with their `SOGIS_` equivalents. There are no aliases. This does not rename the Geometry plugin's numeric type ID or the shared `sogeo-geometry` classloader group.

Upgrade the Geometry plugin to the 0.2.0-SNAPSHOT Z/M build at the same time. Shapefile output is UTF-8 with CPG by default, no longer creates QIX, and reports DBF conversions. Existing dataset names and sidecars are protected; append/update/overwrite of Shapefile and GeoPackage remain outside this release. GENERATE retains its existing overwrite option.
