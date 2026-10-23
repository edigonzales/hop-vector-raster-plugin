package ch.so.agi.hop.vector.core.geojson;

import com.atolcd.hop.core.row.value.ValueMetaGeometry;
import com.atolcd.hop.gis.geometry.codec.*;
import java.math.*;
import java.util.*;
import org.apache.hop.core.row.*;
import org.apache.hop.core.row.value.*;
import org.locationtech.jts.geom.Geometry;

/** Stable row layout and explicit typed property projections, independent of file sampling. */
public final class GeoJsonFeatureRows {
  private GeoJsonFeatureRows() {}

  public static IRowMeta schema(GeoJsonFeatureOptions options) {
    if (options.fallbackField().isBlank())
      throw new IllegalArgumentException(
          "Fallback output field is required for a Feature decoder or reader");
    var row = new RowMeta();
    row.addValueMeta(new ValueMetaGeometry(options.geometryField()));
    row.addValueMeta(new ValueMetaGeometry(options.fallbackField()));
    row.addValueMeta(new ValueMetaString(options.propertiesField()));
    row.addValueMeta(new ValueMetaString(options.idField()));
    row.addValueMeta(new ValueMetaString(options.contextField()));
    for (var m : options.mappings())
      row.addValueMeta(
          switch (m.type()) {
            case STRING, JSON -> new ValueMetaString(m.field());
            case INTEGER -> new ValueMetaInteger(m.field());
            case NUMBER -> new ValueMetaNumber(m.field());
            case BIG_NUMBER -> new ValueMetaBigNumber(m.field());
            case BOOLEAN -> new ValueMetaBoolean(m.field());
          });
    return row;
  }

  public static Object[] decodeRow(GeoJsonFeature feature, GeoJsonFeatureOptions options)
      throws GeometryCodecException {
    Object[] row = new Object[5 + options.mappings().size()];
    row[0] = feature.geometry();
    row[1] = feature.fallbackGeometry();
    row[2] = feature.propertiesJson();
    row[3] = feature.idJson();
    row[4] = feature.contextJson();
    Object properties =
        feature.propertiesJson() == null ? null : JsonSupport.parse(feature.propertiesJson());
    for (int i = 0; i < options.mappings().size(); i++) {
      var m = options.mappings().get(i);
      Object value = at(properties, m.pointer());
      row[5 + i] = convert(value, m);
    }
    return row;
  }

  public static GeoJsonFeature encodeRow(
      IRowMeta schema, Object[] row, GeoJsonFeatureOptions options) throws GeometryCodecException {
    Geometry geometry = geometry(schema, row, options.geometryField(), true),
        fallback = geometry(schema, row, options.fallbackField(), false);
    String properties = text(schema, row, options.propertiesField()),
        id = text(schema, row, options.idField()),
        context = text(schema, row, options.contextField());
    Object parsed =
        properties == null
            ? null
            : GeoJsonFeatureCodec.payload(properties, options.propertiesField());
    if (!(parsed == null || parsed instanceof Map))
      throw new IllegalArgumentException("Properties JSON must be an object or null");
    Map<String, Object> values =
        parsed == null ? null : mutableMap(GeoJsonFeatureCodec.asMap(parsed));
    for (var m : options.mappings()) {
      int index = schema.indexOfValue(m.field());
      if (index < 0) throw new IllegalArgumentException("Mapped field not found: " + m.field());
      Object value = mappedValue(schema.getValueMeta(index), row[index], m);
      if (value == null && !m.writeNull()) continue;
      if (values == null) values = new LinkedHashMap<>();
      if (m.type() == GeoJsonFieldMapping.Type.JSON && value != null)
        value = GeoJsonFeatureCodec.payload(String.valueOf(value), m.field());
      else value = convert(value, m);
      put(values, m.pointer(), value);
    }
    return new GeoJsonFeature(geometry, fallback, JsonSupport.stringify(values), id, context);
  }

