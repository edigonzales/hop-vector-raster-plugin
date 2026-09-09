package ch.so.agi.hop.vector.formats.parquet;

import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.*;

import ch.so.agi.hop.vector.core.*;
import java.io.*;
import java.math.RoundingMode;
import java.util.*;
import org.apache.hop.core.row.IValueMeta;
import org.apache.parquet.column.schema.EdgeInterpolationAlgorithm;
import org.apache.parquet.conf.*;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.api.WriteSupport;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.LocalOutputFile;
import org.apache.parquet.io.api.*;
import org.apache.parquet.schema.*;
import org.locationtech.jts.geom.*;

public final class ParquetProvider implements VectorProvider {
  private final CrsDefinitionResolver resolver;

  public ParquetProvider(CrsDefinitionResolver resolver) {
    this.resolver = resolver;
  }

  public VectorFormat format() {
    return VectorFormat.PARQUET;
  }

  public List<LayerSchema> layers(ReadRequest r) {
    throw new UnsupportedOperationException("Parquet supports writing only");
  }

  public VectorSource open(ReadRequest r) {
    throw new UnsupportedOperationException("Parquet supports writing only");
  }

  public VectorSink create(WriteRequest r) throws Exception {
    CloudGeometry.validateFields(r);
    var options = r.options() instanceof ParquetOptions o ? o : ParquetOptions.defaults();
    if (options.logicalType().equals("GEOGRAPHY")
        && (r.geometry().crs() == null || !resolver.isGeographic(r.geometry().crs())))
      throw new IllegalArgumentException("GEOGRAPHY requires a known geographic CRS");
    MessageType schema = schema(r, options);
    return new Sink(r, options, schema);
  }

  static String crs(GeometrySchema s) {
    var c = s.crs();
    if (c == null) return "srid:0";
    if (c.organizationId() > 0
        && c.organization() != null
        && !c.organization().isBlank()
        && !c.organization().equalsIgnoreCase("NONE"))
      return c.organization() + ":" + c.organizationId();
    if (c.wkt() != null && !c.wkt().isBlank() && !c.wkt().equalsIgnoreCase("undefined"))
      return c.wkt();
    return c.srid() > 0 ? "srid:" + c.srid() : "srid:0";
  }

  private static MessageType schema(WriteRequest r, ParquetOptions options) {
    var fields = new ArrayList<Type>();
    for (int i = 0; i < r.rowMeta().size(); i++) {
      var f = r.rowMeta().getValueMeta(i);
      PrimitiveType.PrimitiveTypeName physical;
      LogicalTypeAnnotation logical = null;
      if (i == r.geometryIndex()) {
        physical = BINARY;
        logical =
            options.logicalType().equals("GEOGRAPHY")
                ? LogicalTypeAnnotation.geographyType(
                    crs(r.geometry()), EdgeInterpolationAlgorithm.valueOf(options.algorithm()))
                : LogicalTypeAnnotation.geometryType(crs(r.geometry()));
      } else {
        physical =
            switch (f.getType()) {
              case IValueMeta.TYPE_INTEGER, IValueMeta.TYPE_DATE, IValueMeta.TYPE_TIMESTAMP ->
                  INT64;
              case IValueMeta.TYPE_NUMBER -> DOUBLE;
              case IValueMeta.TYPE_BOOLEAN -> BOOLEAN;
              default -> BINARY;
            };
        logical =
            switch (f.getType()) {
              case IValueMeta.TYPE_STRING -> LogicalTypeAnnotation.stringType();
              case IValueMeta.TYPE_DATE ->
                  LogicalTypeAnnotation.timestampType(true, LogicalTypeAnnotation.TimeUnit.MILLIS);
              case IValueMeta.TYPE_TIMESTAMP ->
                  LogicalTypeAnnotation.timestampType(true, LogicalTypeAnnotation.TimeUnit.NANOS);
              case IValueMeta.TYPE_BIGNUMBER -> {
                if (f.getLength() <= 0 || f.getPrecision() < 0 || f.getPrecision() > f.getLength())
                  throw new IllegalArgumentException(
                      "Set decimal precision (length) and scale in upstream metadata for '"
                          + f.getName()
                          + "'");
                yield LogicalTypeAnnotation.decimalType(f.getPrecision(), f.getLength());
              }
              default -> null;
            };
      }
      var builder = Types.optional(physical);
      if (logical != null) builder.as(logical);
      fields.add(builder.named(f.getName()));
    }
    return new MessageType(r.layer(), fields);
  }

  private static final class Sink implements VectorSink {
    final WriteRequest r;
    final ParquetOptions options;
    final StagedVectorFile staged;
    ParquetWriter<Object[]> writer;
    boolean failed, finished, closed;

    Sink(WriteRequest r, ParquetOptions options, MessageType schema) throws Exception {
      this.r = r;
      this.options = options;
      staged = new StagedVectorFile(r.file(), options.overwrite());
      try {
        writer =
            new Builder(new LocalOutputFile(staged.file), new Support(schema))
                .withConf(new PlainParquetConfiguration())
                .withCodecFactory(new JavaCodecs())
                .withCompressionCodec(CompressionCodecName.valueOf(options.compression()))
                .withRowGroupSize(options.rowGroupSize())
                .withMinRowCountForPageSizeCheck(1)
                .withStatisticsEnabled(r.rowMeta().getValueMeta(r.geometryIndex()).getName(), false)
                .withDictionaryEncoding(
                    r.rowMeta().getValueMeta(r.geometryIndex()).getName(), false)
                .build();
      } catch (Exception | LinkageError e) {
        staged.close();
        throw e;
      }
    }

