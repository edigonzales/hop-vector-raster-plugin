package ch.so.agi.hop.vector.core;

/** FileGDB storage precision and optional inclusive envelope filter in source CRS units. */
public record FileGeodatabaseOptions(
    String precisionMode,
    Double xyResolution,
    Double xyTolerance,
    Double xOrigin,
    Double yOrigin,
    boolean spatialIndex,
    Bounds filter)
    implements FormatOptions {
  public record Bounds(double xMin, double yMin, double xMax, double yMax) {
    public Bounds {
      if (!Double.isFinite(xMin)
          || !Double.isFinite(yMin)
          || !Double.isFinite(xMax)
          || !Double.isFinite(yMax)
          || xMin > xMax
          || yMin > yMax) throw new IllegalArgumentException("Invalid FileGDB query bounds");
    }
  }

  public FileGeodatabaseOptions {
    precisionMode = precisionMode == null ? "LEGACY" : precisionMode;
    if (!precisionMode.equals("LEGACY") && !precisionMode.equals("AUTO"))
      throw new IllegalArgumentException("Unknown precision mode");
    if (xyResolution != null && (!Double.isFinite(xyResolution) || xyResolution <= 0))
      throw new IllegalArgumentException("XY resolution must be positive and finite");
    if (xyTolerance != null && (!Double.isFinite(xyTolerance) || xyTolerance <= 0))
      throw new IllegalArgumentException("XY tolerance must be positive and finite");
    if ((xOrigin == null) != (yOrigin == null))
      throw new IllegalArgumentException("Specify both XY origins");
    if (xOrigin != null && (!Double.isFinite(xOrigin) || !Double.isFinite(yOrigin)))
      throw new IllegalArgumentException("XY origins must be finite");
  }

  public static FileGeodatabaseOptions defaults() {
    return new FileGeodatabaseOptions("LEGACY", null, null, null, null, true, null);
  }

  public static Double optional(String v) {
    return v == null || v.isBlank() ? null : Double.valueOf(v.trim());
  }

  public static Bounds bounds(String xmin, String ymin, String xmax, String ymax) {
    Double[] v = {optional(xmin), optional(ymin), optional(xmax), optional(ymax)};
    if (java.util.Arrays.stream(v).allMatch(java.util.Objects::isNull)) return null;
    if (java.util.Arrays.stream(v).anyMatch(java.util.Objects::isNull))
      throw new IllegalArgumentException("Specify all four FileGDB bounds");
    return new Bounds(v[0], v[1], v[2], v[3]);
  }
}
