package ch.so.agi.hop.vector.formats.flatgeobuf;

import com.google.flatbuffers.FlatBufferBuilder;
import java.util.*;
import org.locationtech.jts.geom.*;
import org.wololo.flatgeobuf.generated.GeometryType;

final class FgbGeometry {
  static byte type(String type) {
    return switch (type.toUpperCase(Locale.ROOT)) {
      case "POINT" -> GeometryType.Point;
      case "MULTIPOINT" -> GeometryType.MultiPoint;
      case "LINESTRING" -> GeometryType.LineString;
      case "MULTILINESTRING" -> GeometryType.MultiLineString;
      case "POLYGON" -> GeometryType.Polygon;
      case "MULTIPOLYGON" -> GeometryType.MultiPolygon;
      default -> throw new IllegalArgumentException("Unsupported geometry type: " + type);
    };
  }

  static int encode(
      FlatBufferBuilder b, org.locationtech.jts.geom.Geometry g, boolean z, boolean m) {
    if (g instanceof MultiPolygon mp) {
      int[] parts = new int[mp.getNumGeometries()];
      for (int i = 0; i < parts.length; i++) parts[i] = encode(b, mp.getGeometryN(i), z, m);
      int p = org.wololo.flatgeobuf.generated.Geometry.createPartsVector(b, parts);
      return org.wololo.flatgeobuf.generated.Geometry.createGeometry(
          b, 0, 0, 0, 0, 0, 0, GeometryType.MultiPolygon, p);
    }
    List<CoordinateSequence> seqs = new ArrayList<>();
    if (g instanceof Polygon p) {
      seqs.add(p.getExteriorRing().getCoordinateSequence());
      for (int i = 0; i < p.getNumInteriorRing(); i++)
        seqs.add(p.getInteriorRingN(i).getCoordinateSequence());
    } else
      g.apply(
          new GeometryComponentFilter() {
            public void filter(org.locationtech.jts.geom.Geometry c) {
              if (c instanceof Point p) seqs.add(p.getCoordinateSequence());
              else if (c instanceof LineString l) seqs.add(l.getCoordinateSequence());
            }
          });
    int count = seqs.stream().mapToInt(CoordinateSequence::size).sum(), k = 0, part = 0;
    double[] xy = new double[count * 2],
        zs = z ? new double[count] : null,
        ms = m ? new double[count] : null;
    long[] ends = new long[seqs.size()];
    for (var q : seqs) {
      for (int i = 0; i < q.size(); i++) {
        xy[2 * k] = q.getX(i);
        xy[2 * k + 1] = q.getY(i);
        if (z) zs[k] = q.hasZ() ? q.getZ(i) : Double.NaN;
        if (m) ms[k] = q.hasM() ? q.getM(i) : Double.NaN;
        k++;
      }
      ends[part++] = k;
    }
    int x = org.wololo.flatgeobuf.generated.Geometry.createXyVector(b, xy);
    int zv = z ? org.wololo.flatgeobuf.generated.Geometry.createZVector(b, zs) : 0;
    int mv = m ? org.wololo.flatgeobuf.generated.Geometry.createMVector(b, ms) : 0;
    int e =
        (g instanceof Polygon || g instanceof MultiLineString) && ends.length > 1
            ? org.wololo.flatgeobuf.generated.Geometry.createEndsVector(b, ends)
            : 0;
    return org.wololo.flatgeobuf.generated.Geometry.createGeometry(
        b, e, x, zv, mv, 0, 0, type(g.getGeometryType()), 0);
  }
}
