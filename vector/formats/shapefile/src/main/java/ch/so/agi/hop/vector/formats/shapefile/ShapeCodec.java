package ch.so.agi.hop.vector.formats.shapefile;

import ch.so.agi.hop.vector.core.*;
import ch.so.agi.hop.vector.formats.shapefile.binary.ShapeType;
import java.nio.*;
import java.util.*;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.*;

/** Esri record bodies. All array sizes are checked before allocation. */
final class ShapeCodec {
  static final double NO_M = -1e39;

  static ShapeType type(GeometrySchema s) throws Exception {
    int base =
        switch (s.type().toUpperCase(Locale.ROOT)) {
          case "POINT" -> 1;
          case "MULTIPOINT" -> 8;
          case "LINE",
              "LINESTRING",
              "MULTILINESTRING",
              "POLYLINE",
              "CIRCULARSTRING",
              "COMPOUNDCURVE",
              "MULTICURVE" ->
              3;
          case "POLYGON", "MULTIPOLYGON", "CURVEPOLYGON", "MULTISURFACE" -> 5;
          default ->
              throw new IllegalArgumentException("Unsupported Shapefile geometry: " + s.type());
        };
    return ShapeType.fromCode(
        base + (s.z() != Ordinate.ABSENT ? 10 : s.m() != Ordinate.ABSENT ? 20 : 0));
  }

  static byte[] encode(Geometry g, GeometrySchema schema) throws Exception {
    if (g == null || g.isEmpty()) return new byte[4];
    ShapeType type = type(schema);
    int family = type.family();
    List<Coordinate[]> parts = new ArrayList<>();
    if (family == 1 && g instanceof Point p) parts.add(p.getCoordinates());
    else if (family == 8 && g instanceof MultiPoint p) parts.add(p.getCoordinates());
    else if (family == 3 && (g instanceof LineString || g instanceof MultiLineString)) {
      for (int i = 0; i < g.getNumGeometries(); i++) parts.add(g.getGeometryN(i).getCoordinates());
    } else if (family == 5 && (g instanceof Polygon || g instanceof MultiPolygon)) {
      for (int i = 0; i < g.getNumGeometries(); i++) {
        Polygon p = (Polygon) g.getGeometryN(i);
        parts.add(oriented(p.getExteriorRing().getCoordinates(), false));
        for (int j = 0; j < p.getNumInteriorRing(); j++)
          parts.add(oriented(p.getInteriorRingN(j).getCoordinates(), true));
      }
    } else
      throw new IllegalArgumentException(
          "Geometry family differs from output layer: " + g.getGeometryType());
    int n = parts.stream().mapToInt(a -> a.length).sum();
    boolean z = type.hasZ(), m = schema.m() != Ordinate.ABSENT || (family == 1 && z);
    long size = family == 1 ? 20 : family == 8 ? 40L + 16L * n : 44L + 4L * parts.size() + 16L * n;
    if (z) size += family == 1 ? 8 : 16L + 8L * n;
    if (m) size += family == 1 ? 8 : 16L + 8L * n;
    if (size > Integer.MAX_VALUE) throw new IllegalArgumentException("Shape record too large");
    ByteBuffer b = ByteBuffer.allocate((int) size).order(ByteOrder.LITTLE_ENDIAN);
    b.putInt(type.code());
    if (family != 1) {
      var e = g.getEnvelopeInternal();
      b.putDouble(e.getMinX()).putDouble(e.getMinY()).putDouble(e.getMaxX()).putDouble(e.getMaxY());
      if (family != 8) b.putInt(parts.size());
      b.putInt(n);
      if (family != 8) {
        int start = 0;
        for (var p : parts) {
          b.putInt(start);
          start += p.length;
        }
      }
    }
    for (var p : parts)
      for (var c : p) {
        if (!Double.isFinite(c.x) || !Double.isFinite(c.y))
          throw new IllegalArgumentException("Non-finite XY coordinate");
        if (!z && !Double.isNaN(c.getZ()))
          throw new IllegalArgumentException("Output schema would discard Z");
        if (schema.m() == Ordinate.ABSENT && !Double.isNaN(c.getM()))
          throw new IllegalArgumentException("Output schema would discard M");
        b.putDouble(c.x).putDouble(c.y);
      }
    if (z) ordinates(b, parts, true, family != 1);
    if (m) ordinates(b, parts, false, family != 1);
    return b.array();
  }

  private static Coordinate[] oriented(Coordinate[] coordinates, boolean ccw) {
    Coordinate[] result = coordinates.clone();
    if (Orientation.isCCW(result) != ccw)
      for (int i = 0, j = result.length - 1; i < j; i++, j--) {
        var c = result[i];
        result[i] = result[j];
        result[j] = c;
      }
    return result;
  }

  private static void ordinates(ByteBuffer b, List<Coordinate[]> parts, boolean z, boolean range) {
    double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
    for (var p : parts)
      for (var c : p) {
        double v = z ? c.getZ() : c.getM();
        if (z && !Double.isFinite(v))
          throw new IllegalArgumentException("Missing or non-finite required Z");
        if (!Double.isNaN(v) && (!Double.isFinite(v) || !z && v < -1e38))
          throw new IllegalArgumentException("Invalid ordinate");
        if (Double.isFinite(v)) {
          min = Math.min(min, v);
          max = Math.max(max, v);
        }
      }
    if (range)
      b.putDouble(min == Double.POSITIVE_INFINITY ? NO_M : min)
          .putDouble(max == Double.NEGATIVE_INFINITY ? NO_M : max);
    for (var p : parts)
      for (var c : p) {
        double v = z ? c.getZ() : c.getM();
        b.putDouble(Double.isNaN(v) ? NO_M : v);
      }
  }

