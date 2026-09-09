package ch.so.agi.hop.raster.stats;

public class RasterZonalStatsData extends org.apache.hop.pipeline.transform.BaseTransformData {
  public org.apache.hop.core.row.IRowMeta inputMeta, outputMeta;
  public ch.so.agi.hop.raster.core.RasterRowSupport sources =
      new ch.so.agi.hop.raster.core.RasterRowSupport();
}
