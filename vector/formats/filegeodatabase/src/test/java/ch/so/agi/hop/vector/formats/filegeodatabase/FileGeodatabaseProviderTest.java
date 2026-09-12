package ch.so.agi.hop.vector.formats.filegeodatabase;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.catalog.CrsDefinition;
import ch.so.agi.filegdb.geometry.FileGdbPart;
import ch.so.agi.filegdb.geometry.FileGdbPoint;
import ch.so.agi.filegdb.geometry.FileGdbPolygon;
import ch.so.agi.filegdb.geometry.GeometryFieldDefinition;
import ch.so.agi.filegdb.geometry.GeometryKind;
import ch.so.agi.filegdb.table.FileGdbField;
import ch.so.agi.filegdb.write.FeatureClassDefinition;
import ch.so.agi.filegdb.write.GdbFeatureWriter;
import ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver;
import ch.so.agi.hop.vector.core.CrsDefinitionResolver;
import ch.so.agi.hop.vector.core.Diagnostics;
import ch.so.agi.hop.vector.core.FormatOptions;
import ch.so.agi.hop.vector.core.GeometrySchema;
import ch.so.agi.hop.vector.core.LayerSchema;
import ch.so.agi.hop.vector.core.Ordinate;
import ch.so.agi.hop.vector.core.ReadRequest;
import ch.so.agi.hop.vector.core.VectorSink;
import ch.so.agi.hop.vector.core.VectorSource;
import ch.so.agi.hop.vector.core.WriteRequest;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

class FileGeodatabaseProviderTest {

