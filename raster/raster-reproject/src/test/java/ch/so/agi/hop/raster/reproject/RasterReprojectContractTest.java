package ch.so.agi.hop.raster.reproject;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.raster.core.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.plugins.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.engines.local.LocalPipelineEngine;
import org.apache.hop.pipeline.transform.*;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RasterReprojectContractTest {
  @TempDir Path dir;

  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
  }

  private RasterReprojectMeta settings() throws Exception {
    var image = new BufferedImage(2, 2, BufferedImage.TYPE_USHORT_GRAY);
    image.getRaster().setSamples(0, 0, 2, 2, 0, new int[] {1, 2, 3, 4});
    var coverage =
        new GridCoverageFactory()
            .create(
                "source", image, new ReferencedEnvelope(0, 2, 0, 2, CRS.decode("EPSG:3857", true)));
    Path file = dir.resolve("source.tif");
    var writer = new GeoTiffWriter(file.toFile());
    try {
      writer.write(coverage);
    } finally {
      writer.dispose();
      coverage.dispose(true);
    }
    var meta = new RasterReprojectMeta();
    meta.setSource(file.toString());
    meta.setResolutionX("1");
    meta.setResolutionY("1");
    meta.setOutputNoData("65535");
    meta.setOutput(dir.resolve("output.tif").toString());
    return meta;
  }

  @Test
  void registryAndCompleteMetadataRoundtrip() throws Exception {
    var plugin =
        PluginRegistry.getInstance()
            .findPluginWithId(TransformPluginType.class, "SOGIS_RASTER_REPROJECT");
    assertThat(plugin).isNotNull();
    var discovered = PluginRegistry.getInstance().loadClass(plugin, ITransformMeta.class);
    assertThat(discovered).isInstanceOf(RasterReprojectMeta.class);
    assertThat(
            discovered
                .getClass()
                .getAnnotation(org.apache.hop.core.annotations.Transform.class)
                .classLoaderGroup())
        .isEqualTo("sogeo-geometry");
    var meta = settings();
    meta.setSourceField(true);
    meta.setTargetCrs("crs");
    meta.setTargetCrsField(true);
    meta.setResolutionFields(true);
    meta.setResolutionX("dx");
    meta.setResolutionY("dy");
    meta.setExtentMode("BOUNDING_BOX");
    meta.setMinX("x1");
    meta.setMinY("y1");
    meta.setMaxX("x2");
    meta.setMaxY("y2");
    meta.setBboxFields(true);
    meta.setInterpolation("BILINEAR");
    meta.setOutputType("FLOAT32");
    meta.setSourceNoData("-9999");
    meta.setOutputNoData("NaN");
    meta.setOutputField(true);
    meta.setOverwrite(true);
    meta.setPrefix("test_");
    String xml = meta.getXml();
    var restored = new RasterReprojectMeta();
    restored.loadXml(
        XmlHandler.loadXmlString("<transform>" + xml + "</transform>").getDocumentElement(), null);
    assertThat(restored.getXml()).isEqualTo(xml);
    var row = new RowMeta();
    row.addValueMeta(new ValueMetaInteger("id"));
    meta.getFields(row, "reproject", null, null, new Variables(), null);
    assertThat(row.getFieldNames()).containsExactly("id", "test_output_file", "test_status");
    assertThatThrownBy(() -> meta.getFields(row, "reproject", null, null, new Variables(), null))
        .hasMessageContaining("already exists");
  }

  private static PipelineMeta pipeline(RasterReprojectMeta meta) {
    var pipeline = new PipelineMeta();
    pipeline.setName("contract");
    pipeline.addTransform(new TransformMeta("reproject", meta));
    return pipeline;
  }

  private final class Harness extends RasterReprojectTransform {
    final ArrayDeque<Object[]> queue = new ArrayDeque<>();
    final List<Object[]> output = new ArrayList<>();
    final List<String> errors = new ArrayList<>();
    final IRowMeta rowMeta;
    int done;

    Harness(RasterReprojectMeta meta, IRowMeta rowMeta, boolean errorHop) {
      this(meta, rowMeta, pipeline(meta), errorHop);
    }

    private Harness(
        RasterReprojectMeta meta, IRowMeta rowMeta, PipelineMeta pipeline, boolean errorHop) {
      super(
          pipeline.findTransform("reproject"),
          meta,
          new RasterReprojectData(),
          0,
          pipeline,
          new LocalPipelineEngine(pipeline));
      this.rowMeta = rowMeta;
      if (errorHop) {
        var error =
            new TransformErrorMeta(
                getTransformMeta(), new TransformMeta("errors", new RasterReprojectMeta()));
        error.setEnabled(true);
        getTransformMeta().setTransformErrorMeta(error);
      }
    }

    @Override
    public Object[] getRow() {
      return queue.poll();
    }

    @Override
    public IRowMeta getInputRowMeta() {
      return rowMeta;
    }

    @Override
    public void putRow(IRowMeta fields, Object[] row) {
      output.add(Arrays.copyOf(row, fields.size()));
    }

    @Override
    public void putError(
        IRowMeta fields, Object[] row, long count, String message, String names, String code) {
      errors.add(code + ":" + message);
    }

    @Override
    public void setOutputDone() {
      done++;
    }
  }

  @Test
  void rowsVariablesAndFieldBindingsProduceFilesAndPreserveAttributes() throws Exception {
    var meta = settings();
    String source = meta.getSource();
    meta.setSource("src");
    meta.setSourceField(true);
    meta.setOutput("destination");
    meta.setOutputField(true);
    meta.setTargetCrs("crs");
    meta.setTargetCrsField(true);
    meta.setResolutionFields(true);
    meta.setResolutionX("dx");
    meta.setResolutionY("dy");
    meta.setPrefix("${PREFIX}");
    meta.setExtentMode("BOUNDING_BOX");
    meta.setBboxFields(true);
    meta.setMinX("x1");
    meta.setMinY("y1");
    meta.setMaxX("x2");
    meta.setMaxY("y2");
    var fields = new RowMeta();
    for (String f : List.of("id", "src", "destination", "crs", "dx", "dy", "x1", "y1", "x2", "y2"))
      fields.addValueMeta(new ValueMetaString(f));
    var transform = new Harness(meta, fields, false);
    transform.setVariable("PREFIX", "result_");
    Object[] first = {
      "a", source, dir.resolve("a.tif").toString(), "EPSG:3857", "1", "1", "0", "0", "2", "2"
    };
    Object[] second = {
      "b", source, dir.resolve("b.tif").toString(), "EPSG:3857", "2", "2", "0", "0", "2", "2"
    };
    transform.queue.add(first);
    transform.queue.add(second);
    try {
      while (transform.processRow()) {}
    } finally {
      transform.dispose();
    }
    assertThat(transform.output).hasSize(2);
    assertThat(transform.done).isEqualTo(1);
    assertThat(Arrays.copyOf(transform.output.get(0), first.length)).containsExactly(first);
    assertThat(first).hasSize(10);
    assertThat(transform.output.get(0)[11]).isEqualTo("OK");
    for (Object[] row : transform.output) {
      try (var raster = new GeoTiffSource(new RasterDatasetRef((String) row[10]))) {
        assertThat(raster.bands()).isEqualTo(1);
      }
    }
  }

  @Test
  void errorHopContinuesAndUnconfiguredErrorHopFails() throws Exception {
    var meta = settings();
    meta.setOutput("destination");
    meta.setOutputField(true);
    var fields = new RowMeta();
    fields.addValueMeta(new ValueMetaString("destination"));
    var transform = new Harness(meta, fields, true);
    transform.queue.add(new Object[] {dir.resolve("missing-folder/out.tif").toString()});
    transform.queue.add(new Object[] {dir.resolve("valid.tif").toString()});
    try {
      while (transform.processRow()) {}
    } finally {
      transform.dispose();
    }
    assertThat(transform.errors).singleElement().asString().startsWith("RASTER_REPROJECT_ERROR:");
    assertThat(transform.output).hasSize(1);
    var failing = new Harness(meta, fields, false);
    failing.queue.add(new Object[] {""});
    try {
      assertThatThrownBy(failing::processRow).hasMessageContaining("Input field is empty");
    } finally {
      failing.dispose();
    }
  }

  @Test
  void noInputRowsDoNotWriteAFile() throws Exception {
    var meta = settings();
    var transform = new Harness(meta, new RowMeta(), false);
    try {
      assertThat(transform.processRow()).isFalse();
      assertThat(transform.done).isEqualTo(1);
    } finally {
      transform.dispose();
    }
    assertThat(Path.of(meta.getOutput())).doesNotExist();
  }
}
