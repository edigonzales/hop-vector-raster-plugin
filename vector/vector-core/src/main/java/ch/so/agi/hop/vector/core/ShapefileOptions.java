package ch.so.agi.hop.vector.core;

import java.util.*;

public record ShapefileOptions(
    String charset, String timezone, List<Field> fields, boolean overwrite)
    implements FormatOptions {
  public record Field(String source, String target, int width, int scale) {}

  public ShapefileOptions(String charset, String timezone, List<Field> fields) {
    this(charset, timezone, fields, false);
  }

  public ShapefileOptions {
    charset = charset == null ? "" : charset;
    timezone = timezone == null || timezone.isBlank() ? "UTC" : timezone;
    fields = List.copyOf(fields);
    java.time.ZoneId.of(timezone);
  }

  public static ShapefileOptions defaults() {
    return new ShapefileOptions("", "UTC", List.of(), false);
  }
}
