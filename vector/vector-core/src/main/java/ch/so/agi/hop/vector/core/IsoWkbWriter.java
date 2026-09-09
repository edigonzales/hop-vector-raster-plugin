package ch.so.agi.hop.vector.core;

import java.io.*;
import org.locationtech.jts.geom.*;

/** ISO 13249 dimensional type codes, big endian, no EWKB flags or embedded SRID. */
public final class IsoWkbWriter {
  private IsoWkbWriter() {}

  public static byte[] write(Geometry g, GeometrySchema schema) throws IOException {
    var bytes = new ByteArrayOutputStream();
    write(
        new DataOutputStream(bytes),
        g,
        schema.z() != Ordinate.ABSENT,
        schema.m() != Ordinate.ABSENT);
    return bytes.toByteArray();
  }

  private static void write(DataOutputStream out, Geometry g, boolean z, boolean m)
      throws IOException {
    int type =
        g instanceof Point
            ? 1
            : g instanceof LineString
                ? 2
                : g instanceof Polygon
                    ? 3
                    : g instanceof MultiPoint
                        ? 4
                        : g instanceof MultiLineString ? 5 : g instanceof MultiPolygon ? 6 : 0;
    if (type == 0)
      throw new IllegalArgumentException("Unsupported WKB geometry " + g.getGeometryType());
    out.writeByte(0);
    out.writeInt(type + (z ? 1000 : 0) + (m ? 2000 : 0));
    if (g instanceof Point p) {
      if (p.isEmpty())
        for (int i = 0; i < 2 + (z ? 1 : 0) + (m ? 1 : 0); i++) out.writeDouble(Double.NaN);
      else coordinate(out, p.getCoordinateSequence(), 0, z, m);
    } else if (g instanceof LineString l) sequence(out, l.getCoordinateSequence(), z, m);
    else if (g instanceof Polygon p) {
      out.writeInt(p.isEmpty() ? 0 : p.getNumInteriorRing() + 1);
      if (!p.isEmpty()) {
        sequence(out, p.getExteriorRing().getCoordinateSequence(), z, m);
        for (int i = 0; i < p.getNumInteriorRing(); i++)
          sequence(out, p.getInteriorRingN(i).getCoordinateSequence(), z, m);
      }
    } else {
      out.writeInt(g.getNumGeometries());
      for (int i = 0; i < g.getNumGeometries(); i++) write(out, g.getGeometryN(i), z, m);
    }
  }

  private static void sequence(DataOutputStream out, CoordinateSequence q, boolean z, boolean m)
      throws IOException {
    out.writeInt(q.size());
    for (int i = 0; i < q.size(); i++) coordinate(out, q, i, z, m);
  }

  private static void coordinate(
      DataOutputStream out, CoordinateSequence q, int i, boolean z, boolean m) throws IOException {
    out.writeDouble(q.getX(i));
    out.writeDouble(q.getY(i));
    if (z) out.writeDouble(q.hasZ() ? q.getZ(i) : Double.NaN);
    if (m) out.writeDouble(q.hasM() ? q.getM(i) : Double.NaN);
  }
}
