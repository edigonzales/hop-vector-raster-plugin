package ch.so.agi.hop.raster.core;

import it.geosolutions.imageio.plugins.tiff.TIFFDirectory;
import it.geosolutions.imageio.plugins.tiff.TIFFField;
import it.geosolutions.imageio.plugins.tiff.TIFFImageWriteParam;
import it.geosolutions.imageio.plugins.tiff.TIFFTag;
import it.geosolutions.imageioimpl.plugins.tiff.TIFFImageWriterSpi;
import java.awt.geom.AffineTransform;
import java.awt.image.RenderedImage;
import java.nio.file.Path;
import javax.imageio.IIOImage;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.stream.FileImageOutputStream;
import org.geotools.coverage.grid.io.imageio.geotiff.CRS2GeoTiffMetadataAdapter;
import org.geotools.coverage.grid.io.imageio.geotiff.GeoTiffConstants;
import org.geotools.gce.geotiff.GeoTiffWriteParams;
import org.geotools.gce.geotiff.GeoTiffWriter;

/** GeoTools georeferencing with explicit ImageIO TIFF metadata for raw sample semantics. */
final class GeoTiffOutput {
  private GeoTiffOutput() {}

  static void write(
      Path file,
      RenderedImage image,
      RasterSource source,
      int band,
      double noData,
      GeoTiffWriteParams options)
      throws Exception {
    var encoder = new CRS2GeoTiffMetadataAdapter(source.crs()).parseCoordinateReferenceSystem();
    var transform = new AffineTransform((AffineTransform) source.gridToWorld());
    // GeoTIFF indices start at zero and refer to the upper-left pixel corner.
    transform.translate(image.getMinX() - .5, image.getMinY() - .5);
    encoder.addGeoShortParam(
        GeoTiffConstants.GTRasterTypeGeoKey, GeoTiffConstants.RasterPixelIsArea);
    encoder.setModelTransformation(transform);
    encoder.setNoData(noData);
    var writer = new TIFFImageWriterSpi().createWriterInstance();
    try (var stream = new FileImageOutputStream(file.toFile())) {
      var params = (TIFFImageWriteParam) options.getAdaptee();
      params.setForceToBigTIFF(options.isForceToBigTIFF());
      var metadata =
          GeoTiffWriter.createGeoTiffIIOMetadata(
              writer, ImageTypeSpecifier.createFromRenderedImage(image), encoder, params);
      var directory = TIFFDirectory.createFromMetadata(metadata);
      // GeoTiffWriter.setMetadataValue only accepts known baseline/georeferencing tags,
      // so it silently omits GDAL_METADATA. Write this standard extension explicitly.
      if (source.scale(band) != 1 || source.offset(band) != 0) {
        String xml =
            "<GDALMetadata><Item name=\"SCALE\" sample=\"0\" role=\"scale\">"
                + source.scale(band)
                + "</Item><Item name=\"OFFSET\" sample=\"0\" role=\"offset\">"
                + source.offset(band)
                + "</Item></GDALMetadata>";
        directory.addTIFFField(
            new TIFFField(
                new TIFFTag("GDAL_METADATA", 42112, 1 << TIFFTag.TIFF_ASCII),
                TIFFTag.TIFF_ASCII,
                1,
                new String[] {xml}));
      }
      writer.setOutput(stream);
      writer.write(
          writer.getDefaultStreamMetadata(params),
          new IIOImage(image, null, directory.getAsMetadata()),
          params);
    } finally {
      writer.dispose();
    }
  }
}
