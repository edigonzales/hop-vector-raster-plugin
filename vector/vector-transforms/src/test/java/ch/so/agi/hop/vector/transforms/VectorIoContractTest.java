package ch.so.agi.hop.vector.transforms;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.vector.core.*;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.nio.file.*;
import java.util.*;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.transform.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;

class VectorIoContractTest {
  @TempDir Path dir;

  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void allCommonAndGenerateSettingsRoundtrip() throws Exception {
    VectorWriterMeta m = new VectorWriterMeta();
    m.setFileName("${OUT}");
    m.setLayerName("layer");
    m.setFormat("ARCINFO_GENERATE");
    m.setGeometryField("geom");
    m.setGeometryType("POLYGON");
    m.setDimension("XYZ");
    m.setIdField("${ID_FIELD}");
    m.setStartId(81);
    m.setDecimals(7);
    m.setDiscardExtraOrdinates(true);
    m.setComma(true);
    m.setSkipEmpty(true);
    m.setOverwrite(true);
    VectorWriterMeta copy = new VectorWriterMeta();
    copy.loadXml(
        XmlHandler.loadXmlString("<transform>" + m.getXml() + "</transform>").getDocumentElement(),
        null);
    assertThat(copy.getXml()).isEqualTo(m.getXml());
    assertThat(copy.options()).isEqualTo(m.options());
    VectorReaderMeta reader = new VectorReaderMeta();
    reader.setFormat("GEOPACKAGE");
    reader.setFileName("${IN}");
    reader.setLayerName("places");
    reader.setGeometryFieldName("shape");
    var restored = new VectorReaderMeta();
    restored.loadXml(
        XmlHandler.loadXmlString("<transform>" + reader.getXml() + "</transform>")
            .getDocumentElement(),
        null);
    assertThat(restored.getXml()).isEqualTo(reader.getXml());
  }

  @Test
  void sharedWriterAndReaderWorkForShapefileAndGeoPackage() throws Exception {
    for (String extension : List.of("shp", "gpkg")) {
      Path file = dir.resolve("places." + extension);
      RowMeta rm = new RowMeta();
      rm.addValueMeta(new ValueMetaString("name"));
      rm.addValueMeta(new ValueMetaGeometry("shape"));
      Geometry point =
          new GeometryFactory(new PrecisionModel(), 2056)
              .createPoint(new Coordinate(2600000, 1200000));
      VectorWriterMeta meta = new VectorWriterMeta();
      meta.setFileName(file.toString());
      meta.setGeometryField("shape");
      PipelineMeta pm = new PipelineMeta();
      Pipeline pipeline = new org.apache.hop.pipeline.engines.local.LocalPipelineEngine(pm);
      TransformMeta tm = new TransformMeta("writer", meta);
      pm.addTransform(tm);
      ArrayDeque<Object[]> rows = new ArrayDeque<>();
      rows.add(new Object[] {"first null", null});
      rows.add(new Object[] {"point", point});
      VectorWriter writer =
          new VectorWriter(tm, meta, new VectorWriterData(), 0, pm, pipeline) {
            @Override
            public Object[] getRow() {
              return rows.poll();
            }

            @Override
            public IRowMeta getInputRowMeta() {
              return rm;
            }
          };
      assertThat(writer.processRow()).isTrue();
      assertThat(writer.processRow()).isTrue();
      assertThat(file).doesNotExist();
      assertThat(writer.processRow()).isFalse();
      writer.dispose();
      assertThat(file).exists();
      VectorReaderMeta readerMeta = new VectorReaderMeta();
      readerMeta.setFileName(file.toString());
      readerMeta.setGeometryFieldName("geometry");
      RowMeta detected = new RowMeta();
      readerMeta.getFields(detected, "reader", null, null, new Variables(), null);
      assertThat(detected.getFieldNames()).containsExactly("name", "geometry");
      try (var reader =
          VectorProviders.get(VectorFormat.resolve("AUTO", file)).open(file, "", "geometry")) {
        assertThat(reader.read()[1]).isNull();
        Object[] row = reader.read();
        assertThat(row[0]).isEqualTo("point");
        assertThat(((Geometry) row[1]).equalsExact(point)).isTrue();
        assertThat(reader.read()).isNull();
      }
    }
  }

  @Test
  void shapefileAbortAndSidecarCollisionDoNotPublish() throws Exception {
    RowMeta rm = new RowMeta();
    rm.addValueMeta(new ValueMetaGeometry("geometry"));
    Geometry point = new GeometryFactory().createPoint(new Coordinate(1, 2));
    Path file = dir.resolve("places.shp");
    var provider = VectorProviders.get(VectorFormat.SHAPEFILE);
    var r =
        new WriteRequest(
            file,
            "places",
            rm,
            0,
            point,
            null,
            new ch.so.agi.hop.vector.core.FormatOptions.None(),
            ch.so.agi.hop.vector.core.Diagnostics.NONE);
    try (var sink = provider.create(r)) {
      sink.write(new Object[] {point});
    }
    assertThat(file).doesNotExist();
    try (var files = Files.list(dir)) {
      assertThat(files.toList()).isEmpty();
    }
    Files.writeString(dir.resolve("places.dbf"), "existing");
    assertThatThrownBy(() -> provider.create(r)).hasMessageContaining("sidecar");
    assertThat(file).doesNotExist();
    assertThat(Files.readString(dir.resolve("places.dbf"))).isEqualTo("existing");
    try (var files = Files.list(dir)) {
      assertThat(files.toList()).containsExactly(dir.resolve("places.dbf"));
    }
  }

  @Test
  void generateEmptyStreamNeedsNoRowSchemaOrGeometryInference() throws Exception {
    Path file = dir.resolve("empty.gen");
    VectorWriterMeta meta = new VectorWriterMeta();
    meta.setFormat("AUTO");
    meta.setFileName("${OUTPUT_PATH}");
    PipelineMeta pm = new PipelineMeta();
    TransformMeta tm = new TransformMeta("writer", meta);
    pm.addTransform(tm);
    VectorWriter writer =
        new VectorWriter(
            tm,
            meta,
            new VectorWriterData(),
            0,
            pm,
            new org.apache.hop.pipeline.engines.local.LocalPipelineEngine(pm)) {
          @Override
          public Object[] getRow() {
            return null;
          }
        };
    writer.setVariable("OUTPUT_PATH", file.toString());
    assertThat(writer.processRow()).isFalse();
    writer.dispose();
    assertThat(Files.readString(file)).isEqualTo("END\n");
  }
}
