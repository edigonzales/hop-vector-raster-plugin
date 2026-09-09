package ch.so.agi.hop.vector.transforms;

import ch.so.agi.hop.vector.core.*;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.nio.file.*;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.locationtech.jts.io.WKTReader;

/** Loaded with only the installed ZIP plus Hop core and the shared geometry dependency. */
public final class CloudOutputSmoke {
  public static void main(String[] args) throws Exception {
    try {
      Class.forName("org.apache.hadoop.conf.Configuration");
      throw new AssertionError("Hadoop must not be on runtime classpath");
    } catch (ClassNotFoundException expected) {
    }
    var rm = new RowMeta();
    rm.addValueMeta(new ValueMetaInteger("id"));
    rm.addValueMeta(new ValueMetaGeometry("geometry"));
    var geometry = new WKTReader().read("POINT ZM (2600000 1200000 30 4)");
    geometry.setSRID(2056);
    for (var format : new VectorFormat[] {VectorFormat.FLATGEOBUF, VectorFormat.PARQUET}) {
      VectorProvider provider =
          format == VectorFormat.FLATGEOBUF
              ? new ch.so.agi.hop.vector.formats.flatgeobuf.FlatGeobufProvider()
              : new ch.so.agi.hop.vector.formats.parquet.ParquetProvider(
                  id -> new CrsDefinitionResolver.Definition(id, "LV95", "EPSG", id, ""));
      if (!provider
          .getClass()
          .getProtectionDomain()
          .getCodeSource()
          .getLocation()
          .toString()
          .endsWith(".jar")) throw new AssertionError("Provider must come from installed JAR");
      Path file =
          Path.of(args[0], format == VectorFormat.FLATGEOBUF ? "smoke.fgb" : "smoke.parquet");
      FormatOptions options =
          format == VectorFormat.FLATGEOBUF
              ? FlatGeobufOptions.defaults()
              : ParquetOptions.defaults();
      try (var sink =
          provider.create(
              new WriteRequest(
                  file,
                  "test",
                  rm,
                  1,
                  geometry,
                  GeometrySchema.infer(geometry),
                  options,
                  Diagnostics.NONE))) {
        sink.write(new Object[] {1L, geometry});
        sink.finish();
      }
      if (Files.size(file) < 50) throw new AssertionError("Incomplete output");
      System.out.println(format + " output from installed ZIP OK");
    }
  }
}
