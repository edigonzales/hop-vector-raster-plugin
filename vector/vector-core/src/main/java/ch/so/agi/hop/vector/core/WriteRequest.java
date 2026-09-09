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
    Diagnostics diagnostics) {
  public WriteRequest {
    if (geometry == null && sample != null) geometry = GeometrySchema.infer(sample);
    if (sample == null && geometry != null) sample = geometry.emptyGeometry();
    options = options == null ? new FormatOptions.None() : options;
    diagnostics = diagnostics == null ? Diagnostics.NONE : diagnostics;
  }

  public ShapefileOptions shapefile() {
    return options instanceof ShapefileOptions s ? s : ShapefileOptions.defaults();
  }

  public GenerateOptions generate() {
    return options instanceof GenerateOptions s ? s : GenerateOptions.defaults();
  }
}
