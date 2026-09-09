package ch.so.agi.hop.vector.formats.shapefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBoolean;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.row.value.ValueMetaString;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.FeatureWriter;
import org.geotools.api.data.Transaction;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

class GeoToolsVectorSupportTest {

  private final GeometryFactory geometryFactory = new GeometryFactory();

  @TempDir Path tempDir;

  @Test
  void detectsMvpFormats() {
    assertThat(GeoToolsReference.detectFormat(Path.of("roads.SHP")))
        .isEqualTo(GeoToolsReference.VectorFormat.SHAPEFILE);
    assertThat(GeoToolsReference.detectFormat(Path.of("roads.gpkg")))
        .isEqualTo(GeoToolsReference.VectorFormat.GEOPACKAGE);
    assertThatThrownBy(() -> GeoToolsReference.detectFormat(Path.of("roads.geojson")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(".shp and .gpkg");
  }

  @Test
  void mapsHopTypesToGeoToolsBindings() {
    assertThat(GeoToolsReference.toJavaBinding(new ValueMetaString("s"))).isEqualTo(String.class);
    assertThat(GeoToolsReference.toJavaBinding(new ValueMetaInteger("i"))).isEqualTo(Long.class);
    assertThat(GeoToolsReference.toJavaBinding(new ValueMetaNumber("n"))).isEqualTo(Double.class);
    assertThat(GeoToolsReference.toJavaBinding(new ValueMetaBoolean("b"))).isEqualTo(Boolean.class);
  }

  @Test
  void blankGeometryOutputFieldPreservesSourceGeometryName() {
    SimpleFeatureType type = featureType("places", "source_geom");

    RowMeta rowMeta = GeoToolsReference.toHopRowMeta(type, "");

    assertThat(rowMeta.getValueMeta(rowMeta.size() - 1).getName()).isEqualTo("source_geom");
  }

  @Test
  void explicitGeometryOutputFieldOverridesSourceGeometryName() {
    SimpleFeatureType type = featureType("places", "source_geom");

    RowMeta rowMeta = GeoToolsReference.toHopRowMeta(type, "geometry");

    assertThat(rowMeta.getValueMeta(rowMeta.size() - 1).getName()).isEqualTo("geometry");
  }

  @Test
  void createsAndReadsGeoPackage() throws Exception {
    Path file = tempDir.resolve("places.gpkg");
    writeFixture(file);
    assertFixture(file);
  }

  @Test
  void createsAndReadsShapefile() throws Exception {
    Path file = tempDir.resolve("places.shp");
    writeFixture(file);
    assertFixture(file);
  }

  private SimpleFeatureType featureType(String layerName, String geometryFieldName) {
    SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
    builder.setName(layerName);
    builder.add("name", String.class);
    builder.add(geometryFieldName, Point.class);
    builder.setDefaultGeometry(geometryFieldName);
    return builder.buildFeatureType();
  }

  private void writeFixture(Path file) throws Exception {
    SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
    builder.setName("places");
    builder.setCRS(org.geotools.referencing.CRS.decode("EPSG:2056", true));
    builder.add("name", String.class);
    builder.add("rank", Integer.class);
    builder.add("geometry", Point.class);
    builder.setDefaultGeometry("geometry");
    SimpleFeatureType type = builder.buildFeatureType();

    DataStore store = GeoToolsReference.create(file);
    Transaction transaction = new DefaultTransaction("fixture");
    FeatureWriter<SimpleFeatureType, SimpleFeature> writer = null;
    try {
      store.createSchema(type);
      String actualTypeName = store.getTypeNames()[0];
      writer = store.getFeatureWriterAppend(actualTypeName, transaction);
      writeFeature(writer, "A", 1, 2600000, 1200000);
      writeFeature(writer, "B", 2, 2600100, 1200100);
      writer.close();
      writer = null;
      transaction.commit();
    } finally {
      if (writer != null) {
        writer.close();
      }
      transaction.close();
      store.dispose();
    }
  }

  private void writeFeature(
      FeatureWriter<SimpleFeatureType, SimpleFeature> writer,
      String name,
      int rank,
      double x,
      double y)
      throws Exception {
    SimpleFeature feature = writer.next();
    feature.setAttribute("name", name);
    feature.setAttribute("rank", rank);
    Point point = geometryFactory.createPoint(new Coordinate(x, y));
    point.setSRID(2056);
    feature.setDefaultGeometry(point);
    writer.write();
  }

  private void assertFixture(Path file) throws Exception {
    ch.so.agi.hop.vector.core.VectorProvider provider =
        file.toString().endsWith(".gpkg")
            ? new ch.so.agi.hop.vector.formats.geopackage.GeoPackageProvider(
                new ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver())
            : new ShapefileProvider();
    try (var source = provider.open(file, "", "")) {
      int read = 0;
      Object[] row;
      while ((row = source.read()) != null) {
        assertThat(row[0]).isIn("A", "B");
        assertThat(row[1]).isInstanceOf(Long.class);
        assertThat(row[2]).isInstanceOf(Point.class);
        read++;
      }
      assertThat(read).isEqualTo(2);
    }
    DataStore store = GeoToolsReference.open(file);
    try {
      String typeName = GeoToolsReference.resolveTypeName(store, "");
      assertThat(typeName).isNotBlank();
      assertThat(store.getSchema(typeName).getGeometryDescriptor()).isNotNull();

      int count = 0;
      try (SimpleFeatureIterator iterator =
          store.getFeatureSource(typeName).getFeatures().features()) {
        while (iterator.hasNext()) {
          SimpleFeature feature = iterator.next();
          assertThat(feature.getDefaultGeometry()).isInstanceOf(Point.class);
          assertThat(feature.getAttribute("name")).isIn("A", "B");
          count++;
        }
      }
      assertThat(count).isEqualTo(2);
    } finally {
      store.dispose();
    }
  }
}
