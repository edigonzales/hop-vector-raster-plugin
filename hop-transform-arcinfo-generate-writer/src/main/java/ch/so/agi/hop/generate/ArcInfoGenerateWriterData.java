package ch.so.agi.hop.generate;

public class ArcInfoGenerateWriterData extends org.apache.hop.pipeline.transform.BaseTransformData {
  public org.apache.hop.core.row.IRowMeta inputMeta, outputMeta;
  public GenerateFile file;
  public long nextId;
  public boolean idExhausted;
  public long skipped;
}
