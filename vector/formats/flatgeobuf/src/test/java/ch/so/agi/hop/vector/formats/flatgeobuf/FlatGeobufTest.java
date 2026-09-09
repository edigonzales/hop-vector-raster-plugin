package ch.so.agi.hop.vector.formats.flatgeobuf;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.vector.core.*;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKTReader;
import org.wololo.flatgeobuf.*;
import org.wololo.flatgeobuf.generated.*;

class FlatGeobufTest {
  @TempDir Path dir;

  WriteRequest request(String type, String dim, FlatGeobufOptions o, Diagnostics d) {
    var rm = new RowMeta();
    rm.addValueMeta(new ValueMetaInteger("id"));
    rm.addValueMeta(new ValueMetaGeometry("geometry"));
    return new WriteRequest(
        dir.resolve("out.fgb"),
        "test",
        rm,
        1,
        null,
        GeometrySchema.explicit(
            type, dim, new CrsDefinitionResolver.Definition(2056, "LV95", "EPSG", 2056, "")),
        o,
        d);
  }

  record FileData(ByteBuffer buffer, Header header, int treeOffset, int featuresOffset) {}

  FileData read() throws Exception {
    var b =
        ByteBuffer.wrap(Files.readAllBytes(dir.resolve("out.fgb"))).order(ByteOrder.LITTLE_ENDIAN);
    assertThat(b.get(0)).isEqualTo((byte) 'f');
    assertThat(b.get(1)).isEqualTo((byte) 'g');
    int length = b.getInt(8);
    b.position(12);
    Header h = Header.getRootAsHeader(b.slice().order(ByteOrder.LITTLE_ENDIAN));
    int tree = 12 + length;
    int feature =
        tree
            + (h.indexNodeSize() == 0
                ? 0
                : (int) PackedRTree.calcSize((int) h.featuresCount(), h.indexNodeSize()));
    return new FileData(b, h, tree, feature);
  }

  Feature feature(FileData data, long offset) {
    var b = data.buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
    b.position(Math.toIntExact(data.featuresOffset + offset + 4));
    return Feature.getRootAsFeature(b.slice().order(ByteOrder.LITTLE_ENDIAN));
  }

  @Test
  void dimensionsAndFamilies() throws Exception {
    for (String dim : List.of("XY", "XYZ", "XYM", "XYZM")) {
      String suffix = dim.substring(2),
          c = "1 2" + (dim.contains("Z") ? " 3" : "") + (dim.contains("M") ? " 4" : "");
      for (String text :
          List.of(
              "POINT (" + c + ")",
              "MULTIPOINT ((" + c + "))",
              "LINESTRING (" + c + "," + c + ")",
              "MULTILINESTRING ((" + c + "," + c + "))",
              "POLYGON ((" + c + "," + c + "," + c + "," + c + "))",
              "MULTIPOLYGON (((" + c + "," + c + "," + c + "," + c + ")))")) {
        var g = new WKTReader().read(text.replace(" (", " " + suffix + " ("));
        g.setSRID(2056);
        try (var sink =
            new FlatGeobufProvider()
                .create(
                    request(
                        g.getGeometryType(),
                        dim,
                        new FlatGeobufOptions(true, false, true),
                        Diagnostics.NONE))) {
          sink.write(new Object[] {Long.MAX_VALUE, g});
          sink.finish();
        }
        var f = read();
        assertThat(f.header.featuresCount()).isEqualTo(1);
        assertThat(f.header.hasZ()).isEqualTo(dim.contains("Z"));
        assertThat(f.header.hasM()).isEqualTo(dim.contains("M"));
        var geo = feature(f, 0).geometry();
        if (g instanceof MultiPolygon) geo = geo.parts(0);
        assertThat(geo.xy(0)).isEqualTo(1);
        assertThat(geo.zLength()).isEqualTo(dim.contains("Z") ? geo.xyLength() / 2 : 0);
        assertThat(geo.mLength()).isEqualTo(dim.contains("M") ? geo.xyLength() / 2 : 0);
        if (dim.contains("M")) assertThat(geo.m(0)).isEqualTo(4);
        assertThat(PackedRTree.search(f.buffer, f.treeOffset, 1, 16, new Envelope(0, 3, 0, 3)))
            .hasSize(1);
      }
    }
  }

