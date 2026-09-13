package ch.so.agi.hop.vector.formats.geopackage;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver;
import ch.so.agi.hop.vector.core.*;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import com.atolcd.hop.gis.geometry.curve.CircularString;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;

class GeoPackageAppendTest {
  @TempDir Path temp;
  GeoPackageProvider provider = new GeoPackageProvider(new GeoToolsCrsDefinitionResolver());
  GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 2056);

  RowMeta fields() {
    var r = new RowMeta();
    r.addValueMeta(new ValueMetaString("name"));
    r.addValueMeta(new ValueMetaGeometry("geom"));
    return r;
  }

  Point point(double x, double y) {
    return factory.createPoint(new Coordinate(x, y));
  }

  WriteRequest request(
      Path path,
      String layer,
      GeoPackageOptions.WriteMode mode,
      boolean index,
      IRowMeta fields,
      int gi,
      Geometry sample,
      AtomicBoolean stop) {
    return new WriteRequest(
        path,
        layer,
        fields,
        gi,
        sample,
        sample == null ? null : GeometrySchema.infer(sample),
        new GeoPackageOptions(mode, index),
        Diagnostics.NONE,
        stop::get);
  }

  void create(Path path, boolean index) throws Exception {
    try (var w =
        provider.create(
            request(
                path,
                "places",
                GeoPackageOptions.WriteMode.CREATE_FILE,
                index,
                fields(),
                1,
                point(1, 2),
                new AtomicBoolean()))) {
      w.write(new Object[] {"old", point(1, 2)});
      w.finish();
    }
  }

  Connection connection(Path path) throws Exception {
    return DriverManager.getConnection("jdbc:sqlite:" + path);
  }

  long count(Path path, String table) throws Exception {
    try (var c = connection(path);
        var s = c.createStatement();
        var r = s.executeQuery("SELECT count(*) FROM " + GeoPackageProvider.quote(table))) {
      r.next();
      return r.getLong(1);
    }
  }

  WriteRequest append(Path p, boolean index, AtomicBoolean stop) {
    return request(
        p, "places", GeoPackageOptions.WriteMode.APPEND_FEATURES, index, fields(), 1, null, stop);
  }

  @Test
  void addsLayerAndAppendsMaintainingExistingIndexAndBounds() throws Exception {
    Path p = temp.resolve("a.gpkg");
    create(p, true);
    try (var w =
        provider.create(
            request(
                p,
                "other",
                GeoPackageOptions.WriteMode.ADD_LAYER,
                true,
                fields(),
                1,
                point(0, 0),
                new AtomicBoolean()))) {
      w.finish();
    }
    var rm = new RowMeta();
    rm.addValueMeta(new ValueMetaGeometry("incoming"));
    rm.addValueMeta(new ValueMetaString("NAME"));
    for (int i = 0; i < 2; i++)
      try (var w =
          provider.create(
              request(
                  p,
                  "places",
                  GeoPackageOptions.WriteMode.APPEND_FEATURES,
                  false,
                  rm,
                  0,
                  null,
                  new AtomicBoolean()))) {
        var unknown = new GeometryFactory().createPoint(new Coordinate(100 + i, 200));
        w.write(new Object[] {unknown, "new"});
        w.finish();
      }
    assertThat(count(p, "other")).isZero();
    assertThat(count(p, "places")).isEqualTo(3);
    assertThat(count(p, "rtree_places_geom")).isEqualTo(3);
    try (var c = connection(p);
        var s = c.createStatement()) {
      try (var r =
          s.executeQuery(
              "SELECT min_x,max_x,min_y,max_y FROM gpkg_contents WHERE table_name='places'")) {
        r.next();
        assertThat(r.getDouble(1)).isEqualTo(1);
        assertThat(r.getDouble(2)).isEqualTo(101);
        assertThat(r.getDouble(4)).isEqualTo(200);
      }
      try (var r =
          s.executeQuery(
              "SELECT count(*) FROM places p JOIN rtree_places_geom r ON p.fid=r.id WHERE"
                  + " r.maxx>=100 AND r.minx<=101")) {
        r.next();
        assertThat(r.getInt(1)).isEqualTo(2);
      }
    }
  }

  @Test
  void buildsMissingIndexIncludingOldRowsAndEmptyAppendIsNoOp() throws Exception {
    Path p = temp.resolve("a.gpkg");
    create(p, false);
    byte[] before = Files.readAllBytes(p);
    try (var w = provider.create(append(p, false, new AtomicBoolean()))) {
      w.finish();
    }
    assertThat(Files.readAllBytes(p)).isEqualTo(before);
    try (var w = provider.create(append(p, true, new AtomicBoolean()))) {
      w.finish();
    }
    assertThat(count(p, "rtree_places_geom")).isEqualTo(1);
    try (var w = provider.create(append(p, false, new AtomicBoolean()))) {
      w.write(new Object[] {"null", null});
      w.write(new Object[] {"empty", factory.createPoint()});
      w.finish();
    }
    assertThat(count(p, "places")).isEqualTo(3);
    assertThat(count(p, "rtree_places_geom")).isEqualTo(1);
  }

  @Test
  void failuresAndStopRollbackRowsSchemaAndIndex() throws Exception {
    Path p = temp.resolve("a.gpkg");
    create(p, false);
    byte[] original = Files.readAllBytes(p);
    var stop = new AtomicBoolean();
    try (var w = provider.create(append(p, true, stop))) {
      w.write(new Object[] {"new", point(5, 6)});
      stop.set(true);
      assertThatThrownBy(w::finish).hasMessageContaining("stopped");
    }
    assertThat(Files.readAllBytes(p)).isEqualTo(original);
    try (var w = provider.create(append(p, true, new AtomicBoolean()))) {
      w.write(new Object[] {"valid", point(5, 6)});
      assertThatThrownBy(
              () ->
                  w.write(
                      new Object[] {
                        "bad",
                        factory.createLineString(
                            new Coordinate[] {new Coordinate(1, 2), new Coordinate(3, 4)})
                      }))
          .hasMessageContaining("row 2");
      assertThatThrownBy(w::finish).hasMessageContaining("failed");
    }
    assertThat(Files.readAllBytes(p)).isEqualTo(original);
    try (var w =
        provider.create(
            request(
                p,
                "unpublished",
                GeoPackageOptions.WriteMode.ADD_LAYER,
                true,
                fields(),
                1,
                point(1, 2),
                new AtomicBoolean()))) {
      w.write(new Object[] {"discard", point(2, 3)});
    }
    assertThat(Files.readAllBytes(p)).isEqualTo(original);
  }

  @Test
  void validatesMappingsDefaultsConstraintsAndConflicts() throws Exception {
    Path p = temp.resolve("a.gpkg");
    create(p, false);
    try (var c = connection(p);
        var s = c.createStatement()) {
      s.execute("ALTER TABLE places ADD COLUMN required TEXT NOT NULL DEFAULT 'default'");
      s.execute("ALTER TABLE places ADD COLUMN limited TEXT(3)");
      s.execute("ALTER TABLE places ADD COLUMN number SMALLINT");
    }
    try (var w = provider.create(append(p, false, new AtomicBoolean()))) {
      w.write(new Object[] {"new", point(0, 0)});
      w.finish();
    }
    var extra = fields();
    extra.addValueMeta(new ValueMetaString("unknown"));
    assertThatThrownBy(
            () ->
                provider.create(
                    request(
                        p,
                        "places",
                        GeoPackageOptions.WriteMode.APPEND_FEATURES,
                        false,
                        extra,
                        1,
                        null,
                        new AtomicBoolean())))
        .hasMessageContaining("Unknown target field");
    var fid = fields();
    fid.addValueMeta(new ValueMetaInteger("fid"));
    assertThatThrownBy(
            () ->
                provider.create(
                    request(
                        p,
                        "places",
                        GeoPackageOptions.WriteMode.APPEND_FEATURES,
                        false,
                        fid,
                        1,
                        null,
                        new AtomicBoolean())))
        .hasMessageContaining("SOURCE_FID");
    var rm = fields();
    rm.addValueMeta(new ValueMetaString("limited"));
    rm.addValueMeta(new ValueMetaInteger("number"));
    for (Object[] values :
        List.of(
            new Object[] {"x", point(1, 2), "long", 1L},
            new Object[] {"x", point(1, 2), "ok", 40000L}))
      try (var w =
          provider.create(
              request(
                  p,
                  "places",
                  GeoPackageOptions.WriteMode.APPEND_FEATURES,
                  true,
                  rm,
                  1,
                  null,
                  new AtomicBoolean()))) {
        assertThatThrownBy(() -> w.write(values)).hasMessageContaining("Field");
      }
    assertThat(count(p, "places")).isEqualTo(2);
    assertThatThrownBy(
            () ->
                provider.create(
                    request(
                        p,
                        "places",
                        GeoPackageOptions.WriteMode.ADD_LAYER,
                        true,
                        fields(),
                        1,
                        point(0, 0),
                        new AtomicBoolean())))
        .hasMessageContaining("already exists");
    assertThatThrownBy(
            () ->
                provider.create(
                    request(
                        p,
                        "missing",
                        GeoPackageOptions.WriteMode.APPEND_FEATURES,
                        true,
                        fields(),
                        1,
                        null,
                        new AtomicBoolean())))
        .hasMessageContaining("does not exist");
    assertThatThrownBy(
            () -> provider.create(append(temp.resolve("missing.gpkg"), true, new AtomicBoolean())))
        .hasMessageContaining("does not exist");
  }

  @Test
  void detectsIncompleteIndexAndHonorsExternalWriteLock() throws Exception {
    Path p = temp.resolve("a.gpkg");
    create(p, true);
    try (var c = connection(p);
        var s = c.createStatement()) {
      s.execute("BEGIN IMMEDIATE");
      assertThatThrownBy(() -> provider.create(append(p, true, new AtomicBoolean())))
          .isInstanceOf(SQLException.class);
      s.execute("ROLLBACK");
      s.execute("DROP TRIGGER rtree_places_geom_insert");
    }
    assertThatThrownBy(() -> provider.create(append(p, false, new AtomicBoolean())))
        .hasMessageContaining("Incomplete spatial index");
    assertThat(count(p, "places")).isEqualTo(1);
  }

  @Test
  void stopDuringIndexBuildRollsBackAndMissingBoundsAreRecovered() throws Exception {
    Path p = temp.resolve("rebuild.gpkg");
    create(p, false);
    try (var w = provider.create(append(p, false, new AtomicBoolean()))) {
      for (int i = 0; i < 10; i++) w.write(new Object[] {"existing", point(i + 2, i + 3)});
      w.finish();
    }
    byte[] before = Files.readAllBytes(p);
    var checks = new java.util.concurrent.atomic.AtomicInteger();
    var request =
        new WriteRequest(
            p,
            "places",
            fields(),
            1,
            null,
            null,
            new GeoPackageOptions(GeoPackageOptions.WriteMode.APPEND_FEATURES, true),
            Diagnostics.NONE,
            () -> checks.incrementAndGet() > 4);
    assertThatThrownBy(() -> provider.create(request)).hasMessageContaining("stopped");
    assertThat(checks.get()).isGreaterThan(4);
    assertThat(Files.readAllBytes(p)).isEqualTo(before);
    try (var c = connection(p);
        var statement = c.createStatement()) {
      statement.execute(
          "UPDATE gpkg_contents SET min_x=NULL,max_x=NULL,min_y=NULL,max_y=NULL WHERE"
              + " table_name='places'");
    }
    try (var w = provider.create(append(p, true, new AtomicBoolean()))) {
      w.write(new Object[] {"new", point(100, 200)});
      w.finish();
    }
    try (var c = connection(p);
        var statement = c.createStatement();
        var rs =
            statement.executeQuery(
                "SELECT min_x,min_y,max_x,max_y FROM gpkg_contents WHERE table_name='places'")) {
      rs.next();
      assertThat(rs.getDouble(1)).isEqualTo(1);
      assertThat(rs.getDouble(2)).isEqualTo(2);
      assertThat(rs.getDouble(3)).isEqualTo(100);
      assertThat(rs.getDouble(4)).isEqualTo(200);
    }
  }

  @Test
  void curvedIndexIncludesExactExtremumAndTriggersHandleUpdatesAndDeletes() throws Exception {
    Path p = temp.resolve("curve.gpkg");
    Coordinate[] points = new Coordinate[3];
    int i = 0;
    for (double degree : new double[] {10, 65, 170}) {
      double a = Math.toRadians(degree);
      points[i++] = new Coordinate(10 * Math.cos(a), 10 * Math.sin(a));
    }
    var curve = new CircularString(points, factory);
    try (var w =
        provider.create(
            request(
                p,
                "curves",
                GeoPackageOptions.WriteMode.CREATE_FILE,
                true,
                fields(),
                1,
                curve,
                new AtomicBoolean()))) {
      w.write(new Object[] {"arc", curve});
      w.finish();
    }
    try (var c = connection(p);
        var s = c.createStatement()) {
      GeoPackageIndex.register(c);
      try (var r = s.executeQuery("SELECT maxy FROM rtree_curves_geom")) {
        r.next();
        assertThat(r.getDouble(1)).isGreaterThanOrEqualTo(10);
      }
      try (var r = s.executeQuery("SELECT max_y FROM gpkg_contents WHERE table_name='curves'")) {
        r.next();
        assertThat(r.getDouble(1)).isCloseTo(10, within(1e-12));
      }
      s.execute("UPDATE curves SET fid=100");
      try (var r = s.executeQuery("SELECT id FROM rtree_curves_geom")) {
        r.next();
        assertThat(r.getInt(1)).isEqualTo(100);
      }
      s.execute("UPDATE curves SET geom=NULL");
      assertThat(count(p, "rtree_curves_geom")).isZero();
      try (var ins = c.prepareStatement("UPDATE curves SET geom=?")) {
        ins.setBytes(1, GeoPackageBinary.encode(curve));
        ins.executeUpdate();
      }
      assertThat(count(p, "rtree_curves_geom")).isEqualTo(1);
      s.execute("DELETE FROM curves");
      assertThat(count(p, "rtree_curves_geom")).isZero();
    }
  }
}
