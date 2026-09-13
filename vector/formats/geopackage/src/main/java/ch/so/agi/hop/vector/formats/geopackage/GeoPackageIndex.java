package ch.so.agi.hop.vector.formats.geopackage;

import static ch.so.agi.hop.vector.formats.geopackage.GeoPackageProvider.quote;

import ch.so.agi.hop.vector.core.WriteRequest;
import com.atolcd.hop.gis.geometry.curve.*;
import java.nio.*;
import java.sql.*;
import java.util.*;
import org.locationtech.jts.geom.*;
import org.sqlite.Function;

/** GeoPackage RTree triggers and their connection-local geometry functions. */
final class GeoPackageIndex {
  private GeoPackageIndex() {}

  static Envelope envelope(Geometry g) {
    Envelope e = new Envelope();
    if (g == null || g.isEmpty()) return e;
    if (g instanceof CircularString c) {
      for (var a : c.getArcSegments()) arc(e, a.getStartPoint(), a.getMidPoint(), a.getEndPoint());
    } else if (g instanceof CompoundCurve c) {
      for (var part : c.getComponents()) e.expandToInclude(envelope(part));
    } else if (g instanceof CurvePolygon c) {
      for (var part : c.getCurveRings()) e.expandToInclude(envelope(part));
    } else if (g instanceof GeometryCollection c) {
      for (int i = 0; i < c.getNumGeometries(); i++) e.expandToInclude(envelope(c.getGeometryN(i)));
    } else e.expandToInclude(g.getEnvelopeInternal());
    if (!e.isNull()
        && (!Double.isFinite(e.getMinX())
            || !Double.isFinite(e.getMaxX())
            || !Double.isFinite(e.getMinY())
            || !Double.isFinite(e.getMaxY())))
      throw new IllegalArgumentException("Non-finite geometry bounds");
    return e;
  }

  private static double positive(double a) {
    double p = a % (2 * Math.PI);
    return p < 0 ? p + 2 * Math.PI : p;
  }

  private static void arc(Envelope e, Coordinate a, Coordinate b, Coordinate c) {
    e.expandToInclude(a);
    e.expandToInclude(b);
    e.expandToInclude(c);
    double cx, cy, radius;
    boolean full = a.equals2D(c) && !a.equals2D(b);
    if (full) {
      cx = a.x + (b.x - a.x) / 2;
      cy = a.y + (b.y - a.y) / 2;
      radius = a.distance(b) / 2;
    } else {
      double ux = b.x - a.x, uy = b.y - a.y, vx = c.x - a.x, vy = c.y - a.y;
      double d = 2 * (ux * vy - uy * vx);
      if (d == 0) return;
      double u2 = ux * ux + uy * uy, v2 = vx * vx + vy * vy;
      double x = (u2 * vy - v2 * uy) / d, y = (v2 * ux - u2 * vx) / d;
      cx = a.x + x;
      cy = a.y + y;
      radius = Math.hypot(x, y);
    }
    double start = Math.atan2(a.y - cy, a.x - cx),
        mid = Math.atan2(b.y - cy, b.x - cx),
        end = Math.atan2(c.y - cy, c.x - cx);
    double sweep = positive(end - start);
    boolean ccw = positive(mid - start) <= sweep;
    for (int i = 0; i < 4; i++) {
      double angle = i * Math.PI / 2;
      if (full
          || (ccw
              ? positive(angle - start) <= sweep
              : positive(start - angle) <= 2 * Math.PI - sweep)) {
        double x = cx + (i == 0 ? radius : i == 2 ? -radius : 0);
        double y = cy + (i == 1 ? radius : i == 3 ? -radius : 0);
        e.expandToInclude(x, y);
      }
    }
  }

