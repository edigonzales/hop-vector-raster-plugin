package ch.so.agi.hop.raster.values;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.raster.type.ValueMetaRaster;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.pipeline.PipelineMeta;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.Test;

class RasterValueContractTest {
  @Test
  void geotiffOverviewOptInResolvesVariablesAndKeepsCogBehavior() {
    var writer = new RasterWriterMeta();
    writer.setFormat("${FORMAT}");
    writer.setOverviews("${OVERVIEWS}");
    var vars = new Variables();
    vars.setVariable("FORMAT", " GEOTIFF ");
    vars.setVariable("OVERVIEWS", " AUTO ");
    assertThat(writer.writeOptions(vars).overviews().name()).isEqualTo("NONE");
    writer.setAddOverviews(true);
    assertThat(writer.writeOptions(vars).overviews().name()).isEqualTo("AUTO");
    vars.setVariable("OVERVIEWS", "NONE");
    assertThat(writer.writeOptions(vars).overviews().name()).isEqualTo("NONE");
    writer.setAddOverviews(false);
    vars.setVariable("FORMAT", "COG");
    vars.setVariable("OVERVIEWS", "AUTO");
    assertThat(writer.writeOptions(vars).overviews().name()).isEqualTo("AUTO");
  }

  @Test
  void writerSettingsResolvePipelineParametersBeforeValidation() throws Exception {
    var writer = new RasterWriterMeta();
    writer.setOutput("${OUTPUT_FILE}");
    writer.setFormat("${FORMAT}");
    writer.setCompression("${COMPRESSION}");
    writer.setOverviews("${OVERVIEWS}");
    writer.setOverviewResampling("${OVERVIEW_RESAMPLING}");
    var vars = new Variables();
    vars.setVariable("OUTPUT_FILE", "destination.tif");
    vars.setVariable("FORMAT", "COG");
    vars.setVariable("COMPRESSION", "Deflate");
    vars.setVariable("OVERVIEWS", "AUTO");
    vars.setVariable("OVERVIEW_RESAMPLING", "AVERAGE");
    writer.validateSettings(vars);
    var row = new RowMeta();
    row.addValueMeta(new ValueMetaRaster("raster"));
    writer.getFields(row, "Write COG", null, null, vars, null);
    assertThat(row.indexOfValue("raster_output_file")).isGreaterThanOrEqualTo(0);
    assertThat(row.indexOfValue("raster_status")).isGreaterThanOrEqualTo(0);
  }

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
  void existingWriterOutputAndOutputFieldXmlRemainCompatible() throws Exception {
    var restored = new RasterWriterMeta();
    restored.loadXml(
        XmlHandler.loadXmlString(
                "<transform><output>destination_path</output><outputField>Y</outputField>"
                    + "<overwrite>Y</overwrite><prefix>result_</prefix></transform>")
            .getDocumentElement(),
        null);

    assertThat(restored.getOutput()).isEqualTo("destination_path");
    assertThat(restored.isOutputField()).isTrue();
    assertThat(restored.getCompression()).isEqualTo("Deflate");
    assertThat(restored.getFormat()).isEqualTo("GEOTIFF");
    assertThat(restored.getOverviews()).isEqualTo("AUTO");
    assertThat(restored.isAddOverviews()).isFalse();
    assertThat(restored.getOverviewResampling()).isEqualTo("AVERAGE");
    assertThat(restored.getJpegQuality()).isEqualTo(75);
    assertThat(restored.isOverwrite()).isTrue();
    assertThat(restored.getPrefix()).isEqualTo("result_");

    var roundTrip = new RasterWriterMeta();
    roundTrip.loadXml(
        XmlHandler.loadXmlString("<transform>" + restored.getXml() + "</transform>")
            .getDocumentElement(),
        null);
    assertThat(roundTrip.getOutput()).isEqualTo("destination_path");
    assertThat(roundTrip.isOutputField()).isTrue();
    assertThat(roundTrip.getCompression()).isEqualTo("Deflate");
    assertThat(roundTrip.isOverwrite()).isTrue();
    assertThat(roundTrip.getPrefix()).isEqualTo("result_");
  }

  @Test
  void writerFormatAndOverviewSettingsRoundTrip() throws Exception {
    var writer = new RasterWriterMeta();
    writer.setOutput("destination_path");
    writer.setFormat("COG");
    writer.setCompression("LZW");
    writer.setOverviews("NONE");
    writer.setOverviewResampling("NEAREST");
    writer.setJpegQuality(90);
    writer.setAddOverviews(true);
    var restored = new RasterWriterMeta();
    restored.loadXml(
        XmlHandler.loadXmlString("<transform>" + writer.getXml() + "</transform>")
            .getDocumentElement(),
        null);

    assertThat(restored.getFormat()).isEqualTo("COG");
    assertThat(restored.getCompression()).isEqualTo("LZW");
    assertThat(restored.getOverviews()).isEqualTo("NONE");
    assertThat(restored.getOverviewResampling()).isEqualTo("NEAREST");
    assertThat(restored.getJpegQuality()).isEqualTo(90);
    assertThat(restored.isAddOverviews()).isTrue();
  }

  @Test
  void writerRejectsUnknownFormatAndOverviewSettings() {
    var format = new RasterWriterMeta();
    format.setOutput("destination.tif");
    format.setFormat("PNG");
    assertThatThrownBy(format::validateSettings)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("format");

    var overviews = new RasterWriterMeta();
    overviews.setOutput("destination.tif");
    overviews.setOverviews("EXTERNAL");
    assertThatThrownBy(overviews::validateSettings)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Overview");

    var resampling = new RasterWriterMeta();
    resampling.setOutput("destination.tif");
    resampling.setOverviewResampling("CUBIC");
    assertThatThrownBy(resampling::validateSettings)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("resampling");

    var quality = new RasterWriterMeta();
    quality.setOutput("destination.tif");
    quality.setJpegQuality(0);
    assertThatThrownBy(quality::validateSettings)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("JPEG quality");
  }

  @Test
  void writerCompressionChoicesRoundTripIncludingUncompressedMode() throws Exception {
    var modes = new java.util.ArrayList<String>();
    modes.add("None");
    modes.addAll(ch.so.agi.hop.raster.geotools.GeoToolsRasterBackend.compressionTypes());

    for (String mode : modes) {
      var writer = new RasterWriterMeta();
      writer.setCompression(mode);
      var restored = new RasterWriterMeta();
      restored.loadXml(
          XmlHandler.loadXmlString("<transform>" + writer.getXml() + "</transform>")
              .getDocumentElement(),
          null);
      assertThat(restored.getCompression()).isEqualTo(mode);
    }
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
  void writerPathSuggestionsOnlyIncludeStringFields() {
    var fields = new RowMeta();
    fields.addValueMeta(new ValueMetaString("destination_path"));
    fields.addValueMeta(new ValueMetaRaster("raster"));
    fields.addValueMeta(new ValueMetaInteger("row_number"));
    fields.addValueMeta(new ValueMetaString("description"));

    assertThat(RasterValueDialog.stringFieldNames(fields))
        .containsExactly("destination_path", "description");
    assertThat(RasterValueDialog.stringFieldNames(null)).isEmpty();
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
