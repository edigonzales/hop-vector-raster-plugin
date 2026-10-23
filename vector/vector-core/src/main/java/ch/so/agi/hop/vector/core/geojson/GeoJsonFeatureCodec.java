package ch.so.agi.hop.vector.core.geojson;

import com.atolcd.hop.gis.geometry.codec.*;
import java.util.*;
import org.locationtech.jts.geom.Geometry;

/** Shared Feature-level codec. Geometry interpretation belongs to Geometry Type. */
public final class GeoJsonFeatureCodec {
  private static final String CONF = "http://www.opengis.net/spec/json-fg-1/1.0/conf/";
  private final GeometryCodec geometries = new GeometryCodec(GeometryReadLimits.defaults());

  public GeoJsonFeature decode(
      String json, GeoJsonFeatureOptions options, String collectionContextJson)
      throws GeometryCodecException {
    return decodeObject(
        JsonSupport.parseObject(json),
        options,
        collectionContextJson == null || collectionContextJson.isBlank()
            ? Map.of()
            : JsonSupport.parseObject(collectionContextJson));
  }

  public GeoJsonFeature decodeObject(
      Map<String, Object> feature, GeoJsonFeatureOptions options, Map<String, Object> root)
      throws GeometryCodecException {
    if (!"Feature".equals(feature.get("type")))
      throw new IllegalArgumentException("A GeoJSON Feature is required");
    if (!feature.containsKey("properties")
        || !(feature.get("properties") == null || feature.get("properties") instanceof Map))
      throw new IllegalArgumentException("Feature properties must be an object or null");
    if (!feature.containsKey("geometry"))
      throw new IllegalArgumentException("Feature geometry member is required (null is allowed)");
    Object id = feature.get("id");
    if (feature.containsKey("id") && !(id instanceof String || id instanceof Number))
      throw new IllegalArgumentException("Feature id must be a string or number");
    GeoJsonProfile profile = profile(options.profile(), root, feature);
    Map<String, Object> metadata =
        metadata(feature, Set.of("type", "geometry", "place", "properties", "id", "bbox"));
    Map<String, Object> rootMetadata =
        metadata(root, Set.of("type", "features", "bbox", "geometry", "place", "properties", "id"));
    if (profile == GeoJsonProfile.RFC7946
        && (root.containsKey("crs") || feature.containsKey("crs")))
      throw new IllegalArgumentException("RFC 7946 does not accept a declared Legacy CRS");
    if (profile == GeoJsonProfile.JSONFG10
        && ((root.containsKey("coordRefSys") && root.get("coordRefSys") == null)
            || (feature.containsKey("coordRefSys") && feature.get("coordRefSys") == null)))
      throw new IllegalArgumentException("JSON-FG coordRefSys must not be null");
    if (profile == GeoJsonProfile.LEGACY2008
        && ((feature.containsKey("crs") && feature.get("crs") == null)
            || (!feature.containsKey("crs") && root.containsKey("crs") && root.get("crs") == null)))
      throw new IllegalArgumentException(
          "Legacy crs:null has an unknown coordinate reference system");
    if (profile == GeoJsonProfile.JSONFG10) validateFg(root, feature);
    else if (feature.get("place") != null)
      throw new IllegalArgumentException("JSON-FG place requires the JSONFG10 profile");
    Object coordinateReference =
        root.containsKey("coordRefSys") ? root.get("coordRefSys") : feature.get("coordRefSys");
    if (profile == GeoJsonProfile.LEGACY2008)
      coordinateReference = feature.containsKey("crs") ? feature.get("crs") : root.get("crs");
    Object rawMeasures =
        feature.containsKey("measures") ? feature.get("measures") : root.get("measures");
    if (rawMeasures != null && !(rawMeasures instanceof Map))
      throw new IllegalArgumentException("JSON-FG measures must be an object");
    Map<String, Object> measures = asMap(rawMeasures);
    GeoJsonContext context = new GeoJsonContext(profile, coordinateReference, measures);
    Geometry geometry = null, fallback = null;
    GeoJsonContext actual = context;
    boolean place = profile == GeoJsonProfile.JSONFG10 && feature.get("place") != null;
    Object value = place ? feature.get("place") : feature.get("geometry");
    if (value != null) {
      if (place) {
        var placeObject = object(value, "JSON-FG place");
        for (String key : List.of("coordRefSys", "conformsTo", "measures"))
          if (placeObject.containsKey(key))
            throw new IllegalArgumentException(
                "JSON-FG place must inherit " + key + " from its Feature or document");
      }
      GeoJsonReadResult result =
          geometries.readGeoJsonObject(
              object(value, "Feature geometry"),
              new GeoJsonReadOptions(
                  place
                      ? profile
                      : profile == GeoJsonProfile.JSONFG10 ? GeoJsonProfile.RFC7946 : profile,
                  place
                      ? context
                      : profile == GeoJsonProfile.JSONFG10 ? GeoJsonContext.empty() : context));
      geometry = result.geometry();
      actual = result.context();
    }
    if (place
        && isCrs84(actual.coordRefSys())
        && !Boolean.TRUE.equals(measures.get("enabled"))
        && !containsCurves(value))
      throw new IllegalArgumentException(
          "Plain CRS84/CRS84h Simple Feature geometries must use geometry rather than JSON-FG"
              + " place");
    if (place && feature.get("geometry") != null) {
      if (jsonEquivalent(feature.get("geometry"), feature.get("place")))
        throw new IllegalArgumentException(
            "JSON-FG geometry and place may not have identical values");
      fallback =
          geometries
              .readGeoJsonObject(
                  object(feature.get("geometry"), "WGS84 fallback"),
                  new GeoJsonReadOptions(GeoJsonProfile.RFC7946, GeoJsonContext.empty()))
              .geometry();
    }
    if (profile == GeoJsonProfile.JSONFG10 && feature.get("geometry") != null)
      validateFgRfcGeometry(
          object(feature.get("geometry"), "WGS84 geometry"), place ? fallback : geometry);
    Map<String, Object> saved = new LinkedHashMap<>();
    saved.put("version", 1);
    saved.put("root", rootMetadata);
    saved.put("feature", metadata);
    saved.put("geometry", actual.toMap());
    saved.put("profile", profile.name());
    if (fallback != null)
      saved.put("fallback", new GeoJsonContext(GeoJsonProfile.RFC7946, null, Map.of()).toMap());
    if (profile == GeoJsonProfile.JSONFG10) assertDimension(geometry, root, feature);
    return new GeoJsonFeature(
        geometry,
        fallback,
        JsonSupport.stringify(feature.get("properties")),
        feature.containsKey("id") ? JsonSupport.stringify(id) : null,
        JsonSupport.stringify(saved));
  }