  static void register(Connection c) throws SQLException {
    for (String name : List.of("ST_IsEmpty", "ST_MinX", "ST_MaxX", "ST_MinY", "ST_MaxY"))
      Function.create(
          c,
          name,
          new Function() {
            protected void xFunc() throws SQLException {
              byte[] bytes = value_blob(0);
              if (bytes == null) {
                result();
                return;
              }
              try {
                if (bytes.length < 8)
                  throw new IllegalArgumentException("Truncated GeoPackage geometry");
                int srid =
                    ByteBuffer.wrap(bytes)
                        .order((bytes[3] & 1) == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN)
                        .getInt(4);
                Geometry g = GeoPackageBinary.decode(bytes, srid);
                if (name.equals("ST_IsEmpty")) {
                  result(g.isEmpty() ? 1 : 0);
                  return;
                }
                Envelope e = envelope(g);
                if (e.isNull()) {
                  result();
                  return;
                }
                result(
                    switch (name) {
                      case "ST_MinX" -> e.getMinX();
                      case "ST_MaxX" -> e.getMaxX();
                      case "ST_MinY" -> e.getMinY();
                      default -> e.getMaxY();
                    });
              } catch (Exception ex) {
                throw new SQLException("Invalid geometry in " + name, ex);
              }
            }
          },
          1,
          Function.FLAG_DETERMINISTIC);
  }

  static void extensions(Connection c) throws SQLException {
    try (var s = c.createStatement()) {
      s.execute(
          "CREATE TABLE IF NOT EXISTS gpkg_extensions (table_name TEXT,column_name"
              + " TEXT,extension_name TEXT NOT NULL,definition TEXT NOT NULL,scope TEXT NOT"
              + " NULL,UNIQUE(table_name,column_name,extension_name))");
    }
  }

  static boolean exists(Connection c, String layer, String geom) throws SQLException {
    String base = "rtree_" + layer + "_" + geom;
    boolean table = false, extension = false;
    Set<String> triggers = new HashSet<>();
    try (var p = c.prepareStatement("SELECT type,name,tbl_name,sql FROM sqlite_master");
        var rs = p.executeQuery()) {
      while (rs.next()) {
        String name = rs.getString(2);
        if (name.equalsIgnoreCase(base)) {
          if (!rs.getString(1).equals("table")
              || !rs.getString(4).toLowerCase(Locale.ROOT).contains("using rtree"))
            throw new SQLException("Invalid spatial index: " + base);
          table = true;
        }
        if (rs.getString(1).equals("trigger")
            && name.toLowerCase(Locale.ROOT).startsWith(base.toLowerCase(Locale.ROOT) + "_")) {
          if (!rs.getString(3).equalsIgnoreCase(layer))
            throw new SQLException("Spatial index trigger on wrong table: " + name);
          triggers.add(name.substring(base.length() + 1).toLowerCase(Locale.ROOT));
        }
      }
    }
    if (hasTable(c, "gpkg_extensions"))
      try (var p =
          c.prepareStatement(
              "SELECT scope FROM gpkg_extensions WHERE table_name=? AND column_name=? AND"
                  + " extension_name='gpkg_rtree_index'")) {
        p.setString(1, layer);
        p.setString(2, geom);
        try (var rs = p.executeQuery()) {
          if (rs.next()) {
            extension = true;
            if (!"write-only".equals(rs.getString(1)))
              throw new SQLException("Invalid RTree extension scope");
          }
        }
      }
    if (!table && !extension && triggers.isEmpty()) return false;
    if (!table
        || !extension
        || !triggers.containsAll(Set.of("insert", "delete", "update2", "update4"))
        || !(triggers.contains("update3") || triggers.contains("update5"))
        || !(triggers.contains("update1") || triggers.containsAll(Set.of("update6", "update7"))))
      throw new SQLException("Incomplete spatial index definition: " + base);
    try (var s = c.createStatement();
        var rs =
            s.executeQuery("SELECT id,minx,maxx,miny,maxy FROM " + quote(base) + " LIMIT 0")) {}
    return true;
  }