  private final FileGeodatabaseProvider provider =
      new FileGeodatabaseProvider(new GeoToolsCrsDefinitionResolver());
  private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 2056);

  @Test
  void repeatedCurveRoundTripsDoNotAddQuantizedVertices() {
    var adapter = new HopCurveAdapter(2056);
    var curve =
        new com.atolcd.hop.gis.geometry.curve.CircularString(
            new Coordinate[] {
              new org.locationtech.jts.geom.CoordinateXYZM(2600005, 1200000, 1, 10),
              new org.locationtech.jts.geom.CoordinateXYZM(2600000, 1200005, 2, 20),
              new org.locationtech.jts.geom.CoordinateXYZM(2599995, 1200000, 3, 30)
            },
            geometryFactory);
    var definition = GeometryFieldDefinition.of("shape", GeometryKind.LINE).withZ().withM();
    definition =
        definition.withPrecision(definition.precision().withXY(0.001, 0.01, 2500000, 1100000));
    org.locationtech.jts.geom.Geometry current = curve;
    for (int i = 0; i < 5; i++) {
      var nativeCurve = (ch.so.agi.filegdb.geometry.FileGdbPolyline) adapter.write(current);
      assertThat(nativeCurve.parts().getFirst().points()).hasSize(2);
      var bytes = ch.so.agi.filegdb.geometry.GeometryCodec.encode(nativeCurve, definition);
      var decoded = ch.so.agi.filegdb.geometry.GeometryCodec.decode(bytes, definition);
      var bounds = ch.so.agi.filegdb.geometry.GeometryBounds.of(decoded);
      assertThat(bounds.xMin()).isEqualTo(2599995);
      assertThat(bounds.yMax()).isEqualTo(1200005);
      current = adapter.read(decoded);
    }
  }

  @Test
  void nativeCurveCodecPreservesAllFourDimensions() {
    var adapter = new HopCurveAdapter(2056);
    for (int dimension = 0; dimension < 4; dimension++) {
      org.locationtech.jts.geom.Coordinate[] coordinates =
          new org.locationtech.jts.geom.Coordinate[3];
      double[][] xy = {{5, 0}, {0, 5}, {-5, 0}};
      for (int i = 0; i < 3; i++)
        coordinates[i] =
            switch (dimension) {
              case 0 -> new org.locationtech.jts.geom.CoordinateXY(xy[i][0], xy[i][1]);
              case 1 -> new Coordinate(xy[i][0], xy[i][1], i + 1);
              case 2 -> new org.locationtech.jts.geom.CoordinateXYM(xy[i][0], xy[i][1], i + 10);
              default ->
                  new org.locationtech.jts.geom.CoordinateXYZM(xy[i][0], xy[i][1], i + 1, i + 10);
            };
      var curve =
          new com.atolcd.hop.gis.geometry.curve.CircularString(coordinates, geometryFactory);
      var definition = GeometryFieldDefinition.of("shape", GeometryKind.LINE);
      if (dimension == 1 || dimension == 3) definition = definition.withZ();
      if (dimension >= 2) definition = definition.withM();
      var encoded =
          ch.so.agi.filegdb.geometry.GeometryCodec.encode(adapter.write(curve), definition);
      var decoded =
          adapter.read(ch.so.agi.filegdb.geometry.GeometryCodec.decode(encoded, definition));
      assertThat(GeometrySchema.infer(decoded).dimension())
          .isEqualTo(GeometrySchema.infer(curve).dimension());
      assertThat(com.atolcd.hop.gis.geometry.curve.CurveGeometrySupport.isCurveGeometry(decoded))
          .isTrue();
      var points = decoded.getCoordinates();
      assertThat(points[0].x).isEqualTo(5);
      assertThat(points[points.length - 1].x).isEqualTo(-5);
      if (dimension >= 2) {
        assertThat(points[0].getM()).isEqualTo(10);
        assertThat(points[points.length - 1].getM()).isEqualTo(12);
      }
    }
  }

  @Test
  void readsLayersAndFeatures(@TempDir Path directory) throws Exception {
    Path gdb = directory.resolve("source.gdb");
    writeFixture(gdb);

    List<LayerSchema> layers =
        provider.layers(
            new ReadRequest(gdb, "", "", "", new FormatOptions.None(), Diagnostics.NONE));
    assertThat(layers).hasSize(1);
    LayerSchema layer = layers.get(0);
    assertThat(layer.name()).isEqualTo("roads");
    assertThat(layer.geometryType()).isEqualTo("Polygon");
    assertThat(layer.srid()).isEqualTo(2056);
    assertThat(layer.rowMeta().getValueMetaList())
        .extracting(org.apache.hop.core.row.IValueMeta::getName)
        .containsExactly("OBJECTID", "name", "lanes", "shape");

    try (VectorSource source =
        provider.open(
            new ReadRequest(
                gdb, "roads", "shape", "", new FormatOptions.None(), Diagnostics.NONE))) {
      Object[] first = source.read();
      assertThat(first).isNotNull();
      assertThat(first[1]).isEqualTo("A1");
      assertThat(first[2]).isEqualTo(2L);
      assertThat(first[3]).isInstanceOf(org.locationtech.jts.geom.Polygon.class);
      assertThat(((org.locationtech.jts.geom.Geometry) first[3]).getSRID()).isEqualTo(2056);
      Object[] second = source.read();
      assertThat(second[1]).isEqualTo("B2");
      assertThat(second[3]).isNull();
      assertThat(source.read()).isNull();
    }
  }

  @Test
  void writesFileGeodatabase(@TempDir Path directory) throws Exception {
    Path output = directory.resolve("written.gdb");
    CrsDefinitionResolver.Definition crs = new GeoToolsCrsDefinitionResolver().resolve(2056);

    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("name"));
    rowMeta.addValueMeta(new ValueMetaInteger("lanes"));
    rowMeta.addValueMeta(new ValueMetaGeometry("shape"));
    Polygon sample = square(2600000, 1200000, 100);
    GeometrySchema schema = new GeometrySchema("Polygon", Ordinate.ABSENT, Ordinate.ABSENT, crs);
    WriteRequest request =
        new WriteRequest(
            output,
            "roads",
            rowMeta,
            2,
            sample,
            schema,
            new FormatOptions.None(),
            Diagnostics.NONE);

    try (VectorSink sink = provider.create(request)) {
      assertThat(sink.write(new Object[] {"A1", 2L, square(2600000, 1200000, 100)})).isTrue();
      assertThat(sink.write(new Object[] {"B2", null, null})).isTrue();
      sink.finish();
    }

    assertThat(output).exists();
    try (FileGeodatabase database = FileGeodatabase.open(output);
        var table = database.featureClass("roads")) {
      assertThat(table.rowCount()).isEqualTo(2);
      assertThat(table.crs().effectiveWkid()).isEqualTo(2056);
      assertThat(table.read(1).get("name")).isEqualTo("A1");
      assertThat(table.read(1).get("lanes")).isEqualTo(2L);
      assertThat(table.read(2).get("lanes")).isNull();
      assertThat(table.read(2).geometry()).isNull();
    }

    try (var entries = Files.list(directory)) {
      assertThat(entries.map(path -> path.getFileName().toString()))
          .noneMatch(name -> name.startsWith(".hop-filegdb-"));
    }
  }

  @Test
  void transportsCurvesDatesIdsAndPrecision(@TempDir Path directory) throws Exception {
    var f = geometryFactory;
    var curve =
        new com.atolcd.hop.gis.geometry.curve.CircularString(
            new Coordinate[] {
              new org.locationtech.jts.geom.CoordinateXYZM(2600005, 1200000, 1, 10),
              new org.locationtech.jts.geom.CoordinateXYZM(2600000, 1200005, 3, 20),
              new org.locationtech.jts.geom.CoordinateXYZM(2599995, 1200000, 7, 30)
            },
            f);
    IRowMeta rm = new RowMeta();
    rm.addValueMeta(new ValueMetaInteger("OBJECTID"));
    rm.addValueMeta(new org.apache.hop.core.row.value.ValueMetaDate("created"));
    rm.addValueMeta(new ValueMetaGeometry("shape"));
    var options =
        new ch.so.agi.hop.vector.core.FileGeodatabaseOptions(
            "AUTO", 0.001, 0.01, 2500000.0, 1100000.0, true, null);
    var geometry =
        new GeometrySchema(
            "CircularString",
            Ordinate.REQUIRED,
            Ordinate.OPTIONAL,
            new GeoToolsCrsDefinitionResolver().resolve(2056));
    Path output = directory.resolve("curves.gdb");
    java.util.Date date = java.util.Date.from(java.time.Instant.parse("2024-05-07T08:30:15Z"));
    try (var sink =
        provider.create(
            new WriteRequest(
                output, "curves", rm, 2, curve, geometry, options, Diagnostics.NONE))) {
      sink.write(new Object[] {99L, date, curve});
      sink.finish();
    }
    try (var source =
        provider.open(
            new ReadRequest(
                output, "curves", "shape", "", new FormatOptions.None(), Diagnostics.NONE))) {
      Object[] row = source.read();
      assertThat(row[source.schema().rowMeta().indexOfValue("SOURCE_OBJECTID")]).isEqualTo(99L);
      int dateIndex = source.schema().rowMeta().indexOfValue("created");
      assertThat(source.schema().rowMeta().getValueMeta(dateIndex).getDate(row[dateIndex]))
          .isEqualTo(date);
      var actual =
          (org.locationtech.jts.geom.Geometry) row[source.schema().rowMeta().indexOfValue("shape")];
      assertThat(com.atolcd.hop.gis.geometry.curve.CurveGeometrySupport.isCurveGeometry(actual))
          .isTrue();
      assertThat(com.atolcd.hop.gis.geometry.curve.CurveGeometrySupport.writeWkt(actual))
          .contains("CIRCULARSTRING");
    }
    try (var db = FileGeodatabase.open(output);
        var table = db.table("curves")) {
      assertThat(table.geomField().geometry().precision().xyResolution()).isEqualTo(0.001);
      assertThat(
              table
                  .query(
                      new ch.so.agi.filegdb.geometry.Envelope(2599999, 1200004, 2600001, 1200006))
                  .rows())
          .hasSize(1);
    }
  }

  private void writeFixture(Path gdb) throws Exception {
    try (FileGeodatabase database = FileGeodatabase.create(gdb)) {
      FeatureClassDefinition definition =
          FeatureClassDefinition.builder("roads")
              .field(FileGdbField.string("name", 255).asRequired())
              .field(FileGdbField.integer("lanes").asNullable())
              .geometry(GeometryFieldDefinition.of("shape", GeometryKind.POLYGON))
              .crs(new CrsDefinition(2056, 2056, ""))
              .build();
      try (GdbFeatureWriter writer = database.createFeatureClass(definition)) {
        writer.write(new Object[] {"A1", 2L}, fileGdbSquare(2600000, 1200000, 100));
        writer.write(new Object[] {"B2", null}, null);
      }
    }
  }

  private Polygon square(double x, double y, double size) {
    return geometryFactory.createPolygon(
        new Coordinate[] {
          new Coordinate(x, y),
          new Coordinate(x + size, y),
          new Coordinate(x + size, y + size),
          new Coordinate(x, y + size),
          new Coordinate(x, y)
        });
  }

  private static FileGdbPolygon fileGdbSquare(double x, double y, double size) {
    List<FileGdbPoint> ring =
        List.of(
            new FileGdbPoint(x, y),
            new FileGdbPoint(x + size, y),
            new FileGdbPoint(x + size, y + size),
            new FileGdbPoint(x, y + size),
            new FileGdbPoint(x, y));
    return new FileGdbPolygon(List.of(new FileGdbPart(ring)));
  }
}
