package ch.so.agi.hop.raster.stats;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.raster.clip.*;
import ch.so.agi.hop.vector.transforms.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.transform.*;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;

class TransformContractTest {
  @TempDir Path dir;

  @BeforeAll
  static void setup() throws Exception {
    HopEnvironment.init();
  }

  static void roundtrip(BaseTransformMeta<?, ?> original, BaseTransformMeta<?, ?> restored)
      throws Exception {
    String xml = original.getXml();
    restored.loadXml(
        XmlHandler.loadXmlString("<transform>" + xml + "</transform>").getDocumentElement(), null);
    assertThat(restored.getXml()).isEqualTo(xml);
  }

  @Test
  void discoversAllTransformsInSharedClassLoaderGroup() throws Exception {
    var registry = org.apache.hop.core.plugins.PluginRegistry.getInstance();
    for (String old :
        List.of(
            "GEOTOOLS_VECTOR_READER",
            "GEOTOOLS_VECTOR_WRITER",
            "GEOTOOLS_RASTER_CLIP",
            "GEOTOOLS_RASTER_ZONAL_STATS",
            "ARCINFO_GENERATE_WRITER"))
      assertThat(
              registry.findPluginWithId(org.apache.hop.core.plugins.TransformPluginType.class, old))
          .as(old)
          .isNull();
    for (String id :
        List.of(
            "SOGIS_RASTER_CLIP",
            "SOGIS_RASTER_ZONAL_STATS",
            "SOGIS_VECTOR_WRITER",
            "SOGIS_VECTOR_READER")) {
      var plugin =
          registry.findPluginWithId(org.apache.hop.core.plugins.TransformPluginType.class, id);
      assertThat(plugin).as(id).isNotNull();
      var meta = registry.loadClass(plugin, ITransformMeta.class);
      assertThat(
              meta.getClass()
                  .getAnnotation(org.apache.hop.core.annotations.Transform.class)
                  .classLoaderGroup())
          .isEqualTo("sogeo-geometry");
    }
  }

  @Test
  void metadataRoundtripAndOutputContract() throws Exception {
    var stats = new RasterZonalStatsMeta();
    stats.setDefault();
    stats.setSource("${RASTER}");
    stats.setSourceField(true);
    stats.setExplicitCrs("EPSG:4326");
    stats.setStatistics("sum mean stddev");
    stats.setPrefix("height_");
    stats.setNoData("-9999");
    roundtrip(stats, new RasterZonalStatsMeta());
    var row = new RowMeta();
    row.addValueMeta(new ValueMetaString("address"));
    stats.getFields(row, "stats", null, null, new Variables(), null);
    assertThat(row.getFieldNames())
        .containsExactly(
            "address",
            "height_sum",
            "height_mean",
            "height_stddev",
            "height_count",
            "height_status");
    assertThat(row.getValueMeta(4).getType()).isEqualTo(IValueMeta.TYPE_INTEGER);
    assertThatThrownBy(() -> stats.getFields(row, "stats", null, null, new Variables(), null))
        .hasMessageContaining("already exists");
    var clip = new RasterClipMeta();
    clip.setDefault();
    clip.setSource("${RASTER}");
    clip.setOutput("${OUT}");
    clip.setClipMethod("BOUNDING_BOX");
    clip.setMinX("x1");
    clip.setMinY("y1");
    clip.setMaxX("x2");
    clip.setMaxY("y2");
    clip.setBboxFields(true);
    clip.setExplicitCrs("EPSG:2056");
    clip.setOverwrite(true);
    roundtrip(clip, new RasterClipMeta());
    var gen = new VectorWriterMeta();
    gen.setDefault();
    gen.setFormat("ARCINFO_GENERATE");
    gen.setFileName("${OUT}");
    gen.setGeometryType("POLYGON");
    gen.setDimension("XYZ");
    gen.setStartId(123);
    gen.setDecimals(3);
    gen.setComma(true);
    gen.setIdField("id");
    gen.setDiscardExtraOrdinates(true);
    gen.setSkipEmpty(true);
    gen.setOverwrite(true);
    gen.setLayerName("unused");
    roundtrip(gen, new VectorWriterMeta());
  }

