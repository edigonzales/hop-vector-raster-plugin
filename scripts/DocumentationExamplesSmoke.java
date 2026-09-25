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
      case "prepare" -> {
        prepare(temp);
        prepareCatalog(Path.of(args[0]), temp);
      }
      case "check" -> {
        check(temp);
        checkClippedOverviews(temp);
        checkCatalog(temp);
        checkGeoPackageAppend(temp);
      }
      default -> throw new IllegalArgumentException("Unknown mode: " + args[2]);
    }
  }

  static void checkClippedOverviews(Path temp) throws Exception {
    String[] names = {
      "clip-COG-AVERAGE-False-AUTO", "clip-COG-NEAREST-False-AUTO",
      "clip-GEOTIFF-AVERAGE-True-AUTO", "clip-GEOTIFF-NEAREST-True-AUTO",
      "clip-GEOTIFF-AVERAGE-False-AUTO", "clip-GEOTIFF-AVERAGE-True-NONE"
    };
    for (String name : names) {
      Path file = temp.resolve(name + ".tif");
      boolean overviewExpected = !name.contains("GEOTIFF-AVERAGE-False") && !name.endsWith("NONE");
      try (var stream = javax.imageio.ImageIO.createImageInputStream(file.toFile())) {
        var reader = javax.imageio.ImageIO.getImageReaders(stream).next();
        try {
          reader.setInput(stream);
          if (reader.getNumImages(true) != (overviewExpected ? 2 : 1))
            throw new AssertionError("Unexpected overview count: " + name);
          if (reader.getWidth(0) != 777 || reader.getHeight(0) != 665)
            throw new AssertionError("Clipped dimensions changed: " + name);
          var main = reader.read(0).getRaster();
          if (main.getSampleDouble(0, 0, 0) != (double) (float) 7.09)
            throw new AssertionError("Clipped main pixels shifted: " + name);
          if (overviewExpected) {
            var reduced = reader.read(1).getRaster();
            if (reduced.getWidth() != 389 || reduced.getHeight() != 333)
              throw new AssertionError("Wrong overview dimensions: " + name);
            double expected = main.getSampleDouble(0, 0, 0);
            if (name.contains("AVERAGE"))
              expected =
                  (double)
                      (float)
                          ((expected
                                  + main.getSampleDouble(1, 0, 0)
                                  + main.getSampleDouble(0, 1, 0)
                                  + main.getSampleDouble(1, 1, 0))
                              / 4);
            if (reduced.getSampleDouble(0, 0, 0) != expected
                || reduced.getSampleDouble(388, 332, 0) != main.getSampleDouble(776, 664, 0))
              throw new AssertionError("Wrong overview samples: " + name);
          }
        } finally {
          reader.dispose();
        }
      }
      var geoReader = new GeoTiffReader(file.toFile());
      var coverage = geoReader.read();
      try {
        if (coverage.getEnvelope2D().getMinX() != 2600007
            || coverage.getEnvelope2D().getMaxY() != 1200991)
          throw new AssertionError("Clipped georeferencing changed: " + name);
      } finally {
        coverage.dispose(true);
        geoReader.dispose();
      }
    }
    try (var files = Files.list(temp)) {
      if (files.anyMatch(p -> p.getFileName().toString().startsWith(".hop-raster-")))
        throw new AssertionError("Raster temporary files were not removed");
    }
  }

  static void checkCloudOptimizedGeoTiff(Path temp) throws Exception {
    Path cog = temp.resolve("cog.tif");
    if (!Files.exists(cog)) return;
    try (var source =
        new ch.so.agi.hop.raster.geotools.GeoTiffSource(
            new ch.so.agi.hop.raster.geotools.RasterDatasetRef(cog.toString()))) {
      if (source.bounds().width != 1200 || source.bounds().height != 1000)
        throw new AssertionError("COG bounds are wrong: " + source.bounds());
      if (source.noData(0) == null || source.noData(0) != -9999d)
        throw new AssertionError("COG lost its NoData sentinel");
      var window = new java.awt.Rectangle(100, 200, 2, 2);
      var raster = source.read(new ch.so.agi.hop.raster.geotools.RasterReadRequest(window, 0));
      if (raster.getSampleDouble(100, 200, 0) != 2.0)
        throw new AssertionError("COG pixel value changed: " + raster.getSampleDouble(100, 200, 0));
    }
  }

  static void checkJpegCloudOptimizedGeoTiff(Path temp) throws Exception {
    Path rgb = temp.resolve("input-rgb.tif");
    Path jpegCog = temp.resolve("cog-jpeg.tif");
    if (!Files.exists(jpegCog)) return;
    try (var source =
            new ch.so.agi.hop.raster.geotools.GeoTiffSource(
                new ch.so.agi.hop.raster.geotools.RasterDatasetRef(rgb.toString()));
        var written =
            new ch.so.agi.hop.raster.geotools.GeoTiffSource(
                new ch.so.agi.hop.raster.geotools.RasterDatasetRef(jpegCog.toString()))) {
      if (written.bounds().width != 1200 || written.bounds().height != 1000)
        throw new AssertionError("JPEG COG bounds are wrong: " + written.bounds());
      if (written.bands() != 3
          || written.colorInfo().kind() != ch.so.agi.hop.raster.geotools.RasterColorInfo.Kind.RGB)
        throw new AssertionError("JPEG COG lost its RGB interpretation");
      var window = new java.awt.Rectangle(300, 300, 64, 64);
      for (int band = 0; band < 3; band++) {
        var expected =
            source.read(new ch.so.agi.hop.raster.geotools.RasterReadRequest(window, band));
        var actual =
            written.read(new ch.so.agi.hop.raster.geotools.RasterReadRequest(window, band));
        double expectedMean = 0, actualMean = 0;
        for (int y = 300; y < 364; y++)
          for (int x = 300; x < 364; x++) {
            expectedMean += expected.getSampleDouble(x, y, 0);
            actualMean += actual.getSampleDouble(x, y, 0);
          }
        expectedMean /= 4096;
        actualMean /= 4096;
        if (Math.abs(actualMean - expectedMean) > 8)
          throw new AssertionError(
              "JPEG COG band " + band + " mean deviates: " + actualMean + " vs " + expectedMean);
      }
    }
  }

  static void checkGeoPackageAppend(Path temp) throws Exception {
    var provider =
        new GeoPackageProvider(new ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver());
    for (String layer : java.util.List.of("zones", "zones_copy")) {
      try (var source = provider.open(temp.resolve("gpkg-append.gpkg"), layer, "")) {
        int count = 0;
        while (source.read() != null) count++;
        if (count != (layer.equals("zones") ? 2 : 1))
          throw new AssertionError("Wrong appended feature count: " + layer);
      }
    }
  }

  static void prepareCatalog(Path repo, Path temp) throws Exception {
    var schema =
        ch.so.agi.hop.vector.formats.filegeodatabase.FileGdbExportSchema.read(
            repo.resolve("docs/examples/filegdb/buildings.json"));
    var buildings = new RowMeta();
    buildings.addValueMeta(new org.apache.hop.core.row.value.ValueMetaInteger("id"));
    buildings.addValueMeta(new org.apache.hop.core.row.value.ValueMetaInteger("status"));
    buildings.addValueMeta(new org.apache.hop.core.row.value.ValueMetaNumber("height"));
    buildings.addValueMeta(new ValueMetaGeometry("geometry"));
    var entrances = new RowMeta();
    entrances.addValueMeta(new org.apache.hop.core.row.value.ValueMetaInteger("id"));
    entrances.addValueMeta(new org.apache.hop.core.row.value.ValueMetaInteger("building_id"));
    entrances.addValueMeta(new org.apache.hop.core.row.value.ValueMetaString("label"));
    for (int batch = 0; batch < 2; batch++) {
      try (var session =
          new ch.so.agi.hop.vector.formats.filegeodatabase.FileGdbExportSession(
              temp.resolve(batch == 0 ? "catalog-input.gdb" : "catalog-additions.gdb"),
              schema,
              java.util.Map.of("buildings", buildings, "entrances", entrances),
              new ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver())) {
        var point =
            new GeometryFactory(new PrecisionModel(), 2056)
                .createPoint(new Coordinate(2600000 + batch * 1000, 1200000 + batch * 1000));
        for (long i = 1 + batch * 100; i <= 100 + batch * 100; i++) {
          session.write("buildings", new Object[] {i, 2L, 20.0, point});
          session.write("entrances", new Object[] {i, i, "Entrance " + i});
        }
        session.finish();
      }
    }
  }

  static void checkCatalog(Path temp) throws Exception {
    try (var db = ch.so.agi.filegdb.FileGeodatabase.open(temp.resolve("catalog-output.gdb"))) {
      if (db.domains().size() != 2 || db.relationships().size() != 2)
        throw new AssertionError("Missing domain or relationship");
      try (var added = db.table("inspections")) {
        if (added.rowCount() != 100) throw new AssertionError("Missing added inspections");
      }
      try (var a = db.table("buildings");
          var b = db.table("entrances")) {
        if (a.rowCount() != 200 || b.rowCount() != 100)
          throw new AssertionError("Lost catalog export rows");
        if (!"status".equals(a.field("status").orElseThrow().domain()))
          throw new AssertionError("Lost field domain");
      }
    }
    var rows =
        ch.so.agi.hop.vector.formats.filegeodatabase.FileGdbCatalog.read(
            temp.resolve("catalog-output.gdb"),
            ch.so.agi.hop.vector.formats.filegeodatabase.FileGdbCatalog.Mode.DOMAIN_VALUES,
            "status");
    if (rows.size() != 2) throw new AssertionError("Lost domain codes");
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

    Path large = temp.resolve("input-large.tif");
    int width = 1200, height = 1000;
    var floatRaster =
        java.awt.image.Raster.createWritableRaster(
            new java.awt.image.BandedSampleModel(
                java.awt.image.DataBuffer.TYPE_FLOAT, width, height, 1),
            new java.awt.image.DataBufferFloat(width * height),
            new java.awt.Point());
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) floatRaster.setSample(x, y, 0, (x % 100) + y / 100.0);
    floatRaster.setSample(4, 0, 0, -9999);
    var factory = new GridCoverageFactory();
    var largeCoverage =
        factory.create(
            "large",
            floatRaster,
            new ReferencedEnvelope(
                2600000,
                2600000 + width,
                1201000 - height,
                1201000,
                CRS.decode("EPSG:2056", true)));
    largeCoverage =
        factory.create(
            "large",
            largeCoverage.getRenderedImage(),
            largeCoverage.getEnvelope(),
            null,
            null,
            java.util.Map.of(
                org.eclipse.imagen.media.range.NoDataContainer.GC_NODATA,
                new org.eclipse.imagen.media.range.NoDataContainer(-9999)));
    var largeParams = new org.geotools.gce.geotiff.GeoTiffWriteParams();
    largeParams.setTilingMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
    largeParams.setTiling(512, 512);
    largeParams.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
    largeParams.setCompressionType("Deflate");
    var largeOptions =
        org.geotools.coverage.grid.io.AbstractGridFormat.GEOTOOLS_WRITE_PARAMS.createValue();
    largeOptions.setValue(largeParams);
    var largeWriter = new GeoTiffWriter(large.toFile());
    try {
      largeWriter.write(largeCoverage, largeOptions);
    } finally {
      largeWriter.dispose();
      largeCoverage.dispose(true);
    }

    Path rgb = temp.resolve("input-rgb.tif");
    var rgbImage = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++)
        rgbImage.setRGB(
            x,
            y,
            (sample(128 + 100 * Math.sin(x / 70.0)) << 16)
                | (sample(128 + 90 * Math.cos(y / 55.0)) << 8)
                | sample(128 + 80 * Math.sin((x + y) / 95.0)));
    var rgbCoverage =
        factory.create(
            "rgb",
            rgbImage,
            new ReferencedEnvelope(
                2600000,
                2600000 + width,
                1201000 - height,
                1201000,
                CRS.decode("EPSG:2056", true)));
    var rgbWriter = new GeoTiffWriter(rgb.toFile());
    try {
      rgbWriter.write(rgbCoverage, largeOptions);
    } finally {
      rgbWriter.dispose();
      rgbCoverage.dispose(true);
    }

    try (var raw =
        new ch.so.agi.hop.raster.geotools.GeoTiffSource(
            new ch.so.agi.hop.raster.geotools.RasterDatasetRef(raster.toString()))) {
      var scaled =
          new ch.so.agi.hop.raster.geotools.RasterSource() {
            public java.awt.Rectangle bounds() {
              return raw.bounds();
            }

            public org.geotools.api.referencing.crs.CoordinateReferenceSystem crs() {
              return raw.crs();
            }

            public org.geotools.api.referencing.operation.MathTransform gridToWorld() {
              return raw.gridToWorld();
            }

            public int bands() {
              return 1;
            }

            public int dataType() {
              return raw.dataType();
            }

            public Double noData(int band) {
              return 255d;
            }

            public double scale(int band) {
              return .5;
            }

            public double offset(int band) {
              return 100;
            }

            public boolean valid(double v, int band) {
              return v != 255 && Double.isFinite(v);
            }

            public double physical(double v, int band) {
              return v * .5 + 100;
            }

            public java.awt.image.Raster read(ch.so.agi.hop.raster.geotools.RasterReadRequest r)
                throws Exception {
              return raw.read(r);
            }

            public void close() {}
          };
      var full = new GeometryFactory().toGeometry(new Envelope(2600000, 2600004, 1200000, 1200003));
      ch.so.agi.hop.raster.geotools.RasterClip.write(
          scaled, full, false, 0, 255d, temp.resolve("input-scaled.tif"), false, () -> false);
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

  private static int sample(double value) {
    return Math.max(0, Math.min(255, (int) Math.round(value)));
  }

  static void check(Path temp) throws Exception {
    checkCloudOptimizedGeoTiff(temp);
    checkJpegCloudOptimizedGeoTiff(temp);
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
    try (var scaled =
        new ch.so.agi.hop.raster.geotools.GeoTiffSource(
            new ch.so.agi.hop.raster.geotools.RasterDatasetRef(
                temp.resolve("branch-a.tif").toString()))) {
      if (scaled.scale(0) != .5 || scaled.offset(0) != 100)
        throw new AssertionError("Installed Raster value chain lost scale/offset");
    }
    System.out.println("Installed Hop clip HPL: 2x2 source-grid pixels and georeferencing OK");
  }
}
