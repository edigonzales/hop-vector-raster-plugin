# GeoJSON and JSON-FG examples

Install the current Geometry Type, Raster Type and Vector/Raster snapshots. Open a
pipeline in Hop GUI and run with the local engine. All inputs are included beside
the pipeline; output directories must exist and output files must not exist.

| Pipeline | Expected result |
|---|---|
| [RFC 7946](rfc7946.hpl) | Two features: WGS84 Bern point and NULL geometry. Nested properties, exact decimal and large numeric ID survive. |
| [GeoJSON 2008](legacy2008.hpl) | One EPSG:2056 point at 2600000 / 1200000; named CRS and attributes survive. |
| [JSON-FG 1.0](jsonfg10.hpl) | One native LV95 CircularString with M=0/1/2, UTC time and the supplied WGS84 linear fallback survive. |

Every pipeline uses Vector Reader → GeoJSON Feature Encoder → GeoJSON Feature
Decoder → Vector Writer. Decoder outputs use `decoded_` names to avoid collisions
with the original row fields. The Writer retains the original Reader context field
`geojson_context_json` so collection-level metadata keeps its original scope when
passing through standalone Feature JSON strings.

`INPUT_VECTOR` defaults to the matching `.geojson` beside the pipeline.
`OUTPUT_FILE` defaults to `<profile>-result.geojson` beside it; choose a new file
for repeated runs. The JSON-FG fallback is an illustrative approximation supplied
in the fixture. The pipeline does not calculate or reproject it.

Properties, IDs and context are JSON text fields. Formatting and key order can
change; numeric values and JSON structure are preserved. The installed E2E suite
executes all three examples and checks their outputs with Python's Decimal parser.
