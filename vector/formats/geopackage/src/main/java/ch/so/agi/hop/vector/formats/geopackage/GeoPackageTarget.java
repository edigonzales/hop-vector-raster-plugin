package ch.so.agi.hop.vector.formats.geopackage;

import static ch.so.agi.hop.vector.formats.geopackage.GeoPackageProvider.quote;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import org.apache.hop.core.row.*;
import org.locationtech.jts.geom.*;

/** The existing SQLite schema, shared by runtime validation and the writer preview. */
record GeoPackageTarget(
    String layer, String geometry, String type, int srid, List<Column> columns, String fid) {
  record Column(String name, String type, boolean required, String defaultValue, boolean primary) {
    String baseType() {
      return type.replaceAll("\\(.*", "").trim().toUpperCase(Locale.ROOT);
    }

    void validateMeta(IValueMeta meta) {
      int t = meta.getType();
      boolean numeric =
          Set.of(IValueMeta.TYPE_INTEGER, IValueMeta.TYPE_NUMBER, IValueMeta.TYPE_BIGNUMBER)
              .contains(t);
      boolean valid =
          switch (baseType()) {
            case "BOOLEAN" -> t == IValueMeta.TYPE_BOOLEAN;
            case "TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "FLOAT", "DOUBLE", "REAL" ->
                numeric;
            case "TEXT", "VARCHAR", "CHAR" ->
                t == IValueMeta.TYPE_STRING
                    || t == com.atolcd.hop.core.row.value.ValueMetaGeometry.TYPE_GEOMETRY;
            case "BLOB" -> t == IValueMeta.TYPE_BINARY;
            case "DATE", "DATETIME" -> t == IValueMeta.TYPE_DATE || t == IValueMeta.TYPE_TIMESTAMP;
            default -> false;
          };
      if (!valid)
        throw new IllegalArgumentException(
            "Incompatible input type for field " + name + " (" + type + ")");
    }

    Object normalize(IValueMeta meta, Object raw) throws Exception {
      Object v = meta.convertToNormalStorageType(raw);
      if (v == null) {
        if (required) throw new IllegalArgumentException("NULL in required field " + name);
        return null;
      }
      String base = baseType();
      if (Set.of("TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER").contains(base)) {
        long value = new BigDecimal(v.toString()).longValueExact();
        long min =
            switch (base) {
              case "TINYINT" -> -128;
              case "SMALLINT" -> -32768;
              case "MEDIUMINT" -> -2147483648L;
              default -> Long.MIN_VALUE;
            };
        long max =
            switch (base) {
              case "TINYINT" -> 127;
              case "SMALLINT" -> 32767;
              case "MEDIUMINT" -> 2147483647L;
              default -> Long.MAX_VALUE;
            };
        if (value < min || value > max)
          throw new IllegalArgumentException("Integer outside " + base + " range");
        return value;
      }
      if (Set.of("FLOAT", "DOUBLE", "REAL").contains(base)) {
        double value = ((Number) v).doubleValue();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite number");
        if (v instanceof BigDecimal || v instanceof Long) {
          if (new BigDecimal(v.toString()).compareTo(BigDecimal.valueOf(value)) != 0)
            throw new IllegalArgumentException("Number loses precision in " + base);
        }
        if (base.equals("FLOAT")
            && (!Float.isFinite((float) value)
                || Double.compare(value, (double) (float) value) != 0))
          throw new IllegalArgumentException("Number loses precision in FLOAT");
        return value;
      }
      if (base.equals("TEXT")
          || base.equals("VARCHAR")
          || base.equals("CHAR")
          || base.equals("BLOB")) {
        var matcher = java.util.regex.Pattern.compile(".*\\(\\s*(\\d+)\\s*\\).*?").matcher(type);
        Object value = base.equals("BLOB") ? v : v.toString();
        int length =
            value instanceof byte[] b
                ? b.length
                : ((String) value).codePointCount(0, ((String) value).length());
        if (matcher.matches() && length > Integer.parseInt(matcher.group(1)))
          throw new IllegalArgumentException("Value exceeds declared length");
        return value;
      }
      return v;
    }
  }

  static GeoPackageTarget read(Connection c, String requested) throws Exception {
    String layer, geom, type;
    int srid;
    try (var p =
        c.prepareStatement(
            "SELECT g.table_name,g.column_name,g.geometry_type_name,g.srs_id,g.z,g.m FROM"
                + " gpkg_geometry_columns g JOIN gpkg_contents t ON t.table_name=g.table_name WHERE"
                + " g.table_name=? COLLATE NOCASE AND t.data_type='features'")) {
      p.setString(1, requested);
      try (var rs = p.executeQuery()) {
        if (!rs.next())
          throw new IllegalArgumentException("Feature layer does not exist: " + requested);
        layer = rs.getString(1);
        geom = rs.getString(2);
        type = rs.getString(3).toUpperCase(Locale.ROOT);
        srid = rs.getInt(4);
        if (rs.getInt(5) != 0 || rs.getInt(6) != 0)
          throw new IllegalArgumentException("GeoPackage writer supports XY only");
      }
    }
    try (var p = c.prepareStatement("SELECT 1 FROM gpkg_spatial_ref_sys WHERE srs_id=?")) {
      p.setInt(1, srid);
      try (var row = p.executeQuery()) {
        if (!row.next())
          throw new IllegalArgumentException("Missing target CRS definition: " + srid);
      }
    }
    List<Column> columns = new ArrayList<>();
    String fid = null;
    boolean found = false;
    try (var s = c.createStatement();
        var rs = s.executeQuery("PRAGMA table_xinfo(" + quote(layer) + ")")) {
      while (rs.next()) {
        if (rs.getInt("hidden") != 0)
          throw new IllegalArgumentException(
              "Generated/hidden columns are not supported: " + layer);
        var column =
            new Column(
                rs.getString("name"),
                rs.getString("type"),
                rs.getBoolean("notnull"),
                rs.getString("dflt_value"),
                rs.getInt("pk") > 0);
        columns.add(column);
        if (column.primary()) {
          if (fid != null || !column.type().equalsIgnoreCase("INTEGER"))
            throw new IllegalArgumentException("Expected one INTEGER primary key: " + layer);
          fid = column.name();
        }
        if (column.name().equalsIgnoreCase(geom)) found = true;
      }
    }
    if (fid == null || !found)
      throw new IllegalArgumentException("Invalid GeoPackage feature table: " + layer);
    return new GeoPackageTarget(layer, geom, type, srid, List.copyOf(columns), fid);
  }

  Column[] mapping(IRowMeta meta, int gi) {
    if (meta == null || gi < 0 || gi >= meta.size())
      throw new IllegalArgumentException("Source geometry field is required");
    if (!(meta.getValueMeta(gi) instanceof com.atolcd.hop.core.row.value.ValueMetaGeometry))
      throw new IllegalArgumentException("Source geometry field must have Hop Geometry type");
    var sourceNames = new HashSet<String>();
    for (var field : meta.getValueMetaList())
      if (!sourceNames.add(field.getName().toLowerCase(Locale.ROOT)))
        throw new IllegalArgumentException("Ambiguous input field: " + field.getName());
    Column[] result = new Column[meta.size()];
    Set<String> used = new HashSet<>();
    for (int i = 0; i < meta.size(); i++) {
      String name = i == gi ? geometry : meta.getValueMeta(i).getName();
      if (!used.add(name.toLowerCase(Locale.ROOT)))
        throw new IllegalArgumentException("Ambiguous input field: " + name);
      var col =
          columns.stream()
              .filter(f -> f.name().equalsIgnoreCase(name))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("Unknown target field: " + name));
      if (col.primary())
        throw new IllegalArgumentException(
            "Primary key " + name + " is generated; rename source field to SOURCE_FID");
      if (i != gi) col.validateMeta(meta.getValueMeta(i));
      result[i] = col;
    }
    for (var col : columns)
      if (!col.primary()
          && col.required()
          && col.defaultValue() == null
          && !used.contains(col.name().toLowerCase(Locale.ROOT)))
        throw new IllegalArgumentException("Missing required target field: " + col.name());
    return result;
  }

  void validateGeometry(Geometry g) {
    if (g == null) return;
    if (g.getSRID() != 0 && g.getSRID() != srid)
      throw new IllegalArgumentException("Geometry CRS differs from target layer");
    String actual = GeoPackageBinary.geometryType(g);
    boolean accepted =
        type.equals(actual)
            || type.equals("GEOMETRY")
            || switch (type) {
              case "CURVE" ->
                  Set.of("LINESTRING", "CIRCULARSTRING", "COMPOUNDCURVE").contains(actual);
              case "SURFACE" -> Set.of("POLYGON", "CURVEPOLYGON").contains(actual);
              case "MULTICURVE" -> actual.equals("MULTILINESTRING");
              case "MULTISURFACE" -> actual.equals("MULTIPOLYGON");
              case "GEOMETRYCOLLECTION" ->
                  Set.of("MULTIPOINT", "MULTILINESTRING", "MULTIPOLYGON").contains(actual);
              default -> false;
            };
    if (!accepted)
      throw new IllegalArgumentException(
          "Geometry type " + actual + " differs from target " + type);
  }
}
