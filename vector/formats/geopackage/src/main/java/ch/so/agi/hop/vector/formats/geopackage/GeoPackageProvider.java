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

  public VectorSink create(WriteRequest r) throws Exception {
    Geometry sample = r.sample();
    boolean assignUnknownCrs =
        sample != null && sample.getSRID() == 0 && r.geometry() != null && r.geometry().srid() > 0;
    if (sample != null && r.geometry() != null) {
      if (!r.geometry().type().equalsIgnoreCase(sample.getGeometryType()))
        throw new IllegalArgumentException("Geometry type differs from output schema");
      if (sample.getSRID() > 0
          && r.geometry().srid() > 0
          && sample.getSRID() != r.geometry().srid())
        throw new IllegalArgumentException("Geometry SRID differs from output schema");
      if (sample.getSRID() != r.geometry().srid()) {
        sample = com.atolcd.hop.gis.geometry.curve.CurveGeometrySupport.copy(sample);
        sample.setSRID(r.geometry().srid());
      }
      r =
          new WriteRequest(
              r.file(),
              r.layer(),
              r.rowMeta(),
              r.geometryIndex(),
              sample,
              r.geometry(),
              r.options(),
              r.diagnostics());
    }
    return new Sink(r, crs, assignUnknownCrs);
  }

  private static final class Sink implements VectorSink {
    private static final Set<Path> ACTIVE = java.util.concurrent.ConcurrentHashMap.newKeySet();
    final WriteRequest r;
    final Path output;
    Path temp;
    GeoPackageFeatureWriter writer;
    boolean closed, failed;
    final boolean assignUnknownCrs;

    Sink(WriteRequest r, CrsDefinitionResolver crs, boolean assignUnknownCrs) throws Exception {
      this.assignUnknownCrs = assignUnknownCrs;
      this.r = r;
      Path absolute = r.file().toAbsolutePath().normalize();
      Files.createDirectories(absolute.getParent());
      output = absolute.getParent().toRealPath().resolve(absolute.getFileName());
      if (!ACTIVE.add(output))
        throw new IllegalArgumentException("Another GeoPackage writer owns the output path");
      try {
        if (Files.exists(output))
          throw new IllegalArgumentException("Output already exists: " + output);
        if (r.geometry().z() != Ordinate.ABSENT || r.geometry().m() != Ordinate.ABSENT)
          throw new IllegalArgumentException("GeoPackage supports XY only");
        if (r.sample() == null)
          throw new IllegalArgumentException("GeoPackage requires a geometry sample");
        GeoPackageBinary.encode(r.sample());
        temp = Files.createTempFile(output.getParent(), ".hop-geopackage-", ".gpkg");
        createSchema(temp, r, crs);
        writer =
            GeoPackageFeatureWriter.open(
                temp,
                r.layer(),
                r.rowMeta().getValueMeta(r.geometryIndex()).getName(),
                r.rowMeta(),
                r.geometryIndex(),
                r.sample());
      } catch (Exception e) {
        close();
        throw e;
      }
    }

    public boolean write(Object[] row) throws Exception {
      try {
        return writeRow(row);
      } catch (Exception e) {
        failed = true;
        throw e;
      }
    }

    private boolean writeRow(Object[] row) throws Exception {
      Geometry g = (Geometry) row[r.geometryIndex()];
      if (g != null) {
        if (g.getSRID() != r.sample().getSRID() && !(assignUnknownCrs && g.getSRID() == 0))
          throw new IllegalArgumentException("Geometry SRID differs from output layer");
        if (!GeoPackageBinary.geometryType(g).equals(GeoPackageBinary.geometryType(r.sample())))
          throw new IllegalArgumentException("Geometry type differs from output layer");
      }
      if (g != null && g.getSRID() != r.sample().getSRID()) {
        g = com.atolcd.hop.gis.geometry.curve.CurveGeometrySupport.copy(g);
        g.setSRID(r.sample().getSRID());
      }
      writer.write(row, g);
      return true;
    }

    public void finish() throws Exception {
      if (failed) {
        close();
        throw new IllegalStateException("Cannot publish failed GeoPackage export");
      }
      writer.commit();
      writer.close();
      writer = null;
      try {
        Files.move(temp, output);
        temp = null;
      } finally {
        close();
      }
    }

    public void close() throws Exception {
      if (closed) return;
      closed = true;
      try {
        if (writer != null) {
          try {
            writer.rollback();
          } finally {
            writer.close();
            writer = null;
          }
        }
      } finally {
        try {
          if (temp != null) Files.deleteIfExists(temp);
        } finally {
          ACTIVE.remove(output);
        }
      }
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

  private static void createSchema(Path file, WriteRequest r, CrsDefinitionResolver crs)
      throws Exception {
    ensureSqliteDriver();
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath())) {
      try (Statement s = c.createStatement()) {
        s.execute("PRAGMA application_id=1196444487");
        s.execute("PRAGMA user_version=10400");
        s.execute("PRAGMA foreign_keys=ON");
        c.setAutoCommit(false);
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
      for (int srid : new TreeSet<>(List.of(-1, 0, 4326, r.sample().getSRID()))) {
        var d =
            srid == r.geometry().srid()
                    && r.geometry().crs() != null
                    && !r.geometry().crs().wkt().isBlank()
                ? r.geometry().crs()
                : crs.resolve(srid);
        try (PreparedStatement p =
            c.prepareStatement("INSERT INTO gpkg_spatial_ref_sys VALUES(?,?,?,?,?,NULL)")) {
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
        p.setInt(3, r.sample().getSRID());
        p.executeUpdate();
      }
      try (PreparedStatement p =
          c.prepareStatement("INSERT INTO gpkg_geometry_columns VALUES(?,?,?,?,0,0)")) {
        p.setString(1, r.layer());
        p.setString(2, geom);
        p.setString(3, GeoPackageBinary.geometryType(r.sample()));
        p.setInt(4, r.sample().getSRID());
        p.executeUpdate();
      }
      // Register every nested non-linear type, not just the top-level geometry.
      byte[] binary = GeoPackageBinary.encode(r.sample());
      Set<Integer> types =
          GeoPackageBinary.validateWkb(Arrays.copyOfRange(binary, 8, binary.length));
      for (int type : types)
        if (type >= 8)
          try (PreparedStatement p =
              c.prepareStatement(
                  "INSERT INTO gpkg_extensions"
                      + " VALUES(?,?,?,'http://www.geopackage.org/spec/#extension_geometry_types','read-write')")) {
            p.setString(1, r.layer());
            p.setString(2, geom);
            p.setString(3, "gpkg_geom_" + GeoPackageBinary.typeName(type));
            p.executeUpdate();
          }
      c.commit();
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
