package ch.so.agi.hop.raster.reproject;

import ch.so.agi.hop.raster.core.*;
import java.nio.file.Path;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

public class RasterReprojectTransform
    extends BaseTransform<RasterReprojectMeta, RasterReprojectData> {
  public RasterReprojectTransform(
      TransformMeta transform,
      RasterReprojectMeta meta,
      RasterReprojectData data,
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
        throw new HopTransformException("Invalid reproject output fields", e);
      }
    }
    try {
      String location = value(meta.getSource(), meta.isSourceField(), row);
      var source = data.sources.source(location);
      var extent =
          "BOUNDING_BOX".equals(meta.getExtentMode())
              ? new RasterReprojectRequest.Extent(
                  number(meta.getMinX(), meta.isBboxFields(), row),
                  number(meta.getMinY(), meta.isBboxFields(), row),
                  number(meta.getMaxX(), meta.isBboxFields(), row),
                  number(meta.getMaxY(), meta.isBboxFields(), row))
              : null;
      Path output =
          Path.of(value(meta.getOutput(), meta.isOutputField(), row)).toAbsolutePath().normalize();
      var request =
          new RasterReprojectRequest(
              value(meta.getTargetCrs(), meta.isTargetCrsField(), row),
              number(meta.getResolutionX(), meta.isResolutionFields(), row),
              number(meta.getResolutionY(), meta.isResolutionFields(), row),
              extent,
              RasterReprojectRequest.Interpolation.valueOf(meta.getInterpolation()),
              RasterReprojectRequest.OutputType.valueOf(meta.getOutputType()),
              RasterRowSupport.noData(meta.getSourceNoData(), this),
              RasterRowSupport.noData(meta.getOutputNoData(), this),
              output,
              meta.isOverwrite());
      RasterReproject.write(source, request, this::isStopped);
      Object[] result = RowDataUtil.resizeArray(row, data.outputMeta.size());
      result[data.inputMeta.size()] = output.toString();
      result[data.inputMeta.size() + 1] = "OK";
      putRow(data.outputMeta, result);
    } catch (Exception e) {
      data.sources.close();
      if (isStopped()) return false;
      String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      if (isErrorHandlingEnabled())
        putError(data.inputMeta, row, 1L, message, "", "RASTER_REPROJECT_ERROR");
      else throw new HopTransformException("Raster reprojection failed: " + message, e);
    }
    return true;
  }

  private String value(String setting, boolean field, Object[] row) throws Exception {
    return RasterRowSupport.value(setting, field, row, data.inputMeta, this);
  }

  private double number(String setting, boolean field, Object[] row) throws Exception {
    double value = Double.parseDouble(value(setting, field, row));
    if (!Double.isFinite(value))
      throw new IllegalArgumentException("Grid coordinates and pixel sizes must be finite");
    return value;
  }

  private boolean isErrorHandlingEnabled() {
    var errorMeta = getTransformMeta().getTransformErrorMeta();
    return getTransformMeta().isDoingErrorHandling() || (errorMeta != null && errorMeta.isEnabled());
  }

  @Override
  public void dispose() {
    data.sources.close();
    super.dispose();
  }
}
