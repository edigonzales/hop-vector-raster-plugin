package ch.so.agi.hop.vector.formats.filegeodatabase;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.filegdb.*;
import ch.so.agi.filegdb.catalog.*;
import ch.so.agi.filegdb.geometry.*;
import ch.so.agi.filegdb.table.*;
import ch.so.agi.filegdb.write.*;
import ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver;
import ch.so.agi.hop.vector.core.*;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.nio.file.*;
import java.util.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;

class FileGdbAppendTest {
  @TempDir Path temp;
  final GeoToolsCrsDefinitionResolver crs = new GeoToolsCrsDefinitionResolver();
  final GeometryFactory geometry = new GeometryFactory(new PrecisionModel(), 2056);

  Path create() throws Exception {
    Path p = temp.resolve("existing.gdb");
    try (var db = FileGeodatabase.create(p)) {
      db.createDomain(
          new CodedValueDomain(
              "status", FileGdbFieldType.INT32, "Status", List.of(new CodedValue("Active", "1"))));
      try (var w =
          db.createFeatureClass(
              FeatureClassDefinition.builder("buildings")
                  .field(FileGdbField.integer("id"))
                  .field(FileGdbField.integer("status").withDomain("status"))
                  .field(FileGdbField.string("label", 50).withDefaultValue("default"))
                  .geometry(
                      GeometryFieldDefinition.of("Shape", GeometryKind.POINT)
                          .withWkt(crs.resolve(2056).wkt()))
                  .crs(new CrsDefinition(2056, 2056, crs.resolve(2056).wkt()))
                  .build())) {
        w.write(new Object[] {1L, 1L, "old"}, new FileGdbPoint(10, 20));
      }
    }
    return p;
  }

  RowMeta fields() {
    var fields = new RowMeta();
    fields.addValueMeta(new ValueMetaInteger("STATUS"));
    fields.addValueMeta(new ValueMetaGeometry("geometry"));
    fields.addValueMeta(new ValueMetaInteger("ID"));
    return fields;
  }

  FileGdbExportSession.InputOptions append() {
    return new FileGdbExportSession.InputOptions(
        FileGdbExportSession.Action.APPEND_ROWS, "geometry", false);
  }

  @Test
  void mixedCreateAndAppendPreservesDomainAndAddsRelationship() throws Exception {
    Path p = create();
    UUID before;
    try (var db = FileGeodatabase.open(p)) {
      before =
          db.items().stream()
              .filter(i -> i.name().equals("buildings"))
              .findFirst()
              .orElseThrow()
              .uuid();
    }
    var newFields = new RowMeta();
    newFields.addValueMeta(new ValueMetaInteger("building_id"));
    var spec =
        new FileGdbExportSchema.DatasetSpec(
            "entrances",
            "TABLE",
            List.of(
                new FileGdbExportSchema.FieldSpec(
                    null, "building_id", FileGdbFieldType.INT32, false, null, null)),
            null);
    var relationship =
        new FileGdbExportSchema.RelationshipSpec(
            "building_entrances",
            "buildings",
            "entrances",
            "id",
            "building_id",
            RelationshipCardinality.ONE_TO_MANY,
            "Entrances",
            "Building");
    var schema = new FileGdbExportSchema(1, List.of(), List.of(spec), List.of(relationship));
    try (var s =
        new FileGdbExportSession(
            p,
            schema,
            Map.of("buildings", fields(), "entrances", newFields),
            Map.of(
                "buildings",
                append(),
                "entrances",
                new FileGdbExportSession.InputOptions(
                    FileGdbExportSession.Action.CREATE_DATASET, "", false)),
            true,
            crs,
            () -> {})) {
      s.write("buildings", new Object[] {1L, null, 2L});
      s.write("entrances", new Object[] {2L});
      s.write("buildings", new Object[] {1L, geometry.createPoint(new Coordinate(50, 60)), 3L});
      s.finish();
    }
    try (var db = FileGeodatabase.open(p);
        var table = db.table("buildings")) {
      assertThat(table.rowCount()).isEqualTo(3);
      assertThat(table.read(2).get("label")).isEqualTo("default");
      assertThat(table.read(1).get("label")).isEqualTo("old");
      assertThat(db.relationships()).hasSize(1);
      assertThat(
              db.items().stream()
                  .filter(i -> i.name().equals("buildings"))
                  .findFirst()
                  .orElseThrow()
                  .uuid())
          .isEqualTo(before);
      assertThat(table.query(new ch.so.agi.filegdb.geometry.Envelope(49, 59, 51, 61)).rows())
          .hasSize(1);
    }
    assertThat(FileGdbExportSession.preview(p, "buildings", fields(), "geometry"))
        .contains("Spatial index: true", "status", "building_entrances");
  }

