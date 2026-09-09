package ch.so.agi.hop.vector.formats.flatgeobuf;

import ch.so.agi.hop.vector.core.*;
import com.google.flatbuffers.FlatBufferBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.apache.hop.core.row.IValueMeta;
import org.locationtech.jts.geom.Envelope;
import org.wololo.flatgeobuf.Constants;
import org.wololo.flatgeobuf.generated.*;

public final class FlatGeobufProvider implements VectorProvider {
  private final long sortBudget;

  public FlatGeobufProvider() {
    this(64L * 1024 * 1024);
  }

  FlatGeobufProvider(long sortBudget) {
    this.sortBudget = sortBudget;
  }

  public VectorFormat format() {
    return VectorFormat.FLATGEOBUF;
  }

  public List<LayerSchema> layers(ReadRequest r) {
    throw new UnsupportedOperationException("FlatGeobuf supports writing only");
  }

  public VectorSource open(ReadRequest r) {
    throw new UnsupportedOperationException("FlatGeobuf supports writing only");
  }

  public VectorSink create(WriteRequest r) throws Exception {
    CloudGeometry.validateFields(r);
    var options = r.options() instanceof FlatGeobufOptions o ? o : FlatGeobufOptions.defaults();
    if (r.rowMeta().size() - 1 > 65535)
      throw new IllegalArgumentException("Too many FlatGeobuf attributes");
    return new Sink(r, options, sortBudget);
  }

  private static final class Sink implements VectorSink {
    final WriteRequest r;
    final FlatGeobufOptions options;
    final long budget;
    final StagedVectorFile staged;
    final Path features, index;
    OutputStream featureOut;
    DataOutputStream indexOut;
    final Envelope envelope = new Envelope();
    long count, offset;
    boolean failed, finished, closed;

    Sink(WriteRequest r, FlatGeobufOptions options, long budget) throws IOException {
      this.r = r;
      this.options = options;
      this.budget = budget;
      staged = new StagedVectorFile(r.file(), options.overwrite());
      features = staged.directory.resolve("features");
      index = staged.directory.resolve("index");
      try {
        featureOut = new BufferedOutputStream(Files.newOutputStream(features));
        indexOut = DiskIndex.output(index);
      } catch (IOException e) {
        close();
        throw e;
      }
    }

    public boolean write(Object[] row) throws Exception {
      if (failed || finished || closed) throw new IOException("Writer is not open");
      try {
        r.checkCancelled();
        var g = CloudGeometry.prepare(r, row[r.geometryIndex()]);
        if (g == null || g.isEmpty()) {
          if (options.skipEmpty()) {
            r.diagnostics()
                .warning("geometry", "skip_empty", "Skipped NULL/EMPTY FlatGeobuf feature");
            return false;
          }
          if (options.spatialIndex())
            throw new IllegalArgumentException(
                "Indexed FlatGeobuf requires nonempty geometry; enable skipping or disable index");
          if (g != null)
            r.diagnostics()
                .warning("geometry", "empty_to_null", "FlatGeobuf EMPTY exported as NULL");
          g = null;
        }
        var b = new FlatBufferBuilder();
        int geometry =
            g == null
                ? 0
                : FgbGeometry.encode(
                    b, g, r.geometry().z() != Ordinate.ABSENT, r.geometry().m() != Ordinate.ABSENT);
        byte[] properties = properties(row);
        int props = Feature.createPropertiesVector(b, properties);
        Feature.startFeature(b);
        if (geometry != 0) Feature.addGeometry(b, geometry);
        Feature.addProperties(b, props);
        Feature.finishSizePrefixedFeatureBuffer(b, Feature.endFeature(b));
        byte[] data = b.sizedByteArray();
        featureOut.write(data);
        if (g != null) {
          var e = g.getEnvelopeInternal();
          envelope.expandToInclude(e);
          if (options.spatialIndex())
            new DiskIndex.Entry(
                    0, offset, data.length, e.getMinX(), e.getMinY(), e.getMaxX(), e.getMaxY())
                .write(indexOut);
        }
        count++;
        offset = Math.addExact(offset, data.length);
        return true;
      } catch (Exception e) {
        failed = true;
        throw e;
      }
    }

    private byte[] properties(Object[] row) throws Exception {
      var bytes = new ByteArrayOutputStream();
      var out = new DataOutputStream(bytes);
      int column = 0;
      for (int i = 0; i < r.rowMeta().size(); i++) {
        if (i == r.geometryIndex()) continue;
        int id = column++;
        if (row[i] == null) continue;
        var f = r.rowMeta().getValueMeta(i);
        out.writeShort(Short.reverseBytes((short) id));
        switch (f.getType()) {
          case IValueMeta.TYPE_BOOLEAN -> out.writeByte(r.rowMeta().getBoolean(row, i) ? 1 : 0);
          case IValueMeta.TYPE_INTEGER ->
              out.writeLong(Long.reverseBytes(r.rowMeta().getInteger(row, i)));
          case IValueMeta.TYPE_NUMBER ->
              out.writeLong(
                  Long.reverseBytes(Double.doubleToLongBits(r.rowMeta().getNumber(row, i))));
          case IValueMeta.TYPE_BINARY -> variable(out, r.rowMeta().getBinary(row, i));
          case IValueMeta.TYPE_BIGNUMBER -> {
            variable(
                out,
                r.rowMeta().getBigNumber(row, i).toPlainString().getBytes(StandardCharsets.UTF_8));
            r.diagnostics()
                .warning(
                    f.getName(), "decimal_string", "Exact decimal exported as FlatGeobuf String");
          }
          case IValueMeta.TYPE_DATE, IValueMeta.TYPE_TIMESTAMP -> {
            java.util.Date date = r.rowMeta().getDate(row, i);
            var instant =
                date instanceof java.sql.Timestamp t
                    ? t.toInstant()
                    : java.time.Instant.ofEpochMilli(date.getTime());
            variable(out, instant.toString().getBytes(StandardCharsets.UTF_8));
          }
          default -> variable(out, r.rowMeta().getString(row, i).getBytes(StandardCharsets.UTF_8));
        }
      }
      return bytes.toByteArray();
    }

