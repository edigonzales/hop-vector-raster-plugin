package ch.so.agi.hop.vector.transforms;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.pipeline.PipelineMeta;
import org.eclipse.swt.widgets.Shell;

public class GeoJsonFeatureEncoderDialog extends GeoJsonFeatureDialog {
  public GeoJsonFeatureEncoderDialog(
      Shell parent, IVariables variables, GeoJsonFeatureEncoderMeta meta, PipelineMeta pipeline) {
    super(parent, variables, meta, pipeline);
  }
}