  @Test
  void domainFailureRollsBackAlreadyWrittenRows() throws Exception {
    Path p = create();
    try (var s =
        new FileGdbExportSession(
            p,
            null,
            Map.of("buildings", fields()),
            Map.of("buildings", append()),
            true,
            crs,
            () -> {})) {
      s.write("buildings", new Object[] {1L, null, 2L});
      assertThatThrownBy(() -> s.write("buildings", new Object[] {99L, null, 3L}))
          .hasMessageContaining("status")
          .hasMessageContaining("row 2");
      assertThatThrownBy(s::finish).isInstanceOf(IllegalStateException.class);
    }
    try (var db = FileGeodatabase.open(p);
        var table = db.table("buildings")) {
      assertThat(table.rowCount()).isEqualTo(1);
    }
  }

  @Test
  void rejectsUnknownFieldsAndObjectIdsAndCrsMismatch() throws Exception {
    Path p = create();
    for (String extra : List.of("OBJECTID", "unknown")) {
      var rm = fields();
      rm.addValueMeta(new ValueMetaInteger(extra));
      assertThatThrownBy(
              () ->
                  new FileGdbExportSession(
                      p,
                      null,
                      Map.of("buildings", rm),
                      Map.of("buildings", append()),
                      true,
                      crs,
                      () -> {}))
          .hasMessageContaining(extra.equals("OBJECTID") ? "SOURCE_OBJECTID" : "Unknown");
    }
    try (var s =
        new FileGdbExportSession(
            p,
            null,
            Map.of("buildings", fields()),
            Map.of("buildings", append()),
            true,
            crs,
            () -> {})) {
      var point = new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(1, 2));
      assertThatThrownBy(() -> s.write("buildings", new Object[] {1L, point, 2L}))
          .hasMessageContaining("CRS");
    }
  }

  @Test
  void cancellationBeforeCommitPreservesOriginalAndAllowsRetry() throws Exception {
    Path p = create();
    var stopped = new java.util.concurrent.atomic.AtomicBoolean();
    try (var session =
        new FileGdbExportSession(
            p,
            null,
            Map.of("buildings", fields()),
            Map.of("buildings", append()),
            true,
            crs,
            () -> {
              if (stopped.get()) throw new java.util.concurrent.CancellationException("Stopped");
            })) {
      session.write(
          "buildings", new Object[] {1L, geometry.createPoint(new Coordinate(100, 200)), 2L});
      stopped.set(true);
      assertThatThrownBy(session::finish)
          .isInstanceOf(java.util.concurrent.CancellationException.class);
    }
    try (var db = FileGeodatabase.open(p);
        var table = db.table("buildings")) {
      assertThat(table.rowCount()).isEqualTo(1);
    }
    try (var retry =
        new FileGdbExportSession(
            p,
            null,
            Map.of("buildings", fields()),
            Map.of("buildings", append()),
            true,
            crs,
            () -> {})) {
      retry.finish();
    }
  }

  @Test
  void duplicateDatasetNamesAreRejectedWithoutOpeningAWorkingCopy() throws Exception {
    Path p = create();
    assertThatThrownBy(
            () ->
                new FileGdbExportSession(
                    p,
                    null,
                    Map.of("buildings", fields(), "BUILDINGS", fields()),
                    Map.of("buildings", append(), "BUILDINGS", append()),
                    true,
                    crs,
                    () -> {}))
        .hasMessageContaining("Duplicate dataset mapping");
  }

  @Test
  void providerAppendsWithoutSampleAndCreatesAdditionalLayer() throws Exception {
    Path p = create();
    var provider = new FileGeodatabaseProvider(crs);
    var opts =
        new FileGeodatabaseOptions(
            "AUTO",
            null,
            null,
            null,
            null,
            false,
            null,
            FileGeodatabaseOptions.WriteMode.APPEND_ROWS);
    try (var sink =
        provider.create(new WriteRequest(p, "buildings", fields(), 1, null, null, opts, null))) {
      sink.write(new Object[] {1L, null, 2L});
      sink.finish();
    }
    var createFields = new RowMeta();
    createFields.addValueMeta(new ValueMetaGeometry("geom"));
    var add =
        new FileGeodatabaseOptions(
            "AUTO",
            null,
            null,
            null,
            null,
            true,
            null,
            FileGeodatabaseOptions.WriteMode.ADD_DATASET);
    try (var sink =
        provider.create(
            new WriteRequest(
                p, "other", createFields, 0, geometry.createPoint(), null, add, null))) {
      sink.finish();
    }
    try (var db = FileGeodatabase.open(p);
        var table = db.table("buildings")) {
      assertThat(table.rowCount()).isEqualTo(2);
      assertThat(db.dataset("other")).isPresent();
    }
  }
}
