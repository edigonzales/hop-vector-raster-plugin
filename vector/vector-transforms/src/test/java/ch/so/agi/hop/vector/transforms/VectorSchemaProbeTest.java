package ch.so.agi.hop.vector.transforms;

import static org.assertj.core.api.Assertions.assertThat;

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

class VectorSchemaProbeTest {

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

    List<VectorSchemaProbe.LayerDefinition> layers = VectorSchemaProbe.readLayers(file);

    assertThat(layers)
        .extracting(VectorSchemaProbe.LayerDefinition::name)
        .containsExactlyInAnyOrder("roads", "places");

    VectorSchemaProbe.LayerDefinition roads = VectorSchemaProbe.resolveLayer(layers, "roads");
    assertThat(roads.geometryFieldName()).isEqualTo("geom");
    assertThat(roads.geometryType()).isEqualTo("LineString");
    assertThat(roads.fields())
        .containsExactly(new VectorSchemaProbe.FieldDefinition("name", "STRING"));

    VectorSchemaProbe.LayerDefinition places = VectorSchemaProbe.resolveLayer(layers, "PLACES");
    assertThat(places.geometryFieldName()).isEqualTo("shape");
    assertThat(places.geometryType()).isEqualTo("Point");
    assertThat(places.fields())
        .containsExactly(new VectorSchemaProbe.FieldDefinition("rank", "INTEGER", -1, 0));
  }

  @Test
  void formatsReadableFieldPreview() {
    VectorSchemaProbe.LayerDefinition layer =
        new VectorSchemaProbe.LayerDefinition(
            "places",
            "shape",
            "Point",
            List.of(
                new VectorSchemaProbe.FieldDefinition("name", "STRING"),
                new VectorSchemaProbe.FieldDefinition("rank", "INTEGER", -1, 0)));

    assertThat(VectorSchemaProbe.formatFieldPreview(layer))
        .contains("Layer: places")
        .contains("Geometry type: Point")
        .contains("Geometry field: shape")
        .contains("- name (STRING)")
        .contains("- rank (INTEGER)");
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