  /** Validates document-level semantics even when a FeatureCollection has no rows. */
  public void validateCollection(Map<String, Object> root, GeoJsonFeatureOptions options) {
    if (!"FeatureCollection".equals(root.get("type")))
      throw new IllegalArgumentException("A FeatureCollection is required");
    GeoJsonProfile profile = profile(options.profile(), root, Map.of());
    if (profile == GeoJsonProfile.RFC7946 && root.containsKey("crs"))
      throw new IllegalArgumentException("RFC 7946 does not accept a declared Legacy CRS");
    if (profile == GeoJsonProfile.JSONFG10
        && root.containsKey("coordRefSys")
        && root.get("coordRefSys") == null)
      throw new IllegalArgumentException("JSON-FG coordRefSys must not be null");
    if (profile == GeoJsonProfile.LEGACY2008 && root.containsKey("crs") && root.get("crs") == null)
      throw new IllegalArgumentException(
          "Legacy crs:null has an unknown coordinate reference system");
    if (profile == GeoJsonProfile.JSONFG10) validateFg(root, root);
    else if (root.containsKey("place"))
      throw new IllegalArgumentException("A FeatureCollection must not contain place");
  }

  public String encode(GeoJsonFeature feature, GeoJsonFeatureOptions options)
      throws GeometryCodecException {
    return JsonSupport.stringify(encodeObject(feature, options, true));
  }

