package ch.so.agi.hop.vector.formats.geopackage;

import ch.so.agi.hop.vector.core.*;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.locationtech.jts.geom.Geometry;

/** SQLite/JDBC vector adapter. No GeoTools types or dependencies. */
public final class GeoPackageProvider implements VectorProvider {
  private final CrsDefinitionResolver crs;

  public GeoPackageProvider(CrsDefinitionResolver crs) {
    this.crs = crs;
  }

  public VectorFormat format() {
    return VectorFormat.GEOPACKAGE;
  }

  static String quote(String s) {
    if (s == null || s.isBlank() || s.indexOf(0) >= 0)
      throw new IllegalArgumentException("Invalid SQL identifier");
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }

  private static Connection readConnection(Path path) throws Exception {
    if (!Files.isRegularFile(path))
      throw new IllegalArgumentException("GeoPackage does not exist: " + path);
    ensureSqliteDriver();
    java.util.Properties props = new java.util.Properties();
    props.setProperty("open_mode", "1");
    return DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath(), props);
  }

  public List<LayerSchema> layers(ReadRequest request) throws Exception {
    Path file = request.file();
    try (Connection c = readConnection(file)) {
      List<LayerSchema> result = new ArrayList<>();
      for (var layer : layers(c)) {
        var geometry =
            request.crsOverride().isBlank()
                ? layer.geometry()
                : new GeometrySchema(
                    layer.geometryType(), layer.z(), layer.m(), crs.parse(request.crsOverride()));
        var rowMeta = layer.rowMeta().clone();
        String column = layer.geometryColumn();
        if (!request.geometryField().isBlank()) {
          int gi = rowMeta.indexOfValue(column);
          int collision = rowMeta.indexOfValue(request.geometryField());
          if (collision >= 0 && collision != gi)
            throw new IllegalArgumentException("Geometry output name collides with an attribute");
          column = request.geometryField();
          rowMeta.getValueMeta(gi).setName(column);
        }
        result.add(new LayerSchema(layer.name(), column, geometry, rowMeta));
      }
      return List.copyOf(result);
    }
  }

  private static List<LayerSchema> layers(Connection c) throws Exception {
    List<LayerSchema> layers = new ArrayList<>();
    try (Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery(
                "SELECT g.table_name,g.column_name,g.geometry_type_name,g.srs_id,g.z,g.m FROM"
                    + " gpkg_geometry_columns g JOIN gpkg_contents t ON t.table_name=g.table_name"
                    + " WHERE t.data_type='features' ORDER BY g.table_name")) {
      while (rs.next()) {
        String table = rs.getString(1),
            geom = rs.getString(2),
            type = rs.getString(3).toUpperCase(Locale.ROOT);
        RowMeta rm = new RowMeta();
        boolean found = false;
        try (Statement columns = c.createStatement();
            ResultSet col = columns.executeQuery("PRAGMA table_info(" + quote(table) + ")")) {
          while (col.next()) {
            String name = col.getString("name");
            if (name.equalsIgnoreCase(geom)) {
              found = true;
              continue;
            }
            if (col.getInt("pk") > 0) continue;
            rm.addValueMeta(field(name, col.getString("type")));
          }
        }
        if (!found)
          throw new IllegalArgumentException("Geometry column missing: " + table + "." + geom);
        rm.addValueMeta(new ValueMetaGeometry(geom));
        layers.add(
            new LayerSchema(
                table,
                geom,
                new GeometrySchema(
                    displayGeometryType(type),
                    ordinate(rs.getInt(5)),
                    ordinate(rs.getInt(6)),
                    definition(c, rs.getInt(4))),
                rm));
      }
    }
    return List.copyOf(layers);
  }

  private static Ordinate ordinate(int value) {
    return switch (value) {
      case 0 -> Ordinate.ABSENT;
      case 1 -> Ordinate.REQUIRED;
      case 2 -> Ordinate.OPTIONAL;
      default -> throw new IllegalArgumentException("Invalid GeoPackage dimension flag");
    };
  }

  private static CrsDefinitionResolver.Definition definition(Connection c, int srid)
      throws SQLException {
    try (var p =
        c.prepareStatement(
            "SELECT srs_name,organization,organization_coordsys_id,definition FROM"
                + " gpkg_spatial_ref_sys WHERE srs_id=?")) {
      p.setInt(1, srid);
      try (var r = p.executeQuery()) {
        if (!r.next()) throw new SQLException("Missing CRS definition");
        return new CrsDefinitionResolver.Definition(
            srid, r.getString(1), r.getString(2), r.getInt(3), r.getString(4));
      }
    }
  }

  private static String displayGeometryType(String t) {
    return switch (t) {
      case "LINESTRING" -> "LineString";
      case "MULTILINESTRING" -> "MultiLineString";
      case "MULTIPOINT" -> "MultiPoint";
      case "MULTIPOLYGON" -> "MultiPolygon";
      case "GEOMETRYCOLLECTION" -> "GeometryCollection";
      case "CIRCULARSTRING" -> "CircularString";
      case "COMPOUNDCURVE" -> "CompoundCurve";
      case "CURVEPOLYGON" -> "CurvePolygon";
      case "MULTICURVE" -> "MultiCurve";
      case "MULTISURFACE" -> "MultiSurface";
      default -> t.charAt(0) + t.substring(1).toLowerCase(Locale.ROOT);
    };
  }

  private static IValueMeta field(String name, String declaration) {
    String t = declaration.toUpperCase(Locale.ROOT);
    if (t.contains("BOOL")) return new ValueMetaBoolean(name);
    if (t.contains("INT")) return new ValueMetaInteger(name);
    if (t.contains("REAL")
        || t.contains("FLOA")
        || t.contains("DOUB")
        || t.contains("NUMERIC")
        || t.contains("DECIMAL")) return new ValueMetaNumber(name);
    if (t.contains("DATE") || t.contains("TIME")) return new ValueMetaDate(name);
    if (t.contains("BLOB")) return new ValueMetaBinary(name);
    return new ValueMetaString(name);
  }

  public VectorSource open(ReadRequest request) throws Exception {
    Path file = request.file();
    String layer = request.layer(), geometryField = request.geometryField();
    Connection c = readConnection(file);
    try {
      LayerSchema s = VectorProvider.resolveLayer(layers(c), layer);
      if (s.z() != Ordinate.ABSENT || s.m() != Ordinate.ABSENT)
        throw new IllegalArgumentException(
            "Layer " + s.name() + " declares Z/M; this adapter supports XY only");
      if (!Set.of(
              "GEOMETRY",
              "POINT",
              "LINESTRING",
              "POLYGON",
              "MULTIPOINT",
              "MULTILINESTRING",
              "MULTIPOLYGON",
              "GEOMETRYCOLLECTION",
              "CIRCULARSTRING",
              "COMPOUNDCURVE",
              "CURVEPOLYGON",
              "MULTICURVE",
              "MULTISURFACE")
          .contains(s.geometryType().toUpperCase(Locale.ROOT)))
        throw new IllegalArgumentException(
            "Unsupported GeoPackage geometry type: " + s.geometryType());
      IRowMeta rm = s.rowMeta().clone();
      int gi = rm.size() - 1;
      List<String> cols = new ArrayList<>();
      for (var f : rm.getValueMetaList()) cols.add(quote(f.getName()));
      if (geometryField != null && !geometryField.isBlank()) {
        if (rm.indexOfValue(geometryField) >= 0
            && !rm.getValueMeta(gi).getName().equals(geometryField))
          throw new IllegalArgumentException("Geometry output name collides with an attribute");
        rm.getValueMeta(gi).setName(geometryField);
      }
      LayerSchema output =
          new LayerSchema(
              s.name(),
              rm.getValueMeta(gi).getName(),
              request.crsOverride().isBlank()
                  ? s.geometry()
                  : new GeometrySchema(
                      s.geometryType(), s.z(), s.m(), crs.parse(request.crsOverride())),
              rm);
      Statement statement = c.createStatement();
      ResultSet rows =
          statement.executeQuery("SELECT " + String.join(",", cols) + " FROM " + quote(s.name()));
      return new VectorSource() {
        public LayerSchema schema() {
          return output;
        }

        public Object[] read() throws Exception {
          if (!rows.next()) return null;
          Object[] row = new Object[rm.size()];
          for (int i = 0; i < gi; i++)
            row[i] = attribute(rows, i + 1, rm.getValueMeta(i).getType());
          row[gi] = GeoPackageBinary.decode(rows.getBytes(gi + 1), s.srid());
          if (row[gi] != null) ((Geometry) row[gi]).setSRID(output.srid());
          return row;
        }

        public void close() throws Exception {
          try {
            rows.close();
          } finally {
            try {
              statement.close();
            } finally {
              c.close();
            }
          }
        }
      };
    } catch (Exception e) {
      c.close();
      throw e;
    }
  }

  private static Object attribute(ResultSet rs, int col, int type) throws Exception {
    if (rs.getObject(col) == null) return null;
    return switch (type) {
      case IValueMeta.TYPE_BOOLEAN -> rs.getBoolean(col);
      case IValueMeta.TYPE_INTEGER -> rs.getLong(col);
      case IValueMeta.TYPE_NUMBER -> rs.getDouble(col);
      case IValueMeta.TYPE_BINARY -> rs.getBytes(col);
      case IValueMeta.TYPE_DATE -> {
        String d = rs.getString(col);
        yield java.util.Date.from(
            d.length() == 10
                ? LocalDate.parse(d).atStartOfDay(ZoneOffset.UTC).toInstant()
                : Instant.parse(d));
      }
      default -> rs.getString(col);
    };
  }

  public VectorSink create(WriteRequest request) throws Exception {
    return new Sink(request, crs);
  }

  public String preview(Path path, String layer, IRowMeta input, int geometryIndex)
      throws Exception {
    try (var c = readConnection(path)) {
      validateDatabase(c);
      var t = GeoPackageTarget.read(c, layer);
      if (input != null) t.mapping(input, geometryIndex);
      var text =
          new StringBuilder(
              "Layer: "
                  + t.layer()
                  + "\nGeometry: "
                  + t.geometry()
                  + " / "
                  + t.type()
                  + " / XY\nCRS ID: "
                  + t.srid()
                  + "\nSpatial index: "
                  + GeoPackageIndex.exists(c, t.layer(), t.geometry())
                  + "\nFields:\n");
      for (var f : t.columns())
        text.append(f.name())
            .append(" : ")
            .append(f.type())
            .append(
                f.primary()
                    ? " (generated primary key)"
                    : f.required() ? " (required)" : " (nullable)")
            .append(f.defaultValue() == null ? "" : " default=" + f.defaultValue())
            .append('\n');
      return text.toString();
    }
  }

  private static void validateDatabase(Connection c) throws Exception {
    try (var s = c.createStatement();
        var r = s.executeQuery("PRAGMA application_id")) {
      if (!r.next() || r.getInt(1) != 1196444487)
        throw new IllegalArgumentException("Not a GeoPackage database");
    }
    for (String table : List.of("gpkg_contents", "gpkg_spatial_ref_sys", "gpkg_geometry_columns"))
      if (!GeoPackageIndex.hasTable(c, table))
        throw new IllegalArgumentException("Missing GeoPackage metadata: " + table);
  }

  private static final class Sink implements VectorSink {
    private static final Set<Path> ACTIVE = java.util.concurrent.ConcurrentHashMap.newKeySet();
    final WriteRequest r;
    final Path output;
    Path temp;
    Connection connection;
    GeoPackageFeatureWriter writer;
    GeoPackageTarget target;
    boolean closed, failed, changed;
    long rowNumber;

    Sink(WriteRequest r, CrsDefinitionResolver crs) throws Exception {
      this.r = r;
      var options = r.options() instanceof GeoPackageOptions o ? o : GeoPackageOptions.defaults();
      Path absolute = r.file().toAbsolutePath().normalize();
      boolean create = options.writeMode() == GeoPackageOptions.WriteMode.CREATE_FILE;
      if (create) Files.createDirectories(absolute.getParent());
      output =
          Files.exists(absolute)
              ? absolute.toRealPath()
              : absolute.getParent().toRealPath().resolve(absolute.getFileName());
      if (!ACTIVE.add(output))
        throw new IllegalArgumentException("Another GeoPackage writer owns output " + output);
      try {
        r.checkCancelled();
        if (create && Files.exists(output))
          throw new IllegalArgumentException("Output already exists: " + output);
        if (!create && !Files.isRegularFile(output))
          throw new IllegalArgumentException("GeoPackage does not exist: " + output);
        ensureSqliteDriver();
        var config = new org.sqlite.SQLiteConfig();
        config.setBusyTimeout(5000);
        config.enforceForeignKeys(true);
        config.setTransactionMode(org.sqlite.SQLiteConfig.TransactionMode.IMMEDIATE);
        Path file = output;
        if (create) {
          temp = Files.createTempFile(output.getParent(), ".hop-geopackage-", ".gpkg");
          file = temp;
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + file, config.toProperties());
        GeoPackageIndex.register(connection);
        connection.setAutoCommit(false);
        if (create) createSystemTables(connection);
        else validateDatabase(connection);
        if (options.writeMode() != GeoPackageOptions.WriteMode.APPEND_FEATURES) {
          if (r.geometry() == null || r.sample() == null)
            throw new IllegalArgumentException(
                "Explicit geometry schema or a geometry sample required");
          if (r.geometry().z() != Ordinate.ABSENT || r.geometry().m() != Ordinate.ABSENT)
            throw new IllegalArgumentException("GeoPackage supports XY only");
          if (!r.geometry().type().equalsIgnoreCase(r.sample().getGeometryType())
              && !r.geometry().type().equalsIgnoreCase(GeoPackageBinary.geometryType(r.sample())))
            throw new IllegalArgumentException("Geometry type differs from output schema");
          if (r.sample().getSRID() != 0 && r.sample().getSRID() != r.geometry().srid())
            throw new IllegalArgumentException("Geometry SRID differs from output schema");
          GeoPackageBinary.encode(r.sample());
          createLayer(connection, r, crs);
          changed = true;
        }
        target = GeoPackageTarget.read(connection, r.layer());
        writer = GeoPackageFeatureWriter.open(connection, target, r.rowMeta(), r.geometryIndex());
        if (!changed) writer.includeExistingBounds(r);
        boolean indexed = GeoPackageIndex.exists(connection, target.layer(), target.geometry());
        if (!indexed && options.createSpatialIndex()) {
          GeoPackageIndex.create(connection, target.layer(), target.geometry(), target.fid(), r);
          changed = true;
        }
      } catch (Exception e) {
        failed = true;
        cleanup(e);
        throw e;
      }
    }

    public boolean write(Object[] row) throws Exception {
      try {
        r.checkCancelled();
        rowNumber++;
        Geometry geometry = (Geometry) row[r.geometryIndex()];
        target.validateGeometry(geometry);
        var options = r.options() instanceof GeoPackageOptions o ? o : GeoPackageOptions.defaults();
        if (geometry != null
            && geometry.getSRID() == 0
            && target.srid() != 0
            && options.writeMode() != GeoPackageOptions.WriteMode.APPEND_FEATURES
            && r.sample().getSRID() != 0)
          throw new IllegalArgumentException("Geometry SRID differs from output layer");
        if (geometry != null && geometry.getSRID() == 0 && target.srid() != 0) {
          geometry = com.atolcd.hop.gis.geometry.curve.CurveGeometrySupport.copy(geometry);
          geometry.setSRID(target.srid());
        }
        writer.write(row, geometry);
        return true;
      } catch (Exception e) {
        failed = true;
        throw new IllegalArgumentException(
            "GeoPackage "
                + output
                + ", layer "
                + target.layer()
                + ", row "
                + rowNumber
                + ": "
                + e.getMessage(),
            e);
      }
    }

    public void finish() throws Exception {
      if (closed || failed)
        throw new IllegalStateException("Cannot commit a closed or failed GeoPackage writer");
      try {
        r.checkCancelled();
        writer.updateContents(changed);
        writer.close();
        writer = null;
        r.checkCancelled();
        connection.commit();
        connection.close();
        connection = null;
        if (temp != null) {
          Files.move(temp, output);
          temp = null;
        }
      } catch (Exception e) {
        failed = true;
        cleanup(e);
        throw e;
      }
      close();
    }

    private void cleanup(Exception original) {
      try {
        close();
      } catch (Exception e) {
        original.addSuppressed(e);
      }
    }

    public void close() throws Exception {
      if (closed) return;
      closed = true;
      Exception error = null;
      try {
        if (writer != null)
          try {
            writer.close();
          } catch (Exception e) {
            error = e;
          }
        if (connection != null) {
          try {
            connection.rollback();
          } catch (Exception e) {
            if (error == null) error = e;
            else error.addSuppressed(e);
          }
          try {
            connection.close();
          } catch (Exception e) {
            if (error == null) error = e;
            else error.addSuppressed(e);
          }
        }
        if (temp != null) Files.deleteIfExists(temp);
      } finally {
        ACTIVE.remove(output);
      }
      if (error != null) throw error;
    }
  }

  static void ensureSqliteDriver() throws SQLException {
    // Hop loads plugin libraries through child classloaders, so the SQLite JDBC service
    // provider is not guaranteed to be discovered by DriverManager automatically.
    try {
      Class.forName("org.sqlite.JDBC");
    } catch (ClassNotFoundException e) {
      throw new SQLException("SQLite JDBC driver is missing from the installed plugin", e);
    }
  }

  private static void createSystemTables(Connection c) throws Exception {
    try (Statement s = c.createStatement()) {
      s.execute("PRAGMA application_id=1196444487");
      s.execute("PRAGMA user_version=10400");
      s.execute(
          "CREATE TABLE gpkg_spatial_ref_sys (srs_name TEXT NOT NULL,srs_id INTEGER NOT NULL"
              + " PRIMARY KEY,organization TEXT NOT NULL,organization_coordsys_id INTEGER NOT"
              + " NULL,definition TEXT NOT NULL,description TEXT)");
      s.execute(
          "CREATE TABLE gpkg_contents (table_name TEXT NOT NULL PRIMARY KEY,data_type TEXT NOT"
              + " NULL,identifier TEXT UNIQUE,description TEXT DEFAULT '',last_change DATETIME"
              + " NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),min_x DOUBLE,min_y"
              + " DOUBLE,max_x DOUBLE,max_y DOUBLE,srs_id INTEGER,FOREIGN KEY(srs_id) REFERENCES"
              + " gpkg_spatial_ref_sys(srs_id))");
      s.execute(
          "CREATE TABLE gpkg_geometry_columns (table_name TEXT NOT NULL,column_name TEXT NOT"
              + " NULL,geometry_type_name TEXT NOT NULL,srs_id INTEGER NOT NULL,z TINYINT NOT"
              + " NULL,m TINYINT NOT NULL,PRIMARY"
              + " KEY(table_name,column_name),UNIQUE(table_name),FOREIGN KEY(table_name)"
              + " REFERENCES gpkg_contents(table_name),FOREIGN KEY(srs_id) REFERENCES"
              + " gpkg_spatial_ref_sys(srs_id))");
      s.execute(
          "CREATE TABLE gpkg_extensions (table_name TEXT,column_name TEXT,extension_name TEXT NOT"
              + " NULL,definition TEXT NOT NULL,scope TEXT NOT"
              + " NULL,UNIQUE(table_name,column_name,extension_name))");
    }
  }

  private static void createLayer(Connection c, WriteRequest r, CrsDefinitionResolver crs)
      throws Exception {
    for (int srid : new TreeSet<>(List.of(-1, 0, 4326, r.geometry().srid()))) {
      var d =
          srid == r.geometry().srid()
                  && r.geometry().crs() != null
                  && !r.geometry().crs().wkt().isBlank()
              ? r.geometry().crs()
              : crs.resolve(srid);
      try (var existing =
          c.prepareStatement(
              "SELECT organization,organization_coordsys_id,definition FROM gpkg_spatial_ref_sys"
                  + " WHERE srs_id=?")) {
        existing.setInt(1, d.srid());
        try (var row = existing.executeQuery()) {
          if (row.next()) {
            boolean sameAuthority =
                "EPSG".equalsIgnoreCase(d.organization())
                    && "EPSG".equalsIgnoreCase(row.getString(1))
                    && row.getInt(2) == d.organizationId();
            if (!sameAuthority && !java.util.Objects.equals(row.getString(3), d.wkt()))
              throw new IllegalArgumentException(
                  "Conflicting CRS definition for GeoPackage SRS ID " + d.srid());
          }
        }
      }
      try (PreparedStatement p =
          c.prepareStatement(
              "INSERT OR IGNORE INTO gpkg_spatial_ref_sys"
                  + " (srs_name,srs_id,organization,organization_coordsys_id,definition,description)"
                  + " VALUES(?,?,?,?,?,NULL)")) {
        p.setString(1, d.name());
        p.setInt(2, d.srid());
        p.setString(3, d.organization());
        p.setInt(4, d.organizationId());
        p.setString(5, d.wkt());
        p.executeUpdate();
      }
    }
    String geom = r.rowMeta().getValueMeta(r.geometryIndex()).getName();
    Set<String> names = new HashSet<>();
    for (var f : r.rowMeta().getValueMetaList())
      if (!names.add(f.getName().toLowerCase(Locale.ROOT)))
        throw new IllegalArgumentException("Duplicate field name: " + f.getName());
    if (r.layer().toLowerCase(Locale.ROOT).startsWith("gpkg_")
        || r.layer().toLowerCase(Locale.ROOT).startsWith("sqlite_"))
      throw new IllegalArgumentException("Reserved layer name");
    String fid = "fid";
    while (names.contains(fid.toLowerCase(Locale.ROOT))) fid = "_" + fid;
    List<String> columns = new ArrayList<>();
    columns.add(quote(fid) + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL");
    for (int i = 0; i < r.rowMeta().size(); i++) {
      var f = r.rowMeta().getValueMeta(i);
      columns.add(
          quote(f.getName())
              + " "
              + (i == r.geometryIndex()
                  ? GeoPackageBinary.geometryType(r.sample())
                  : sqlType(f.getType())));
    }
    try (Statement s = c.createStatement()) {
      s.execute("CREATE TABLE " + quote(r.layer()) + " (" + String.join(",", columns) + ")");
    }
    try (PreparedStatement p =
        c.prepareStatement(
            "INSERT INTO gpkg_contents(table_name,data_type,identifier,srs_id)"
                + " VALUES(?,'features',?,?)")) {
      p.setString(1, r.layer());
      p.setString(2, r.layer());
      p.setInt(3, r.geometry().srid());
      p.executeUpdate();
    }
    try (PreparedStatement p =
        c.prepareStatement("INSERT INTO gpkg_geometry_columns VALUES(?,?,?,?,0,0)")) {
      p.setString(1, r.layer());
      p.setString(2, geom);
      p.setString(3, GeoPackageBinary.geometryType(r.sample()));
      p.setInt(4, r.geometry().srid());
      p.executeUpdate();
    }
  }

  private static String sqlType(int type) {
    return switch (type) {
      case IValueMeta.TYPE_BOOLEAN -> "BOOLEAN";
      case IValueMeta.TYPE_INTEGER -> "INTEGER";
      case IValueMeta.TYPE_NUMBER, IValueMeta.TYPE_BIGNUMBER -> "REAL";
      case IValueMeta.TYPE_DATE, IValueMeta.TYPE_TIMESTAMP -> "DATETIME";
      case IValueMeta.TYPE_BINARY -> "BLOB";
      case IValueMeta.TYPE_STRING -> "TEXT";
        // A row may contain additional geometry attributes besides the selected
        // feature geometry. They are ordinary attributes in this layer and are
        // represented as their textual WKT/debug value rather than as a second
        // GeoPackage geometry column.
      case ValueMetaGeometry.TYPE_GEOMETRY -> "TEXT";
      default -> throw new IllegalArgumentException("Unsupported Hop attribute type: " + type);
    };
  }
}
