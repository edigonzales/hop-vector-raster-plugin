package ch.so.agi.hop.vector.formats.filegeodatabase;

import ch.so.agi.filegdb.FileGeodatabase;
import ch.so.agi.filegdb.catalog.CrsDefinition;
import ch.so.agi.filegdb.catalog.Dataset;
import ch.so.agi.filegdb.geometry.FileGdbGeometry;
import ch.so.agi.filegdb.geometry.GeometryFieldDefinition;
import ch.so.agi.filegdb.geometry.GeometryKind;
import ch.so.agi.filegdb.jts.JtsGeometryReader;
import ch.so.agi.filegdb.table.FileGdbField;
import ch.so.agi.filegdb.table.FileGdbGeomField;
import ch.so.agi.filegdb.table.FileGdbRow;
import ch.so.agi.filegdb.table.FileGdbTable;
import ch.so.agi.filegdb.write.FeatureClassDefinition;
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
import java.nio.file.Path;
import java.util.ArrayList;
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
      table = database.table(dataset.name());
      LayerSchema output = schema(dataset, table, request);
      int geometryIndex = table.geomFieldIndex();
      int srid = output.srid();
      HopCurveAdapter jts = new HopCurveAdapter(srid);
      java.util.Iterator<FileGdbRow> iterator;
      if (request.options() instanceof ch.so.agi.hop.vector.core.FileGeodatabaseOptions options
          && options.filter() != null) {
        if (geometryIndex < 0)
          throw new IllegalArgumentException("Spatial filtering requires a feature class");
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
            Object geometry = geometryIndex < 0 ? null : values[geometryIndex];
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
    var options =
        request.options() instanceof ch.so.agi.hop.vector.core.FileGeodatabaseOptions o
            ? o
            : ch.so.agi.hop.vector.core.FileGeodatabaseOptions.defaults();
    boolean append =
        options.writeMode()
            == ch.so.agi.hop.vector.core.FileGeodatabaseOptions.WriteMode.APPEND_ROWS;
    FileGdbExportSchema schema = null;
    String geometrySource = request.rowMeta().getValueMeta(request.geometryIndex()).getName();
    if (!append) {
      FeatureClassDefinition definition = definition(request);
      var fields = new ArrayList<FileGdbExportSchema.FieldSpec>();
      int attribute = 0;
      for (int i = 0; i < request.rowMeta().size(); i++) {
        if (i == request.geometryIndex()) continue;
        var f = definition.fields().get(attribute++);
        fields.add(
            new FileGdbExportSchema.FieldSpec(
                request.rowMeta().getValueMeta(i).getName(),
                f.name(),
                f.type(),
                f.nullable(),
                f.maxWidth() > 0 ? f.maxWidth() : null,
                f.domain()));
      }
      var g = definition.geometry();
      var p = g.precision();
      String geometryCrs =
          g.wkt().isBlank() && request.geometry().srid() > 0
              ? "EPSG:" + request.geometry().srid()
              : g.wkt();
      var geometry =
          new FileGdbExportSchema.GeometrySpec(
              geometrySource,
              g.name(),
              request.geometry().type(),
              request.geometry().dimension(),
              geometryCrs,
              "LEGACY",
              p.xyResolution(),
              p.xyTolerance(),
              p.xOrigin(),
              p.yOrigin(),
              options.spatialIndex());
      schema =
          new FileGdbExportSchema(
              1,
              List.of(),
              List.of(
                  new FileGdbExportSchema.DatasetSpec(
                      request.layer(), "FEATURE_CLASS", fields, geometry)),
              List.of());
    } else {
      try (var db = FileGeodatabase.open(request.file())) {
        if (!db.dataset(request.layer())
            .orElseThrow(() -> new IllegalArgumentException("Target dataset missing"))
            .isFeatureClass())
          throw new IllegalArgumentException("Use FileGDB Writer for attribute tables");
      }
    }
    var operation =
        new FileGdbExportSession.InputOptions(
            append
                ? FileGdbExportSession.Action.APPEND_ROWS
                : FileGdbExportSession.Action.CREATE_DATASET,
            geometrySource,
            options.spatialIndex());
    var session =
        new FileGdbExportSession(
            request.file(),
            schema,
            java.util.Map.of(request.layer(), request.rowMeta()),
            java.util.Map.of(request.layer(), operation),
            options.writeMode()
                != ch.so.agi.hop.vector.core.FileGeodatabaseOptions.WriteMode.CREATE_DATABASE,
            crs,
            () -> {
              try {
                request.checkCancelled();
              } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
              }
            });
    return new VectorSink() {
      public boolean write(Object[] row) throws Exception {
        session.write(request.layer(), row);
        return true;
      }

      public void finish() throws Exception {
        session.finish();
      }

      public void close() throws Exception {
        session.close();
      }
    };
  }

  private LayerSchema schema(Dataset dataset, FileGdbTable table, ReadRequest request)
      throws Exception {
    FileGdbGeomField geomField = table.geomField();
    IRowMeta rowMeta = new RowMeta();
    for (FileGdbField field : table.fields()) {
      rowMeta.addValueMeta(valueMeta(field));
    }
    if (geomField == null)
      return new LayerSchema(dataset.name(), "", (GeometrySchema) null, rowMeta);
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
    IValueMeta valueMeta =
        switch (field.type()) {
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
    if (field.type() == ch.so.agi.filegdb.table.FileGdbFieldType.STRING && field.maxWidth() > 0) {
      valueMeta.setLength(field.maxWidth());
    }
    return valueMeta;
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
}
