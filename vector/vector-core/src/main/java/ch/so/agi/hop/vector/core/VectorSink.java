package ch.so.agi.hop.vector.core;

public interface VectorSink extends AutoCloseable {
  /** Returns false only when a configured skip policy skipped the row. */
  boolean write(Object[] row) throws Exception;

  /** Publish a complete dataset. close without finish aborts the export. */
  void finish() throws Exception;

  @Override
  void close() throws Exception;
}
