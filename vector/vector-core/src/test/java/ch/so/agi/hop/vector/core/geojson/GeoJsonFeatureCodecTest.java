package ch.so.agi.hop.vector.core.geojson;

import static org.assertj.core.api.Assertions.*;

import com.atolcd.hop.gis.geometry.codec.*;
import java.math.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class GeoJsonFeatureCodecTest {
  private final GeoJsonFeatureCodec codec = new GeoJsonFeatureCodec();
  private final GeoJsonFeatureOptions options = GeoJsonFeatureOptions.defaults();
  private static final String SIMPLE =
      """
      {"type":"Feature","id":123456789012345678901234567890,"geometry":{"type":"Point","coordinates":[7.4,47.2]},
      "properties":{"precise":0.12345678901234567890123456789,"nested":{"tags":[true,null,"a"]}},"time":{"date":"2026-10-23"},"custom":{"key":"value"},"bbox":[1,2,3,4]}
      """;

  @Test
  void retainsArbitraryPrecisionAndForeignMetadataWithoutStaleBoundingBox() throws Exception {
    var feature = codec.decode(SIMPLE, options, null);
    var result = JsonSupport.parseObject(codec.encode(feature, options));
    assertThat(feature.geometry().getSRID()).isEqualTo(4326);
    assertThat(result.get("id")).isEqualTo(new BigInteger("123456789012345678901234567890"));
    assertThat(GeoJsonFeatureCodec.asMap(result.get("properties")).get("precise"))
        .isEqualTo(new BigDecimal("0.12345678901234567890123456789"));
    assertThat(result).containsKeys("time", "custom").doesNotContainKey("bbox");
  }

  @Test
  void nullPropertiesAndMissingIdsRemainDistinct() throws Exception {
    var feature =
        codec.decode("{\"type\":\"Feature\",\"geometry\":null,\"properties\":null}", options, null);
    assertThat(feature.geometry()).isNull();
    assertThat(feature.propertiesJson()).isEqualTo("null");
    assertThat(feature.idJson()).isNull();
    assertThat(JsonSupport.parseObject(codec.encode(feature, options)))
        .containsEntry("properties", null)
        .doesNotContainKey("id");
  }

  @Test
  void projectsTypedPointersAndOverlaysExplicitValues() throws Exception {
    var mapped =
        new GeoJsonFeatureOptions(
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
                    "/precise", "decimal", GeoJsonFieldMapping.Type.BIG_NUMBER, false),
                new GeoJsonFieldMapping(
                    "/nested/tags/2", "tag", GeoJsonFieldMapping.Type.STRING, false)));
    var feature = codec.decode(SIMPLE, mapped, null);
    var row = GeoJsonFeatureRows.decodeRow(feature, mapped);
    assertThat(row[5]).isEqualTo(new BigDecimal("0.12345678901234567890123456789"));
    assertThat(row[6]).isEqualTo("a");
    row[5] = null;
    row[6] = "changed";
    var output = GeoJsonFeatureRows.encodeRow(GeoJsonFeatureRows.schema(mapped), row, mapped);
    var properties = JsonSupport.parseObject(output.propertiesJson());
    assertThat(properties.get("precise"))
        .isEqualTo(new BigDecimal("0.12345678901234567890123456789"));
    assertThat(GeoJsonFeatureCodec.asMap(properties.get("nested")).get("tags"))
        .isEqualTo(java.util.Arrays.asList(true, null, "changed"));
  }

  @Test
  void writesJsonNullOnlyWhenExplicitlyRequested() throws Exception {
    var mapped =
        new GeoJsonFeatureOptions(
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
                    "/precise", "decimal", GeoJsonFieldMapping.Type.BIG_NUMBER, true)));
    var row = GeoJsonFeatureRows.decodeRow(codec.decode(SIMPLE, mapped, null), mapped);
    row[5] = null;
    var output = GeoJsonFeatureRows.encodeRow(GeoJsonFeatureRows.schema(mapped), row, mapped);
    assertThat(JsonSupport.parseObject(output.propertiesJson())).containsEntry("precise", null);
  }

  @Test
  void rejectsInvalidMappingsAndUnsafeConversions() throws Exception {
    assertThatThrownBy(
            () ->
                new GeoJsonFeatureOptions(
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
                        new GeoJsonFieldMapping("/a", "a", GeoJsonFieldMapping.Type.STRING, false),
                        new GeoJsonFieldMapping(
                            "/a/b", "b", GeoJsonFieldMapping.Type.STRING, false))))
        .hasMessageContaining("Overlapping");
    assertThatThrownBy(
            () -> new GeoJsonFieldMapping("/bad~2", "x", GeoJsonFieldMapping.Type.STRING, false))
        .hasMessageContaining("escape");
    var mapped =
        new GeoJsonFeatureOptions(
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
                    "/precise", "integer", GeoJsonFieldMapping.Type.INTEGER, false)));
    assertThatThrownBy(
            () -> GeoJsonFeatureRows.decodeRow(codec.decode(SIMPLE, mapped, null), mapped))
        .hasMessageContaining("incompatible");
  }

  @Test
  void retainsCollectionAndFeatureContextAndRejectsNestedRootMembers() throws Exception {
    String root =
        "{\"conformsTo\":[\""
            + GeoJsonContext.JSONFG_CORE
            + "\"],\"coordRefSys\":\"http://www.opengis.net/def/crs/EPSG/0/2056\",\"title\":\"Survey\"}";
    String feature =
        "{\"type\":\"Feature\",\"geometry\":null,\"place\":{\"type\":\"Point\",\"coordinates\":[2600000,1200000]},\"properties\":{},\"time\":{\"date\":\"2026-10-23\"}}";
    var decoded = codec.decode(feature, options, root);
    assertThat(decoded.geometry().getSRID()).isEqualTo(2056);
    assertThat(
            GeoJsonFeatureCodec.asMap(
                GeoJsonFeatureCodec.context(decoded.contextJson()).get("root")))
        .containsEntry("title", "Survey");
    var fg = options.withProfile(GeoJsonProfile.JSONFG10);
    var embedded = codec.encodeObject(decoded, fg, false);
    assertThat(embedded).containsKey("place").doesNotContainKeys("coordRefSys", "conformsTo");
    assertThat(codec.rootMetadata(decoded, fg)).containsKeys("coordRefSys", "conformsTo", "title");
    assertThatThrownBy(
            () ->
                codec.decode(
                    feature.replace(
                        "\"properties\":{}", "\"coordRefSys\":\"EPSG:2056\",\"properties\":{}"),
                    options,
                    root))
        .hasMessageContaining("document root");
  }

  @Test
  void rejectsUnknownVersionsAndMalformedFeaturePayloads() {
    for (String json :
        List.of(
            "{\"type\":\"FeatureCollection\",\"features\":[]}",
            "{\"type\":\"Feature\",\"geometry\":null,\"properties\":[]}",
            "{\"type\":\"Feature\",\"geometry\":null,\"properties\":{},\"id\":null}",
            "{\"type\":\"Feature\",\"geometry\":null,\"properties\":{},\"conformsTo\":[\"http://www.opengis.net/spec/json-fg-1/9.0/conf/core\"]}"))
      assertThatThrownBy(() -> codec.decode(json, options, null)).isInstanceOf(Exception.class);
    assertThatThrownBy(
            () ->
                codec.decode(
                    "{\"type\":\"Feature\",\"geometry\":null,\"properties\":{}}",
                    options.withProfile(GeoJsonProfile.JSONFG10),
                    null))
        .hasMessageContaining("core");
  }

  @Test
  void mappingsJsonRoundTripsWithoutLosingNullPolicy() throws Exception {
    var values =
        List.of(
            new GeoJsonFieldMapping(
                "/nested/a~1b", "label", GeoJsonFieldMapping.Type.STRING, true));
    assertThat(GeoJsonFieldMapping.parseJson(GeoJsonFieldMapping.toJson(values))).isEqualTo(values);
  }

  @Test
  void jsonFgFallbackIsExplicitAndDoesNotReprojectNativeCoordinates() throws Exception {
    String json =
        "{\"type\":\"Feature\",\"conformsTo\":[\""
            + GeoJsonContext.JSONFG_CORE
            + "\"],\"coordRefSys\":\"http://www.opengis.net/def/crs/EPSG/0/2056\",\"place\":{\"type\":\"Point\",\"coordinates\":[2600000,1200000]},\"geometry\":{\"type\":\"Point\",\"coordinates\":[7.4,47.2]},\"properties\":{}}";
    var feature = codec.decode(json, options, null);
    assertThat(feature.geometry().getCoordinate().x).isEqualTo(2600000);
    assertThat(feature.fallbackGeometry().getCoordinate().x).isEqualTo(7.4);
    var output =
        JsonSupport.parseObject(
            codec.encode(feature, options.withProfile(GeoJsonProfile.JSONFG10)));
    assertThat(GeoJsonFeatureCodec.asMap(output.get("geometry")).get("coordinates"))
        .isEqualTo(List.of(new BigDecimal("7.4"), new BigDecimal("47.2")));
    assertThat(GeoJsonFeatureCodec.asMap(output.get("place")))
        .doesNotContainKeys("coordRefSys", "conformsTo", "measures");
    assertThatThrownBy(() -> codec.encode(feature, options))
        .hasMessageContaining("fallback geometry");
  }

  @Test
  void measuredGeometryIsDeclaredOnFeatureRatherThanPlace() throws Exception {
    String json =
        "{\"type\":\"Feature\",\"conformsTo\":[\""
            + GeoJsonContext.JSONFG_CORE
            + "\",\""
            + GeoJsonContext.JSONFG_MEASURES
            + "\"],\"coordRefSys\":\"http://www.opengis.net/def/crs/EPSG/0/2056\",\"measures\":{\"enabled\":true,\"unit\":\"m\",\"description\":\"stationing\"},\"place\":{\"type\":\"Point\",\"coordinates\":[2600000,1200000,12.5]},\"geometry\":null,\"properties\":{}}";
    var feature = codec.decode(json, options, null);
    var fg = options.withProfile(GeoJsonProfile.JSONFG10);
    var output = codec.encodeObject(feature, fg, false);
    assertThat(GeoJsonFeatureCodec.asMap(output.get("measures")))
        .containsEntry("unit", "m")
        .containsEntry("description", "stationing");
    assertThat(GeoJsonFeatureCodec.asMap(output.get("place"))).doesNotContainKey("measures");
    assertThat(codec.rootMetadata(feature, fg)).doesNotContainKey("measures");
  }

  @Test
  void jsonFgTimeAndGeometryDimensionMustRemainConsistent() throws Exception {
    String prefix =
        "{\"type\":\"Feature\",\"conformsTo\":[\""
            + GeoJsonContext.JSONFG_CORE
            + "\"],\"geometry\":{\"type\":\"Point\",\"coordinates\":[7,47]},\"properties\":{},";
    for (String tail :
        List.of(
            "\"geometryDimension\":1}",
            "\"geometryDimension\":0.5}",
            "\"time\":{\"timestamp\":\"2026-10-23T10:00:00+02:00\"}}",
            "\"time\":{\"interval\":[\"2026-10-24\",\"2026-10-23\"]}}",
            "\"time\":{\"interval\":[null,\"..\"]}}",
            "\"time\":{\"date\":\"2026-10-23\",\"timestamp\":\"2026-10-24T10:00:00Z\"}}"))
      assertThatThrownBy(() -> codec.decode(prefix + tail, options, null))
          .isInstanceOf(Exception.class);
    assertThat(
            codec
                .decode(
                    prefix
                        + "\"time\":{\"interval\":[\"..\",\"2026-10-23\"],\"note\":\"keep me\"}}",
                    options,
                    null)
                .contextJson())
        .contains("keep me");
  }

  @Test
  void emptyCollectionStillRequiresValidJsonFgDeclarations() throws Exception {
    assertThatThrownBy(
            () ->
                codec.validateCollection(
                    Map.of(
                        "type",
                        "FeatureCollection",
                        "conformsTo",
                        List.of("http://www.opengis.net/spec/json-fg-1/9.0/conf/core")),
                    options))
        .hasMessageContaining("version");
    assertThatThrownBy(
            () ->
                codec.validateCollection(
                    Map.of("type", "FeatureCollection"),
                    options.withProfile(GeoJsonProfile.JSONFG10)))
        .hasMessageContaining("core");
  }
}
