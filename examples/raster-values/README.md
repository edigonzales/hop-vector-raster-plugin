# Raster value chain

`clip-reproject.hpl` reads once, plans a bounding-box clip and resampling, and writes once.
No intermediate TIFF is produced. Supply the input/output paths and a bounding box in
`SOURCE_CRS`; specify `TARGET_CRS` and pixel size in target CRS units. The defaults describe
a Swiss EPSG:2056 extent. The Reader has no upstream rows and emits one Raster value.

The Clip selects all bands. Numeric integer inputs without NoData need an explicit compatible
NoData setting in Clip. Existing output files are rejected by default. For per-feature clips,
connect a Vector Reader before the Raster Reader and select a geometry field in Clip.

Attach an Error Hop to the Writer for pixel failures; attach one to Reader/Clip/Reproject for
source and planning failures. The error description includes the source, consuming transform
and operation names. Without an Error Hop the example fails the pipeline on any error.
