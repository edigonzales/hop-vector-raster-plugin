package ch.so.agi.hop.vector.transforms;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.hop.vector.core.LayerSchema;
import ch.so.agi.hop.vector.core.VectorFormat;
import ch.so.agi.hop.vector.core.VectorProvider;
import ch.so.agi.hop.vector.formats.shapefile.GeoToolsReference;
import java.nio.file.Path;
import java.util.List;
import org.geotools.api.data.DataStore;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

class VectorReaderSchemaTest {

  @TempDir Path tempDir;

  @Test
  void readsAllGeoPackageLayersAndTheirSchemasWithCrs() throws Exception {
    Path file = tempDir.resolve("multi.gpkg");
    DataStore store = GeoToolsReference.create(file);
    try {
      store.createSchema(featureType("roads", "geom", LineString.class, "name", String.class));
      store.createSchema(featureType("places", "shape", Point.class, "rank", Integer.class));
    } finally {
      store.dispose();
    }

    List<LayerSchema> layers = VectorProviders.get(VectorFormat.GEOPACKAGE).layers(file);

    assertThat(layers)
        .extracting(LayerSchema::name)
        .containsExactlyInAnyOrder("roads", "places");

    LayerSchema roads = VectorProvider.resolveLayer(layers, "roads");
    assertThat(roads.geometryColumn()).isEqualTo("geom");
    assertThat(roads.geometryType()).isEqualTo("LineString");
    assertThat(VectorReaderDialog.formatFieldPreview(roads)).contains("- name (STRING)");

    LayerSchema places = VectorProvider.resolveLayer(layers, "PLACES");
    assertThat(places.geometryColumn()).isEqualTo("shape");
    assertThat(places.geometryType()).isEqualTo("Point");
    assertThat(VectorReaderDialog.formatFieldPreview(places))
        .contains("- rank (INTEGER) length=-1 precision=0");
  }

  @Test
  void formatsReadableFieldPreview() {
    var fields = new org.apache.hop.core.row.RowMeta();
    fields.addValueMeta(new org.apache.hop.core.row.value.ValueMetaString("name"));
    fields.addValueMeta(new org.apache.hop.core.row.value.ValueMetaInteger("rank"));
    var precise = new org.apache.hop.core.row.value.ValueMetaBigNumber("precise");
    precise.setLength(12, 4);
    fields.addValueMeta(precise);
    var layer = new LayerSchema("places", "shape", "Point", 2056, fields);

    assertThat(VectorReaderDialog.formatFieldPreview(layer))
        .contains("Layer: places")
        .contains("Geometry type: Point")
        .contains("Geometry field: shape")
        .contains("- name (STRING)")
        .contains("- rank (INTEGER)")
        .contains("- precise (BIGNUMBER) length=12 precision=4");
  }

  private static SimpleFeatureType featureType(
      String layerName,
      String geometryFieldName,
      Class<?> geometryBinding,
      String attributeName,
      Class<?> attributeBinding)
      throws Exception {
    SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
    builder.setName(layerName);
    builder.setCRS(CRS.decode("EPSG:2056", true));
    builder.add(attributeName, attributeBinding);
    builder.add(geometryFieldName, geometryBinding);
    builder.setDefaultGeometry(geometryFieldName);
    return builder.buildFeatureType();
  }
}