  public Map<String, Object> encodeObject(
      GeoJsonFeature feature, GeoJsonFeatureOptions options, boolean root)
      throws GeometryCodecException {
    var saved = fieldContext(feature.contextJson(), options.contextField());
    GeoJsonProfile profile = outputProfile(options.profile(), saved);
    if (profile != GeoJsonProfile.JSONFG10 && feature.fallbackGeometry() != null)
      throw new GeoJsonFieldException(
          options.fallbackField(),
          "A second fallback geometry cannot be represented by this profile; explicitly disable its"
              + " input field",
          null);
    var result = new LinkedHashMap<String, Object>();
    result.put("type", "Feature");
    result.putAll(asMap(saved.get("feature")));
    result.remove("bbox");
    result.remove("conformsTo");
    result.remove("coordRefSys");
    result.remove("crs");
    result.remove("measures");
    if (root)
      for (var entry : rootMetadata(feature, options).entrySet()) {
        if (profile == GeoJsonProfile.LEGACY2008 && entry.getKey().equals("crs")) continue;
        if (result.containsKey(entry.getKey())
            && !Objects.equals(result.get(entry.getKey()), entry.getValue()))
          throw new IllegalArgumentException(
              "Collection metadata conflicts with Feature member '"
                  + entry.getKey()
                  + "'; retain the original context field separately");
        result.put(entry.getKey(), entry.getValue());
      }
    Object properties =
        feature.propertiesJson() == null
            ? null
            : payload(feature.propertiesJson(), options.propertiesField());
    if (!(properties == null || properties instanceof Map))
      throw new GeoJsonFieldException(
          options.propertiesField(), "Properties JSON must be an object or null", null);
    result.put("properties", properties);
    if (feature.idJson() != null) {
      Object id = payload(feature.idJson(), options.idField());
      if (!(id instanceof String || id instanceof Number))
        throw new GeoJsonFieldException(
            options.idField(), "Feature id must be a JSON string or number", null);
      result.put("id", id);
    }
    GeoJsonContext geometryContext = writeContext(feature, profile, saved);
    var writeOptions =
        new GeoJsonWriteOptions(
            profile, geometryContext, options.dropZ(), options.dropM(), options.emptyToNull());
    Map<String, Object> value = geometries.writeGeoJsonObject(feature.geometry(), writeOptions);
    if (profile == GeoJsonProfile.JSONFG10) {
      boolean nativePlace = value != null && needsPlace(value);
      if (nativePlace) {
        var place = new LinkedHashMap<>(value);
        place.remove("coordRefSys");
        place.remove("conformsTo");
        Object measures = place.remove("measures");
        if (measures != null) result.put("measures", measures);
        result.put("place", place);
        Map<String, Object> fallback =
            geometries.writeGeoJsonObject(
                feature.fallbackGeometry(),
                new GeoJsonWriteOptions(
                    GeoJsonProfile.RFC7946,
                    GeoJsonContext.empty(),
                    options.dropZ(),
                    options.dropM(),
                    options.emptyToNull()));
        if (fallback != null && fallback.equals(place))
          throw new IllegalArgumentException(
              "JSON-FG geometry and place may not have identical values");
        result.put("geometry", fallback);
      } else {
        result.remove("place");
        if (value != null && feature.fallbackGeometry() != null)
          throw new IllegalArgumentException(
              "A separate fallback cannot be represented when the primary geometry already uses"
                  + " WGS84 geometry; explicitly disable its input field");
        result.put(
            "geometry",
            geometries.writeGeoJsonObject(
                value == null ? feature.fallbackGeometry() : feature.geometry(),
                new GeoJsonWriteOptions(
                    GeoJsonProfile.RFC7946,
                    value == null
                        ? GeoJsonContext.empty()
                        : new GeoJsonContext(
                            GeoJsonProfile.RFC7946, geometryContext.coordRefSys(), Map.of()),
                    options.dropZ(),
                    options.dropM(),
                    options.emptyToNull())));
      }
      var effectiveMeasures = effectiveMeasures(saved, geometryContext);
      if (!options.dropM() && !result.containsKey("measures") && !effectiveMeasures.isEmpty())
        result.put("measures", effectiveMeasures);
      validateFgRfcGeometry(
          asMap(result.get("geometry")),
          nativePlace
              ? feature.fallbackGeometry()
              : feature.geometry() == null ? feature.fallbackGeometry() : feature.geometry());
      assertDimension(
          feature.geometry() == null ? feature.fallbackGeometry() : feature.geometry(),
          asMap(saved.get("root")),
          result);
      validateFg(root ? result : rootMetadata(feature, options), result);
    } else {
      result.remove("place");
      result.remove("coordRefSys");
      result.remove("conformsTo");
      result.remove("measures");
      if (profile == GeoJsonProfile.LEGACY2008) {
        Object declaration = null;
        if (value != null) {
          value = new LinkedHashMap<>(value);
          declaration = value.remove("crs");
        } else if (geometryContext.coordRefSys() != null) {
          Object reference = geometryContext.coordRefSys();
          declaration =
              reference instanceof Map
                  ? reference
                  : Map.of("type", "name", "properties", Map.of("name", String.valueOf(reference)));
        }
        if (declaration != null) result.put("crs", declaration);
      }
      result.put("geometry", value);
    }
    return result;
  }

