package ch.so.agi.hop.vector.transforms;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.apache.hop.core.annotations.Transform;
import org.junit.jupiter.api.Test;

class VectorPluginContractTest {

  @Test
  void readerAndWriterUseSharedGeometryClassLoaderGroup() {
    Transform reader = VectorReaderMeta.class.getAnnotation(Transform.class);
    Transform writer = VectorWriterMeta.class.getAnnotation(Transform.class);

    assertThat(reader).isNotNull();
    assertThat(writer).isNotNull();
    assertThat(reader.classLoaderGroup()).isEqualTo("sogeo-geometry");
    assertThat(writer.classLoaderGroup()).isEqualTo("sogeo-geometry");
  }

  @Test
  void readerAndWriterDeclareIcons() {
    Transform reader = VectorReaderMeta.class.getAnnotation(Transform.class);
    Transform writer = VectorWriterMeta.class.getAnnotation(Transform.class);

    assertThat(reader.image()).isEqualTo("ch/so/agi/hop/vector/transforms/icons/vector-reader.svg");
    assertThat(writer.image()).isEqualTo("ch/so/agi/hop/vector/transforms/icons/vector-writer.svg");
  }

  @Test
  void userFacingNamesDoNotExposeGeoTools() {
    Transform reader = VectorReaderMeta.class.getAnnotation(Transform.class);
    Transform writer = VectorWriterMeta.class.getAnnotation(Transform.class);

    assertThat(reader.name()).isEqualTo("Vector Reader");
    assertThat(writer.name()).isEqualTo("Vector Writer");
    assertThat(reader.name()).doesNotContainIgnoringCase("geotools");
    assertThat(writer.name()).doesNotContainIgnoringCase("geotools");
  }

  @Test
  void derivesLayerNameFromOutputFile() {
    assertThat(VectorWriter.defaultLayerName(Path.of("/tmp/parcels.gpkg"))).isEqualTo("parcels");
    assertThat(VectorWriter.defaultLayerName(Path.of("roads.shp"))).isEqualTo("roads");
  }
}
