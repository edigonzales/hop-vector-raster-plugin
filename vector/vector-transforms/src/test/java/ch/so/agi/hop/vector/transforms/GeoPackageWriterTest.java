package ch.so.agi.hop.vector.transforms;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver;
import ch.so.agi.hop.vector.core.*;
import ch.so.agi.hop.vector.formats.geopackage.GeoPackageProvider;
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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;

class GeoPackageWriterTest {
  @TempDir Path dir;

  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
  }

  RowMeta fields() {
    var m = new RowMeta();
    m.addValueMeta(new ValueMetaString("name"));
    m.addValueMeta(new ValueMetaGeometry("source_geometry"));
    return m;
  }

  VectorWriter writer(VectorWriterMeta m, java.util.function.Supplier<Object[]> rows) {
    var pm = new PipelineMeta();
    var tm = new TransformMeta("Writer", m);
    pm.addTransform(tm);
    return new VectorWriter(tm, m, new VectorWriterData(), 0, pm, new LocalPipelineEngine(pm)) {
      public Object[] getRow() {
        return rows.get();
      }

      public IRowMeta getInputRowMeta() {
        return fields();
      }
    };
  }

  @Test
  void modesRoundTripAndOldPipelinesDefaultToCreation() throws Exception {
    for (var mode : GeoPackageOptions.WriteMode.values()) {
      var m = new VectorWriterMeta();
      m.setGeoPackageWriteMode(mode.name());
      m.setGeoPackageCreateSpatialIndex(false);
      var copy = new VectorWriterMeta();
      copy.loadXml(
          XmlHandler.loadXmlString("<transform>" + m.getXml() + "</transform>")
              .getDocumentElement(),
          null);
      assertThat(copy.getGeoPackageWriteMode()).isEqualTo(mode.name());
      assertThat(copy.isGeoPackageCreateSpatialIndex()).isFalse();
    }
    var old = new VectorWriterMeta();
    old.loadXml(
        XmlHandler.loadXmlString("<transform><fileName>x.gpkg</fileName></transform>")
            .getDocumentElement(),
        null);
    assertThat(old.getGeoPackageWriteMode()).isEqualTo("CREATE_FILE");
    assertThat(old.isGeoPackageCreateSpatialIndex()).isTrue();
  }

  @Test
  void appendUsesTargetSchemaWithoutFirstGeometryAndResolvesVariables() throws Exception {
    Path file = dir.resolve("test.gpkg");
    var gf = new GeometryFactory(new PrecisionModel(), 2056);
    var provider = new GeoPackageProvider(new GeoToolsCrsDefinitionResolver());
    try (var w =
        provider.create(
            new WriteRequest(
                file,
                "places",
                fields(),
                1,
                gf.createPoint(),
                GeometrySchema.explicit(
                    "POINT", "XY", new GeoToolsCrsDefinitionResolver().resolve(2056)),
                null,
                null))) {
      w.finish();
    }
    var m = new VectorWriterMeta();
    m.setFormat("GEOPACKAGE");
    m.setFileName("${OUTPUT}");
    m.setLayerName("${LAYER}");
    m.setGeometryField("source_geometry");
    m.setGeoPackageWriteMode("APPEND_FEATURES");
    var rows = new ArrayList<Object[]>();
    rows.add(new Object[] {"null", null});
    rows.add(new Object[] {"point", gf.createPoint(new Coordinate(1, 2))});
    var it = rows.iterator();
    var w = writer(m, () -> it.hasNext() ? it.next() : null);
    w.setVariable("OUTPUT", file.toString());
    w.setVariable("LAYER", "places");
    try {
      while (w.processRow()) {}
    } finally {
      w.dispose();
    }
    var empty = writer(m, () -> null);
    empty.setVariable("OUTPUT", file.toString());
    empty.setVariable("LAYER", "places");
    try {
      assertThat(empty.processRow()).isFalse();
    } finally {
      empty.dispose();
    }
    try (var source = provider.open(file, "places", "")) {
      assertThat(source.read()[0]).isEqualTo("null");
      assertThat(source.read()[0]).isEqualTo("point");
      assertThat(source.read()).isNull();
    }
    assertThat(provider.preview(file, "places", fields(), 1))
        .contains("Spatial index: true", "source_geometry");
  }
}