  public Map<String, Object> rootMetadata(GeoJsonFeature feature, GeoJsonFeatureOptions options)
      throws GeometryCodecException {
    var saved = fieldContext(feature.contextJson(), options.contextField());
    GeoJsonProfile profile = outputProfile(options.profile(), saved);
    var root = new LinkedHashMap<>(asMap(saved.get("root")));
    root.remove("bbox");
    root.remove("type");
    root.remove("features");
    if (profile == GeoJsonProfile.JSONFG10) {
      var originalFeature = asMap(saved.get("feature"));
      for (String key : List.of("coordRefSys", "conformsTo"))
        if (!root.containsKey(key) && originalFeature.containsKey(key))
          root.put(key, originalFeature.get(key));
      Map<String, Object> geometry =
          geometries.writeGeoJsonObject(
              feature.geometry(),
              new GeoJsonWriteOptions(
                  profile,
                  writeContext(feature, profile, saved),
                  options.dropZ(),
                  options.dropM(),
                  options.emptyToNull()));
      root.remove("measures");
      var conforms = new LinkedHashSet<String>();
      conforms.add(CONF + "core");
      Object existing = root.get("conformsTo");
      if (existing instanceof List<?> list)
        for (Object value : list) if (value instanceof String text) conforms.add(text);
      if (geometry != null) {
        root.remove("measures");
        if (geometry.containsKey("coordRefSys"))
          root.put("coordRefSys", geometry.get("coordRefSys"));
        if (geometry.containsKey("measures")) {
          conforms.add(CONF + "measures");
        }
        if (containsCurves(geometry)) conforms.add(CONF + "circular-arcs");
      }
      if (!options.dropM()
          && !effectiveMeasures(saved, GeoJsonContext.fromMap(asMap(saved.get("geometry"))))
              .isEmpty()) conforms.add(CONF + "measures");
      var featureMetadata = asMap(saved.get("feature"));
      if (root.containsKey("featureType")
          || root.containsKey("featureSchema")
          || featureMetadata.containsKey("featureType")
          || featureMetadata.containsKey("featureSchema")) conforms.add(CONF + "types-schemas");
      root.put("conformsTo", new ArrayList<>(conforms));
      root.remove("crs");
    } else {
      root.remove("conformsTo");
      root.remove("coordRefSys");
      root.remove("measures");
      if (profile == GeoJsonProfile.RFC7946) root.remove("crs");
    }
    return root;
  }

  static Object payload(String json, String field) {
    try {
      return JsonSupport.parse(json);
    } catch (GeometryCodecException e) {
      throw new GeoJsonFieldException(field, e.getMessage(), e);
    }
  }

  private static Map<String, Object> fieldContext(String json, String field) {
    try {
      return context(json);
    } catch (GeometryCodecException | IllegalArgumentException e) {
      throw new GeoJsonFieldException(field, e.getMessage(), e);
    }
  }

  public static Map<String, Object> context(String json) throws GeometryCodecException {
    if (json == null || json.isBlank()) return Map.of();
    var map = JsonSupport.parseObject(json);
    Object version = map.get("version");
    if (!(version instanceof Number) || !Objects.equals(String.valueOf(version), "1"))
      throw new IllegalArgumentException("Unsupported GeoJSON context version");
    for (String key : asMap(map.get("feature")).keySet())
      if (Set.of("type", "geometry", "place", "properties", "id").contains(key))
        throw new IllegalArgumentException("Reserved Feature member in GeoJSON context: " + key);
    for (String key : asMap(map.get("root")).keySet())
      if (Set.of("type", "features", "geometry", "place", "properties", "id").contains(key))
        throw new IllegalArgumentException("Reserved document member in GeoJSON context: " + key);
    for (String key : List.of("root", "feature", "geometry", "fallback"))
      if (map.containsKey(key) && !(map.get(key) instanceof Map))
        throw new IllegalArgumentException("GeoJSON context " + key + " must be an object");
    for (String key : List.of("geometry", "fallback"))
      if (map.containsKey(key)) GeoJsonContext.fromMap(asMap(map.get(key)));
    if (map.containsKey("profile"))
      try {
        GeoJsonProfile.valueOf(String.valueOf(map.get("profile")));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Unsupported GeoJSON context profile", e);
      }
    return map;
  }

  public static GeoJsonProfile outputProfile(GeoJsonProfile selected, Map<String, Object> saved) {
    if (selected != GeoJsonProfile.AUTO) return selected;
    return GeoJsonProfile.RFC7946;
  }

