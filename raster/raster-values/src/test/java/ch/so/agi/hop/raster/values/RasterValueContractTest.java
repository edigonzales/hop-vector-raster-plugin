package ch.so.agi.hop.raster.values;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.raster.type.ValueMetaRaster;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.pipeline.PipelineMeta;
import org.eclipse.swt.widgets.Shell;
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
  void existingReaderSourceAndSourceFieldXmlRemainCompatible() throws Exception {
    var restored = new RasterReaderMeta();
    restored.loadXml(
        XmlHandler.loadXmlString(
                "<transform><source>raster_path</source><sourceField>Y</sourceField></transform>")
            .getDocumentElement(),
        null);

    assertThat(restored.getSource()).isEqualTo("raster_path");
    assertThat(restored.isSourceField()).isTrue();

    var roundTrip = new RasterReaderMeta();
    roundTrip.loadXml(
        XmlHandler.loadXmlString("<transform>" + restored.getXml() + "</transform>")
            .getDocumentElement(),
        null);
    assertThat(roundTrip.getSource()).isEqualTo("raster_path");
    assertThat(roundTrip.isSourceField()).isTrue();
  }

  @Test
  void inputRasterSuggestionsOnlyIncludeRasterTypedFields() {
    var fields = new RowMeta();
    fields.addValueMeta(new ValueMetaString("raster_path"));
    fields.addValueMeta(new ValueMetaRaster("raster"));
    fields.addValueMeta(new ValueMetaString("description"));

    assertThat(RasterValueDialog.rasterFieldNames(fields)).containsExactly("raster");
    assertThat(RasterValueDialog.rasterFieldNames(null)).isEmpty();
  }

  @Test
  void currentRasterMetasExposeHopCompatibleDialogConstructors() throws Exception {
    var metas =
        java.util.List.of(
            new RasterReaderMeta(),
            new RasterClipMeta(),
            new RasterReprojectMeta(),
            new RasterZonalStatsMeta(),
            new RasterInfoMeta(),
            new RasterWriterMeta());

    for (var meta : metas) {
      assertThat(meta.getDialogClassName()).isEqualTo(RasterValueDialog.class.getName());
      var dialogClass = Class.forName(meta.getDialogClassName());
      assertThat(
              dialogClass.getConstructor(
                  Shell.class, IVariables.class, meta.getClass(), PipelineMeta.class))
          .isNotNull();
    }
  }
}
