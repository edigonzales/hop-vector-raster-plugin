package ch.so.agi.hop.raster.values;

@org.apache.hop.core.annotations.Transform(
    id = "SOGIS_RASTER_WRITER",
    name = "Raster Writer (GeoTools)",
    description = "Raster Writer",
    image = "ch/so/agi/hop/raster/values/icons/raster-writer.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry")
public final class RasterWriterMeta extends RasterValueMeta {
  public String operation() {
    return "WRITER";
  }
}
