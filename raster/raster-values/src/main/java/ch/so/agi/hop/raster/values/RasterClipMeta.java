package ch.so.agi.hop.raster.values;

@org.apache.hop.core.annotations.Transform(
    id = "SOGIS_RASTER_VALUE_CLIP",
    name = "Raster Clip (GeoTools)",
    description = "Raster Clip",
    image = "ch/so/agi/hop/raster/values/icon.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry")
public final class RasterClipMeta extends RasterValueMeta {
  public String operation() {
    return "CLIP";
  }
}
