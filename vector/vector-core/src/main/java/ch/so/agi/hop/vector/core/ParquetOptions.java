package ch.so.agi.hop.vector.core;

public record ParquetOptions(
    String logicalType, String algorithm, String compression, long rowGroupSize, boolean overwrite)
    implements FormatOptions {
  public ParquetOptions {
    if (!java.util.Set.of("GEOMETRY", "GEOGRAPHY").contains(logicalType))
      throw new IllegalArgumentException("Invalid Parquet geometry type");
    if (!java.util.Set.of("SPHERICAL", "VINCENTY", "THOMAS", "ANDOYER", "KARNEY")
        .contains(algorithm)) throw new IllegalArgumentException("Invalid geography interpolation");
    if (!java.util.Set.of("GZIP", "UNCOMPRESSED").contains(compression))
      throw new IllegalArgumentException("Invalid Parquet compression");
    if (rowGroupSize <= 0) throw new IllegalArgumentException("Row group size must be positive");
  }

  public static ParquetOptions defaults() {
    return new ParquetOptions("GEOMETRY", "SPHERICAL", "GZIP", 128L * 1024 * 1024, false);
  }
}
