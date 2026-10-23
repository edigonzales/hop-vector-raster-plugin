package ch.so.agi.hop.vector.transforms;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.apache.hop.core.*;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.engines.local.LocalPipelineEngine;
import org.apache.hop.pipeline.transform.*;
import org.junit.jupiter.api.*;
import org.locationtech.jts.geom.Geometry;

class GeoJsonFeatureTransformTest {
  static final String FEATURE =
      "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[7,47]},\"properties\":{\"name\":\"test\"}}";

  @BeforeAll
  static void initialize() throws Exception {
    HopClientEnvironment.init();
  }

  @Test
  void decoderPreservesOriginalColumnsAndNullInput() throws Exception {
    var h = new Harness("FAIL", false);
    h.add(FEATURE);
    h.add(null);
    h.run();
    var rows = h.rows(h.output);
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0)[0]).isEqualTo(FEATURE);
    assertThat(rows.get(0)[1]).isEqualTo("original");
    assertThat(((Geometry) rows.get(0)[2]).getSRID()).isEqualTo(4326);
    assertThat(rows.get(1)).containsExactly(null, "original", null, null, null, null, null);
  }

  @Test
  void failStopsAtBadFeatureWithoutErrorHop() throws Exception {
    var h = new Harness("FAIL", false);
    h.add(FEATURE);
    h.add("broken");
    assertThatThrownBy(h::run).isInstanceOf(HopException.class).hasMessageContaining("data error");
    assertThat(h.rows(h.output)).hasSize(1);
  }

  @Test
  void returnNullClearsOnlyGeneratedValuesAndContinues() throws Exception {
    var h = new Harness("RETURN_NULL", false);
    h.add("broken");
    h.add(FEATURE);
    h.run();
    var rows = h.rows(h.output);
    assertThat(rows).hasSize(2);
    assertThat(rows.getFirst()).containsExactly("broken", "original", null, null, null, null, null);
  }

  @Test
  void routeModeUsesOriginalRowAndActualHopErrorMetadata() throws Exception {
    var h = new Harness("ROUTE_TO_ERROR", true);
    h.add("broken");
    h.add(FEATURE);
    h.run();
    assertThat(h.rows(h.output)).hasSize(1);
    var errors = h.rows(h.error);
    assertThat(errors).hasSize(1);
    assertThat(errors.getFirst()[0]).isEqualTo("broken");
    assertThat(errors.getFirst()[1]).isEqualTo("original");
    var metadata = h.transform.getErrorRowMeta();
    assertThat(errors.getFirst()[metadata.indexOfValue("error_fields")]).isEqualTo("feature_json");
    assertThat(errors.getFirst()[metadata.indexOfValue("error_codes")])
        .isEqualTo("GEOJSON_DATA_ERROR");
  }

  @Test
  void missingErrorHopAndFieldCollisionsAreConfigurationErrorsEvenForReturnNull() throws Exception {
    var missing = new Harness("ROUTE_TO_ERROR", false);
    missing.add(FEATURE);
    assertThat(missing.transform.init()).isFalse();
    var collision = new Harness("RETURN_NULL", false);
    collision.meta.setGeometryField("attribute");
    collision.add(FEATURE);
    assertThatThrownBy(collision::run).hasMessageContaining("configuration");
  }

  @Test
  void encoderAppendsFeatureJsonAndPreservesItsNativeInput() throws Exception {
    var codec = new com.atolcd.hop.gis.geometry.codec.GeometryCodec(null);
    Geometry geometry = codec.readText("SRID=4326;POINT (7 47)");
    var fields = new RowMeta();
    fields.addValueMeta(new com.atolcd.hop.core.row.value.ValueMetaGeometry("geometry"));
    fields.addValueMeta(new ValueMetaString("properties_json"));
    var meta = new GeoJsonFeatureEncoderMeta();
    var outputs =
        encode(
            meta,
            fields,
            java.util.Arrays.asList(
                new Object[] {geometry, "{\"amount\":0.1234567890123456789}"},
                new Object[] {null, "null"}));
    assertThat(outputs).hasSize(2);
    assertThat(outputs.getFirst()[0]).isSameAs(geometry);
    var feature =
        com.atolcd.hop.gis.geometry.codec.JsonSupport.parseObject((String) outputs.getFirst()[2]);
    assertThat(
            ch.so.agi.hop.vector.core.geojson.GeoJsonFeatureCodec.asMap(feature.get("properties"))
                .get("amount"))
        .isEqualTo(new java.math.BigDecimal("0.1234567890123456789"));
    assertThat(
            com.atolcd.hop.gis.geometry.codec.JsonSupport.parseObject((String) outputs.get(1)[2]))
        .containsEntry("geometry", null);
  }

  @Test
  void encoderReturnNullPreservesOriginalRowAndConfigurationErrorsStillFail() throws Exception {
    Geometry nativeGeometry =
        new com.atolcd.hop.gis.geometry.codec.GeometryCodec(null)
            .readText("SRID=2056;POINT (2600000 1200000)");
    var fields = new RowMeta();
    fields.addValueMeta(new com.atolcd.hop.core.row.value.ValueMetaGeometry("geometry"));
    var meta = new GeoJsonFeatureEncoderMeta();
    meta.setOnDataError("RETURN_NULL");
    var output =
        encode(meta, fields, java.util.Collections.singletonList(new Object[] {nativeGeometry}));
    assertThat(output.getFirst()).containsExactly(nativeGeometry, null);
    fields.addValueMeta(new ValueMetaInteger("properties_json"));
    assertThatThrownBy(
            () ->
                encode(
                    meta,
                    fields,
                    java.util.Collections.singletonList(new Object[] {nativeGeometry, 1L})))
        .hasMessageContaining("configuration");
  }

  private static List<Object[]> encode(
      GeoJsonFeatureEncoderMeta meta, IRowMeta fields, List<Object[]> rows) throws Exception {
    var pipelineMeta = new PipelineMeta();
    var transformMeta = new TransformMeta("Encode", meta);
    pipelineMeta.addTransform(transformMeta);
    var iterator = rows.iterator();
    var transform =
        new GeoJsonFeatureEncoder(
            transformMeta,
            meta,
            new GeoJsonFeatureEncoderData(),
            0,
            pipelineMeta,
            new LocalPipelineEngine(pipelineMeta)) {
          @Override
          public Object[] getRow() {
            return iterator.hasNext() ? iterator.next() : null;
          }

          @Override
          public IRowMeta getInputRowMeta() {
            return fields;
          }
        };
    var output = new ArrayList<Object[]>();
    transform.addRowListener(
        new RowAdapter() {
          @Override
          public void rowWrittenEvent(IRowMeta rowMeta, Object[] row) {
            output.add(row);
          }
        });
    assertThat(transform.init()).isTrue();
    while (transform.processRow()) {}
    return output;
  }

  static final class Harness {
    final GeoJsonFeatureDecoderMeta meta = new GeoJsonFeatureDecoderMeta();
    final IRowMeta inputMeta = new RowMeta();
    final BlockingRowSet input = new BlockingRowSet(30),
        output = new BlockingRowSet(30),
        error = new BlockingRowSet(30);
    final GeoJsonFeatureDecoder transform;

    Harness(String mode, boolean errorHop) {
      meta.setOnDataError(mode);
      inputMeta.addValueMeta(new ValueMetaString("feature_json"));
      inputMeta.addValueMeta(new ValueMetaString("attribute"));
      var tm = new TransformMeta("Decode", meta);
      var pipelineMeta = new PipelineMeta();
      pipelineMeta.addTransform(tm);
      var target = new TransformMeta("Output", new GeoJsonFeatureDecoderMeta());
      pipelineMeta.addTransform(target);
      pipelineMeta.addPipelineHop(new PipelineHopMeta(tm, target));
      if (errorHop) {
        var errorTarget = new TransformMeta("Error", new GeoJsonFeatureDecoderMeta());
        pipelineMeta.addTransform(errorTarget);
        var em =
            new TransformErrorMeta(
                tm, errorTarget, "errors", "error_desc", "error_fields", "error_codes");
        em.setEnabled(true);
        tm.setTransformErrorMeta(em);
        pipelineMeta.addPipelineHop(new PipelineHopMeta(tm, errorTarget));
      }
      output.setThreadNameFromToCopy("Decode", 0, "Output", 0);
      error.setThreadNameFromToCopy("Decode", 0, "Error", 0);
      var pipeline = new LocalPipelineEngine(pipelineMeta);
      pipeline.rowsets = new ArrayList<>(errorHop ? List.of(output, error) : List.of(output));
      pipeline.setRunning(true);
      transform =
          new GeoJsonFeatureDecoder(
              tm, meta, new GeoJsonFeatureDecoderData(), 0, pipelineMeta, pipeline);
      transform.setInputRowMeta(inputMeta);
      transform.setInputRowSets(new ArrayList<>(List.of(input)));
      transform.setOutputRowSets(
          new ArrayList<>(errorHop ? List.of(output, error) : List.of(output)));
    }

    void add(String feature) {
      input.putRow(inputMeta, new Object[] {feature, "original"});
    }

    void run() throws Exception {
      input.setDone();
      assertThat(transform.init()).isTrue();
      transform.identifyErrorOutput();
      while (transform.processRow()) {}
    }

    List<Object[]> rows(BlockingRowSet rowSet) {
      var rows = new ArrayList<Object[]>();
      Object[] row;
      while ((row = rowSet.getRowImmediate()) != null) rows.add(row);
      return rows;
    }
  }
}
