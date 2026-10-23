package ch.so.agi.hop.vector.core.geojson;

import static org.assertj.core.api.Assertions.*;

import com.atolcd.hop.gis.geometry.codec.*;
import java.util.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

class GeoJsonFeatureAdversarialTest {
  private final GeoJsonFeatureCodec codec = new GeoJsonFeatureCodec();
  private final GeoJsonFeatureOptions defaults = GeoJsonFeatureOptions.defaults();
  private final GeoJsonFeatureOptions fg = defaults.withProfile(GeoJsonProfile.JSONFG10);
  private static final String CORE = "\"conformsTo\":[\"" + GeoJsonContext.JSONFG_CORE + "\"]";

  @Test
  void standaloneNullLegacyFeatureRetainsItsDeclaredCrs() throws Exception {
    String json =
        "{\"type\":\"Feature\",\"geometry\":null,\"properties\":{},\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:2056\"}}}";
    var feature = codec.decode(json, defaults, null);
    assertThat(
            JsonSupport.parseObject(
                    codec.encode(feature, defaults.withProfile(GeoJsonProfile.LEGACY2008)))
                .get("crs"))
        .isEqualTo(JsonSupport.parseObject(json).get("crs"));
  }

  @Test
  void plainCrs84PlaceAndIdenticalPlaceAndGeometryAreRejected() {
    String json =
        "{\"type\":\"Feature\","
            + CORE
            + ",\"coordRefSys\":\"OGC:CRS84\",\"place\":{\"type\":\"Point\",\"coordinates\":[7,47]},\"geometry\":null,\"properties\":{}}";
    assertThatThrownBy(() -> codec.decode(json, defaults, null))
        .hasMessageContaining("must use geometry");
    String identical =
        json.replace("OGC:CRS84", "EPSG:4326")
            .replace(
                "\"geometry\":null",
                "\"geometry\":{\"type\":\"Point\",\"coordinates\":[7.0,47.0]}");
    assertThatThrownBy(() -> codec.decode(identical, defaults, null))
        .hasMessageContaining("identical");
  }

  @Test
  void aliasesAndThreeDimensionalWgs84UseRfcGeometryMember() throws Exception {
    for (String coordinates : List.of("[7,47]", "[7,47,123.5]")) {
      var feature =
          codec.decode(
              "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":"
                  + coordinates
                  + "},\"properties\":{}}",
              defaults,
              null);
      var output = codec.encodeObject(feature, fg, true);
      assertThat(output).containsKey("geometry").doesNotContainKey("place");
      assertThat(GeoJsonFeatureCodec.asMap(output.get("geometry")))
          .doesNotContainKeys("coordRefSys", "conformsTo", "measures");
    }
    Geometry point = new GeometryCodec(null).readText("SRID=4326;POINT (7 47)");
    for (Object descriptor :
        List.of(
            "OGC:CRS84",
            "urn:ogc:def:crs:OGC::CRS84",
            Map.of("type", "Reference", "href", "http://www.opengis.net/def/crs/OGC/0/CRS84"))) {
      String context =
          JsonSupport.stringify(
              Map.of(
                  "version",
                  1,
                  "geometry",
                  new GeoJsonContext(GeoJsonProfile.JSONFG10, descriptor, Map.of("enabled", false))
                      .toMap()));
      var output =
          codec.encodeObject(new GeoJsonFeature(point, null, "{}", null, context), fg, true);
      assertThat(output).containsKey("geometry").doesNotContainKey("place");
      assertThat(GeoJsonFeatureCodec.asMap(output.get("measures"))).containsEntry("enabled", false);
    }
  }

  @Test
  void nullCrsAndReservedContextMembersAreRejected() throws Exception {
    for (String declaration : List.of("\"crs\":null", CORE + ",\"coordRefSys\":null"))
      assertThatThrownBy(
              () ->
                  codec.decode(
                      "{\"type\":\"Feature\",\"geometry\":null,\"properties\":{},"
                          + declaration
                          + "}",
                      defaults,
                      null))
          .hasMessageContaining("null");
    for (String member : List.of("type", "id", "geometry", "properties")) {
      String context =
          JsonSupport.stringify(Map.of("version", 1, "feature", Map.of(member, "bad")));
      assertThatThrownBy(
              () -> codec.encode(new GeoJsonFeature(null, null, "{}", null, context), defaults))
          .hasMessageContaining("Reserved");
    }
  }

