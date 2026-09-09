package ch.so.agi.hop.vector.core;

import java.nio.file.Path;

public record ReadRequest(
    Path file,
    String layer,
    String geometryField,
    String crsOverride,
    FormatOptions options,
    Diagnostics diagnostics) {
  public ReadRequest {
    layer = layer == null ? "" : layer;
    geometryField = geometryField == null ? "" : geometryField;
    crsOverride = crsOverride == null ? "" : crsOverride;
    options = options == null ? new FormatOptions.None() : options;
    diagnostics = diagnostics == null ? Diagnostics.NONE : diagnostics;
  }

  public ReadRequest(Path file, String layer, String field) {
    this(file, layer, field, "", new FormatOptions.None(), Diagnostics.NONE);
  }

  public ShapefileOptions shapefile() {
    return options instanceof ShapefileOptions s ? s : ShapefileOptions.defaults();
  }
}
