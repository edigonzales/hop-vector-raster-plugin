package ch.so.agi.hop.vector.transforms;

import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.transform.*;

public class GeoJsonFeatureEncoder
    extends GeoJsonFeatureTransform<GeoJsonFeatureEncoderMeta, GeoJsonFeatureEncoderData> {
  public GeoJsonFeatureEncoder(
      TransformMeta transform,
      GeoJsonFeatureEncoderMeta meta,
      GeoJsonFeatureEncoderData data,
      int copy,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transform, meta, data, copy, pipelineMeta, pipeline);
  }
}
