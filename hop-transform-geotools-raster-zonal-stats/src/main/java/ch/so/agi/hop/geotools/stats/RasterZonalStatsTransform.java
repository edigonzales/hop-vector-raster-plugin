package ch.so.agi.hop.geotools.stats;

import ch.so.agi.hop.geotools.raster.RasterRowSupport;
import ch.so.agi.hop.geotools.raster.ZonalStatistics;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;

public class RasterZonalStatsTransform
    extends BaseTransform<RasterZonalStatsMeta, RasterZonalStatsData> {
  public RasterZonalStatsTransform(
      TransformMeta transform,
      RasterZonalStatsMeta meta,
      RasterZonalStatsData data,
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
        throw new HopTransformException("Invalid raster output fields", e);
      }
    }
    try {
      var source =
          data.sources.source(
              RasterRowSupport.value(
                  meta.getSource(), meta.isSourceField(), row, data.inputMeta, this));
      var geometry =
          RasterRowSupport.geometry(
              meta.getGeometryField(), row, data.inputMeta, this, meta.getExplicitCrs(), source);
      var result =
          ZonalStatistics.compute(
              source,
              geometry,
              meta.getBand() - 1,
              RasterRowSupport.noData(meta.getNoData(), this),
              this::isStopped);
      Object[] output = RowDataUtil.resizeArray(row, data.outputMeta.size());
      int index = data.inputMeta.size();
      for (String statistic : RasterRowSupport.statistics(meta.getStatistics()))
        output[index++] = result.value(statistic);
      output[index] = result.status();
      putRow(data.outputMeta, output);
    } catch (Exception e) {
      data.sources.close();
      if (isStopped()) return false;
      if (getTransformMeta().isDoingErrorHandling())
        putError(
            data.inputMeta, row, 1L, message(e), meta.getGeometryField(), "RASTER_STATS_ERROR");
      else throw new HopTransformException("Raster statistics failed: " + message(e), e);
    }
    return true;
  }

  private static String message(Exception e) {
    return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
  }

  @Override
  public void dispose() {
    data.sources.close();
    super.dispose();
  }
}
