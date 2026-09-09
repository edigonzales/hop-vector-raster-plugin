package ch.so.agi.hop.vector.formats.parquet;

import static org.assertj.core.api.Assertions.*;

import ch.so.agi.hop.vector.core.*;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.math.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.*;
import org.apache.parquet.conf.*;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.format.*;
import org.apache.parquet.hadoop.*;
import org.apache.parquet.hadoop.api.ReadSupport;
import org.apache.parquet.hadoop.example.GroupReadSupport;
import org.apache.parquet.io.LocalInputFile;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.*;

public class ParquetTest {
  @TempDir Path dir;
  static final CrsDefinitionResolver CRS =
      new CrsDefinitionResolver() {
        public Definition resolve(int srid) {
          return new Definition(srid, "test", "EPSG", srid, "");
        }

        public boolean isGeographic(Definition d) {
          return d.srid() == 4326;
        }
      };

  static RowMeta fields() {
    var rm = new RowMeta();
    rm.addValueMeta(new ValueMetaInteger("id"));
    rm.addValueMeta(new ValueMetaGeometry("geometry"));
    var dec = new ValueMetaBigNumber("decimal");
    dec.setLength(33);
    dec.setPrecision(15);
    rm.addValueMeta(dec);
    rm.addValueMeta(new ValueMetaString("text"));
    rm.addValueMeta(new ValueMetaBinary("bytes"));
    rm.addValueMeta(new ValueMetaTimestamp("time"));
    return rm;
  }

  static WriteRequest request(
      Path file, String type, String dim, int srid, ParquetOptions options) {
    return new WriteRequest(
        file,
        "test",
        fields(),
        1,
        null,
        GeometrySchema.explicit(
            type, dim, new CrsDefinitionResolver.Definition(srid, "", "EPSG", srid, "")),
        options,
        Diagnostics.NONE);
  }

  static Object[] row(Geometry geometry) {
    return new Object[] {
      Long.MAX_VALUE,
      geometry,
      new BigDecimal("123456789012345678.123456789012345"),
      "Grüezi 🗺",
      new byte[] {0, 1, -1},
      java.sql.Timestamp.from(java.time.Instant.parse("2020-01-02T03:04:05.123456789Z"))
    };
  }

