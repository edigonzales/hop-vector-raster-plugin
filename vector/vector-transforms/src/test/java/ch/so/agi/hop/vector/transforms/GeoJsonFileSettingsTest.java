package ch.so.agi.hop.vector.transforms;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.hop.core.variables.Variables;
import org.junit.jupiter.api.Test;

class GeoJsonFileSettingsTest {
  @Test
  void writerResolvesMappingVariablesAtRuntimeWithAConstantFilePath() {
    var meta = new VectorWriterMeta();
    meta.setFileName("output.geojson");
    meta.setGeometryField("geometry");
    meta.setGeoJsonMappings("${MAPPINGS}");
    assertDoesNotThrow(() -> meta.validateSettings());
    var variables = new Variables();
    variables.setVariable(
        "MAPPINGS", "[{\"pointer\":\"/name\",\"field\":\"name\",\"type\":\"STRING\"}]");
    assertDoesNotThrow(() -> meta.validateSettings(variables));
    assertEquals(1, meta.geoJsonOptions(variables).features().mappings().size());
    assertEquals("${MAPPINGS}", meta.getGeoJsonMappings());
    variables.setVariable("MAPPINGS", "not JSON");
    assertThrows(IllegalArgumentException.class, () -> meta.validateSettings(variables));
  }

  @Test
  void readerIgnoresInactiveGenericCrsAndLayerSettings() {
    var meta = new VectorReaderMeta();
    meta.setFileName("input.geojson");
    meta.setCrsOverride("${INACTIVE_CRS}");
    meta.setLayerName("${INACTIVE_LAYER}");
    var request = meta.request(new Variables(), ch.so.agi.hop.vector.core.Diagnostics.NONE);
    assertEquals("", request.crsOverride());
    assertEquals("", request.layer());
    assertEquals("${INACTIVE_CRS}", meta.getCrsOverride());
  }
}
