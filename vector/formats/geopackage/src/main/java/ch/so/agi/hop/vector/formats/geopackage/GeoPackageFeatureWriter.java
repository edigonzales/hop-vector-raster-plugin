package ch.so.agi.hop.vector.formats.geopackage;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.locationtech.jts.geom.Geometry;

/** Writes GeoPackage feature rows while preserving SQL/MM curved geometry WKB. */
public final class GeoPackageFeatureWriter implements AutoCloseable {

  private static final String CURVE_EXTENSION_DEFINITION =
      "http://www.geopackage.org/spec/#extension_geometry_types";

  private final Connection connection;
  private final PreparedStatement insertStatement;
  private final IRowMeta rowMeta;
  private final int geometryFieldIndex;
  private String layerName;
  private String geometryColumn;
  private final org.locationtech.jts.geom.Envelope bounds =
      new org.locationtech.jts.geom.Envelope();
  private final java.util.Set<String> registeredExtensions = new java.util.HashSet<>();

  private GeoPackageFeatureWriter(
      Connection connection,
      PreparedStatement insertStatement,
      IRowMeta rowMeta,
      int geometryFieldIndex) {
    this.connection = connection;
    this.insertStatement = insertStatement;
    this.rowMeta = rowMeta;
    this.geometryFieldIndex = geometryFieldIndex;
  }

  private GeoPackageTarget target;
  private GeoPackageTarget.Column[] mapping;
  private long rowCount;

  static GeoPackageFeatureWriter open(
      Connection connection, GeoPackageTarget target, IRowMeta rowMeta, int geometryFieldIndex)
      throws Exception {
    var mapping = target.mapping(rowMeta, geometryFieldIndex);
    String columns =
        java.util.Arrays.stream(mapping)
            .map(c -> quoteIdentifier(c.name()))
            .collect(java.util.stream.Collectors.joining(","));
    String parameters = String.join(",", java.util.Collections.nCopies(mapping.length, "?"));
    var insert =
        connection.prepareStatement(
            "INSERT INTO "
                + quoteIdentifier(target.layer())
                + " ("
                + columns
                + ") VALUES ("
                + parameters
                + ")");
    var result = new GeoPackageFeatureWriter(connection, insert, rowMeta, geometryFieldIndex);
    result.layerName = target.layer();
    result.geometryColumn = target.geometry();
    result.target = target;
    result.mapping = mapping;
    return result;
  }

  void includeExistingBounds(ch.so.agi.hop.vector.core.WriteRequest request) throws Exception {
    try (var p =
        connection.prepareStatement(
            "SELECT min_x,max_x,min_y,max_y FROM gpkg_contents WHERE table_name=?")) {
      p.setString(1, layerName);
      try (var rs = p.executeQuery()) {
        if (rs.next()
            && rs.getObject(1) != null
            && rs.getObject(2) != null
            && rs.getObject(3) != null
            && rs.getObject(4) != null) {
          bounds.expandToInclude(
              new org.locationtech.jts.geom.Envelope(
                  rs.getDouble(1), rs.getDouble(2), rs.getDouble(3), rs.getDouble(4)));
          return;
        }
      }
    }
    try (var s = connection.createStatement();
        var rows =
            s.executeQuery(
                "SELECT "
                    + quoteIdentifier(geometryColumn)
                    + " FROM "
                    + quoteIdentifier(layerName))) {
      while (rows.next()) {
        request.checkCancelled();
        bounds.expandToInclude(
            GeoPackageIndex.envelope(GeoPackageBinary.decode(rows.getBytes(1), target.srid())));
      }
    }
  }

  void write(Object[] row, Geometry geometry) throws Exception {
    target.validateGeometry(geometry);
    rowCount++;
    for (int i = 0; i < rowMeta.size(); i++) {
      int parameter = i + 1;
      try {
        if (i == geometryFieldIndex) {
          if (geometry == null && mapping[i].required())
            throw new IllegalArgumentException("NULL geometry");
          bindGeometry(parameter, geometry);
        } else {
          Object value = mapping[i].normalize(rowMeta.getValueMeta(i), row[i]);
          bindAttribute(parameter, rowMeta.getValueMeta(i), value);
        }
      } catch (Exception e) {
        throw new IllegalArgumentException("Field " + mapping[i].name() + ": " + e.getMessage(), e);
      }
    }
    insertStatement.executeUpdate();
    if (geometry != null && !geometry.isEmpty())
      bounds.expandToInclude(GeoPackageIndex.envelope(geometry));
  }

  void updateContents(boolean changed) throws SQLException {
    if (rowCount == 0 && !changed) return;
    try (PreparedStatement p =
        connection.prepareStatement(
            "UPDATE gpkg_contents SET"
                + " min_x=?,min_y=?,max_x=?,max_y=?,last_change=strftime('%Y-%m-%dT%H:%M:%fZ','now')"
                + " WHERE table_name=?")) {
      if (bounds.isNull()) for (int i = 1; i <= 4; i++) p.setNull(i, Types.DOUBLE);
      else {
        p.setDouble(1, bounds.getMinX());
        p.setDouble(2, bounds.getMinY());
        p.setDouble(3, bounds.getMaxX());
        p.setDouble(4, bounds.getMaxY());
      }
      p.setString(5, layerName);
      p.executeUpdate();
    }
  }

