package ch.so.agi.hop.vector.formats.filegeodatabase;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver;
import ch.so.agi.hop.vector.core.ReadRequest;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.nio.file.*;
import java.util.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;

class FileGdbExportTest {
  @TempDir Path temp;

  private Path example() {
    return Path.of("../../../docs/examples/filegdb/buildings.json");
  }

  private Map<String, IRowMeta> inputs() {
    var b = new RowMeta();
    b.addValueMeta(new ValueMetaInteger("id"));
    b.addValueMeta(new ValueMetaInteger("status"));
    b.addValueMeta(new ValueMetaNumber("height"));
    b.addValueMeta(new ValueMetaGeometry("geometry"));
    var e = new RowMeta();
    e.addValueMeta(new ValueMetaInteger("id"));
    e.addValueMeta(new ValueMetaInteger("building_id"));
    e.addValueMeta(new ValueMetaString("label"));
    return Map.of("buildings", b, "entrances", e);
  }

  @Test
  void exportsDomainsRelationshipsAndPlainTables() throws Exception {
    var schema = FileGdbExportSchema.read(example());
    var path = temp.resolve("out.gdb");
    try (var session =
        new FileGdbExportSession(path, schema, inputs(), new GeoToolsCrsDefinitionResolver())) {
      var point =
          new GeometryFactory(new PrecisionModel(), 2056)
              .createPoint(new Coordinate(2600000, 1200000));
      session.write("entrances", new Object[] {1L, 1L, "A & B"});
      session.write("buildings", new Object[] {1L, 2L, 20.5, point});
      session.finish();
    }
    try (var db = FileGeodatabase.open(path)) {
      assertThat(db.domains()).hasSize(2);
      assertThat(db.relationships()).hasSize(1);
      try (var table = db.table("entrances")) {
        assertThat(table.rowCount()).isEqualTo(1);
      }
    }
    assertThat(FileGdbCatalog.read(path, FileGdbCatalog.Mode.DOMAIN_VALUES, "status")).hasSize(2);
    assertThat(FileGdbCatalog.read(path, FileGdbCatalog.Mode.RELATIONSHIPS, "missing")).isEmpty();
    var provider = new FileGeodatabaseProvider(new GeoToolsCrsDefinitionResolver());
    try (var source = provider.open(new ReadRequest(path, "entrances", ""))) {
      assertThat(source.schema().geometry()).isNull();
      assertThat(source.read()).contains("A & B");
      assertThat(source.read()).isNull();
    }
    String destination = System.getenv("FILEGDB_TEST_EXPORT");
    if (destination != null) {
      try (var paths = Files.walk(path)) {
        for (var p : paths.toList()) {
          var target = Path.of(destination).resolve(path.relativize(p));
          if (Files.isDirectory(p)) Files.createDirectories(target);
          else Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  @Test
  void createsEmptyDatasetsAndAbortsInvalidRows() throws Exception {
    var schema = FileGdbExportSchema.read(example());
    var path = temp.resolve("empty.gdb");
    try (var s =
        new FileGdbExportSession(path, schema, inputs(), new GeoToolsCrsDefinitionResolver())) {
      s.finish();
    }
    try (var db = FileGeodatabase.open(path)) {
      assertThat(db.datasets()).hasSize(2);
    }
    var bad = temp.resolve("bad.gdb");
    try (var s =
        new FileGdbExportSession(bad, schema, inputs(), new GeoToolsCrsDefinitionResolver())) {
      assertThatThrownBy(() -> s.write("buildings", new Object[] {1L, 99L, 20.0, null}))
          .hasMessageContaining("Dataset buildings, row 1: Field status");
      assertThatThrownBy(s::finish).isInstanceOf(IllegalStateException.class);
    }
    assertThat(bad).doesNotExist();
    try (var files = Files.list(temp)) {
      assertThat(files.map(p -> p.getFileName().toString()).toList())
          .noneMatch(n -> n.startsWith(".hop-filegdb-"));
    }
    assertThatThrownBy(
            () ->
                new FileGdbExportSession(
                    path, schema, inputs(), new GeoToolsCrsDefinitionResolver()))
        .hasMessageContaining("already exists");
  }

  @Test
  void rejectsInvalidSchemaAndTypes() throws Exception {
    String original = Files.readString(example());
    for (String changed :
        List.of(
            original.replace("\"schemaVersion\": 1", "\"schemaVersion\": 9"),
            original.replace("\"schemaVersion\": 1", "\"unknown\": true, \"schemaVersion\": 1"),
            original.replace("ONE_TO_MANY", "MANY_TO_MANY"),
            original.replace("\"originKey\": \"id\"", "\"originKey\": \"OBJECTID\""),
            original.replace("\"code\": \"1\"", "\"code\": \"2147483648\""))) {
      var f = temp.resolve("bad.json");
      Files.writeString(f, changed);
      assertThatThrownBy(() -> FileGdbExportSchema.read(f))
          .isInstanceOf(IllegalArgumentException.class);
    }
    for (Object[] row :
        List.of(
            new Object[] {1L, null, 1.0, null},
            new Object[] {1L, 1L, 1001.0, null},
            new Object[] {1.5, 1L, 1.0, null},
            new Object[] {1L, "1", 1.0, null})) {
      var target = temp.resolve(UUID.randomUUID() + ".gdb");
      try (var s =
          new FileGdbExportSession(
              target,
              FileGdbExportSchema.read(example()),
              inputs(),
              new GeoToolsCrsDefinitionResolver())) {
        assertThatThrownBy(() -> s.write("buildings", row))
            .isInstanceOf(IllegalArgumentException.class);
      }
      assertThat(target).doesNotExist();
    }
  }
}
