package ch.so.agi.hop.raster.values;

@org.apache.hop.core.annotations.Transform(
    id = "SOGIS_RASTER_REPROJECT",
    name = "Raster LegacyReproject (GeoTools)",
    description = "Raster LegacyReproject",
    image = "ch/so/agi/hop/raster/values/icon.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry")
public final class RasterLegacyReprojectMeta extends RasterValueMeta {
  public String operation() {
    return "LEGACY";
  }
}
