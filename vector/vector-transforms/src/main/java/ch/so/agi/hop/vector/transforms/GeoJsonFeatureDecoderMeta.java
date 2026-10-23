package ch.so.agi.hop.vector.transforms;

import org.apache.hop.core.annotations.Transform;

@Transform(
    id = "SOGIS_GEOJSON_FEATURE_DECODER",
    name = "GeoJSON Feature Decoder",
    description = "Decode GeoJSON Feature JSON into native geometry and attributes",
    image = "ch/so/agi/hop/vector/transforms/icons/geojson-feature.svg",
    documentationUrl =
        "https://edigonzales.github.io/hop-vector-raster-plugin/benutzerhandbuch/main/index.html#geojson-feature-decoder",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry")
public class GeoJsonFeatureDecoderMeta
    extends GeoJsonFeatureMeta<GeoJsonFeatureDecoder, GeoJsonFeatureDecoderData> {
  public GeoJsonFeatureDecoderMeta() {
    setDefault();
  }

  @Override
  boolean decoder() {
    return true;
  }
}