  @Test
  void externalSortAndIndexQueries() throws Exception {
    var r = request("POINT", "XY", new FlatGeobufOptions(true, false, false), Diagnostics.NONE);
    // Tiny budget forces >64 runs and multi-pass merging.
    try (var sink = new FlatGeobufProvider(192 * 5).create(r)) {
      var factory = new GeometryFactory();
      for (int i = 1999; i >= 0; i--)
        sink.write(new Object[] {(long) i, factory.createPoint(new Coordinate(i, i % 17))});
      sink.finish();
    }
    var f = read();
    assertThat(f.header.featuresCount()).isEqualTo(2000);
    var query = new Envelope(123, 345, 2, 8);
    var hits = PackedRTree.search(f.buffer, f.treeOffset, 2000, 16, query);
    Set<Integer> actual = new HashSet<>();
    for (var hit : hits) {
      var p = feature(f, hit.offset).geometry();
      actual.add((int) p.xy(0));
    }
    Set<Integer> expected = new HashSet<>();
    for (int i = 0; i < 2000; i++) if (query.contains(i, i % 17)) expected.add(i);
    assertThat(actual).isEqualTo(expected);
    assertThat(Files.list(dir).map(p -> p.getFileName().toString()).toList())
        .containsExactly("out.fgb");
  }

  @Test
  void nullEmptyAndAbort() throws Exception {
    var r = request("POINT", "XY", FlatGeobufOptions.defaults(), Diagnostics.NONE);
    try (var sink = new FlatGeobufProvider().create(r)) {
      assertThatThrownBy(() -> sink.write(new Object[] {1L, null}))
          .hasMessageContaining("requires nonempty");
      assertThatThrownBy(sink::finish).hasMessageContaining("cannot finish");
    }
    assertThat(Files.exists(r.file())).isFalse();
    var warnings = new ArrayList<String>();
    r =
        request(
            "POINT",
            "XY",
            new FlatGeobufOptions(false, false, false),
            (f, c, m) -> warnings.add(c));
    try (var sink = new FlatGeobufProvider().create(r)) {
      sink.write(new Object[] {1L, null});
      sink.write(new Object[] {2L, new GeometryFactory().createPoint()});
      sink.finish();
    }
    var f = read();
    assertThat(f.header.indexNodeSize()).isZero();
    assertThat(f.header.featuresCount()).isEqualTo(2);
    assertThat(feature(f, 0).geometry()).isNull();
    assertThat(warnings).contains("empty_to_null");
    byte[] previous = Files.readAllBytes(r.file());
    try (var sink =
        new FlatGeobufProvider()
            .create(
                request(
                    "POINT", "XY", new FlatGeobufOptions(false, false, true), Diagnostics.NONE))) {
      sink.write(new Object[] {3L, new GeometryFactory().createPoint(new Coordinate(4, 5))});
    }
    assertThat(Files.readAllBytes(r.file())).isEqualTo(previous);
  }

  @Test
  void missingMAndPolygonHoles() throws Exception {
    var g =
        new WKTReader()
            .read(
                "POLYGON M ((0 0 NaN,10 0 NaN,10 10 NaN,0 0 NaN),(1 1 NaN,2 1 NaN,2 2 NaN,1 1"
                    + " NaN))");
    try (var sink =
        new FlatGeobufProvider()
            .create(request("POLYGON", "XYM", FlatGeobufOptions.defaults(), Diagnostics.NONE))) {
      sink.write(new Object[] {1L, g});
      sink.finish();
    }
    var geo = feature(read(), 0).geometry();
    assertThat(geo.endsLength()).isEqualTo(2);
    assertThat(geo.ends(0)).isEqualTo(4);
    assertThat(geo.mLength()).isEqualTo(8);
    assertThat(geo.m(0)).isNaN();
  }

