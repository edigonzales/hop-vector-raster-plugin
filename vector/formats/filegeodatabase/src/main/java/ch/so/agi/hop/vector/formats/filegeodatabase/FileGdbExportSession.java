package ch.so.agi.hop.vector.formats.filegeodatabase;

import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.catalog.*;
import ch.so.agi.filegdb.geometry.*;
import ch.so.agi.filegdb.write.*;
import ch.so.agi.hop.vector.core.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import org.apache.hop.core.row.*;
import org.locationtech.jts.geom.Geometry;

/** One owner, one staged database, any number of declared datasets. */
public final class FileGdbExportSession implements AutoCloseable {
  private final FileGdbExportSchema schema;
  private final Path output, stagingParent, staging;
  private FileGeodatabase database;
  private final Map<String, Writer> writers = new LinkedHashMap<>();
  private final Map<String, Domain> domains = new HashMap<>();
  private boolean failed, finished, closed;

  private static final class Writer {
    FileGdbExportSchema.DatasetSpec spec;
    int[] positions;
    int geometryPosition = -1;
    GdbFeatureWriter features;
    GdbTableWriter table;
    long rowNumber;
    GeometryKind kind;
    int srid;
  }

  public FileGdbExportSession(
      Path output,
      FileGdbExportSchema schema,
      Map<String, IRowMeta> inputs,
      CrsDefinitionResolver crs)
      throws Exception {
    schema.validate();
    this.schema = schema;
    this.output = output.toAbsolutePath().normalize();
    if (!this.output.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gdb"))
      throw new IllegalArgumentException("Output must end in .gdb");
    if (Files.exists(this.output))
      throw new IllegalArgumentException("Output already exists: " + output);
    if (!inputs
        .keySet()
        .equals(
            new HashSet<>(
                schema.datasets().stream().map(FileGdbExportSchema.DatasetSpec::name).toList())))
      throw new IllegalArgumentException("Each schema dataset requires exactly one input");
    // Validate field mappings before opening any output files.
    for (var d : schema.datasets()) {
      var w = new Writer();
      w.spec = d;
      IRowMeta rm =
          Objects.requireNonNull(inputs.get(d.name()), "Input metadata required: " + d.name());
      w.positions = new int[d.fields().size()];
      for (int i = 0; i < w.positions.length; i++) {
        var f = d.fields().get(i);
        w.positions[i] = index(rm, f.sourceName());
        validateInputType(f, rm.getValueMeta(w.positions[i]));
      }
      if (d.geometry() != null) {
        w.geometryPosition = index(rm, d.geometry().source());
        if (!(rm.getValueMeta(w.geometryPosition)
            instanceof com.atolcd.hop.core.row.value.ValueMetaGeometry))
          throw new IllegalArgumentException(
              "Expected Hop Geometry field: " + d.geometry().source());
      }
      writers.put(d.name(), w);
    }
    stagingParent = Files.createTempDirectory(this.output.getParent(), ".hop-filegdb-");
    staging = stagingParent.resolve("output.gdb");
    try {
      database = FileGeodatabase.create(staging);
      for (var d : schema.domains()) {
        var domain = d.domain();
        domains.put(d.name(), domain);
        database.createDomain(domain);
      }
      for (var w : writers.values()) {
        var fields = w.spec.fields().stream().map(FileGdbExportSchema.FieldSpec::field).toList();
        if (w.spec.geometry() == null)
          w.table = database.createTable(new TableDefinition(w.spec.name(), fields));
        else {
          var g = w.spec.geometry();
          var def = crs.parse(g.crs());
          w.srid = def.srid();
          w.kind = kind(g.type());
          if (!Set.of("XY", "XYZ", "XYM", "XYZM").contains(g.dimension()))
            throw new IllegalArgumentException("Invalid dimension: " + g.dimension());
          var geometry =
              GeometryFieldDefinition.of(g.name(), w.kind).withNullable(true).withWkt(def.wkt());
          if (g.dimension().contains("Z")) geometry = geometry.withZ();
          if (g.dimension().contains("M")) geometry = geometry.withM();
          var options =
              new FileGeodatabaseOptions(
                  g.precisionMode() == null ? "AUTO" : g.precisionMode(),
                  g.xyResolution(),
                  g.xyTolerance(),
                  g.xOrigin(),
                  g.yOrigin(),
                  !Boolean.FALSE.equals(g.spatialIndex()),
                  null);
          var p = geometry.precision();
          double res = p.xyResolution(), tol = p.xyTolerance(), x = p.xOrigin(), y = p.yOrigin();
          if (options.precisionMode().equals("AUTO")) {
            if (crs.isGeographic(def)) {
              res = 1e-9;
              tol = 8.983153e-9;
              x = -400;
              y = -400;
            } else {
              double unit = crs.linearUnitToMetres(def);
              res = 0.0001 / unit;
              tol = 0.001 / unit;
            }
          }
          if (g.xyResolution() != null) {
            res = g.xyResolution();
            tol = 10 * res;
          }
          if (g.xyTolerance() != null) tol = g.xyTolerance();
          if (g.xOrigin() != null) {
            x = g.xOrigin();
            y = g.yOrigin();
          }
          geometry = geometry.withPrecision(p.withXY(res, tol, x, y));
          w.features =
              database.createFeatureClass(
                  new FeatureClassDefinition(
                      w.spec.name(),
                      "",
                      fields,
                      geometry,
                      new CrsDefinition(def.srid(), def.srid(), def.wkt()),
                      options.spatialIndex()));
        }
      }
    } catch (Exception e) {
      failed = true;
      closeAfterFailure(e);
      throw e;
    }
  }

  private static void validateInputType(FileGdbExportSchema.FieldSpec f, IValueMeta meta) {
    int type = meta.getType();
    boolean numeric =
        type == IValueMeta.TYPE_INTEGER
            || type == IValueMeta.TYPE_NUMBER
            || type == IValueMeta.TYPE_BIGNUMBER;
    boolean valid =
        switch (f.type()) {
          case INT16, INT32, INT64, FLOAT32, FLOAT64 -> numeric;
          case STRING, XML, GUID -> type == IValueMeta.TYPE_STRING;
          case DATETIME, DATE, TIME ->
              type == IValueMeta.TYPE_DATE || type == IValueMeta.TYPE_TIMESTAMP;
          case DATETIME_WITH_OFFSET -> type == IValueMeta.TYPE_STRING;
          case BINARY -> type == IValueMeta.TYPE_BINARY;
          default -> false;
        };
    if (!valid)
      throw new IllegalArgumentException(
          "Incompatible input type for "
              + f.name()
              + ": "
              + meta.getTypeDesc()
              + " -> "
              + f.type());
  }

  private static int index(IRowMeta rm, String name) {
    int i = rm.indexOfValue(name);
    if (i < 0) throw new IllegalArgumentException("Input field not found: " + name);
    return i;
  }

  private static GeometryKind kind(String type) {
    return switch (type.toUpperCase(Locale.ROOT)) {
      case "POINT" -> GeometryKind.POINT;
      case "MULTIPOINT" -> GeometryKind.MULTIPOINT;
      case "LINESTRING", "MULTILINESTRING", "CIRCULARSTRING", "COMPOUNDCURVE", "MULTICURVE" ->
          GeometryKind.LINE;
      case "POLYGON", "MULTIPOLYGON", "CURVEPOLYGON", "MULTISURFACE" -> GeometryKind.POLYGON;
      default -> throw new IllegalArgumentException("Unsupported geometry type: " + type);
    };
  }

  public void write(String dataset, Object[] row) throws Exception {
    if (closed || failed) throw new IllegalStateException("Export session is closed or failed");
    Writer w = writers.get(dataset);
    if (w == null) throw new IllegalArgumentException("Unknown dataset: " + dataset);
    w.rowNumber++;
    try {
      Object[] attributes = new Object[w.positions.length];
      for (int i = 0; i < attributes.length; i++) {
        var f = w.spec.fields().get(i);
        try {
          attributes[i] = convert(f, row[w.positions[i]]);
        } catch (Exception e) {
          throw new IllegalArgumentException("Field " + f.name() + ": " + e.getMessage(), e);
        }
      }
      if (w.table != null) w.table.write(attributes);
      else {
        Object raw = row[w.geometryPosition];
        if (raw != null && !(raw instanceof Geometry))
          throw new IllegalArgumentException("Expected Hop Geometry");
        Geometry geometry = (Geometry) raw;
        if (geometry != null && !geometry.isEmpty()) {
          if (kind(geometry.getGeometryType()) != w.kind)
            throw new IllegalArgumentException("Geometry type differs from schema");
          if (geometry.getSRID() != 0 && geometry.getSRID() != w.srid)
            throw new IllegalArgumentException(
                "Geometry CRS differs from schema; reproject before export");
          String dim = w.spec.geometry().dimension();
          var actual = GeometrySchema.infer(geometry).dimension();
          if (!actual.equals(dim))
            throw new IllegalArgumentException(
                "Geometry dimension differs from schema: " + actual + " / " + dim);
        }
        w.features.write(attributes, new HopCurveAdapter(w.srid).write(geometry));
      }
    } catch (Exception e) {
      failed = true;
      throw new IllegalArgumentException(
          "Dataset " + dataset + ", row " + w.rowNumber + ": " + e.getMessage(), e);
    }
  }

  private Object convert(FileGdbExportSchema.FieldSpec f, Object value) {
    if (value == null) {
      if (!Boolean.TRUE.equals(f.nullable()))
        throw new IllegalArgumentException("NULL is not allowed");
      return null;
    }
    Object result =
        switch (f.type()) {
          case INT16, INT32, INT64 -> {
            if (!(value instanceof Number))
              throw new IllegalArgumentException("Expected numeric value");
            var n = new BigDecimal(value.toString());
            yield switch (f.type()) {
              case INT16 -> n.shortValueExact();
              case INT32 -> n.intValueExact();
              default -> n.longValueExact();
            };
          }
          case FLOAT32, FLOAT64 -> {
            if (!(value instanceof Number n))
              throw new IllegalArgumentException("Expected numeric value");
            double d = n.doubleValue();
            if (!Double.isFinite(d)
                || (f.type() == ch.so.agi.filegdb.table.FileGdbFieldType.FLOAT32
                    && !Float.isFinite(n.floatValue())))
              throw new IllegalArgumentException("Nonfinite or overflowing number");
            yield d;
          }
          case STRING, XML -> {
            if (!(value instanceof String s)) throw new IllegalArgumentException("Expected string");
            if (f.type() == ch.so.agi.filegdb.table.FileGdbFieldType.STRING
                && s.length() > f.field().maxWidth())
              throw new IllegalArgumentException("String exceeds field length");
            yield s;
          }
          case GUID -> {
            if (value instanceof UUID id) yield id;
            if (!(value instanceof String s)
                || !s.matches("\\{?[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}\\}?"))
              throw new IllegalArgumentException("Expected GUID");
            yield UUID.fromString(s.replace("{", "").replace("}", ""));
          }
          case DATETIME, DATE, TIME -> {
            Object v = value;
            if (value instanceof java.util.Date d)
              v =
                  java.time.LocalDateTime.ofInstant(
                      java.time.Instant.ofEpochMilli(d.getTime()), java.time.ZoneOffset.UTC);
            if (f.type() == ch.so.agi.filegdb.table.FileGdbFieldType.DATE
                && v instanceof java.time.LocalDateTime d) v = d.toLocalDate();
            if (f.type() == ch.so.agi.filegdb.table.FileGdbFieldType.TIME
                && v instanceof java.time.LocalDateTime d) v = d.toLocalTime();
            if (!(v instanceof java.time.temporal.Temporal))
              throw new IllegalArgumentException("Expected date/time value");
            yield DomainValues.parse(f.type(), v.toString());
          }
          case DATETIME_WITH_OFFSET -> {
            if (value instanceof String text) yield java.time.OffsetDateTime.parse(text);
            if (!(value instanceof java.time.OffsetDateTime))
              throw new IllegalArgumentException("Expected ISO OffsetDateTime");
            yield value;
          }
          case BINARY -> {
            if (!(value instanceof byte[])) throw new IllegalArgumentException("Expected binary");
            yield value;
          }
          default -> throw new IllegalArgumentException("Unsupported field type");
        };
    if (f.domain() != null && !DomainValues.contains(domains.get(f.domain()), result.toString()))
      throw new IllegalArgumentException("Value outside domain " + f.domain());
    return result;
  }

  public void finish() throws Exception {
    if (closed || failed) throw new IllegalStateException("Cannot publish closed or failed export");
    try {
      for (var w : writers.values()) {
        if (w.table != null) w.table.close();
        if (w.features != null) w.features.close();
      }
      for (var r : schema.relationships()) database.createRelationship(r.definition());
      database.close();
      if (Files.exists(output))
        throw new IllegalArgumentException("Output already exists: " + output);
      Files.move(staging, output, StandardCopyOption.ATOMIC_MOVE);
      finished = true;
    } catch (Exception e) {
      failed = true;
      closeAfterFailure(e);
      throw e;
    }
    close();
  }

  private void closeAfterFailure(Exception original) {
    try {
      close();
    } catch (Exception cleanup) {
      original.addSuppressed(cleanup);
    }
  }

  @Override
  public void close() throws Exception {
    if (closed) return;
    closed = true;
    Exception error = null;
    for (var w : writers.values())
      try {
        if (w.table != null) w.table.close();
        if (w.features != null) w.features.close();
      } catch (Exception e) {
        error = e;
      }
    try {
      if (database != null) database.close();
    } catch (Exception e) {
      error = e;
    }
    if (Files.exists(stagingParent))
      try (var paths = Files.walk(stagingParent)) {
        for (var path : paths.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(path);
      }
    if (error != null && !finished) throw error;
  }
}