  static boolean hasTable(Connection c, String table) throws SQLException {
    try (var p =
        c.prepareStatement(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? COLLATE NOCASE")) {
      p.setString(1, table);
      try (var rs = p.executeQuery()) {
        return rs.next();
      }
    }
  }

  static void create(Connection c, String layer, String geom, String fid, WriteRequest request)
      throws Exception {
    extensions(c);
    String base = "rtree_" + layer + "_" + geom,
        r = quote(base),
        t = quote(layer),
        g = quote(geom),
        id = quote(fid);
    String n = "NEW." + g, oldId = "OLD." + id, newId = "NEW." + id;
    String nonempty = n + " IS NOT NULL AND NOT ST_IsEmpty(" + n + ")";
    String values =
        newId + ",ST_MinX(" + n + "),ST_MaxX(" + n + "),ST_MinY(" + n + "),ST_MaxY(" + n + ")";
    String insert = "INSERT OR REPLACE INTO " + r + " VALUES(" + values + ");";
    try (var s = c.createStatement()) {
      s.execute("CREATE VIRTUAL TABLE " + r + " USING rtree(id,minx,maxx,miny,maxy)");
      trigger(s, base, "insert", "AFTER INSERT", t, nonempty, insert);
      trigger(
          s,
          base,
          "update6",
          "AFTER UPDATE OF " + g,
          t,
          oldId
              + "="
              + newId
              + " AND "
              + nonempty
              + " AND OLD."
              + g
              + " IS NOT NULL AND NOT ST_IsEmpty(OLD."
              + g
              + ")",
          insert);
      trigger(
          s,
          base,
          "update7",
          "AFTER UPDATE OF " + g,
          t,
          oldId
              + "="
              + newId
              + " AND "
              + nonempty
              + " AND (OLD."
              + g
              + " IS NULL OR ST_IsEmpty(OLD."
              + g
              + "))",
          insert);
      trigger(
          s,
          base,
          "update2",
          "AFTER UPDATE OF " + g,
          t,
          oldId + "=" + newId + " AND (" + n + " IS NULL OR ST_IsEmpty(" + n + "))",
          "DELETE FROM " + r + " WHERE id=" + oldId + ";");
      trigger(
          s,
          base,
          "update5",
          "AFTER UPDATE",
          t,
          oldId + "!=" + newId + " AND " + nonempty,
          "DELETE FROM " + r + " WHERE id=" + oldId + ";" + insert);
      trigger(
          s,
          base,
          "update4",
          "AFTER UPDATE",
          t,
          oldId + "!=" + newId + " AND (" + n + " IS NULL OR ST_IsEmpty(" + n + "))",
          "DELETE FROM " + r + " WHERE id IN (" + oldId + "," + newId + ");");
      trigger(
          s,
          base,
          "delete",
          "AFTER DELETE",
          t,
          "OLD." + g + " IS NOT NULL",
          "DELETE FROM " + r + " WHERE id=" + oldId + ";");
    }
    try (var p =
        c.prepareStatement(
            "INSERT INTO gpkg_extensions"
                + " VALUES(?,?,'gpkg_rtree_index','http://www.geopackage.org/spec/#extension_rtree','write-only')")) {
      p.setString(1, layer);
      p.setString(2, geom);
      p.executeUpdate();
    }
    try (var s = c.createStatement();
        var rows = s.executeQuery("SELECT " + id + "," + g + " FROM " + t);
        var p = c.prepareStatement("INSERT INTO " + r + " VALUES(?,?,?,?,?)")) {
      while (rows.next()) {
        request.checkCancelled();
        byte[] bytes = rows.getBytes(2);
        if (bytes == null) continue;
        int srid =
            ByteBuffer.wrap(bytes)
                .order((bytes[3] & 1) == 1 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN)
                .getInt(4);
        Envelope e = envelope(GeoPackageBinary.decode(bytes, srid));
        if (e.isNull()) continue;
        p.setLong(1, rows.getLong(1));
        p.setDouble(2, e.getMinX());
        p.setDouble(3, e.getMaxX());
        p.setDouble(4, e.getMinY());
        p.setDouble(5, e.getMaxY());
        p.executeUpdate();
      }
    }
  }

  private static void trigger(
      Statement s, String base, String suffix, String event, String table, String when, String body)
      throws SQLException {
    s.execute(
        "CREATE TRIGGER "
            + quote(base + "_" + suffix)
            + " "
            + event
            + " ON "
            + table
            + " WHEN "
            + when
            + " BEGIN "
            + body
            + " END");
  }
}
