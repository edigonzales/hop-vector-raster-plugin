package ch.so.agi.hop.vector.formats.filegeodatabase;

import static org.assertj.core.api.Assertions.assertThat;

import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.catalog.CrsDefinition;
import ch.so.agi.filegdb.geometry.FileGdbPart;
import ch.so.agi.filegdb.geometry.FileGdbPoint;
import ch.so.agi.filegdb.geometry.FileGdbPolygon;
import ch.so.agi.filegdb.geometry.GeometryFieldDefinition;
import ch.so.agi.filegdb.geometry.GeometryKind;
import ch.so.agi.filegdb.table.FileGdbField;
import ch.so.agi.filegdb.write.FeatureClassDefinition;
import ch.so.agi.filegdb.write.GdbFeatureWriter;
import ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver;
import ch.so.agi.hop.vector.core.CrsDefinitionResolver;
import ch.so.agi.hop.vector.core.Diagnostics;
import ch.so.agi.hop.vector.core.FormatOptions;
import ch.so.agi.hop.vector.core.GeometrySchema;
import ch.so.agi.hop.vector.core.LayerSchema;
import ch.so.agi.hop.vector.core.Ordinate;
import ch.so.agi.hop.vector.core.ReadRequest;
import ch.so.agi.hop.vector.core.VectorSink;
import ch.so.agi.hop.vector.core.VectorSource;
import ch.so.agi.hop.vector.core.WriteRequest;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

class FileGeodatabaseProviderTest {

  private final FileGeodatabaseProvider provider =
      new FileGeodatabaseProvider(new GeoToolsCrsDefinitionResolver());
  private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 2056);

  @Test
  void readsLayersAndFeatures(@TempDir Path directory) throws Exception {
    Path gdb = directory.resolve("source.gdb");
    writeFixture(gdb);

    List<LayerSchema> layers =
        provider.layers(new ReadRequest(gdb, "", "", "", new FormatOptions.None(), Diagnostics.NONE));
    assertThat(layers).hasSize(1);
    LayerSchema layer = layers.get(0);
    assertThat(layer.name()).isEqualTo("roads");
    assertThat(layer.geometryType()).isEqualTo("Polygon");
    assertThat(layer.srid()).isEqualTo(2056);
    assertThat(layer.rowMeta().getValueMetaList())
        .extracting(org.apache.hop.core.row.IValueMeta::getName)
        .containsExactly("OBJECTID", "name", "lanes", "shape");

    try (VectorSource source =
        provider.open(new ReadRequest(gdb, "roads", "shape", "", new FormatOptions.None(), Diagnostics.NONE))) {
      Object[] first = source.read();
      assertThat(first).isNotNull();
      assertThat(first[1]).isEqualTo("A1");
      assertThat(first[2]).isEqualTo(2L);
      assertThat(first[3]).isInstanceOf(org.locationtech.jts.geom.Polygon.class);
      assertThat(((org.locationtech.jts.geom.Geometry) first[3]).getSRID()).isEqualTo(2056);
      Object[] second = source.read();
      assertThat(second[1]).isEqualTo("B2");
      assertThat(second[3]).isNull();
      assertThat(source.read()).isNull();
    }
  }

  @Test
  void writesFileGeodatabase(@TempDir Path directory) throws Exception {
    Path output = directory.resolve("written.gdb");
    CrsDefinitionResolver.Definition crs = new GeoToolsCrsDefinitionResolver().resolve(2056);

    IRowMeta rowMeta = new RowMeta();
    rowMeta.addValueMeta(new ValueMetaString("name"));
    rowMeta.addValueMeta(new ValueMetaInteger("lanes"));
    rowMeta.addValueMeta(new ValueMetaGeometry("shape"));
    Polygon sample = square(2600000, 1200000, 100);
    GeometrySchema schema =
        new GeometrySchema("Polygon", Ordinate.ABSENT, Ordinate.ABSENT, crs);
    WriteRequest request =
        new WriteRequest(
            output, "roads", rowMeta, 2, sample, schema, new FormatOptions.None(), Diagnostics.NONE);

    try (VectorSink sink = provider.create(request)) {
      assertThat(sink.write(new Object[] {"A1", 2L, square(2600000, 1200000, 100)})).isTrue();
      assertThat(sink.write(new Object[] {"B2", null, null})).isTrue();
      sink.finish();
    }

    assertThat(output).exists();
    try (FileGeodatabase database = FileGeodatabase.open(output);
        var table = database.featureClass("roads")) {
      assertThat(table.rowCount()).isEqualTo(2);
      assertThat(table.crs().effectiveWkid()).isEqualTo(2056);
      assertThat(table.read(1).get("name")).isEqualTo("A1");
      assertThat(table.read(1).get("lanes")).isEqualTo(2L);
      assertThat(table.read(2).get("lanes")).isNull();
      assertThat(table.read(2).geometry()).isNull();
    }

    try (var entries = Files.list(directory)) {
      assertThat(entries.map(path -> path.getFileName().toString()))
          .noneMatch(name -> name.startsWith(".hop-filegdb-"));
    }
  }

  private void writeFixture(Path gdb) throws Exception {
    try (FileGeodatabase database = FileGeodatabase.create(gdb)) {
      FeatureClassDefinition definition =
          FeatureClassDefinition.builder("roads")
              .field(FileGdbField.string("name", 255).asRequired())
              .field(FileGdbField.integer("lanes").asNullable())
              .geometry(GeometryFieldDefinition.of("shape", GeometryKind.POLYGON))
              .crs(new CrsDefinition(2056, 2056, ""))
              .build();
      try (GdbFeatureWriter writer = database.createFeatureClass(definition)) {
        writer.write(new Object[] {"A1", 2L}, fileGdbSquare(2600000, 1200000, 100));
        writer.write(new Object[] {"B2", null}, null);
      }
    }
  }

  private Polygon square(double x, double y, double size) {
    return geometryFactory.createPolygon(
        new Coordinate[] {
          new Coordinate(x, y),
          new Coordinate(x + size, y),
          new Coordinate(x + size, y + size),
          new Coordinate(x, y + size),
          new Coordinate(x, y)
        });
  }

  private static FileGdbPolygon fileGdbSquare(double x, double y, double size) {
    List<FileGdbPoint> ring =
        List.of(
            new FileGdbPoint(x, y),
            new FileGdbPoint(x + size, y),
            new FileGdbPoint(x + size, y + size),
            new FileGdbPoint(x, y + size),
            new FileGdbPoint(x, y));
    return new FileGdbPolygon(List.of(new FileGdbPart(ring)));
  }
}
