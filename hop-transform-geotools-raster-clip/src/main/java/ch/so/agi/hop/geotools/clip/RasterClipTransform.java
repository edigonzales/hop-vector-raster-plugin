package ch.so.agi.hop.geotools.clip;

import ch.so.agi.hop.geotools.common.CrsSupport;
import ch.so.agi.hop.geotools.raster.RasterClip;
import ch.so.agi.hop.geotools.raster.RasterRowSupport;
import java.nio.file.Path;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

public class RasterClipTransform extends BaseTransform<RasterClipMeta, RasterClipData> {
  public RasterClipTransform(
      TransformMeta transform,
      RasterClipMeta meta,
      RasterClipData data,
      int copy,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transform, meta, data, copy, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    Object[] row = getRow();
    if (isStopped()) {
      data.sources.close();
      return false;
    }
    if (row == null) {
      data.sources.close();
      setOutputDone();
      return false;
    }
    if (data.outputMeta == null) {
      try {
        data.inputMeta = getInputRowMeta();
        data.outputMeta = data.inputMeta.clone();
        meta.getFields(data.outputMeta, getTransformName(), null, null, this, null);
      } catch (Exception e) {
        throw new HopTransformException("Invalid clip output fields", e);
      }
    }
    try {
      String location =
          RasterRowSupport.value(meta.getSource(), meta.isSourceField(), row, data.inputMeta, this);
      var source = data.sources.source(location);
      Geometry region;
      boolean polygon = "POLYGON".equals(meta.getClipMethod());
      if (polygon)
        region =
            RasterRowSupport.geometry(
                meta.getGeometryField(), row, data.inputMeta, this, meta.getExplicitCrs(), source);
      else {
        double minX = coordinate(meta.getMinX(), row),
            minY = coordinate(meta.getMinY(), row),
            maxX = coordinate(meta.getMaxX(), row),
            maxY = coordinate(meta.getMaxY(), row);
        if (minX >= maxX || minY >= maxY)
          throw new IllegalArgumentException("Bounding box minimum must be below maximum");
        Geometry box = new GeometryFactory().toGeometry(new Envelope(minX, maxX, minY, maxY));
        // Densify bounding-box edges before a potentially nonlinear CRS transform.
        box =
            org.locationtech.jts.densify.Densifier.densify(
                box, Math.max(maxX - minX, maxY - minY) / 100);
        region = CrsSupport.inRasterCrs(box, resolve(meta.getExplicitCrs()), source.crs());
      }
      if (region == null || region.isEmpty())
        throw new IllegalArgumentException("Clip geometry is empty");
      Path output =
          Path.of(
                  RasterRowSupport.value(
                      meta.getOutput(), meta.isOutputField(), row, data.inputMeta, this))
              .toAbsolutePath()
              .normalize();
      if (!new ch.so.agi.hop.geotools.raster.RasterDatasetRef(location).remote()
          && (output.equals(Path.of(location).toAbsolutePath().normalize())
              || (java.nio.file.Files.exists(output)
                  && java.nio.file.Files.isSameFile(output, Path.of(location)))))
        throw new IllegalArgumentException("Clip output must differ from its input");
      RasterClip.write(
          source,
          region,
          polygon,
          meta.getBand() - 1,
          RasterRowSupport.noData(meta.getNoData(), this),
          output,
          meta.isOverwrite(),
          this::isStopped);
      Object[] result = RowDataUtil.resizeArray(row, data.outputMeta.size());
      result[data.inputMeta.size()] = output.toString();
      result[data.inputMeta.size() + 1] = "OK";
      putRow(data.outputMeta, result);
    } catch (Exception e) {
      data.sources.close();
      if (isStopped()) return false;
      String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      if (getTransformMeta().isDoingErrorHandling())
        putError(data.inputMeta, row, 1L, message, meta.getGeometryField(), "RASTER_CLIP_ERROR");
      else throw new HopTransformException("Raster clip failed: " + message, e);
    }
    return true;
  }

  private double coordinate(String setting, Object[] row) throws Exception {
    double value =
        Double.parseDouble(
            RasterRowSupport.value(setting, meta.isBboxFields(), row, data.inputMeta, this));
    if (!Double.isFinite(value))
      throw new IllegalArgumentException("Bounding box coordinates must be finite");
    return value;
  }

  @Override
  public void dispose() {
    data.sources.close();
    super.dispose();
  }
}
