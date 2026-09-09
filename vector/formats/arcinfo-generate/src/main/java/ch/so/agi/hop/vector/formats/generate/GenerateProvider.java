package ch.so.agi.hop.vector.formats.generate;

import ch.so.agi.hop.vector.core.*;
import java.util.List;
import org.apache.hop.core.row.IValueMeta;
import org.locationtech.jts.geom.Geometry;

public final class GenerateProvider implements VectorProvider {
  public VectorFormat format() {
    return VectorFormat.ARCINFO_GENERATE;
  }

  public List<LayerSchema> layers(ReadRequest request) {
    throw new UnsupportedOperationException("GENERATE supports writing only");
  }

  public VectorSource open(ReadRequest request) {
    throw new UnsupportedOperationException("GENERATE supports writing only");
  }

  public VectorSink create(WriteRequest r) throws Exception {
    var options =
        new GenerateEncoder.Options(
            GenerateEncoder.Type.valueOf(r.generate().geometryType()),
            GenerateEncoder.Dimension.valueOf(r.generate().dimension()),
            r.generate().discardExtraOrdinates(),
            r.generate().decimals(),
            r.generate().comma());
    var file = new GenerateFile(r.file(), r.generate().overwrite(), options);
    return new VectorSink() {
      long next = r.generate().startId();
      boolean exhausted;

      public boolean write(Object[] row) throws Exception {
        Object value = row[r.geometryIndex()];
        if (value != null && !(value instanceof Geometry))
          throw new IllegalArgumentException("Expected Hop Geometry value");
        Geometry geometry = (Geometry) value;
        if ((geometry == null || geometry.isEmpty()) && r.generate().skipEmpty()) return false;
        String idField = r.generate().idField();
        long id;
        if (idField.isBlank()) {
          if (exhausted) throw new IllegalArgumentException("Sequential ID overflow");
          id = next;
        } else {
          int i = r.rowMeta().indexOfValue(idField);
          if (i < 0 || r.rowMeta().getValueMeta(i).getType() != IValueMeta.TYPE_INTEGER)
            throw new IllegalArgumentException("ID field must have Hop Integer type");
          Long n = r.rowMeta().getInteger(row, i);
          if (n == null) throw new IllegalArgumentException("ID is null");
          id = n;
        }
        file.write(id, geometry);
        if (idField.isBlank()) {
          if (next == Long.MAX_VALUE) exhausted = true;
          else next++;
        }
        return true;
      }

      public void finish() throws Exception {
        file.commit();
      }

      public void close() throws Exception {
        file.close();
      }
    };
  }
}
