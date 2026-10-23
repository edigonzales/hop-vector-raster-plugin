package ch.so.agi.hop.vector.core.geojson;

/** An explicit projection from a properties JSON pointer to a Hop field. */
public record GeoJsonFieldMapping(String pointer, String field, Type type, boolean writeNull) {
  public static java.util.List<GeoJsonFieldMapping> parseJson(String json)
      throws com.atolcd.hop.gis.geometry.codec.GeometryCodecException {
    if (json == null || json.isBlank()) return java.util.List.of();
    Object parsed = com.atolcd.hop.gis.geometry.codec.JsonSupport.parse(json);
    if (!(parsed instanceof java.util.List<?> values))
      throw new IllegalArgumentException("Mappings must be a JSON array");
    var result = new java.util.ArrayList<GeoJsonFieldMapping>();
    for (Object v : values) {
      if (!(v instanceof java.util.Map))
        throw new IllegalArgumentException("Each mapping must be an object");
      var m = GeoJsonFeatureCodec.asMap(v);
      if (!(m.get("pointer") instanceof String)
          || !(m.get("field") instanceof String)
          || !(m.get("type") instanceof String))
        throw new IllegalArgumentException("Mapping pointer, field and type must be strings");
      if (m.containsKey("writeNull") && !(m.get("writeNull") instanceof Boolean))
        throw new IllegalArgumentException("Mapping writeNull must be boolean");
      for (String key : m.keySet())
        if (!java.util.Set.of("pointer", "field", "type", "writeNull").contains(key))
          throw new IllegalArgumentException("Unknown mapping setting: " + key);
      result.add(
          new GeoJsonFieldMapping(
              String.valueOf(m.get("pointer")),
              String.valueOf(m.get("field")),
              Type.valueOf(String.valueOf(m.get("type"))),
              Boolean.TRUE.equals(m.get("writeNull"))));
    }
    return java.util.List.copyOf(result);
  }

  public static String toJson(java.util.List<GeoJsonFieldMapping> mappings)
      throws com.atolcd.hop.gis.geometry.codec.GeometryCodecException {
    var values = new java.util.ArrayList<java.util.Map<String, Object>>();
    for (var m : mappings)
      values.add(
          java.util.Map.of(
              "pointer",
              m.pointer(),
              "field",
              m.field(),
              "type",
              m.type().name(),
              "writeNull",
              m.writeNull()));
    return com.atolcd.hop.gis.geometry.codec.JsonSupport.stringify(values);
  }

  public enum Type {
    STRING,
    INTEGER,
    NUMBER,
    BIG_NUMBER,
    BOOLEAN,
    JSON
  }

  public GeoJsonFieldMapping {
    if (pointer == null || !pointer.startsWith("/"))
      throw new IllegalArgumentException("Properties pointer must begin with /");
    if (field == null || field.isBlank())
      throw new IllegalArgumentException("Mapped field is required");
    if (type == null) throw new IllegalArgumentException("Mapped type is required");
    for (int i = 0; i < pointer.length(); i++)
      if (pointer.charAt(i) == '~'
          && (i + 1 >= pointer.length() || "01".indexOf(pointer.charAt(++i)) < 0))
        throw new IllegalArgumentException("Invalid JSON pointer escape");
  }
}