    private static void variable(DataOutputStream out, byte[] value) throws IOException {
      out.writeInt(Integer.reverseBytes(value.length));
      out.write(value);
    }

    private byte[] header() {
      var b = new FlatBufferBuilder();
      int[] columns = new int[r.rowMeta().size() - 1];
      int c = 0;
      for (int i = 0; i < r.rowMeta().size(); i++) {
        if (i == r.geometryIndex()) continue;
        var f = r.rowMeta().getValueMeta(i);
        int name = b.createString(f.getName());
        int type =
            switch (f.getType()) {
              case IValueMeta.TYPE_BOOLEAN -> ColumnType.Bool;
              case IValueMeta.TYPE_INTEGER -> ColumnType.Long;
              case IValueMeta.TYPE_NUMBER -> ColumnType.Double;
              case IValueMeta.TYPE_BINARY -> ColumnType.Binary;
              case IValueMeta.TYPE_DATE, IValueMeta.TYPE_TIMESTAMP -> ColumnType.DateTime;
              default -> ColumnType.String;
            };
        Column.startColumn(b);
        Column.addName(b, name);
        Column.addType(b, type);
        Column.addNullable(b, true);
        columns[c++] = Column.endColumn(b);
      }
      int cols = Header.createColumnsVector(b, columns), name = b.createString(r.layer());
      int env =
          envelope.isNull()
              ? 0
              : Header.createEnvelopeVector(
                  b,
                  new double[] {
                    envelope.getMinX(), envelope.getMinY(), envelope.getMaxX(), envelope.getMaxY()
                  });
      var crs = r.geometry().crs();
      int crsOffset = 0;
      if (crs != null
          && (crs.srid() > 0
              || (!crs.wkt().isBlank() && !crs.wkt().equalsIgnoreCase("undefined")))) {
        int org = b.createString(crs.organization()),
            label = b.createString(crs.name()),
            wkt = crs.wkt().isBlank() ? 0 : b.createString(crs.wkt());
        crsOffset = Crs.createCrs(b, org, crs.organizationId(), label, 0, wkt, 0);
      }
      Header.startHeader(b);
      Header.addName(b, name);
      Header.addGeometryType(b, FgbGeometry.type(r.geometry().type()));
      Header.addHasZ(b, r.geometry().z() != Ordinate.ABSENT);
      Header.addHasM(b, r.geometry().m() != Ordinate.ABSENT);
      Header.addColumns(b, cols);
      Header.addFeaturesCount(b, count);
      Header.addIndexNodeSize(b, options.spatialIndex() && count > 0 ? 16 : 0);
      if (env != 0) Header.addEnvelope(b, env);
      if (crsOffset != 0) Header.addCrs(b, crsOffset);
      Header.finishSizePrefixedHeaderBuffer(b, Header.endHeader(b));
      return b.sizedByteArray();
    }

    public void finish() throws Exception {
      if (failed || finished || closed) throw new IOException("Writer cannot finish");
      try {
        featureOut.close();
        indexOut.close();
        Path sorted = null, tree = null;
        if (options.spatialIndex() && count > 0) {
          sorted = DiskIndex.sort(index, staged.directory, envelope, budget, r::checkCancelled);
          tree = DiskIndex.tree(sorted, staged.directory, count, r::checkCancelled);
        }
        try (var out = new BufferedOutputStream(Files.newOutputStream(staged.file))) {
          out.write(Constants.MAGIC_BYTES);
          out.write(header());
          if (tree == null) DiskIndex.copy(features, out, r::checkCancelled);
          else {
            DiskIndex.copy(tree, out, r::checkCancelled);
            try (var in = DiskIndex.input(sorted);
                var data = new RandomAccessFile(features.toFile(), "r")) {
              byte[] buffer = new byte[65536];
              DiskIndex.Entry e;
              while ((e = DiskIndex.Entry.read(in)) != null) {
                data.seek(e.offset());
                long left = e.size();
                while (left > 0) {
                  r.checkCancelled();
                  int n = (int) Math.min(left, buffer.length);
                  data.readFully(buffer, 0, n);
                  out.write(buffer, 0, n);
                  left -= n;
                }
              }
            }
          }
        }
        r.checkCancelled();
        staged.publish();
        finished = true;
      } catch (Exception e) {
        failed = true;
        throw e;
      }
    }

    public void close() throws IOException {
      if (closed) return;
      closed = true;
      try {
        if (featureOut != null) featureOut.close();
      } finally {
        try {
          if (indexOut != null) indexOut.close();
        } finally {
          staged.close();
        }
      }
    }
  }
}
