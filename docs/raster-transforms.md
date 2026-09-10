# Raster transforms

## Sources, CRS and errors

All raster transforms accept a local GeoTIFF path or public HTTP/HTTPS COG URL. A source may be a constant (including Hop variables) or the name of an input field. Reuse the reader while the source is unchanged. HTTP servers must support byte ranges with HTTP 206; a server that returns the complete file is rejected. Credentials and custom headers are not supported in this version.

Clip and Zonal Statistics bands are **1 based** in the dialog. Geometry uses the shared Hop Geometry type. An explicit CRS (for example `EPSG:2056`) overrides its SRID; otherwise a nonzero SRID is required. Coordinates use XY/lon-lat order. An input geometry is copied before CRS transformation, so the original geometry and other attributes remain unchanged. Polygon/MultiPolygon zones may have holes. Curved and invalid geometries must be handled upstream.

Pixel selection uses **CENTER**: include a pixel if the polygon covers its centre, including a centre exactly on its boundary. NoData/masked/nonfinite samples are excluded before applying band scale/offset. Zero is valid. A NoData override replaces the raster's declared sentinel. `ALL_TOUCHED`, fractional weighting and percentiles are not offered. Clip and Zonal Statistics do not resample; the separate Reproject / Resample transform is described below.

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
mvn -B -ntp -pl raster/raster-core -am test \
  -Dtest=RemoteCogTest -Dsurefire.failIfNoSpecifiedTests=false -DremoteCogTests=true
