# GeoPackage vector adapter

The adapter in `vector/formats/geopackage` uses SQLite JDBC and the shared Hop Geometry/JTS libraries. It does not depend on GeoTools or iox-wkf. The common transform supplies a `CrsDefinitionResolver`; the current implementation uses the existing GeoTools EPSG/WKT service. Raster retains GeoTools; Shapefile I/O is native Java.

## Supported behavior

- Feature layers from `gpkg_contents` and `gpkg_geometry_columns`; explicit layer selection, case-insensitive fallback and schema preview.
- Hop String, Integer, Number/BigNumber, Boolean, Date/Timestamp and Binary attributes. SQLite REAL represents Number/BigNumber (as in the previous double-based GeoTools schema); this is not an exact-decimal storage promise. Datetimes are UTC ISO 8601 text. SQLite integer primary keys are feature identifiers, not ordinary output attributes; an incoming attribute named `fid` remains an attribute by assigning a different internal primary-key name.
- Geometry is the last reader field. Its original column name is retained unless the user overrides the Hop output name. The geometry SRID comes from the GeoPackage header and must match the layer metadata.
- XY linear WKB plus the shared `CIRCULARSTRING`, `COMPOUNDCURVE`, `CURVEPOLYGON`, `MULTICURVE` and `MULTISURFACE` codecs. SQL NULL and WKB EMPTY remain distinct. Z/M dimensions, extended user-defined binary encodings and curves nested inside a generic GeometryCollection are rejected explicitly.
- Both GP header byte orders and optional XY envelopes are read. Output uses little endian, standard GP binary version 0, no envelope and standard WKB without embedded EWKB SRID. Nested curve extensions are registered as `gpkg_geom_<TYPE>` with `read-write` scope.
- New output file with a single feature layer, metadata tables, required undefined/WGS84 SRS definitions and the selected EPSG CRS. Type and SRID are inferred from the first non-null geometry and must remain consistent. All-null streams cannot supply that inference.
- Parameter-bound attribute values, quoted identifiers and explicit transactions. `gpkg_contents` bounds and last-change timestamp are finalized on success. A temporary sibling database is published only after commit and close. Abort/error removes temporary output. Existing files are rejected. There is no append/update mode or new spatial-index option.

The adapter intentionally does not implement raster/tile GeoPackages. No GDAL installation is needed; SQLite JDBC carries its own platform libraries in the common ZIP.

## References and validation

The binary and metadata contracts follow [OGC GeoPackage 1.4](https://www.geopackage.org/spec/), especially the GP binary header, feature table definitions and non-linear geometry extensions. [iox-wkf](https://github.com/claeis/iox-wkf/) informed the direct-JDBC approach without adding its full dependency graph.

Tests include hand-authored byte-order/NULL/EMPTY fixtures and SQL fixtures independent of the encoder, SQL integrity and foreign-key checks, exact attributes/SRID/curves, and reciprocal reading with GeoTools as a test-only reference. Cancellation, ownership conflicts, wrong SRIDs, malformed headers and unsupported dimensions are covered. No GeoTools GeoPackage implementation is shipped.
