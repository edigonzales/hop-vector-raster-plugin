package ch.so.agi.hop.vector.transforms;

import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.transform.*;

public class GeoJsonFeatureDecoder
    extends GeoJsonFeatureTransform<GeoJsonFeatureDecoderMeta, GeoJsonFeatureDecoderData> {
  public GeoJsonFeatureDecoder(
      TransformMeta transform,
      GeoJsonFeatureDecoderMeta meta,
      GeoJsonFeatureDecoderData data,
      int copy,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transform, meta, data, copy, pipelineMeta, pipeline);
  }
}
