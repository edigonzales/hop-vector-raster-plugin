package ch.so.agi.hop.vector.transforms;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.vector.core.*;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.nio.file.*;
import java.util.*;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.engines.local.LocalPipelineEngine;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.io.WKTReader;

class CloudFormatsContractTest {
  @TempDir Path dir;

  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void xmlOptionsAndCapabilities() throws Exception {
    for (String format : List.of("FLATGEOBUF", "PARQUET")) {
      var m = new VectorWriterMeta();
      m.setFileName("out." + (format.equals("PARQUET") ? "parquet" : "fgb"));
      m.setFormat(format);
      m.setFlatGeobufIndex(false);
      m.setFlatGeobufSkipEmpty(true);
      m.setOverwrite(true);
      m.setParquetLogicalType("GEOGRAPHY");
      m.setParquetAlgorithm("KARNEY");
      m.setParquetCompression("UNCOMPRESSED");
      m.setParquetRowGroupSize(65536);
      var copy = new VectorWriterMeta();
      copy.loadXml(
          XmlHandler.loadXmlString("<transform>" + m.getXml() + "</transform>")
              .getDocumentElement(),
          null);
      assertThat(copy.getXml()).isEqualTo(m.getXml());
      assertThat(copy.options()).isEqualTo(m.options());
      var f = VectorFormat.resolve("AUTO", Path.of(m.getFileName()));
      assertThat(f.readable()).isFalse();
      assertThat(f.writable()).isTrue();
      assertThatThrownBy(
              () -> VectorProviders.get(f).open(Path.of(m.getFileName()), "", "geometry"))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Test
  void sharedWriterInferenceAndExplicitEmptyOutput() throws Exception {
    for (String ext : List.of("fgb", "parquet"))
      for (boolean empty : List.of(false, true)) {
        var rm = new RowMeta();
        rm.addValueMeta(new ValueMetaInteger("id"));
        rm.addValueMeta(new ValueMetaGeometry("geometry"));
        var meta = new VectorWriterMeta();
        meta.setFileName(dir.resolve(ext + empty + "." + ext).toString());
        meta.setFlatGeobufIndex(false);
        if (empty) {
          meta.setLayerGeometryType("POINT");
          meta.setLayerDimension("XYZM");
        }
        var pm = new PipelineMeta();
        var tm = new TransformMeta("writer", meta);
        pm.addTransform(tm);
        var rows = new ArrayDeque<Object[]>();
        if (!empty) {
          rows.add(new Object[] {1L, null});
          rows.add(new Object[] {2L, new WKTReader().read("POINT ZM (1 2 3 4)")});
        }
        var writer =
            new VectorWriter(tm, meta, new VectorWriterData(), 0, pm, new LocalPipelineEngine(pm)) {
              public Object[] getRow() {
                return rows.poll();
              }

              public IRowMeta getInputRowMeta() {
                return rm;
              }
            };
        try {
          while (writer.processRow()) {}
        } finally {
          writer.dispose();
        }
        assertThat(Path.of(meta.getFileName())).exists();
      }
  }

  @Test
  void curvesAndGeographicCrsService() throws Exception {
    var resolver = new ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver();
    assertThat(resolver.isGeographic(resolver.resolve(4326))).isTrue();
    assertThat(resolver.isGeographic(resolver.resolve(2056))).isFalse();
    var curve =
        new com.atolcd.hop.gis.geometry.curve.CircularString(
            new org.locationtech.jts.geom.Coordinate[] {
              new org.locationtech.jts.geom.CoordinateXY(0, 0),
              new org.locationtech.jts.geom.CoordinateXY(1, 1),
              new org.locationtech.jts.geom.CoordinateXY(2, 0)
            },
            new org.locationtech.jts.geom.GeometryFactory());
    var rm = new RowMeta();
    rm.addValueMeta(new ValueMetaGeometry("geometry"));
    for (var f : new VectorFormat[] {VectorFormat.FLATGEOBUF, VectorFormat.PARQUET}) {
      var warnings = new ArrayList<String>();
      var r =
          new WriteRequest(
              dir.resolve(f.name()),
              "curve",
              rm,
              0,
              curve,
              GeometrySchema.infer(curve),
              f == VectorFormat.FLATGEOBUF
                  ? FlatGeobufOptions.defaults()
                  : ParquetOptions.defaults(),
              (field, cause, message) -> warnings.add(cause));
      try (var sink = VectorProviders.get(f).create(r)) {
        sink.write(new Object[] {curve});
        sink.finish();
      }
      assertThat(warnings).contains("curve");
    }
  }

  @Test
  void examplePipelinesRun() throws Exception {
    var rm = new RowMeta();
    rm.addValueMeta(new ValueMetaInteger("id"));
    rm.addValueMeta(new ValueMetaGeometry("geometry"));
    Path source = dir.resolve("input.shp");
    var point = new WKTReader().read("POINT ZM (1 2 3 4)");
    try (var sink =
        VectorProviders.get(VectorFormat.SHAPEFILE)
            .create(
                new WriteRequest(
                    source,
                    "input",
                    rm,
                    1,
                    point,
                    GeometrySchema.infer(point),
                    ShapefileOptions.defaults(),
                    Diagnostics.NONE))) {
      sink.write(new Object[] {1L, point});
      sink.finish();
    }
    for (String f : List.of("flatgeobuf", "parquet")) {
      Path example = Path.of("../../examples/cloud-output/vector-to-" + f + ".hpl");
      var vars = new org.apache.hop.core.variables.Variables();
      var pm =
          new PipelineMeta(
              example.toString(),
              new org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider(),
              vars);
      Path target = dir.resolve(f.equals("parquet") ? "example.parquet" : "example.fgb");
      // Use the actual parameterized example, not a reconstructed pipeline.
      var pipeline = new LocalPipelineEngine(pm);
      pipeline.copyParametersFromDefinitions(pm);
      pipeline.setParameterValue("INPUT_VECTOR", source.toString());
      pipeline.setParameterValue("INPUT_LAYER", "");
      pipeline.setParameterValue("OUTPUT_FILE", target.toString());
      pipeline.activateParameters(pipeline);
      pipeline.prepareExecution();
      pipeline.startThreads();
      pipeline.waitUntilFinished();
      assertThat(pipeline.getErrors()).isZero();
      assertThat(target).exists();
    }
  }
}
