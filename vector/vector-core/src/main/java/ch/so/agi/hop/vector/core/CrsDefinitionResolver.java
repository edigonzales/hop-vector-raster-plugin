package ch.so.agi.hop.vector.core;

@FunctionalInterface
public interface CrsDefinitionResolver {
  record Definition(int srid, String name, String organization, int organizationId, String wkt) {}

  Definition resolve(int srid) throws Exception;

  default Definition parse(String value) throws Exception {
    if (value == null || value.isBlank()) return new Definition(0, "Unknown", "NONE", 0, "");
    String s = value.trim();
    if (s.matches("(?i)(EPSG:)?[0-9]+"))
      return resolve(Integer.parseInt(s.replaceFirst("(?i)^EPSG:", "")));
    return new Definition(0, "Custom CRS", "NONE", 0, s);
  }
}
