# Shapefile I/O

The Shapefile provider uses Java NIO and the shared JTS Geometry type; GeoTools is used only behind the injected CRS service. Binary/DBF components were adapted from ilitransformer under MIT; see the module's THIRD-PARTY-NOTICES.md. The shared Vector Reader/Writer contain all format options.

## Geometry and schemas

Point, MultiPoint, PolyLine and Polygon records support XY, XYZ, XYM and XYZM. Lines and polygons can contain multiple parts and polygon holes. Point and MultiPoint are separate file types. MultiPatch and mixed GeometryCollections are rejected.

Set layer geometry type and dimension explicitly to create empty files or files containing only NULL geometries. AUTO infers from the first non-empty geometry; initial NULL/EMPTY rows are buffered up to 10,000 and then fail with a configuration hint. Attribute metadata must be available from upstream. All subsequent rows are checked: additional Z/M are never silently dropped, required Z must be finite, and missing M is stored as Esri NoData (below -10^38), not zero. Z shape headers cannot declare whether their optional M section is present, so the preview reports M as optional. The reader uses the actual record's ordinates.

NULL and EMPTY become Null Shape records; readers return NULL. EMPTY conversion is warned. Curves are automatically written from their already linearized Hop coordinate representation and counted as conversions. This does not introduce another approximation tolerance or expand curve Z/M support.

EPSG or WKT can assign the CRS; it does not transform coordinates. Known conflicting SRIDs are rejected on output. Missing PRJ leaves CRS unknown and produces a warning. Custom WKT remains in the source schema and can be supplied explicitly to the writer; arbitrary intervening Hop transforms are not claimed to transport custom WKT metadata. Use the CRS shown in the preview or configure it explicitly when the source has no EPSG identifier.

## DBF encoding and fields

Read precedence is explicit charset, CPG, recognized DBF language-driver ID, then ISO-8859-1 with a warning. Common numeric CPG identifiers are accepted. UTF-8 with a CPG sidecar is the write default; other Java charsets can be selected. The reader rejects malformed byte sequences. On writing, unrepresentable characters become `?` with a warning and long text is truncated without splitting a multibyte character.

The output field table offers source name, target name, byte width and decimal places. Get fields populates it from Hop metadata; Preview DBF schema displays the actual names and widths. Remove a mapping to return that field to inferred settings. `-1` uses inferred/default width or precision. All incoming attributes are exported; select/remove attributes upstream.

Names are normalized to ASCII identifiers of at most ten characters. Collisions receive stable numeric suffixes in input order. Text defaults to 254 bytes; Integer to width 20; Number/BigNumber to width 33 and scale 15 unless metadata or the field table supplies values. BigNumber is formatted as BigDecimal without a Double intermediate. HALF_UP rounding is reported. Numeric overflow after rounding is an error; integral digits are never truncated. Fractional numeric DBF fields are read as Hop BigNumber, integral fields up to width 20 as Hop Integer.

Date/Timestamp fields become DBF calendar dates. The timezone defaults to UTC and is configurable. Dropped time-of-day is warned. NULL attributes use blank/unknown DBF representations: numeric/date/logical blanks read as NULL, character blanks as empty text. NULL and empty text are therefore not distinguishable. Binary and complex fields must be converted upstream.

Warnings are emitted once per field/cause initially, with counts in the final summary. Encoding replacement, byte truncation, naming, rounding, NULL/EMPTY conversion and curve linearization are included. These counts describe records, with schema-level warnings counted as one event.

## Files, errors and scope

Input is sequential and does not create indexes or other files. SHX is optional; when present it is validated against SHP. Deleted DBF rows skip the corresponding geometry. Component lengths, record counts and part indices are validated. No complete dataset is loaded into memory.

Output uses a staging directory and publishes SHP, SHX, DBF, CPG, and PRJ for a known CRS only after headers and record counts are finalized. Errors/aborts clean up owned files; existing sidecars and competing writers are protected. Publication of a multi-file bundle is not a filesystem-wide atomic transaction. A failed writer cannot be finalized successfully. No QIX, append/update, spatial filtering or files over the interoperable 2 GB component limit are provided.

Use Geometry plugin 0.2.0-SNAPSHOT or later with the Z/M update. A tested pipeline `Vector Reader → Sort Rows (disk spill) → Vector Writer` preserves XYZM. GeoPackage still supports its existing XY/curve scope and rejects Z/M; adding Shapefile support does not imply GeoPackage dimensional support.
