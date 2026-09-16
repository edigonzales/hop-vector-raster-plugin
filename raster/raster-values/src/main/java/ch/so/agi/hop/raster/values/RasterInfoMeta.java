package ch.so.agi.hop.raster.values;

@org.apache.hop.core.annotations.Transform(
    id = "SOGIS_RASTER_INFO",
    name = "Raster Info (GeoTools)",
    description = "Raster Info",
    image = "ch/so/agi/hop/raster/values/icon.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry")
public final class RasterInfoMeta extends RasterValueMeta {
  public String operation() {
    return "INFO";
  }
}
