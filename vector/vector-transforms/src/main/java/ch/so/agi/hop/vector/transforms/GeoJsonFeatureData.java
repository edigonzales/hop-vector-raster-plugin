package ch.so.agi.hop.vector.transforms;

import ch.so.agi.hop.vector.core.geojson.*;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.pipeline.transform.BaseTransformData;

public class GeoJsonFeatureData extends BaseTransformData {
  GeoJsonFeatureOptions options;
  GeoJsonFeatureCodec codec;
  IRowMeta outputMeta;
  int inputIndex;
  int inputSize;
  GeoJsonFeatureMeta.OnDataError errorMode;
}
