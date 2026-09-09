package ch.so.agi.hop.vector.core;

import com.atolcd.hop.gis.geometry.curve.CurveGeometrySupport;
import org.apache.hop.core.row.IValueMeta;
import org.locationtech.jts.geom.*;

/** Validation shared by the two cloud-format writers; never modifies the input geometry. */
public final class CloudGeometry {
  private CloudGeometry() {}

  public static void validateFields(WriteRequest r) {
    if (r.geometry() == null || r.rowMeta() == null || r.geometryIndex() < 0)
      throw new IllegalArgumentException("Geometry and attribute schema required");
    r.geometry().emptyGeometry();
    var names = new java.util.HashSet<String>();
    for (int i = 0; i < r.rowMeta().size(); i++) {
      var f = r.rowMeta().getValueMeta(i);
      if (f.getName() == null || f.getName().isBlank() || !names.add(f.getName()))
        throw new IllegalArgumentException("Empty or duplicate field name: " + f.getName());
      if (i == r.geometryIndex()) continue;
      switch (f.getType()) {
        case IValueMeta.TYPE_STRING,
            IValueMeta.TYPE_BOOLEAN,
            IValueMeta.TYPE_INTEGER,
            IValueMeta.TYPE_NUMBER,
            IValueMeta.TYPE_BIGNUMBER,
            IValueMeta.TYPE_DATE,
            IValueMeta.TYPE_TIMESTAMP,
            IValueMeta.TYPE_BINARY -> {}
        default ->
            throw new IllegalArgumentException(
                "Convert unsupported attribute '"
                    + f.getName()
                    + "' before exporting (only one Geometry field is supported)");
      }
    }
  }

  public static Geometry prepare(WriteRequest r, Object value) {
    if (value == null) return null;
    if (!(value instanceof Geometry g)) throw new IllegalArgumentException("Expected Hop Geometry");
    if (CurveGeometrySupport.isCurveGeometry(g))
      r.diagnostics().warning("geometry", "curve", "Curves are linearized for export");
    GeometrySchema s = r.geometry();
    if (g.getSRID() != 0 && s.srid() != 0 && g.getSRID() != s.srid())
      throw new IllegalArgumentException("Conflicting geometry SRID");
    String type = s.type().toUpperCase(java.util.Locale.ROOT);
    Geometry result = g;
    if (!g.getGeometryType().equalsIgnoreCase(type)) {
      var f = g.getFactory();
      result =
          switch (type) {
            case "MULTIPOINT" -> g instanceof Point p ? f.createMultiPoint(new Point[] {p}) : null;
            case "MULTILINESTRING" ->
                g instanceof LineString l ? f.createMultiLineString(new LineString[] {l}) : null;
            case "MULTIPOLYGON" ->
                g instanceof Polygon p ? f.createMultiPolygon(new Polygon[] {p}) : null;
            default -> null;
          };
      if (result == null)
        throw new IllegalArgumentException("Geometry does not match output type " + s.type());
    }
    result.apply(
        new CoordinateSequenceFilter() {
          public boolean isDone() {
            return false;
          }

          public boolean isGeometryChanged() {
            return false;
          }

          public void filter(CoordinateSequence q, int i) {
            if (!Double.isFinite(q.getX(i)) || !Double.isFinite(q.getY(i)))
              throw new IllegalArgumentException("Non-finite XY coordinate");
            if (s.z() == Ordinate.ABSENT && q.hasZ() && !Double.isNaN(q.getZ(i)))
              throw new IllegalArgumentException("Extra Z ordinate");
            if (s.m() == Ordinate.ABSENT && q.hasM())
              throw new IllegalArgumentException("Extra M ordinate");
            if (s.z() == Ordinate.REQUIRED && (!q.hasZ() || !Double.isFinite(q.getZ(i))))
              throw new IllegalArgumentException("Required Z ordinate missing");
            if (q.hasM() && Double.isInfinite(q.getM(i)))
              throw new IllegalArgumentException("Infinite M ordinate");
          }
        });
    return result;
  }
}
