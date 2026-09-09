package ch.so.agi.hop.vector.core;

public interface VectorSource extends AutoCloseable {
  LayerSchema schema();

  /** Returns the next Hop row, or null at EOF. */
  Object[] read() throws Exception;

  @Override
  void close() throws Exception;
}
