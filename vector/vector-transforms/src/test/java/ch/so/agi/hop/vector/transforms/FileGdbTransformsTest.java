package ch.so.agi.hop.vector.transforms;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.hop.core.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.transform.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class FileGdbTransformsTest {
  @TempDir Path temp;

  @BeforeAll
  static void init() throws Exception {
    HopEnvironment.init();
  }

  @Test
  void configurationRoundTrips() throws Exception {
    var m = new FileGdbWriterMeta();
    m.setFileName("${OUTPUT}");
    m.setSchemaFile("${SCHEMA}");
    m.setInputs(
        List.of(
            new FileGdbWriterMeta.Input("a", "Left"), new FileGdbWriterMeta.Input("b", "Right")));
    var copy = new FileGdbWriterMeta();
    copy.loadXml(
        XmlHandler.loadXmlString("<transform>" + m.getXml() + "</transform>").getDocumentElement(),
        null);
    assertThat(copy.getXml()).isEqualTo(m.getXml());
    assertThat(copy.getInputs()).hasSize(2);
    var clone = (FileGdbWriterMeta) m.clone();
    clone.getInputs().getFirst().setTransform("Other");
    assertThat(m.getInputs().getFirst().getTransform()).isEqualTo("Left");
    var c = new FileGdbCatalogReaderMeta();
    c.setFileName("${INPUT}");
    c.setMode("DOMAIN_VALUES");
    c.setNameFilter("${DOMAIN}");
    var restored = new FileGdbCatalogReaderMeta();
    restored.loadXml(
        XmlHandler.loadXmlString("<transform>" + c.getXml() + "</transform>").getDocumentElement(),
        null);
    assertThat(restored.getXml()).isEqualTo(c.getXml());
    var v = new VectorWriterMeta();
    v.setFileGdbSchemaFile("${SCHEMA}");
    var vc = new VectorWriterMeta();
    vc.loadXml(
        XmlHandler.loadXmlString("<transform>" + v.getXml() + "</transform>").getDocumentElement(),
        null);
    assertThat(vc.getFileGdbSchemaFile()).isEqualTo("${SCHEMA}");
  }

  private Path singleSchema() throws Exception {
    Path schema = temp.resolve("single.json");
    Files.writeString(
        schema,
        """
      {"schemaVersion":1,"domains":[{"name":"status","type":"CODED","fieldType":"INT32",
        "values":[{"code":"1","label":"One"}]}],
       "datasets":[{"name":"a","kind":"TABLE","fields":[{"name":"id","type":"INT32","domain":"status"}]}]}
      """);
    return schema;
  }

  private RowMeta fields() {
    var fields = new RowMeta();
    fields.addValueMeta(new ValueMetaInteger("id"));
    return fields;
  }

  @Test
  void singleWriterAndCatalogResolveVariablesAndPreserveCodes() throws Exception {
    var m = new VectorWriterMeta();
    m.setFileName("${OUTPUT}");
    m.setFileGdbSchemaFile("${SCHEMA}");
    m.setLayerName("a");
    var pm = new PipelineMeta();
    var tm = new TransformMeta("Writer", m);
    pm.addTransform(tm);
    var w =
        new VectorWriter(
            tm,
            m,
            new VectorWriterData(),
            0,
            pm,
            new org.apache.hop.pipeline.engines.local.LocalPipelineEngine(pm)) {
          boolean sent;

          public Object[] getRow() {
            if (sent) return null;
            sent = true;
            return new Object[] {1L};
          }

          public IRowMeta getInputRowMeta() {
            return fields();
          }
        };
    Path output = temp.resolve("single.gdb");
    w.setVariable("OUTPUT", output.toString());
    w.setVariable("SCHEMA", singleSchema().toString());
    try {
      while (w.processRow()) {}
    } finally {
      w.dispose();
    }
    try (var db = ch.so.agi.filegdb.FileGeodatabase.open(output);
        var table = db.table("a")) {
      assertThat(table.rowCount()).isEqualTo(1);
      assertThat(table.domain("id")).isPresent();
    }
    var c = new FileGdbCatalogReaderMeta();
    c.setFileName("${INPUT}");
    c.setNameFilter("${DOMAIN}");
    c.setMode("DOMAIN_VALUES");
    var ct = new TransformMeta("Catalog", c);
    pm.addTransform(ct);
    var reader =
        new FileGdbCatalogReader(
            ct,
            c,
            new FileGdbCatalogReaderData(),
            0,
            pm,
            new org.apache.hop.pipeline.engines.local.LocalPipelineEngine(pm));
    reader.setVariable("INPUT", output.toString());
    reader.setVariable("DOMAIN", "status");
    var rows = new ArrayList<Object[]>();
    reader.addRowListener(
        new RowAdapter() {
          public void rowWrittenEvent(IRowMeta meta, Object[] row) {
            assertThat(meta.getFieldNames())
                .containsExactly("domain", "code", "description", "field_type");
            rows.add(row);
          }
        });
    try {
      while (reader.processRow()) {}
    } finally {
      reader.dispose();
    }
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst()).containsExactly("status", "1", "One", "INT32");
  }

  @Test
  void rejectsMissingAndDuplicateInputMappingsWithoutPublishing() throws Exception {
    for (var mappings :
        List.of(
            List.<FileGdbWriterMeta.Input>of(),
            List.of(new FileGdbWriterMeta.Input("a", "Missing")),
            List.of(
                new FileGdbWriterMeta.Input("a", "Left"),
                new FileGdbWriterMeta.Input("a", "Left")))) {
      var w = mappedWriter(mappings);
      try {
        assertThatThrownBy(w::processRow).hasMessageContaining("FileGDB export failed");
      } finally {
        w.dispose();
      }
      assertThat(temp.resolve("result.gdb")).doesNotExist();
      try (var paths = Files.list(temp)) {
        assertThat(paths.map(p -> p.getFileName().toString()))
            .noneMatch(n -> n.startsWith(".hop-filegdb-"));
      }
    }
  }

  @Test
  void stopAfterWritingDiscardsStagedDatabase() throws Exception {
    var w = mappedWriter(List.of(new FileGdbWriterMeta.Input("a", "Left")));
    try {
      assertThat(w.processRow()).isTrue();
      assertThat(w.getLinesOutput()).isEqualTo(1);
      w.setStopped(true);
      assertThat(w.processRow()).isFalse();
    } finally {
      w.dispose();
    }
    assertThat(temp.resolve("result.gdb")).doesNotExist();
    try (var paths = Files.list(temp)) {
      assertThat(paths.map(p -> p.getFileName().toString()))
          .noneMatch(n -> n.startsWith(".hop-filegdb-"));
    }
  }

  private FileGdbWriter mappedWriter(List<FileGdbWriterMeta.Input> mappings) throws Exception {
    var m = new FileGdbWriterMeta();
    m.setFileName(temp.resolve("result.gdb").toString());
    m.setSchemaFile(singleSchema().toString());
    m.setInputs(mappings);
    var pm =
        new PipelineMeta() {
          public IRowMeta getTransformFields(
              org.apache.hop.core.variables.IVariables vars, String name) {
            return fields();
          }
        };
    var tm = new TransformMeta("Writer", m);
    pm.addTransform(tm);
    var w =
        new FileGdbWriter(
            tm,
            m,
            new FileGdbWriterData(),
            0,
            pm,
            new org.apache.hop.pipeline.engines.local.LocalPipelineEngine(pm));
    var input = new BlockingRowSet(2);
    input.setThreadNameFromToCopy("Left", 0, "Writer", 0);
    input.putRow(fields(), new Object[] {1L});
    w.addRowSetToInputRowSets(input);
    return w;
  }

  @Test
  @Timeout(20)
  void drainsBranchedInputsWithSmallQueues() throws Exception {
    var fields = new RowMeta();
    fields.addValueMeta(new ValueMetaInteger("id"));
    var schema = temp.resolve("schema.json");
    Files.writeString(
        schema,
        """
      {"schemaVersion":1,"datasets":[
        {"name":"a","kind":"TABLE","fields":[{"name":"id","type":"INT32"}]},
        {"name":"b","kind":"TABLE","fields":[{"name":"id","type":"INT32"}]}],
        "relationships":[{"name":"ab","origin":"a","destination":"b","originKey":"id","foreignKey":"id","cardinality":"ONE_TO_ONE"}]}
      """);
    var m = new FileGdbWriterMeta();
    m.setFileName(temp.resolve("result.gdb").toString());
    m.setSchemaFile(schema.toString());
    m.setInputs(
        List.of(
            new FileGdbWriterMeta.Input("a", "Left"), new FileGdbWriterMeta.Input("b", "Right")));
    var pm =
        new PipelineMeta() {
          @Override
          public IRowMeta getTransformFields(
              org.apache.hop.core.variables.IVariables vars, String name) {
            return fields;
          }
        };
    var tm = new TransformMeta("Writer", m);
    pm.addTransform(tm);
    var p = new org.apache.hop.pipeline.engines.local.LocalPipelineEngine(pm);
    var left = new BlockingRowSet(2);
    left.setThreadNameFromToCopy("Left", 0, "Writer", 0);
    var right = new BlockingRowSet(2);
    right.setThreadNameFromToCopy("Right", 0, "Writer", 0);
    var w = new FileGdbWriter(tm, m, new FileGdbWriterData(), 0, pm, p);
    w.addRowSetToInputRowSets(left);
    w.addRowSetToInputRowSets(right);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<?> producer =
        executor.submit(
            () -> {
              try {
                for (long i = 1; i <= 500; i++) {
                  while (!left.putRowWait(fields, new Object[] {i}, 10, TimeUnit.MILLISECONDS)) {
                    if (Thread.currentThread().isInterrupted()) return;
                  }
                  while (!right.putRowWait(fields, new Object[] {i}, 10, TimeUnit.MILLISECONDS)) {
                    if (Thread.currentThread().isInterrupted()) return;
                  }
                }
              } finally {
                left.setDone();
                right.setDone();
              }
            });
    try {
      while (w.processRow()) {}
      producer.get(5, TimeUnit.SECONDS);
    } finally {
      producer.cancel(true);
      executor.shutdownNow();
      w.dispose();
    }
    try (var db = ch.so.agi.filegdb.FileGeodatabase.open(temp.resolve("result.gdb"))) {
      try (var a = db.table("a");
          var b = db.table("b")) {
        assertThat(a.rowCount()).isEqualTo(500);
        assertThat(b.rowCount()).isEqualTo(500);
      }
      assertThat(db.relationships()).hasSize(1);
    }
  }
}