  @Test
  void timeUsesExactFractionsAndChecksBothIntervalGranularities() throws Exception {
    String prefix =
        "{\"type\":\"Feature\"," + CORE + ",\"geometry\":null,\"properties\":{},\"time\":";
    for (String time :
        List.of(
            "{\"timestamp\":\"2026-10-23T10:00:00Z\",\"interval\":[\"2026-10-24\",\"2026-10-25\"]}",
            "{\"date\":\"2026-10-23\",\"interval\":[\"2026-10-24T00:00:00Z\",\"2026-10-25T00:00:00Z\"]}",
            "{}"))
      assertThatThrownBy(() -> codec.decode(prefix + time + "}", defaults, null))
          .isInstanceOf(IllegalArgumentException.class);
    String instant = "2026-10-23T10:00:00.12345678901234567890Z";
    var feature =
        codec.decode(
            prefix
                + "{\"timestamp\":\""
                + instant
                + "\",\"interval\":[\"2026-10-23T10:00:00.12345678901234567889Z\",\"2026-10-23T10:00:00.12345678901234567891Z\"]}}",
            defaults,
            null);
    assertThat(codec.encode(feature, fg)).contains(instant);
    var foreignTime =
        codec.decode(prefix + "{\"note\":\"calendar-specific metadata\"}}", defaults, null);
    assertThat(
            GeoJsonFeatureCodec.asMap(
                JsonSupport.parseObject(codec.encode(foreignTime, fg)).get("time")))
        .containsEntry("note", "calendar-specific metadata");
  }

