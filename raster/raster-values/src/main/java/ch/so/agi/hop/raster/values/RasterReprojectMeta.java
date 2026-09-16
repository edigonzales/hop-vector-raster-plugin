package ch.so.agi.hop.raster.values;

@org.apache.hop.core.annotations.Transform(
    id = "SOGIS_RASTER_VALUE_REPROJECT",
    name = "Raster Reproject (GeoTools)",
    description = "Raster Reproject",
    image = "ch/so/agi/hop/raster/values/icon.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry")
public final class RasterReprojectMeta extends RasterValueMeta {
  public String operation() {
    return "REPROJECT";
  }
}
