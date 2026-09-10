# Example pipelines

Open an `.hpl` in Hop GUI and supply its parameters in the run configuration.
Install the Vector Raster and Geometry plugins first. Output directories must exist;
use new output filenames. No external raster files are stored in this repository.

| Pipeline | Parameters / behaviour |
|---|---|
| [FlatGeobuf](cloud-output/vector-to-flatgeobuf.hpl) | `INPUT_VECTOR`, `INPUT_LAYER`, `OUTPUT_FILE`; indexed output |
| [Parquet](cloud-output/vector-to-parquet.hpl) | Same parameters; native GEOMETRY, GZIP |
| [COG clip](raster-clip/clip-cog.hpl) | `INPUT_RASTER`, `BBOX_CRS`, `MIN_X`, `MIN_Y`, `MAX_X`, `MAX_Y`, `NODATA`, `OUTPUT_FILE`; band 1, source grid |
| [Elevation resampling](raster-reproject/elevation.hpl) | Raster, output, target CRS and pixel sizes; fixed Solothurn bounding box |
| [Multiband resampling](raster-reproject/multiband.hpl) | Raster, output, target CRS, pixel sizes, output NoData; bilinear |
| [Palette resampling](raster-reproject/palette.hpl) | Same parameters; nearest, palette preserved |
| [Zonal statistics](raster-stats/zonal-statistics.hpl) | `INPUT_VECTOR`, `INPUT_LAYER`, `INPUT_RASTER`, `GEOMETRY_CRS`, `NODATA`; band 1, output rows for preview |

Raster examples accept local GeoTIFFs or public HTTP COGs. Band numbers are integer
dialog settings, not variable parameters. Detailed German instructions and complete
parameter examples are in the [handbook](https://edigonzales.github.io/hop-vector-raster-plugin/benutzerhandbuch/main/index.html#beispiele).

After `mvn clean verify`, run `python3 scripts/check-doc-examples.py` with Java 21
to execute the clip and statistics pipelines using small local fixtures.
