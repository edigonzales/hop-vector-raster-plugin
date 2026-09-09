package ch.so.agi.hop.vector.core;

public record GenerateOptions(
    String geometryType,
    String dimension,
    boolean discardExtraOrdinates,
    int decimals,
    boolean comma,
    boolean skipEmpty,
    boolean overwrite,
    String idField,
    long startId)
    implements FormatOptions {
  public static GenerateOptions defaults() {
    return new GenerateOptions("LINE", "XY", false, -1, false, false, false, "", 1);
  }
}
