package ch.so.agi.hop.vector.formats.geojson;

import ch.so.agi.hop.vector.core.*;
import ch.so.agi.hop.vector.core.geojson.*;
import com.atolcd.hop.gis.geometry.codec.JsonSupport;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Streaming FeatureCollection provider; schema inspection never scans the input file. */
public final class GeoJsonProvider implements VectorProvider {
  public VectorFormat format() {
    return VectorFormat.GEOJSON;
  }

  public List<LayerSchema> layers(ReadRequest request) {
    return List.of(schema(request, readOptions(request)));
  }

  public VectorSource open(ReadRequest request) {
    if (!request.crsOverride().isBlank())
      throw new IllegalArgumentException(
          "GeoJSON uses its declared CRS; use an explicit Geometry Converter CRS assignment"
              + " instead");
    return new Source(request, readOptions(request));
  }

  private static GeoJsonFeatureOptions readOptions(ReadRequest request) {
    GeoJsonFeatureOptions original = options(request.options()).features();
    return request.geometryField().isBlank()
        ? original
        : new GeoJsonFeatureOptions(
            original.profile(),
            request.geometryField(),
            original.fallbackField(),
            original.propertiesField(),
            original.idField(),
            original.contextField(),
            original.dropZ(),
            original.dropM(),
            original.emptyToNull(),
            original.mappings());
  }

  public VectorSink create(WriteRequest request) throws Exception {
    if (request.rowMeta() == null)
      throw new IllegalArgumentException("Upstream schema unavailable");
    GeoJsonOptions settings = options(request.options());
    GeoJsonFeatureRows.validateEncodingSchema(request.rowMeta(), settings.features());
    return new Sink(request, settings);
  }

  private static GeoJsonOptions options(FormatOptions options) {
    if (options instanceof GeoJsonOptions json) return json;
    if (options instanceof GeoJsonFeatureOptions feature) return new GeoJsonOptions(feature, false);
    return GeoJsonOptions.defaults();
  }

  private static LayerSchema schema(ReadRequest request, GeoJsonFeatureOptions options) {
    return new LayerSchema(
        "features",
        options.geometryField(),
        (GeometrySchema) null,
        GeoJsonFeatureRows.schema(options));
  }

  private static final class Source implements VectorSource {
    private final ReadRequest request;
    private final GeoJsonFeatureOptions options;
    private final LayerSchema schema;
    private final GeoJsonFeatureCodec codec = new GeoJsonFeatureCodec();
    private FeatureCursor cursor;
    private Map<String, Object> root;
    private String context;
    private boolean started, singleRead, closed;

    Source(ReadRequest request, GeoJsonFeatureOptions options) {
      this.request = request;
      this.options = options;
      this.schema = GeoJsonProvider.schema(request, options);
    }

    public LayerSchema schema() {
      return schema;
    }

    public Object[] read() throws Exception {
      if (closed) return null;
      StagedVectorFile.checkCancelled();
      if (!started) {
        started = true;
        root = scanRoot(request);
        Object type = root.get("type");
        if ("FeatureCollection".equals(type)) {
          codec.validateCollection(root, options);
          Map<String, Object> metadata = new LinkedHashMap<>(root);
          metadata.remove("type");
          metadata.remove("bbox");
          context = JsonSupport.stringify(metadata);
          cursor = new FeatureCursor(request);
        } else if (!"Feature".equals(type)) {
          throw new IOException(
              "Expected a Feature or FeatureCollection; use Geometry Converter for a geometry"
                  + " object");
        }
      }
      Map<String, Object> feature;
      if (cursor == null) {
        if (singleRead) return null;
        singleRead = true;
        feature = root;
      } else feature = cursor.read();
      if (feature == null) {
        if (!singleRead
            && root.keySet().stream()
                .anyMatch(key -> !Set.of("type", "conformsTo", "bbox").contains(key)))
          request
              .diagnostics()
              .warning(
                  "",
                  "empty-collection-context",
                  "An empty collection has no feature rows to carry its root metadata; retain the"
                      + " source document separately");
        singleRead = true;
        return null;
      }
      singleRead = true;
      return GeoJsonFeatureRows.decodeRow(
          codec.decode(JsonSupport.stringify(feature), options, context), options);
    }

    public void close() throws Exception {
      closed = true;
      if (cursor != null) cursor.close();
    }
  }

  /**
   * First pass skips the feature array and retains bounded root context regardless of key order.
   */
  private static Map<String, Object> scanRoot(ReadRequest request) throws Exception {
    Map<String, Object> result = new LinkedHashMap<>();
    boolean features = false;
    long metadataSize = 0;
    try (var reader =
        JsonSupport.open(Files.newBufferedReader(request.file(), StandardCharsets.UTF_8))) {
      require(reader.next(), JsonSupport.Token.START_OBJECT, "Expected JSON object");
      JsonSupport.Token token;
      while ((token = reader.next()) != JsonSupport.Token.END_OBJECT) {
        require(token, JsonSupport.Token.PROPERTY_NAME, "Expected root property");
        String key = reader.name();
        token = reader.next();
        if ("features".equals(key)) {
          require(token, JsonSupport.Token.START_ARRAY, "features must be an array");
          features = true;
          reader.skipValue();
        } else {
          Object value = reader.readValue();
          metadataSize +=
              JsonSupport.stringify(key).length() + JsonSupport.stringify(value).length();
          if (metadataSize > 64L * 1024 * 1024)
            throw new IOException("GeoJSON root metadata exceeds 64 MiB");
          result.put(key, value);
        }
        StagedVectorFile.checkCancelled();
      }
      if (reader.next() != null) throw new IOException("Trailing JSON data");
    }
    if ("FeatureCollection".equals(result.get("type")) != features)
      throw new IOException(
          "FeatureCollection requires a features array; Feature must not have one");
    return result;
  }