  static Geometry decode(byte[] bytes, ShapeType expected, int srid) throws Exception {
    try {
      ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
      ShapeType t = ShapeType.fromCode(b.getInt());
      if (t == ShapeType.NULL) {
        if (b.remaining() != 0) throw new IllegalArgumentException("Invalid Null Shape length");
        return null;
      }
      if (t != expected || t == ShapeType.MULTIPATCH)
        throw new IllegalArgumentException("Record shape type differs from header");
      int family = t.family(), n = 1;
      int[] starts = {0};
      if (family != 1) {
        b.position(b.position() + 32);
        int np = family == 8 ? 1 : b.getInt();
        n = b.getInt();
        if (n < 0 || np < 0 || np > n || 16L * n + (family == 8 ? 0 : 4L * np) > b.remaining())
          throw new IllegalArgumentException("Invalid shape counts");
        starts = new int[np];
        if (family != 8)
          for (int i = 0; i < np; i++) {
            starts[i] = b.getInt();
            if (starts[i] < 0
                || starts[i] >= n
                || i == 0 && starts[i] != 0
                || i > 0 && starts[i] <= starts[i - 1])
              throw new IllegalArgumentException("Invalid part index");
          }
      }
      double[] x = new double[n], y = new double[n], z = new double[n], m = new double[n];
      Arrays.fill(z, Double.NaN);
      Arrays.fill(m, Double.NaN);
      for (int i = 0; i < n; i++) {
        x[i] = b.getDouble();
        y[i] = b.getDouble();
        if (!Double.isFinite(x[i]) || !Double.isFinite(y[i]))
          throw new IllegalArgumentException("Non-finite XY coordinate");
      }
      if (t.hasZ()) {
        if (family != 1) b.position(b.position() + 16);
        for (int i = 0; i < n; i++) {
          z[i] = b.getDouble();
          if (!Double.isFinite(z[i])) throw new IllegalArgumentException("Non-finite Z coordinate");
        }
      }
      boolean hasM = t.hasM() || t.hasZ() && b.remaining() > 0;
      if (hasM && b.remaining() > 0) {
        if (family != 1) b.position(b.position() + 16);
        for (int i = 0; i < n; i++) {
          double v = b.getDouble();
          if (!Double.isFinite(v)) throw new IllegalArgumentException("Non-finite M coordinate");
          m[i] = v < -1e38 ? Double.NaN : v;
        }
      }
      if (b.remaining() != 0) throw new IllegalArgumentException("Unexpected shape record bytes");
      Coordinate[] cs = new Coordinate[n];
      for (int i = 0; i < n; i++)
        cs[i] =
            t.hasZ()
                ? (hasM
                    ? new CoordinateXYZM(x[i], y[i], z[i], m[i])
                    : new Coordinate(x[i], y[i], z[i]))
                : (hasM ? new CoordinateXYM(x[i], y[i], m[i]) : new CoordinateXY(x[i], y[i]));
      GeometryFactory f = new GeometryFactory(new PrecisionModel(), srid);
      if (family == 1) return f.createPoint(cs[0]);
      if (family == 8) return f.createMultiPointFromCoords(cs);
      List<Coordinate[]> parts = new ArrayList<>();
      for (int i = 0; i < starts.length; i++)
        parts.add(Arrays.copyOfRange(cs, starts[i], i + 1 < starts.length ? starts[i + 1] : n));
      if (family == 3) {
        LineString[] lines = parts.stream().map(f::createLineString).toArray(LineString[]::new);
        return f.createMultiLineString(lines);
      }
      if (family == 5) return polygons(parts, f);
      throw new IllegalArgumentException("Unsupported shape type: " + t);
    } catch (BufferUnderflowException | IndexOutOfBoundsException e) {
      throw new IllegalArgumentException("Truncated shape record", e);
    }
  }

  private static Geometry polygons(List<Coordinate[]> parts, GeometryFactory f) {
    List<Polygon> rings = new ArrayList<>();
    for (var p : parts) rings.add(f.createPolygon(p));
    rings.sort(Comparator.comparingDouble(Polygon::getArea).reversed());
    int[] parent = new int[rings.size()], depth = new int[rings.size()];
    Arrays.fill(parent, -1);
    for (int i = 0; i < rings.size(); i++)
      for (int j = i - 1; j >= 0; j--) {
        if (rings.get(j).covers(rings.get(i))) {
          parent[i] = j;
          depth[i] = depth[j] + 1;
          break;
        }
      }
    List<Polygon> result = new ArrayList<>();
    for (int i = 0; i < rings.size(); i++)
      if (depth[i] % 2 == 0) {
        List<LinearRing> holes = new ArrayList<>();
        for (int j = i + 1; j < rings.size(); j++)
          if (parent[j] == i) holes.add(rings.get(j).getExteriorRing());
        result.add(
            f.createPolygon(rings.get(i).getExteriorRing(), holes.toArray(LinearRing[]::new)));
      }
    return f.createMultiPolygon(result.toArray(Polygon[]::new));
  }
}
