package ch.so.agi.hop.vector.formats.shapefile;

import ch.so.agi.hop.vector.core.*;
import ch.so.agi.hop.vector.formats.shapefile.binary.*;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import com.atolcd.hop.gis.geometry.curve.CurveGeometrySupport;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.apache.hop.core.row.RowMeta;
import org.locationtech.jts.geom.*;

/** Streaming SHP/SHX/DBF adapter. CRS services are injected through the neutral core. */
public final class ShapefileProvider implements VectorProvider {
  private final CrsDefinitionResolver crs;

  public ShapefileProvider() {
    this(
        srid -> {
          if (srid > 0)
            throw new IllegalArgumentException("CRS resolver required for EPSG:" + srid);
          return new CrsDefinitionResolver.Definition(srid, "Unknown", "NONE", srid, "");
        });
  }

  public ShapefileProvider(CrsDefinitionResolver crs) {
    this.crs = crs;
  }

  public static String preview(WriteRequest request) {
    return DbfMapping.columns(request).stream()
        .map(
            c ->
                request.rowMeta().getValueMeta(c.index()).getName()
                    + " -> "
                    + c.field().name()
                    + " ("
                    + c.field().type()
                    + ", width="
                    + c.field().length()
                    + ", scale="
                    + c.field().decimalCount()
                    + ")")
        .collect(java.util.stream.Collectors.joining("\n"));
  }

  public VectorFormat format() {
    return VectorFormat.SHAPEFILE;
  }

  public List<LayerSchema> layers(ReadRequest r) throws Exception {
    try (var s = open(r)) {
      return List.of(s.schema());
    }
  }

  public VectorSource open(ReadRequest r) throws Exception {
    return new Source(r, crs);
  }

  public VectorSink create(WriteRequest r) throws Exception {
    return new Sink(r, crs);
  }

  static String base(Path path) {
    String n = path.getFileName().toString();
    if (!n.toLowerCase(Locale.ROOT).endsWith(".shp"))
      throw new IllegalArgumentException("Shapefile path must end in .shp");
    return n.substring(0, n.length() - 4);
  }

  static void read(FileChannel c, ByteBuffer b) throws IOException {
    while (b.hasRemaining())
      if (c.read(b) < 0) throw new EOFException("Truncated Shapefile component");
    b.flip();
  }

  static void write(FileChannel c, ByteBuffer b) throws IOException {
    while (b.hasRemaining()) c.write(b);
  }

  static ShapefileHeader header(FileChannel c) throws Exception {
    c.position(0);
    var h = ShapefileHeader.read(c);
    h.validateMainFileHeader();
    if (h.fileLengthWords() < 50 || 2L * h.fileLengthWords() != c.size())
      throw new IOException("Shapefile length does not match header");
    if (h.shapeType() == ShapeType.MULTIPATCH)
      throw new IllegalArgumentException("MultiPatch is not supported");
    return h;
  }

  private static final class Source implements VectorSource {
    FileChannel shp, shx;
    DbfReader dbf;
    LayerSchema schema;
    ShapefileHeader header;
    ReadRequest r;
    long record;
    ZoneId zone;