  static FileMetaData footer(Path file) throws Exception {
    byte[] data = Files.readAllBytes(file);
    assertThat(new String(data, 0, 4, java.nio.charset.StandardCharsets.US_ASCII))
        .isEqualTo("PAR1");
    int n = ByteBuffer.wrap(data, data.length - 8, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    return Util.readFileMetaData(new java.io.ByteArrayInputStream(data, data.length - 8 - n, n));
  }

  static ParquetReader<Group> reader(Path file) throws Exception {
    return new ParquetReader.Builder<Group>(
        new LocalInputFile(file), new PlainParquetConfiguration()) {
      protected ReadSupport<Group> getReadSupport() {
        return new GroupReadSupport();
      }
    }.withCodecFactory(new JavaCodecs()).build();
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
        Path path = dir.resolve("out.parquet");
        try (var sink =
            new ParquetProvider(CRS)
                .create(
                    request(
                        path,
                        g.getGeometryType(),
                        dim,
                        2056,
                        new ParquetOptions("GEOMETRY", "SPHERICAL", "GZIP", 1024, true)))) {
          sink.write(row(g));
          sink.finish();
        }
        var footer = footer(path);
        var annotation = footer.schema.get(2).logicalType;
        assertThat(annotation.getGEOMETRY().crs).isEqualTo("EPSG:2056");
        for (var rg : footer.row_groups) {
          var stats = rg.columns.get(1).meta_data.statistics;
          assertThat(
                  stats == null
                      || (!stats.isSetMin()
                          && !stats.isSetMax()
                          && !stats.isSetMin_value()
                          && !stats.isSetMax_value()))
              .isTrue();
        }
        try (var reader = reader(path)) {
          var record = reader.read();
          byte[] wkb = record.getBinary("geometry", 0).getBytes();
          int type = ByteBuffer.wrap(wkb).getInt(1);
          assertThat(type / 1000)
              .isEqualTo((dim.contains("Z") ? 1 : 0) + (dim.contains("M") ? 2 : 0));
          assertThat(new WKBReader().read(wkb).equalsExact(g)).isTrue();
          assertThat(record.getLong("id", 0)).isEqualTo(Long.MAX_VALUE);
          assertThat(new BigDecimal(new BigInteger(record.getBinary("decimal", 0).getBytes()), 15))
              .isEqualTo(row(g)[2]);
          assertThat(record.getString("text", 0)).isEqualTo("Grüezi 🗺");
          assertThat(record.getBinary("bytes", 0).getBytes()).containsExactly(0, 1, -1);
          assertThat(record.getLong("time", 0)).isEqualTo(1577934245123456789L);
          assertThat(reader.read()).isNull();
        }
      }
    }
  }

  @Test
  void nullEmptyAndRowGroups() throws Exception {
    Path file = dir.resolve("groups.parquet");
    var r =
        request(
            file,
            "POINT",
            "XYM",
            0,
            new ParquetOptions("GEOMETRY", "SPHERICAL", "UNCOMPRESSED", 1024, false));
    try (var sink = new ParquetProvider(CRS).create(r)) {
      for (int i = 0; i < 2000; i++)
        sink.write(row(i % 2 == 0 ? null : new GeometryFactory().createPoint()));
      sink.finish();
    }
    var meta = footer(file);
    assertThat(meta.num_rows).isEqualTo(2000);
    assertThat(meta.row_groups.size()).isGreaterThan(1);
    assertThat(meta.schema.get(2).logicalType.getGEOMETRY().crs).isEqualTo("srid:0");
    try (var reader = reader(file)) {
      assertThat(reader.read().getFieldRepetitionCount("geometry")).isZero();
      byte[] empty = reader.read().getBinary("geometry", 0).getBytes();
      assertThat(ByteBuffer.wrap(empty).getInt(1)).isEqualTo(2001);
      assertThat(new WKBReader().read(empty).isEmpty()).isTrue();
    }
  }

  @Test
  void decimalErrorsDoNotReplaceTarget() throws Exception {
    Path file = dir.resolve("out.parquet");
    Files.writeString(file, "previous");
    var r =
        request(
            file,
            "POINT",
            "XY",
            0,
            new ParquetOptions("GEOMETRY", "SPHERICAL", "GZIP", 1024, true));
    try (var sink = new ParquetProvider(CRS).create(r)) {
      Object[] row = row(null);
      row[2] = new BigDecimal("1.1234567890123456");
      assertThatThrownBy(() -> sink.write(row)).isInstanceOf(ArithmeticException.class);
      assertThatThrownBy(sink::finish).hasMessageContaining("cannot finish");
    }
    assertThat(Files.readString(file)).isEqualTo("previous");
    r.rowMeta().getValueMeta(2).setLength(-1);
    assertThatThrownBy(() -> new ParquetProvider(CRS).create(r))
        .hasMessageContaining("upstream metadata");
  }

  @Test
  void geographyAndIsoBytes() throws Exception {
    var inferred = new WKTReader().read("POINT (1 2)");
    inferred.setSRID(2056);
    assertThat(ParquetProvider.crs(GeometrySchema.infer(inferred))).isEqualTo("srid:2056");
    Path file = dir.resolve("geo.parquet");
    var o = new ParquetOptions("GEOGRAPHY", "KARNEY", "GZIP", 1024, false);
    assertThatThrownBy(() -> new ParquetProvider(CRS).create(request(file, "POINT", "XY", 2056, o)))
        .hasMessageContaining("geographic CRS");
    try (var sink = new ParquetProvider(CRS).create(request(file, "POINT", "XY", 4326, o))) {
      sink.write(row(new WKTReader().read("POINT (7 47)")));
      sink.finish();
    }
    var ann = footer(file).schema.get(2).logicalType.getGEOGRAPHY();
    assertThat(ann.crs).isEqualTo("EPSG:4326");
    assertThat(ann.algorithm).isEqualTo(EdgeInterpolationAlgorithm.KARNEY);
    byte[] expected =
        java.util.HexFormat.of()
            .parseHex("0000000bb93ff0000000000000400000000000000040080000000000004010000000000000");
    var schema = GeometrySchema.explicit("POINT", "XYZM", null);
    assertThat(IsoWkbWriter.write(new WKTReader().read("POINT ZM (1 2 3 4)"), schema))
        .isEqualTo(expected);
  }

  @Test
  void cancellationAndCoordinateValidation() throws Exception {
    Path file = dir.resolve("cancel.parquet");
    Files.writeString(file, "previous");
    var r =
        request(
            file,
            "POINT",
            "XYM",
            4326,
            new ParquetOptions("GEOGRAPHY", "SPHERICAL", "GZIP", 1024, true));
    try (var sink = new ParquetProvider(CRS).create(r)) {
      assertThatThrownBy(() -> sink.write(row(new WKTReader().read("POINT M (181 47 NaN)"))))
          .hasMessageContaining("bounds");
    }
    var stop = new java.util.concurrent.atomic.AtomicBoolean();
    r =
        new WriteRequest(
            r.file(),
            r.layer(),
            r.rowMeta(),
            1,
            null,
            r.geometry(),
            r.options(),
            Diagnostics.NONE,
            stop::get);
    try (var sink = new ParquetProvider(CRS).create(r)) {
      sink.write(row(new WKTReader().read("POINT M (7 47 NaN)")));
      stop.set(true);
      assertThatThrownBy(sink::finish).isInstanceOf(java.io.InterruptedIOException.class);
    }
    assertThat(Files.readString(file)).isEqualTo("previous");
    try (var paths = Files.list(dir)) {
      assertThat(paths.count()).isEqualTo(1);
    }
  }

  @Test
  void emptyFileOverflowAndExtraGeometryField() throws Exception {
    Path file = dir.resolve("empty.parquet");
    var r = request(file, "POINT", "XYZM", 0, ParquetOptions.defaults());
    try (var sink = new ParquetProvider(CRS).create(r)) {
      sink.finish();
    }
    assertThat(footer(file).num_rows).isZero();
    Files.delete(file);
    try (var sink = new ParquetProvider(CRS).create(r)) {
      var row = row(null);
      row[2] = new BigDecimal("1234567890123456789");
      assertThatThrownBy(() -> sink.write(row)).hasMessageContaining("overflow");
    }
    try (var sink = new ParquetProvider(CRS).create(r)) {
      var row = row(null);
      row[5] = java.sql.Timestamp.valueOf("2500-01-01 00:00:00");
      assertThatThrownBy(() -> sink.write(row)).isInstanceOf(ArithmeticException.class);
    }
    r.rowMeta().addValueMeta(new ValueMetaGeometry("second"));
    assertThatThrownBy(() -> new ParquetProvider(CRS).create(r))
        .hasMessageContaining("only one Geometry");
  }

  /** Invoked with the unpacked distribution and no Hadoop dependencies. */
  public static void main(String[] args) throws Exception {
    Path file = Path.of(args[0]);
    try (var sink =
        new ParquetProvider(CRS)
            .create(request(file, "POINT", "XYZM", 2056, ParquetOptions.defaults()))) {
      sink.write(row(new WKTReader().read("POINT ZM (1 2 3 4)")));
      sink.finish();
    }
    if (footer(file).num_rows != 1) throw new AssertionError("Wrong row count");
    System.out.println("Parquet output without Hadoop runtime OK");
  }
}
