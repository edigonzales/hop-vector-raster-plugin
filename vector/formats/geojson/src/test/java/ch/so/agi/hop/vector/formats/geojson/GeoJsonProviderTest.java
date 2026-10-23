package ch.so.agi.hop.vector.formats.geojson;

import static org.junit.jupiter.api.Assertions.*;

import ch.so.agi.hop.vector.core.*;
import ch.so.agi.hop.vector.core.geojson.*;
import com.atolcd.hop.gis.geometry.codec.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;

class GeoJsonProviderTest {
  @TempDir Path directory;
  private final GeoJsonProvider provider = new GeoJsonProvider();

  private GeoJsonFeatureOptions fields(GeoJsonProfile profile) {
    return GeoJsonFeatureOptions.defaults().withProfile(profile);
  }

  private ReadRequest read(Path file) {
    return new ReadRequest(
        file, "", "", "", new GeoJsonOptions(fields(GeoJsonProfile.AUTO), false), Diagnostics.NONE);
  }

  private WriteRequest write(Path file, GeoJsonProfile profile, boolean overwrite) {
    var options = fields(profile);
    return new WriteRequest(
        file,
        "features",
        GeoJsonFeatureRows.schema(options),
        0,
        null,
        null,
        new GeoJsonOptions(options, overwrite),
        Diagnostics.NONE);
  }

  private Object[] row(Geometry geometry, String properties) {
    return new Object[] {geometry, null, properties, "123456789012345678901234567890", null};
  }

  private Geometry point(int srid) {
    Geometry point = new GeometryFactory().createPoint(new CoordinateXY(7.5, 47.2));
    point.setSRID(srid);
    return point;
  }

  @Test
  void schemaIsAvailableWithoutOpeningOrScanningFile() throws Exception {
    Path missing = directory.resolve("missing.geojson");
    assertEquals(VectorFormat.GEOJSON, VectorFormat.resolve("AUTO", missing));
    assertTrue(VectorFormat.GEOJSON.readable());
    assertFalse(VectorFormat.GEOJSON.requiresGeometrySample());
    try (var source = provider.open(read(missing))) {
      assertEquals(5, source.schema().rowMeta().size());
      assertEquals("geometry", source.schema().geometryColumn());
      assertNull(source.schema().geometry());
      assertThrows(Exception.class, source::read);
    }
  }

  @Test
  void nullFirstAndHeterogeneousRowsRoundTripWithExactPropertiesNumbers() throws Exception {
    Path file = directory.resolve("mixed.geojson");
    String properties =
        "{\"precise\":123456789.1234567890123456789,\"nested\":{\"a\":[true,null,\"x\"]}}";
    try (var sink = provider.create(write(file, GeoJsonProfile.RFC7946, false))) {
      sink.write(row(null, "null"));
      sink.write(row(point(4326), properties));
      var line =
          new GeometryFactory()
              .createLineString(
                  new Coordinate[] {new CoordinateXY(7, 47), new CoordinateXY(8, 48)});
      line.setSRID(4326);
      sink.write(row(line, "{}"));
      assertFalse(Files.exists(file));
      sink.finish();
    }
    try (var source = provider.open(read(file))) {
      assertNull(source.read()[0]);
      Object[] point = source.read();
      assertEquals("Point", ((Geometry) point[0]).getGeometryType());
      assertEquals(JsonSupport.parse(properties), JsonSupport.parse((String) point[2]));
      assertEquals("123456789012345678901234567890", point[3]);
      assertEquals("LineString", ((Geometry) source.read()[0]).getGeometryType());
      assertNull(source.read());
    }
  }

  @Test
  void directFeatureOptionsRetainTheExplicitLegacyProfile() throws Exception {
    var options = fields(GeoJsonProfile.LEGACY2008);
    Path file = directory.resolve("direct-options.geojson");
    var request =
        new WriteRequest(
            file, "", GeoJsonFeatureRows.schema(options), 0, null, null, options, Diagnostics.NONE);
    try (var sink = provider.create(request)) {
      sink.write(row(point(2056), "{}"));
      sink.finish();
    }
    try (var source = provider.open(read(file))) {
      assertEquals(2056, ((Geometry) source.read()[0]).getSRID());
    }
  }

  @Test
  void lateTypeAndCrsApplyToEveryFeatureWithoutCollectingRows() throws Exception {
    Path file = directory.resolve("legacy.json");
    Files.writeString(
        file,
        "{\"features\":[{\"geometry\":{\"coordinates\":[2600000,1200000],\"type\":\"Point\"},\"properties\":{},\"type\":\"Feature\"}],\"type\":\"FeatureCollection\",\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:2056\"}}}");
    try (var source = provider.open(read(file))) {
      Geometry point = (Geometry) source.read()[0];
      assertEquals(2056, point.getSRID());
      assertEquals(2600000, point.getCoordinate().x);
      assertNull(source.read());
    }
  }

  @Test
  void readsStandaloneFeatureAndPreservesForeignMetadata() throws Exception {
    Path file = directory.resolve("feature.geojson");
    Files.writeString(
        file,
        "{\"properties\":null,\"type\":\"Feature\",\"geometry\":null,\"source\":{\"name\":\"local\"},\"bbox\":[0,0,1,1]}");
    try (var source = provider.open(read(file))) {
      Object[] row = source.read();
      assertNull(row[0]);
      assertEquals("null", row[2]);
      assertTrue(((String) row[4]).contains("local"));
      assertFalse(((String) row[4]).contains("bbox"));
      assertNull(source.read());
    }
  }