  private static void require(JsonSupport.Token actual, JsonSupport.Token expected, String message)
      throws IOException {
    if (actual != expected) throw new IOException(message);
  }

  /** Second pass materializes at most one feature. Root syntax was validated by the first pass. */
  private static final class FeatureCursor implements AutoCloseable {
    private final JsonSupport.JsonReader reader;
    private boolean ended;

    FeatureCursor(ReadRequest request) throws Exception {
      reader = JsonSupport.open(Files.newBufferedReader(request.file(), StandardCharsets.UTF_8));
      try {
        require(reader.next(), JsonSupport.Token.START_OBJECT, "Expected root object");
        JsonSupport.Token token;
        while ((token = reader.next()) != JsonSupport.Token.END_OBJECT) {
          require(token, JsonSupport.Token.PROPERTY_NAME, "Expected root property");
          String key = reader.name();
          token = reader.next();
          if ("features".equals(key)) {
            require(token, JsonSupport.Token.START_ARRAY, "Expected features array");
            return;
          }
          reader.skipValue();
        }
        throw new IOException("Missing features array");
      } catch (Exception | Error e) {
        reader.close();
        throw e;
      }
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> read() throws Exception {
      if (ended) return null;
      JsonSupport.Token token = reader.next();
      if (token == JsonSupport.Token.END_ARRAY) {
        ended = true;
        return null;
      }
      require(token, JsonSupport.Token.START_OBJECT, "Feature array element must be an object");
      return (Map<String, Object>) reader.readValue();
    }

    public void close() throws Exception {
      reader.close();
    }
  }

  private static final class Sink implements VectorSink {
    private final WriteRequest request;
    private final GeoJsonFeatureOptions options;
    private final GeoJsonFeatureCodec codec = new GeoJsonFeatureCodec();
    private final StagedVectorFile stage;
    private final BufferedWriter writer;
    private final Map<String, Object> metadata = new LinkedHashMap<>();
    private boolean first = true, finished, closed, failed;

    Sink(WriteRequest request, GeoJsonOptions options) throws Exception {
      this.request = request;
      this.options = options.features();
      stage = new StagedVectorFile(request.file(), options.overwrite());
      BufferedWriter candidate = null;
      try {
        candidate = Files.newBufferedWriter(stage.file, StandardCharsets.UTF_8);
        candidate.write("{\"type\":\"FeatureCollection\",\"features\":[");
        writer = candidate;
      } catch (Exception | Error e) {
        if (candidate != null) candidate.close();
        stage.close();
        throw e;
      }
    }

    public boolean write(Object[] row) throws Exception {
      if (finished || closed || failed) throw new IOException("GeoJSON output is closed");
      try {
        request.checkCancelled();
        GeoJsonFeature feature = GeoJsonFeatureRows.encodeRow(request.rowMeta(), row, options);
        Map<String, Object> object = codec.encodeObject(feature, options, false);
        merge(codec.rootMetadata(feature, options));
        if (!first) writer.write(',');
        writer.write(JsonSupport.stringify(object));
        first = false;
        return true;
      } catch (Exception | Error error) {
        failed = true;
        throw error;
      }
    }

    private void merge(Map<String, Object> next) throws IOException {
      for (var entry : next.entrySet()) {
        String key = entry.getKey();
        if (Set.of("type", "features", "bbox").contains(key)) continue;
        if (key.equals("conformsTo")) {
          LinkedHashSet<Object> union = new LinkedHashSet<>();
          if (metadata.get(key) instanceof List<?> old) union.addAll(old);
          if (entry.getValue() instanceof List<?> values) union.addAll(values);
          metadata.put(key, new ArrayList<>(union));
        } else if (metadata.containsKey(key)
            && !Objects.equals(metadata.get(key), entry.getValue())) {
          throw new IOException(
              "Inconsistent collection metadata '"
                  + key
                  + "'; partition the rows into separate files");
        } else metadata.put(key, entry.getValue());
      }
    }

    public void finish() throws Exception {
      if (finished) return;
      if (closed || failed) throw new IOException("GeoJSON output was aborted");
      try {
        request.checkCancelled();
        // Even an empty JSON-FG collection must carry the core conformance declaration.
        if (options.profile().name().equals("JSONFG10") && !metadata.containsKey("conformsTo"))
          metadata.put(
              "conformsTo", List.of("http://www.opengis.net/spec/json-fg-1/1.0/conf/core"));
        writer.write(']');
        for (var entry : metadata.entrySet()) {
          writer.write(',');
          writer.write(JsonSupport.stringify(entry.getKey()));
          writer.write(':');
          writer.write(JsonSupport.stringify(entry.getValue()));
        }
        writer.write('}');
        writer.close();
        request.checkCancelled();
        stage.publish();
        finished = true;
      } catch (Exception | Error error) {
        failed = true;
        throw error;
      }
    }

    public void close() throws Exception {
      if (closed) return;
      closed = true;
      try {
        writer.close();
      } finally {
        stage.close();
      }
    }
  }
}
