package ch.so.agi.hop.vector.formats.filegeodatabase;

import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.catalog.CrsDefinition;
import ch.so.agi.filegdb.catalog.Dataset;
import ch.so.agi.filegdb.geometry.FileGdbGeometry;
import ch.so.agi.filegdb.geometry.GeometryFieldDefinition;
import ch.so.agi.filegdb.geometry.GeometryKind;
import ch.so.agi.filegdb.jts.JtsGeometryReader;
import ch.so.agi.filegdb.jts.JtsGeometryWriter;
import ch.so.agi.filegdb.table.FileGdbField;
import ch.so.agi.filegdb.table.FileGdbGeomField;
import ch.so.agi.filegdb.table.FileGdbRow;
import ch.so.agi.filegdb.table.FileGdbTable;
import ch.so.agi.filegdb.write.FeatureClassDefinition;
import ch.so.agi.filegdb.write.GdbFeatureWriter;
import ch.so.agi.hop.vector.core.CrsDefinitionResolver;
import ch.so.agi.hop.vector.core.GeometrySchema;
import ch.so.agi.hop.vector.core.LayerSchema;
import ch.so.agi.hop.vector.core.Ordinate;
import ch.so.agi.hop.vector.core.ReadRequest;
import ch.so.agi.hop.vector.core.VectorFormat;
import ch.so.agi.hop.vector.core.VectorProvider;
import ch.so.agi.hop.vector.core.VectorSink;
import ch.so.agi.hop.vector.core.VectorSource;
import ch.so.agi.hop.vector.core.WriteRequest;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaBinary;
import org.apache.hop.core.row.value.ValueMetaDate;
import org.apache.hop.core.row.value.ValueMetaInteger;
import org.apache.hop.core.row.value.ValueMetaNumber;
import org.apache.hop.core.row.value.ValueMetaString;
import org.locationtech.jts.geom.Geometry;

/**
 * Vector adapter for Esri file geodatabases, based on the standalone {@code filegdb4j} library. No
 * GDAL runtime is required.
 */
public final class FileGeodatabaseProvider implements VectorProvider {

  private final CrsDefinitionResolver crs;
  private final JtsGeometryWriter jtsWriter = new JtsGeometryWriter();

  public FileGeodatabaseProvider(CrsDefinitionResolver crs) {
    this.crs = crs;
  }

  @Override
  public VectorFormat format() {
    return VectorFormat.FILEGEODATABASE;
  }

  @Override
  public List<LayerSchema> layers(ReadRequest request) throws Exception {
    Path file = request.file();
    try (FileGeodatabase database = FileGeodatabase.open(file)) {
      List<LayerSchema> layers = new ArrayList<>();
      for (Dataset dataset : database.datasets()) {
        if (!dataset.isFeatureClass()) {
          continue;
        }
        try (FileGdbTable table = database.table(dataset.name())) {
          layers.add(schema(dataset, table, request));
        }
      }
      if (layers.isEmpty()) {
        throw new IllegalArgumentException("Vector dataset contains no layers");
      }
      return List.copyOf(layers);
    }
  }

