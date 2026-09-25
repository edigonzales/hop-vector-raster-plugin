package ch.so.agi.hop.raster.values;

@org.apache.hop.core.annotations.Transform(
    id = "SOGIS_RASTER_READER",
    name = "Raster Reader (GeoTools)",
    description = "Raster Reader",
    image = "ch/so/agi/hop/raster/values/icons/raster-reader.svg",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry")
public final class RasterReaderMeta extends RasterValueMeta {
  public String operation() {
    return "READER";
  }
}
