package ch.so.agi.hop.vector.formats.geopackage;

import com.atolcd.hop.gis.geometry.curve.*;
import java.nio.*;
import java.util.*;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.*;

/** OGC GeoPackageBinary v0 wrapping standard 2D WKB / SQL-MM curve WKB. */
public final class GeoPackageBinary {
  private GeoPackageBinary() {}

  public static byte[] encode(Geometry geometry) {
    if (geometry == null) return null;
    rejectCurvesInLinearCollection(geometry);
    for (Coordinate c : geometry.getCoordinates())
      if (!Double.isNaN(c.getZ()) || !Double.isNaN(c.getM()))
        throw new IllegalArgumentException(
            "GeoPackage adapter supports XY only; Z/M cannot be discarded");
    Geometry copy = CurveGeometrySupport.copy(geometry);
    copy.setSRID(0);
    byte[] wkb =
        CurveGeometrySupport.isCurveGeometry(copy)
            ? new CurveWkbWriter().write(copy)
            : new WKBWriter(2, ByteOrderValues.LITTLE_ENDIAN, false).write(copy);
    validateWkb(wkb);
    ByteBuffer b = ByteBuffer.allocate(8 + wkb.length).order(ByteOrder.LITTLE_ENDIAN);
    b.put((byte) 'G')
        .put((byte) 'P')
        .put((byte) 0)
        .put((byte) (1 | (geometry.isEmpty() ? 16 : 0)))
        .putInt(geometry.getSRID())
        .put(wkb);
    return b.array();
  }

  private static void rejectCurvesInLinearCollection(Geometry geometry) {
    if (geometry instanceof GeometryCollection collection
        && !CurveGeometrySupport.isCurveGeometry(geometry)) {
      for (int i = 0; i < collection.getNumGeometries(); i++) {
        Geometry child = collection.getGeometryN(i);
        if (CurveGeometrySupport.isCurveGeometry(child))
          throw new IllegalArgumentException(
              "Curves inside a linear GeometryCollection are not supported; use MultiCurve or"
                  + " MultiSurface");
        rejectCurvesInLinearCollection(child);
      }
    }
  }

  public static Geometry decode(byte[] bytes, int expectedSrid) throws Exception {
    if (bytes == null) return null;
    if (bytes.length < 13 || bytes[0] != 'G' || bytes[1] != 'P' || bytes[2] != 0)
      throw new IllegalArgumentException("Invalid GeoPackage binary header");
    int flags = bytes[3] & 255;
    int envelope = (flags >> 1) & 7;
    if ((flags & 0xE0) != 0 || envelope > 4)
      throw new IllegalArgumentException("Unsupported GeoPackage binary flags");
    if (envelope > 1)
      throw new IllegalArgumentException("GeoPackage adapter supports XY envelopes only");
    boolean empty = (flags & 16) != 0;
    if (empty && envelope != 0)
      throw new IllegalArgumentException("Empty geometry must not have an envelope");
    ByteBuffer b =
        ByteBuffer.wrap(bytes)
            .order((flags & 1) == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
    int srid = b.getInt(4);
    if (srid != expectedSrid)
      throw new IllegalArgumentException("Geometry SRID differs from layer SRID");
    int offset = 8 + (envelope == 1 ? 32 : 0);
    if (bytes.length < offset + 5)
      throw new IllegalArgumentException("Truncated GeoPackage geometry");
    byte[] wkb = Arrays.copyOfRange(bytes, offset, bytes.length);
    Set<Integer> types = validateWkb(wkb);
    int top =
        ByteBuffer.wrap(wkb)
            .order(wkb[0] == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN)
            .getInt(1);
    if (top < 8 && types.stream().anyMatch(t -> t >= 8))
      throw new IllegalArgumentException(
          "Curves inside generic GeometryCollection are not supported");
    Geometry g = top >= 8 ? new CurveWkbReader().read(wkb) : new WKBReader().read(wkb);
    if (g.isEmpty() != empty)
      throw new IllegalArgumentException("GeoPackage empty flag disagrees with WKB");
    g.setSRID(srid);
    return g;
  }

  static Set<Integer> validateWkb(byte[] wkb) {
    try {
      ByteBuffer b = ByteBuffer.wrap(wkb);
      Set<Integer> types = new TreeSet<>();
      scan(b, types, 0);
      if (b.hasRemaining()) throw new IllegalArgumentException("Trailing WKB bytes");
      return types;
    } catch (BufferUnderflowException e) {
      throw new IllegalArgumentException("Truncated WKB", e);
    }
  }

  private static int count(ByteBuffer b, int minimumBytes) {
    int n = b.getInt();
    if (n < 0 || n > b.remaining() / minimumBytes)
      throw new IllegalArgumentException("Invalid WKB count");
    return n;
  }

  private static void scan(ByteBuffer b, Set<Integer> types, int depth) {
    if (depth > 64) throw new IllegalArgumentException("WKB nesting too deep");
    int order = b.get() & 255;
    if (order > 1) throw new IllegalArgumentException("Invalid WKB byte order");
    b.order(order == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
    int type = b.getInt();
    if (type < 1 || type > 12)
      throw new IllegalArgumentException("Unsupported WKB type/dimension (XY only): " + type);
    types.add(type);
    switch (type) {
      case 1 -> {
        double x = b.getDouble(), y = b.getDouble();
        if (!(Double.isNaN(x) && Double.isNaN(y)) && (!Double.isFinite(x) || !Double.isFinite(y)))
          throw new IllegalArgumentException("Invalid point coordinates");
      }
      case 2, 8 -> {
        int n = count(b, 16);
        for (int i = 0; i < n; i++) xy(b);
      }
      case 3 -> {
        int rings = count(b, 4);
        for (int i = 0; i < rings; i++) {
          int n = count(b, 16);
          for (int j = 0; j < n; j++) xy(b);
        }
      }
      default -> {
        int n = count(b, 5);
        ByteOrder parent = b.order();
        for (int i = 0; i < n; i++) {
          scan(b, types, depth + 1);
          b.order(parent);
        }
      }
    }
  }

  private static void xy(ByteBuffer b) {
    if (!Double.isFinite(b.getDouble()) || !Double.isFinite(b.getDouble()))
      throw new IllegalArgumentException("Non-finite coordinates");
  }

  static String geometryType(Geometry g) {
    return g.getGeometryType().toUpperCase(Locale.ROOT);
  }

  static String typeName(int id) {
    return switch (id) {
      case 8 -> "CIRCULARSTRING";
      case 9 -> "COMPOUNDCURVE";
      case 10 -> "CURVEPOLYGON";
      case 11 -> "MULTICURVE";
      case 12 -> "MULTISURFACE";
      default -> throw new IllegalArgumentException("Not a curve type");
    };
  }
}