  @Override
  public VectorSource open(ReadRequest request) throws Exception {
    FileGeodatabase database = FileGeodatabase.open(request.file());
    FileGdbTable table = null;
    try {
      Dataset dataset =
          database
              .dataset(request.layer())
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Layer '" + request.layer() + "' not found in " + request.file()));
      if (!dataset.isFeatureClass()) {
        throw new IllegalArgumentException("Dataset is not a feature class: " + dataset.name());
      }
      table = database.table(dataset.name());
      LayerSchema output = schema(dataset, table, request);
      int geometryIndex = table.geomFieldIndex();
      int srid = output.srid();
      HopCurveAdapter jts = new HopCurveAdapter(srid);
      java.util.Iterator<FileGdbRow> iterator;
      if (request.options() instanceof ch.so.agi.hop.vector.core.FileGeodatabaseOptions options
          && options.filter() != null) {
        var b = options.filter();
        var query =
            table.query(
                new ch.so.agi.filegdb.geometry.Envelope(b.xMin(), b.yMin(), b.xMax(), b.yMax()));
        request
            .diagnostics()
            .warning(
                "",
                "SPATIAL_FILTER",
                query.indexUsed()
                    ? "Using FileGDB spatial index"
                    : "Scanning: no compatible envelope spatial index");
        iterator = query.iterator();
      } else iterator = table.iterator();
      FileGdbTable sourceTable = table;
      FileGeodatabase sourceDatabase = database;
      return new VectorSource() {
        private boolean warnedCurveFallback;

        @Override
        public LayerSchema schema() {
          return output;
        }

        @Override
        public Object[] read() {
          while (iterator.hasNext()) {
            FileGdbRow row = iterator.next();
            Object[] values = row.values();
            for (int i = 0; i < values.length; i++) {
              Object v = values[i];
              if (v instanceof java.time.LocalDateTime d)
                values[i] = java.util.Date.from(d.toInstant(java.time.ZoneOffset.UTC));
              else if (v instanceof java.time.LocalDate d)
                values[i] =
                    java.util.Date.from(d.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
              else if (v instanceof java.time.LocalTime d)
                values[i] =
                    java.util.Date.from(
                        d.atDate(java.time.LocalDate.of(1970, 1, 1))
                            .toInstant(java.time.ZoneOffset.UTC));
              else if (v instanceof java.time.OffsetDateTime d)
                values[i] = java.util.Date.from(d.toInstant());
              else if (v instanceof java.util.UUID id) values[i] = id.toString();
            }
            Object geometry = values[geometryIndex];
            if (geometry != null) {
              FileGdbGeometry nativeGeometry = (FileGdbGeometry) geometry;
              var parts =
                  nativeGeometry instanceof ch.so.agi.filegdb.geometry.FileGdbPolyline l
                      ? l.parts()
                      : nativeGeometry instanceof ch.so.agi.filegdb.geometry.FileGdbPolygon p
                          ? p.parts()
                          : java.util.List.<ch.so.agi.filegdb.geometry.FileGdbPart>of();
              if (!warnedCurveFallback
                  && parts.stream()
                      .flatMap(p -> p.segments().stream())
                      .anyMatch(
                          s -> !(s instanceof ch.so.agi.filegdb.geometry.CircularArcSegment))) {
                request
                    .diagnostics()
                    .warning(
                        "",
                        "CURVE_LINEARIZED",
                        "Bezier/ellipse curves were linearized by the FileGDB reader");
                warnedCurveFallback = true;
              }
              Geometry jtsGeometry = jts.read(nativeGeometry);
              jtsGeometry.setSRID(srid);
              values[geometryIndex] = jtsGeometry;
            }
            return values;
          }
          return null;
        }

        @Override
        public void close() throws Exception {
          try {
            sourceTable.close();
          } finally {
            sourceDatabase.close();
          }
        }
      };
    } catch (Exception e) {
      if (table != null) {
        table.close();
      }
      database.close();
      throw e;
    }
  }

  @Override
  public VectorSink create(WriteRequest request) throws Exception {
    Path output = request.file().toAbsolutePath().normalize();
    if (Files.exists(output)) {
      throw new IllegalArgumentException("Output already exists: " + output);
    }
    Path outputParent = output.getParent();
    if (outputParent == null) {
      throw new IllegalArgumentException("Output has no parent directory: " + output);
    }
    Path stagingParent = Files.createTempDirectory(outputParent, ".hop-filegdb-");
    Path staging = stagingParent.resolve("output.gdb");

    FileGeodatabase database = null;
    GdbFeatureWriter writer = null;
    try {
      database = FileGeodatabase.create(staging);
      FeatureClassDefinition definition = definition(request);
      writer = database.createFeatureClass(definition);

      FileGeodatabase sinkDatabase = database;
      GdbFeatureWriter sinkWriter = writer;
      IRowMeta rowMeta = request.rowMeta();
      int geometryIndex = request.geometryIndex();
      List<IValueMeta> attributeMetas = new ArrayList<>();
      for (int i = 0; i < rowMeta.size(); i++) {
        if (i != geometryIndex) {
          attributeMetas.add(rowMeta.getValueMeta(i));
        }
      }
      int[] attributePositions =
          java.util.stream.IntStream.range(0, rowMeta.size())
              .filter(i -> i != geometryIndex)
              .toArray();
      return new VectorSink() {
        private boolean finished;
        private boolean failed;

        @Override
        public boolean write(Object[] row) throws Exception {
          request.checkCancelled();
          try {
            Object[] attributes = new Object[attributeMetas.size()];
            for (int i = 0; i < attributeMetas.size(); i++) {
              attributes[i] = convert(attributeMetas.get(i), row[attributePositions[i]]);
            }
            Geometry geometry = row[geometryIndex] instanceof Geometry g && !g.isEmpty() ? g : null;
            FileGdbGeometry value =
                geometry == null
                    ? null
                    : new HopCurveAdapter(geometry == null ? 0 : geometry.getSRID())
                        .write(geometry);
            sinkWriter.write(attributes, value);
            return true;
          } catch (Exception e) {
            failed = true;
            throw e;
          }
        }

        @Override
        public void finish() throws Exception {
          if (failed) {
            close();
            throw new IllegalStateException("Cannot publish failed file geodatabase export");
          }
          sinkWriter.close();
          sinkDatabase.close();
          Files.move(staging, output, StandardCopyOption.ATOMIC_MOVE);
          finished = true;
          deleteRecursively(stagingParent);
        }

        @Override
        public void close() throws Exception {
          if (finished) {
            return;
          }
          try {
            sinkWriter.close();
          } catch (Exception ignored) {
            // already closed or failed
          }
          try {
            sinkDatabase.close();
          } catch (Exception ignored) {
            // already closed or failed
          }
          deleteRecursively(stagingParent);
        }
      };
    } catch (Exception e) {
      if (writer != null) {
        try {
          writer.close();
        } catch (Exception ignored) {
          // original exception wins
        }
      }
      if (database != null) {
        try {
          database.close();
        } catch (Exception ignored) {
          // original exception wins
        }
      }
      deleteRecursively(stagingParent);
      throw e;
    }
  }

  private LayerSchema schema(Dataset dataset, FileGdbTable table, ReadRequest request)
      throws Exception {
    FileGdbGeomField geomField = table.geomField();
    if (geomField == null) {
      throw new IllegalArgumentException("Dataset has no geometry field: " + dataset.name());
    }
    IRowMeta rowMeta = new RowMeta();
    for (FileGdbField field : table.fields()) {
      rowMeta.addValueMeta(valueMeta(field));
    }
    int geometryIndex = table.geomFieldIndex();
    String geometryColumn = rowMeta.getValueMeta(geometryIndex).getName();
    if (!request.geometryField().isBlank()) {
      int collision = rowMeta.indexOfValue(request.geometryField());
      if (collision >= 0 && collision != geometryIndex) {
        throw new IllegalArgumentException("Geometry output name collides with an attribute");
      }
      rowMeta.getValueMeta(geometryIndex).setName(request.geometryField());
      geometryColumn = request.geometryField();
    }
    CrsDefinitionResolver.Definition definition = definition(dataset, geomField);
    if (!request.crsOverride().isBlank()) {
      definition = crs.parse(request.crsOverride());
    }
    GeometrySchema geometry =
        new GeometrySchema(
            displayGeometryType(table, geomField),
            geomField.geometry().hasZ() ? Ordinate.REQUIRED : Ordinate.ABSENT,
            geomField.geometry().hasM() ? Ordinate.OPTIONAL : Ordinate.ABSENT,
            definition);
    var precision = geomField == null ? null : geomField.geometry().precision();
    return new LayerSchema(
        dataset.name(),
        geometryColumn,
        geometry,
        rowMeta,
        precision == null
            ? null
            : new LayerSchema.XYPrecision(
                precision.xyResolution(),
                precision.xyTolerance(),
                precision.xOrigin(),
                precision.yOrigin()));
  }

  private CrsDefinitionResolver.Definition definition(Dataset dataset, FileGdbGeomField geomField) {
    CrsDefinition definition = dataset.crs();
    if (definition != null && definition.wkt() != null && !definition.wkt().isBlank()) {
      try {
        return crs.parse(definition.wkt());
      } catch (Exception e) {
        // fall through to the WKID based lookup
      }
    }
    int srid = definition == null ? 0 : definition.effectiveWkid();
    if (srid > 0) {
      try {
        return crs.resolve(srid);
      } catch (Exception e) {
        // fall through to an undefined CRS
      }
    }
    return new CrsDefinitionResolver.Definition(0, "", "", 0, "");
  }

  private static String displayGeometryType(FileGdbTable table, FileGdbGeomField geomField) {
    try {
      int checked = 0;
      JtsGeometryReader reader = new JtsGeometryReader(0);
      for (FileGdbRow row : table) {
        if (row.geometry() != null) {
          Geometry geometry = reader.read(row.geometry());
          if (geometry != null && !geometry.isEmpty()) {
            return geometry.getGeometryType();
          }
        }
        if (++checked >= 100) {
          break;
        }
      }
    } catch (RuntimeException e) {
      // fall back to the table level type
    }
    return switch (geomField.geometry().kind()) {
      case POINT -> "Point";
      case MULTIPOINT -> "MultiPoint";
      case LINE -> "MultiLineString";
      case POLYGON, MULTIPATCH -> "MultiPolygon";
      case NONE -> "Geometry";
    };
  }

  private static IValueMeta valueMeta(FileGdbField field) {
    return switch (field.type()) {
      case INT16, INT32, INT64, OBJECTID -> new ValueMetaInteger(field.name());
      case FLOAT32, FLOAT64 -> new ValueMetaNumber(field.name());
      case STRING, XML, GUID, GLOBALID -> new ValueMetaString(field.name());
      case DATETIME, DATE, TIME, DATETIME_WITH_OFFSET -> new ValueMetaDate(field.name());
      case BINARY -> new ValueMetaBinary(field.name());
      case GEOMETRY -> new ValueMetaGeometry(field.name());
      default ->
          throw new IllegalArgumentException(
              "Unsupported file geodatabase field type: "
                  + field.type()
                  + " ("
                  + field.name()
                  + ")");
    };
  }

  private FeatureClassDefinition definition(WriteRequest request) throws Exception {
    List<FileGdbField> fields = new ArrayList<>();
    for (int i = 0; i < request.rowMeta().size(); i++) {
      if (i == request.geometryIndex()) {
        continue;
      }
      fields.add(field(request.rowMeta().getValueMeta(i)));
    }
    String geometryName = request.rowMeta().getValueMeta(request.geometryIndex()).getName();
    GeometryKind kind = geometryKind(request.geometry().type());
    GeometryFieldDefinition geometry =
        GeometryFieldDefinition.of(geometryName, kind)
            .withNullable(true)
            .withWkt(crsWkt(request.geometry()));
    if (request.geometry().z() != Ordinate.ABSENT) {
      geometry = geometry.withZ();
    }
    if (request.geometry().m() != Ordinate.ABSENT) {
      geometry = geometry.withM();
    }
    var options =
        request.options() instanceof ch.so.agi.hop.vector.core.FileGeodatabaseOptions o
            ? o
            : ch.so.agi.hop.vector.core.FileGeodatabaseOptions.defaults();
    var precision = geometry.precision();
    double resolution = precision.xyResolution(),
        tolerance = precision.xyTolerance(),
        x = precision.xOrigin(),
        y = precision.yOrigin();
    if (options.precisionMode().equals("AUTO")) {
      if (options.xyResolution() == null) {
        if (crs.isGeographic(request.geometry().crs())) {
          resolution = 1e-9;
          tolerance = 8.983153e-9;
          x = -400;
          y = -400;
        } else {
          double unit = crs.linearUnitToMetres(request.geometry().crs());
          resolution = 0.0001 / unit;
          tolerance = 0.001 / unit;
        }
      }
    }
    if (options.xyResolution() != null) {
      resolution = options.xyResolution();
      tolerance = 10 * resolution;
    }
    if (options.xyTolerance() != null) tolerance = options.xyTolerance();
    if (options.xOrigin() != null) {
      x = options.xOrigin();
      y = options.yOrigin();
    }
    geometry = geometry.withPrecision(precision.withXY(resolution, tolerance, x, y));
    int srid = request.geometry().srid();
    FeatureClassDefinition.Builder builder =
        FeatureClassDefinition.builder(request.layer())
            .geometry(geometry)
            .spatialIndex(options.spatialIndex())
            .crs(new CrsDefinition(srid, srid > 0 ? srid : null, crsWkt(request.geometry())));
    for (FileGdbField field : fields) {
      builder.field(field);
    }
    return builder.build();
  }

  private static String crsWkt(GeometrySchema geometry) {
    return geometry.crs() == null || geometry.crs().wkt() == null ? "" : geometry.crs().wkt();
  }

  private static GeometryKind geometryKind(String type) {
    return switch (type.toUpperCase(Locale.ROOT)) {
      case "POINT" -> GeometryKind.POINT;
      case "MULTIPOINT" -> GeometryKind.MULTIPOINT;
      case "LINESTRING",
              "MULTILINESTRING",
              "LINEARRING",
              "LINE",
              "POLYLINE",
              "CIRCULARSTRING",
              "COMPOUNDCURVE",
              "MULTICURVE" ->
          GeometryKind.LINE;
      case "POLYGON", "MULTIPOLYGON", "CURVEPOLYGON", "MULTISURFACE" -> GeometryKind.POLYGON;
      default -> throw new IllegalArgumentException("Unsupported geometry type: " + type);
    };
  }

  private static FileGdbField field(IValueMeta valueMeta) {
    String name =
        valueMeta.getName().equalsIgnoreCase("OBJECTID") ? "SOURCE_OBJECTID" : valueMeta.getName();
    int width = valueMeta.getLength() > 0 ? Math.min(65535, valueMeta.getLength()) : 255;
    return switch (valueMeta.getType()) {
      case IValueMeta.TYPE_BOOLEAN -> FileGdbField.smallInteger(name).asNullable();
      case IValueMeta.TYPE_INTEGER -> FileGdbField.bigInteger(name).asNullable();
      case IValueMeta.TYPE_NUMBER, IValueMeta.TYPE_BIGNUMBER ->
          FileGdbField.real(name).asNullable();
      case IValueMeta.TYPE_STRING -> FileGdbField.string(name, width).asNullable();
      case IValueMeta.TYPE_DATE, IValueMeta.TYPE_TIMESTAMP ->
          FileGdbField.dateTime(name).withHighPrecision().asNullable();
      case IValueMeta.TYPE_BINARY -> FileGdbField.binary(name).asNullable();
      default ->
          throw new IllegalArgumentException(
              "Unsupported Hop attribute type: " + valueMeta.getTypeDesc());
    };
  }

  private static Object convert(IValueMeta valueMeta, Object value) {
    if (value == null) {
      return null;
    }
    return switch (valueMeta.getType()) {
      case IValueMeta.TYPE_BOOLEAN -> Boolean.TRUE.equals(value) ? 1L : 0L;
      default -> value;
    };
  }

  private static void deleteRecursively(Path directory) {
    if (directory == null || !Files.exists(directory)) {
      return;
    }
    try (var paths = Files.walk(directory)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    } catch (IOException e) {
      // best effort cleanup
    }
  }
}
