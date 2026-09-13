package ch.so.agi.hop.vector.transforms;

import ch.so.agi.hop.vector.formats.filegeodatabase.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.hop.core.exception.*;
import org.apache.hop.pipeline.*;
import org.apache.hop.pipeline.transform.*;

public class FileGdbWriter extends BaseTransform<FileGdbWriterMeta, FileGdbWriterData> {
  public FileGdbWriter(
      TransformMeta t,
      FileGdbWriterMeta m,
      FileGdbWriterData d,
      int copy,
      PipelineMeta pm,
      Pipeline p) {
    super(t, m, d, copy, pm, p);
  }

  @Override
  public boolean processRow() throws HopException {
    try {
      if (isStopped()) {
        abort();
        setOutputDone();
        return false;
      }
      if (data.session == null) {
        if (getTransformMeta().getCopies(this) != 1)
          throw new IllegalArgumentException("FileGDB Writer requires one copy");
        var schema = FileGdbExportSchema.read(Path.of(resolve(meta.getSchemaFile())));
        data.streams = new ArrayList<>();
        data.datasets = new ArrayList<>();
        var rows = new LinkedHashMap<String, org.apache.hop.core.row.IRowMeta>();
        var used = new HashSet<String>();
        for (var input : meta.getInputs()) {
          if (!used.add(input.getTransform()) || rows.containsKey(input.getDataset()))
            throw new IllegalArgumentException("Duplicate input mapping");
          var matches =
              getInputRowSets().stream()
                  .filter(r -> r.getOriginTransformName().equals(input.getTransform()))
                  .toList();
          if (matches.size() != 1)
            throw new IllegalArgumentException(
                "Expected one input stream from " + input.getTransform());
          data.streams.add(matches.getFirst());
          data.datasets.add(input.getDataset());
          rows.put(
              input.getDataset(), getPipelineMeta().getTransformFields(this, input.getTransform()));
        }
        if (data.streams.size() != getInputRowSets().size())
          throw new IllegalArgumentException("Unmapped input stream");
        data.session =
            new FileGdbExportSession(
                Path.of(resolve(meta.getFileName())),
                schema,
                rows,
                new ch.so.agi.hop.support.geotools.GeoToolsCrsDefinitionResolver());
      }
      // One bounded wait per turn, round-robin. Never drain a branch before reading another.
      int n = data.streams.size();
      boolean done = true;
      for (int j = 0; j < n; j++) {
        var s = data.streams.get(j);
        if (!s.isDone() || s.size() != 0) {
          done = false;
          break;
        }
      }
      if (done) {
        data.session.finish();
        data.session = null;
        setOutputDone();
        return false;
      }
      int i = data.cursor;
      data.cursor = (data.cursor + 1) % n;
      var stream = data.streams.get(i);
      Object[] row = stream.getRowWait(1, TimeUnit.MILLISECONDS);
      if (row != null) {
        incrementLinesRead();
        data.session.write(data.datasets.get(i), row);
        incrementLinesOutput();
      }
      return true;
    } catch (Exception e) {
      abort();
      throw new HopTransformException("FileGDB export failed: " + e.getMessage(), e);
    }
  }

  private void abort() {
    if (data.session != null) {
      try {
        data.session.close();
      } catch (Exception e) {
        logError("Cannot clean FileGDB staging", e);
      }
      data.session = null;
    }
  }

  @Override
  public void dispose() {
    abort();
    super.dispose();
  }
}