  /** Checks fixed upstream metadata before data-error policies may be applied. */
  public static void validateEncodingSchema(IRowMeta schema, GeoJsonFeatureOptions options) {
    int geometry = schema.indexOfValue(options.geometryField());
    if (geometry < 0 || !(schema.getValueMeta(geometry) instanceof ValueMetaGeometry))
      throw new IllegalArgumentException(
          "An existing native Geometry field is required: " + options.geometryField());
    int fallback =
        options.fallbackField().isBlank() ? -1 : schema.indexOfValue(options.fallbackField());
    if (fallback >= 0 && !(schema.getValueMeta(fallback) instanceof ValueMetaGeometry))
      throw new IllegalArgumentException("Fallback field must be native Geometry");
    for (String field :
        List.of(options.propertiesField(), options.idField(), options.contextField())) {
      int index = schema.indexOfValue(field);
      if (index >= 0 && schema.getValueMeta(index).getType() != IValueMeta.TYPE_STRING)
        throw new IllegalArgumentException("JSON payload field must be String: " + field);
    }
    for (var mapping : options.mappings()) {
      int index = schema.indexOfValue(mapping.field());
      if (index < 0)
        throw new IllegalArgumentException("Mapped field not found: " + mapping.field());
      int type =
          switch (mapping.type()) {
            case STRING, JSON -> IValueMeta.TYPE_STRING;
            case INTEGER -> IValueMeta.TYPE_INTEGER;
            case NUMBER -> IValueMeta.TYPE_NUMBER;
            case BIG_NUMBER -> IValueMeta.TYPE_BIGNUMBER;
            case BOOLEAN -> IValueMeta.TYPE_BOOLEAN;
          };
      if (schema.getValueMeta(index).getType() != type)
        throw new IllegalArgumentException(
            "Mapped field type does not match " + mapping.type() + ": " + mapping.field());
    }
  }

  private static Object mappedValue(
      IValueMeta metadata, Object value, GeoJsonFieldMapping mapping) {
    if (value == null) return null;
    try {
      return switch (mapping.type()) {
        case STRING, JSON -> metadata.getString(value);
        case INTEGER -> metadata.getInteger(value);
        case NUMBER -> metadata.getNumber(value);
        case BIG_NUMBER -> metadata.getBigNumber(value);
        case BOOLEAN -> metadata.getBoolean(value);
      };
    } catch (org.apache.hop.core.exception.HopValueException e) {
      throw new IllegalStateException(
          "Cannot read typed Hop storage for mapped field " + mapping.field(), e);
    }
  }

  private static Geometry geometry(IRowMeta meta, Object[] row, String field, boolean required) {
    int i = field.isBlank() ? -1 : meta.indexOfValue(field);
    if (i < 0) {
      if (required) throw new IllegalArgumentException("Geometry field not found: " + field);
      return null;
    }
    if (!(meta.getValueMeta(i) instanceof ValueMetaGeometry))
      throw new IllegalArgumentException("Native Geometry required: " + field);
    if (row[i] != null && !(row[i] instanceof Geometry))
      throw new IllegalStateException(
          "Native Geometry value is incompatible with the shared Geometry classloader: " + field);
    return (Geometry) row[i];
  }

  private static String text(IRowMeta meta, Object[] row, String field) {
    int i = meta.indexOfValue(field);
    if (i < 0 || row[i] == null) return null;
    if (meta.getValueMeta(i).getType() != IValueMeta.TYPE_STRING)
      throw new IllegalArgumentException("JSON text field must be String: " + field);
    try {
      return meta.getValueMeta(i).getString(row[i]);
    } catch (org.apache.hop.core.exception.HopValueException e) {
      throw new IllegalStateException("Cannot read Hop String storage: " + field, e);
    }
  }

