# ArcInfo Generate Writer

This is a dedicated file sink. It consumes Hop Geometry and writes a homogeneous ASCII GENERATE file. It has no GeoTools or GDAL dependency.

## Dialects and primary sources

The implementation distinguishes these documented Esri variants:

| Dimension/type | Feature header | Coordinate records | Terminators |
|---|---|---|---|
| XY point | `ID X Y` | same line | one file `END` |
| XY line | `ID` | `X Y` per vertex | feature `END`, file `END` |
| XY polygon | `ID AUTO` | closed exterior ring, `X Y` | feature `END`, file `END` |
| XYZ point | `ID X Y Z` | same line | one file `END` |
| XYZ line | `ID` | `X Y Z` per vertex | feature `END`, file `END` |
| XYZ polygon | `ID` | closed exterior ring, `X Y Z` | feature `END`, file `END` |

- [Esri: How Generate works (ArcInfo coverage)](https://desktop.arcgis.com/en/arcmap/10.3/tools/coverage-toolbox/how-generate-works.htm) defines classic point/line/polygon input and automatic polygon label points (`AUTO`).
- [Esri: ASCII 3D To Feature Class](https://doc.esri.com/en/arcgis-pro/latest/tool-reference/3d-analyst/ascii-3d-to-feature-class.html) defines the XYZ GENERATE grammar and polygon closure/orientation.
- [Esri: Feature Class Z To ASCII](https://doc.esri.com/en/arcgis-pro/latest/tool-reference/3d-analyst/feature-class-z-to-ascii.html) describes Esri's corresponding 3D output format.
- [Esri: Generate, ArcMap 10.2](https://resources.arcgis.com/en/help/main/10.2/0013/00130000001w000000.htm) documents coverage generation, including the reserved `-99999` island convention. This writer rejects that polygon ID.

These are separate dialects, not a generic claim that every program accepting a `.gen` extension supports every variant. In particular, an application that expects polygons as plain closed lines should use `LINE` after explicitly converting the geometry to its boundary upstream. Interoperability has been checked with exact golden outputs; an import into a licensed Esri product has not been run.

## Configuration and validation

Choose `POINT`, `LINE` or `POLYGON`, and `XY` or `XYZ`. Supply a Hop Integer ID field or leave it empty for sequential IDs (default starts at 1, overflow is an error). IDs are Java signed 64-bit integers; use an ID range accepted by the receiving application. The writer does not enforce uniqueness across rows.

Only Point, LineString and Polygon are accepted. Reject multipart, geometry collections, curves, polygon holes, invalid geometry and nonfinite coordinates. Polygon rings are emitted clockwise and closed in every written dimension without changing the incoming geometry. Linearize, explode, remove holes or convert geometry explicitly upstream when needed.

`XYZ` requires finite Z for every coordinate. Extra Z/M ordinates require the explicit **Discard extra Z/M ordinates** option; there is no silent dimensional reduction. An XYM coordinate is not treated as XYZ.

Default precision preserves the Java double value using plain decimal notation. Decimal places 0–15 apply decimal HALF_UP rounding. The writer checks for geometry collapse after rounding, uses a decimal point independently of locale, normalizes negative zero, and writes LF line endings. Space is the default delimiter; comma is optional. Null/empty geometries fail unless **Skip null / empty geometries** is selected; the skipped count is logged.

No CRS or arbitrary attributes are embedded. Coordinates must already have the required CRS. One file contains exactly the selected geometry type. Polygon holes and multipart IDs are deliberately not approximated.

The destination's parent directory must exist. Existing files require explicit overwrite. One transform copy owns the output. A sibling temporary file is committed at successful EOF; an error or cancellation removes it and preserves any previous destination. Even an empty successful stream produces the final `END` record.

Examples are in [docs/examples](examples). Tests cover all six combinations, geometry/ordinate rejection, precision, locale, cancellation and output ownership.
