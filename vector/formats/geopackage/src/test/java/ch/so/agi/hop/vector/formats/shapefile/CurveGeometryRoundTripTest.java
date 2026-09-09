package ch.so.agi.hop.vector.formats.shapefile;

import static org.assertj.core.api.Assertions.assertThat;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import com.atolcd.hop.gis.geometry.curve.CircularString;
import com.atolcd.hop.gis.geometry.curve.CompoundCurve;
import com.atolcd.hop.gis.geometry.curve.CurveGeometrySupport;
import com.atolcd.hop.gis.geometry.curve.CurvePolygon;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaString;
import org.geotools.api.data.DataStore;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

class CurveGeometryRoundTripTest {

  private final GeometryFactory geometryFactory = new GeometryFactory();

  @TempDir Path tempDir;

  @Test
  void preservesCircularStringThroughGeoPackage() throws Exception {
    CircularString curve =
        new CircularString(
            new Coordinate[] {
              new Coordinate(2600000, 1200000),
              new Coordinate(2600050, 1200050),
              new Coordinate(2600100, 1200000)
            },
            geometryFactory);
    curve.setSRID(2056);

    Geometry actual = roundTripCurve("circular", "CIRCULARSTRING", curve);

    assertThat(actual).isInstanceOf(CircularString.class);
    assertThat(CurveGeometrySupport.writeWkt(actual))
        .isEqualTo(CurveGeometrySupport.writeWkt(curve));
    assertThat(actual.getSRID()).isEqualTo(2056);
  }

  @Test
  void preservesCompoundCurveThroughGeoPackage() throws Exception {
    LineString first =
        geometryFactory.createLineString(
            new Coordinate[] {new Coordinate(2600000, 1200000), new Coordinate(2600050, 1200000)});
    CircularString arc =
        new CircularString(
            new Coordinate[] {
              new Coordinate(2600050, 1200000),
              new Coordinate(2600075, 1200025),
              new Coordinate(2600100, 1200000)
            },
            geometryFactory);
    LineString last =
        geometryFactory.createLineString(
            new Coordinate[] {new Coordinate(2600100, 1200000), new Coordinate(2600150, 1200000)});
    CompoundCurve curve = new CompoundCurve(List.of(first, arc, last), geometryFactory);
    curve.setSRID(2056);

    Geometry actual = roundTripCurve("compound", "COMPOUNDCURVE", curve);

    assertThat(actual).isInstanceOf(CompoundCurve.class);
    assertThat(CurveGeometrySupport.writeWkt(actual))
        .isEqualTo(CurveGeometrySupport.writeWkt(curve));
    assertThat(actual.getSRID()).isEqualTo(2056);
  }

  @Test
  void preservesCurvePolygonThroughGeoPackage() throws Exception {
    CircularString ring =
        new CircularString(
            new Coordinate[] {
              new Coordinate(2600000, 1200000),
              new Coordinate(2600050, 1200050),
              new Coordinate(2600100, 1200000),
              new Coordinate(2600050, 1199950),
              new Coordinate(2600000, 1200000)
            },
            geometryFactory);
    CurvePolygon polygon = new CurvePolygon(List.of(ring), geometryFactory);
    polygon.setSRID(2056);

    Geometry actual = roundTripCurve("curvepolygon", "CURVEPOLYGON", polygon);

    assertThat(actual).isInstanceOf(CurvePolygon.class);
    assertThat(CurveGeometrySupport.writeWkt(actual))
        .isEqualTo(CurveGeometrySupport.writeWkt(polygon));
    assertThat(actual.getSRID()).isEqualTo(2056);
  }

