package ch.so.agi.hop.vector.formats.generate;

import static org.assertj.core.api.Assertions.*;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.io.WKTReader;

class GenerateEncoderTest {
  @TempDir Path dir;

  private String encode(
      String wkt, GenerateEncoder.Type type, GenerateEncoder.Dimension dimension, int decimals)
      throws Exception {
    StringWriter writer = new StringWriter();
    GenerateEncoder encoder =
        new GenerateEncoder(new GenerateEncoder.Options(type, dimension, false, decimals, false));
    encoder.writeFeature(writer, 42, new WKTReader().read(wkt));
    encoder.finish(writer);
    return writer.toString();
  }

  @Test
  void sixEsriFormsAndFinalTerminators() throws Exception {
    assertThat(
            encode(
                "POINT (2600000.125 1200000.25)",
                GenerateEncoder.Type.POINT,
                GenerateEncoder.Dimension.XY,
                -1))
        .isEqualTo("42 2600000.125 1200000.25\nEND\n");
    assertThat(
            encode(
                "POINT Z (1 2 3)", GenerateEncoder.Type.POINT, GenerateEncoder.Dimension.XYZ, -1))
        .isEqualTo("42 1 2 3\nEND\n");
    assertThat(
            encode(
                "LINESTRING (1 2,3 4)",
                GenerateEncoder.Type.LINE,
                GenerateEncoder.Dimension.XY,
                -1))
        .isEqualTo("42\n1 2\n3 4\nEND\nEND\n");
    assertThat(
            encode(
                "LINESTRING Z (1 2 3,4 5 6)",
                GenerateEncoder.Type.LINE,
                GenerateEncoder.Dimension.XYZ,
                -1))
        .isEqualTo("42\n1 2 3\n4 5 6\nEND\nEND\n");
    assertThat(
            encode(
                "POLYGON ((0 0,2 0,2 2,0 0))",
                GenerateEncoder.Type.POLYGON,
                GenerateEncoder.Dimension.XY,
                -1))
        .isEqualTo("42 AUTO\n0 0\n2 2\n2 0\n0 0\nEND\nEND\n");
    assertThat(
            encode(
                "POLYGON Z ((0 0 1,2 0 2,2 2 3,0 0 1))",
                GenerateEncoder.Type.POLYGON,
                GenerateEncoder.Dimension.XYZ,
                -1))
        .isEqualTo("42\n0 0 1\n2 2 3\n2 0 2\n0 0 1\nEND\nEND\n");
  }

  @Test
  void validatesDimensionsAndTopologyBeforeWriting() throws Exception {
    assertThatThrownBy(
            () ->
                encode(
                    "POINT (1 2)", GenerateEncoder.Type.POINT, GenerateEncoder.Dimension.XYZ, -1))
        .hasMessageContaining("finite Z");
    assertThatThrownBy(
            () ->
                encode(
                    "POINT Z (1 2 3)",
                    GenerateEncoder.Type.POINT,
                    GenerateEncoder.Dimension.XY,
                    -1))
        .hasMessageContaining("extra ordinates");
    assertThatThrownBy(
            () ->
                encode(
                    "MULTIPOINT ((1 2),(3 4))",
                    GenerateEncoder.Type.POINT,
                    GenerateEncoder.Dimension.XY,
                    -1))
        .hasMessageContaining("single");
    assertThatThrownBy(
            () ->
                encode(
                    "POLYGON ((0 0,4 0,4 4,0 4,0 0),(1 1,2 1,2 2,1 1))",
                    GenerateEncoder.Type.POLYGON,
                    GenerateEncoder.Dimension.XY,
                    -1))
        .hasMessageContaining("holes");
    assertThatThrownBy(
            () ->
                encode(
                    "LINESTRING (1.01 2,1.02 2)",
                    GenerateEncoder.Type.LINE,
                    GenerateEncoder.Dimension.XY,
                    0))
        .hasMessageContaining("collapses");
    assertThatThrownBy(
            () ->
                encode(
                    "POLYGON ((0 0,0.01 0,0.01 0.01,0 0))",
                    GenerateEncoder.Type.POLYGON,
                    GenerateEncoder.Dimension.XY,
                    0))
        .hasMessageContaining("rounding");
  }

  @Test
  void roundingIsIndependentOfLocale() throws Exception {
    java.util.Locale previous = java.util.Locale.getDefault();
    try {
      java.util.Locale.setDefault(java.util.Locale.GERMANY);
      assertThat(
              encode(
                  "POINT (-0.0001 1.235)",
                  GenerateEncoder.Type.POINT,
                  GenerateEncoder.Dimension.XY,
                  2))
          .isEqualTo("42 0.00 1.24\nEND\n");
    } finally {
      java.util.Locale.setDefault(previous);
    }
  }

  @Test
  void cancellationAndConcurrentOutputNeverReplaceOriginal() throws Exception {
    Path file = dir.resolve("result.gen");
    Files.writeString(file, "old");
    var options =
        new GenerateEncoder.Options(
            GenerateEncoder.Type.POINT, GenerateEncoder.Dimension.XY, false, -1, false);
    try (var writer = new GenerateFile(file, true, options)) {
      writer.write(1, new WKTReader().read("POINT (1 2)"));
      assertThatThrownBy(() -> new GenerateFile(file, true, options))
          .hasMessageContaining("Another");
    }
    assertThat(Files.readString(file)).isEqualTo("old");
    try (var writer = new GenerateFile(file, true, options)) {
      writer.write(1, new WKTReader().read("POINT (1 2)"));
      writer.commit();
    }
    assertThat(Files.readString(file)).isEqualTo("1 1 2\nEND\n");
    try (var files = Files.list(dir)) {
      assertThat(files.count()).isEqualTo(1);
    }
  }
}
