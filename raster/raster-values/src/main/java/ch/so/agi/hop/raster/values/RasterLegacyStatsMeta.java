package ch.so.agi.hop.raster.values;

@org.apache.hop.core.annotations.Transform(
    id = "SOGIS_RASTER_ZONAL_STATS",
    name = "Raster LegacyStats (GeoTools)",
    description = "Raster LegacyStats",
    image = "ch/so/agi/hop/raster/values/icon.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry")
public final class RasterLegacyStatsMeta extends RasterValueMeta {
  public String operation() {
    return "LEGACY";
  }
}
