package ch.so.agi.hop.vector.transforms;

public class FileGdbWriterData extends org.apache.hop.pipeline.transform.BaseTransformData {
  ch.so.agi.hop.vector.formats.filegeodatabase.FileGdbExportSession session;
  java.util.List<org.apache.hop.core.IRowSet> streams;
  java.util.List<String> datasets;
  int cursor;
}