  @Test
  void foreignMetadataConflictsFailAndPerFeatureTypesStayPerFeature() throws Exception {
    String source =
        "{\"type\":\"Feature\",\"geometry\":null,\"properties\":{},\"title\":\"feature\"}";
    var feature = codec.decode(source, defaults, "{\"title\":\"collection\"}");
    assertThatThrownBy(() -> codec.encode(feature, defaults)).hasMessageContaining("conflicts");
    String json =
        "{\"type\":\"Feature\",\"conformsTo\":[\""
            + GeoJsonContext.JSONFG_CORE
            + "\",\""
            + GeoJsonContext.JSONFG_TYPES_SCHEMAS
            + "\"],\"geometry\":null,\"properties\":{},\"featureType\":\"Parcel\",\"featureSchema\":{\"Parcel\":\"https://example.org/parcel.json\"}}";
    var typed = codec.decode(json, defaults, null);
    assertThat(codec.rootMetadata(typed, fg)).doesNotContainKeys("featureType", "featureSchema");
    assertThat(codec.encodeObject(typed, fg, false)).containsEntry("featureType", "Parcel");
    for (String invalid :
        List.of(
            json.replace("\"Parcel\",\"featureSchema\"", "7,\"featureSchema\""),
            json.replace("https://example.org/parcel.json", "relative.json"),
            json.replace(
                "\"featureType\":\"Parcel\",\"featureSchema\":{\"Parcel\":\"https://example.org/parcel.json\"}",
                "\"extra\":true")))
      assertThatThrownBy(() -> codec.decode(invalid, defaults, null))
          .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void canonicalArrayPointersAreRequiredAndMissingIndicesProjectToNull() throws Exception {
    String json = "{\"type\":\"Feature\",\"geometry\":null,\"properties\":{\"a\":[1,2]}}";
    for (String index : List.of("01", "+1", "-0")) {
      var mapped = withMappings("/a/" + index);
      assertThatThrownBy(
              () -> GeoJsonFeatureRows.decodeRow(codec.decode(json, mapped, null), mapped))
          .hasMessageContaining("array index");
    }
    var missing = withMappings("/a/2");
    assertThat(GeoJsonFeatureRows.decodeRow(codec.decode(json, missing, null), missing)[5])
        .isNull();
  }

  @Test
  void lazyStringStorageIsDecodedAndWrongMappedTypesAreConfigurationErrors() throws Exception {
    var mapped = withMappings("/number");
    IRowMeta schema = GeoJsonFeatureRows.schema(mapped);
    var string = schema.getValueMeta(2);
    string.setStorageType(IValueMeta.STORAGE_TYPE_BINARY_STRING);
    string.setStorageMetadata(new ValueMetaString("properties_json"));
    Object[] row = {
      null, null, "{\"number\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8), null, null, 2L
    };
    GeoJsonFeatureRows.validateEncodingSchema(schema, mapped);
    assertThat(GeoJsonFeatureRows.encodeRow(schema, row, mapped).propertiesJson())
        .isEqualTo("{\"number\":2}");
    schema.setValueMeta(5, new ValueMetaString("projected"));
    assertThatThrownBy(() -> GeoJsonFeatureRows.validateEncodingSchema(schema, mapped))
        .hasMessageContaining("type does not match");
  }

  @Test
  void optionalFallbackCanBeExplicitlyDisabledAndForeignGeometryValuesAreFatal() throws Exception {
    var fields = new RowMeta();
    fields.addValueMeta(new com.atolcd.hop.core.row.value.ValueMetaGeometry("geometry"));
    fields.addValueMeta(new com.atolcd.hop.core.row.value.ValueMetaGeometry("geometry_wgs84"));
    Geometry point = new GeometryCodec(null).readText("SRID=4326;POINT (7 47)");
    var disabled =
        new GeoJsonFeatureOptions(
            GeoJsonProfile.RFC7946,
            "geometry",
            "",
            null,
            null,
            null,
            false,
            false,
            false,
            List.of());
    assertThat(
            codec.encode(
                GeoJsonFeatureRows.encodeRow(fields, new Object[] {point, point}, disabled),
                disabled))
        .contains("Feature");
    assertThatThrownBy(
            () ->
                codec.encode(
                    GeoJsonFeatureRows.encodeRow(fields, new Object[] {point, point}, defaults),
                    defaults))
        .hasMessageContaining("fallback");
    assertThatThrownBy(
            () -> GeoJsonFeatureRows.encodeRow(fields, new Object[] {new Object(), null}, defaults))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("classloader");
  }

  @Test
  void jsonFgDefaultContextIsBudgetedBeforeSridTraversal() {
    GeometryFactory factory = new GeometryFactory();
    Geometry geometry = factory.createPoint(new CoordinateXY(7, 47));
    geometry.setSRID(4326);
    for (int i = 0; i < 10000; i++)
      geometry = factory.createGeometryCollection(new Geometry[] {geometry});
    Geometry deep = geometry;
    assertThatThrownBy(() -> codec.encode(new GeoJsonFeature(deep, null, "{}", null, null), fg))
        .isInstanceOf(GeometryCodecException.class)
        .hasMessageContaining("GC_LIMIT_EXCEEDED");
  }

  @Test
  void jsonFgRfcGeometrySlotRejectsMixedCoordinateDimensionsOnReadAndWrite() throws Exception {
    String mixed =
        "{\"type\":\"GeometryCollection\",\"geometries\":[{\"type\":\"Point\",\"coordinates\":[7,47]},{\"type\":\"Point\",\"coordinates\":[7,47,100]}]}";
    String json =
        "{\"type\":\"Feature\"," + CORE + ",\"geometry\":" + mixed + ",\"properties\":{}}";
    assertThatThrownBy(() -> codec.decode(json, defaults, null))
        .hasMessageContaining("coordinate dimension");
    var nativeGeometry = new GeometryCodec(null).readText("SRID=2056;POINT (2600000 1200000)");
    var fallback =
        new GeometryCodec(null).readGeoJson(mixed, GeoJsonReadOptions.defaults()).geometry();
    assertThatThrownBy(
            () -> codec.encode(new GeoJsonFeature(nativeGeometry, fallback, "{}", null, null), fg))
        .hasMessageContaining("coordinate dimension");
  }

  @Test
  void jsonFgRfcGeometrySlotRejectsInvalidSimpleFeaturesOnReadAndWrite() throws Exception {
    String bowtie = "{\"type\":\"Polygon\",\"coordinates\":[[[7,47],[8,48],[8,47],[7,48],[7,47]]]}";
    String json =
        "{\"type\":\"Feature\"," + CORE + ",\"geometry\":" + bowtie + ",\"properties\":{}}";
    assertThatThrownBy(() -> codec.decode(json, defaults, null))
        .hasMessageContaining("valid Simple Feature");
    var nativeGeometry = new GeometryCodec(null).readText("SRID=2056;POINT (2600000 1200000)");
    var fallback =
        new GeometryCodec(null).readGeoJson(bowtie, GeoJsonReadOptions.defaults()).geometry();
    assertThatThrownBy(
            () -> codec.encode(new GeoJsonFeature(nativeGeometry, fallback, "{}", null, null), fg))
        .hasMessageContaining("valid Simple Feature");
  }

  private GeoJsonFeatureOptions withMappings(String pointer) {
    return new GeoJsonFeatureOptions(
        GeoJsonProfile.AUTO,
        null,
        null,
        null,
        null,
        null,
        false,
        false,
        false,
        List.of(
            new GeoJsonFieldMapping(
                pointer, "projected", GeoJsonFieldMapping.Type.INTEGER, false)));
  }
}
