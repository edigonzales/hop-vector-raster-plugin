package ch.so.agi.hop.generate;

import java.nio.file.Path;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.locationtech.jts.geom.Geometry;

public class ArcInfoGenerateWriterTransform
    extends BaseTransform<ArcInfoGenerateWriterMeta, ArcInfoGenerateWriterData> {
  public ArcInfoGenerateWriterTransform(
      TransformMeta transform,
      ArcInfoGenerateWriterMeta meta,
      ArcInfoGenerateWriterData data,
      int copy,
      PipelineMeta pipelineMeta,
      Pipeline pipeline) {
    super(transform, meta, data, copy, pipelineMeta, pipeline);
  }

  @Override
  public boolean processRow() throws HopException {
    Object[] row = getRow();
    try {
      if (isStopped()) {
        closeFile();
        return false;
      }
      if (data.file == null) {
        meta.validateSettings();
        data.nextId = meta.getStartId();
        if (getTransformMeta().getCopies(this) > 1)
          throw new IllegalArgumentException(
              "GENERATE Writer requires one transform copy per output file");
        data.file =
            new GenerateFile(
                Path.of(resolve(meta.getOutput())), meta.isOverwrite(), meta.options());
      }
      if (row == null) {
        data.file.commit();
        data.file = null;
        if (data.skipped > 0) logBasic("Skipped " + data.skipped + " null/empty geometries");
        setOutputDone();
        return false;
      }
      data.inputMeta = getInputRowMeta();
      int geometryIndex = data.inputMeta.indexOfValue(resolve(meta.getGeometryField()));
      if (geometryIndex < 0) throw new IllegalArgumentException("Geometry field not found");
      Object value = row[geometryIndex];
      if (value != null && !(value instanceof Geometry))
        throw new IllegalArgumentException("Expected Hop Geometry value");
      Geometry geometry = (Geometry) value;
      if ((geometry == null || geometry.isEmpty()) && meta.isSkipEmpty()) {
        data.skipped++;
        return true;
      }
      long id;
      if (meta.getIdField().isBlank()) {
        if (data.idExhausted) throw new IllegalArgumentException("Sequential ID overflow");
        id = data.nextId;
      } else {
        int index = data.inputMeta.indexOfValue(resolve(meta.getIdField()));
        if (index < 0 || data.inputMeta.getValueMeta(index).getType() != IValueMeta.TYPE_INTEGER)
          throw new IllegalArgumentException("ID field must have Hop Integer type");
        Long number = data.inputMeta.getInteger(row, index);
        if (number == null) throw new IllegalArgumentException("ID is null");
        id = number;
      }
      data.file.write(id, geometry);
      incrementLinesOutput();
      if (meta.getIdField().isBlank()) {
        if (data.nextId == Long.MAX_VALUE) data.idExhausted = true;
        else data.nextId++;
      }
      return true;
    } catch (Exception e) {
      closeFile();
      throw new HopTransformException("GENERATE export failed: " + e.getMessage(), e);
    }
  }

  private void closeFile() {
    if (data.file != null) {
      try {
        data.file.close();
      } catch (Exception e) {
        logError("Unable to close GENERATE output", e);
      }
      data.file = null;
    }
  }

  @Override
  public void dispose() {
    closeFile();
    super.dispose();
  }
}
