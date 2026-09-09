package ch.so.agi.hop.vector.formats.geopackage;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver;
import ch.so.agi.hop.vector.core.*;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import com.atolcd.hop.gis.geometry.curve.*;
import java.nio.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.*;

class GeoPackageAdapterTest {
  @TempDir Path dir;
  final GeoPackageProvider provider = new GeoPackageProvider(new GeoToolsCrsDefinitionResolver());
  final GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 2056);

  RowMeta meta() {
    RowMeta rm = new RowMeta();
    rm.addValueMeta(new ValueMetaString("name"));
    rm.addValueMeta(new ValueMetaGeometry("shape"));
    return rm;
  }

  WriteRequest request(Path file, RowMeta rm, Geometry sample) {
    return new WriteRequest(
        file,
        "a layer",
        rm,
        1,
        sample,
        null,
        new ch.so.agi.hop.vector.core.FormatOptions.None(),
        ch.so.agi.hop.vector.core.Diagnostics.NONE);
  }

  @Test
  void attributesNullEmptyBoundsAndIndependentReader() throws Exception {
    Path file = dir.resolve("values.gpkg");
    RowMeta rm = meta();
    rm.addValueMeta(new ValueMetaInteger("fid"));
    rm.addValueMeta(new ValueMetaBoolean("flag"));
    rm.addValueMeta(new ValueMetaNumber("height"));
    rm.addValueMeta(new ValueMetaDate("date"));
    rm.addValueMeta(new ValueMetaBinary("blob"));
    Point point = gf.createPoint(new Coordinate(2600000, 1200000));
    java.util.Date date = java.util.Date.from(java.time.Instant.parse("2026-09-09T10:20:30Z"));
    try (VectorSink sink = provider.create(request(file, rm, point))) {
      assertThat(file).doesNotExist();
      sink.write(new Object[] {"A", point, 99L, true, 4.5, date, new byte[] {1, 2, 3}});
      sink.write(new Object[] {null, null, null, null, null, null, null});
      sink.write(new Object[] {"empty", gf.createPoint(), 101L, false, 0.0, date, new byte[0]});
      sink.finish();
    }
    try (VectorSource source = provider.open(file, "", "geometry")) {
      assertThat(source.schema().rowMeta().getFieldNames())
          .containsExactly("name", "fid", "flag", "height", "date", "blob", "geometry");
      Object[] a = source.read();
      assertThat(a[1]).isEqualTo(99L);
      assertThat(a[2]).isEqualTo(true);
      assertThat(a[3]).isEqualTo(4.5);
      assertThat(a[4]).isEqualTo(date);
      assertThat((byte[]) a[5]).containsExactly(1, 2, 3);
      assertThat(((Geometry) a[6]).getSRID()).isEqualTo(2056);
      assertThat(source.read()).containsOnlyNulls();
      assertThat(((Geometry) source.read()[6]).isEmpty()).isTrue();
      assertThat(source.read()).isNull();
    }
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file);
        Statement s = c.createStatement()) {
      try (ResultSet rs = s.executeQuery("PRAGMA foreign_key_check")) {
        assertThat(rs.next()).isFalse();
      }
      try (ResultSet rs = s.executeQuery("PRAGMA integrity_check")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getString(1)).isEqualTo("ok");
      }
      try (ResultSet rs = s.executeQuery("SELECT min_x,max_y FROM gpkg_contents")) {
        rs.next();
        assertThat(rs.getDouble(1)).isEqualTo(2600000);
        assertThat(rs.getDouble(2)).isEqualTo(1200000);
      }
      try (ResultSet rs =
          s.executeQuery(
              "SELECT hex(shape) FROM "
                  + GeoPackageProvider.quote("a layer")
                  + " WHERE name='empty'")) {
        rs.next();
        assertThat(rs.getString(1)).startsWith("47500011");
      }
    }
    var store = ch.so.agi.hop.vector.formats.shapefile.GeoToolsReference.open(file);
    try (var it = store.getFeatureSource(store.getTypeNames()[0]).getFeatures().features()) {
      assertThat(it.hasNext()).isTrue();
      assertThat(((Geometry) it.next().getDefaultGeometry()).equalsExact(point)).isTrue();
      assertThat(it.next().getDefaultGeometry()).isNull();
      assertThat(((Geometry) it.next().getDefaultGeometry()).isEmpty()).isTrue();
    } finally {
      store.dispose();
    }
  }

  @Test
  void cancellationFailureAndOwnershipNeverPublishIncompleteFile() throws Exception {
    Path file = dir.resolve("abort.gpkg");
    Point point = gf.createPoint(new Coordinate(1, 2));
    WriteRequest r = request(file, meta(), point);
    try (VectorSink sink = provider.create(r)) {
      sink.write(new Object[] {"x", point});
      assertThatThrownBy(() -> provider.create(r)).hasMessageContaining("owns");
      Point other = new GeometryFactory().createPoint(new Coordinate(1, 2));
      assertThatThrownBy(() -> sink.write(new Object[] {"bad", other}))
          .hasMessageContaining("SRID");
    }
    assertThat(file).doesNotExist();
    try (var files = Files.list(dir)) {
      assertThat(files.toList()).isEmpty();
    }
    try (VectorSink sink = provider.create(r)) {
      sink.write(new Object[] {"ok", point});
      sink.finish();
    }
    byte[] original = Files.readAllBytes(file);
    assertThatThrownBy(() -> provider.create(r)).hasMessageContaining("already exists");
    assertThat(Files.readAllBytes(file)).isEqualTo(original);
  }

  @Test
  void recordsNestedCurveExtensionsEvenWhenFirstCompoundHasOnlyLines() throws Exception {
    var line = gf.createLineString(new Coordinate[] {new Coordinate(0, 0), new Coordinate(1, 1)});
    var first = new CompoundCurve(List.of(line), gf);
    first.setSRID(2056);
    var arc =
        new CircularString(
            new Coordinate[] {new Coordinate(0, 0), new Coordinate(1, 1), new Coordinate(2, 0)},
            gf);
    var later = new CompoundCurve(List.of(arc), gf);
    later.setSRID(2056);
    Path file = dir.resolve("nested.gpkg");
    try (var sink = provider.create(request(file, meta(), first))) {
      sink.write(new Object[] {"first", first});
      sink.write(new Object[] {"later", later});
      sink.finish();
    }
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file);
        Statement s = c.createStatement();
        ResultSet rs =
            s.executeQuery("SELECT extension_name FROM gpkg_extensions ORDER BY extension_name")) {
      List<String> names = new ArrayList<>();
      while (rs.next()) names.add(rs.getString(1));
      assertThat(names).containsExactly("gpkg_geom_CIRCULARSTRING", "gpkg_geom_COMPOUNDCURVE");
    }
    try (var source = provider.open(file, "", "")) {
      source.read();
      Geometry g = (Geometry) source.read()[1];
      assertThat(CurveGeometrySupport.writeWkt(g)).isEqualTo(CurveGeometrySupport.writeWkt(later));
    }
  }

  @Test
  void binaryFixturesValidateBothByteOrdersEmptyAndMalformedDimensions() throws Exception {
    // Hand-written standard GP headers + Point(1 2) WKB, independent of our encoder.
    byte[] little =
        HexFormat.of().parseHex("47500001080800000101000000000000000000f03f0000000000000040");
    byte[] big =
        HexFormat.of().parseHex("475000000000080800000000013ff00000000000004000000000000000");
    for (byte[] bytes : List.of(little, big)) {
      Geometry g = GeoPackageBinary.decode(bytes, 2056);
      assertThat(g.getCoordinate()).isEqualTo(new Coordinate(1, 2));
      assertThat(GeoPackageBinary.encode(g)).isEqualTo(little);
    }
    byte[] envelope =
        ByteBuffer.allocate(little.length + 32)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put((byte) 'G')
            .put((byte) 'P')
            .put((byte) 0)
            .put((byte) 3)
            .putInt(2056)
            .putDouble(1)
            .putDouble(1)
            .putDouble(2)
            .putDouble(2)
            .put(little, 8, little.length - 8)
            .array();
    assertThat(GeoPackageBinary.decode(envelope, 2056).getCoordinate())
        .isEqualTo(new Coordinate(1, 2));
    assertThatThrownBy(() -> GeoPackageBinary.decode(little, 4326)).hasMessageContaining("SRID");
    byte[] bad = little.clone();
    bad[3] = 17;
    assertThatThrownBy(() -> GeoPackageBinary.decode(bad, 2056)).hasMessageContaining("empty");
    byte[] xyz =
        new WKBWriter(3, ByteOrderValues.LITTLE_ENDIAN)
            .write(gf.createPoint(new Coordinate(1, 2, 3)));
    byte[] gp =
        ByteBuffer.allocate(8 + xyz.length)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(little, 0, 8)
            .put(xyz)
            .array();
    assertThatThrownBy(() -> GeoPackageBinary.decode(gp, 2056)).hasMessageContaining("dimension");
    assertThatThrownBy(() -> GeoPackageBinary.encode(gf.createPoint(new Coordinate(1, 2, 3))))
        .hasMessageContaining("XY only");
    assertThatThrownBy(() -> GeoPackageBinary.decode(Arrays.copyOf(little, 12), 2056))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsCurvesNestedInLinearCollectionsWithoutLosingTheirShape() {
    CircularString arc =
        new CircularString(
            new Coordinate[] {new Coordinate(0, 0), new Coordinate(1, 1), new Coordinate(2, 0)},
            gf);
    assertThatThrownBy(
            () -> GeoPackageBinary.encode(gf.createGeometryCollection(new Geometry[] {arc})))
        .hasMessageContaining("use MultiCurve");
    assertThatThrownBy(
            () -> GeoPackageBinary.encode(gf.createMultiLineString(new LineString[] {arc})))
        .hasMessageContaining("use MultiCurve");
  }

  @Test
  void independentlyConstructedSqlFixtureIncludesNullEmptyAndLayerSelection() throws Exception {
    Path file = dir.resolve("independent.gpkg");
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file);
        Statement s = c.createStatement()) {
      String fixture =
          Files.readString(Path.of(getClass().getResource("/independent.sql").toURI()));
      for (String sql : fixture.split(";")) if (!sql.isBlank()) s.execute(sql);
    }
    assertThat(provider.layers(file)).extracting(LayerSchema::name).containsExactly("points");
    try (var source = provider.open(file, "POINTS", "geom")) {
      assertThat(source.read()[0]).isEqualTo("one");
      assertThat(source.read()[1]).isNull();
      assertThat(((Geometry) source.read()[1]).isEmpty()).isTrue();
      assertThat(source.read()).isNull();
    }
    assertThatThrownBy(() -> provider.open(file, "missing", "")).hasMessageContaining("not found");
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file);
        Statement s = c.createStatement()) {
      s.execute("UPDATE gpkg_geometry_columns SET z=1");
    }
    assertThatThrownBy(() -> provider.open(file, "points", "")).hasMessageContaining("Z/M");
    // An unsupported layer must not prevent discovery or reading a different XY layer.
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file);
        Statement statement = c.createStatement()) {
      statement.execute("CREATE TABLE other(fid INTEGER PRIMARY KEY,label TEXT,shape POINT)");
      statement.execute("INSERT INTO other SELECT * FROM points");
      statement.execute("INSERT INTO gpkg_contents VALUES('other','features',2056)");
      statement.execute(
          "INSERT INTO gpkg_geometry_columns VALUES('other','shape','POINT',2056,0,0)");
    }
    assertThat(provider.layers(file))
        .extracting(LayerSchema::name)
        .containsExactly("other", "points");
    try (var source = provider.open(file, "other", "")) {
      assertThat(source.read()[1]).isInstanceOf(Point.class);
    }
  }
}
