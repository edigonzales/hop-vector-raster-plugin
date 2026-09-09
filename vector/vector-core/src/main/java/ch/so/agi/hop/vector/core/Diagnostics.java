package ch.so.agi.hop.vector.core;

@FunctionalInterface
public interface Diagnostics {
  default void nextRecord() {}

  void warning(String field, String cause, String message);

  Diagnostics NONE = (field, cause, message) -> {};
}
