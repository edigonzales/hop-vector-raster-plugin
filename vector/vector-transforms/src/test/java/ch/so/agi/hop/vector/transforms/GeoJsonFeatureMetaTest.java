package ch.so.agi.hop.vector.transforms;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.apache.hop.core.*;
import org.apache.hop.core.injection.bean.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.*;

class GeoJsonFeatureMetaTest {
  @BeforeAll
  static void initialize() throws Exception {
    HopClientEnvironment.init();
  }

  @Test
  void everySettingRoundTripsIncludingUnresolvedExpressions() throws Exception {
    var original = new GeoJsonFeatureDecoderMeta();
    original.setInputField("${INPUT}");
    original.setProfile("${PROFILE}");
    original.setGeometryField("${GEOM}");
    original.setMappingsJson("${MAPPINGS}");
    original.setDropZ(true);
    original.setDropM(true);
    original.setEmptyToNull(true);
    original.setOnDataError("RETURN_NULL");
    var restored = new GeoJsonFeatureDecoderMeta();
    restored.loadXml(
        XmlHandler.loadXmlString("<transform>" + original.getXml() + "</transform>")
            .getDocumentElement(),
        null);
    assertThat(restored.getXml()).isEqualTo(original.getXml());
    assertThat(restored.getInputField()).isEqualTo("${INPUT}");
    assertThat(restored.isDropM()).isTrue();
    var variables = new Variables();
    variables.setVariable("PROFILE", "LEGACY2008");
    variables.setVariable("GEOM", "shape");
    variables.setVariable("MAPPINGS", "[]");
    assertThat(restored.options(variables).geometryField()).isEqualTo("shape");
    assertThat(restored.getGeometryField()).isEqualTo("${GEOM}");
  }

  @Test
  void outputMetadataPreservesInputAndRejectsFieldCollisions() throws Exception {
    var meta = new GeoJsonFeatureDecoderMeta();
    var fields = new RowMeta();
    fields.addValueMeta(new ValueMetaString("feature_json"));
    meta.getFields(fields, "decode", null, null, new Variables(), null);
    assertThat(fields.getFieldNames())
        .containsExactly(
            "feature_json",
            "geometry",
            "geometry_wgs84",
            "properties_json",
            "feature_id_json",
            "geojson_context_json");
    assertThat(fields.getValueMeta(1).getOrigin()).isEqualTo("decode");
    assertThatThrownBy(() -> meta.getFields(fields, "again", null, null, new Variables(), null))
        .hasCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void encoderDefaultsAppendJsonText() throws Exception {
    var meta = new GeoJsonFeatureEncoderMeta();
    var fields = new RowMeta();
    meta.getFields(fields, "encode", null, null, new Variables(), null);
    assertThat(fields.getFieldNames()).containsExactly("feature_json");
    assertThat(fields.getValueMeta(0).getType()).isEqualTo(IValueMeta.TYPE_STRING);
    assertThat(meta.supportsErrorHandling()).isTrue();
  }

  @Test
  void actualHopMetadataInjectorSupportsInheritedProperties() throws Exception {
    var meta = new GeoJsonFeatureDecoderMeta();
    var info = new BeanInjectionInfo<GeoJsonFeatureDecoderMeta>(GeoJsonFeatureDecoderMeta.class);
    var injector = new BeanInjector<GeoJsonFeatureDecoderMeta>(info, new MemoryMetadataProvider());
    var values =
        Map.of(
            "inputField",
            "${INPUT}",
            "profile",
            "JSONFG10",
            "geometryField",
            "shape",
            "dropM",
            "Y",
            "onDataError",
            "RETURN_NULL");
    for (var item : values.entrySet()) {
      var row = new RowMeta();
      row.addValueMeta(new ValueMetaString("value"));
      injector.setProperty(
          meta, item.getKey(), List.of(new RowMetaAndData(row, item.getValue())), "value");
    }
    assertThat(meta.getInputField()).isEqualTo("${INPUT}");
    assertThat(meta.getGeometryField()).isEqualTo("shape");
    assertThat(meta.isDropM()).isTrue();
    assertThat(meta.getOnDataError()).isEqualTo("RETURN_NULL");
  }

  @Test
  void dialogDraftDoesNotShareChangesAndCommitPreservesExpressions() {
    var meta = new GeoJsonFeatureDecoderMeta();
    meta.setGeometryField("${ORIGINAL}");
    var draft = (GeoJsonFeatureDecoderMeta) meta.clone();
    draft.setGeometryField("${EDITED}");
    draft.setDropM(true);
    assertThat(meta.getGeometryField()).isEqualTo("${ORIGINAL}");
    assertThat(meta.isDropM()).isFalse();
    GeoJsonFeatureDialog.copySettings(draft, meta);
    assertThat(meta.getGeometryField()).isEqualTo("${EDITED}");
    assertThat(meta.isDropM()).isTrue();
  }
}