  public static GeoJsonProfile profile(
      GeoJsonProfile selected, Map<String, Object> root, Map<String, Object> feature) {
    Object declared =
        root.containsKey("conformsTo") ? root.get("conformsTo") : feature.get("conformsTo");
    boolean jsonFg = false;
    if (declared instanceof List<?> list)
      for (Object value : list)
        if (value instanceof String text && text.contains("/spec/json-fg-")) {
          if (!text.startsWith(CONF))
            throw new IllegalArgumentException("Unsupported JSON-FG version");
          jsonFg = true;
        }
    if (selected == GeoJsonProfile.AUTO && jsonFg) return GeoJsonProfile.JSONFG10;
    if (selected != GeoJsonProfile.AUTO) return selected;
    if (root.containsKey("place")
        || feature.containsKey("place")
        || root.containsKey("coordRefSys")) return GeoJsonProfile.JSONFG10;
    if (root.containsKey("crs") || feature.containsKey("crs")) return GeoJsonProfile.LEGACY2008;
    return GeoJsonProfile.RFC7946;
  }

  private static boolean needsPlace(Map<String, Object> value) {
    return Boolean.TRUE.equals(asMap(value.get("measures")).get("enabled"))
        || containsCurves(value)
        || !isCrs84(value.get("coordRefSys"));
  }

  private static boolean isCrs84(Object descriptor) {
    if (descriptor instanceof Map<?, ?> map)
      return "Reference".equals(map.get("type")) && isCrs84(map.get("href"));
    if (!(descriptor instanceof String text)) return false;
    return text.matches(
        "(?i)(?:OGC:|urn:ogc:def:crs:OGC:[^:]*:|https?://www\\.opengis\\.net/def/crs/OGC/[^/]+/)?CRS84h?");
  }

  private GeoJsonContext writeContext(
      GeoJsonFeature feature, GeoJsonProfile profile, Map<String, Object> saved)
      throws GeometryCodecException {
    GeoJsonContext context = GeoJsonContext.fromMap(asMap(saved.get("geometry")));
    validateContextTraversal(feature.geometry(), geometries.limits());
    geometries.validateSupportedGeometry(feature.geometry());
    if (profile == GeoJsonProfile.JSONFG10
        && context.coordRefSys() == null
        && feature.geometry() != null) {
      int srid = GeometrySridSupport.resolveConsistentSrid(feature.geometry());
      if (srid == 4326 || srid == 4979)
        return new GeoJsonContext(
            GeoJsonProfile.JSONFG10,
            "http://www.opengis.net/def/crs/OGC/0/" + (srid == 4979 ? "CRS84h" : "CRS84"),
            context.measures());
    }
    return context;
  }

  /** Bounds the legacy recursive SRID helper before choosing a default document context. */
  private static void validateContextTraversal(Geometry geometry, GeometryReadLimits limits)
      throws GeometryCodecException {
    if (geometry == null) return;
    record Entry(Geometry geometry, int depth) {}
    var pending = new ArrayDeque<Entry>();
    pending.push(new Entry(geometry, 1));
    long count = 0;
    while (!pending.isEmpty()) {
      Entry entry = pending.pop();
      if (!limits.allows(entry.depth()) || ++count > limits.maxComponents())
        throw new GeometryCodecException(
            GeometryCodecErrorCode.GC_LIMIT_EXCEEDED,
            "Geometry context component/depth budget exceeded");
      Geometry part = entry.geometry();
      List<? extends Geometry> children = null;
      if (part instanceof com.atolcd.hop.gis.geometry.curve.CompoundCurve curve)
        children = curve.getComponents();
      else if (part instanceof com.atolcd.hop.gis.geometry.curve.CurvePolygon polygon)
        children = polygon.getCurveRings();
      else if (part instanceof org.locationtech.jts.geom.Polygon polygon) {
        long childrenCount = 1L + polygon.getNumInteriorRing();
        if (count + pending.size() + childrenCount > limits.maxComponents())
          throw new GeometryCodecException(
              GeometryCodecErrorCode.GC_LIMIT_EXCEEDED,
              "Geometry context component budget exceeded");
        pending.push(new Entry(polygon.getExteriorRing(), entry.depth() + 1));
        for (int i = 0; i < polygon.getNumInteriorRing(); i++)
          pending.push(new Entry(polygon.getInteriorRingN(i), entry.depth() + 1));
      } else if (part instanceof org.locationtech.jts.geom.GeometryCollection collection) {
        if (count + pending.size() + collection.getNumGeometries() > limits.maxComponents())
          throw new GeometryCodecException(
              GeometryCodecErrorCode.GC_LIMIT_EXCEEDED,
              "Geometry context component budget exceeded");
        for (int i = 0; i < collection.getNumGeometries(); i++)
          pending.push(new Entry(collection.getGeometryN(i), entry.depth() + 1));
      }
      if (children != null) {
        if (count + pending.size() + children.size() > limits.maxComponents())
          throw new GeometryCodecException(
              GeometryCodecErrorCode.GC_LIMIT_EXCEEDED,
              "Geometry context component budget exceeded");
        for (Geometry child : children) pending.push(new Entry(child, entry.depth() + 1));
      }
    }
  }

