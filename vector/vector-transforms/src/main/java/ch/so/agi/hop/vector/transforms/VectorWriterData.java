package ch.so.agi.hop.vector.transforms;

public class VectorWriterData extends org.apache.hop.pipeline.transform.BaseTransformData {
  ch.so.agi.hop.vector.core.VectorSink sink;
  long skipped;
  java.util.List<Object[]> pending = new java.util.ArrayList<>();
}
