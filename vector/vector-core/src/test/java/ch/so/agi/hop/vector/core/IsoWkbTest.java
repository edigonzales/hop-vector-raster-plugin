package ch.so.agi.hop.vector.core;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.io.WKTReader;

class IsoWkbTest {
  @Test
  void isoDimensionsAndNoSrid() throws Exception {
    for (String dim : java.util.List.of("XY", "XYZ", "XYM", "XYZM")) {
      String text =
          "POINT "
              + dim.substring(2)
              + " (1 2"
              + (dim.contains("Z") ? " 3" : "")
              + (dim.contains("M") ? " 4" : "")
              + ")";
      var g = new WKTReader().read(text);
      g.setSRID(2056);
      byte[] bytes = IsoWkbWriter.write(g, GeometrySchema.explicit("POINT", dim, null));
      assertThat(bytes.length)
          .isEqualTo(5 + 8 * (2 + (dim.contains("Z") ? 1 : 0) + (dim.contains("M") ? 1 : 0)));
      assertThat(java.nio.ByteBuffer.wrap(bytes).getInt(1))
          .isEqualTo(1 + (dim.contains("Z") ? 1000 : 0) + (dim.contains("M") ? 2000 : 0));
    }
  }
}
