package ch.so.agi.hop.vector.core;

public record FlatGeobufOptions(boolean spatialIndex, boolean skipEmpty, boolean overwrite)
    implements FormatOptions {
  public static FlatGeobufOptions defaults() {
    return new FlatGeobufOptions(true, false, false);
  }
}