  @Override
  public void close() throws SQLException {
    insertStatement.close();
  }

  private void bindGeometry(int parameter, Geometry geometry) throws Exception {
    if (geometry == null) {
      insertStatement.setNull(parameter, Types.BLOB);
      return;
    }
    byte[] encoded = GeoPackageBinary.encode(geometry);
    for (int type :
        GeoPackageBinary.validateWkb(java.util.Arrays.copyOfRange(encoded, 8, encoded.length)))
      if (type >= 8) {
        String extension = "gpkg_geom_" + GeoPackageBinary.typeName(type);
        if (registeredExtensions.add(extension)) {
          GeoPackageIndex.extensions(connection);
          try (PreparedStatement p =
              connection.prepareStatement(
                  "INSERT OR IGNORE INTO gpkg_extensions VALUES(?,?,?,?,'read-write')")) {
            p.setString(1, layerName);
            p.setString(2, geometryColumn);
            p.setString(3, extension);
            p.setString(4, CURVE_EXTENSION_DEFINITION);
            p.executeUpdate();
          }
        }
      }
    insertStatement.setBytes(parameter, encoded);
  }

  private void bindAttribute(int parameter, IValueMeta valueMeta, Object value) throws Exception {
    Object normalized = value;
    if (normalized == null) {
      insertStatement.setObject(parameter, null);
      return;
    }
    if (normalized instanceof Long l) {
      insertStatement.setLong(parameter, l);
      return;
    }
    if (normalized instanceof Double d) {
      insertStatement.setDouble(parameter, d);
      return;
    }
    switch (valueMeta.getType()) {
      case IValueMeta.TYPE_BOOLEAN -> insertStatement.setBoolean(parameter, (Boolean) normalized);
      case IValueMeta.TYPE_INTEGER ->
          insertStatement.setLong(parameter, ((Number) normalized).longValue());
      case IValueMeta.TYPE_NUMBER ->
          insertStatement.setDouble(parameter, ((Number) normalized).doubleValue());
      case IValueMeta.TYPE_BIGNUMBER -> {
        if (normalized instanceof BigDecimal decimal) {
          insertStatement.setBigDecimal(parameter, decimal);
        } else {
          insertStatement.setDouble(parameter, ((Number) normalized).doubleValue());
        }
      }
      case IValueMeta.TYPE_DATE, IValueMeta.TYPE_TIMESTAMP -> {
        if (normalized instanceof java.util.Date date) {
          if (mapping[parameter - 1].baseType().equals("DATE")) {
            insertStatement.setString(
                parameter,
                Instant.ofEpochMilli(date.getTime())
                    .atOffset(java.time.ZoneOffset.UTC)
                    .toLocalDate()
                    .toString());
            break;
          }
          insertStatement.setString(
              parameter,
              new java.time.format.DateTimeFormatterBuilder()
                  .appendInstant(3)
                  .toFormatter()
                  .format(Instant.ofEpochMilli(date.getTime())));
        } else {
          insertStatement.setString(parameter, normalized.toString());
        }
      }
      case IValueMeta.TYPE_BINARY -> insertStatement.setBytes(parameter, (byte[]) normalized);
      default -> insertStatement.setString(parameter, normalized.toString());
    }
  }

  private static String quoteIdentifier(String identifier) {
    return "\"" + identifier.replace("\"", "\"\"") + "\"";
  }

  enum CurveType {
    CIRCULARSTRING("CIRCULARSTRING"),
    COMPOUNDCURVE("COMPOUNDCURVE"),
    CURVEPOLYGON("CURVEPOLYGON"),
    MULTICURVE("MULTICURVE"),
    MULTISURFACE("MULTISURFACE");

    private final String geometryTypeName;

    CurveType(String geometryTypeName) {
      this.geometryTypeName = geometryTypeName;
    }

    static CurveType fromGeometry(Geometry geometry) {
      Geometry normalized = geometry;
      if (normalized instanceof com.atolcd.hop.gis.geometry.curve.CircularString) {
        return CIRCULARSTRING;
      }
      if (normalized instanceof com.atolcd.hop.gis.geometry.curve.CompoundCurve) {
        return COMPOUNDCURVE;
      }
      if (normalized instanceof com.atolcd.hop.gis.geometry.curve.CurvePolygon) {
        return CURVEPOLYGON;
      }
      if (normalized instanceof com.atolcd.hop.gis.geometry.curve.MultiCurve) {
        return MULTICURVE;
      }
      if (normalized instanceof com.atolcd.hop.gis.geometry.curve.MultiSurface) {
        return MULTISURFACE;
      }
      return null;
    }
  }
}
