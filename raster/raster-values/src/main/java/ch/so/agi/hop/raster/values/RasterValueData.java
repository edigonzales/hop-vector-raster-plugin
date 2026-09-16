package ch.so.agi.hop.raster.values;

public final class RasterValueData extends org.apache.hop.pipeline.transform.BaseTransformData {
  public org.apache.hop.core.row.IRowMeta inputMeta, outputMeta;
  public final ch.so.agi.hop.raster.geotools.GeoToolsRasterBackend backend =
      new ch.so.agi.hop.raster.geotools.GeoToolsRasterBackend();
  public boolean emitted;
}
