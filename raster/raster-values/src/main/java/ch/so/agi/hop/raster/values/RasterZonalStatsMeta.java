package ch.so.agi.hop.raster.values;

@org.apache.hop.core.annotations.Transform(
    id = "SOGIS_RASTER_VALUE_ZONAL_STATS",
    name = "Raster ZonalStats (GeoTools)",
    description = "Raster ZonalStats",
    image = "ch/so/agi/hop/raster/values/icons/raster-zonal-stats.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry")
public final class RasterZonalStatsMeta extends RasterValueMeta {
  public String operation() {
    return "STATS";
  }
}
