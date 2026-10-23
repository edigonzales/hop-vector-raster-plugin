package ch.so.agi.hop.vector.core.geojson;

import ch.so.agi.hop.vector.core.FormatOptions;
import com.atolcd.hop.gis.geometry.codec.GeoJsonProfile;
import java.util.*;

/** Shared settings for row and file feature processing. */
public record GeoJsonFeatureOptions(
    GeoJsonProfile profile,
    String geometryField,
    String fallbackField,
    String propertiesField,
    String idField,
    String contextField,
    boolean dropZ,
    boolean dropM,
    boolean emptyToNull,
    List<GeoJsonFieldMapping> mappings)
    implements FormatOptions {
  public GeoJsonFeatureOptions {
    profile = profile == null ? GeoJsonProfile.AUTO : profile;
    geometryField = field(geometryField, "geometry");
    fallbackField = fallbackField == null ? "geometry_wgs84" : fallbackField;
    propertiesField = field(propertiesField, "properties_json");
    idField = field(idField, "feature_id_json");
    contextField = field(contextField, "geojson_context_json");
    mappings = List.copyOf(mappings == null ? List.of() : mappings);
    Set<String> names = new HashSet<>();
    for (String name :
        List.of(geometryField, fallbackField, propertiesField, idField, contextField))
      names.add(name.toLowerCase(Locale.ROOT));
    if (names.size() != 5)
      throw new IllegalArgumentException("GeoJSON output field names must be distinct");
    for (var m : mappings)
      if (!names.add(m.field().toLowerCase(Locale.ROOT)))
        throw new IllegalArgumentException("Duplicate output field: " + m.field());
    for (int i = 0; i < mappings.size(); i++)
      for (int j = i + 1; j < mappings.size(); j++) {
        String a = mappings.get(i).pointer(), b = mappings.get(j).pointer();
        if (a.equals(b) || a.startsWith(b + "/") || b.startsWith(a + "/"))
          throw new IllegalArgumentException("Overlapping properties mappings: " + a + ", " + b);
      }
  }

  private static String field(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  public static GeoJsonFeatureOptions defaults() {
    return new GeoJsonFeatureOptions(
        GeoJsonProfile.AUTO,
        "geometry",
        "geometry_wgs84",
        "properties_json",
        "feature_id_json",
        "geojson_context_json",
        false,
        false,
        false,
        List.of());
  }

  public GeoJsonFeatureOptions withProfile(GeoJsonProfile value) {
    return new GeoJsonFeatureOptions(
        value,
        geometryField,
        fallbackField,
        propertiesField,
        idField,
        contextField,
        dropZ,
        dropM,
        emptyToNull,
        mappings);
  }
}
