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

  public enum Action {
    CREATE_DATASET,
    APPEND_ROWS
  }

  public record InputOptions(Action action, String geometryField, boolean spatialIndex) {}

  private final Path output;
  private ch.so.agi.filegdb.FileGdbEditSession edit;
  private final Runnable cancellation;
  private FileGeodatabase database;
  private final Map<String, Writer> writers = new LinkedHashMap<>();
  private final Map<String, Domain> domains = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  private boolean failed, finished, closed;

  private static final class Writer {
    FileGdbExportSchema.DatasetSpec spec;
    int[] positions;
    int geometryPosition = -1;
    GdbFeatureWriter features;
    GdbTableWriter table;
    long rowNumber;
    GeometryKind kind;
    IRowMeta inputMeta;
    java.util.List<ch.so.agi.filegdb.table.FileGdbField> targetFields;
    Object[] defaults;
    boolean geometryNullable = true;
    int srid;
  }

  public FileGdbExportSession(
      Path output,
      FileGdbExportSchema schema,
      Map<String, IRowMeta> inputs,
      CrsDefinitionResolver crs)
      throws Exception {
    this(output, schema, inputs, null, false, crs, () -> {});
  }

  public FileGdbExportSession(
      Path output,
      FileGdbExportSchema schema,
      Map<String, IRowMeta> inputs,
      Map<String, InputOptions> operations,
      boolean existing,
      CrsDefinitionResolver crs,
      Runnable cancellation)
      throws Exception {
    this.schema =
        schema == null ? new FileGdbExportSchema(1, List.of(), List.of(), List.of()) : schema;
    this.cancellation = cancellation;
    this.output = output.toAbsolutePath().normalize();
    if (operations == null) {
      operations = new LinkedHashMap<>();
      for (var d : this.schema.datasets())
        operations.put(
            d.name(),
            new InputOptions(
                Action.CREATE_DATASET,
                d.geometry() == null ? "" : d.geometry().source(),
                d.geometry() != null && !Boolean.FALSE.equals(d.geometry().spatialIndex())));
    }
    if (!inputs.keySet().equals(operations.keySet()) || inputs.isEmpty())
      throw new IllegalArgumentException("Each selected dataset requires exactly one input");
    var selectedNames = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    for (var op : operations.entrySet()) {
      if (!selectedNames.add(op.getKey()))
        throw new IllegalArgumentException("Duplicate dataset mapping: " + op.getKey());
      if (!existing && op.getValue().action() != Action.CREATE_DATASET)
        throw new IllegalArgumentException("New databases require CREATE_DATASET for every input");
      boolean declared =
          this.schema.datasets().stream().anyMatch(d -> d.name().equals(op.getKey()));
      if (declared != (op.getValue().action() == Action.CREATE_DATASET))
        throw new IllegalArgumentException("JSON must define new datasets only: " + op.getKey());
    }
    if (this.schema.datasets().stream().anyMatch(d -> !inputs.containsKey(d.name())))
      throw new IllegalArgumentException("Each JSON dataset requires one input");
    edit = ch.so.agi.filegdb.FileGdbEditSession.open(this.output, !existing, cancellation);
    database = edit.database();
    try {
      if (existing) this.schema.validate(database);
      else this.schema.validate();
      for (var d : database.domains()) domains.put(d.name(), d);
      for (var d : this.schema.domains()) {
        database.createDomain(d.domain());
        domains.put(d.name(), d.domain());
      }
      for (var op : operations.entrySet()) {
        if (op.getValue().action() == Action.APPEND_ROWS)
          writers.put(
              op.getKey(),
              appendWriter(database, op.getKey(), inputs.get(op.getKey()), op.getValue()));
      }
      // Validate field mappings before opening any output files.
      for (var d : this.schema.datasets()) {
        var w = new Writer();
        w.spec = d;
        IRowMeta rm =
            Objects.requireNonNull(inputs.get(d.name()), "Input metadata required: " + d.name());
        validateCreation(d, rm);
        w.inputMeta = rm;
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
      for (var w : writers.values()) {
        if (w.table != null || w.features != null) continue;
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
                      operations.get(w.spec.name()).spatialIndex()));
        }
      }
    } catch (Exception e) {
      failed = true;
      closeAfterFailure(e);
      throw e;
    }
  }

  private static Writer appendWriter(
      FileGeodatabase database, String name, IRowMeta input, InputOptions options)
      throws Exception {
    Writer w = inspectAppend(database, name, input, options.geometryField());
    if (w.spec.geometry() == null) w.table = database.appendRows(name);
    else w.features = database.appendFeatures(name, options.spatialIndex());
    return w;
  }

  private static Writer inspectAppend(
      FileGeodatabase database, String name, IRowMeta input, String geometrySource)
      throws Exception {
    database.validateAppend(name);
    var dataset =
        database
            .dataset(name)
            .orElseThrow(() -> new IllegalArgumentException("Dataset missing: " + name));
    Writer w = new Writer();
    w.inputMeta = input;
    var specs = new ArrayList<FileGdbExportSchema.FieldSpec>();
    FileGdbExportSchema.GeometrySpec geometry = null;
    try (var table = database.table(name)) {
      w.targetFields =
          table.fields().stream()
              .filter(
                  f ->
                      f.type() != ch.so.agi.filegdb.table.FileGdbFieldType.OBJECTID
                          && f.type() != ch.so.agi.filegdb.table.FileGdbFieldType.GEOMETRY)
              .toList();
      w.positions = new int[w.targetFields.size()];
      w.defaults = new Object[w.positions.length];
      var sources = new HashMap<String, Integer>();
      if (input != null)
        for (int i = 0; i < input.size(); i++) {
          if (sources.put(input.getValueMeta(i).getName().toLowerCase(Locale.ROOT), i) != null)
            throw new IllegalArgumentException(
                "Ambiguous input field: " + input.getValueMeta(i).getName());
        }
      if (table.geomField() != null) {
        var g = table.geomField().geometry();
        w.kind = g.kind();
        w.srid = dataset.crs().effectiveWkid();
        w.geometryNullable = table.geomField().nullable();
        String dimension = "XY" + (g.hasZ() ? "Z" : "") + (g.hasM() ? "M" : "");
        String type =
            switch (w.kind) {
              case POINT -> "POINT";
              case MULTIPOINT -> "MULTIPOINT";
              case LINE -> "MULTILINESTRING";
              case POLYGON -> "MULTIPOLYGON";
              default -> throw new IllegalArgumentException("Unsupported geometry family");
            };
        geometry =
            new FileGdbExportSchema.GeometrySpec(
                geometrySource,
                g.name(),
                type,
                dimension,
                g.wkt(),
                null,
                null,
                null,
                null,
                null,
                null);
        if (input != null) {
          if (geometrySource == null || geometrySource.isBlank())
            throw new IllegalArgumentException("Select an input Geometry field for " + name);
          Integer position = sources.remove(geometrySource.toLowerCase(Locale.ROOT));
          if (position == null
              || !(input.getValueMeta(position)
                  instanceof com.atolcd.hop.core.row.value.ValueMetaGeometry))
            throw new IllegalArgumentException("Select an input Geometry field for " + name);
          w.geometryPosition = position;
        }
      }
      for (var f : table.fields())
        if (f.type() == ch.so.agi.filegdb.table.FileGdbFieldType.OBJECTID
            && sources.containsKey(f.name().toLowerCase(Locale.ROOT)))
          throw new IllegalArgumentException(
              "OBJECTID is generated; rename " + f.name() + " to SOURCE_OBJECTID");
      for (int i = 0; i < w.positions.length; i++) {
        var f = w.targetFields.get(i);
        var spec =
            new FileGdbExportSchema.FieldSpec(
                f.name(), f.name(), f.type(), f.nullable(), f.maxWidth(), f.domain());
        specs.add(spec);
        w.defaults[i] = f.defaultValue();
        Integer position = sources.remove(f.name().toLowerCase(Locale.ROOT));
        w.positions[i] = position == null ? -1 : position;
        if (input != null) {
          if (position == null && !f.nullable() && f.defaultValue() == null)
            throw new IllegalArgumentException("Required input field missing: " + f.name());
          if (position != null) validateInputType(spec, input.getValueMeta(position));
        }
      }
      if (!sources.isEmpty())
        throw new IllegalArgumentException("Unknown input attributes: " + sources.keySet());
    }
    w.spec =
        new FileGdbExportSchema.DatasetSpec(
            name, geometry == null ? "TABLE" : "FEATURE_CLASS", specs, geometry);
    return w;
  }

  public static String preview(Path path, String dataset, IRowMeta input, String geometrySource)
      throws Exception {
    try (var db = FileGeodatabase.open(path)) {
      var w = inspectAppend(db, dataset, input, geometrySource);
      var text = new StringBuilder("Dataset: ").append(dataset).append('\n');
      if (w.spec.geometry() == null) text.append("Tabelle ohne Geometrie\n");
      else
        try (var table = db.table(dataset)) {
          text.append("Geometry: ")
              .append(table.geomField().name())
              .append(" / ")
              .append(w.spec.geometry().type())
              .append(" / ")
              .append(w.spec.geometry().dimension())
              .append("\nCRS: ")
              .append(w.srid)
              .append("\nPrecision: ")
              .append(table.geomField().geometry().precision())
              .append("\nSpatial index: ")
              .append(Files.exists(ch.so.agi.filegdb.index.SpatialIndex.path(table.path())))
              .append('\n');
        }
      for (var f : w.targetFields)
        text.append(f.name())
            .append(" : ")
            .append(f.type())
            .append(f.nullable() ? " nullable" : " required")
            .append(" default=")
            .append(f.defaultValue())
            .append(" domain=")
            .append(f.domain())
            .append('\n');
      for (var d : db.domains())
        if (w.targetFields.stream().anyMatch(f -> d.name().equals(f.domain())))
          text.append(d).append('\n');
      for (var r : db.relationships())
        if (dataset.equalsIgnoreCase(r.originClassName())
            || dataset.equalsIgnoreCase(r.destinationClassName())) text.append(r).append('\n');
      return text.toString();
    }
  }

  public static void validateCreation(FileGdbExportSchema.DatasetSpec spec, IRowMeta input) {
    for (var f : spec.fields())
      validateInputType(f, input.getValueMeta(index(input, f.sourceName())));
    if (spec.geometry() != null
        && !(input.getValueMeta(index(input, spec.geometry().source()))
            instanceof com.atolcd.hop.core.row.value.ValueMetaGeometry))
      throw new IllegalArgumentException(
          "Expected Hop Geometry field: " + spec.geometry().source());
  }

  private static void validateInputType(FileGdbExportSchema.FieldSpec f, IValueMeta meta) {
    int type = meta.getType();
    boolean numeric =
        type == IValueMeta.TYPE_INTEGER
            || type == IValueMeta.TYPE_NUMBER
            || type == IValueMeta.TYPE_BIGNUMBER;
    boolean valid =
        switch (f.type()) {
          case INT16, INT32, INT64, FLOAT32, FLOAT64 -> numeric || type == IValueMeta.TYPE_BOOLEAN;
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
    cancellation.run();
    Writer w = writers.get(dataset);
    if (w == null) throw new IllegalArgumentException("Unknown dataset: " + dataset);
    w.rowNumber++;
    try {
      Object[] attributes = new Object[w.positions.length];
      for (int i = 0; i < attributes.length; i++) {
        var f = w.spec.fields().get(i);
        try {
          Object value = w.positions[i] < 0 ? w.defaults[i] : row[w.positions[i]];
          if (w.positions[i] >= 0
              && w.inputMeta.getValueMeta(w.positions[i]).isStorageBinaryString())
            value = w.inputMeta.getValueMeta(w.positions[i]).convertToNormalStorageType(value);
          if (value instanceof Boolean bool) value = bool ? 1L : 0L;
          attributes[i] = convert(f, value);
          if (w.targetFields != null
              && attributes[i] instanceof java.time.LocalDateTime date
              && !w.targetFields.get(i).highPrecision()
              && date.getNano() != 0)
            throw new IllegalArgumentException(
                "Target datetime field does not support fractional seconds");
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
        if (geometry == null && !w.geometryNullable)
          throw new IllegalArgumentException("Geometry is required");
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
          "FileGDB "
              + output
              + ", Dataset "
              + dataset
              + ", row "
              + w.rowNumber
              + ": "
              + e.getMessage(),
          e);
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
            if (f.type() == ch.so.agi.filegdb.table.FileGdbFieldType.FLOAT32
                && (double) n.floatValue() != d)
              throw new IllegalArgumentException(
                  "Value cannot be represented without loss as FLOAT32");
            if (!(n instanceof Double)
                && !(n instanceof Float)
                && new BigDecimal(n.toString()).compareTo(BigDecimal.valueOf(d)) != 0)
              throw new IllegalArgumentException(
                  "Value cannot be represented without loss as floating point");
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
            if (value instanceof java.sql.Timestamp timestamp)
              v =
                  java.time.LocalDateTime.ofInstant(
                      timestamp.toInstant(), java.time.ZoneOffset.UTC);
            else if (value instanceof java.util.Date d)
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
      cancellation.run();
      edit.commit();
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
      if (edit != null) edit.close();
    } catch (Exception e) {
      error = e;
    }
    if (error != null && !finished) throw error;
  }
}
