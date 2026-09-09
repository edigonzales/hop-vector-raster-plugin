package ch.so.agi.hop.vector.formats.shapefile;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.vector.core.*;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.io.*;
import java.math.*;
import java.nio.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;

class ShapefileAdapterTest {
  @TempDir Path dir;
  final ShapefileProvider provider = new ShapefileProvider();
  final GeometryFactory gf = new GeometryFactory();

  RowMeta meta() {
    var rm = new RowMeta();
    rm.addValueMeta(new ValueMetaString("name"));
    rm.addValueMeta(new ValueMetaGeometry("shape"));
    return rm;
  }

  WriteRequest request(Path file, RowMeta rm, Geometry g, ShapefileOptions options, Diagnostics d) {
    return new WriteRequest(file, "layer", rm, rm.indexOfValue("shape"), g, null, options, d);
  }

  Geometry point(String dimension) {
    Coordinate c =
        switch (dimension) {
          case "XY" -> new CoordinateXY(1, 2);
          case "XYZ" -> new Coordinate(1, 2, 3);
          case "XYM" -> new CoordinateXYM(1, 2, 7);
          default -> new CoordinateXYZM(1, 2, 3, 7);
        };
    return gf.createPoint(c);
  }

  @Test
  void allFamiliesAndDimensionsIncludingHopSerialization() throws Exception {
    for (String dim : List.of("XY", "XYZ", "XYM", "XYZM")) {
      var p = (Point) point(dim);
      Coordinate a = p.getCoordinate();
      Coordinate b = a.copy();
      b.x = 4;
      b.y = 5;
      Coordinate c = a.copy();
      c.x = 5;
      c.y = 1;
      Geometry[] geometries = {
        p,
        gf.createMultiPointFromCoords(new Coordinate[] {a, b}),
        gf.createLineString(new Coordinate[] {a, b}),
        gf.createMultiLineString(
            new LineString[] {
              gf.createLineString(new Coordinate[] {a, b}),
              gf.createLineString(new Coordinate[] {b, c})
            }),
        gf.createPolygon(new Coordinate[] {a, b, c, a.copy()}),
        gf.createMultiPolygon(
            new Polygon[] {gf.createPolygon(new Coordinate[] {a, b, c, a.copy()})})
      };
      for (int i = 0; i < geometries.length; i++) {
        Geometry g = geometries[i];
        Path file = dir.resolve(dim + i + ".shp");
        try (var sink =
            provider.create(
                request(file, meta(), g, ShapefileOptions.defaults(), Diagnostics.NONE))) {
          sink.write(new Object[] {"Grüezi", g});
          sink.finish();
        }
        Geometry read;
        try (var source = provider.open(file, "", "shape")) {
          Object[] row = source.read();
          assertThat(row[0]).isEqualTo("Grüezi");
          read = (Geometry) row[1];
          assertThat(read.getCoordinates()).hasSize(g.getCoordinates().length);
          if (dim.contains("M")) assertThat(source.schema().m()).isNotEqualTo(Ordinate.ABSENT);
          assertThat(source.read()).isNull();
        }
        var vm = new ValueMetaGeometry("shape");
        var bytes = new ByteArrayOutputStream();
        vm.writeData(new DataOutputStream(bytes), read);
        read =
            (Geometry)
                vm.readData(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        for (var coord : read.getCoordinates()) {
          if (dim.contains("Z")) assertThat(coord.getZ()).isEqualTo(3);
          else assertThat(coord.getZ()).isNaN();
          if (dim.contains("M")) assertThat(coord.getM()).isEqualTo(7);
          else assertThat(coord.getM()).isNaN();
        }
        Path again = dir.resolve(dim + i + "again.shp");
        try (var sink =
            provider.create(
                request(again, meta(), read, ShapefileOptions.defaults(), Diagnostics.NONE))) {
          sink.write(new Object[] {"again", read});
          sink.finish();
        }
      }
    }
  }

  @Test
  void independentEsriPointMFixture() throws Exception {
    // Literal layout from the Esri whitepaper, not produced by the adapter.
    Path path = dir.resolve("fixture.shp");
    ByteBuffer shp = ByteBuffer.allocate(136);
    shp.putInt(9994);
    shp.position(24);
    shp.putInt(68);
    shp.order(ByteOrder.LITTLE_ENDIAN).putInt(1000).putInt(21);
    shp.putDouble(12).putDouble(34).putDouble(12).putDouble(34);
    shp.position(100);
    shp.order(ByteOrder.BIG_ENDIAN).putInt(1).putInt(14);
    shp.order(ByteOrder.LITTLE_ENDIAN).putInt(21).putDouble(12).putDouble(34).putDouble(56);
    Files.write(path, shp.array());
    ByteBuffer dbf = ByteBuffer.allocate(35).order(ByteOrder.LITTLE_ENDIAN);
    dbf.put((byte) 3);
    dbf.position(4);
    dbf.putInt(1).putShort((short) 33).putShort((short) 1);
    dbf.position(32);
    dbf.put((byte) 13).put((byte) 32).put((byte) 26);
    Files.write(dir.resolve("fixture.dbf"), dbf.array());
    try (var source = provider.open(path, "", "geom")) {
      var g = (Point) source.read()[0];
      assertThat(g.getX()).isEqualTo(12);
      assertThat(g.getCoordinate().getM()).isEqualTo(56);
      assertThat(g.getCoordinate().getZ()).isNaN();
      assertThat(source.read()).isNull();
    }
    assertThat(dir.resolve("fixture.shx")).doesNotExist();
  }

  @Test
  void geotoolsReadsNativeAndNativeReadsGeotools() throws Exception {
    Path file = dir.resolve("cross.shp");
    Geometry g = point("XYM");
    try (var sink =
        provider.create(request(file, meta(), g, ShapefileOptions.defaults(), Diagnostics.NONE))) {
      sink.write(new Object[] {"hello", g});
      sink.finish();
    }
    var ds =
        new org.geotools.data.shapefile.ShapefileDataStoreFactory()
            .createDataStore(file.toUri().toURL());
    try {
      try (var features = ds.getFeatureSource().getFeatures().features()) {
        Geometry result = (Geometry) features.next().getDefaultGeometry();
        assertThat(result.getCoordinate().getM()).isEqualTo(7);
      }
    } finally {
      ds.dispose();
    }
    Path reference = dir.resolve("reference.shp");
    var store =
        new org.geotools.data.shapefile.ShapefileDataStoreFactory()
            .createNewDataStore(
                Map.of("url", reference.toUri().toURL(), "create spatial index", false));
    try {
      var b = new org.geotools.feature.simple.SimpleFeatureTypeBuilder();
      b.setName("reference");
      b.add("the_geom", Point.class);
      b.add("label", String.class);
      store.createSchema(b.buildFeatureType());
      try (var w =
          store.getFeatureWriterAppend(
              store.getTypeNames()[0], org.geotools.api.data.Transaction.AUTO_COMMIT)) {
        var f = w.next();
        f.setDefaultGeometry(point("XYZM"));
        f.setAttribute("label", "reference");
        w.write();
      }
    } finally {
      store.dispose();
    }
    try (var source = provider.open(reference, "", "shape")) {
      var row = source.read();
      assertThat(row[0]).isEqualTo("reference");
      assertThat(((Geometry) row[1]).getCoordinate().getZ()).isEqualTo(3);
      assertThat(((Geometry) row[1]).getCoordinate().getM()).isEqualTo(7);
    }
  }

  @Test
  void holesDisjointAndNestedRings() throws Exception {
    Geometry g =
        new org.locationtech.jts.io.WKTReader()
            .read(
                "MULTIPOLYGON (((0 0,10 0,10 10,0 10,0 0),(2 2,2 8,8 8,8 2,2 2)),((3 3,4 3,4 4,3"
                    + " 4,3 3)),((20 0,22 0,22 2,20 2,20 0)))");
    Path f = dir.resolve("rings.shp");
    try (var s =
        provider.create(request(f, meta(), g, ShapefileOptions.defaults(), Diagnostics.NONE))) {
      s.write(new Object[] {"rings", g});
      s.finish();
    }
    try (var s = provider.open(f, "", "shape")) {
      var result = (Geometry) s.read()[1];
      assertThat(result.getArea()).isEqualTo(g.getArea());
      assertThat(result.equalsTopo(g)).isTrue();
    }
  }

  @Test
  void encodingPrecedenceAndWarnings() throws Exception {
    Path f = dir.resolve("encoding.shp");
    var warnings = new ArrayList<String>();
    Diagnostics d = (field, cause, message) -> warnings.add(cause);
    var options =
        new ShapefileOptions(
            "windows-1252", "UTC", List.of(new ShapefileOptions.Field("name", "name", 4, 0)));
    try (var s = provider.create(request(f, meta(), point("XY"), options, d))) {
      s.write(new Object[] {"ä😀abcdef", point("XY")});
      s.finish();
    }
    assertThat(warnings).contains("encoding-replacement", "text-truncation");
    assertThat(Files.readString(dir.resolve("encoding.cpg"))).isEqualTo("windows-1252");
    try (var source = provider.open(f, "", "shape")) {
      assertThat(source.read()[0]).isEqualTo("ä?ab");
    }
    Files.writeString(dir.resolve("encoding.cpg"), "1252");
    try (var source = provider.open(f, "", "shape")) {
      assertThat(source.read()[0]).isEqualTo("ä?ab");
    }
    Files.delete(dir.resolve("encoding.cpg"));
    byte[] dbf = Files.readAllBytes(dir.resolve("encoding.dbf"));
    dbf[29] = 3;
    Files.write(dir.resolve("encoding.dbf"), dbf);
    try (var source = provider.open(f, "", "shape")) {
      assertThat(source.read()[0]).isEqualTo("ä?ab");
    }
    dbf[29] = 0;
    Files.write(dir.resolve("encoding.dbf"), dbf);
    try (var source =
        provider.open(new ReadRequest(f, "", "shape", "", ShapefileOptions.defaults(), d))) {
      source.read();
    }
    assertThat(warnings).contains("encoding-fallback");
    Files.writeString(dir.resolve("encoding.cpg"), "nonsense");
    try (var source = provider.open(new ReadRequest(f, "", "shape", "", options, d))) {
      assertThat(source.read()[0]).isEqualTo("ä?ab");
    }
    assertThatThrownBy(() -> provider.open(f, "", "shape"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void utf8TruncationDoesNotSplitCharacters() throws Exception {
    Path f = dir.resolve("utf.shp");
    var o =
        new ShapefileOptions(
            "UTF-8", "UTC", List.of(new ShapefileOptions.Field("name", "name", 5, 0)));
    try (var s = provider.create(request(f, meta(), point("XY"), o, Diagnostics.NONE))) {
      s.write(new Object[] {"ä😀Z", point("XY")});
      s.finish();
    }
    try (var s = provider.open(f, "", "shape")) {
      assertThat(s.read()[0]).isEqualTo("ä");
    }
  }

  @Test
  void namesPrecisionDatesAndNulls() throws Exception {
    RowMeta rm = meta();
    rm.addValueMeta(new ValueMetaBigNumber("long_field_name"));
    rm.addValueMeta(new ValueMetaInteger("long_field_number"));
    rm.addValueMeta(new ValueMetaTimestamp("when"));
    var o =
        new ShapefileOptions(
            "",
            "Europe/Zurich",
            List.of(new ShapefileOptions.Field("long_field_name", "long_field_name", 33, 3)));
    var warnings = new ArrayList<String>();
    Path f = dir.resolve("values.shp");
    try (var s =
        provider.create(
            request(f, rm, point("XY"), o, (field, cause, message) -> warnings.add(cause)))) {
      s.write(
          new Object[] {
            null,
            point("XY"),
            new BigDecimal("12345678901234567890.1235"),
            Long.MIN_VALUE,
            java.util.Date.from(Instant.parse("2026-09-09T23:30:00Z"))
          });
      s.finish();
    }
    try (var s = provider.open(new ReadRequest(f, "", "shape", "", o, Diagnostics.NONE))) {
      var row = s.read();
      assertThat(row[0]).isEqualTo("");
      assertThat(row[1]).isEqualTo(new BigDecimal("12345678901234567890.124"));
      assertThat(row[2]).isEqualTo(Long.MIN_VALUE);
      assertThat(((java.util.Date) row[3]).toInstant())
          .isEqualTo(
              LocalDate.of(2026, 9, 10).atStartOfDay(ZoneId.of("Europe/Zurich")).toInstant());
      assertThat(s.schema().rowMeta().getFieldNames()).doesNotHaveDuplicates();
    }
    assertThat(warnings).contains("rounding", "field-name", "date-time-loss", "null-attribute");
  }

  @Test
  void abortCollisionsNullsAndMalformedInputs() throws Exception {
    Path f = dir.resolve("abort.shp");
    try (var s =
        provider.create(
            request(f, meta(), point("XY"), ShapefileOptions.defaults(), Diagnostics.NONE))) {
      s.write(new Object[] {"a", point("XY")});
    }
    assertThat(f).doesNotExist();
    Files.writeString(dir.resolve("abort.PRJ"), "existing");
    assertThatThrownBy(
            () ->
                provider.create(
                    request(f, meta(), point("XY"), ShapefileOptions.defaults(), Diagnostics.NONE)))
        .isInstanceOf(IOException.class);
    assertThat(Files.readString(dir.resolve("abort.PRJ"))).isEqualTo("existing");
    Files.delete(dir.resolve("abort.PRJ"));
    var schema = GeometrySchema.explicit("POINT", "XY", null);
    try (var s =
        provider.create(
            new WriteRequest(
                f,
                "layer",
                meta(),
                1,
                null,
                schema,
                ShapefileOptions.defaults(),
                Diagnostics.NONE))) {
      s.write(new Object[] {"null", null});
      s.write(new Object[] {"empty", gf.createPoint()});
      s.finish();
    }
    Files.delete(dir.resolve("abort.shx"));
    try (var s = provider.open(f, "", "shape")) {
      assertThat(s.read()[1]).isNull();
      assertThat(s.read()[1]).isNull();
      assertThat(s.read()).isNull();
    }
    byte[] bytes = Files.readAllBytes(f);
    Files.write(f, Arrays.copyOf(bytes, bytes.length - 1));
    assertThatThrownBy(() -> provider.open(f, "", "shape")).isInstanceOf(IOException.class);
  }

  @Test
  void deletedDbfRowsStayAlignedAndCountMismatchFails() throws Exception {
    Path f = dir.resolve("deleted.shp");
    try (var s =
        provider.create(
            request(f, meta(), point("XY"), ShapefileOptions.defaults(), Diagnostics.NONE))) {
      s.write(new Object[] {"first", point("XY")});
      s.write(new Object[] {"second", gf.createPoint(new CoordinateXY(5, 6))});
      s.finish();
    }
    Path dbf = dir.resolve("deleted.dbf");
    byte[] b = Files.readAllBytes(dbf);
    int h = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getShort(8) & 65535;
    b[h] = 42;
    Files.write(dbf, b);
    try (var s = provider.open(f, "", "shape")) {
      var row = s.read();
      assertThat(row[0]).isEqualTo("second");
      assertThat(((Point) row[1]).getX()).isEqualTo(5);
      assertThat(s.read()).isNull();
    }
    ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 5);
    Files.write(dbf, b);
    assertThatThrownBy(() -> provider.open(f, "", "shape")).isInstanceOf(IOException.class);
  }

  @Test
  void overflowAndDimensionLossFailBeforePublication() throws Exception {
    RowMeta rm = meta();
    rm.addValueMeta(new ValueMetaInteger("n"));
    var o = new ShapefileOptions("", "UTC", List.of(new ShapefileOptions.Field("n", "n", 2, 0)));
    Path f = dir.resolve("overflow.shp");
    try (var s = provider.create(request(f, rm, point("XY"), o, Diagnostics.NONE))) {
      assertThatThrownBy(() -> s.write(new Object[] {"a", point("XY"), 123L}))
          .isInstanceOf(IOException.class);
    }
    assertThat(f).doesNotExist();
    try (var s =
        provider.create(
            request(f, meta(), point("XY"), ShapefileOptions.defaults(), Diagnostics.NONE))) {
      assertThatThrownBy(() -> s.write(new Object[] {"a", point("XYM")}))
          .hasMessageContaining("discard M");
    }
  }

  @Test
  void missingMeasuresAndCurvesAreExplicitlyHandled() throws Exception {
    Path f = dir.resolve("missing.shp");
    var sample = point("XYZM");
    var missing = gf.createPoint(new CoordinateXYZM(1, 2, 3, Double.NaN));
    try (var w =
        provider.create(
            request(f, meta(), sample, ShapefileOptions.defaults(), Diagnostics.NONE))) {
      w.write(new Object[] {"missing", missing});
      w.finish();
    }
    try (var r = provider.open(f, "", "shape")) {
      var g = (Geometry) r.read()[1];
      assertThat(g.getCoordinate().getZ()).isEqualTo(3);
      assertThat(g.getCoordinate().getM()).isNaN();
    }
    var curve =
        new com.atolcd.hop.gis.geometry.curve.CircularString(
            new Coordinate[] {
              new CoordinateXY(0, 0), new CoordinateXY(1, 1), new CoordinateXY(2, 0)
            },
            gf);
    var warnings = new ArrayList<String>();
    f = dir.resolve("curve.shp");
    try (var w =
        provider.create(
            request(
                f,
                meta(),
                curve,
                ShapefileOptions.defaults(),
                (field, cause, message) -> warnings.add(cause)))) {
      w.write(new Object[] {"curve", curve});
      w.finish();
    }
    try (var r = provider.open(f, "", "shape")) {
      var g = (Geometry) r.read()[1];
      assertThat(g.getCoordinates()).hasSize(curve.getCoordinates().length);
      assertThat(g.getNumPoints()).isGreaterThan(3);
    }
    assertThat(warnings).contains("curve-linearization");
  }

  @Test
  void failedWriteCannotBePublishedAndRequiredZCannotBeMissing() throws Exception {
    Path f = dir.resolve("failed.shp");
    try (var w =
        provider.create(
            request(f, meta(), point("XYZ"), ShapefileOptions.defaults(), Diagnostics.NONE))) {
      w.write(new Object[] {"good", point("XYZ")});
      assertThatThrownBy(() -> w.write(new Object[] {"bad", point("XY")}))
          .hasMessageContaining("required Z");
      assertThatThrownBy(w::finish).hasMessageContaining("failed");
    }
    assertThat(f).doesNotExist();
    try (var files = Files.list(dir)) {
      assertThat(files.toList()).isEmpty();
    }
  }

  @Test
  void explicitWktAndCrsOverrideAreKeptInNeutralSchema() throws Exception {
    String wkt =
        "LOCAL_CS[\"Local"
            + " grid\",LOCAL_DATUM[\"Local\",0],UNIT[\"metre\",1],AXIS[\"Easting\",EAST],AXIS[\"Northing\",NORTH]]";
    Path f = dir.resolve("crs.shp");
    var schema =
        GeometrySchema.explicit(
            "POINT", "XY", new CrsDefinitionResolver.Definition(0, "Local grid", "NONE", 0, wkt));
    try (var w =
        provider.create(
            new WriteRequest(
                f,
                "layer",
                meta(),
                1,
                point("XY"),
                schema,
                ShapefileOptions.defaults(),
                Diagnostics.NONE))) {
      w.write(new Object[] {"point", point("XY")});
      w.finish();
    }
    assertThat(Files.readString(dir.resolve("crs.prj"))).isEqualTo(wkt);
    try (var r = provider.open(f, "", "shape")) {
      assertThat(r.schema().geometry().crs().wkt()).isEqualTo(wkt);
    }
    CrsDefinitionResolver resolver =
        srid -> new CrsDefinitionResolver.Definition(srid, "EPSG", "EPSG", srid, "test");
    try (var r =
        new ShapefileProvider(resolver)
            .open(
                new ReadRequest(
                    f, "", "shape", "EPSG:2056", ShapefileOptions.defaults(), Diagnostics.NONE))) {
      assertThat(r.schema().srid()).isEqualTo(2056);
      assertThat(((Geometry) r.read()[1]).getSRID()).isEqualTo(2056);
    }
  }
}
