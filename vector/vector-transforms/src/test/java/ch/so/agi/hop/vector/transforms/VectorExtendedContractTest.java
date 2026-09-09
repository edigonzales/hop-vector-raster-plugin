package ch.so.agi.hop.vector.transforms;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.vector.core.*;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.nio.file.*;
import java.util.*;
import org.apache.hop.core.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.engines.local.LocalPipelineEngine;
import org.apache.hop.pipeline.transform.*;
import org.apache.hop.pipeline.transforms.sort.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;

class VectorExtendedContractTest {
  @TempDir Path dir;

  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void optionsSurviveXml() throws Exception {
    var meta = new VectorWriterMeta();
    meta.setFileName("out.shp");
    meta.setFormat("SHAPEFILE");
    meta.setLayerGeometryType("MULTIPOINT");
    meta.setLayerDimension("XYZM");
    meta.setCharset("windows-1252");
    meta.setTimezone("Europe/Zurich");
    meta.setCrsOverride("EPSG:2056");
    meta.setShapefileFields(List.of(new ShapefileFieldMeta("original", "target", 12, 3)));
    var restored = new VectorWriterMeta();
    restored.loadXml(
        XmlHandler.loadXmlString("<transform>" + meta.getXml() + "</transform>")
            .getDocumentElement(),
        null);
    assertThat(restored.getXml()).isEqualTo(meta.getXml());
    assertThat(restored.getShapefileFields()).hasSize(1);
    assertThat(restored.options()).isEqualTo(meta.options());
    var reader = new VectorReaderMeta();
    reader.setFileName("out.shp");
    reader.setCharset("UTF-8");
    reader.setTimezone("Europe/Zurich");
    reader.setCrsOverride("EPSG:2056");
    var copy = new VectorReaderMeta();
    copy.loadXml(
        XmlHandler.loadXmlString("<transform>" + reader.getXml() + "</transform>")
            .getDocumentElement(),
        null);
    assertThat(copy.getXml()).isEqualTo(reader.getXml());
  }

  RowMeta rm() {
    RowMeta rm = new RowMeta();
    rm.addValueMeta(new ValueMetaInteger("id"));
    rm.addValueMeta(new ValueMetaGeometry("geometry"));
    return rm;
  }

  VectorWriter writer(VectorWriterMeta meta, java.util.function.Supplier<Object[]> rows) {
    var pm = new PipelineMeta();
    var tm = new TransformMeta("writer", meta);
    pm.addTransform(tm);
    return new VectorWriter(tm, meta, new VectorWriterData(), 0, pm, new LocalPipelineEngine(pm)) {
      public Object[] getRow() {
        return rows.get();
      }

      public IRowMeta getInputRowMeta() {
        return rm();
      }
    };
  }

  @Test
  void emptyFilesNullOnlyAndBoundedInference() throws Exception {
    for (String ext : List.of("shp", "gpkg")) {
      var meta = new VectorWriterMeta();
      Path file = dir.resolve("empty." + ext);
      meta.setFileName(file.toString());
      meta.setLayerGeometryType("POINT");
      meta.setLayerDimension("XY");
      meta.setCrsOverride("EPSG:2056");
      var w = writer(meta, () -> null);
      assertThat(w.processRow()).isFalse();
      w.dispose();
      assertThat(file).exists();
      try (var source =
          VectorProviders.get(VectorFormat.resolve("AUTO", file)).open(file, "", "geometry")) {
        assertThat(source.read()).isNull();
      }
      meta.setFileName(dir.resolve("nulls." + ext).toString());
      var count = new java.util.concurrent.atomic.AtomicInteger();
      w = writer(meta, () -> count.getAndIncrement() < 2 ? new Object[] {1L, null} : null);
      while (w.processRow()) {}
      w.dispose();
    }
    var meta = new VectorWriterMeta();
    Path file = dir.resolve("limit.shp");
    meta.setFileName(file.toString());
    var w = writer(meta, () -> new Object[] {1L, null});
    for (int i = 0; i < 10000; i++) assertThat(w.processRow()).isTrue();
    assertThatThrownBy(w::processRow).hasMessageContaining("10000");
    w.dispose();
    assertThat(file).doesNotExist();
  }

  @Test
  void actualPipelineSortSpillsAndRetainsMeasures() throws Exception {
    Path source = dir.resolve("source.shp"),
        target = dir.resolve("sorted.shp"),
        temp = Files.createDirectory(dir.resolve("sort-temp"));
    var gf = new GeometryFactory();
    var provider = VectorProviders.get(VectorFormat.SHAPEFILE);
    Geometry sample = gf.createPoint(new CoordinateXYZM(1, 2, 3, 7));
    try (var sink =
        provider.create(
            new WriteRequest(
                source,
                "source",
                rm(),
                1,
                sample,
                null,
                ShapefileOptions.defaults(),
                Diagnostics.NONE))) {
      for (int i = 30; i > 0; i--)
        sink.write(
            new Object[] {(long) i, gf.createPoint(new CoordinateXYZM(i, i + 1, 3, i + 100))});
      sink.finish();
    }
    var reader = new VectorReaderMeta();
    reader.setFileName(source.toString());
    reader.setGeometryFieldName("geometry");
    var sort = new SortRowsMeta();
    sort.setSortSize("2");
    sort.setDirectory(temp.toString());
    sort.setPrefix("z-m-spill");
    sort.setSortFields(List.of(new SortRowsField("id", true, false, false, 0, false)));
    var writer = new VectorWriterMeta();
    writer.setFileName(target.toString());
    var pm = new PipelineMeta();
    pm.setName("Shapefile sort with disk spill");
    var a = new TransformMeta("read", reader);
    var b = new TransformMeta("sort", sort);
    var c = new TransformMeta("write", writer);
    pm.addTransform(a);
    pm.addTransform(b);
    pm.addTransform(c);
    pm.addPipelineHop(new PipelineHopMeta(a, b));
    pm.addPipelineHop(new PipelineHopMeta(b, c));
    var pipeline = new LocalPipelineEngine(pm);
    pipeline.prepareExecution();
    var observedFiles = new java.util.concurrent.atomic.AtomicInteger();
    var sorter = (SortRows) pipeline.findRunThread("sort");
    sorter.addRowListener(
        new org.apache.hop.pipeline.transform.RowAdapter() {
          public void rowWrittenEvent(IRowMeta metadata, Object[] row) {
            observedFiles.accumulateAndGet(sorter.getData().files.size(), Math::max);
          }
        });
    pipeline.startThreads();
    pipeline.waitUntilFinished();
    assertThat(pipeline.getErrors()).isZero();
    var sortTransform = pipeline.findRunThread("sort");
    assertThat(sortTransform.getLinesWritten()).isEqualTo(30);
    assertThat(observedFiles.get()).as("sort must actually merge temporary files").isGreaterThan(0);
    try (var input = provider.open(target, "", "geometry")) {
      for (int i = 1; i <= 30; i++) {
        var row = input.read();
        assertThat(row[0]).isEqualTo((long) i);
        var g = (Geometry) row[1];
        assertThat(g.getCoordinate().getM()).isEqualTo(i + 100);
        assertThat(g.getCoordinate().getZ()).isEqualTo(3);
      }
      assertThat(input.read()).isNull();
    }
  }
}