  @Test
  void statsPreserveRowsAndGenerateCommitsOnlyAtEnd() throws Exception {
    Path file = dir.resolve("input.tif");
    var image = new BufferedImage(4, 3, BufferedImage.TYPE_BYTE_GRAY);
    for (int y = 0; y < 3; y++)
      for (int x = 0; x < 4; x++) image.getRaster().setSample(x, y, 0, 1 + x + y * 4);
    var coverage =
        new GridCoverageFactory()
            .create(
                "test",
                image,
                new ReferencedEnvelope(
                    2600000, 2600004, 1200000, 1200003, CRS.decode("EPSG:2056", true)));
    var writer = new GeoTiffWriter(file.toFile());
    try {
      writer.write(coverage);
    } finally {
      writer.dispose();
      coverage.dispose(true);
    }
    var meta = new RasterZonalStatsMeta();
    meta.setDefault();
    meta.setSource(file.toString());
    meta.setPrefix("height_");
    var geometry =
        new GeometryFactory(new PrecisionModel(), 2056)
            .toGeometry(new Envelope(2600000, 2600004, 1200000, 1200003));
    var rows = new RowMeta();
    rows.addValueMeta(new ValueMetaInteger("id"));
    rows.addValueMeta(new com.atolcd.hop.core.row.value.ValueMetaGeometry("geometry"));
    rows.addValueMeta(new ValueMetaString("address"));
    // Row metadata does not perform geometry conversion: the transform consumes the shared JTS
    // value.
    var queue = new ArrayDeque<Object[]>();
    queue.add(new Object[] {17L, geometry, "Main Street"});
    queue.add(new Object[] {18L, null, "Empty"});
    var output = new ArrayList<Object[]>();
    var pm = new PipelineMeta();
    pm.setName("test");
    var tm = new TransformMeta("stats", meta);
    pm.addTransform(tm);
    var pipeline = new org.apache.hop.pipeline.engines.local.LocalPipelineEngine(pm);
    var transform =
        new RasterZonalStatsTransform(tm, meta, new RasterZonalStatsData(), 0, pm, pipeline) {
          @Override
          public Object[] getRow() {
            return queue.poll();
          }

          @Override
          public IRowMeta getInputRowMeta() {
            return rows;
          }

          @Override
          public void putRow(IRowMeta fields, Object[] row) {
            output.add(java.util.Arrays.copyOf(row, fields.size()));
          }
        };
    try {
      while (transform.processRow()) {}
    } finally {
      transform.dispose();
    }
    assertThat(output).hasSize(2);
    assertThat(output.get(0))
        .containsExactly(17L, geometry, "Main Street", 6.5, 1d, 12d, 12L, "OK");
    assertThat(output.get(0)[1]).isSameAs(geometry);
    assertThat(output.get(1))
        .containsExactly(18L, null, "Empty", null, null, null, 0L, "EMPTY_GEOMETRY");
    var cm = new RasterClipMeta();
    cm.setDefault();
    cm.setSource(file.toString());
    cm.setClipMethod("BOUNDING_BOX");
    cm.setExplicitCrs("EPSG:2056");
    cm.setMinX("2600000");
    cm.setMinY("1200000");
    cm.setMaxX("2600004");
    cm.setMaxY("1200003");
    cm.setNoData("255");
    Path clipped = dir.resolve("clip.tif");
    cm.setOutput(clipped.toString());
    var ctm = new TransformMeta("clip", cm);
    pm.addTransform(ctm);
    queue.add(output.get(0));
    var clipRows = new ArrayList<Object[]>();
    var clip =
        new RasterClipTransform(ctm, cm, new RasterClipData(), 0, pm, pipeline) {
          @Override
          public Object[] getRow() {
            return queue.poll();
          }

          @Override
          public IRowMeta getInputRowMeta() {
            return rows;
          }

          @Override
          public void putRow(IRowMeta fields, Object[] row) {
            clipRows.add(java.util.Arrays.copyOf(row, fields.size()));
          }
        };
    try {
      while (clip.processRow()) {}
    } finally {
      clip.dispose();
    }
    assertThat(clipRows.get(0))
        .containsExactly(17L, geometry, "Main Street", clipped.toString(), "OK");
    assertThat(clipped).exists();
    var gen = new VectorWriterMeta();
    gen.setDefault();
    gen.setFormat("ARCINFO_GENERATE");
    gen.setGeometryType("POLYGON");
    gen.setIdField("id");
    Path target = dir.resolve("polygons.gen");
    gen.setFileName(target.toString());
    var gm = new TransformMeta("generate", gen);
    pm.addTransform(gm);
    queue.add(output.get(0));
    var export =
        new VectorWriter(gm, gen, new VectorWriterData(), 0, pm, pipeline) {
          @Override
          public Object[] getRow() {
            return queue.poll();
          }

          @Override
          public IRowMeta getInputRowMeta() {
            return rows;
          }
        };
    try {
      assertThat(export.processRow()).isTrue();
      assertThat(target).doesNotExist();
      assertThat(export.processRow()).isFalse();
    } finally {
      export.dispose();
    }
    assertThat(Files.readString(target)).startsWith("17 AUTO\n").endsWith("END\nEND\n");
  }
}
