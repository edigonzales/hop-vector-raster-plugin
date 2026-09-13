# Domains and relationships in FileGDB

`export.hpl` uses two Vector Readers and one **FileGDB Writer**. Set:

- `INPUT_FILE`: source `.gdb` containing `buildings` and `entrances` with the fields in `buildings.json`.
- `SCHEMA_FILE`: absolute path to `buildings.json`.
- `OUTPUT_FILE`: a new, non-existing `.gdb` directory.

The writer creates the status and height domains, writes both datasets and creates
`building_entrances` as a simple 1:n relationship. Its dialog maps each JSON dataset
to an upstream transform. Connect every mapped transform with an enabled hop; use
one copy per writer and per mapped upstream transform. Inputs may have different
row metadata. They are consumed in round-robin order with bounded waits, so branches
with small queues can progress concurrently. The complete GDB is published only
after every input has ended successfully. Do not run competing writers against the
same output path.

`catalog.hpl` reads status codes and descriptions from the result. Use Hop's preview
or connect a normal lookup/output transform. The Catalog Reader supports `DOMAINS`,
`DOMAIN_VALUES`, `FIELD_DOMAINS`, `RELATIONSHIPS` and `RELATIONSHIP_KEYS`. The optional
filter is an exact, case-sensitive domain/dataset/relationship name, depending on
mode. Codes and range limits are text with a separate declared field type. Boolean
relationship flags are the strings `true` and `false`. Empty results are valid.

## JSON contract

The packaged `export-schema-v1.json` documents version 1. Unknown properties,
duplicate JSON keys and unsupported schema versions fail. The JSON is read once;
Hop resolves variables in the file paths, not in its contents.

- `domains` and `relationships` default to empty arrays; `datasets` is required.
- A field's `source` defaults to its target `name`. `nullable` defaults to false;
  string `length` defaults to 255. Fields not declared in the schema are omitted.
- Codes and range limits must be quoted text, including numeric values. Integers
  are checked without conversion through floating point. Range limits are inclusive.
- Domain policies are `DEFAULT_VALUE`, `DUPLICATE`, `GEOMETRY_RATIO` for split and
  `DEFAULT_VALUE`, `SUM_VALUES`, `AREA_WEIGHTED` for merge. Both default to
  `DEFAULT_VALUE`. These describe geodatabase editing behavior; Hop does not run
  splitting or merging when exporting rows.
- Use `TABLE` without a geometry object or `FEATURE_CLASS` with explicit geometry
  source/name/type/dimension/CRS. This also supports empty inputs.
- Geometry XY precision defaults to `AUTO`, index creation to true. An explicit
  resolution defaults tolerance to ten times that resolution; explicit tolerance
  overrides it. Specify both origins together. The schema supplies these settings
  when selected in the ordinary Vector Writer, overriding its geometry/XY controls.
- `GUID` is explicit and receives UUID text from a Hop String field. Integer keys
  receive numeric Hop fields with exact integer/range checks. Date/time fields
  receive Hop Date/Timestamp values (UTC); offset datetimes receive ISO-8601 text.
- `OBJECTID` is generated. Explicitly map source OBJECTIDs to an ordinary field such
  as `SOURCE_OBJECTID`; relationships must refer to stable, explicitly declared
  fields. Domain types must match assigned field types; relationship key types must
  match. Nulls, types, string lengths and domain values are validated during export.
- Relationships support simple 1:1 and 1:n definitions only. Do not add composite,
  attachment or attributed flags: they are unsupported properties. n:m export is
  rejected. Their metadata remains readable from existing GDBs.

The writer does **not** test key uniqueness, missing foreign-key targets or actual
1:1 cardinality. Validate those in the pipeline if required. A declared relationship
is schema information, not proof of referential integrity.

For a one-dataset export, the existing **Vector Writer** accepts the same schema
format through its optional JSON schema field. Its configured layer name must match
the sole dataset; files containing additional datasets or relationships require the
new FileGDB Writer. Existing pipelines without a schema file keep their behavior.

Domains and relationship definitions are not implicitly transported in Hop field
metadata. Vector Reader emits stored codes unchanged. Its schema preview shows
catalog definitions; the Catalog Reader provides explicit metadata rows for lookups.
Normal FileGDB tables can be selected in Vector Reader without a geometry column.

No append/update of an existing GDB is supported. Cross-reader verification uses
GDAL/OpenFileGDB; this is not an assertion of having tested ArcGIS.
