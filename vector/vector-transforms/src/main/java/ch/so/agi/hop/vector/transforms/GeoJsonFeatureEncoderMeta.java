package ch.so.agi.hop.vector.transforms;

import org.apache.hop.core.annotations.Transform;

@Transform(
    id = "SOGIS_GEOJSON_FEATURE_ENCODER",
    name = "GeoJSON Feature Encoder",
    description = "Encode native geometry and attributes as a GeoJSON Feature",
    image = "ch/so/agi/hop/vector/transforms/icons/geojson-feature.svg",
    documentationUrl =
        "https://edigonzales.github.io/hop-vector-raster-plugin/benutzerhandbuch/main/index.html#geojson-feature-encoder",
    categoryDescription = "Geospatial",
    classLoaderGroup = "sogeo-geometry")
public class GeoJsonFeatureEncoderMeta
    extends GeoJsonFeatureMeta<GeoJsonFeatureEncoder, GeoJsonFeatureEncoderData> {
  public GeoJsonFeatureEncoderMeta() {
    setDefault();
  }

  @Override
  public void setDefault() {
    super.setDefault();
    setProfile("RFC7946");
  }

  @Override
  boolean decoder() {
    return false;
  }
}
