package ch.so.agi.hop.raster.clip;

public class RasterClipData extends org.apache.hop.pipeline.transform.BaseTransformData {
  public org.apache.hop.core.row.IRowMeta inputMeta, outputMeta;
  public ch.so.agi.hop.raster.core.RasterRowSupport sources =
      new ch.so.agi.hop.raster.core.RasterRowSupport();
}
