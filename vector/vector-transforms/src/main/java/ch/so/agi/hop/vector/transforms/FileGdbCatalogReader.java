package ch.so.agi.hop.vector.transforms;

import ch.so.agi.hop.vector.formats.filegeodatabase.FileGdbCatalog;
import org.apache.hop.core.exception.*;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.transform.*;

public class FileGdbCatalogReader
    extends BaseTransform<FileGdbCatalogReaderMeta, FileGdbCatalogReaderData> {
  public FileGdbCatalogReader(
      TransformMeta t,
      FileGdbCatalogReaderMeta m,
      FileGdbCatalogReaderData d,
      int copy,
      PipelineMeta pm,
      Pipeline p) {
    super(t, m, d, copy, pm, p);
  }

  @Override
  public boolean processRow() throws HopException {
    try {
      if (isStopped()) {
        setOutputDone();
        return false;
      }
      if (data.rows == null) {
        var mode = FileGdbCatalog.Mode.valueOf(meta.getMode());
        data.rowMeta = mode.rowMeta();
        data.rows =
            FileGdbCatalog.read(
                    java.nio.file.Path.of(resolve(meta.getFileName())),
                    mode,
                    resolve(meta.getNameFilter()))
                .iterator();
      }
      if (!data.rows.hasNext()) {
        setOutputDone();
        return false;
      }
      putRow(data.rowMeta, data.rows.next());
      return true;
    } catch (Exception e) {
      throw new HopTransformException("Cannot read FileGDB catalog", e);
    }
  }
}