```

It checks both sources at the box above, writes clips in `raster/raster-core/target/cog-smoke`, opens them again, and compares valid counts and means. It verifies the expected output dimensions and a bounded transfer volume. It logs requests/bytes and timings; normal CI does not depend on these external servers. The large files are not downloaded in full.

For the tested snapshot on 2026-09-09, the nDSM returned 130009 valid pixels with mean 13.589200030; the DTM returned 40000 with mean 430.198857469. These are acceptance observations for this box, not immutable assertions about future data at the `aktuell` URLs.

Deterministic tests use synthetic TIFF/BigTIFF fixtures, a local range server, poisoned overview values, and known polygon statistics. They cover cache reuse, >4 GiB HTTP offsets, ignored/malformed ranges, changing resources, CRS, holes/multipart, NoData/zero, scaling, integer output and cancellation. Hop contract tests exercise metadata XML persistence, row enrichment and GENERATE sink completion. The existing Linux/macOS/Windows Java 17 CI matrix also runs the distribution audit.

## Raster Reproject / Resample (GeoTools)

Pipeline: **Generate Rows (one row) → Raster Reproject / Resample (GeoTools)**.
Each incoming row writes one local GeoTIFF and appends `<prefix>output_file` and
`<prefix>status = OK`. The default prefix is `raster_`. Input columns are retained;
output name collisions are errors. This transform always processes all bands.

| Setting | Behavior |
|---|---|
| Input raster | Local GeoTIFF or public HTTP/HTTPS COG URL |
| Target CRS | EPSG code; empty keeps the source CRS |
| Pixel size X / Y | Required positive, finite values in target CRS units |
| Extent | `AUTO` or `BOUNDING_BOX`; box coordinates are in the target CRS |
| Interpolation | `NEAREST` (default) or `BILINEAR` |
| Numeric output type | `AUTO` (default), `SOURCE`, `FLOAT32`, `FLOAT64` |
| Source NoData override | Empty uses source metadata; a value replaces its sentinel |
| Output NoData | Empty uses source override/metadata, then NaN for floating point |
| Output GeoTIFF | Local destination; parent directory must exist |
| Overwrite | Off by default |

Source, target CRS, pixel sizes, bounding-box coordinates and output path accept
constants with Hop variables or input field names using their checkboxes. NoData
settings and output prefix accept Hop variables. Use a unique destination per row
and transform copy. One reader is reused until the source changes.

### Target grid

The output is north-up, with XY/lon-lat axis order. Pixel boundaries are integer
multiples of the X/Y resolutions relative to the target CRS origin; the requested
extent expands outwards to those boundaries. A box may therefore grow by less
than one pixel along each edge. Equal CRS and resolution settings produce aligned
pixel grids. Resolutions are never chosen automatically. A degree-based CRS uses
degrees, not metres.

Automatic bounds transform the actual outer source pixel edges, including rotated
source grids, with 256 segments per edge. Ambiguous boundaries, longitude
wraparound, missing CRS and failed coordinate transformations are errors. A
fully disjoint explicit box is an error. A partially overlapping box is retained
in full and filled with NoData/transparency outside the source. An overlapping
raster containing only NoData is a valid output.

### Numerical bands

Numeric bands retain their order and individual raw scale/offset metadata.
`AUTO` keeps the source sample type for Nearest and uses Float64 for Bilinear.
Float32/Float64 can also be explicitly selected. `SOURCE` with integer samples
rounds interpolated values to the nearest integer, with ties to even. Overflow
fails the row instead of clipping the value to a type boundary.

NoData, masks and nonfinite values are excluded before interpolation. Bilinear
renormalizes the weights of valid neighbours; no valid contributor yields NoData.
Zero is valid. Interpolation is on raw samples, with scale/offset retained rather
than applied twice. A finite output sentinel must be exactly representable;
integer output without an inherited sentinel requires an explicit value. A valid
result colliding with the chosen sentinel is an error. NoData is common to the
output TIFF; band-specific source validity is evaluated independently.

### Colors, palettes and transparency

TIFF photometric and extra-sample tags identify colors and alpha; three bands do
not automatically imply RGB. Numeric output type settings do not convert colors.
RGB and gray-with-alpha retain unsigned Byte/UInt16 or floating-point sample types.
Float colors/alpha use 0–1. RGB output includes an unassociated alpha band; gray
with alpha retains two bands. Pixels outside the source are transparent. RGB
source NoData marks a pixel when all color channels match the sentinel; a valid
zero-valued individual channel is retained.

Nearest preserves palette indices and the full UInt16 color table. For transparent
output cells it needs a declared or explicit unused NoData index. If no such index
exists, choose an RGBA workflow; the transform does not discard a valid index to
make room. The storage table may be padded to the Byte/UInt16 index capacity.
Bilinear expands palette images to UInt16 RGBA. Alpha-weighted color interpolation
prevents transparent pixels from introducing color fringes; premultiplied source
alpha is converted to unassociated output alpha. Interpolation uses stored color
values, without a separate color-space conversion. Nonstandard color spaces or
extra-channel layouts fail explicitly.

### Resources, output and examples

Output uses 512 × 512 DEFLATE tiles, with BigTIFF selected when estimated raw size
reaches 2 GiB. This is GeoTIFF, not a promised COG. The source and destination must
differ, including filesystem aliases. Writing stages a temporary sibling file;
errors and cancellation preserve an existing destination and remove the temporary
file. Technical errors use a configured Hop error hop or fail the transform.

The source cache is capped at 64 MiB per copy. Reprojection adds capped 16 MiB
output and 8 MiB operation caches. Intermediate source blocks hold at most 262144
pixels and are split further when a coarse target would span a large source
window. These caps are not a bound on the entire JVM heap: output tiles, reader
buffers and per-band working arrays also require memory. Source reads always use
original-resolution pixels, not overviews.

Runnable templates in `examples/raster-reproject/` contain one Generate Rows
operation and this transform:

- `elevation.hpl`: Float64 Bilinear output of a 100 m Solothurn DTM box at 1 m resolution.
- `multiband.hpl`: all numeric bands, configurable target CRS and resolution.
- `palette.hpl`: Nearest palette preservation, configurable target CRS and resolution.

Set their `INPUT_RASTER` / `OUTPUT_FILE` parameters before running; for the latter
two also supply resolutions and an output sentinel where needed. Referenced input
files are never downloaded or bundled. Reference-grid matching, automatic pixel
size, Bicubic, Average and polygon masks are outside this transform's scope.

Tests cover numeric and color roundtrips, NoData interpolation, source windows,
CRS/grid alignment, tile seams, HTTP COG byte ranges and cache reuse, cancellation,
metadata persistence and Hop row/error handling. Run with:

```bash
mvn -B -ntp -pl raster/raster-reproject -am test
```