    public boolean write(Object[] row) throws Exception {
      if (failed || finished || closed) throw new IOException("Writer is not open");
      try {
        r.checkCancelled();
        var g = CloudGeometry.prepare(r, row[r.geometryIndex()]);
        if (g != null && options.logicalType().equals("GEOGRAPHY"))
          g.apply(
              new CoordinateSequenceFilter() {
                public boolean isDone() {
                  return false;
                }

                public boolean isGeometryChanged() {
                  return false;
                }

                public void filter(CoordinateSequence q, int i) {
                  if (q.getX(i) < -180 || q.getX(i) > 180 || q.getY(i) < -90 || q.getY(i) > 90)
                    throw new IllegalArgumentException(
                        "GEOGRAPHY coordinates outside longitude/latitude bounds");
                }
              });
        var values = new Object[r.rowMeta().size()];
        for (int i = 0; i < values.length; i++) {
          if (i == r.geometryIndex()) {
            values[i] =
                g == null
                    ? null
                    : Binary.fromConstantByteArray(IsoWkbWriter.write(g, r.geometry()));
            continue;
          }
          if (row[i] == null) continue;
          var f = r.rowMeta().getValueMeta(i);
          values[i] =
              switch (f.getType()) {
                case IValueMeta.TYPE_INTEGER -> r.rowMeta().getInteger(row, i);
                case IValueMeta.TYPE_NUMBER -> r.rowMeta().getNumber(row, i);
                case IValueMeta.TYPE_BOOLEAN -> r.rowMeta().getBoolean(row, i);
                case IValueMeta.TYPE_STRING -> Binary.fromString(r.rowMeta().getString(row, i));
                case IValueMeta.TYPE_BINARY ->
                    Binary.fromConstantByteArray(r.rowMeta().getBinary(row, i));
                case IValueMeta.TYPE_BIGNUMBER -> {
                  var n =
                      r.rowMeta()
                          .getBigNumber(row, i)
                          .setScale(f.getPrecision(), RoundingMode.UNNECESSARY);
                  if (n.precision() > f.getLength())
                    throw new IllegalArgumentException("Decimal overflow in '" + f.getName() + "'");
                  yield Binary.fromConstantByteArray(n.unscaledValue().toByteArray());
                }
                case IValueMeta.TYPE_DATE -> r.rowMeta().getDate(row, i).getTime();
                case IValueMeta.TYPE_TIMESTAMP -> {
                  var d = r.rowMeta().getDate(row, i);
                  var instant =
                      d instanceof java.sql.Timestamp t
                          ? t.toInstant()
                          : java.time.Instant.ofEpochMilli(d.getTime());
                  yield Math.addExact(
                      Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L),
                      instant.getNano());
                }
                default ->
                    throw new IllegalArgumentException("Unsupported attribute " + f.getName());
              };
        }
        writer.write(values);
        return true;
      } catch (Exception e) {
        failed = true;
        throw e;
      }
    }

    public void finish() throws Exception {
      if (failed || finished || closed) throw new IOException("Writer cannot finish");
      try {
        r.checkCancelled();
        writer.close();
        writer = null;
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
        if (writer != null) writer.close();
      } finally {
        staged.close();
      }
    }
  }

  private static final class Support extends WriteSupport<Object[]> {
    final MessageType schema;
    RecordConsumer consumer;

    Support(MessageType schema) {
      this.schema = schema;
    }

    public WriteContext init(org.apache.hadoop.conf.Configuration ignored) {
      throw new UnsupportedOperationException("Use neutral Parquet configuration");
    }

    public WriteContext init(ParquetConfiguration ignored) {
      return new WriteContext(schema, Map.of());
    }

    public void prepareForWrite(RecordConsumer consumer) {
      this.consumer = consumer;
    }

    public void write(Object[] values) {
      consumer.startMessage();
      for (int i = 0; i < values.length; i++) {
        Object v = values[i];
        if (v == null) continue;
        String name = schema.getFieldName(i);
        consumer.startField(name, i);
        if (v instanceof Binary b) consumer.addBinary(b);
        else if (v instanceof Long l) consumer.addLong(l);
        else if (v instanceof Double d) consumer.addDouble(d);
        else if (v instanceof Boolean b) consumer.addBoolean(b);
        else throw new IllegalArgumentException("Unsupported Parquet value");
        consumer.endField(name, i);
      }
      consumer.endMessage();
    }
  }

  private static final class Builder extends ParquetWriter.Builder<Object[], Builder> {
    final Support support;

    Builder(org.apache.parquet.io.OutputFile file, Support support) {
      super(file);
      this.support = support;
    }

    protected Builder self() {
      return this;
    }

    protected WriteSupport<Object[]> getWriteSupport(org.apache.hadoop.conf.Configuration ignored) {
      throw new UnsupportedOperationException("Use neutral Parquet configuration");
    }

    protected WriteSupport<Object[]> getWriteSupport(ParquetConfiguration ignored) {
      return support;
    }
  }
}
