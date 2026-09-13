# Create a GeoPackage, add a layer, append features

Run the three pipelines **sequentially**, using the same parameters:

- `INPUT_VECTOR`: an existing GeoPackage or Shapefile with XY features.
- `INPUT_LAYER`: source layer name (optional for a single-layer source).
- `OUTPUT_FILE`: the same destination `.gpkg` for all three runs; it must not exist before step 1.

1. `01-create_file.hpl` creates the file with a `zones` layer.
2. `02-add_layer.hpl` adds `zones_copy` without changing `zones`.
3. `03-append_features.hpl` appends the input features to `zones`.

Afterwards `zones` contains twice the source feature count and `zones_copy` once.
Each layer has a spatial index. The installed Hop E2E suite executes these exact
pipelines, and also appends to independently created GDAL files with and without
an index.

## Writer settings

The GeoPackage options in Vector Writer select `CREATE_FILE`, `ADD_LAYER`, or
`APPEND_FEATURES`. Existing pipelines default to `CREATE_FILE`. There is no implicit
file or layer replacement. Select **Layer laden** to inspect an existing file and
**Eingangsschema prüfen** to compare upstream fields with the selected target.

Append uses the existing layer's geometry type and CRS. The source geometry field
can have a different name. Attribute names match without case sensitivity, regardless
of their order. Use Select Values to rename/remove fields before writing. Unknown
input attributes are errors. Missing target columns must be nullable or have a
database default; omitted columns receive that default. Explicit nulls never invoke
defaults. The target primary key is generated: rename a source identifier to an
ordinary attribute such as `SOURCE_FID` if it must be retained.

Known differing CRS values are rejected; SRID 0 receives the target SRID. This does
not reproject coordinates. XY curves are preserved. Z/M, updates, upserts, layer
replacement, schema evolution and nonspatial tables remain outside this feature.

**Räumlichen Index erstellen, falls nicht vorhanden** defaults to enabled. When
appending, it also indexes pre-existing features if there was no index. Turning it
off never disables maintenance of an existing index. Indexes exclude null/empty
geometries, preserve curve extrema, and are updated together with layer extent and
change time. Incomplete index definitions are rejected, not silently repaired.

Each writer uses one transaction and one owner per output path. A failed or stopped
run rolls back its inserted features, new layer and index changes. Existing layers
and other GeoPackage content remain intact. External lock conflicts fail after five
seconds. Run writers to one file sequentially; use one transform copy. The guarantee
is per writer execution, not a transaction across several pipelines.

File and layer names support Hop variables. Missing design-time values disable only
the preview; schema validation is repeated at runtime. Empty appends work without a
geometry sample. Creating an empty layer requires an explicit supported geometry
schema.