    Source(ReadRequest r, CrsDefinitionResolver crs) throws Exception {
      this.r = r;
      try {
        String name = base(r.file());
        if (!r.layer().isBlank() && !r.layer().equalsIgnoreCase(name))
          throw new IllegalArgumentException("Layer not found: " + r.layer());
        var ds = ShapefileDataset.fromPath(r.file(), false);
        zone = ZoneId.of(r.shapefile().timezone());
        shp = FileChannel.open(ds.shp());
        header = header(shp);
        dbf = DbfReader.open(ds.dbf(), DbfMapping.inputCharset(ds, r));
        if (ds.shx() != null) {
          shx = FileChannel.open(ds.shx());
          var index = header(shx);
          if (index.shapeType() != header.shapeType()
              || shx.size() != 100L + 8L * dbf.header().recordCount())
            throw new IOException("Inconsistent SHX header/count");
        }
        String wkt = ds.prj().isPresent() ? Files.readString(ds.prj().get()) : "";
        var definition = crs.parse(r.crsOverride().isBlank() ? wkt : r.crsOverride());
        if (wkt.isBlank() && r.crsOverride().isBlank())
          r.diagnostics().warning("", "unknown-crs", "No PRJ; CRS is unknown");
        RowMeta rm = new RowMeta();
        Set<String> names = new HashSet<>();
        for (var f : dbf.fields()) {
          if (!names.add(f.name().toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Duplicate DBF field: " + f.name());
          rm.addValueMeta(DbfMapping.meta(f));
        }
        String geom = r.geometryField().isBlank() ? "the_geom" : r.geometryField();
        if (names.contains(geom.toLowerCase(Locale.ROOT)))
          throw new IllegalArgumentException("Geometry field collides with attribute: " + geom);
        rm.addValueMeta(new ValueMetaGeometry(geom));
        ShapeType t = header.shapeType();
        String kind =
            switch (t.family()) {
              case 1 -> "Point";
              case 8 -> "MultiPoint";
              case 3 -> "MultiLineString";
              case 5 -> "MultiPolygon";
              default -> "Geometry";
            };
        schema =
            new LayerSchema(
                name,
                geom,
                new GeometrySchema(
                    kind,
                    t.hasZ() ? Ordinate.REQUIRED : Ordinate.ABSENT,
                    t.hasM() || t.hasZ() ? Ordinate.OPTIONAL : Ordinate.ABSENT,
                    definition),
                rm);
      } catch (Exception e) {
        close();
        throw e;
      }
    }

    public LayerSchema schema() {
      return schema;
    }

    public Object[] read() throws Exception {
      while (record < dbf.header().recordCount()) {
        r.diagnostics().nextRecord();
        long offset = shp.position();
        ByteBuffer rh = ByteBuffer.allocate(8);
        ShapefileProvider.read(shp, rh);
        int number = rh.getInt(), words = rh.getInt();
        long length = 2L * words;
        if (number != record + 1
            || length < 4
            || length > Integer.MAX_VALUE
            || offset + 8 + length > shp.size())
          throw new IOException("Invalid SHP record header at " + offset);
        if (shx != null) {
          ByteBuffer index = ByteBuffer.allocate(8);
          ShapefileProvider.read(shx, index);
          if (index.getInt() * 2L != offset || index.getInt() != words)
            throw new IOException("SHX entry differs from SHP record");
        }
        byte[] body = new byte[(int) length];
        ShapefileProvider.read(shp, ByteBuffer.wrap(body));
        var attributes = dbf.readNext().orElseThrow();
        record++;
        Geometry geometry = ShapeCodec.decode(body, header.shapeType(), schema.srid());
        if (attributes.deleted()) {
          r.diagnostics()
              .warning("", "deleted-record", "Deleted DBF row and matching geometry skipped");
          continue;
        }
        Object[] row = new Object[schema.rowMeta().size()];
        for (int i = 0; i < dbf.fields().size(); i++)
          row[i] = DbfMapping.read(dbf.fields().get(i), attributes.values().get(i), zone);
        row[row.length - 1] = geometry;
        return row;
      }
      if (shp.position() != shp.size()) throw new IOException("SHP/DBF record counts differ");
      return null;
    }

    public void close() throws IOException {
      IOException failure = null;
      for (AutoCloseable c : new AutoCloseable[] {dbf, shx, shp})
        if (c != null)
          try {
            c.close();
          } catch (Exception e) {
            if (failure == null) failure = new IOException(e);
            else failure.addSuppressed(e);
          }
      dbf = null;
      shp = null;
      shx = null;
      if (failure != null) throw failure;
    }
  }

  private static final class Sink implements VectorSink {
    static final Set<Path> ACTIVE = java.util.concurrent.ConcurrentHashMap.newKeySet();
    final WriteRequest r;
    Path output, staging;
    FileChannel shp, shx, dbfChannel;
    DbfWriter dbf;
    List<DbfMapping.Column> columns;
    Charset charset;
    ZoneId zone;
    GeometrySchema schema;
    ShapeType type;
    boolean owned, finished, closed, failed;
    int records;
    Envelope bounds = new Envelope();
    double zmin = Double.POSITIVE_INFINITY,
        zmax = Double.NEGATIVE_INFINITY,
        mmin = Double.POSITIVE_INFINITY,
        mmax = Double.NEGATIVE_INFINITY;
    List<Path> published = new ArrayList<>();

    Sink(WriteRequest r, CrsDefinitionResolver crs) throws Exception {
      this.r = r;
      try {
        base(r.file());
        output = r.file().toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        output = output.getParent().toRealPath().resolve(output.getFileName());
        if (!ACTIVE.add(output)) throw new IOException("Another Shapefile writer owns output");
        owned = true;
        String base = base(output);
        for (String ext :
            List.of(".shp", ".shx", ".dbf", ".cpg", ".prj", ".qix", ".fix", ".sbn", ".sbx"))
          if (sidecarExists(output.getParent(), base + ext))
            throw new IOException("Shapefile output/sidecar already exists: " + base + ext);
        schema = Objects.requireNonNull(r.geometry(), "Output geometry schema required");
        type = ShapeCodec.type(schema);
        columns = DbfMapping.columns(r);
        charset =
            r.shapefile().charset().isBlank()
                ? StandardCharsets.UTF_8
                : DbfMapping.charset(r.shapefile().charset());
        zone = ZoneId.of(r.shapefile().timezone());
        staging = Files.createTempDirectory(output.getParent(), ".hop-shapefile-");
        shp = open(base + ".shp");
        shx = open(base + ".shx");
        dbfChannel = open(base + ".dbf");
        patch(shp, 100);
        patch(shx, 100);
        shp.position(100);
        shx.position(100);
        dbf =
            new DbfWriter(
                dbfChannel,
                columns.stream().map(DbfMapping.Column::field).toList(),
                charset,
                ShapefileWriteOptions.OverflowPolicy.TRUNCATE);
        dbf.writeHeader(0);
        Files.writeString(
            staging.resolve(base + ".cpg"), charset.name(), StandardCharsets.US_ASCII);
        var d = schema.crs();
        if (d == null || d.wkt() == null || d.wkt().isBlank()) d = crs.resolve(schema.srid());
        if (d != null && d.wkt() != null && !d.wkt().isBlank() && !d.wkt().equals("undefined"))
          Files.writeString(staging.resolve(base + ".prj"), d.wkt());
        else r.diagnostics().warning("", "unknown-crs", "Output has no PRJ because CRS is unknown");
      } catch (Exception e) {
        close();
        throw e;
      }
    }

    FileChannel open(String name) throws IOException {
      return FileChannel.open(
          staging.resolve(name), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    static boolean sidecarExists(Path parent, String name) throws IOException {
      try (var files = Files.list(parent)) {
        return files.anyMatch(p -> p.getFileName().toString().equalsIgnoreCase(name));
      }
    }

    public boolean write(Object[] row) throws Exception {
      try {
        return writeRow(row);
      } catch (Exception e) {
        failed = true;
        throw e;
      }
    }

    private boolean writeRow(Object[] row) throws Exception {
      r.diagnostics().nextRecord();
      if (closed) throw new IOException("Writer closed");
      Geometry g = (Geometry) row[r.geometryIndex()];
      if (g != null) {
        if (g.getSRID() > 0 && schema.srid() > 0 && g.getSRID() != schema.srid())
          throw new IllegalArgumentException("Geometry SRID differs from output layer");
        if (g.isEmpty())
          r.diagnostics().warning("", "empty-geometry", "EMPTY written as Null Shape");
        if (CurveGeometrySupport.isCurveGeometry(g))
          r.diagnostics()
              .warning("", "curve-linearization", "Curve written using its linearized coordinates");
      }
      byte[] content = ShapeCodec.encode(g, schema);
      long offset = shp.position();
      if (offset + 8L + content.length > Integer.MAX_VALUE
          || dbfChannel.position() + dbf.recordLength() + 1L > Integer.MAX_VALUE)
        throw new IOException("Shapefile component exceeds interoperable 2 GB limit");
      Object[] values = new Object[columns.size()];
      for (int i = 0; i < values.length; i++)
        values[i] =
            DbfMapping.write(
                row[columns.get(i).index()], columns.get(i), charset, zone, r.diagnostics());
      ByteBuffer head = ByteBuffer.allocate(8).putInt(++records).putInt(content.length / 2);
      head.flip();
      ShapefileProvider.write(shp, head);
      ShapefileProvider.write(shp, ByteBuffer.wrap(content));
      ByteBuffer index =
          ByteBuffer.allocate(8).putInt((int) (offset / 2)).putInt(content.length / 2);
      index.flip();
      ShapefileProvider.write(shx, index);
      dbf.writeRecord(values);
      if (g != null && !g.isEmpty()) {
        bounds.expandToInclude(g.getEnvelopeInternal());
        for (var c : g.getCoordinates()) {
          if (Double.isFinite(c.getZ())) {
            zmin = Math.min(zmin, c.getZ());
            zmax = Math.max(zmax, c.getZ());
          }
          if (Double.isFinite(c.getM())) {
            mmin = Math.min(mmin, c.getM());
            mmax = Math.max(mmax, c.getM());
          }
        }
      }
      return true;
    }

    void patch(FileChannel c, long bytes) throws Exception {
      c.position(0);
      new ShapefileHeader(
              9994,
              (int) (bytes / 2),
              1000,
              type,
              bounds.isNull() ? 0 : bounds.getMinX(),
              bounds.isNull() ? 0 : bounds.getMinY(),
              bounds.isNull() ? 0 : bounds.getMaxX(),
              bounds.isNull() ? 0 : bounds.getMaxY(),
              Double.isFinite(zmin) ? zmin : 0,
              Double.isFinite(zmax) ? zmax : 0,
              Double.isFinite(mmin) ? mmin : ShapeCodec.NO_M,
              Double.isFinite(mmax) ? mmax : ShapeCodec.NO_M)
          .write(c);
    }

    public void finish() throws Exception {
      if (closed) throw new IOException("Writer closed");
      try {
        if (failed) throw new IOException("Cannot publish a failed Shapefile export");
        dbf.writeEndOfFile();
        dbf.patchRecordCount(records);
        patch(shp, shp.size());
        patch(shx, shx.size());
        release();
        try (var files = Files.list(staging)) {
          var bundle = files.toList();
          for (var f : bundle)
            if (sidecarExists(output.getParent(), f.getFileName().toString()))
              throw new IOException("Shapefile sidecar already exists: " + f.getFileName());
          for (var f : bundle) {
            Path target = output.getParent().resolve(f.getFileName());
            Files.move(f, target);
            published.add(target);
          }
        }
        finished = true;
      } finally {
        close();
      }
    }

    void release() throws IOException {
      IOException error = null;
      for (var c : new FileChannel[] {shp, shx, dbfChannel})
        if (c != null)
          try {
            c.close();
          } catch (IOException e) {
            if (error == null) error = e;
            else error.addSuppressed(e);
          }
      shp = null;
      shx = null;
      dbfChannel = null;
      if (error != null) throw error;
    }

    public void close() throws Exception {
      if (closed) return;
      closed = true;
      try {
        release();
      } finally {
        try {
          if (!finished) for (var p : published) Files.deleteIfExists(p);
          if (staging != null)
            try (var files = Files.walk(staging)) {
              for (var p : files.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(p);
            }
        } finally {
          if (owned) ACTIVE.remove(output);
        }
      }
    }
  }
}