  private static Map<String, Object> effectiveMeasures(
      Map<String, Object> saved, GeoJsonContext geometryContext) {
    if (!geometryContext.measures().isEmpty()) return geometryContext.measures();
    var feature = asMap(saved.get("feature"));
    var root = asMap(saved.get("root"));
    return asMap(feature.containsKey("measures") ? feature.get("measures") : root.get("measures"));
  }

  private static boolean containsCurves(Object value) {
    if (value instanceof Map<?, ?> m) {
      if (m.get("type") != null
          && Set.of("CircularString", "CompoundCurve", "CurvePolygon", "MultiCurve", "MultiSurface")
              .contains(m.get("type"))) return true;
      for (Object child : m.values()) if (containsCurves(child)) return true;
    }
    if (value instanceof List<?> list)
      for (Object child : list) if (containsCurves(child)) return true;
    return false;
  }

  private static void validateFg(Map<String, Object> root, Map<String, Object> feature) {
    boolean embedded = root != feature && !root.isEmpty();
    if (embedded && (feature.containsKey("coordRefSys") || feature.containsKey("conformsTo")))
      throw new IllegalArgumentException(
          "JSON-FG coordRefSys and conformsTo are allowed only on the document root");
    Object conforms = embedded ? root.get("conformsTo") : feature.get("conformsTo");
    if (!(conforms instanceof List<?> list) || !list.contains(CONF + "core"))
      throw new IllegalArgumentException("JSON-FG requires its 1.0 core conformsTo declaration");
    for (Object uri : (List<?>) conforms) {
      if (!(uri instanceof String text))
        throw new IllegalArgumentException("JSON-FG conformsTo entries must be URI strings");
      if (text.contains("/spec/json-fg-") && !text.startsWith(CONF))
        throw new IllegalArgumentException("Unsupported JSON-FG conformance version");
      if (text.startsWith(CONF)
          && !Set.of(
                  CONF + "core", CONF + "circular-arcs", CONF + "measures", CONF + "types-schemas")
              .contains(text))
        throw new IllegalArgumentException("Unsupported JSON-FG conformance class: " + text);
    }
    Object measures =
        feature.containsKey("measures") ? feature.get("measures") : root.get("measures");
    if (measures != null) {
      if (!(measures instanceof Map))
        throw new IllegalArgumentException("JSON-FG measures must be an object");
      var descriptor = asMap(measures);
      if (!(descriptor.get("enabled") instanceof Boolean))
        throw new IllegalArgumentException("JSON-FG measures.enabled must be boolean");
      for (String key : List.of("unit", "description"))
        if (descriptor.containsKey(key) && !(descriptor.get(key) instanceof String))
          throw new IllegalArgumentException("JSON-FG measures." + key + " must be a string");
      if (!((List<?>) conforms).contains(CONF + "measures"))
        throw new IllegalArgumentException(
            "JSON-FG measured geometries require measures conformance");
    }
    if (containsCurves(feature.get("place"))
        && !((List<?>) conforms).contains(CONF + "circular-arcs"))
      throw new IllegalArgumentException("JSON-FG curves require circular-arcs conformance");
    if (feature.containsKey("time") && feature.get("time") != null)
      validateTime(feature.get("time"));
    boolean schemas =
        ((List<?>) conforms).contains(CONF + "types-schemas")
            || root.containsKey("featureType")
            || root.containsKey("featureSchema")
            || feature.containsKey("featureType")
            || feature.containsKey("featureSchema");
    if (schemas && !((List<?>) conforms).contains(CONF + "types-schemas"))
      throw new IllegalArgumentException(
          "JSON-FG feature types and schemas require types-schemas conformance");
    for (Map<String, Object> holder : List.of(root, feature)) {
      if (holder.containsKey("featureType") && !(holder.get("featureType") instanceof String))
        throw new IllegalArgumentException("JSON-FG featureType must be a string");
      if (holder.containsKey("featureSchema")) validateSchemaReference(holder.get("featureSchema"));
    }
    if (root.get("featureSchema") instanceof String
        && root.get("featureType") instanceof String
        && feature.containsKey("featureType")
        && !Objects.equals(root.get("featureType"), feature.get("featureType")))
      throw new IllegalArgumentException(
          "A collection with a single featureSchema URI requires a consistent featureType");
    if (schemas
        && !"FeatureCollection".equals(feature.get("type"))
        && !root.containsKey("featureType")
        && !feature.containsKey("featureType"))
      throw new IllegalArgumentException(
          "JSON-FG featureType is required when types-schemas conformance is used");
    if (feature.containsKey("geometryDimension")) {
      Object value = feature.get("geometryDimension");
      dimension(value);
    }
  }

