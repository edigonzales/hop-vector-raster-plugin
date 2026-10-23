package ch.so.agi.hop.vector.core;

import java.nio.file.Path;
import org.apache.hop.core.row.IRowMeta;
import org.locationtech.jts.geom.Geometry;

public record WriteRequest(
    Path file,
    String layer,
    IRowMeta rowMeta,
    int geometryIndex,
    Geometry sample,
    GeometrySchema geometry,
    FormatOptions options,
    Diagnostics diagnostics,
    java.util.function.BooleanSupplier cancelled) {
  public WriteRequest(
      Path file,
      String layer,
      IRowMeta rowMeta,
      int geometryIndex,
      Geometry sample,
      GeometrySchema geometry,
      FormatOptions options,
      Diagnostics diagnostics) {
    this(file, layer, rowMeta, geometryIndex, sample, geometry, options, diagnostics, () -> false);
  }

  public void checkCancelled() throws java.io.InterruptedIOException {
    StagedVectorFile.checkCancelled();
    if (cancelled.getAsBoolean()) throw new java.io.InterruptedIOException("Vector export stopped");
  }

  public WriteRequest {
    cancelled = cancelled == null ? () -> false : cancelled;
    options = options == null ? new FormatOptions.None() : options;
    // GeoJSON represents each feature independently; sampling would traverse native curves and
    // collections before the shared codec can enforce its depth and coordinate budgets.
    boolean geoJson =
        options instanceof GeoJsonOptions
            || options instanceof ch.so.agi.hop.vector.core.geojson.GeoJsonFeatureOptions;
    if (!geoJson && geometry == null && sample != null) geometry = GeometrySchema.infer(sample);
    if (!geoJson && sample == null && geometry != null) sample = geometry.emptyGeometry();
    diagnostics = diagnostics == null ? Diagnostics.NONE : diagnostics;
  }

  public ShapefileOptions shapefile() {
    return options instanceof ShapefileOptions s ? s : ShapefileOptions.defaults();
  }

  public GenerateOptions generate() {
    return options instanceof GenerateOptions s ? s : GenerateOptions.defaults();
  }
}
