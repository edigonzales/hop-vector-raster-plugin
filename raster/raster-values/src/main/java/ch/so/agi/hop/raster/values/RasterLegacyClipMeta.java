package ch.so.agi.hop.raster.values;

@org.apache.hop.core.annotations.Transform(
    id = "SOGIS_RASTER_CLIP",
    name = "Raster LegacyClip (GeoTools)",
    description = "Raster LegacyClip",
    image = "ch/so/agi/hop/raster/values/icon.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry")
public final class RasterLegacyClipMeta extends RasterValueMeta {
  public String operation() {
    return "LEGACY";
  }
}