  private static void validateSchemaReference(Object value) {
    if (value instanceof String text) {
      validUri(text);
      return;
    }
    if (value instanceof Map<?, ?> map) {
      for (Object uri : map.values()) {
        if (!(uri instanceof String text))
          throw new IllegalArgumentException("JSON-FG featureSchema values must be URI strings");
        validUri(text);
      }
      return;
    }
    throw new IllegalArgumentException("JSON-FG featureSchema must be a URI or a URI map");
  }

  private static void validUri(String text) {
    try {
      if (!java.net.URI.create(text).isAbsolute()) throw new IllegalArgumentException();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("JSON-FG featureSchema must contain absolute URIs", e);
    }
  }

  /** Additional Feature-level requirements for the RFC geometry slot in JSON-FG. */
  private static void validateFgRfcGeometry(Map<String, Object> object, Geometry geometry) {
    if (geometry == null) return;
    int dimension = -1;
    var scopes = new ArrayDeque<Iterator<?>>();
    Object value = object;
    while (true) {
      if (value instanceof Map<?, ?> map) {
        var children = new ArrayList<Object>(2);
        if (map.get("coordinates") instanceof List<?> coordinates) children.add(coordinates);
        if (map.get("geometries") instanceof List<?> geometries) children.add(geometries);
        if (!children.isEmpty()) scopes.push(children.iterator());
      } else if (value instanceof List<?> list && !list.isEmpty()) {
        if (list.getFirst() instanceof Number) {
          if (dimension < 0) dimension = list.size();
          else if (dimension != list.size())
            throw new IllegalArgumentException(
                "All JSON-FG geometry positions must have the same coordinate dimension");
        } else scopes.push(list.iterator());
      }
      while (!scopes.isEmpty() && !scopes.peek().hasNext()) scopes.pop();
      if (scopes.isEmpty()) break;
      value = scopes.peek().next();
    }
    var invalidity =
        new org.locationtech.jts.operation.valid.IsValidOp(geometry).getValidationError();
    if (invalidity != null)
      throw new IllegalArgumentException(
          "JSON-FG geometry must be a valid Simple Feature: " + invalidity.getMessage());
  }

  private static boolean jsonEquivalent(Object a, Object b) {
    if (a instanceof Number x && b instanceof Number y)
      return new java.math.BigDecimal(x.toString())
              .compareTo(new java.math.BigDecimal(y.toString()))
          == 0;
    if (a instanceof List<?> x && b instanceof List<?> y) {
      if (x.size() != y.size()) return false;
      for (int i = 0; i < x.size(); i++) if (!jsonEquivalent(x.get(i), y.get(i))) return false;
      return true;
    }
    if (a instanceof Map<?, ?> x && b instanceof Map<?, ?> y) {
      if (!x.keySet().equals(y.keySet())) return false;
      for (Object key : x.keySet()) if (!jsonEquivalent(x.get(key), y.get(key))) return false;
      return true;
    }
    return Objects.equals(a, b);
  }

  @SafeVarargs
  private static void assertDimension(Geometry geometry, Map<String, Object>... contexts) {
    if (geometry == null) return;
    for (var context : contexts)
      if (context.containsKey("geometryDimension")
          && dimension(context.get("geometryDimension")) != geometry.getDimension())
        throw new IllegalArgumentException("JSON-FG geometryDimension disagrees with geometry");
  }

  private static int dimension(Object value) {
    try {
      if (!(value instanceof Number)) throw new IllegalArgumentException();
      int d = new java.math.BigDecimal(value.toString()).intValueExact();
      if (d < 0 || d > 3) throw new IllegalArgumentException();
      return d;
    } catch (ArithmeticException | IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid JSON-FG geometryDimension", e);
    }
  }

