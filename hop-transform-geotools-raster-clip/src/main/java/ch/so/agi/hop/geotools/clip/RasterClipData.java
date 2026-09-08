package ch.so.agi.hop.geotools.clip;

public class RasterClipData extends org.apache.hop.pipeline.transform.BaseTransformData {
  public org.apache.hop.core.row.IRowMeta inputMeta, outputMeta;
  public ch.so.agi.hop.geotools.raster.RasterRowSupport sources =
      new ch.so.agi.hop.geotools.raster.RasterRowSupport();
}
