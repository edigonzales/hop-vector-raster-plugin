package ch.so.agi.hop.vector.transforms;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.eclipse.swt.widgets.Shell;

public class GeoJsonFeatureDecoderDialog extends GeoJsonFeatureDialog {
  public GeoJsonFeatureDecoderDialog(
      Shell parent, IVariables variables, GeoJsonFeatureDecoderMeta meta, PipelineMeta pipeline) {
    super(parent, variables, meta, pipeline);
  }
}