  private static void validateTime(Object value) {
    if (!(value instanceof Map<?, ?> raw))
      throw new IllegalArgumentException("JSON-FG time must be an object or null");
    var time = asMap(raw);
    if (time.isEmpty()) throw new IllegalArgumentException("JSON-FG time must not be empty");
    try {
      java.time.LocalDate date = null;
      TimePoint timestamp = null;
      if (time.containsKey("date")) date = parseDate(time.get("date"));
      if (time.containsKey("timestamp")) timestamp = parseTimestamp(time.get("timestamp"));
      if (date != null && timestamp != null && !date.equals(timestamp.date()))
        throw new IllegalArgumentException("JSON-FG date and timestamp disagree");
      if (time.containsKey("interval")) {
        Object interval = time.get("interval");
        if (!(interval instanceof List<?> ends) || ends.size() != 2)
          throw new IllegalArgumentException("JSON-FG interval requires two endpoints");
        Object start = ends.get(0), end = ends.get(1);
        boolean openStart = "..".equals(start), openEnd = "..".equals(end);
        boolean timestampInterval =
            (!openStart && String.valueOf(start).contains("T"))
                || (!openEnd && String.valueOf(end).contains("T"));
        if (timestampInterval) {
          TimePoint a = openStart ? null : parseTimestamp(start),
              b = openEnd ? null : parseTimestamp(end);
          if (a != null && b != null && a.compareTo(b) > 0)
            throw new IllegalArgumentException("JSON-FG interval is reversed");
          if (timestamp != null
              && ((a != null && timestamp.compareTo(a) < 0)
                  || (b != null && timestamp.compareTo(b) > 0)))
            throw new IllegalArgumentException("JSON-FG timestamp lies outside interval");
          if (date != null
              && ((a != null && date.isBefore(a.date())) || (b != null && date.isAfter(b.date()))))
            throw new IllegalArgumentException("JSON-FG date lies outside timestamp interval");

        } else {
          java.time.LocalDate a = openStart ? null : parseDate(start),
              b = openEnd ? null : parseDate(end);
          if (a != null && b != null && a.isAfter(b))
            throw new IllegalArgumentException("JSON-FG interval is reversed");
          if (date != null && ((a != null && date.isBefore(a)) || (b != null && date.isAfter(b))))
            throw new IllegalArgumentException("JSON-FG date lies outside interval");
          if (timestamp != null
              && ((a != null && timestamp.date().isBefore(a))
                  || (b != null && timestamp.date().isAfter(b))))
            throw new IllegalArgumentException("JSON-FG timestamp lies outside date interval");
        }
      }
    } catch (java.time.DateTimeException e) {
      throw new IllegalArgumentException("Invalid JSON-FG temporal value", e);
    }
  }

  private static java.time.LocalDate parseDate(Object value) {
    if (!(value instanceof String text) || !text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
      throw new IllegalArgumentException("Invalid JSON-FG date");
    return java.time.LocalDate.parse(text);
  }

  private record TimePoint(long seconds, java.math.BigDecimal fraction)
      implements Comparable<TimePoint> {
    @Override
    public int compareTo(TimePoint other) {
      int secondsOrder = Long.compare(seconds, other.seconds);
      return secondsOrder != 0 ? secondsOrder : fraction.compareTo(other.fraction);
    }

    java.time.LocalDate date() {
      return java.time.Instant.ofEpochSecond(seconds)
          .atOffset(java.time.ZoneOffset.UTC)
          .toLocalDate();
    }
  }

  private static TimePoint parseTimestamp(Object value) {
    if (!(value instanceof String text)
        || !text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]+)?Z"))
      throw new IllegalArgumentException("JSON-FG timestamps require UTC Z");
    int dot = text.indexOf('.');
    String base = dot < 0 ? text : text.substring(0, dot) + "Z";
    if (dot >= 0 && text.length() - dot > 10002)
      throw new IllegalArgumentException("JSON-FG timestamp fraction exceeds the precision budget");
    java.math.BigDecimal fraction =
        dot < 0
            ? java.math.BigDecimal.ZERO
            : new java.math.BigDecimal("0" + text.substring(dot, text.length() - 1));
    return new TimePoint(java.time.Instant.parse(base).getEpochSecond(), fraction);
  }

  public static Map<String, Object> metadata(Map<String, Object> input, Set<String> excluded) {
    var result = new LinkedHashMap<String, Object>();
    input.forEach(
        (k, v) -> {
          if (!excluded.contains(k)) result.put(k, v);
        });
    return result;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> asMap(Object value) {
    return value instanceof Map ? (Map<String, Object>) value : Map.of();
  }

  private static Map<String, Object> object(Object value, String label) {
    if (!(value instanceof Map))
      throw new IllegalArgumentException(label + " must be a JSON object or null");
    return asMap(value);
  }
}
