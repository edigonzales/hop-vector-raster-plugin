package ch.so.agi.hop.vector.core;

import org.apache.hop.core.row.IRowMeta;

/** Format-neutral source metadata. Field widths and scales live in rowMeta. */
public record LayerSchema(
    String name,
    String geometryColumn,
    GeometrySchema geometry,
    IRowMeta rowMeta,
    XYPrecision xyPrecision) {
  public record XYPrecision(double resolution, double tolerance, double xOrigin, double yOrigin) {}

  public LayerSchema(String name, String column, GeometrySchema geometry, IRowMeta rowMeta) {
    this(name, column, geometry, rowMeta, null);
  }

  public LayerSchema(String name, String column, String type, int srid, IRowMeta rm) {
    this(
        name,
        column,
        new GeometrySchema(
            type,
            Ordinate.ABSENT,
            Ordinate.ABSENT,
            new CrsDefinitionResolver.Definition(srid, "", "", srid, "")),
        rm);
  }

  public String geometryType() {
    return geometry == null ? "" : geometry.type();
  }

  public int srid() {
    return geometry == null ? 0 : geometry.srid();
  }

  public Ordinate z() {
    return geometry == null ? Ordinate.ABSENT : geometry.z();
  }

  public Ordinate m() {
    return geometry == null ? Ordinate.ABSENT : geometry.m();
  }
}
