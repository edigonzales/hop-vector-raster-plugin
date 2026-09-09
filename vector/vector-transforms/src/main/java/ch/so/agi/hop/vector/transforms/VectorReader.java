package ch.so.agi.hop.vector.transforms;

import ch.so.agi.hop.vector.core.*;
import java.nio.file.Path;
import org.apache.hop.core.exception.*;

public class VectorReader
    extends org.apache.hop.pipeline.transform.BaseTransform<VectorReaderMeta, VectorReaderData> {
  public VectorReader(
      org.apache.hop.pipeline.transform.TransformMeta t,
      VectorReaderMeta m,
      VectorReaderData d,
      int copy,
      org.apache.hop.pipeline.PipelineMeta pm,
      org.apache.hop.pipeline.Pipeline p) {
    super(t, m, d, copy, pm, p);
  }

  private final VectorDiagnostics diagnostics = new VectorDiagnostics(this::logBasic);

  public boolean processRow() throws HopException {
    try {
      if (isStopped()) {
        closeSource();
        return false;
      }
      if (data.source == null) {
        Path file = Path.of(resolve(meta.getFileName()));
        data.source =
            VectorProviders.get(VectorFormat.resolve(meta.getFormat(), file))
                .open(meta.request(this, diagnostics));
      }
      diagnostics.nextRow();
      Object[] row = data.source.read();
      if (row == null) {
        closeSource();
        setOutputDone();
        return false;
      }
      putRow(data.source.schema().rowMeta(), row);
      return true;
    } catch (Exception e) {
      closeSource();
      throw new HopTransformException("Unable to read vector dataset", e);
    }
  }

  private void closeSource() {
    if (data.source != null) {
      try {
        data.source.close();
      } catch (Exception e) {
        logError("Unable to close vector input", e);
      }
      data.source = null;
      diagnostics.finish();
    }
  }

  @Override
  public void dispose() {
    closeSource();
    super.dispose();
  }
}