  private static Object convert(Object value, GeoJsonFieldMapping m) throws GeometryCodecException {
    if (value == null) return null;
    try {
      return switch (m.type()) {
        case JSON -> JsonSupport.stringify(value);
        case STRING -> {
          if (!(value instanceof String)) throw new IllegalArgumentException();
          yield value;
        }
        case BOOLEAN -> {
          if (!(value instanceof Boolean)) throw new IllegalArgumentException();
          yield value;
        }
        case INTEGER -> {
          if (!(value instanceof Number)) throw new IllegalArgumentException();
          yield new BigDecimal(value.toString()).longValueExact();
        }
        case BIG_NUMBER -> {
          if (!(value instanceof Number)) throw new IllegalArgumentException();
          yield new BigDecimal(value.toString());
        }
        case NUMBER -> {
          if (!(value instanceof Number)) throw new IllegalArgumentException();
          double result = ((Number) value).doubleValue();
          if (!Double.isFinite(result)
              || BigDecimal.valueOf(result).compareTo(new BigDecimal(value.toString())) != 0)
            throw new IllegalArgumentException();
          yield result;
        }
      };
    } catch (ArithmeticException | IllegalArgumentException e) {
      throw new GeoJsonFieldException(
          m.field(),
          "Property " + m.pointer() + " is incompatible with " + m.type() + " field " + m.field(),
          e);
    }
  }

  private static String[] parts(String pointer) {
    return Arrays.stream(pointer.substring(1).split("/", -1))
        .map(s -> s.replace("~1", "/").replace("~0", "~"))
        .toArray(String[]::new);
  }

  private static Object at(Object value, String pointer) {
    for (String key : parts(pointer)) {
      if (value instanceof Map<?, ?> map) value = map.get(key);
      else if (value instanceof List<?> list) {
        try {
          if (!key.matches("0|[1-9][0-9]*"))
            throw new IllegalArgumentException("Invalid properties array index: " + pointer);
          int i = Integer.parseInt(key);
          value = i < list.size() ? list.get(i) : null;
        } catch (NumberFormatException e) {
          return null;
        }
      } else return null;
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  private static void put(Map<String, Object> root, String pointer, Object value) {
    Object current = root;
    String[] parts = parts(pointer);
    for (int i = 0; i < parts.length - 1; i++) {
      String key = parts[i];
      if (current instanceof Map<?, ?> map) {
        Object child = map.get(key);
        if (child == null) {
          child = new LinkedHashMap<String, Object>();
          ((Map<String, Object>) map).put(key, child);
        }
        if (!(child instanceof Map || child instanceof List))
          throw new IllegalArgumentException("Properties path traverses a scalar: " + pointer);
        current = child;
      } else if (current instanceof List<?> list) {
        int index = arrayIndex(key, list.size(), pointer);
        current = list.get(index);
      } else throw new IllegalArgumentException("Invalid properties path: " + pointer);
    }
    String last = parts[parts.length - 1];
    if (current instanceof Map<?, ?> map) ((Map<String, Object>) map).put(last, value);
    else if (current instanceof List<?> list)
      ((List<Object>) list).set(arrayIndex(last, list.size(), pointer), value);
    else throw new IllegalArgumentException("Invalid properties path: " + pointer);
  }

  private static int arrayIndex(String value, int size, String pointer) {
    try {
      if (!value.matches("0|[1-9][0-9]*")) throw new IllegalArgumentException();
      int index = Integer.parseInt(value);
      if (index >= size) throw new IllegalArgumentException();
      return index;
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid properties array index: " + pointer, e);
    }
  }

  private static Map<String, Object> mutableMap(Map<String, Object> input) {
    var out = new LinkedHashMap<String, Object>();
    input.forEach((k, v) -> out.put(k, mutable(v)));
    return out;
  }

  private static Object mutable(Object value) {
    if (value instanceof Map<?, ?>) return mutableMap(GeoJsonFeatureCodec.asMap(value));
    if (value instanceof List<?> list) {
      var out = new ArrayList<>();
      for (Object v : list) out.add(mutable(v));
      return out;
    }
    return value;
  }
}