  @Test
  void writesOrdinaryGeometryThroughDirectGeoPackagePath() throws Exception {
    Point point = geometryFactory.createPoint(new Coordinate(2600000, 1200000));
    point.setSRID(2056);

    Path file = tempDir.resolve("point.gpkg");
    RowMeta rowMeta = rowMeta();
    String typeName = "curves";
    try (var writer =
        new ch.so.agi.hop.vector.formats.geopackage.GeoPackageProvider(
                new ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver())
            .create(
                new ch.so.agi.hop.vector.core.WriteRequest(
                    file,
                    typeName,
                    rowMeta,
                    1,
                    point,
                    null,
                    new ch.so.agi.hop.vector.core.FormatOptions.None(),
                    ch.so.agi.hop.vector.core.Diagnostics.NONE))) {
      writer.write(new Object[] {"point", point});
      writer.finish();
    }

    DataStore store = GeoToolsReference.open(file);
    try {
      try (SimpleFeatureIterator iterator =
          store.getFeatureSource(typeName).getFeatures().features()) {
        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next().getDefaultGeometry()).isInstanceOf(Point.class);
      }
    } finally {
      store.dispose();
    }
  }

  @Test
  void preservesMultiCurveAndMultiSurfaceThroughGeoPackage() throws Exception {
    CircularString arc =
        new CircularString(
            new Coordinate[] {new Coordinate(0, 0), new Coordinate(5, 5), new Coordinate(10, 0)},
            geometryFactory);
    var multiCurve =
        new com.atolcd.hop.gis.geometry.curve.MultiCurve(List.of(arc), geometryFactory);
    multiCurve.setSRID(2056);
    Geometry actual = roundTripCurve("multicurve", "MULTICURVE", multiCurve);
    assertThat(actual).isInstanceOf(com.atolcd.hop.gis.geometry.curve.MultiCurve.class);
    CircularString ring =
        new CircularString(
            new Coordinate[] {
              new Coordinate(0, 0),
              new Coordinate(5, 5),
              new Coordinate(10, 0),
              new Coordinate(5, -5),
              new Coordinate(0, 0)
            },
            geometryFactory);
    CurvePolygon polygon = new CurvePolygon(List.of(ring), geometryFactory);
    var multiSurface =
        new com.atolcd.hop.gis.geometry.curve.MultiSurface(List.of(polygon), geometryFactory);
    multiSurface.setSRID(2056);
    actual = roundTripCurve("multisurface", "MULTISURFACE", multiSurface);
    assertThat(actual).isInstanceOf(com.atolcd.hop.gis.geometry.curve.MultiSurface.class);
  }

  @Test
  void explicitlyLinearizesCurvesForShapefile() {
    CircularString curve =
        new CircularString(
            new Coordinate[] {new Coordinate(0, 0), new Coordinate(5, 5), new Coordinate(10, 0)},
            geometryFactory);
    curve.setSRID(2056);

    Geometry linearized = CurveGeometryAdapter.toShapefileGeometry(curve);

    assertThat(linearized).isInstanceOf(LineString.class);
    assertThat(linearized).isNotInstanceOf(CircularString.class);
    assertThat(linearized.getNumPoints()).isGreaterThan(3);
    assertThat(linearized.getSRID()).isEqualTo(2056);
  }

  private Geometry roundTripCurve(String fileStem, String geometryTypeName, Geometry geometry)
      throws Exception {
    Path file = tempDir.resolve(fileStem + ".gpkg");
    RowMeta rowMeta = rowMeta();
    String typeName = "curves";
    var provider =
        new ch.so.agi.hop.vector.formats.geopackage.GeoPackageProvider(
            new ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver());
    try (var writer =
        provider.create(
            new ch.so.agi.hop.vector.core.WriteRequest(
                file,
                typeName,
                rowMeta,
                1,
                geometry,
                null,
                new ch.so.agi.hop.vector.core.FormatOptions.None(),
                ch.so.agi.hop.vector.core.Diagnostics.NONE))) {
      writer.write(new Object[] {fileStem, geometry});
      writer.finish();
    }
    try (var reader = provider.open(file, typeName, "geometry")) {
      Geometry nativeGeometry = (Geometry) reader.read()[1];
      assertThat(CurveGeometrySupport.writeWkt(nativeGeometry))
          .isEqualTo(CurveGeometrySupport.writeWkt(geometry));
      assertThat(nativeGeometry.getSRID()).isEqualTo(2056);
    }

    assertCurveMetadata(file, typeName, geometryTypeName);

    DataStore store = GeoToolsReference.open(file);
    try {
      try (SimpleFeatureIterator iterator =
          store.getFeatureSource(typeName).getFeatures().features()) {
        assertThat(iterator.hasNext()).isTrue();
        SimpleFeature feature = iterator.next();
        Geometry geoToolsGeometry = (Geometry) feature.getDefaultGeometry();
        assertThat(geoToolsGeometry.getClass().getName()).startsWith("org.geotools.geometry.jts.");
        Geometry hopGeometry = CurveGeometryAdapter.toHopGeometry(geoToolsGeometry);
        assertThat(iterator.hasNext()).isFalse();
        return hopGeometry;
      }
    } finally {
      store.dispose();
    }
  }

  private String createSchema(Path file, RowMeta rowMeta, Geometry sampleGeometry)
      throws Exception {
    SimpleFeatureType type =
        GeoToolsReference.buildFeatureType("curves", rowMeta, 1, sampleGeometry);
    DataStore store = GeoToolsReference.create(file);
    try {
      store.createSchema(type);
      return store.getTypeNames()[0];
    } finally {
      store.dispose();
    }
  }

  private RowMeta rowMeta() {
    RowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("name"));
    rowMeta.addValueMeta(new ValueMetaGeometry("geometry"));
    return rowMeta;
  }

  private void assertCurveMetadata(Path file, String layerName, String geometryTypeName)
      throws Exception {
    String database = file.toAbsolutePath().normalize().toString().replace('\\', '/');
    try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
      try (PreparedStatement statement =
          connection.prepareStatement(
              "SELECT geometry_type_name FROM gpkg_geometry_columns WHERE table_name = ? AND"
                  + " column_name = 'geometry'")) {
        statement.setString(1, layerName);
        try (ResultSet result = statement.executeQuery()) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString(1)).isEqualTo(geometryTypeName);
        }
      }

      try (PreparedStatement statement =
          connection.prepareStatement(
              "SELECT scope FROM gpkg_extensions WHERE table_name = ? AND column_name = 'geometry'"
                  + " AND extension_name = ?")) {
        statement.setString(1, layerName);
        statement.setString(2, "gpkg_geom_" + geometryTypeName);
        try (ResultSet result = statement.executeQuery()) {
          assertThat(result.next()).isTrue();
          assertThat(result.getString(1)).isEqualTo("read-write");
        }
      }
    }
  }
}