  @Test
  void attributeEncodingAndNullProperties() throws Exception {
    var rm = new RowMeta();
    rm.addValueMeta(new ValueMetaGeometry("geometry"));
    rm.addValueMeta(new ValueMetaInteger("integer"));
    rm.addValueMeta(new ValueMetaString("text"));
    rm.addValueMeta(new ValueMetaBigNumber("decimal"));
    rm.addValueMeta(new ValueMetaBinary("binary"));
    rm.addValueMeta(new ValueMetaTimestamp("time"));
    rm.addValueMeta(new ValueMetaBoolean("bool"));
    var warning = new ArrayList<String>();
    var r =
        new WriteRequest(
            dir.resolve("out.fgb"),
            "attributes",
            rm,
            0,
            null,
            GeometrySchema.explicit("POINT", "XY", null),
            new FlatGeobufOptions(false, false, false),
            (f, c, m) -> warning.add(c));
    var time = java.sql.Timestamp.from(java.time.Instant.parse("2020-01-02T03:04:05.123456789Z"));
    try (var sink = new FlatGeobufProvider().create(r)) {
      sink.write(
          new Object[] {
            null,
            Long.MAX_VALUE,
            "Grüezi 🗺",
            new java.math.BigDecimal("12345678901234567890.123456789"),
            new byte[] {0, -1, 1},
            time,
            true
          });
      sink.write(new Object[] {null, null, "", null, null, null, null});
      sink.finish();
    }
    var f = read();
    var feature = feature(f, 0);
    var props = feature.propertiesAsByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
    assertThat(props.getShort()).isZero();
    assertThat(props.getLong()).isEqualTo(Long.MAX_VALUE);
    assertThat(props.getShort()).isEqualTo((short) 1);
    assertThat(string(props)).isEqualTo("Grüezi 🗺");
    assertThat(props.getShort()).isEqualTo((short) 2);
    assertThat(string(props)).isEqualTo("12345678901234567890.123456789");
    assertThat(props.getShort()).isEqualTo((short) 3);
    byte[] binary = new byte[props.getInt()];
    props.get(binary);
    assertThat(binary).containsExactly(0, -1, 1);
    assertThat(props.getShort()).isEqualTo((short) 4);
    assertThat(string(props)).isEqualTo("2020-01-02T03:04:05.123456789Z");
    assertThat(props.getShort()).isEqualTo((short) 5);
    assertThat(props.get()).isEqualTo((byte) 1);
    int next = f.buffer.getInt(f.featuresOffset) + 4;
    var empty = feature(f, next).propertiesAsByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
    assertThat(empty.remaining()).isEqualTo(6);
    assertThat(empty.getShort()).isEqualTo((short) 1);
    assertThat(empty.getInt()).isZero();
    assertThat(warning).contains("decimal_string");
  }

  static String string(ByteBuffer b) {
    byte[] value = new byte[b.getInt()];
    b.get(value);
    return new String(value, java.nio.charset.StandardCharsets.UTF_8);
  }

  @Test
  void cancellationDuringIndexPreservesTarget() throws Exception {
    var r = request("POINT", "XY", new FlatGeobufOptions(true, false, true), Diagnostics.NONE);
    Files.writeString(r.file(), "previous");
    var cancel = new java.util.concurrent.atomic.AtomicBoolean();
    var checks = new java.util.concurrent.atomic.AtomicInteger();
    r =
        new WriteRequest(
            r.file(),
            r.layer(),
            r.rowMeta(),
            r.geometryIndex(),
            null,
            r.geometry(),
            r.options(),
            r.diagnostics(),
            () -> cancel.get() && checks.incrementAndGet() > 20);
    try (var sink = new FlatGeobufProvider(192).create(r)) {
      for (int i = 0; i < 300; i++)
        sink.write(
            new Object[] {(long) i, new GeometryFactory().createPoint(new Coordinate(i, 0))});
      cancel.set(true);
      assertThatThrownBy(sink::finish).isInstanceOf(java.io.InterruptedIOException.class);
    }
    assertThat(Files.readString(r.file())).isEqualTo("previous");
    try (var paths = Files.list(dir)) {
      assertThat(paths.count()).isEqualTo(1);
    }
  }

  @Test
  void schemaErrorsSkippedRowsAndEmptyFile() throws Exception {
    var r = request("POINT", "XYZ", FlatGeobufOptions.defaults(), Diagnostics.NONE);
    try (var sink = new FlatGeobufProvider().create(r)) {
      assertThatThrownBy(
              () ->
                  sink.write(
                      new Object[] {1L, new GeometryFactory().createPoint(new Coordinate(1, 2))}))
          .hasMessageContaining("Required Z");
    }
    try (var sink =
        new FlatGeobufProvider()
            .create(
                request(
                    "POINT", "XY", new FlatGeobufOptions(true, true, false), Diagnostics.NONE))) {
      assertThat(sink.write(new Object[] {1L, null})).isFalse();
      sink.finish();
    }
    assertThat(read().header.featuresCount()).isZero();
    assertThat(read().header.indexNodeSize()).isZero();
    assertThatThrownBy(() -> new FlatGeobufProvider().create(r))
        .isInstanceOf(FileAlreadyExistsException.class);
  }
}
