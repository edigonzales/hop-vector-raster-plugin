# Raster transforms

## Sources, CRS and errors

Both transforms accept a local GeoTIFF path or public HTTP/HTTPS COG URL. A source may be a constant (including Hop variables) or the name of an input field. Reuse the reader while the source is unchanged. HTTP servers must support byte ranges with HTTP 206; a server that returns the complete file is rejected. Credentials and custom headers are not supported in this version.

Bands are **1 based** in the dialog. Geometry uses the shared Hop Geometry type. An explicit CRS (for example `EPSG:2056`) overrides its SRID; otherwise a nonzero SRID is required. Coordinates use XY/lon-lat order. An input geometry is copied before CRS transformation, so the original geometry and other attributes remain unchanged. Polygon/MultiPolygon zones may have holes. Curved and invalid geometries must be handled upstream.

Pixel selection uses **CENTER**: include a pixel if the polygon covers its centre, including a centre exactly on its boundary. NoData/masked/nonfinite samples are excluded before applying band scale/offset. Zero is valid. A NoData override replaces the raster's declared sentinel. `ALL_TOUCHED`, fractional weighting, percentiles and resampling are not offered.

Technical failures stop execution unless a Hop error hop is configured. They are never returned as successful zero-count rows. Sources close on failure, source change, EOF and disposal. Cache capacity is 64 MiB for raw windows per transform copy, with a separate capped metadata cache. This is not a bound on the entire JVM's heap.

## Raster Clip (GeoTools)

Every input row describes one operation. To clip a single constant box, place **Generate Rows** before Clip and generate one row.

Example settings:

| Setting | Value |
|---|---|
| Input raster | public COG URL below |
| Clip method | `BOUNDING_BOX` |
| Minimum X / Y | `2607400` / `1228400` |
| Maximum X / Y | `2607500` / `1228500` |
| Explicit CRS | `EPSG:2056` |
| Output GeoTIFF | `${OUTPUT_DIR}/solothurn.tif` |
| Band | `1` |

Box coordinates and output path can alternatively name input fields (enable their respective checkboxes). In `POLYGON` mode, select the geometry field. Output appends `<prefix>output_file` and `<prefix>status` (default prefix `raster_`). An existing field with either output name is an error.

The output uses the source pixel grid, selected raw band type, CRS, scale/offset and NoData. Box mode crops the envelope transformed into the source CRS; polygon mode additionally masks pixels outside the geometry. It clips the overlapping portion; a wholly disjoint or empty region is an error. It does not resample/reproject raster pixels. Compression is DEFLATE, tiles are 512 × 512 and sufficiently large output uses BigTIFF. This output is **GeoTIFF, not a promised COG**.

The parent output directory must exist. The output must differ from the input. Existing output requires the overwrite checkbox. Use unique output paths per row/copy. A temporary sibling file is moved into place after a successful write; errors/cancellation preserve the previous destination. For integer input without NoData, supply an exactly representable sentinel; floating-point input can use NaN.

## Raster Zonal Statistics (GeoTools)

Pipeline: **Vector Reader → Raster Zonal Statistics (GeoTools) → Vector Writer**.

Use the buildings GeoPackage as vector input, set the reader's geometry output field to `geometry`, and configure:

| Setting | Value |
|---|---|
| Input raster | buildings nDSM URL below |
| Geometry field | `geometry` |
| Explicit CRS | empty if the geometry SRID is correct |
| Statistics | `mean min max stddev` |
| Output prefix | `height_` |

The transform appends the selected statistics in the listed order, then `count` if not already selected, and `status`. Selection accepts spaces, commas or semicolons. Standard deviation is the population standard deviation. `count` counts valid pixels; it is always present. Technical failures use an error hop or fail the transform.

| Status | Count/statistics |
|---|---|
| `OK` | positive count and numeric selected statistics |
| `EMPTY_GEOMETRY` | null/empty zone; zero count, null statistics |
| `NO_VALID_PIXELS` | outside raster, NoData only, or no covered pixel centres; zero count, null statistics |

The buildings nDSM already contains building heights. Do **not** subtract the terrain model. `height_mean` means mean valid raster height within the polygon; it is not automatically a ridge, eaves or regulatory building height. The terrain dataset supplies ground elevations when used separately.

## Public acceptance datasets

- [Buildings nDSM 2023](https://files.geo.so.ch/ch.swisstopo.lidar_2023.ndsm_buildings/aktuell/ch.swisstopo.lidar_2023.ndsm_buildings.tif): 0.25 m, EPSG:2056, Float32, NoData -9999, about 3.12 GB.
- [swissALTI3D DTM 2025](https://files.geo.so.ch/ch.swisstopo.swissalti3d_2025.dtm/aktuell/ch.swisstopo.swissalti3d_2025.dtm.tif): 0.5 m, EPSG:2056, Float32, NoData -9999, about 9.82 GB.

Run the external acceptance test explicitly:

```bash
mvn -B -ntp -pl hop-geotools-raster-core -am test \
  -Dtest=RemoteCogTest -Dsurefire.failIfNoSpecifiedTests=false -DremoteCogTests=true
```

It checks both sources at the box above, writes clips in `hop-geotools-raster-core/target/cog-smoke`, opens them again, and compares valid counts and means. It verifies the expected output dimensions and a bounded transfer volume. It logs requests/bytes and timings; normal CI does not depend on these external servers. The large files are not downloaded in full.

For the tested snapshot on 2026-09-08, the nDSM returned 130009 valid pixels with mean 13.589200030; the DTM returned 40000 with mean 430.198857469. These are acceptance observations for this box, not immutable assertions about future data at the `aktuell` URLs.

Deterministic tests use synthetic TIFF/BigTIFF fixtures, a local range server, poisoned overview values, and known polygon statistics. They cover cache reuse, >4 GiB HTTP offsets, ignored/malformed ranges, changing resources, CRS, holes/multipart, NoData/zero, scaling, integer output and cancellation. Hop contract tests exercise metadata XML persistence, row enrichment and GENERATE sink completion. The existing Linux/macOS/Windows Java 17 CI matrix also runs the distribution audit.
