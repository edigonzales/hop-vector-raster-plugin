package ch.so.agi.hop.vector.core;

import org.locationtech.jts.geom.*;

public record GeometrySchema(
    String type, Ordinate z, Ordinate m, CrsDefinitionResolver.Definition crs) {
  public int srid() {
    return crs == null ? 0 : crs.srid();
  }

  public String dimension() {
    return "XY" + (z != Ordinate.ABSENT ? "Z" : "") + (m != Ordinate.ABSENT ? "M" : "");
  }

  public static GeometrySchema infer(Geometry g) {
    boolean[] dims = {false, false};
    g.apply(
        new CoordinateSequenceFilter() {
          public void filter(CoordinateSequence s, int i) {
            dims[0] |= s.hasZ() && !Double.isNaN(s.getZ(i));
            dims[1] |= s.hasM();
          }

          public boolean isDone() {
            return false;
          }

          public boolean isGeometryChanged() {
            return false;
          }
        });
    return new GeometrySchema(
        g.getGeometryType(),
        dims[0] ? Ordinate.REQUIRED : Ordinate.ABSENT,
        dims[1] ? Ordinate.OPTIONAL : Ordinate.ABSENT,
        new CrsDefinitionResolver.Definition(g.getSRID(), "", "", g.getSRID(), ""));
  }

  public static GeometrySchema explicit(
      String type, String dimension, CrsDefinitionResolver.Definition crs) {
    if (!java.util.Set.of("XY", "XYZ", "XYM", "XYZM").contains(dimension))
      throw new IllegalArgumentException("Invalid coordinate dimension: " + dimension);
    return new GeometrySchema(
        type,
        dimension.contains("Z") ? Ordinate.REQUIRED : Ordinate.ABSENT,
        dimension.contains("M") ? Ordinate.OPTIONAL : Ordinate.ABSENT,
        crs);
  }

  public Geometry emptyGeometry() {
    GeometryFactory f = new GeometryFactory(new PrecisionModel(), srid());
    return switch (type.toUpperCase(java.util.Locale.ROOT)) {
      case "POINT" -> f.createPoint();
      case "MULTIPOINT" -> f.createMultiPoint();
      case "LINESTRING", "LINE", "POLYLINE" -> f.createLineString();
      case "MULTILINESTRING" -> f.createMultiLineString();
      case "POLYGON" -> f.createPolygon();
      case "MULTIPOLYGON" -> f.createMultiPolygon();
      default ->
          throw new IllegalArgumentException("Explicit geometry type not supported: " + type);
    };
  }
}
