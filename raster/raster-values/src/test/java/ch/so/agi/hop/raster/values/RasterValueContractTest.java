package ch.so.agi.hop.raster.values;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.raster.type.ValueMetaRaster;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.junit.jupiter.api.Test;

class RasterValueContractTest {
  @Test
  void readerAndInPlaceOperationsExposeRasterType() throws Exception {
    var vars = new Variables();
    var row = new RowMeta();
    var reader = new RasterReaderMeta();
    reader.setSource("/tmp/input.tif");
    reader.getFields(row, "reader", null, null, vars, null);
    assertThat(row.getValueMeta(0)).isInstanceOf(ValueMetaRaster.class);
    var clip = new RasterClipMeta();
    clip.getFields(row, "clip", null, null, vars, null);
    assertThat(row.size()).isEqualTo(1);
    clip.setOutputRasterField("clipped");
    clip.getFields(row, "clip", null, null, vars, null);
    assertThat(row.size()).isEqualTo(2);
    assertThatThrownBy(() -> clip.getFields(row, "clip", null, null, vars, null))
        .hasMessageContaining("already exists");
  }

  @Test
  void metadataXmlRoundTripKeepsAllSettings() throws Exception {
    var source = new RasterReprojectMeta();
    source.setRasterField("dem");
    source.setResolutionX("${RES}");
    source.setResolutionY("2");
    source.setTargetCrs("EPSG:2056");
    source.setInterpolation("BILINEAR");
    source.setOutputRasterField("warped");
    var restored = new RasterReprojectMeta();
    restored.loadXml(
        XmlHandler.loadXmlString("<transform>" + source.getXml() + "</transform>")
            .getDocumentElement(),
        null);
    assertThat(restored.getResolutionX()).isEqualTo("${RES}");
    assertThat(restored.getRasterField()).isEqualTo("dem");
    assertThat(restored.getOutputRasterField()).isEqualTo("warped");
    assertThat(restored.getVersion()).isEqualTo(1);
  }

  @Test
  void legacyPipelineGetsMigrationMessage() {
    assertThatThrownBy(() -> new RasterLegacyClipMeta().validateSettings())
        .hasMessageContaining("migrate");
    assertThatThrownBy(() -> new RasterLegacyReprojectMeta().validateSettings())
        .hasMessageContaining("migrate");
  }
}
