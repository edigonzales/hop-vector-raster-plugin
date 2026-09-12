import ch.so.agi.hop.vector.core.*;
import ch.so.agi.hop.vector.formats.geopackage.GeoPackageProvider;
import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import org.apache.hop.core.HopEnvironment;
import org.apache.hop.core.row.RowMeta;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.*;

/** Prepares and validates deterministic fixtures for the installed Hop smoke. */
public class DocumentationExamplesSmoke {
  public static void main(String[] args) throws Exception {
    HopEnvironment.init();
    if (args.length < 3) throw new IllegalArgumentException("Expected prepare or check");
    Path temp = Path.of(args[1]);
    switch (args[2]) {
      case "prepare" -> prepare(temp);
      case "check" -> check(temp);
      default -> throw new IllegalArgumentException("Unknown mode: " + args[2]);
    }
  }

  static void prepare(Path temp) throws Exception {
    Files.createDirectories(temp);
    Path raster = temp.resolve("input.tif");
    var image = new BufferedImage(4, 3, BufferedImage.TYPE_BYTE_GRAY);
    for (int y = 0; y < 3; y++)
      for (int x = 0; x < 4; x++) image.getRaster().setSample(x, y, 0, x + y * 4);
    var coverage =
        new GridCoverageFactory()
            .create(
                "fixture",
                image,
                new ReferencedEnvelope(
                    2600000, 2600004, 1200000, 1200003, CRS.decode("EPSG:2056", true)));
    var writer = new GeoTiffWriter(raster.toFile());
    try {
      writer.write(coverage);
    } finally {
      writer.dispose();
      coverage.dispose(true);
    }

    var geometry =
        new GeometryFactory(new PrecisionModel(), 2056)
            .toGeometry(new Envelope(2600000, 2600004, 1200000, 1200003));
    var fields = new RowMeta();
    fields.addValueMeta(new org.apache.hop.core.row.value.ValueMetaString("name"));
    fields.addValueMeta(new ValueMetaGeometry("geometry"));
    Class.forName("org.sqlite.JDBC");
    var provider =
        new GeoPackageProvider(new ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver());
    try (var sink =
        provider.create(
            new WriteRequest(
                temp.resolve("zones.gpkg"),
                "zones",
                fields,
                1,
                geometry,
                ch.so.agi.hop.vector.core.GeometrySchema.infer(geometry),
                null,
                ch.so.agi.hop.vector.core.Diagnostics.NONE))) {
      sink.write(new Object[] {"whole raster", geometry});
      sink.finish();
    }
    var curve =
        new com.atolcd.hop.gis.geometry.curve.CircularString(
            new Coordinate[] {
              new CoordinateXYZM(2600005, 1200000, 1, 10),
              new CoordinateXYZM(2600000, 1200005, 2, 20),
              new CoordinateXYZM(2599995, 1200000, 3, 30)
            },
            new GeometryFactory(new PrecisionModel(), 2056));
    var curveFields = new RowMeta();
    curveFields.addValueMeta(new org.apache.hop.core.row.value.ValueMetaDate("created"));
    curveFields.addValueMeta(new ValueMetaGeometry("geometry"));
    var fgdb =
        new ch.so.agi.hop.vector.formats.filegeodatabase.FileGeodatabaseProvider(
            new ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver());
    try (var sink =
        fgdb.create(
            new WriteRequest(
                temp.resolve("curves.gdb"),
                "curves",
                curveFields,
                1,
                curve,
                GeometrySchema.infer(curve),
                FileGeodatabaseOptions.defaults(),
                Diagnostics.NONE))) {
      for (int i = 0; i < 1025; i++)
        sink.write(new Object[] {new java.util.Date(1700000000000L), curve});
      sink.finish();
    }
    System.out.println("Prepared deterministic raster and vector fixtures");
  }

  static void check(Path temp) throws Exception {
    var fgdb =
        new ch.so.agi.hop.vector.formats.filegeodatabase.FileGeodatabaseProvider(
            new ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver());
    try (var source =
        fgdb.open(new ReadRequest(temp.resolve("curves-copy.gdb"), "curves", "geometry"))) {
      var rm = source.schema().rowMeta();
      int gi = rm.indexOfValue("geometry"),
          di = rm.indexOfValue("created"),
          oi = rm.indexOfValue("SOURCE_OBJECTID");
      int count = 0;
      Object[] row;
      while ((row = source.read()) != null) {
        count++;
        if (!com.atolcd.hop.gis.geometry.curve.CurveGeometrySupport.isCurveGeometry(
            (Geometry) row[gi])) throw new AssertionError("Lost exact curve");
        if (!(row[di] instanceof java.util.Date d) || d.getTime() != 1700000000000L)
          throw new AssertionError("Lost date");
        if (((Number) row[oi]).longValue() != count)
          throw new AssertionError("Lost source object id");
        var seq = ((LineString) row[gi]).getCoordinateSequence();
        if (!seq.hasZ() || !seq.hasM()) throw new AssertionError("Lost Z/M");
      }
      if (count != 1025) throw new AssertionError("Expected 1025 curves, got " + count);
      if (source.schema().xyPrecision().resolution() != 0.001)
        throw new AssertionError("Lost configured precision");
    }

    var reader = new GeoTiffReader(temp.resolve("clip.tif").toFile());
    var result = reader.read();
    try {
      var pixels = result.getRenderedImage().getData();
      if (pixels.getWidth() != 2
          || pixels.getHeight() != 2
          || pixels.getSampleDouble(0, 0, 0) != 1
          || pixels.getSampleDouble(1, 1, 0) != 6)
        throw new AssertionError("Clip pixels differ from documented source-grid semantics");
      if (result.getEnvelope2D().getMinX() != 2600001
          || result.getEnvelope2D().getMaxY() != 1200003)
        throw new AssertionError("Clip georeferencing mismatch");
    } finally {
      result.dispose(true);
      reader.dispose();
    }
    System.out.println("Installed Hop clip HPL: 2x2 source-grid pixels and georeferencing OK");
  }
}