  @Test
  void emptyCollectionsAreValidIncludingJsonFg() throws Exception {
    for (var profile : List.of(GeoJsonProfile.RFC7946, GeoJsonProfile.JSONFG10)) {
      Path file = directory.resolve(profile.name() + ".geojson");
      try (var sink = provider.create(write(file, profile, false))) {
        sink.finish();
      }
      var object = JsonSupport.parseObject(Files.readString(file));
      assertEquals(List.of(), object.get("features"));
      if (profile == GeoJsonProfile.JSONFG10) assertNotNull(object.get("conformsTo"));
      try (var source = provider.open(read(file))) {
        assertNull(source.read());
      }
    }
  }

  @Test
  void failedOrCancelledExportNeverReplacesExistingOutput() throws Exception {
    Path file = directory.resolve("existing.geojson");
    Files.writeString(file, "original");
    try (var sink = provider.create(write(file, GeoJsonProfile.RFC7946, true))) {
      sink.write(row(point(4326), "{}"));
      assertThrows(Exception.class, () -> sink.write(row(point(2056), "{}")));
      assertThrows(java.io.IOException.class, sink::finish);
    }
    assertEquals("original", Files.readString(file));
    try (var paths = Files.list(directory)) {
      assertEquals(1, paths.count());
    }
    var base = write(file, GeoJsonProfile.RFC7946, true);
    var cancelled =
        new WriteRequest(
            base.file(),
            base.layer(),
            base.rowMeta(),
            0,
            null,
            null,
            base.options(),
            Diagnostics.NONE,
            () -> true);
    try (var sink = provider.create(cancelled)) {
      assertThrows(java.io.InterruptedIOException.class, sink::finish);
    }
    assertEquals("original", Files.readString(file));
  }

  @Test
  void rejectsMixedJsonFgCrsAndDoesNotPublishPartialFile() throws Exception {
    Path file = directory.resolve("mixed-crs.geojson");
    try (var sink = provider.create(write(file, GeoJsonProfile.JSONFG10, false))) {
      sink.write(row(point(2056), "{}"));
      assertThrows(Exception.class, () -> sink.write(row(point(3857), "{}")));
    }
    assertFalse(Files.exists(file));
  }

  @Test
  void malformedCollectionsAndBareGeometryFailClearly() throws Exception {
    int count = 0;
    for (String json :
        List.of(
            "{\"type\":\"FeatureCollection\"}",
            "{\"type\":\"FeatureCollection\",\"features\":[null]}",
            "{\"type\":\"Point\",\"coordinates\":[1,2]}",
            "{\"type\":\"FeatureCollection\",\"features\":[]}garbage")) {
      Path file = directory.resolve("bad" + (count++) + ".json");
      Files.writeString(file, json);
      try (var source = provider.open(read(file))) {
        assertThrows(Exception.class, source::read);
      }
    }
  }

  @Test
  void largeCollectionStreamsInInputOrder() throws Exception {
    Path file = directory.resolve("large.geojson");
    try (var out = Files.newBufferedWriter(file)) {
      out.write("{\"features\":[");
      for (int i = 0; i < 20000; i++) {
        if (i > 0) out.write(',');
        out.write("{\"type\":\"Feature\",\"id\":" + i + ",\"properties\":{},\"geometry\":null}");
      }
      out.write("],\"type\":\"FeatureCollection\"}");
    }
    try (var source = provider.open(read(file))) {
      for (int i = 0; i < 20000; i++) assertEquals(Integer.toString(i), source.read()[3]);
      assertNull(source.read());
    }
  }

  @Test
  void samplingNeverTraversesDeepNativeCollectionsBeforeCodecLimits() throws Exception {
    Geometry deep = point(4326);
    for (int i = 0; i < 10000; i++)
      deep = new GeometryFactory().createGeometryCollection(new Geometry[] {deep});
    var base = write(directory.resolve("deep.geojson"), GeoJsonProfile.RFC7946, false);
    var request =
        new WriteRequest(
            base.file(),
            base.layer(),
            base.rowMeta(),
            0,
            deep,
            null,
            base.options(),
            Diagnostics.NONE);
    assertNull(request.geometry());
    Object[] value = row(deep, "{}");
    try (var sink = provider.create(request)) {
      assertThrows(GeometryCodecException.class, () -> sink.write(value));
    }
    assertFalse(Files.exists(base.file()));
  }

  @Test
  void invalidFixedSchemaFailsBeforeCreatingAStageEvenWithNoRows() throws Exception {
    var base = write(directory.resolve("invalid.geojson"), GeoJsonProfile.RFC7946, false);
    var invalid = base.rowMeta().clone();
    invalid.setValueMeta(2, new org.apache.hop.core.row.value.ValueMetaInteger("properties_json"));
    var request =
        new WriteRequest(
            base.file(), base.layer(), invalid, 0, null, null, base.options(), Diagnostics.NONE);
    assertThrows(IllegalArgumentException.class, () -> provider.create(request));
    try (var files = Files.list(directory)) {
      assertEquals(0, files.count());
    }
  }

  @Test
  void validatesJsonFgHeadersEvenWhenNoFeaturesArePresent() throws Exception {
    int index = 0;
    for (String metadata :
        List.of(
            "\"conformsTo\":[\"http://www.opengis.net/spec/json-fg-1/1.1/conf/core\"]",
            "\"coordRefSys\":\"EPSG:2056\"",
            "\"conformsTo\":[\"http://www.opengis.net/spec/json-fg-1/1.0/conf/core\"],\"coordRefSys\":null")) {
      Path file = directory.resolve("empty-bad-" + index++ + ".geojson");
      Files.writeString(file, "{\"type\":\"FeatureCollection\",\"features\":[]," + metadata + "}");
      try (var source = provider.open(read(file))) {
        assertThrows(IllegalArgumentException.class, source::read);
      }
    }
  }
}
