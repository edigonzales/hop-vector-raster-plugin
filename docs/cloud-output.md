# FlatGeobuf and native spatial Parquet output

Select **FLATGEOBUF** or **PARQUET** in the common Vector Writer, or use `.fgb` / `.parquet` with `AUTO`. Both formats are write-only. They use the existing `SOGIS_VECTOR_WRITER` ID, the same ZIP and the separately installed Geometry plugin (minimum 0.2.0-SNAPSHOT). No GeoTools format adapter, GDAL, Hadoop installation or new native codec is needed.

## Common geometry and file rules

One selected Hop Geometry column is written alongside the attributes. Supported families are Point, MultiPoint, LineString, MultiLineString, Polygon and MultiPolygon, with XY, XYZ, XYM and XYZM. An explicit Multipart schema accepts its corresponding Single geometry. Heterogeneous GeometryCollections and additional Geometry attributes are rejected.

Set geometry type and dimension explicitly for empty or all-NULL datasets. Otherwise the first nonempty geometry determines the schema, with at most 10,000 initial NULL/EMPTY rows buffered. Every subsequent geometry must match that schema. Required Z values must exist, optional missing M values remain NaN, and additional dimensions are never silently removed. Curves use the shared geometry type's existing linearized representation, with a warning.

CRS can be assigned using the existing EPSG/WKT field. Assignment does not reproject coordinates; conflicting known SRIDs fail. Field names are preserved; duplicate/empty names and unsupported complex attributes fail with a request for upstream conversion.

Files are staged beside the destination and published only after completion. Overwrite is off by default. Failure or cancellation preserves existing targets and deletes this writer's temporary files. One transform copy per destination is required. The parent directory must already exist. Publication requires filesystem support for atomic rename (overwrite) or hard links (no overwrite); unsupported filesystems fail instead of exposing partially written output.

## FlatGeobuf

**Spatial index** is enabled by default. Features are staged, sorted by Hilbert key (input position breaks ties), then written with a packed R-tree (node size 16). The index changes feature order. Without an index, input order is preserved.

Sorting uses a 64 MiB working budget and external merge passes with at most 64 open runs. Index levels are constructed on disk. Temporary disk space is proportional to the dataset and index; the complete feature/index list is not held in heap. Individual features and their FlatBuffer encoding still need memory.

Indexed output rejects NULL/EMPTY geometries by default. Enable **Skip NULL/EMPTY geometries with warning** to omit and count these rows. Without an index, NULL features are preserved; EMPTY becomes NULL with a warning. A zero-feature file has no index.

Attributes map as follows:

| Hop type | FlatGeobuf |
|---|---|
| Integer | Long (64 bit) |
| Number | Double |
| BigNumber | Exact decimal String, with a type-conversion warning |
| String | UTF-8 String |
| Boolean | Bool |
| Binary | Binary |
| Date / Timestamp | ISO-8601 UTC DateTime, retaining available fractional seconds |

NULL properties are omitted, distinguishing them from empty strings. Known CRS authority/code and available WKT are written to the header; unknown CRS is not labelled as WGS84.

## Parquet

This is **native spatial Parquet**, using Apache Parquet Java 1.17.0 and the [GEOMETRY/GEOGRAPHY specification](https://github.com/apache/parquet-format/blob/master/Geospatial.md). It does not emit a GeoParquet `geo` metadata document. Clients must support native Parquet geometry types or interpret the WKB binary column explicitly.

Defaults: **GEOMETRY**, **GZIP**, **134217728 bytes (128 MiB)** per row group. `UNCOMPRESSED` is also available. Row group size is a target, not a hard process memory ceiling. No JNI compression libraries or Hadoop runtime are bundled.

`GEOMETRY` uses planar edges. `GEOGRAPHY` requires a known geographic CRS and longitude/latitude coordinates in [-180, 180] / [-90, 90]. Supported interpolation annotations are SPHERICAL (default), VINCENTY, THOMAS, ANDOYER and KARNEY. Selecting an annotation does not perform coordinate transformation or geometry processing.

Geometry payloads use ISO-WKB dimensional codes, with no EWKB flags or embedded SRID. The CRS is stored on the logical type. Unknown GEOMETRY CRS is explicitly `srid:0`, avoiding the implicit CRS84 default. NULL and EMPTY are distinct. Ordinary binary min/max statistics are disabled for geometry columns; no additional spatial statistics are promised.

| Hop type | Parquet |
|---|---|
| Integer | INT64 |
| Number | DOUBLE |
| BigNumber | BINARY with DECIMAL precision/scale |
| String | BINARY with STRING / UTF-8 |
| Boolean | BOOLEAN |
| Binary | BINARY |
| Date | TIMESTAMP(MILLIS, UTC) |
| Timestamp | TIMESTAMP(NANOS, UTC) |

For BigNumber set **length = total precision** and **precision = scale** in upstream Hop metadata (for example Select Values). Both must be explicit; missing metadata, overflow and values requiring rounding fail. No conversion through Double occurs. Timestamp nanosecond overflow also fails.

## Example pipelines

The parameterized pipelines in `examples/cloud-output/` read Shapefile or GeoPackage and export one selected layer:

- `vector-to-flatgeobuf.hpl`: `INPUT_VECTOR`, `INPUT_LAYER`, `OUTPUT_FILE`; indexed output, errors on NULL/EMPTY.
- `vector-to-parquet.hpl`: the same parameters; GEOMETRY with GZIP and 128 MiB row groups.

The reader emits `geometry`; all other attributes are forwarded. Set `INPUT_LAYER` when selecting a particular GeoPackage layer. Specify decimal metadata upstream if the source lacks it. Use an explicit output geometry schema for empty source layers.

## Verification

Run `mvn clean verify`, `python3 scripts/check-distribution.py` and `python3 scripts/check-cloud-output.py`. The last command extracts the ZIP and writes both formats without Hadoop on the runtime classpath. Unit/integration tests inspect FlatBuffers and index queries, force multi-pass external sorting, independently decode Parquet footer/page data and ISO-WKB, and exercise cancellation and exact attribute conversions.

Writer architecture and mappings were adapted from [gpkg2cloudformats](https://github.com/edigonzales/gpkg2cloudformats); the MIT license is included in each adapter JAR's `META-INF/NOTICE.txt`.
